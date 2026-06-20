package com.eliteforge.spawn;

import com.eliteforge.EliteForge;
import com.eliteforge.ability.Ability;
import com.eliteforge.ability.AbilityRegistry;
import com.eliteforge.ability.AbilityInteraction;
import com.eliteforge.capability.EliteCapability;
import com.eliteforge.capability.EliteData;
import com.eliteforge.capability.EliteCapabilitySync;
import com.eliteforge.config.DifficultyMode;
import com.eliteforge.config.EliteForgeConfig;
import com.eliteforge.difficulty.ChunkHeatManager;
import com.eliteforge.difficulty.DifficultyManager;
import com.eliteforge.difficulty.PlayerExperienceManager;
import com.eliteforge.quality.LootHandler;
import com.eliteforge.quality.QualityTier;
// SetBonus removed in v0.2.0
import com.eliteforge.util.NBTKeys;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityMobGriefingEvent;

import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Central event handler for all EliteForge game events. Dispatches events to
 * the appropriate subsystems (abilities, difficulty, quality, etc.).
 * <p>
 * Phase 6 additions:
 * - Elite death event rewards (particles, kill message, loot, heat, exp)
 * - Elite despawn for unengaged CASUAL mode elites
 * - HUNTER set bonus damage multiplier applied in LivingHurtEvent
 * - Player death to elite triggers difficulty fatigue
 * <p>
 * Q7: Added {@link #dispatchAbilityTick}, {@link #dispatchAbilityHurt},
 * {@link #dispatchAbilityAttack}, {@link #dispatchAbilityDeath}, and
 * {@link #dispatchAbilityPlayerKill} helpers to centralize the
 * "iterate abilities, look up by ID, try/catch dispatch" pattern that was
 * repeated 6+ times throughout this class. Each helper handles the
 * defensive copy of the abilities entry set, null check on the looked-up
 * ability, and exception swallowing with consistent log levels (Q8).
 */
@Mod.EventBusSubscriber(modid = EliteForge.MODID)
public class EliteEventHandler {

    private static final Logger LOGGER = LogManager.getLogger();
    private static final EliteSpawnHandler SPAWN_HANDLER = new EliteSpawnHandler();
    private static int tickCounter = 0;

    /** Tracking set for known elite entities. Uses identity semantics to avoid
     *  issues with Entity.equals() implementations. Maintained by tickEliteAbilities
     *  and onEntityJoinLevel. Cleaned up on entity death/removal. */
    private static final Set<LivingEntity> TRACKED_ELITES = java.util.Collections.newSetFromMap(new IdentityHashMap<>());

    /** Tracking set for summoned minions (Necromancy undead, Clone clones, ...).
     *  Used by {@link #tickSummonLeashes} to apply leash pull-back every 20 ticks.
     *  Separate from {@link #TRACKED_ELITES} because most summons are NOT marked
     *  elite (they have no abilities to tick and must not trigger elite death
     *  rewards), so they would be evicted from TRACKED_ELITES by processEliteBatch. */
    private static final Set<LivingEntity> TRACKED_SUMMONS = java.util.Collections.newSetFromMap(new IdentityHashMap<>());

    // ==================== Q7: Ability Dispatch Helpers ====================

    /**
     * Dispatch an ability lifecycle callback to all abilities on an entity.
     * Centralizes the defensive-copy + null-check + try/catch pattern.
     * Uses LOGGER.error for exceptions (Q8: genuine errors, not recoverable).
     */
    private static void dispatchAbilityTick(LivingEntity entity, EliteData data) {
        for (Map.Entry<String, Integer> entry : new ArrayList<>(data.getAbilities().entrySet())) {
            Ability ability = AbilityRegistry.getAbility(entry.getKey());
            if (ability == null) continue;
            // Supreme passive: +level to all other abilities (capped at V). Use the
            // data-variant to avoid a redundant capability lookup per ability per tick.
            int effectiveLevel = com.eliteforge.ability.legendary.AbilitySupreme.getEffectiveLevelForData(data, ability, entry.getValue());
            try {
                ability.onTick(entity, effectiveLevel);
            } catch (Exception e) {
                LOGGER.error("Error in ability onTick for {}: {}", ability.getIdString(), e.getMessage(), e);
            }
        }
        // Also tick the active mutated ability (if any) so reactive abilities gained
        // via Mutation work — the mutated ability lives in NBT, not the ability map.
        // Skip if the entity's own Mutation onTick already ran (it ticks the mutated
        // ability itself), to avoid double-ticking.
        if (!data.hasAbility("eliteforge:mutation")) {
            dispatchMutatedAbilityTick(entity);
        }
    }

    private static void dispatchAbilityHurt(LivingEntity entity, EliteData data, float amount) {
        for (Map.Entry<String, Integer> entry : new ArrayList<>(data.getAbilities().entrySet())) {
            Ability ability = AbilityRegistry.getAbility(entry.getKey());
            if (ability == null) continue;
            int effectiveLevel = com.eliteforge.ability.legendary.AbilitySupreme.getEffectiveLevelForData(data, ability, entry.getValue());
            try {
                ability.onHurt(entity, amount, effectiveLevel);
            } catch (Exception e) {
                LOGGER.error("Error in ability onHurt for {}: {}", ability.getIdString(), e.getMessage(), e);
            }
        }
        dispatchMutatedAbilityHurt(entity, amount);
    }

    private static void dispatchAbilityAttack(LivingEntity attacker, EliteData data, LivingEntity target, float damage) {
        for (Map.Entry<String, Integer> entry : new ArrayList<>(data.getAbilities().entrySet())) {
            Ability ability = AbilityRegistry.getAbility(entry.getKey());
            if (ability == null) continue;
            int effectiveLevel = com.eliteforge.ability.legendary.AbilitySupreme.getEffectiveLevelForData(data, ability, entry.getValue());
            try {
                ability.onAttack(attacker, target, damage, effectiveLevel);
            } catch (Exception e) {
                LOGGER.error("Error in ability onAttack for {}: {}", ability.getIdString(), e.getMessage(), e);
            }
        }
        dispatchMutatedAbilityAttack(attacker, target, damage);
    }

    private static void dispatchAbilityDeath(LivingEntity entity, EliteData data) {
        for (Map.Entry<String, Integer> entry : new ArrayList<>(data.getAbilities().entrySet())) {
            Ability ability = AbilityRegistry.getAbility(entry.getKey());
            if (ability == null) continue;
            int effectiveLevel = com.eliteforge.ability.legendary.AbilitySupreme.getEffectiveLevelForData(data, ability, entry.getValue());
            try {
                ability.onDeath(entity, effectiveLevel);
            } catch (Exception e) {
                LOGGER.error("Error in ability onDeath for {}: {}", ability.getIdString(), e.getMessage(), e);
            }
        }
        dispatchMutatedAbilityDeath(entity);
    }

    private static void dispatchAbilityPlayerKill(LivingEntity entity, EliteData data, ServerPlayer killer) {
        for (Map.Entry<String, Integer> entry : new ArrayList<>(data.getAbilities().entrySet())) {
            Ability ability = AbilityRegistry.getAbility(entry.getKey());
            if (ability == null) continue;
            int effectiveLevel = com.eliteforge.ability.legendary.AbilitySupreme.getEffectiveLevelForData(data, ability, entry.getValue());
            try {
                ability.onPlayerKill(entity, killer, effectiveLevel);
            } catch (Exception e) {
                LOGGER.error("Error in ability onPlayerKill for {}: {}", ability.getIdString(), e.getMessage(), e);
            }
        }
    }

    /**
     * If the entity has an active Mutation (AbilityMutation), also dispatch the
     * event to the mutated ability. The mutated ability is NOT in the capability
     * ability map (Mutation stores it in NBT instead), so the regular dispatch
     * helpers skip it — reactive abilities gained via Mutation (Reflect, Thorns,
     * Evade, Shield, Absorption, IronWall, Phase, Doom, ...) would otherwise be
     * completely useless. This helper closes that gap.
     */
    private static void dispatchMutatedAbilityTick(LivingEntity entity) {
        net.minecraft.nbt.CompoundTag nbt = entity.getPersistentData();
        if (!nbt.getBoolean(NBTKeys.MUTATION_ACTIVE)) return;
        String mutatedId = nbt.getString(NBTKeys.MUTATION_ABILITY);
        int mutatedLevel = nbt.getInt(NBTKeys.MUTATION_LEVEL);
        if (mutatedId.isEmpty() || mutatedLevel <= 0) return;
        Ability ability = AbilityRegistry.getAbility(mutatedId);
        if (ability == null) return;
        int effectiveLevel = com.eliteforge.ability.legendary.AbilitySupreme.getEffectiveLevelForEntity(entity, ability, mutatedLevel);
        try {
            ability.onTick(entity, effectiveLevel);
        } catch (Exception e) {
            LOGGER.error("Error in mutated ability onTick for {}: {}", ability.getIdString(), e.getMessage(), e);
        }
    }

    private static void dispatchMutatedAbilityHurt(LivingEntity entity, float amount) {
        net.minecraft.nbt.CompoundTag nbt = entity.getPersistentData();
        if (!nbt.getBoolean(NBTKeys.MUTATION_ACTIVE)) return;
        String mutatedId = nbt.getString(NBTKeys.MUTATION_ABILITY);
        int mutatedLevel = nbt.getInt(NBTKeys.MUTATION_LEVEL);
        if (mutatedId.isEmpty() || mutatedLevel <= 0) return;
        Ability ability = AbilityRegistry.getAbility(mutatedId);
        if (ability == null) return;
        int effectiveLevel = com.eliteforge.ability.legendary.AbilitySupreme.getEffectiveLevelForEntity(entity, ability, mutatedLevel);
        try {
            ability.onHurt(entity, amount, effectiveLevel);
        } catch (Exception e) {
            LOGGER.error("Error in mutated ability onHurt for {}: {}", ability.getIdString(), e.getMessage(), e);
        }
    }

    private static void dispatchMutatedAbilityAttack(LivingEntity attacker, LivingEntity target, float damage) {
        net.minecraft.nbt.CompoundTag nbt = attacker.getPersistentData();
        if (!nbt.getBoolean(NBTKeys.MUTATION_ACTIVE)) return;
        String mutatedId = nbt.getString(NBTKeys.MUTATION_ABILITY);
        int mutatedLevel = nbt.getInt(NBTKeys.MUTATION_LEVEL);
        if (mutatedId.isEmpty() || mutatedLevel <= 0) return;
        Ability ability = AbilityRegistry.getAbility(mutatedId);
        if (ability == null) return;
        int effectiveLevel = com.eliteforge.ability.legendary.AbilitySupreme.getEffectiveLevelForEntity(attacker, ability, mutatedLevel);
        try {
            ability.onAttack(attacker, target, damage, effectiveLevel);
        } catch (Exception e) {
            LOGGER.error("Error in mutated ability onAttack for {}: {}", ability.getIdString(), e.getMessage(), e);
        }
    }

    private static void dispatchMutatedAbilityDeath(LivingEntity entity) {
        net.minecraft.nbt.CompoundTag nbt = entity.getPersistentData();
        if (!nbt.getBoolean(NBTKeys.MUTATION_ACTIVE)) return;
        String mutatedId = nbt.getString(NBTKeys.MUTATION_ABILITY);
        int mutatedLevel = nbt.getInt(NBTKeys.MUTATION_LEVEL);
        if (mutatedId.isEmpty() || mutatedLevel <= 0) return;
        Ability ability = AbilityRegistry.getAbility(mutatedId);
        if (ability == null) return;
        int effectiveLevel = com.eliteforge.ability.legendary.AbilitySupreme.getEffectiveLevelForEntity(entity, ability, mutatedLevel);
        try {
            ability.onDeath(entity, effectiveLevel);
        } catch (Exception e) {
            LOGGER.error("Error in mutated ability onDeath for {}: {}", ability.getIdString(), e.getMessage(), e);
        }
    }

    /**
     * Register a living entity as an elite in the tracking set.
     * <p>
     * Call this whenever a new elite is created outside the normal spawn flow
     * (e.g., via Bestowal bestowing, Revenge squad spawning, or Ecosystem nurturing)
     * so that its abilities are ticked on the very next tick instead of waiting
     * for the periodic full scan (every 20 ticks / 1 second).
     * <p>
     * Safe to call even if the entity is already tracked (no-op in that case),
     * since the backing set uses identity-based comparison ({@link IdentityHashMap}).
     * Also safe to call on the client side — the method returns immediately
     * without modifying the set.
     *
     * @param entity the elite entity to track; must not be null
     */
    public static void trackElite(LivingEntity entity) {
        if (entity != null && !entity.level().isClientSide()) {
            TRACKED_ELITES.add(entity);
        }
    }

    /**
     * Register a summoned minion with the leash system so that
     * {@link #tickSummonLeashes} pulls it back toward its owner when it wanders
     * too far. Safe to call on the client side (no-op) and safe to call
     * repeatedly (identity-set, no duplicates).
     * <p>
     * Call this right after spawning the summon and writing its
     * {@link NBTKeys#SUMMONER_UUID} link — see
     * {@link com.eliteforge.ability.legendary.AbilityNecromancy#linkSummon}.
     *
     * @param entity the summoned minion to leash-track; must not be null
     */
    public static void trackSummon(LivingEntity entity) {
        if (entity != null && !entity.level().isClientSide()) {
            TRACKED_SUMMONS.add(entity);
        }
    }

    /**
     * Handle EntityJoinLevelEvent - delegate to EliteSpawnHandler.
     *
     * @param event the entity join level event
     */
    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        SPAWN_HANDLER.onEntityJoinLevel(event);

        // Track newly converted elites immediately so their abilities
        // are ticked on the very next tick without waiting for the periodic scan.
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof LivingEntity living) {
            living.getCapability(EliteCapability.CAPABILITY).ifPresent(cap -> {
                if (cap.isElite()) {
                    TRACKED_ELITES.add(living);
                }
            });
        }
    }

    /**
     * Handle PlayerEvent.StartTracking - sync capability data to tracking players.
     *
     * @param event the start tracking event
     */
    @SubscribeEvent
    public static void onPlayerStartTracking(PlayerEvent.StartTracking event) {
        EliteCapabilitySync.onPlayerStartTracking(event);
    }

    /**
     * Handle LivingHurtEvent - dispatch to abilities' onHurt callback,
     * apply HUNTER set bonus damage multiplier, and track engagement.
     * <p>
     * B5: Consolidated to a single capability lookup on the hurt entity
     * (previously 2 lookups: main handler + HUNTER set bonus). The attacker
     * lookup remains separate because it is a different entity.
     *
     * @param event the living hurt event
     */
    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        LivingEntity entity = event.getEntity();
        final LivingEntity sourceEntity = event.getSource().getEntity() instanceof LivingEntity le ? le : null;
        final ServerPlayer attackerPlayer = sourceEntity instanceof ServerPlayer sp ? sp : null;

        // ===== HURT ENTITY PROCESSING (single capability lookup) =====
        // Check if the hurt entity is elite — if so, dispatch onHurt, apply
        // defensive abilities, track engagement, apply enchantments, AND apply
        // the HUNTER set bonus all in one pass.
        entity.getCapability(EliteCapability.CAPABILITY).ifPresent(cap -> {
            if (!cap.isElite()) return;

            EliteData data = cap.getEliteData();
            if (data == null) return; // Guard against null data

            // ===== REINCARNATION REVIVAL PROTECTION =====
            // If this entity is currently reviving (Reincarnation ability), cancel all damage
            // to prevent death during the revival period where the entity only has 1.0 HP.
            if (entity.getPersistentData().getBoolean(NBTKeys.REINCARNATION_REVIVING)) {
                event.setAmount(0);
                event.setCanceled(true);
                return;
            }

            // Track engagement: if a player damages this elite
            if (attackerPlayer != null) {
                if (data.getEngagedPlayerUUID() == null) {
                    data.engage(attackerPlayer.getUUID());
                    // Cancel despawn timer on engagement
                    data.setDespawnTimer(0);
                    LOGGER.debug("Elite {} engaged by player {}",
                            entity.getName().getString(), attackerPlayer.getName().getString());
                }

                // ===== ELITE BANE ENCHANTMENT =====
                // Apply Elite Bane damage bonus (+20% per level) when a player with this
                // enchantment on their main-hand weapon hits an elite.
                ItemStack weapon = attackerPlayer.getMainHandItem();
                int eliteBaneLevel = weapon.getEnchantmentLevel(
                        com.eliteforge.init.ModEnchantments.ELITE_BANE.get());
                if (eliteBaneLevel > 0) {
                    float multiplier = com.eliteforge.enchantment.EliteBaneEnchantment
                            .getEliteDamageMultiplier(eliteBaneLevel, entity);
                    if (multiplier > 1.0f) {
                        event.setAmount(event.getAmount() * multiplier);
                    }
                }

                // ===== PURIFYING TOUCH ENCHANTMENT =====
                // Chance to remove a random non-creator ability from the elite on hit.
                int purifyLevel = weapon.getEnchantmentLevel(
                        com.eliteforge.init.ModEnchantments.PURIFYING_TOUCH.get());
                if (purifyLevel > 0) {
                    com.eliteforge.enchantment.PurifyingTouchEnchantment.tryPurify(entity, purifyLevel);
                }
            }

            // Apply Iron Wall (铁壁) damage reduction
            if (data.hasAbility("eliteforge:iron_wall")) {
                int ironWallLevel = data.getAbilityLevel("eliteforge:iron_wall");
                // Cap reduction at 0.95 to prevent healing on hit at very high levels.
                // At level 5: 0.10 + 5*0.08 = 0.50 (50%) — balanced.
                // Without cap, level 12+ would exceed 1.0 (healing on hit).
                float reduction = Math.min(0.95f, 0.10f + ironWallLevel * 0.08f);
                event.setAmount(event.getAmount() * (1.0f - reduction));
            }

            // Apply Evade (闪避) dodge chance
            if (data.hasAbility("eliteforge:evade")) {
                int evadeLevel = data.getAbilityLevel("eliteforge:evade");
                // Cap dodge chance at 0.95 to prevent 100% invulnerability at very high levels.
                // At level 5: 0.05 + 5*0.07 = 0.40 (40%) — balanced.
                // Without cap, level 14+ would always dodge.
                float dodgeChance = Math.min(0.95f, 0.05f + evadeLevel * 0.07f);
                if (entity.getRandom().nextFloat() < dodgeChance) {
                    event.setAmount(0);
                    // Spawn dodge particles
                    if (entity.level() instanceof ServerLevel serverLevel) {
                        serverLevel.sendParticles(ParticleTypes.CLOUD,
                                entity.getX(), entity.getY() + entity.getBbHeight() * 0.5, entity.getZ(),
                                8, 0.3, 0.3, 0.3, 0.02);
                    }
                    return;
                }
            }

            // Dispatch to each ability's onHurt via centralized helper (Q7)
            dispatchAbilityHurt(entity, data, event.getAmount());

            // SetBonus (HUNTER damage multiplier) was removed in v0.2.0 phase 1.
        });

        // ===== ATTACKER PROCESSING (separate entity, separate lookup) =====
        // Check if the attacker is elite — dispatch onAttack and apply synergy bonus.
        if (sourceEntity != null) {
            sourceEntity.getCapability(EliteCapability.CAPABILITY).ifPresent(cap -> {
                if (!cap.isElite()) return;

                EliteData data = cap.getEliteData();

                // Dispatch to each ability's onAttack via centralized helper (Q7)
                dispatchAbilityAttack(sourceEntity, data, entity, event.getAmount());

                // Apply combined synergy damage bonus for the attacker's active synergies.
                // This wires the synergy system into gameplay: when an elite has two abilities
                // that have a defined synergy (e.g. Fire + SpiritBurn = "Inferno"), its outgoing
                // damage is boosted by the combined synergy bonus (capped at +50%).
                float synergyBonus = com.eliteforge.ability.AbilityInteraction.getCombinedSynergyBonus(sourceEntity);
                if (synergyBonus > 1.0f) {
                    event.setAmount(event.getAmount() * synergyBonus);
                }
            });
        }

        // SetBonus (GUARDIAN damage reduction) was removed in v0.2.0 phase 1;
        // the block that applied it here is gone.
    }

    /**
     * Handle LivingDeathEvent - handle elite death with full reward system.
     * <p>
     * When a player kills an elite:
     * - Show death animation (particles + sound)
     * - Display kill message in action bar
     * - Drop quality-appropriate loot
     * - Update chunk heat with kill bonus
     * - Grant player experience
     * - Check for advancements/achievements
     * <p>
     * Also handles player death to elite (difficulty fatigue).
     *
     * @param event the living death event
     */
    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity entity = event.getEntity();

        // Check if player died to an elite - apply difficulty fatigue
        if (entity instanceof ServerPlayer deadPlayer) {
            DamageSource source = event.getSource();
            if (source.getEntity() instanceof LivingEntity killer) {
                killer.getCapability(EliteCapability.CAPABILITY).ifPresent(cap -> {
                    if (cap.isElite()) {
                        EliteData killerData = cap.getEliteData();
                        // Increment the elite's player-kill count (used for awakening eligibility).
                        // This tracks how many PLAYERS this elite has killed, NOT how many times
                        // the elite itself has died. The awakening system requires >= 2 player kills.
                        killerData.setKillCount(killerData.getKillCount() + 1);
                        killerData.setLastKillTime(System.currentTimeMillis());
                        cap.setEliteData(killerData);

                        // Apply difficulty fatigue: reduce nearby heat by 5
                        if (deadPlayer.level() instanceof ServerLevel serverLevel) {
                            DifficultyManager.INSTANCE.applyDifficultyFatigue(serverLevel, deadPlayer.blockPosition());
                            // Elite-killed-player: trigger the kill-strengthening buff on the killer
                            // (heals 20%, +5% damage for 30s, capped at +25%). This was previously
                            // a dead method — never called from anywhere.
                            try {
                                com.eliteforge.spawn.DynamicStrengthening.onEliteKillPlayer(killer);
                            } catch (Exception e) {
                                LOGGER.error("Error in DynamicStrengthening.onEliteKillPlayer: {}", e.getMessage());
                            }
                            LOGGER.debug("Difficulty fatigue applied: player {} died to elite {} (elite kill count: {})",
                                    deadPlayer.getName().getString(), killer.getName().getString(),
                                    killerData.getKillCount());
                        }
                    }
                });
            }
        }

        entity.getCapability(EliteCapability.CAPABILITY).ifPresent(cap -> {
            if (!cap.isElite()) return;

            EliteData data = cap.getEliteData();
            if (data == null) return; // Guard against null data
            DamageSource source = event.getSource();

            // ===== REINCARNATION DEATH PREVENTION =====
            // If this entity has the Reincarnation creator ability and has remaining
            // rebirths, prevent death and trigger the revival process directly.
            // We check the remaining rebirth count from NBT (set by AbilityReincarnation.onApply)
            // rather than relying on a flag set during onDeath (which runs too late).
            if (data.isCreatorEntity() && "eliteforge:creator_reincarnation".equals(data.getCreatorAbilityId())) {
                net.minecraft.nbt.CompoundTag entityNbt = entity.getPersistentData();
                int remainingRebirths = entityNbt.getInt(NBTKeys.REINCARNATION_REMAINING);
                if (remainingRebirths > 0) {
                    // Cancel death and start revival process
                    event.setCanceled(true);
                    entity.setHealth(1.0f); // Keep alive with minimal health
                    entity.setInvulnerable(true); // Protect from damage during revival period

                    // H3 fix: call onDeath BEFORE decrementing remainingRebirths.
                    // Previously, remainingRebirths was decremented first, so onDeath
                    // saw remaining==0 and dropped the Reincarnation Crystal — but the
                    // entity was reviving, not dying. The crystal would drop again on
                    // the entity's actual final death, causing duplicate drops.
                    // Now: onDeath sees remaining > 0, does particle effects only,
                    // and does NOT drop the crystal. The crystal drops only when the
                    // entity truly dies (remaining == 0, no revival) via the normal
                    // dispatchAbilityDeath path below.
                    int creatorLevel = data.getCreatorAbilityLevel();

                    // Call the Reincarnation ability's onDeath for revival effects (particles)
                    Ability reincarnationAbility = AbilityRegistry.getAbility("eliteforge:creator_reincarnation");
                    if (reincarnationAbility != null) {
                        try {
                            reincarnationAbility.onDeath(entity, creatorLevel);
                        } catch (Exception e) {
                            LOGGER.error("Error in Reincarnation onDeath: {}", e.getMessage());
                        }
                    }

                    // Now decrement remaining count AFTER onDeath has run
                    entityNbt.putInt(NBTKeys.REINCARNATION_REMAINING, remainingRebirths - 1);
                    data.setReincarnationRemaining(remainingRebirths - 1);
                    cap.setEliteData(data);

                    // Start revival delay timer
                    int delayTicks = switch (creatorLevel) {
                        case 1 -> 60;   // 3 seconds
                        case 2 -> 40;   // 2 seconds
                        default -> 20;  // 1 second
                    };
                    entityNbt.putBoolean(NBTKeys.REINCARNATION_REVIVING, true);
                    entityNbt.putInt(NBTKeys.REINCARNATION_REVIVE_TIMER, delayTicks);
                    entityNbt.putInt(NBTKeys.REINCARNATION_INVULN, delayTicks); // Track invulnerability for cleanup

                    EliteCapabilitySync.broadcastEliteDataUpdate(entity, data);

                    LOGGER.debug("Prevented death for elite {} with Reincarnation ability (remaining: {})",
                            entity.getName().getString(), remainingRebirths - 1);
                    return;
                }
            }

            // ===== BERSERK REVIVAL CHECK =====
            // If the elite has the Berserk (涅槃) ability and hasn't revived yet,
            // cancel the death event and heal the entity instead.
            if (data.hasAbility("eliteforge:berserk") && !entity.getPersistentData().getBoolean(NBTKeys.BERSERK_REVIVED)) {
                int berserkLevel = data.getAbilityLevel("eliteforge:berserk");
                float revivalHealthPercent = 0.10f + berserkLevel * 0.10f;
                float revivalHealth = entity.getMaxHealth() * revivalHealthPercent;

                // Mark as revived to prevent infinite revival
                entity.getPersistentData().putBoolean(NBTKeys.BERSERK_REVIVED, true);

                // Cancel the death event
                event.setCanceled(true);

                // Heal the entity
                entity.setHealth(revivalHealth);

                // Apply revival buffs (Speed II + Strength II for 10 seconds)
                entity.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                        net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED, 200, 1, false, true));
                entity.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                        net.minecraft.world.effect.MobEffects.DAMAGE_BOOST, 200, 1, false, true));

                // Play revival effects
                if (entity.level() instanceof ServerLevel serverLevel) {
                    serverLevel.sendParticles(ParticleTypes.TOTEM_OF_UNDYING,
                            entity.getX(), entity.getY() + entity.getBbHeight() * 0.5, entity.getZ(),
                            40, 0.5, 0.5, 0.5, 0.3);
                    serverLevel.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                            net.minecraft.sounds.SoundEvents.TOTEM_USE, net.minecraft.sounds.SoundSource.HOSTILE, 1.0f, 1.0f);
                }

                LOGGER.debug("Elite {} revived via Berserk ability at {}% health",
                        entity.getName().getString(), (int)(revivalHealthPercent * 100));
                return;
            }

            // Dispatch to each ability's onDeath via centralized helper (Q7)
            dispatchAbilityDeath(entity, data);

            // If killed by a player, handle special logic
            if (source.getEntity() instanceof ServerPlayer killer) {
                // Call onPlayerKill for each ability via centralized helper (Q7)
                dispatchAbilityPlayerKill(entity, data, killer);

                // ===== ELITE DEATH REWARDS =====

                // 1. Show death animation (particles + sound)
                if (entity.level() instanceof ServerLevel serverLevel) {
                    showDeathAnimation(serverLevel, entity, data.getQualityTier());
                }

                // 2. Display kill message in action bar (configurable)
                if (EliteForgeConfig.SERVER.enableKillMessage.get()) {
                    showKillMessage(killer, entity, data);
                }

                // 3. Update chunk heat with kill bonus (applies Heat Shield enchantment reduction)
                if (entity.level() instanceof ServerLevel serverLevel) {
                    ChunkHeatManager heatManager = ChunkHeatManager.get(serverLevel);
                    heatManager.onEliteKill(serverLevel, entity.blockPosition(), killer);

                    // 4. Grant player experience
                    PlayerExperienceManager expManager = PlayerExperienceManager.get(serverLevel);
                    expManager.onEliteKill(killer);
                }

                // 5. Update anti-farm tracking
                // NOTE: killCount is now incremented in the player-death handler above
                // (when an elite kills a player), NOT when the elite itself dies.
                // This is required for the awakening system to function correctly.
                data.setLastKillTime(System.currentTimeMillis());

                LOGGER.debug("Elite {} killed by player {}",
                        entity.getName().getString(), killer.getName().getString());

                // 6. Check for advancements/achievements (placeholder for future)
                checkAchievements(killer, data);

                // 6b. Show a client-side toast notification to the killer.
                // Uses DistExecutor.unsafeRunWhenOn so the client-only Toast class is never
                // loaded on a dedicated server.
                final String eliteNameStr = entity.getName().getString();
                final QualityTier eliteQuality = data.getQualityTier();
                final int eliteLvl = data.getLevel();
                net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                        () -> () -> com.eliteforge.client.toast.EliteDeathToastHandler.showToast(
                                eliteNameStr, eliteQuality, eliteLvl));

                // 7. Drop loot — the v0.2.0 material + modded-item loot system. Previously
                // generateLoot/dropLoot had ZERO callers, so elites dropped nothing beyond
                // vanilla. Now invoked for every player-killed elite.
                if (entity.level() instanceof ServerLevel serverLevel) {
                    try {
                        java.util.List<net.minecraft.world.item.ItemStack> loot =
                                com.eliteforge.quality.LootHandler.generateLoot(entity, data, killer);
                        com.eliteforge.quality.LootHandler.dropLoot(serverLevel, entity, loot);
                    } catch (Exception e) {
                        LOGGER.error("Error dropping elite loot for {}: {}", entity.getName().getString(), e.getMessage());
                    }
                }
            }

            // ===== CREATOR-TIER DEATH HANDLING =====
            // Unregister from ecosystem if this was a creator entity
            if (data.isCreatorEntity() && entity.level() instanceof ServerLevel serverLevel) {
                EliteEcosystem.unregisterCreator(entity);

                // Clean up all creator-related NBT keys on death
                net.minecraft.nbt.CompoundTag entityNbt = entity.getPersistentData();
                // Nexus
                entityNbt.remove(NBTKeys.NEXUS_COOLDOWN);
                entityNbt.remove(NBTKeys.NEXUS_ACTIVE);
                // Dominion
                entityNbt.remove(NBTKeys.DOMINION_ACTIVE);
                entityNbt.remove(NBTKeys.DOMINION_TIMER);
                entityNbt.remove(NBTKeys.DOMINION_COOLDOWN);
                // Evolution
                entityNbt.remove(NBTKeys.EVOLUTION_COUNT);
                entityNbt.remove(NBTKeys.EVOLUTION_DAMAGE_ACCUM);
                entityNbt.remove(NBTKeys.EVOLUTION_APPLIED);
                // Annihilate
                entityNbt.remove(NBTKeys.ANNIHILATE_WARNING);
                entityNbt.remove(NBTKeys.ANNIHILATE_WARNING_TICKS);
                entityNbt.remove(NBTKeys.ANNIHILATE_TRIGGERED);
                entityNbt.remove(NBTKeys.ANNIHILATE_KILLER_UUID);
                entityNbt.remove(NBTKeys.ANNIHILATE_CHAIN_EXPLOSION);
                // Reincarnation
                entityNbt.remove(NBTKeys.REINCARNATION_REMAINING);
                entityNbt.remove(NBTKeys.REINCARNATION_COUNT);
                entityNbt.remove(NBTKeys.REINCARNATION_REVIVING);
                entityNbt.remove(NBTKeys.REINCARNATION_REVIVE_TIMER);
                entityNbt.remove(NBTKeys.REINCARNATION_INVULN);
                entityNbt.remove(NBTKeys.REINCARNATION_STORED_LEVEL);
                // Commander
                entityNbt.remove(NBTKeys.COMMANDER_COOLDOWN);
                entityNbt.remove(NBTKeys.COMMANDER_SQUAD);
                // Bestowal
                entityNbt.remove(NBTKeys.BESTOWAL_COOLDOWN);
                // Assimilate
                entityNbt.remove(NBTKeys.ASSIMILATE_COOLDOWN);
                entityNbt.remove(NBTKeys.ASSIMILATE_INVULN);
                entityNbt.remove(NBTKeys.ASSIMILATE_APPLIED_COUNT);

                LOGGER.info("Creator-tier elite {} has been slain, NBT cleaned up", entity.getName().getString());
            }

            // ===== REVENGE SYSTEM: Track elite kills per chunk =====
            if (entity.level() instanceof ServerLevel serverLevel
                    && EliteForgeConfig.SERVER.enableRevengeSystem.get()) {
                EliteRevenge.onEliteKill(entity.blockPosition(), serverLevel);
            }

            // ===== ASSIMILATION: Notify nearby creators with C4 Assimilate =====
            if (entity.level() instanceof ServerLevel serverLevel) {
                com.eliteforge.ability.creator.AbilityAssimilate.onNearbyEliteDeath(entity, serverLevel);
            }

            // Remove from tracking set
            TRACKED_ELITES.remove(entity);
            // Also drop from the summon tracker (summons that happen to be elites, e.g.
            // Clone clones, are in both sets). When enableSummonLeash is false the leash
            // tick never runs, so dead summons would otherwise linger here for up to 200 ticks.
            TRACKED_SUMMONS.remove(entity);

            // Remove client-side data
            if (entity.level() instanceof ServerLevel) {
                EliteCapabilitySync.broadcastEliteRemoval(entity);
            }
        });

        // Safety: always remove dead entities from tracking set, even if the
        // capability lookup above failed (e.g., capability removed before death).
        // This prevents memory leaks from orphaned entries in TRACKED_ELITES.
        // Guard with isCanceled: if Reincarnation/Berserk canceled the death event,
        // the entity is still alive and must stay tracked so its abilities keep ticking.
        if (!event.isCanceled()) {
            TRACKED_ELITES.remove(entity);
            TRACKED_SUMMONS.remove(entity);
        } else {
            // Revived entity: re-track to be safe (the lambda above may have removed it).
            TRACKED_ELITES.add(entity);
        }
    }

    /**
     * Show death animation for an elite mob (particles + sound).
     *
     * @param level  the server level
     * @param entity the elite entity
     * @param quality the quality tier
     */
    private static void showDeathAnimation(ServerLevel level, LivingEntity entity, QualityTier quality) {
        double x = entity.getX();
        double y = entity.getY() + entity.getBbHeight() * 0.5;
        double z = entity.getZ();

        // Explosion of particles based on quality tier
        int particleCount = switch (quality) {
            case MYTHIC -> 60;
            case LEGENDARY -> 45;
            case EPIC -> 35;
            case FINE -> 25;
            case GOOD -> 15;
            default -> 15;
        };

        // Quality-specific particles
        switch (quality) {
            case MYTHIC -> {
                level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, x, y, z, particleCount, 1.5, 1.5, 1.5, 0.5);
                level.sendParticles(ParticleTypes.DRAGON_BREATH, x, y, z, particleCount / 2, 1.0, 1.0, 1.0, 0.2);
                level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, x, y, z, particleCount / 3, 0.8, 0.8, 0.8, 0.15);
            }
            case LEGENDARY -> {
                level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, x, y, z, particleCount, 1.0, 1.0, 1.0, 0.5);
                level.sendParticles(ParticleTypes.DRAGON_BREATH, x, y, z, particleCount / 2, 0.5, 0.5, 0.5, 0.1);
            }
            case EPIC -> {
                level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, x, y, z, particleCount, 0.8, 0.8, 0.8, 0.3);
                level.sendParticles(ParticleTypes.WITCH, x, y, z, particleCount / 2, 0.5, 0.5, 0.5, 0.1);
            }
            case FINE -> {
                level.sendParticles(ParticleTypes.END_ROD, x, y, z, particleCount, 0.5, 0.5, 0.5, 0.2);
                level.sendParticles(ParticleTypes.ENCHANT, x, y, z, particleCount / 2, 0.5, 0.5, 0.5, 0.3);
            }
            default -> {
                level.sendParticles(ParticleTypes.POOF, x, y, z, particleCount, 0.5, 0.5, 0.5, 0.1);
            }
        }

        // Death sound — play the mod's elite_death sound (registered in ModSounds, defined
        // in sounds.json). Previously only vanilla bell/warden sounds played.
        level.playSound(null, x, y, z,
                com.eliteforge.init.ModSounds.ELITE_DEATH.get(),
                SoundSource.HOSTILE,
                1.0f, quality == QualityTier.MYTHIC ? 0.5f : quality == QualityTier.LEGENDARY ? 0.7f : 1.0f);
    }

    /**
     * Show kill message in the player's action bar.
     * C1: Now uses translatable key "message.eliteforge.kill" with parameters
     * (qualityColor, qualityName, level, entityName) instead of hardcoded
     * Chinese/English literal string. Format string in en_us:
     *   "✦ Defeated %s %s Lv.%d Elite %s ✦"
     * and in zh_cn:
     *   "✦ 击杀了 %s %s Lv.%d精英 %s ✦"
     *
     * @param killer the killing player
     * @param entity the killed elite entity
     * @param data   the elite's data
     */
    private static void showKillMessage(ServerPlayer killer, LivingEntity entity, EliteData data) {
        QualityTier quality = data.getQualityTier();
        int level = data.getLevel();
        String entityName = entity.getName().getString();

        // C1: Use Component.translatable with arguments. The translation string
        // contains %s/%d placeholders filled positionally by the args.
        // The first %s receives the quality display name, the second %s receives
        // the quality display name again (for color context), %d receives the
        // level, and the final %s receives the entity name.
        // We build a literal-style message using translatable for the format string.
        MutableComponent message = Component.translatable("message.eliteforge.kill",
                Component.literal(quality.getDisplayName()).withStyle(quality.getChatColor()),
                Component.literal(quality.getDisplayName()).withStyle(quality.getChatColor()),
                level,
                Component.literal(entityName).withStyle(quality.getChatColor())
        ).withStyle(ChatFormatting.GOLD);

        killer.sendSystemMessage(message);
    }

    /**
     * Check for advancements/achievements when a player kills an elite.
     * Tracks total elite kills per player using the player's persistent NBT data,
     * rather than using the elite's own kill count (which tracks the elite's kills, not the player's).
     * Placeholder for future achievement system integration.
     *
     * @param player the killing player
     * @param data   the elite's data
     */
    private static void checkAchievements(ServerPlayer player, EliteData data) {
        // Track total elite kills on the player's NBT data
        net.minecraft.nbt.CompoundTag playerData = player.getPersistentData();
        int totalEliteKills = playerData.getInt(NBTKeys.PLAYER_ELITE_KILLS) + 1;
        playerData.putInt(NBTKeys.PLAYER_ELITE_KILLS, totalEliteKills);

        // First elite kill
        if (totalEliteKills == 1) {
            EliteForge.LOGGER.debug("Player {} achieved first elite kill", player.getName().getString());
        }

        // Legendary kill
        if (data.getQualityTier() == QualityTier.LEGENDARY) {
            playerData.putInt(NBTKeys.PLAYER_LEGENDARY_KILLS, playerData.getInt(NBTKeys.PLAYER_LEGENDARY_KILLS) + 1);
            EliteForge.LOGGER.debug("Player {} killed a legendary elite", player.getName().getString());
        }

        // Mythic kill
        if (data.getQualityTier() == QualityTier.MYTHIC) {
            playerData.putInt(NBTKeys.PLAYER_MYTHIC_KILLS, playerData.getInt(NBTKeys.PLAYER_MYTHIC_KILLS) + 1);
            EliteForge.LOGGER.debug("Player {} killed a mythic elite", player.getName().getString());
        }

        // High level kill
        if (data.getLevel() >= 5) {
            playerData.putInt(NBTKeys.PLAYER_HIGH_LEVEL_KILLS, playerData.getInt(NBTKeys.PLAYER_HIGH_LEVEL_KILLS) + 1);
            EliteForge.LOGGER.debug("Player {} killed a level 5+ elite", player.getName().getString());
        }
    }

    /**
     * Handle EntityMobGriefingEvent - check if elite has destructive abilities.
     *
     * @param event the mob griefing event
     */
    @SubscribeEvent
    public static void onMobGriefing(EntityMobGriefingEvent event) {
        if (!(event.getEntity() instanceof LivingEntity livingEntity)) {
            return;
        }

        livingEntity.getCapability(EliteCapability.CAPABILITY).ifPresent(cap -> {
            if (!cap.isElite()) return;

            EliteData data = cap.getEliteData();

            // Check if elite has destructive abilities (explosion, fire, etc.)
            boolean hasDestructiveAbility = data.hasAbility("eliteforge:explosion")
                    || data.hasAbility("eliteforge:fire")
                    || data.hasAbility("eliteforge:storm");

            // Always deny mob griefing for elites with destructive abilities
            // to prevent terrain destruction, regardless of anti-farm settings
            if (hasDestructiveAbility) {
                event.setResult(Event.Result.DENY);
            }
        });
    }

    /**
     * Handle TickEvent.ServerTickEvent - tick chunk heat, player experience,
     * ability ticking (every 20 ticks), and CASUAL mode despawn checks.
     *
     * @param event the server tick event
     */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        tickCounter++;

        // Tick all server levels
        for (ServerLevel level : event.getServer().getAllLevels()) {
            // Tick chunk heat
            ChunkHeatManager heatManager = ChunkHeatManager.get(level);
            heatManager.tick(level);

            // Tick abilities on all elite entities every tick for accurate timing.
            // Abilities use internal cooldown counters that expect per-tick increments.
            tickEliteAbilities(level);

            // Every 20 ticks: batch process all 20-tick-interval tasks in a
            // single TRACKED_ELITES iteration (dynamic strengthening, awakening
            // checks, CASUAL despawn) plus separate calls for NBT timers,
            // revenge, and scorched earth zones.
            if (tickCounter % 20 == 0) {
                processEliteBatch(level);
                tickScorchedEarthZones(level);
            }

            // Update all creator positions in the ecosystem tracker every 100 ticks (5 seconds).
            // This ensures cached positions are fresh even when no one queries getNearbyCreators(),
            // and removes dead/removed creators from the tracker proactively.
            if (tickCounter % 300 == 0) {
                EliteEcosystem.tickAllCreators(level);
            }

            // Periodic deep cleanup of TRACKED_ELITES every 200 ticks (10 seconds).
            // Entities that despawn naturally (not through the death event) may linger
            // in the tracking set. The per-tick check in tickEliteAbilities catches
            // most cases, but this additional sweep ensures that any entities that
            // slipped through (e.g., unloaded chunks, dimension changes) are removed.
            if (tickCounter % 300 == 0) {
                cleanupTrackedElites();
            }
        }

        // Global (per-server, not per-level) ticks: PlayerExperienceManager and
        // EliteRevenge both use static state keyed globally, so ticking them once
        // per server tick — not once per loaded level — keeps decay rates at the
        // configured value on multi-dimension servers (previously N× faster).
        ServerLevel overworld = event.getServer().overworld();
        if (overworld != null) {
            PlayerExperienceManager.get(overworld).tick(overworld);
            if (tickCounter % 20 == 0 && EliteForgeConfig.SERVER.enableRevengeSystem.get()) {
                EliteRevenge.tickRevenge(overworld);
            }
        }

        // B4: Periodic cross-dimension creator cleanup every 6000 ticks (5 minutes).
        // Removes stale ACTIVE_CREATORS entries whose entities died in another
        // dimension without triggering the death event (e.g., chunk-unload despawn).
        if (tickCounter % 6000 == 0) {
            EliteEcosystem.cleanupStaleCrossDimensionCreators(event.getServer());
        }

        // LootHandler no longer maintains a kill-timestamp cache (removed with the
        // anti-farm rework); the periodic cleanup call was removed with it.
    }

    /**
     * Tick all abilities on elite entities in a server level.
     * <p>
     * Uses a tracking set ({@link #TRACKED_ELITES}) to avoid iterating ALL living
     * entities every tick. Only known elite entities are ticked each tick; a full
     * scan runs every 20 ticks to discover new or changed elites (e.g., from
     * chunk loading, awakening, or bestowal).
     *
     * @param level the server level
     */
    private static void tickEliteAbilities(ServerLevel level) {
        // Clean up dead or removed entities from the tracking set
        TRACKED_ELITES.removeIf(e -> !e.isAlive() || e.isRemoved() || e.level().isClientSide());

        // Periodic full cleanup every 100 ticks (5 seconds):
        // Remove stale entries that may have survived the basic checks above,
        // such as entities that changed dimensions, despawned without a death
        // event, or are no longer elite (e.g., after bestowal reversion).
        // This is more expensive (requires capability lookup) so runs less frequently.
        if (tickCounter % 300 == 0) {
            TRACKED_ELITES.removeIf(e -> {
                if (!e.isAlive() || e.isRemoved() || e.level().isClientSide()) return true;
                // Check if the entity is still elite; if capability is absent
                // or no longer elite, remove from tracking set
                var cap = e.getCapability(EliteCapability.CAPABILITY).orElse(null);
                return cap == null || !cap.isElite();
            });
        }

        // Periodic full scan to discover new elites (every 20 ticks / 1 second).
        // Also runs when the set is empty (e.g., after server restart).
        // This catches elites from chunk loading, awakening, bestowal, etc.
        if (tickCounter % 60 == 0 || TRACKED_ELITES.isEmpty()) {
            // Use level.getAllEntities() instead of AABB.INFINITE to avoid
            // performance issues with the spatial index. Iterating all loaded
            // entities is equivalent but doesn't stress the AABB lookup path.
            for (net.minecraft.world.entity.Entity entity : level.getAllEntities()) {
                if (!(entity instanceof LivingEntity living) || !living.isAlive()) continue;
                living.getCapability(EliteCapability.CAPABILITY).ifPresent(cap -> {
                    if (cap.isElite()) {
                        TRACKED_ELITES.add(living);
                    }
                });
            }
        }

        // Tick abilities for tracked elites in this level only.
        // CRITICAL: iterate a defensive copy. Ability onTick callbacks can re-enter
        // EliteEventHandler and mutate TRACKED_ELITES (Bestowal.onTick → trackElite,
        // Annihilate.onTick → entity.kill() → onLivingDeath → TRACKED_ELITES.remove,
        // Nexus.onTick → nurtureNearbyElites → trackElite). Iterating the live set
        // would throw ConcurrentModificationException. The copy cost is negligible
        // (50–1000 entity refs per tick).
        java.util.List<LivingEntity> toRemove = new java.util.ArrayList<>();
        for (LivingEntity living : new java.util.ArrayList<>(TRACKED_ELITES)) {
            if (!living.isAlive() || living.isRemoved() || living.level().isClientSide()) {
                toRemove.add(living);
                continue;
            }
            if (living.level() != level) continue;

            living.getCapability(EliteCapability.CAPABILITY).ifPresent(cap -> {
                if (!cap.isElite()) {
                    toRemove.add(living);
                    return;
                }

                EliteData data = cap.getEliteData();
                if (data == null) {
                    toRemove.add(living);
                    return;
                }

                // Dispatch ability onTick via centralized helper (Q7)
                try {
                    dispatchAbilityTick(living, data);
                } catch (Throwable t) {
                    // Swallow per-ability exceptions so one bad ability can't crash
                    // the per-tick loop for every other elite.
                    LOGGER.error("Error ticking abilities for {}: {}",
                            living.getName().getString(), t.getMessage());
                }
            });
        }

        // Remove dead/non-elite entities after iteration
        if (!toRemove.isEmpty()) {
            TRACKED_ELITES.removeAll(toRemove);
        }
    }

    /**
     * Deep cleanup of the TRACKED_ELITES set.
     * <p>
     * Runs every 200 ticks (10 seconds) to remove entries where the entity
     * is no longer alive or has been removed (e.g., natural despawn, dimension
     * change). This complements the per-tick cleanup in {@link #tickEliteAbilities}
     * which handles the common cases, and the 100-tick capability-based cleanup.
     * <p>
     * The primary goal is to catch entities that despawned naturally without
     * triggering a death event — such entities may not be caught by the per-tick
     * {@code isAlive()/isRemoved()} checks if they are unloaded before those
     * flags are set. This method ensures the set does not grow unbounded during
     * long play sessions.
     */
    private static void cleanupTrackedElites() {
        int removedCount = 0;
        for (LivingEntity entity : new ArrayList<>(TRACKED_ELITES)) {
            if (!entity.isAlive() || entity.isRemoved()) {
                TRACKED_ELITES.remove(entity);
                removedCount++;
            }
        }
        if (removedCount > 0) {
            LOGGER.debug("Periodic TRACKED_ELITES cleanup removed {} stale entries (remaining: {})",
                    removedCount, TRACKED_ELITES.size());
        }

        // Sweep TRACKED_SUMMONS at the same cadence (every 200 ticks) for the same
        // reason: summons that despawned via chunk-unload without a death event
        // can linger. tickSummonLeashes already evicts dead/removed entries inline,
        // but this belt-and-braces pass guards against entities unloaded before
        // those flags were set.
        int summonRemoved = 0;
        for (LivingEntity summon : new ArrayList<>(TRACKED_SUMMONS)) {
            if (!summon.isAlive() || summon.isRemoved()) {
                TRACKED_SUMMONS.remove(summon);
                summonRemoved++;
            }
        }
        if (summonRemoved > 0) {
            LOGGER.debug("Periodic TRACKED_SUMMONS cleanup removed {} stale entries (remaining: {})",
                    summonRemoved, TRACKED_SUMMONS.size());
        }
    }

    /**
     * Combined 20-tick processing for all elite entities.
     * Handles: dynamic strengthening, awakening checks, creator position updates,
     * CASUAL despawn, creator NBT timers, and revenge system.
     * <p>
     * Consolidates what were previously 4 separate iterations of TRACKED_ELITES
     * into a single pass, reducing per-tick overhead from O(4n) to O(n) for
     * the 20-tick interval processing.
     * <p>
     * P4: Uses Iterator pattern instead of `new ArrayList<>(TRACKED_ELITES)` to
     * avoid copying the entire set every 20 ticks. The IdentityHashMap-backed
     * set supports Iterator.remove() for inline cleanup.
     *
     * @param level the server level
     */
    private static void processEliteBatch(ServerLevel level) {
        boolean updateCreatorPositions = (tickCounter % 100 == 0);

        // P4: Iterator-based iteration avoids copying the entire set every 20 ticks.
        java.util.Iterator<LivingEntity> batchIter = TRACKED_ELITES.iterator();
        while (batchIter.hasNext()) {
            LivingEntity living = batchIter.next();
            if (!living.isAlive() || living.isRemoved()) {
                batchIter.remove();
                continue;
            }
            if (living.level() != level) continue;

            living.getCapability(EliteCapability.CAPABILITY).ifPresent(cap -> {
                if (!cap.isElite()) {
                    batchIter.remove();
                    return;
                }

                EliteData data = cap.getEliteData();

                // Update creator positions in ecosystem tracker every 100 ticks (5 seconds)
                // for efficient nearby creator queries
                if (updateCreatorPositions && data.isCreatorEntity()) {
                    EliteEcosystem.updateCreatorPosition(living);
                }

                // Dynamic strengthening
                if (EliteForgeConfig.SERVER.enableDynamicStrengthening.get()) {
                    try {
                        DynamicStrengthening.tickElite(living, data);
                    } catch (Exception e) {
                        LOGGER.error("Error in dynamic strengthening for {}: {}",
                                living.getName().getString(), e.getMessage());
                    }
                }

                // Awakening check
                if (EliteForgeConfig.SERVER.enableAwakening.get()) {
                    if (data.getQualityTier() == QualityTier.LEGENDARY && !data.isCreatorEntity()) {
                        try {
                            EliteAwakening.checkAwakening(living, data, level);
                        } catch (Exception e) {
                            LOGGER.error("Error in awakening check for {}: {}",
                                    living.getName().getString(), e.getMessage());
                        }
                    }
                }

                // CASUAL despawn check
                // processEliteBatch runs every 20 ticks, so decrement by 20 per call
                // to keep the despawn timer in real tick units (6000 ticks = 5 minutes)
                if (data.getSpawnMode() == DifficultyMode.CASUAL && !data.hasBeenEngaged()) {
                    if (data.tickDespawnTimer(20)) {
                        // Play despawn particles before discarding
                        level.sendParticles(ParticleTypes.SMOKE,
                                living.getX(), living.getY() + living.getBbHeight() * 0.5, living.getZ(),
                                15, 0.5, 0.5, 0.5, 0.05);
                        living.discard();
                        LOGGER.debug("CASUAL elite {} despawned after timer expired",
                                living.getName().getString());
                    }
                }
            });
        }

        // Creator NBT timers (separate iteration for NBT access pattern)
        tickCreatorNbtTimers(level);

        // Summon leash pull-back (keeps Necromancy undead / Clone clones near their owner)
        // Moved out of processEliteBatch (which runs per-level) so the leash tick runs
        // once per level per 20-tick batch — matching its previous cadence.
        if (EliteForgeConfig.SERVER.enableSummonLeash.get()) {
            tickSummonLeashes(level);
        }

        // Revenge system tick is now handled globally in onServerTick (overworld only)
        // to avoid N× decay on multi-dimension servers.
    }

    /**
     * Handle LivingHealEvent - check regen ability with interaction counter.
     * Elite mobs with regeneration abilities may have their healing modified.
     * Wither effect reduces regen by 50% (counter interaction).
     *
     * @param event the living heal event
     */
    @SubscribeEvent
    public static void onLivingHeal(LivingHealEvent event) {
        LivingEntity entity = event.getEntity();

        entity.getCapability(EliteCapability.CAPABILITY).ifPresent(cap -> {
            if (!cap.isElite()) return;

            EliteData data = cap.getEliteData();
            if (data == null) return; // Guard against null data

            // Check for regeneration ability
            if (data.hasAbility("eliteforge:regen")) {
                int regenLevel = data.getAbilityLevel("eliteforge:regen");
                // Boost healing by 50% per regeneration level
                float boostAmount = event.getAmount() * (1.0f + regenLevel * 0.5f);

                // Apply regen counter: wither effect reduces regen by 50%
                float regenMultiplier = AbilityInteraction.getRegenReduction(entity);
                boostAmount *= regenMultiplier;

                event.setAmount(boostAmount);
            }

            // Check for absorption ability
            if (data.hasAbility("eliteforge:absorption")) {
                int absLevel = data.getAbilityLevel("eliteforge:absorption");
                float extraHeal = entity.getMaxHealth() * 0.01f * absLevel;
                event.setAmount(event.getAmount() + extraHeal);
            }
        });
    }

    // ==================== Creator-Tier Integration Methods ====================

    /**
     * Process creator-tier NBT timers for all living entities in a level.
     * Handles:
     * - Assimilate invulnerability timer (3s after absorbing an ability)
     * - Bestowal revert timer (30s after creator dies, bestowed elites revert)
     * - Dominion no-place flag cleanup (clear flag when dominion is not active)
     * - Reincarnation revival health management
     *
     * @param level the server level
     */
    private static void tickCreatorNbtTimers(ServerLevel level) {
        // Iterator-based iteration; the Bestowal-revert branch below calls nbtIter.remove()
        // so the set mutation is safe (no ConcurrentModificationException).
        java.util.Iterator<LivingEntity> nbtIter = TRACKED_ELITES.iterator();
        while (nbtIter.hasNext()) {
            LivingEntity living = nbtIter.next();
            if (living.level() != level || !living.isAlive()) continue;

            net.minecraft.nbt.CompoundTag nbt = living.getPersistentData();

            // === Assimilate invulnerability timer ===
            if (nbt.contains(NBTKeys.ASSIMILATE_INVULN)) {
                int invulnTicks = nbt.getInt(NBTKeys.ASSIMILATE_INVULN);
                invulnTicks -= 20; // Decrement by 20 since we tick every 20 ticks
                if (invulnTicks <= 0) {
                    nbt.remove(NBTKeys.ASSIMILATE_INVULN);
                    living.setInvulnerable(false);
                } else {
                    nbt.putInt(NBTKeys.ASSIMILATE_INVULN, invulnTicks);
                }
            }

            // === Reincarnation timers ===
            // NOTE: EliteForgeReincarnationReviveTimer and EliteForgeReincarnationInvuln
            // are now handled exclusively by AbilityReincarnation.onTick() which runs every
            // tick (decrementing by 1). Previously both onTick and this method decremented
            // them, causing timers to expire ~2x faster than intended.

            // === Bestowal revert timer ===
            // When a C5 Bestowal creator dies, bestowed elites are tagged with
            // NBTKeys.BESTOWAL_REVERT = bestowalRevertTicks (default 600 = 30 seconds). After the timer
            // expires, the elite reverts to a normal mob.
            if (nbt.contains(NBTKeys.BESTOWAL_REVERT)) {
                int revertTicks = nbt.getInt(NBTKeys.BESTOWAL_REVERT);
                revertTicks -= 20; // Decrement by 20 since we tick every 20 ticks
                if (revertTicks <= 0) {
                    nbt.remove(NBTKeys.BESTOWAL_REVERT);
                    // Revert to normal mob
                    living.getCapability(EliteCapability.CAPABILITY).ifPresent(cap -> {
                        EliteData data = cap.getEliteData();
                        if (data.isElite()) {
                            // Q7: Call onRemove for all abilities before removing them.
                            // No centralized helper for onRemove because the iteration here
                            // also clears the abilities map afterward (mixed concerns).
                            for (Map.Entry<String, Integer> abilityEntry : new ArrayList<>(data.getAbilities().entrySet())) {
                                Ability ability = AbilityRegistry.getAbility(abilityEntry.getKey());
                                if (ability != null) {
                                    try {
                                        ability.onRemove(living, abilityEntry.getValue());
                                    } catch (Exception e) {
                                        LOGGER.error("Error in ability onRemove for {}: {}", ability.getIdString(), e.getMessage(), e);
                                    }
                                }
                            }
                            // Remove DynamicStrengthening attribute modifiers that onRemove doesn't cover.
                            com.eliteforge.spawn.DynamicStrengthening.removeAllModifiers(living);
                            // Remove all abilities
                            for (String abilityId : new ArrayList<>(data.getAbilities().keySet())) {
                                data.removeAbility(abilityId);
                            }
                            data.setElite(false);
                            data.setQualityTier(QualityTier.NORMAL);
                            data.setBestowerUUID(null);
                            data.setCreatorEntity(false);
                            data.setCreatorAbilityId(null);
                            data.setCreatorAbilityLevel(0);
                            cap.setEliteData(data);

                            // Remove from tracking set since entity is no longer elite.
                            // Use the iterator's remove() — calling TRACKED_ELITES.remove(living)
                            // here would throw ConcurrentModificationException on the next next().
                            nbtIter.remove();

                            // Broadcast removal to clients so they stop rendering the elite overlay.
                            // (A subsequent broadcastEliteDataUpdate is NOT needed: the removal packet
                            // already clears the client-side EliteCapabilityStorage entry.)
                            EliteCapabilitySync.broadcastEliteRemoval(living);

                            // Visual: puff of smoke
                            if (living.level() instanceof ServerLevel sl) {
                                sl.sendParticles(net.minecraft.core.particles.ParticleTypes.SMOKE,
                                        living.getX(), living.getY() + living.getBbHeight() * 0.5, living.getZ(),
                                        20, 0.5, 0.5, 0.5, 0.05);
                            }

                            LOGGER.debug("Bestowed elite {} reverted to normal after creator death",
                                    living.getName().getString());
                        }
                    });
                } else {
                    nbt.putInt(NBTKeys.BESTOWAL_REVERT, revertTicks);

                    // Weaken the elite as it approaches reversion.
                    // Design: "weakening effects in the last 5 seconds"
                    // In the last 5 seconds (100 ticks), apply Slowness I and Weakness I.
                    // In the last 2 seconds (40 ticks), upgrade Weakness to II for dramatic effect.
                    if (revertTicks <= 100) {
                        int weaknessLevel = revertTicks <= 40 ? 1 : 0; // Weakness I → II in final 2s
                        if (!living.hasEffect(MobEffects.WEAKNESS) || living.getEffect(MobEffects.WEAKNESS).getDuration() < 40) {
                            living.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, weaknessLevel, false, true));
                        }
                        if (!living.hasEffect(MobEffects.MOVEMENT_SLOWDOWN) || living.getEffect(MobEffects.MOVEMENT_SLOWDOWN).getDuration() < 40) {
                            living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 0, false, true));
                        }

                        // Visual indicator: smoke particles around the weakening elite
                        // Only emit every other 20-tick batch to avoid excessive particles
                        if (revertTicks % 40 == 0 || revertTicks <= 40) {
                            if (living.level() instanceof ServerLevel sl) {
                                sl.sendParticles(ParticleTypes.SMOKE,
                                        living.getX(), living.getY() + living.getBbHeight() * 0.5, living.getZ(),
                                        8, 0.3, 0.3, 0.3, 0.02);
                            }
                        }
                    }
                }
            }

            // === Dominion no-place flag cleanup ===
            // Clear the no-place flag if no active dominion is affecting this player.
            // The flag is set by AbilityDominion, but we need to ensure it gets
            // cleaned up when the dominion ends or the player moves away.
            if (nbt.contains(NBTKeys.DOMINION_NO_PLACE) && living instanceof ServerPlayer player) {
                // Check if there's still an active dominion affecting this player
                final boolean[] affectedHolder = {false};
                double checkRange = 45.0; // Maximum dominion range + buffer
                net.minecraft.world.phys.AABB area = new net.minecraft.world.phys.AABB(
                        player.getX() - checkRange, player.getY() - checkRange, player.getZ() - checkRange,
                        player.getX() + checkRange, player.getY() + checkRange, player.getZ() + checkRange
                );

                for (LivingEntity nearby : level.getEntitiesOfClass(LivingEntity.class, area,
                        e -> e.isAlive() && e != player && e.getCapability(EliteCapability.CAPABILITY).isPresent())) {
                    nearby.getCapability(EliteCapability.CAPABILITY).ifPresent(cap -> {
                        EliteData data = cap.getEliteData();
                        if (data.isCreatorEntity() && "eliteforge:creator_dominion".equals(data.getCreatorAbilityId())) {
                            // Check if the dominion is currently active
                            net.minecraft.nbt.CompoundTag creatorNbt = nearby.getPersistentData();
                            if (creatorNbt.getBoolean(NBTKeys.DOMINION_ACTIVE)) {
                                // Check if the player is within the dominion range
                                double dist = nearby.distanceTo(player);
                                int creatorLevel = data.getCreatorAbilityLevel();
                                double range = switch (creatorLevel) {
                                    case 1 -> 20.0;
                                    case 2 -> 30.0;
                                    default -> 40.0;
                                };
                                if (dist <= range) {
                                    affectedHolder[0] = true;
                                }
                            }
                        }
                    });
                    if (affectedHolder[0]) break;
                }

                if (!affectedHolder[0]) {
                    nbt.remove(NBTKeys.DOMINION_NO_PLACE);
                }
            }
        }
    }

    /**
     * Apply leash pull-back to all tracked summons, keeping them near their owner.
     * <p>
     * Called every 20 ticks (1 second) from {@link #processEliteBatch}. For each
     * summon in {@link #TRACKED_SUMMONS}:
     * <ul>
     *   <li>Reads the owner UUID from the summon's persistent NBT
     *       ({@link NBTKeys#SUMMONER_UUID}) — a fast O(1) check that avoids a
     *       capability lookup every tick.</li>
     *   <li>Resolves the owner via {@link ServerLevel#getEntity}. If the owner is
     *       missing, dead, removed, or in a different dimension, the link is
     *       cleared and the summon is released from tracking (it becomes a free
     *       mob that may despawn naturally later).</li>
     *   <li>If the distance exceeds {@code summonLeashRange}: the summon is
     *       navigated back toward the owner at {@code summonLeashPullSpeed},
     *       accompanied by a small purple particle puff to telegraph the pull.</li>
     *   <li>If the distance exceeds {@code summonLeashRange * 1.6} (hard recall):
     *       the summon is teleported directly to a spot near the owner, since
     *       pathfinding across that distance would be too slow. This prevents
     *       summons from getting permanently stuck behind terrain.</li>
     * </ul>
     * This is what makes summons "only wander near their owner": the soft pull
     * every second plus the hard recall cap together clamp the summon's effective
     * roaming radius to roughly {@code summonLeashRange}.
     *
     * @param level the server level
     */
    private static void tickSummonLeashes(ServerLevel level) {
        if (TRACKED_SUMMONS.isEmpty()) return;

        double leashRange = EliteForgeConfig.SERVER.summonLeashRange.get();
        double pullSpeed = EliteForgeConfig.SERVER.summonLeashPullSpeed.get();
        double hardRecallDist = leashRange * 1.6;

        java.util.Iterator<LivingEntity> it = TRACKED_SUMMONS.iterator();
        while (it.hasNext()) {
            LivingEntity summon = it.next();
            // Clean up dead/removed/invalid entries
            if (summon == null || !summon.isAlive() || summon.isRemoved()) {
                it.remove();
                continue;
            }
            if (summon.level() != level) continue; // only process this level's summons
            // Skip multi-part bosses — Wither ignores navigation.moveTo, and EnderDragon's
            // setPos/teleportTo corrupts its parts. Bosses are immune to leashing by design.
            if (summon instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragon
                    || summon instanceof net.minecraft.world.entity.boss.wither.WitherBoss) {
                continue;
            }

            net.minecraft.nbt.CompoundTag nbt = summon.getPersistentData();
            if (!nbt.hasUUID(NBTKeys.SUMMONER_UUID)) {
                // Link already cleared elsewhere — drop from tracking.
                it.remove();
                continue;
            }
            java.util.UUID ownerUUID = nbt.getUUID(NBTKeys.SUMMONER_UUID);

            // Resolve the owner entity (O(1) UUID lookup).
            net.minecraft.world.entity.Entity ownerRaw = level.getEntity(ownerUUID);
            if (!(ownerRaw instanceof LivingEntity owner) || !owner.isAlive() || owner.isRemoved()) {
                // Owner gone (dead / despawned / unloaded too long). Release the summon:
                // clear the link so the chain stops rendering, and drop it from tracking.
                clearSummonLink(summon, nbt);
                it.remove();
                continue;
            }

            double dist = summon.distanceTo(owner);

            if (dist > hardRecallDist) {
                // === Hard recall: teleport directly to a spot near the owner ===
                // Pick a position at ~leashRange*0.5 from the owner, in the direction
                // the summon currently is (so it doesn't stack on top of the owner).
                double dx = summon.getX() - owner.getX();
                double dz = summon.getZ() - owner.getZ();
                double len = Math.sqrt(dx * dx + dz * dz);
                double placeDist = Math.min(leashRange * 0.5, 3.0);
                double placeX, placeZ;
                if (len > 0.01) {
                    placeX = owner.getX() + (dx / len) * placeDist;
                    placeZ = owner.getZ() + (dz / len) * placeDist;
                } else {
                    placeX = owner.getX() + (level.random.nextDouble() - 0.5) * 2;
                    placeZ = owner.getZ() + (level.random.nextDouble() - 0.5) * 2;
                }
                double placeY = owner.getY();
                if (summon instanceof net.minecraft.world.entity.Mob mob) {
                    mob.teleportTo(placeX, placeY, placeZ);
                } else {
                    summon.setPos(placeX, placeY, placeZ);
                }
                // Dramatic purple particle burst at both ends to signal the recall
                level.sendParticles(ParticleTypes.WITCH,
                        summon.getX(), summon.getY() + summon.getBbHeight() * 0.5, summon.getZ(),
                        12, 0.4, 0.4, 0.4, 0.1);
                level.sendParticles(ParticleTypes.REVERSE_PORTAL,
                        owner.getX(), owner.getY() + owner.getBbHeight() * 0.5, owner.getZ(),
                        8, 0.5, 0.5, 0.5, 0.05);
            } else if (dist > leashRange) {
                // === Soft pull: navigate back toward the owner ===
                // Target a point just inside the leash range, on the line from owner
                // to summon, so the summon ends up near (not on top of) the owner.
                double dx = summon.getX() - owner.getX();
                double dy = summon.getY() - owner.getY();
                double dz = summon.getZ() - owner.getZ();
                double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
                double targetDist = leashRange * 0.6;
                double tx, ty, tz;
                if (len > 0.01) {
                    tx = owner.getX() + (dx / len) * targetDist;
                    ty = owner.getY() + (dy / len) * targetDist;
                    tz = owner.getZ() + (dz / len) * targetDist;
                } else {
                    tx = owner.getX();
                    ty = owner.getY();
                    tz = owner.getZ();
                }
                if (summon instanceof net.minecraft.world.entity.Mob mob) {
                    mob.getNavigation().moveTo(tx, ty, tz, pullSpeed);
                } else {
                    // Non-Mob fallback: apply velocity toward the owner.
                    double vx = (tx - summon.getX()) * 0.05;
                    double vy = (ty - summon.getY()) * 0.05 + 0.2; // small hop
                    double vz = (tz - summon.getZ()) * 0.05;
                    summon.setDeltaMovement(vx, vy, vz);
                }
                // Subtle purple sparkle at the summon to telegraph the pull
                if (level.random.nextInt(3) == 0) {
                    level.sendParticles(ParticleTypes.WITCH,
                            summon.getX(), summon.getY() + summon.getBbHeight() * 0.5, summon.getZ(),
                            3, 0.3, 0.3, 0.3, 0.05);
                }
            }
        }
    }

    /**
     * Clear the summon-owner link on an entity: remove the persistent NBT key,
     * null out {@code summonerUUID} in the EliteData capability, broadcast the
     * update so clients stop rendering the purple chain, and (for Mobs) drop
     * persistence so the released summon can despawn naturally once no player
     * is nearby.
     *
     * @param summon the summon whose link should be cleared
     * @param nbt    the summon's persistent data (passed in to avoid a re-fetch)
     */
    private static void clearSummonLink(LivingEntity summon, net.minecraft.nbt.CompoundTag nbt) {
        nbt.remove(NBTKeys.SUMMONER_UUID);
        summon.getCapability(EliteCapability.CAPABILITY).ifPresent(cap -> {
            EliteData data = cap.getEliteData();
            if (data.getSummonerUUID() != null) {
                data.setSummonerUUID(null);
                cap.setEliteData(data);
                EliteCapabilitySync.broadcastEliteDataUpdate(summon, data);
            }
        });
        // Releasing a summon whose owner is gone. setPersistenceRequired() is one-way
        // in vanilla — a persistent mob is SAVED on chunk-unload rather than despawned,
        // so without explicit cleanup these summons would accumulate indefinitely in
        // saved chunks (memory growth + mob-cap saturation). Schedule a delayed discard
        // (60 ticks = 3s grace so players see a brief fade rather than a pop) so the
        // summon is reliably removed. Only applies to Mob summons (non-Mob LivingEntities
        // like players are never summons).
        if (summon instanceof net.minecraft.world.entity.Mob mob && summon.level() instanceof ServerLevel sl) {
            sl.getServer().tell(new net.minecraft.server.TickTask(
                    sl.getServer().getTickCount() + 60,
                    () -> {
                        if (mob.isAlive() && !mob.isRemoved()) {
                            // Particle puff before discard so the disappearance isn't jarring.
                            if (mob.level() instanceof ServerLevel sl2) {
                                sl2.sendParticles(net.minecraft.core.particles.ParticleTypes.WITCH,
                                        mob.getX(), mob.getY() + mob.getBbHeight() * 0.5, mob.getZ(),
                                        8, 0.3, 0.3, 0.3, 0.05);
                            }
                            mob.discard();
                        }
                    }
            ));
        }
    }

    /**
     * Process Scorched Earth zones created by C6 Annihilate ability.
     * Damages all non-creator entities within active zones and decrements timers.
     * Called every 20 ticks (1 second).
     * <p>
     * Cleanup safeguards:
     * <ul>
     *   <li>Zones with {@code ticksRemaining <= 0} are removed</li>
     *   <li>Maximum lifetime cap of 600 ticks (30 seconds) as a safety measure
     *       to prevent stale zones from persisting indefinitely if the server
     *       crashes or tick processing is interrupted</li>
     *   <li>Dimension validation: zone entries from a different dimension are
     *       removed to prevent cross-dimension damage</li>
     * </ul>
     *
     * @param level the server level
     */
    private static void tickScorchedEarthZones(ServerLevel level) {
        // In 1.20.1, ServerLevel.getPersistentData() was removed; Scorched Earth
        // zones are now stored in-memory in AbilityAnnihilate (ConcurrentHashMap
        // keyed by dimension ResourceLocation).
        net.minecraft.nbt.CompoundTag zonesTag =
                com.eliteforge.ability.creator.AbilityAnnihilate.getScorchedZones(level.dimension().location());
        if (zonesTag == null || zonesTag.isEmpty()) return;

        List<String> expiredZones = new ArrayList<>();

        // Maximum lifetime cap: 3x the configured zone duration. Zones exceeding this are
        // force-expired as a safety measure against stale data from server crashes
        // or interrupted tick processing.
        final int MAX_SCORCHED_LIFETIME = EliteForgeConfig.SERVER.scorchedEarthMaxTicks.get() * 3;

        // Current level dimension key for cross-dimension validation
        net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> currentDimension = level.dimension();

        for (String zoneKey : zonesTag.getAllKeys()) {
            net.minecraft.nbt.CompoundTag zoneData = zonesTag.getCompound(zoneKey);
            int ticksRemaining = zoneData.getInt("ticksRemaining");

            // Dimension validation: if the zone was created in a different dimension,
            // expire it immediately to prevent cross-dimension damage
            if (zoneData.contains("dimension")) {
                String dimStr = zoneData.getString("dimension");
                if (!currentDimension.location().toString().equals(dimStr)) {
                    expiredZones.add(zoneKey);
                    LOGGER.debug("Scorched Earth zone {} expired: dimension mismatch (zone={}, current={})",
                            zoneKey, dimStr, currentDimension.location());
                    continue;
                }
            }

            // Safety cap: force-expire zones that have exceeded the maximum lifetime
            if (ticksRemaining > MAX_SCORCHED_LIFETIME) {
                LOGGER.warn("Scorched Earth zone {} has ticksRemaining={} exceeding max lifetime cap {}, force-expiring",
                        zoneKey, ticksRemaining, MAX_SCORCHED_LIFETIME);
                expiredZones.add(zoneKey);
                continue;
            }

            ticksRemaining -= 20; // Decrement by 20 since we tick every 20 ticks

            if (ticksRemaining <= 0) {
                expiredZones.add(zoneKey);
                continue;
            }

            zoneData.putInt("ticksRemaining", ticksRemaining);

            // Apply fire damage to entities in the zone
            double x = zoneData.getDouble("x");
            double y = zoneData.getDouble("y");
            double z = zoneData.getDouble("z");
            double radius = zoneData.getDouble("radius");
            float damagePerSecond = zoneData.getFloat("damagePerSecond");

            net.minecraft.world.phys.AABB area = new net.minecraft.world.phys.AABB(
                    x - radius, y - radius, z - radius,
                    x + radius, y + radius, z + radius
            );

            List<LivingEntity> entities = level.getEntitiesOfClass(LivingEntity.class, area,
                    e -> e.isAlive() && !(e instanceof Player player && (player.isCreative() || player.isSpectator())));

            // Retrieve the creator UUID stored in the zone data for allied-elite exclusion
            java.util.UUID creatorUUID = zoneData.hasUUID("creatorUUID") ? zoneData.getUUID("creatorUUID") : null;

            for (LivingEntity target : entities) {
                double dist = target.distanceToSqr(x, y, z);
                if (dist <= radius) {
                    // Skip allied elites: entities that have EliteCapability and share the
                    // same bestower UUID or nexus source UUID as the Scorched Earth creator.
                    // This prevents the zone from damaging other elites on the same "team".
                    if (creatorUUID != null) {
                        final boolean[] isAllied = {false};
                        target.getCapability(EliteCapability.CAPABILITY).ifPresent(cap -> {
                            EliteData targetData = cap.getEliteData();
                            if (targetData.isElite()) {
                                java.util.UUID bestower = targetData.getBestowerUUID();
                                java.util.UUID nexusSource = targetData.getNexusSourceUUID();
                                if ((bestower != null && bestower.equals(creatorUUID))
                                        || (nexusSource != null && nexusSource.equals(creatorUUID))) {
                                    isAllied[0] = true;
                                }
                            }
                        });
                        if (isAllied[0]) continue;
                    }

                    // Distance-based falloff: full damage at center, 50% at edge
                    float falloff = 1.0f - (float)(dist / radius) * 0.5f;
                    float damage = damagePerSecond * falloff;
                    target.hurt(level.damageSources().onFire(), damage);

                    // Set on fire briefly
                    target.setSecondsOnFire(2);
                }
            }

            // Scorched Earth particles — B7: use tickCounter (mod 40) instead of
            // level.getGameTime() % 40. getGameTime() is per-level and may produce
            // different phases across dimensions, while tickCounter is a single
            // server-wide counter that ensures consistent particle cadence.
            if (tickCounter % 60 == 0) {
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.FLAME,
                        x, y + 1.0, z, 15, radius * 0.5, 1.0, radius * 0.5, 0.05);
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.SMOKE,
                        x, y + 0.5, z, 10, radius * 0.5, 0.5, radius * 0.5, 0.02);
            }
        }

        // Remove expired zones
        for (String expiredKey : expiredZones) {
            zonesTag.remove(expiredKey);
            LOGGER.debug("Scorched Earth zone expired: {}", expiredKey);
        }
    }
}
