package com.eliteforge.spawn;

import com.eliteforge.ability.Ability;
import com.eliteforge.ability.AbilityCategory;
import com.eliteforge.ability.AbilityRegistry;
import com.eliteforge.ability.MutualExclusion;
import com.eliteforge.capability.EliteCapability;
import com.eliteforge.capability.EliteData;
import com.eliteforge.capability.EliteCapabilitySync;
import com.eliteforge.difficulty.ChunkHeatManager;
import com.eliteforge.util.NBTKeys;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;

/**
 * Runtime dynamic strengthening system for elite mobs.
 * <p>
 * Provides four categories of dynamic strengthening:
 * <ul>
 *   <li><b>Time Strengthening</b>: Every 60 seconds alive, elite gets +2% health, +1% damage.
 *       Caps at +20% health, +10% damage.</li>
 *   <li><b>Kill Strengthening</b>: Each time elite kills a player, heals 20%, +5% damage for 30s.
 *       Caps at +25% damage.</li>
 *   <li><b>Group Strengthening</b>: Each nearby elite (within 8 blocks) gives +3% all stats.
 *       Caps at +15% (5 elites).</li>
 *   <li><b>Heat Strengthening</b>: Chunk heat >50: regen speed +50%. >75: attack speed +20%.
 *       >90: 10% chance every 30s to get 1 temporary ability.</li>
 * </ul>
 * <p>
 * Uses entity persistent NBT for tracking timers and counters.
 * Uses {@link AttributeModifier} for stat bonuses with unique UUIDs.
 */
public class DynamicStrengthening {

    private static final Logger LOGGER = LogManager.getLogger();

    // ==================== NBT Key Constants (centralized via NBTKeys) ====================
    private static final String KEY_TIME_TICK = NBTKeys.TIME_STRENGTHEN_TICK;
    private static final String KEY_TIME_HEALTH_STACKS = NBTKeys.TIME_HEALTH_STACKS;
    private static final String KEY_TIME_DAMAGE_STACKS = NBTKeys.TIME_DAMAGE_STACKS;
    private static final String KEY_KILL_STRENGTHEN_COUNT = NBTKeys.KILL_STRENGTHEN_COUNT;
    private static final String KEY_KILL_STRENGTHEN_EXPIRY = NBTKeys.KILL_STRENGTHEN_EXPIRY;
    private static final String KEY_HEAT_ABILITY_TIMER = NBTKeys.HEAT_ABILITY_TIMER;
    private static final String KEY_TEMP_ABILITIES = NBTKeys.TEMP_ABILITIES;
    /** Tracks the last applied health stacks to avoid unnecessary modifier churn */
    private static final String KEY_TIME_LAST_HEALTH_STACKS = NBTKeys.TIME_LAST_HEALTH_STACKS;
    /** Tracks the last applied damage stacks to avoid unnecessary modifier churn */
    private static final String KEY_TIME_LAST_DAMAGE_STACKS = NBTKeys.TIME_LAST_DAMAGE_STACKS;

    // ==================== Config Constants ====================
    /** Time interval for time strengthening check (60 seconds in ticks) */
    private static final int TIME_STRENGTHEN_INTERVAL = 1200;
    /** Max stacks for time health bonus (+2% per stack, max 10 stacks = +20%) */
    private static final int MAX_TIME_HEALTH_STACKS = 10;
    /** Max stacks for time damage bonus (+1% per stack, max 10 stacks = +10%) */
    private static final int MAX_TIME_DAMAGE_STACKS = 10;
    /** Kill strengthening damage bonus per kill (+5% per kill) */
    private static final float KILL_DAMAGE_BONUS = 0.05f;
    /** Max kill strengthening damage bonus (+25%) */
    private static final float MAX_KILL_DAMAGE_BONUS = 0.25f;
    /** Kill strengthening duration (30 seconds in ticks) */
    private static final int KILL_STRENGTHEN_DURATION = 600;
    /** Kill strengthening heal amount (20%) */
    private static final float KILL_HEAL_FRACTION = 0.20f;
    /** Group strengthening range (8 blocks) */
    private static final double GROUP_RANGE = 8.0;
    /** Group strengthening bonus per nearby elite (+3%) */
    private static final float GROUP_BONUS_PER_ELITE = 0.03f;
    /** Max group strengthening bonus (+15%, 5 elites) */
    private static final float MAX_GROUP_BONUS = 0.15f;
    /** Max nearby elites contributing to group bonus */
    private static final int MAX_GROUP_ELITES = 5;
    /** Heat threshold for regen speed bonus */
    private static final float HEAT_REGEN_THRESHOLD = 50.0f;
    /** Heat threshold for attack speed bonus */
    private static final float HEAT_ATTACK_SPEED_THRESHOLD = 75.0f;
    /** Heat threshold for temporary ability chance */
    private static final float HEAT_ABILITY_THRESHOLD = 90.0f;
    /** Heat ability check interval (30 seconds in ticks) */
    private static final int HEAT_ABILITY_INTERVAL = 600;
    /** Chance to gain temporary ability from heat (>90) */
    private static final float HEAT_ABILITY_CHANCE = 0.10f;

