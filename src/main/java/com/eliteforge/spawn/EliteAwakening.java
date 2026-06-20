package com.eliteforge.spawn;

import com.eliteforge.ability.Ability;
import com.eliteforge.ability.AbilityCategory;
import com.eliteforge.ability.AbilityRegistry;
import com.eliteforge.capability.EliteCapability;
import com.eliteforge.capability.EliteData;
import com.eliteforge.capability.EliteCapabilitySync;
import com.eliteforge.config.DifficultyMode;
import com.eliteforge.config.EliteForgeConfig;
import com.eliteforge.difficulty.ChunkHeatManager;
import com.eliteforge.quality.QualityTier;
import com.eliteforge.util.NBTKeys;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ChunkPos;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * System that allows legendary elites to "awaken" into creator-tier entities.
 * <p>
 * Awakening is a periodic check with strict conditions. When triggered, it
 * transforms a legendary elite into a mythic creator entity through a dramatic
 * visual process involving freezing, particle effects, and a server-wide
 * announcement.
 * <p>
 * Conditions for awakening:
 * <ol>
 *   <li>Quality == LEGENDARY</li>
 *   <li>Entity alive > 5 minutes (tickCount > 6000)</li>
 *   <li>Chunk heat >= 80</li>
 *   <li>Entity has killed >= 2 players</li>
 *   <li>Active creator count < max (configurable, default 2)</li>
 *   <li>Awakening chance: 5% per 60-second check</li>
 * </ol>
 */
public class EliteAwakening {

    private static final Logger LOGGER = LogManager.getLogger();

    // ==================== NBT Key Constants (centralized via NBTKeys) ====================
    private static final String KEY_AWAKENING_TIMER = NBTKeys.AWAKENING_TIMER;
    private static final String KEY_IS_AWAKENING = NBTKeys.AWAKENING_IS_AWAKENING;
    private static final String KEY_AWAKENING_FREEZE_TICKS = NBTKeys.AWAKENING_FREEZE_TICKS;

    // ==================== Config Constants (fallback defaults) ====================
    /** Check interval for awakening (60 seconds in ticks) — overridden by config */
    private static final int AWAKENING_CHECK_INTERVAL = 1200;
    /** Minimum entity alive time for awakening (5 minutes in ticks) */
    private static final int MIN_ALIVE_TICKS = 6000;
    /** Minimum chunk heat for awakening */
    private static final float MIN_HEAT = 80.0f;
    /** Minimum player kills by this entity for awakening */
    private static final int MIN_PLAYER_KILLS = 2;
    /** Maximum active creators allowed for awakening — overridden by config */
    private static final int DEFAULT_MAX_CREATORS = 2;
    /** Chance of awakening per successful condition check (5%) — overridden by config */
    private static final float AWAKENING_CHANCE = 0.05f;
    /** Duration of the awakening freeze effect in check-calls (3 calls × 20 ticks/call = 60 ticks = 3 seconds) */
    private static final int FREEZE_DURATION = 3;
    /** Chunk heat increase on awakening */
    private static final float HEAT_INCREASE_ON_AWAKENING = 15.0f;
    /** UUID for the awakening health boost AttributeModifier */
    private static final UUID HEALTH_BOOST_UUID = UUID.fromString("c9d0e1f2-a3b4-5678-9012-cdef78901234");
    /** Name for the awakening health boost AttributeModifier */
    private static final String HEALTH_BOOST_NAME = "EliteForge Awakening Health";

    private EliteAwakening() {
        // Utility class - no instantiation
    }

    /**
     * Periodic check for elite awakening. Called every tick for each elite.
     * <p>
     * Checks all conditions and triggers the awakening process if eligible.
     *
     * @param entity the elite entity to check
     * @param data   the elite data
     * @param level  the server level
     */
    public static void checkAwakening(LivingEntity entity, EliteData data, ServerLevel level) {
        if (entity.level().isClientSide()) return;

        // Handle ongoing awakening freeze
        CompoundTag nbt = entity.getPersistentData();
        if (nbt.getBoolean(KEY_IS_AWAKENING)) {
            tickAwakeningFreeze(entity, nbt);
            return;
        }

        // Only check LEGENDARY entities
        if (data.getQualityTier() != QualityTier.LEGENDARY) return;

        // Already a creator? Skip
        if (data.isCreatorEntity()) return;

        // Increment timer
        // Note: checkAwakening is called every 20 ticks from EliteEventHandler,
        // so we increment by 20 to match real tick time.
        int timer = nbt.getInt(KEY_AWAKENING_TIMER);
        timer += 20;
        nbt.putInt(KEY_AWAKENING_TIMER, timer);

        // Only check every configured interval (config is in seconds, convert to ticks)
        int checkIntervalTicks = (int)(EliteForgeConfig.SERVER.awakeningCheckInterval.get() * 20);
        if (timer < checkIntervalTicks) return;

        // Reset timer
        nbt.putInt(KEY_AWAKENING_TIMER, 0);

        // Check all conditions
        if (!checkConditions(entity, data, level)) return;

        // Roll for awakening chance (configurable)
        float awakeningChance = EliteForgeConfig.SERVER.awakeningChance.get().floatValue();
        if (ThreadLocalRandom.current().nextFloat() >= awakeningChance) {
            LOGGER.debug("Awakening check passed but roll failed for {}",
                    entity.getName().getString());
            return;
        }

        // Trigger awakening!
        triggerAwakening(entity, data, level);
    }

    /**
     * Check all awakening conditions for an entity.
     * C2: Now uses configurable thresholds (awakeningMinAliveTicks, awakeningMinHeat,
     * awakeningMinPlayerKills) instead of hardcoded constants.
     *
     * @param entity the elite entity
     * @param data   the elite data
     * @param level  the server level
     * @return true if all conditions are met
     */
    private static boolean checkConditions(LivingEntity entity, EliteData data, ServerLevel level) {
        // 1. Quality == LEGENDARY (already checked in caller, but double-check)
        if (data.getQualityTier() != QualityTier.LEGENDARY) return false;

        // 2. Entity alive > configurable minimum (C2: was hardcoded MIN_ALIVE_TICKS = 6000)
        int minAliveTicks = EliteForgeConfig.SERVER.awakeningMinAliveTicks.get();
        if (entity.tickCount <= minAliveTicks) return false;

        // 3. Chunk heat >= configurable minimum (C2: was hardcoded MIN_HEAT = 80.0f)
        ChunkHeatManager heatManager = ChunkHeatManager.get(level);
        ChunkPos chunkPos = new ChunkPos(entity.blockPosition());
        float heat = heatManager.getHeat(level, chunkPos);
        float minHeat = EliteForgeConfig.SERVER.awakeningMinHeat.get().floatValue();
        if (heat < minHeat) return false;

        // 4. Entity has killed >= configurable minimum players (C2: was hardcoded MIN_PLAYER_KILLS = 2)
        int minPlayerKills = EliteForgeConfig.SERVER.awakeningMinPlayerKills.get();
        if (data.getKillCount() < minPlayerKills) return false;

        // 5. Active creator count < max
        int activeCreatorCount = EliteEcosystem.getActiveCreatorCount(level);
        if (activeCreatorCount >= getMaxCreators()) return false;

        return true;
    }