    // ==================== Attribute Modifier UUIDs ====================
    private static final UUID TIME_HEALTH_UUID = UUID.fromString("d1e2f3a4-b5c6-7890-abcd-ef1234567001");
    private static final UUID TIME_DAMAGE_UUID = UUID.fromString("d1e2f3a4-b5c6-7890-abcd-ef1234567002");
    private static final UUID KILL_DAMAGE_UUID = UUID.fromString("d1e2f3a4-b5c6-7890-abcd-ef1234567003");
    private static final UUID GROUP_HEALTH_UUID = UUID.fromString("d1e2f3a4-b5c6-7890-abcd-ef1234567004");
    private static final UUID GROUP_DAMAGE_UUID = UUID.fromString("d1e2f3a4-b5c6-7890-abcd-ef1234567005");
    private static final UUID HEAT_ATTACK_SPEED_UUID = UUID.fromString("d1e2f3a4-b5c6-7890-abcd-ef1234567006");

    private DynamicStrengthening() {
        // Utility class - no instantiation
    }

    /**
     * Main tick method for dynamic strengthening. Called every tick for each elite entity.
     * Handles all four strengthening categories.
     *
     * @param entity the elite entity
     * @param data   the elite data
     */
    public static void tickElite(LivingEntity entity, EliteData data) {
        if (entity.level().isClientSide()) return;
        if (!data.isElite()) return;
        if (!(entity.level() instanceof ServerLevel serverLevel)) return;

        CompoundTag nbt = entity.getPersistentData();

        tickTimeStrengthening(entity, nbt);
        tickKillStrengthening(entity, nbt);
        tickGroupStrengthening(entity, nbt, serverLevel);
        tickHeatStrengthening(entity, nbt, data, serverLevel);
    }

    // ==================== Time Strengthening ====================

    /**
     * Time-based strengthening: every 60 seconds alive, elite gets +2% health, +1% damage.
     * Caps at +20% health (10 stacks), +10% damage (10 stacks).
     * <p>
     * Note: This method is called every 20 ticks (1 second) from EliteEventHandler.
     * The timer increments by 20 per call to match real tick time.
     */
    private static void tickTimeStrengthening(LivingEntity entity, CompoundTag nbt) {
        int tick = nbt.getInt(KEY_TIME_TICK);
        tick += 20; // Increment by 20 because called every 20 ticks
        nbt.putInt(KEY_TIME_TICK, tick);

        if (tick >= TIME_STRENGTHEN_INTERVAL) {
            nbt.putInt(KEY_TIME_TICK, 0);

            int healthStacks = Math.min(nbt.getInt(KEY_TIME_HEALTH_STACKS) + 1, MAX_TIME_HEALTH_STACKS);
            int damageStacks = Math.min(nbt.getInt(KEY_TIME_DAMAGE_STACKS) + 1, MAX_TIME_DAMAGE_STACKS);
            nbt.putInt(KEY_TIME_HEALTH_STACKS, healthStacks);
            nbt.putInt(KEY_TIME_DAMAGE_STACKS, damageStacks);

            applyTimeModifiers(entity, healthStacks, damageStacks);
            nbt.putInt(KEY_TIME_LAST_HEALTH_STACKS, healthStacks);
            nbt.putInt(KEY_TIME_LAST_DAMAGE_STACKS, damageStacks);

            LOGGER.debug("Time strengthening tick for {} (health stacks={}, damage stacks={})",
                    entity.getName().getString(), healthStacks, damageStacks);
        } else {
            // Re-apply existing modifiers only if stacks have changed since last application.
            // This avoids removing and re-adding attribute modifiers every 20 ticks,
            // similar to how AbilityNexus uses NEXUS_BONUSES_APPLIED_KEY to track state.
            int healthStacks = nbt.getInt(KEY_TIME_HEALTH_STACKS);
            int damageStacks = nbt.getInt(KEY_TIME_DAMAGE_STACKS);
            int lastHealthStacks = nbt.getInt(KEY_TIME_LAST_HEALTH_STACKS);
            int lastDamageStacks = nbt.getInt(KEY_TIME_LAST_DAMAGE_STACKS);
            if (healthStacks != lastHealthStacks || damageStacks != lastDamageStacks) {
                applyTimeModifiers(entity, healthStacks, damageStacks);
                nbt.putInt(KEY_TIME_LAST_HEALTH_STACKS, healthStacks);
                nbt.putInt(KEY_TIME_LAST_DAMAGE_STACKS, damageStacks);
            }
        }
    }