    /**
     * Trigger the awakening process for an elite entity.
     * <p>
     * Process:
     * 1. Entity freezes for 3 seconds (Slowness X, Resistance V)
     * 2. "Fission" particle effect (large end_rod burst)
     * 3. Remove ALL existing abilities from EliteData
     * 4. Roll a random creator ability (level I)
     * 5. Set quality to MYTHIC
     * 6. Set isCreatorEntity=true, creatorAbilityId, creatorAbilityLevel
     * 7. Apply creator ability onApply()
     * 8. Full server announcement
     * 9. Chunk heat += 15
     * 10. Lightning bolt effect at entity position
     *
     * @param entity the elite entity
     * @param data   the elite data
     * @param level  the server level
     */
    private static void triggerAwakening(LivingEntity entity, EliteData data, ServerLevel level) {
        LOGGER.info("Triggering awakening for elite {} at {}",
                entity.getName().getString(), entity.blockPosition());

        CompoundTag nbt = entity.getPersistentData();

        // Step 1: Freeze the entity for 3 seconds (FREEZE_DURATION is in check-calls;
        // each call is ~20 ticks, so multiply by 20 for tick-based durations)
        int freezeDurationTicks = FREEZE_DURATION * 20;
        entity.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, freezeDurationTicks, 9, false, true));
        entity.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, freezeDurationTicks, 4, false, true));
        nbt.putBoolean(KEY_IS_AWAKENING, true);
        nbt.putInt(KEY_AWAKENING_FREEZE_TICKS, FREEZE_DURATION);

        // Step 2: "Fission" particle effect (large end_rod burst)
        spawnFissionParticles(level, entity);

        // Play dramatic sound
        level.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 1.5f, 0.5f);
    }

    /**
     * Tick the awakening freeze process. When the freeze completes,
     * execute the transformation.
     *
     * @param entity the frozen entity
     * @param nbt    the entity's persistent NBT data
     */
    private static void tickAwakeningFreeze(LivingEntity entity, CompoundTag nbt) {
        int freezeTicks = nbt.getInt(KEY_AWAKENING_FREEZE_TICKS);
        freezeTicks--;
        nbt.putInt(KEY_AWAKENING_FREEZE_TICKS, freezeTicks);

        // Continuously spawn particles during freeze
        if (entity.level() instanceof ServerLevel serverLevel && entity.tickCount % 5 == 0) {
            spawnFissionParticles(serverLevel, entity);
        }

        if (freezeTicks <= 0) {
            // Freeze complete - execute transformation
            nbt.putBoolean(KEY_IS_AWAKENING, false);
            nbt.putInt(KEY_AWAKENING_FREEZE_TICKS, 0);

            if (entity.level() instanceof ServerLevel serverLevel) {
                executeTransformation(entity, serverLevel);
            }
        }
    }

    /**
     * Execute the actual transformation from legendary elite to creator.
     *
     * @param entity      the elite entity
     * @param serverLevel the server level
     */
    private static void executeTransformation(LivingEntity entity, ServerLevel serverLevel) {
        // Safety check: the entity may have died or been removed during the 3-second
        // freeze period (despite Resistance V). Abort transformation if invalid.
        if (!entity.isAlive() || entity.isRemoved()) {
            LOGGER.warn("Aborting awakening transformation for {} — entity is no longer valid (alive={}, removed={})",
                    entity.getName().getString(), entity.isAlive(), entity.isRemoved());
            // Clean up NBT state so the entity doesn't remain stuck in awakening
            CompoundTag nbt = entity.getPersistentData();
            nbt.putBoolean(KEY_IS_AWAKENING, false);
            nbt.putInt(KEY_AWAKENING_FREEZE_TICKS, 0);
            return;
        }

        entity.getCapability(EliteCapability.CAPABILITY).ifPresent(cap -> {
            EliteData data = cap.getEliteData();

            // Step 3: Remove ALL existing abilities
            Collection<Ability> currentAbilities = new ArrayList<>();
            for (String abilityId : data.getAbilities().keySet()) {
                Ability ability = AbilityRegistry.getAbility(abilityId);
                if (ability != null) {
                    currentAbilities.add(ability);
                }
            }
            // Call onRemove for each ability (cleanup)
            for (Ability ability : currentAbilities) {
                try {
                    ability.onRemove(entity, data.getAbilities().get(ability.getIdString()));
                } catch (Exception e) {
                    LOGGER.error("Error cleaning up ability {} during awakening: {}",
                            ability.getIdString(), e.getMessage());
                }
            }
            // Clear all abilities from data
            for (String abilityId : new ArrayList<>(data.getAbilities().keySet())) {
                data.removeAbility(abilityId);
            }

            // Step 4: Roll a random creator ability (level I)
            var creatorAbilities = AbilityRegistry.getAbilitiesByCategory(AbilityCategory.CREATOR)
                    .stream()
                    .filter(Ability::isEnabled)
                    .toList();

            if (creatorAbilities.isEmpty()) {
                LOGGER.error("No creator abilities available for awakening transformation!");
                return;
            }

            Ability chosenAbility = creatorAbilities.get(ThreadLocalRandom.current().nextInt(creatorAbilities.size()));

            // Step 5: Set quality to MYTHIC
            data.setQualityTier(QualityTier.MYTHIC);

            // Step 6: Set creator fields
            data.setCreatorEntity(true);
            data.setCreatorAbilityId(chosenAbility.getIdString());
            data.setCreatorAbilityLevel(1);

            // Add the creator ability
            data.addAbility(chosenAbility.getIdString(), 1);

            // Apply mythic-level health boost using AttributeModifier
            // First, clean up old attribute modifiers from applyEliteModifiers
            // and DynamicStrengthening before applying the creator-tier boost.
            try {
                var healthAttr = entity.getAttribute(Attributes.MAX_HEALTH);
                if (healthAttr != null) {
                    // Remove all existing attribute modifiers (DynamicStrengthening, etc.)
                    for (AttributeModifier mod : new ArrayList<>(healthAttr.getModifiers())) {
                        healthAttr.removeModifier(mod);
                    }

                    // Reset the base value to the original (before applyEliteModifiers scaling).
                    // applyEliteModifiers sets base to: originalBase * (1 + level * healthPerLevel)
                    // Reverse: originalBase = currentBase / healthMultiplier
                    DifficultyMode mode = data.getSpawnMode();
                    float healthPerLevel = mode == DifficultyMode.CASUAL ? 0.20f : 0.30f;
                    float healthMultiplier = 1.0f + data.getLevel() * healthPerLevel;
                    double originalBase = healthAttr.getBaseValue() / healthMultiplier;
                    healthAttr.setBaseValue(originalBase);

                    // Now apply the awakening health boost on the clean base
                    healthAttr.addPermanentModifier(new AttributeModifier(
                            HEALTH_BOOST_UUID,
                            HEALTH_BOOST_NAME,
                            1.0, // MULTIPLY_BASE: +100% = 2x health (consistent with applyMythicModifiers)
                            AttributeModifier.Operation.MULTIPLY_BASE
                    ));
                    entity.setHealth((float) healthAttr.getValue());
                }
            } catch (Exception e) {
                // Attribute may not be available
            }

            // Step 7: Apply creator ability onApply()
            try {
                chosenAbility.onApply(entity, 1);
            } catch (Exception e) {
                LOGGER.error("Error applying creator ability {} during awakening: {}",
                        chosenAbility.getIdString(), e.getMessage());
            }

            // Update capability
            cap.setEliteData(data);
            EliteCapabilitySync.broadcastEliteDataUpdate(entity, data);

            // Register in ecosystem
            EliteEcosystem.registerCreator(entity, chosenAbility.getIdString(), 1);

            // Step 8: Full server announcement
            // C1: Now uses translatable key "message.eliteforge.awakening.announce"
            // with the entity name as %s parameter.
            String entityName = entity.getName().getString();
            Component announcement = Component.translatable("message.eliteforge.awakening.announce", entityName)
                    .withStyle(net.minecraft.ChatFormatting.RED,
                            net.minecraft.ChatFormatting.BOLD);

            for (ServerPlayer player : serverLevel.getServer().getPlayerList().getPlayers()) {
                if (com.eliteforge.config.EliteForgeConfig.SERVER.enableKillMessage.get()) player.sendSystemMessage(announcement);
            }

            // Step 9: Chunk heat += 15
            ChunkHeatManager heatManager = ChunkHeatManager.get(serverLevel);
            ChunkPos chunkPos = new ChunkPos(entity.blockPosition());
            heatManager.addHeat(serverLevel, chunkPos, HEAT_INCREASE_ON_AWAKENING);

            // Step 10: Lightning bolt effect at entity position
            spawnLightningEffect(serverLevel, entity);

            LOGGER.info("Elite {} has awakened as creator with ability {}!",
                    entityName, chosenAbility.getIdString());
        });
    }

    // ==================== Visual Effects ====================

    /**
     * Spawn "fission" particle effect — a large burst of end_rod particles
     * radiating outward from the entity.
     *
     * @param level  the server level
     * @param entity the entity
     */
    private static void spawnFissionParticles(ServerLevel level, LivingEntity entity) {
        double x = entity.getX();
        double y = entity.getY() + entity.getBbHeight() * 0.5;
        double z = entity.getZ();

        // Large central burst
        level.sendParticles(ParticleTypes.END_ROD, x, y, z, 50, 1.5, entity.getBbHeight() * 0.5, 1.5, 0.1);

        // Expanding ring of particles
        int ringPoints = 24;
        for (int i = 0; i < ringPoints; i++) {
            double angle = (Math.PI * 2 * i) / ringPoints + entity.tickCount * 0.1;
            double radius = 2.0 + ThreadLocalRandom.current().nextDouble() * 0.5;
            double px = x + Math.cos(angle) * radius;
            double pz = z + Math.sin(angle) * radius;
            level.sendParticles(ParticleTypes.END_ROD, px, y, pz, 2, 0, 0.1, 0, 0);
        }

        // Upward beam effect
        for (int i = 0; i < 10; i++) {
            double py = y + i * 0.5;
            level.sendParticles(ParticleTypes.END_ROD, x, py, z, 3, 0.1, 0.1, 0.1, 0);
        }
    }

    /**
     * Spawn a lightning bolt visual effect at the entity's position.
     * Uses actual lightning entity for dramatic effect.
     *
     * @param level  the server level
     * @param entity the entity
     */
    private static void spawnLightningEffect(ServerLevel level, LivingEntity entity) {
        // Spawn a visual-only lightning bolt
        var lightning = net.minecraft.world.entity.EntityType.LIGHTNING_BOLT.create(level);
        if (lightning != null) {
            lightning.moveTo(entity.getX(), entity.getY(), entity.getZ());
            lightning.setVisualOnly(true);
            level.addFreshEntity(lightning);
        }

        // Additional dramatic particles
        level.sendParticles(ParticleTypes.DRAGON_BREATH,
                entity.getX(), entity.getY() + entity.getBbHeight() * 0.5, entity.getZ(),
                30, 2.0, entity.getBbHeight() * 0.5, 2.0, 0.05);

        // Sound effect
        level.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.HOSTILE, 2.0f, 0.8f);
        level.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                SoundEvents.WARDEN_ANGRY, SoundSource.HOSTILE, 1.0f, 1.5f);
    }

    // ==================== Config Helpers ====================

    /**
     * Remove the awakening health boost modifier from an entity.
     * Should be called during cleanup (e.g., entity death, dimension change, etc.).
     *
     * @param entity the entity to remove the health boost from
     */
    public static void removeHealthBoost(LivingEntity entity) {
        try {
            var healthAttr = entity.getAttribute(Attributes.MAX_HEALTH);
            if (healthAttr != null) {
                healthAttr.removeModifier(HEALTH_BOOST_UUID);
                // Clamp health to new max if needed
                if (entity.getHealth() > healthAttr.getValue()) {
                    entity.setHealth((float) healthAttr.getValue());
                }
            }
        } catch (Exception e) {
            // Attribute may not be available
        }
    }

    /**
     * Get the maximum number of active creators allowed for awakening.
     *
     * @return max creator count (default 2)
     */
    private static int getMaxCreators() {
        return EliteForgeConfig.SERVER.maxCreatorEntities.get();
    }
}