    /**
     * Apply time-based attribute modifiers.
     */
    private static void applyTimeModifiers(LivingEntity entity, int healthStacks, int damageStacks) {
        try {
            // Health: +2% per stack as MULTIPLY_BASE
            var healthAttr = entity.getAttribute(Attributes.MAX_HEALTH);
            if (healthAttr != null) {
                healthAttr.removeModifier(TIME_HEALTH_UUID);
                if (healthStacks > 0) {
                    healthAttr.addTransientModifier(new AttributeModifier(
                            TIME_HEALTH_UUID,
                            "EliteForge Time Health",
                            healthStacks * 0.02,
                            AttributeModifier.Operation.MULTIPLY_BASE
                    ));
                }
            }

            // Damage: +1% per stack as MULTIPLY_BASE
            var damageAttr = entity.getAttribute(Attributes.ATTACK_DAMAGE);
            if (damageAttr != null) {
                damageAttr.removeModifier(TIME_DAMAGE_UUID);
                if (damageStacks > 0) {
                    damageAttr.addTransientModifier(new AttributeModifier(
                            TIME_DAMAGE_UUID,
                            "EliteForge Time Damage",
                            damageStacks * 0.01,
                            AttributeModifier.Operation.MULTIPLY_BASE
                    ));
                }
            }
        } catch (Exception e) {
            // Attributes may not be available for all entities
        }
    }

    // ==================== Kill Strengthening ====================

    /**
     * Called when an elite kills a player. Heals 20% and grants +5% damage for 30 seconds.
     * Caps at +25% damage from kills.
     *
     * @param entity the elite entity
     */
    public static void onEliteKillPlayer(LivingEntity entity) {
        if (entity.level().isClientSide()) return;

        CompoundTag nbt = entity.getPersistentData();
        int killCount = Math.min(nbt.getInt(KEY_KILL_STRENGTHEN_COUNT) + 1, (int) (MAX_KILL_DAMAGE_BONUS / KILL_DAMAGE_BONUS));
        nbt.putInt(KEY_KILL_STRENGTHEN_COUNT, killCount);

        // Set expiry to 30 seconds from now (game time)
        long expiryTime = entity.level().getGameTime() + KILL_STRENGTHEN_DURATION;
        nbt.putLong(KEY_KILL_STRENGTHEN_EXPIRY, expiryTime);

        // Heal 20% of max health
        float healAmount = entity.getMaxHealth() * KILL_HEAL_FRACTION;
        entity.heal(healAmount);

        // Apply kill damage modifier
        applyKillModifiers(entity, killCount);

        // Particles on kill
        if (entity.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                    entity.getX(), entity.getY() + entity.getBbHeight() * 0.5, entity.getZ(),
                    15, 0.5, entity.getBbHeight() * 0.3, 0.5, 0.05);
        }

        LOGGER.debug("Kill strengthening for {} (kills={}, heal={})",
                entity.getName().getString(), killCount, healAmount);
    }

    /** Tracks the last applied kill count to avoid unnecessary modifier churn */
    private static final String KEY_KILL_LAST_COUNT = NBTKeys.KILL_LAST_COUNT;

    /**
     * Tick kill strengthening - check if the bonus has expired.
     */
    private static void tickKillStrengthening(LivingEntity entity, CompoundTag nbt) {
        int killCount = nbt.getInt(KEY_KILL_STRENGTHEN_COUNT);
        if (killCount <= 0) {
            // If we previously had a kill modifier, clean it up
            if (nbt.getInt(KEY_KILL_LAST_COUNT) > 0) {
                applyKillModifiers(entity, 0);
                nbt.putInt(KEY_KILL_LAST_COUNT, 0);
            }
            return;
        }

        long expiryTime = nbt.getLong(KEY_KILL_STRENGTHEN_EXPIRY);
        long currentGameTime = entity.level().getGameTime();

        if (currentGameTime >= expiryTime) {
            // Kill bonus expired
            nbt.putInt(KEY_KILL_STRENGTHEN_COUNT, 0);
            nbt.putLong(KEY_KILL_STRENGTHEN_EXPIRY, 0);
            applyKillModifiers(entity, 0);
            nbt.putInt(KEY_KILL_LAST_COUNT, 0);
        } else {
            // Only re-apply if the kill count has changed since last application
            int lastKillCount = nbt.getInt(KEY_KILL_LAST_COUNT);
            if (killCount != lastKillCount) {
                applyKillModifiers(entity, killCount);
                nbt.putInt(KEY_KILL_LAST_COUNT, killCount);
            }
        }
    }

    /**
     * Apply kill-based damage modifier.
     */
    private static void applyKillModifiers(LivingEntity entity, int killCount) {
        try {
            var damageAttr = entity.getAttribute(Attributes.ATTACK_DAMAGE);
            if (damageAttr != null) {
                damageAttr.removeModifier(KILL_DAMAGE_UUID);
                if (killCount > 0) {
                    float bonus = Math.min(killCount * KILL_DAMAGE_BONUS, MAX_KILL_DAMAGE_BONUS);
                    damageAttr.addTransientModifier(new AttributeModifier(
                            KILL_DAMAGE_UUID,
                            "EliteForge Kill Damage",
                            bonus,
                            AttributeModifier.Operation.MULTIPLY_BASE
                    ));
                }
            }
        } catch (Exception e) {
            // Attribute may not be available
        }
    }

    // ==================== Group Strengthening ====================

    /**
     * Group-based strengthening: each nearby elite (within 8 blocks) gives +3% all stats.
     * Caps at +15% (5 elites). Re-evaluated every 20 ticks (1 second) for accuracy.
     * <p>
     * P3: Added state-tracking via {@link NBTKeys#GROUP_LAST_BONUS} to avoid removing
     * and re-adding attribute modifiers every 20 ticks when the group bonus hasn't
     * changed. Previously this method called {@code removeModifier} + {@code addTransientModifier}
     * on every invocation (~3 times/second per elite), causing unnecessary attribute
     * recalculation churn. Now modifiers are only re-applied when the bonus value
     * actually changes.
     */
    private static void tickGroupStrengthening(LivingEntity entity, CompoundTag nbt, ServerLevel serverLevel) {
        // No tick-count gate here: this method is already called only every 20 ticks
        // from EliteEventHandler.tickDynamicStrengthening().

        AABB area = new AABB(
                entity.getX() - GROUP_RANGE, entity.getY() - GROUP_RANGE, entity.getZ() - GROUP_RANGE,
                entity.getX() + GROUP_RANGE, entity.getY() + GROUP_RANGE, entity.getZ() + GROUP_RANGE
        );

        List<LivingEntity> nearbyElites = serverLevel.getEntitiesOfClass(LivingEntity.class, area,
                e -> e != entity && e.isAlive()
                        && e.getCapability(EliteCapability.CAPABILITY).map(EliteCapability::isElite).orElse(false));

        int eliteCount = Math.min(nearbyElites.size(), MAX_GROUP_ELITES);
        float groupBonus = eliteCount * GROUP_BONUS_PER_ELITE;
        groupBonus = Math.min(groupBonus, MAX_GROUP_BONUS);

        // P3: Only re-apply modifiers if the bonus value changed since last tick.
        // Store the bonus as an int (bonus * 1000) for NBT-friendly storage.
        int storedBonus = nbt.getInt(NBTKeys.GROUP_LAST_BONUS);
        int currentBonusEncoded = (int) (groupBonus * 1000);
        if (storedBonus != currentBonusEncoded) {
            applyGroupModifiers(entity, groupBonus);
            nbt.putInt(NBTKeys.GROUP_LAST_BONUS, currentBonusEncoded);
        }
    }

    /**
     * Apply group-based attribute modifiers for health and damage.
     */
    private static void applyGroupModifiers(LivingEntity entity, float groupBonus) {
        try {
            var healthAttr = entity.getAttribute(Attributes.MAX_HEALTH);
            if (healthAttr != null) {
                healthAttr.removeModifier(GROUP_HEALTH_UUID);
                if (groupBonus > 0) {
                    healthAttr.addTransientModifier(new AttributeModifier(
                            GROUP_HEALTH_UUID,
                            "EliteForge Group Health",
                            groupBonus,
                            AttributeModifier.Operation.MULTIPLY_BASE
                    ));
                }
            }

            var damageAttr = entity.getAttribute(Attributes.ATTACK_DAMAGE);
            if (damageAttr != null) {
                damageAttr.removeModifier(GROUP_DAMAGE_UUID);
                if (groupBonus > 0) {
                    damageAttr.addTransientModifier(new AttributeModifier(
                            GROUP_DAMAGE_UUID,
                            "EliteForge Group Damage",
                            groupBonus,
                            AttributeModifier.Operation.MULTIPLY_BASE
                    ));
                }
            }
        } catch (Exception e) {
            // Attributes may not be available
        }
    }

    // ==================== Heat Strengthening ====================

    /**
     * Heat-based strengthening:
     * - Chunk heat >50: regen speed +50%
     * - Chunk heat >75: attack speed +20%
     * - Chunk heat >90: 10% chance every 30s to get 1 temporary ability
     */
    private static void tickHeatStrengthening(LivingEntity entity, CompoundTag nbt, EliteData data, ServerLevel serverLevel) {
        ChunkHeatManager heatManager = ChunkHeatManager.get(serverLevel);
        ChunkPos chunkPos = new ChunkPos(entity.blockPosition());
        float heat = heatManager.getHeat(serverLevel, chunkPos);

        // Heat > 50: Regeneration speed +50% (apply Regeneration effect periodically)
        if (heat > HEAT_REGEN_THRESHOLD) {
            // Apply Regeneration I for 3 seconds (60 ticks) if not already present or about to expire
            if (!entity.hasEffect(MobEffects.REGENERATION)
                    || entity.getEffect(MobEffects.REGENERATION).getDuration() < 40) {
                entity.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 60, 0, false, true));
            }
        }

        // Heat > 75: Attack speed +20%
        try {
            var attackSpeedAttr = entity.getAttribute(Attributes.ATTACK_SPEED);
            if (attackSpeedAttr != null) {
                attackSpeedAttr.removeModifier(HEAT_ATTACK_SPEED_UUID);
                if (heat > HEAT_ATTACK_SPEED_THRESHOLD) {
                    attackSpeedAttr.addTransientModifier(new AttributeModifier(
                            HEAT_ATTACK_SPEED_UUID,
                            "EliteForge Heat Attack Speed",
                            0.20,
                            AttributeModifier.Operation.MULTIPLY_BASE
                    ));
                }
            }
        } catch (Exception e) {
            // Attribute may not be available
        }

        // Heat > 90: 10% chance every 30s to get 1 temporary ability
        if (heat > HEAT_ABILITY_THRESHOLD) {
            int heatAbilityTimer = nbt.getInt(KEY_HEAT_ABILITY_TIMER);
            heatAbilityTimer += 20; // Increment by 20 because called every 20 ticks
            nbt.putInt(KEY_HEAT_ABILITY_TIMER, heatAbilityTimer);

            if (heatAbilityTimer >= HEAT_ABILITY_INTERVAL) {
                nbt.putInt(KEY_HEAT_ABILITY_TIMER, 0);

                if (entity.getRandom().nextFloat() < HEAT_ABILITY_CHANCE) {
                    grantTemporaryAbility(entity, data);
                }
            }
        }
    }

    /**
     * Grant a random temporary ability to an elite entity from heat strengthening.
     * Temporary abilities are non-creator abilities that last until the entity dies.
     * Creator-tier entities are excluded from receiving temporary abilities
     * (only C4 Assimilate and C7 Reincarnation can break the creator exclusivity rule).
     */
    private static void grantTemporaryAbility(LivingEntity entity, EliteData data) {
        // Creator entities cannot receive temporary abilities via heat strengthening
        // (only C4 Assimilate and C7 Reincarnation can break the exclusivity rule)
        if (data.isCreatorEntity()) {
            LOGGER.debug("Skipped heat temporary ability for creator entity {} (exclusivity rule)",
                    entity.getName().getString());
            return;
        }

        // Get available abilities (non-creator, not already possessed)
        // P2: Use cached non-creator ability list instead of streaming
        // AbilityRegistry.getAllAbilities() every call.
        var available = AbilityRegistry.getNonCreatorAbilities().stream()
                .filter(a -> !data.hasAbility(a.getIdString()))
                .filter(Ability::isEnabled)
                .filter(a -> {
                    for (String existingId : data.getAbilities().keySet()) {
                        if (MutualExclusion.isMutuallyExclusive(a.getIdString(), existingId)) {
                            return false;
                        }
                    }
                    return true;
                })
                .toList();

        if (available.isEmpty()) {
            LOGGER.debug("No available abilities for heat temporary ability grant on {}",
                    entity.getName().getString());
            return;
        }

        // Pick a random ability
        Ability chosen = available.get(entity.getRandom().nextInt(available.size()));
        int level = 1 + entity.getRandom().nextInt(Math.min(3, chosen.getMaxLevel()));

        // Add ability to elite data — check return value to avoid applying
        // side effects (onApply, NBT tracking, particles) when the maxAbilities
        // limit rejects the addition.
        entity.getCapability(EliteCapability.CAPABILITY).ifPresent(cap -> {
            EliteData eliteData = cap.getEliteData();
            boolean added = eliteData.addAbility(chosen.getIdString(), level);
            if (!added) {
                LOGGER.debug("Heat temporary ability {} rejected for {} (maxAbilities limit reached)",
                        chosen.getIdString(), entity.getName().getString());
                return;
            }

            // Track as temporary ability in NBT
            CompoundTag nbt = entity.getPersistentData();
            String tempAbilities = nbt.getString(KEY_TEMP_ABILITIES);
            if (!tempAbilities.isEmpty()) {
                tempAbilities += ",";
            }
            tempAbilities += chosen.getIdString();
            nbt.putString(KEY_TEMP_ABILITIES, tempAbilities);

            cap.setEliteData(eliteData);
            EliteCapabilitySync.broadcastEliteDataUpdate(entity, eliteData);

            // Apply the ability
            try {
                chosen.onApply(entity, level);
            } catch (Exception e) {
                LOGGER.error("Error applying heat temporary ability {}: {}", chosen.getIdString(), e.getMessage());
            }
        });

        // Particles for temporary ability gain
        if (entity.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.ENCHANTED_HIT,
                    entity.getX(), entity.getY() + entity.getBbHeight() * 0.5, entity.getZ(),
                    20, 0.5, entity.getBbHeight() * 0.3, 0.5, 0.1);
        }

        LOGGER.debug("Granted heat temporary ability {} (level {}) to {}",
                chosen.getIdString(), level, entity.getName().getString());
    }

    // ==================== Cleanup ====================

    /**
     * Remove all dynamic strengthening modifiers from an entity.
     * Called when an elite dies or loses elite status.
     * <p>
     * P3: Also clears the GROUP_LAST_BONUS NBT state so that the next time the
     * entity becomes elite, the group modifier will be freshly applied rather
     * than skipped due to stale state matching.
     *
     * @param entity the entity to clean up
     */
    public static void removeAllModifiers(LivingEntity entity) {
        try {
            var healthAttr = entity.getAttribute(Attributes.MAX_HEALTH);
            if (healthAttr != null) {
                healthAttr.removeModifier(TIME_HEALTH_UUID);
                healthAttr.removeModifier(GROUP_HEALTH_UUID);
            }

            var damageAttr = entity.getAttribute(Attributes.ATTACK_DAMAGE);
            if (damageAttr != null) {
                damageAttr.removeModifier(TIME_DAMAGE_UUID);
                damageAttr.removeModifier(KILL_DAMAGE_UUID);
                damageAttr.removeModifier(GROUP_DAMAGE_UUID);
            }

            var attackSpeedAttr = entity.getAttribute(Attributes.ATTACK_SPEED);
            if (attackSpeedAttr != null) {
                attackSpeedAttr.removeModifier(HEAT_ATTACK_SPEED_UUID);
            }
        } catch (Exception e) {
            // Attributes may not be available
        }
        // P3: Clear the group bonus state tracker so the next elite conversion
        // will re-apply the modifier from scratch instead of skipping.
        entity.getPersistentData().remove(NBTKeys.GROUP_LAST_BONUS);
    }
}
