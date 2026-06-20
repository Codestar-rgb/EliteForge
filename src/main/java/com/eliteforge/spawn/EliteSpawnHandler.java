package com.eliteforge.spawn;

import com.eliteforge.EliteForge;
import com.eliteforge.ability.Ability;
import com.eliteforge.ability.AbilityCategory;
import com.eliteforge.ability.AbilityRegistry;
import com.eliteforge.capability.EliteCapability;
import com.eliteforge.capability.EliteData;
import com.eliteforge.capability.EliteCapabilitySync;
import com.eliteforge.config.DifficultyMode;
import com.eliteforge.config.EliteForgeConfig;
import com.eliteforge.difficulty.ChunkHeatManager;
import com.eliteforge.difficulty.DifficultyManager;
import com.eliteforge.difficulty.PlayerExperienceManager;
// QualityHelper removed in v0.2.0
import com.eliteforge.quality.QualityTier;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

import com.eliteforge.spawn.EliteRevenge;
import com.eliteforge.util.NBTKeys;

/**
 * Handles the EntityJoinLevelEvent to convert qualifying entities into elites.
 * <p>
 * Spawn logic varies by difficulty mode:
 * <ul>
 *   <li>FORGE: Always attempt spawn if chance passes</li>
 *   <li>CASUAL: Only attempt if no elite within 48 blocks, max 2 abilities,
 *       no legendary abilities, reduced health bonus, despawn after 5 min if not engaged</li>
 *   <li>MIXED: Use both rules with respective chances (80% FORGE, 20% CASUAL)</li>
 * </ul>
 * <p>
 * Phase 6 CASUAL mode enhancements:
 * - Elites never have legendary abilities
 * - Max 2 abilities per elite
 * - Elite health bonus reduced to +20% per level (vs +30% in FORGE)
 * - Elites despawn naturally after 5 minutes if not engaged
 * - No chunk heat effect on spawn difficulty
 * - Spawn check: only 1 elite within 48 blocks
 */
public class EliteSpawnHandler {

    private static final Logger LOGGER = LogManager.getLogger();

    // C2: CASUAL mode despawn timer is now configurable via EliteForgeConfig.SERVER.casualDespawnTicks.
    // This constant is kept only as a fallback default for documentation; the actual
    // value is read from config at spawn time. Default: 6000 ticks = 5 minutes.
    private static final int CASUAL_DESPAWN_TICKS_DEFAULT = 6000;

    /**
     * Handle an entity joining a level. This is the main entry point for elite
     * mob conversion.
     *
     * @param event the entity join level event
     */
    public void onEntityJoinLevel(EntityJoinLevelEvent event) {
        // Only process on server side
        if (event.getLevel().isClientSide()) {
            return;
        }

        // Only process LivingEntity types
        if (!(event.getEntity() instanceof LivingEntity livingEntity)) {
            return;
        }

        // Don't process players
        if (livingEntity instanceof Player) {
            return;
        }

        // Only process ServerLevel
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }

        // Check if already elite (prevent double-processing)
        if (livingEntity.getCapability(EliteCapability.CAPABILITY).map(EliteCapability::isElite).orElse(false)) {
            return;
        }

        // Check if entity should spawn as elite (returns effective mode for consistency)
        DifficultyManager.SpawnCheckResult spawnResult = DifficultyManager.INSTANCE.shouldSpawnAsElite(livingEntity, serverLevel);
        if (!spawnResult.shouldSpawn()) {
            // ===== DOMINION ZONE BONUS SPAWN CHECK =====
            // Design: "20% of mobs spawning inside a dominion zone become elite"
            // If the normal spawn check failed, do a second check specifically for
            // dominion zones. If the entity is within range of a creator with the
            // dominion ability and the dominion is active, roll a configurable chance
            // (dominionEliteSpawnBonus, default 20%) to convert to elite regardless of
            // the normal spawn chance.
            if (isInActiveDominionZone(livingEntity, serverLevel)) {
                if (serverLevel.random.nextDouble() < EliteForgeConfig.SERVER.dominionEliteSpawnBonus.get()) {
                    // Use FORGE mode as the effective mode for dominion-spawned elites
                    // since dominion zones always use FORGE rules (no CASUAL restrictions)
                    convertToElite(livingEntity, serverLevel, DifficultyMode.FORGE);
                }
            }
            return;
        }

        // Cancel if the event was already canceled
        if (event.isCanceled()) {
            return;
        }

        // Convert entity to elite using the pre-rolled effective mode
        convertToElite(livingEntity, serverLevel, spawnResult.effectiveMode());
    }

    /**
     * Convert a regular entity into an elite mob.
     * <p>
     * Steps:
     * 1. Calculate difficulty level
     * 2. Generate abilities using AbilityGenerator
     * 3. Apply CASUAL mode restrictions (no legendary, max 2 abilities)
     * 4. Determine quality tier
     * 5. Set capability data
     * 6. Apply elite modifiers (health, damage, name, glow)
     * 7. Fire particle effects
     * 8. Play spawn sound
     * 9. Spawn announcement for EPIC+ elites
     *
     * @param entity      the entity to convert
     * @param serverLevel the server level
     * @param mode        the difficulty mode
     */
    private QualityTier determineQuality(int level, float heat, float exp) {
        // Simple quality determination based on level and heat
        float totalScore = level * 10 + heat + exp * 5;
        if (totalScore > 500) return QualityTier.MYTHIC;
        if (totalScore > 300) return QualityTier.LEGENDARY;
        if (totalScore > 150) return QualityTier.EPIC;
        if (totalScore > 80) return QualityTier.FINE;
        if (totalScore > 30) return QualityTier.GOOD;
        return QualityTier.NORMAL;
    }

    private void convertToElite(LivingEntity entity, ServerLevel serverLevel, DifficultyMode mode) {
        // 1. Calculate difficulty level using the pre-rolled effective mode
        //    to ensure consistency with the spawn eligibility check
        int difficultyLevel = DifficultyManager.INSTANCE.calculateDifficultyLevel(entity, serverLevel, mode);

        // 2. Generate abilities
        List<Pair<Ability, Integer>> abilities = AbilityGenerator.generateAbilities(
                difficultyLevel, mode, entity.getType()
        );

        // 3. Apply CASUAL mode restrictions
        if (mode == DifficultyMode.CASUAL) {
            abilities = applyCasualRestrictions(abilities);
        }

        // 4. Determine quality tier
        ChunkHeatManager heatManager = ChunkHeatManager.get(serverLevel);
        ChunkPos chunkPos = new ChunkPos(entity.blockPosition());
        float chunkHeat = heatManager.getHeat(serverLevel, chunkPos);

        // Get nearest player experience for quality calculation
        float playerExp = 0.0f;
        Player nearestPlayer = serverLevel.getNearestPlayer(entity, 64.0);
        if (nearestPlayer instanceof ServerPlayer serverPlayer) {
            PlayerExperienceManager expManager = PlayerExperienceManager.get(serverLevel);
            playerExp = expManager.getPlayerExperience(serverPlayer);
        }

        QualityTier quality = determineQuality(difficultyLevel, chunkHeat, playerExp);

        // Check for forced quality from entity preset (datapack override)
        com.eliteforge.datapack.EntityPreset presetOverride =
                com.eliteforge.datapack.EntityPresetLoader.getInstance().getPreset(entity.getType());
        if (presetOverride != null && presetOverride.getForcedQuality() != null) {
            quality = QualityTier.fromName(presetOverride.getForcedQuality());
        }

        // Check for forced level from entity preset
        if (presetOverride != null && presetOverride.getForcedLevel() != null) {
            // Clamp to the configured max level (was hardcoded 1-5, which broke the
            // v0.2.0 1-1500 level system for datapack authors).
            int maxLvl = EliteForgeConfig.COMMON.maxEliteLevel.get();
            difficultyLevel = Math.max(1, Math.min(maxLvl, presetOverride.getForcedLevel()));
        }

        // Apply per-entity config overrides (entityOverrides list) — these take
        // precedence over datapack presets for operator convenience.
        com.eliteforge.config.EntityOverrideManager.Override cfgOverride =
                com.eliteforge.config.EntityOverrideManager.get(entity.getType());
        if (cfgOverride.forcedQuality != null) {
            quality = cfgOverride.forcedQuality;
        }
        if (cfgOverride.forcedLevel != null) {
            // Clamp to the configured max level so a typo'd forcedLevel=99999 doesn't
            // spawn a 60000-HP zombie.
            int maxLvl = com.eliteforge.config.EliteForgeConfig.COMMON.maxEliteLevel.get();
            difficultyLevel = Math.max(1, Math.min(maxLvl, cfgOverride.forcedLevel));
        }

        // Check for forced quality from revenge system
        QualityTier forcedQuality = EliteRevenge.consumeForcedQuality(chunkPos);
        if (forcedQuality != null) {
            quality = forcedQuality;
        }

        // ===== CREATOR-TIER CHECK =====
        // After determining quality, check if this elite should be creator-tier.
        // Preset can override creator-tier eligibility per-entity-type (creatorAllowed).
        if (quality.ordinal() >= QualityTier.LEGENDARY.ordinal()
                && EliteForgeConfig.SERVER.enableCreatorTier.get()
                && mode != DifficultyMode.CASUAL) {
            // Check per-entity preset override for creator eligibility
            com.eliteforge.datapack.EntityPreset preset =
                    com.eliteforge.datapack.EntityPresetLoader.getInstance().getPreset(entity.getType());
            Boolean creatorAllowed = (preset != null) ? preset.getCreatorAllowed() : null;
            // creatorAllowed=null means use global setting (already checked above)
            // creatorAllowed=false means this entity type cannot become creator
            if (creatorAllowed == null || creatorAllowed) {
                if (EliteEcosystem.shouldSpawnAsCreator(entity, serverLevel, mode, quality)) {
                    boolean creatorSuccess = convertToCreator(entity, serverLevel, mode, difficultyLevel, chunkHeat);
                    if (creatorSuccess) {
                        return; // Skip normal conversion — creator conversion succeeded
                    }
                    // Creator conversion failed (e.g. no creator abilities registered);
                    // fall through to normal elite conversion below
                }
            }
        }

        // Create final copies for lambda capture
        final QualityTier finalQuality = quality;
        final List<Pair<Ability, Integer>> finalAbilities = abilities;
        final int finalDifficultyLevel = difficultyLevel;

        // 5. Set capability data
        entity.getCapability(EliteCapability.CAPABILITY).ifPresent(cap -> {
            EliteData data = new EliteData();
            data.setElite(true);
            data.setLevel(finalDifficultyLevel);
            data.setQualityTier(finalQuality);
            data.setSpawnMode(mode);
            data.setChunkHeatAtSpawn(chunkHeat);

            // Store abilities in data
            for (Pair<Ability, Integer> abilityPair : finalAbilities) {
                data.addAbility(abilityPair.getFirst().getIdString(), abilityPair.getSecond());
            }

            // CASUAL mode: set despawn timer if not engaged
            // C2: Read from config (default 6000 ticks = 5 minutes, 0 = disabled)
            if (mode == DifficultyMode.CASUAL) {
                int despawnTicks = EliteForgeConfig.SERVER.casualDespawnTicks.get();
                if (despawnTicks > 0) {
                    data.setDespawnTimer(despawnTicks);
                }
            }

            cap.setEliteData(data);

            LOGGER.debug("Converted {} to elite (level={}, quality={}, mode={}, abilities={})",
                    entity.getName().getString(), finalDifficultyLevel, finalQuality, mode, finalAbilities.size());
        });

        // 6. Apply elite modifiers (health, damage, name, glow, ability effects)
        DifficultyManager.INSTANCE.applyEliteModifiers(entity, finalDifficultyLevel, finalAbilities);

        // 6b. Auto-equip elite with quality-scaled gear and enchantments
        // Equipment system removed in v0.2.0

        // 7. Add heat to the chunk for the spawn
        heatManager.onEliteSpawn(serverLevel, entity.blockPosition());

        // 8. Fire particle effects
        spawnParticles(serverLevel, entity);

        // 9. Play spawn sound
        playSpawnSound(serverLevel, entity);

        // 10. Spawn announcement for EPIC+ elites
        announceEliteSpawn(serverLevel, entity, quality);

        // 11. Sync to clients
        entity.getCapability(EliteCapability.CAPABILITY).ifPresent(cap -> {
            EliteCapabilitySync.broadcastEliteDataUpdate(entity, cap.getEliteData());
        });
    }

    /**
     * Apply CASUAL mode restrictions to the generated ability list.
     * - No legendary abilities
     * - Max 2 abilities
     *
     * @param abilities the original ability list
     * @return the restricted ability list
     */
    private List<Pair<Ability, Integer>> applyCasualRestrictions(List<Pair<Ability, Integer>> abilities) {
        return abilities.stream()
                .filter(pair -> pair.getFirst().getCategory() != AbilityCategory.LEGENDARY)
                .limit(2)
                .toList();
    }

    /**
     * Announce elite spawn to nearby players for EPIC+ quality elites.
     * C1: Now uses translatable keys for all three quality tiers:
     *   - message.eliteforge.spawn.epic (EPIC)
     *   - message.eliteforge.spawn.legendary (LEGENDARY)
     *   - message.eliteforge.spawn.mythic (MYTHIC, takes entity name as %s)
     * Also plays warning sound and adds brief glow effect.
     *
     * @param level   the server level
     * @param entity  the elite entity
     * @param quality the quality tier
     */
    private void announceEliteSpawn(ServerLevel level, LivingEntity entity, QualityTier quality) {
        if (!com.eliteforge.config.EliteForgeConfig.SERVER.enableSpawnAnnouncement.get()) {
            return;
        }
        if (quality.ordinal() < QualityTier.EPIC.ordinal()) {
            return; // Only announce EPIC and above
        }

        MutableComponent message;
        if (quality == QualityTier.MYTHIC) {
            String entityName = entity.getName().getString();
            message = Component.translatable("message.eliteforge.spawn.mythic", entityName)
                    .withStyle(net.minecraft.ChatFormatting.DARK_RED, net.minecraft.ChatFormatting.BOLD);
        } else if (quality == QualityTier.LEGENDARY) {
            message = Component.translatable("message.eliteforge.spawn.legendary")
                    .withStyle(net.minecraft.ChatFormatting.GOLD, net.minecraft.ChatFormatting.BOLD);
        } else {
            message = Component.translatable("message.eliteforge.spawn.epic")
                    .withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE);
        }

        // Send to nearby players within 64 blocks
        for (ServerPlayer player : level.getPlayers(p -> p.distanceTo(entity) < 64.0)) {
            player.sendSystemMessage(message);

            // Play warning sound to the player
            level.playSound(
                    null,
                    player.getX(), player.getY(), player.getZ(),
                    quality == QualityTier.LEGENDARY
                            ? SoundEvents.WARDEN_ANGRY
                            : SoundEvents.RAID_HORN.value(),
                    SoundSource.HOSTILE,
                    0.8f, 1.2f
            );
        }

        // Add brief glow effect to the elite (3 seconds)
        // Removed brief GLOWING effect per user feedback
    }

    /**
     * Spawn dramatic particle effects when an elite mob is created.
     *
     * @param level  the server level
     * @param entity the elite entity
     */
    private void spawnParticles(ServerLevel level, LivingEntity entity) {
        double x = entity.getX();
        double y = entity.getY() + entity.getBbHeight() * 0.5;
        double z = entity.getZ();

        // Burst of particles around the entity
        int particleCount = 15 + entity.getRandom().nextInt(10);
        for (int i = 0; i < particleCount; i++) {
            double offsetX = (entity.getRandom().nextDouble() - 0.5) * 2.0;
            double offsetY = (entity.getRandom().nextDouble() - 0.5) * 2.0;
            double offsetZ = (entity.getRandom().nextDouble() - 0.5) * 2.0;

            // Use different particle types based on quality
            QualityTier quality = entity.getCapability(EliteCapability.CAPABILITY)
                    .map(cap -> cap.getEliteData().getQualityTier())
                    .orElse(QualityTier.NORMAL);

            switch (quality) {
                case MYTHIC -> level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                        x + offsetX, y + offsetY, z + offsetZ, 1, 0, 0, 0, 0.02);
                case LEGENDARY -> level.sendParticles(ParticleTypes.DRAGON_BREATH,
                        x + offsetX, y + offsetY, z + offsetZ, 1, 0, 0, 0, 0.02);
                case EPIC -> level.sendParticles(ParticleTypes.WITCH,
                        x + offsetX, y + offsetY, z + offsetZ, 1, 0, 0, 0, 0.02);
                case FINE -> level.sendParticles(ParticleTypes.ENCHANT,
                        x + offsetX, y + offsetY, z + offsetZ, 1, 0, 0, 0, 0.02);
                case GOOD -> level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                        x + offsetX, y + offsetY, z + offsetZ, 1, 0, 0, 0, 0.02);
                default -> level.sendParticles(ParticleTypes.POOF,
                        x + offsetX, y + offsetY, z + offsetZ, 1, 0, 0, 0, 0.02);
            }
        }

        // Ring of particles at the base
        for (int angle = 0; angle < 360; angle += 15) {
            double rad = Math.toRadians(angle);
            double radius = 1.5;
            double px = x + Math.cos(rad) * radius;
            double pz = z + Math.sin(rad) * radius;
            level.sendParticles(ParticleTypes.END_ROD, px, y - entity.getBbHeight() * 0.4, pz,
                    1, 0, 0.1, 0, 0.01);
        }
    }

    /**
     * Play a spawn sound when an elite mob is created.
     *
     * @param level  the server level
     * @param entity the elite entity
     */
    private void playSpawnSound(ServerLevel level, LivingEntity entity) {
        // Different sounds based on quality tier
        QualityTier quality = entity.getCapability(EliteCapability.CAPABILITY)
                .map(cap -> cap.getEliteData().getQualityTier())
                .orElse(QualityTier.NORMAL);

        float pitch = switch (quality) {
            case MYTHIC -> 0.3f;
            case LEGENDARY -> 0.5f;
            case EPIC -> 0.7f;
            case FINE -> 0.9f;
            case GOOD -> 1.0f;
            default -> 1.2f;
        };

        level.playSound(
                null,
                entity.getX(), entity.getY(), entity.getZ(),
                SoundEvents.EVOKER_PREPARE_SUMMON,
                SoundSource.HOSTILE,
                1.0f, pitch
        );

        // Extra dramatic sound for MYTHIC and legendary
        if (quality == QualityTier.MYTHIC) {
            level.playSound(
                    null,
                    entity.getX(), entity.getY(), entity.getZ(),
                    SoundEvents.WARDEN_ANGRY,
                    SoundSource.HOSTILE,
                    2.0f, 0.3f
            );
            level.playSound(
                    null,
                    entity.getX(), entity.getY(), entity.getZ(),
                    SoundEvents.LIGHTNING_BOLT_THUNDER,
                    SoundSource.HOSTILE,
                    1.5f, 0.5f
            );
        } else if (quality == QualityTier.LEGENDARY) {
            level.playSound(
                    null,
                    entity.getX(), entity.getY(), entity.getZ(),
                    SoundEvents.WARDEN_ANGRY,
                    SoundSource.HOSTILE,
                    0.5f, 1.5f
            );
        }
    }

    // ==================== Dominion Zone Check ====================

    /**
     * Check if an entity is within an active Dominion zone.
     * <p>
     * Looks for nearby creator entities with the {@code creator_dominion} ability
     * whose dominion is currently active (NBT key {@code EliteForgeDominionActive}).
     * Uses {@link EliteEcosystem#getNearbyCreators} for efficient creator lookup.
     *
     * @param entity      the entity to check
     * @param serverLevel the server level
     * @return true if the entity is within range of an active dominion creator
     */
    private boolean isInActiveDominionZone(LivingEntity entity, ServerLevel serverLevel) {
        // Check all nearby creators within max dominion range (40 blocks) + buffer
        List<EliteEcosystem.CreatorInfo> nearbyCreators =
                EliteEcosystem.getNearbyCreators(serverLevel, entity.blockPosition(), 45.0);

        for (EliteEcosystem.CreatorInfo creatorInfo : nearbyCreators) {
            if ("eliteforge:creator_dominion".equals(creatorInfo.abilityId())) {
                // Look up the creator entity to check if the dominion is active
                var creatorEntity = serverLevel.getEntity(creatorInfo.entityUUID());
                if (creatorEntity instanceof LivingEntity creatorLiving && creatorLiving.isAlive()) {
                    net.minecraft.nbt.CompoundTag creatorNbt = creatorLiving.getPersistentData();
                    if (creatorNbt.getBoolean(NBTKeys.DOMINION_ACTIVE)) {
                        // Verify the entity is within the dominion's actual range
                        // Dominion range: level 1 = 20, level 2 = 30, level 3 = 40
                        double range = switch (creatorInfo.level()) {
                            case 1 -> 20.0;
                            case 2 -> 30.0;
                            default -> 40.0;
                        };
                        double dist = entity.blockPosition().distSqr(creatorLiving.blockPosition());
                        if (dist <= range * range) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    // ==================== Creator-Tier Conversion ====================

    /**
     * Convert a regular entity into a creator-tier elite.
     * Replaces all normal abilities with a single creator ability.
     *
     * @param entity          the entity to convert
     * @param serverLevel     the server level
     * @param mode            the difficulty mode
     * @param difficultyLevel the difficulty level
     * @param chunkHeat       the chunk heat at spawn
     * @return true if creator conversion succeeded, false if it should fall back to normal conversion
     */
    private boolean convertToCreator(LivingEntity entity, ServerLevel serverLevel, DifficultyMode mode,
                                   int difficultyLevel, float chunkHeat) {
        // 1. Select a random creator ability
        java.util.Collection<Ability> creatorAbilities = com.eliteforge.ability.AbilityRegistry.getAbilitiesByCategory(
                com.eliteforge.ability.AbilityCategory.CREATOR);
        if (creatorAbilities.isEmpty()) {
            LOGGER.warn("No creator abilities registered, falling back to normal elite");
            return false;
        }

        java.util.List<Ability> creatorList = new java.util.ArrayList<>(creatorAbilities);
        // NOTE: This deliberately uses `new Random(uuid.hashCode())` instead of the
        // project-wide ThreadLocalRandom convention. The reason is REPRODUCIBILITY:
        // seeding from the entity's UUID guarantees that the SAME entity always
        // converts to the SAME creator ability on re-conversion (e.g. after a
        // server restart, chunk reload, or rollback). This is important so that
        // a world's "creator-tier population" stays stable across reloads and
        // players cannot reroll a creator elite's identity by reloading chunks.
        // Do NOT "fix" this to ThreadLocalRandom without understanding the
        // reproducibility contract.
        java.util.Random random = new java.util.Random(entity.getUUID().hashCode());
        Ability creatorAbility = creatorList.get(random.nextInt(creatorList.size()));
        int creatorLevel = 1; // Creator abilities start at level I

        // 2. Set capability data
        // CRITICAL: Check capability presence BEFORE applying any modifiers or side effects.
        // If the capability is missing, return false so the caller falls back to normal
        // conversion. Otherwise the entity would get mythic stat scaling and announcements
        // but no actual elite data, creator ability, or client sync — leaving it broken.
        var capabilityOpt = entity.getCapability(EliteCapability.CAPABILITY);
        if (!capabilityOpt.isPresent()) {
            LOGGER.warn("Entity {} has no EliteCapability, cannot convert to creator-tier",
                    entity.getName().getString());
            return false;
        }
        EliteCapability cap = capabilityOpt.orElse(null);
        EliteData data = new EliteData();
        data.setElite(true);
        data.setLevel(difficultyLevel);
        data.setQualityTier(QualityTier.MYTHIC);
        data.setSpawnMode(mode);
        data.setChunkHeatAtSpawn(chunkHeat);

        // Creator entity: only 1 ability (the creator ability)
        data.addAbility(creatorAbility.getIdString(), creatorLevel);
        data.setCreatorEntity(true);
        data.setCreatorAbilityId(creatorAbility.getIdString());
        data.setCreatorAbilityLevel(creatorLevel);

        cap.setEliteData(data);
        // Explicitly set the elite flag on the capability for robustness.
        cap.setElite(true);

        // Apply the creator ability
        try {
            creatorAbility.onApply(entity, creatorLevel);
        } catch (Exception e) {
            LOGGER.error("Error applying creator ability {}: {}", creatorAbility.getIdString(), e.getMessage());
        }

        LOGGER.info("Converted {} to CREATOR-tier elite with {} (level {})",
                entity.getName().getString(), creatorAbility.getIdString(), creatorLevel);

        // 3. Apply MYTHIC-quality modifiers (+100% health, +50% damage, +20% speed)
        applyMythicModifiers(entity);

        // 4. Add heat to the chunk
        ChunkHeatManager heatManager = ChunkHeatManager.get(serverLevel);
        heatManager.addHeat(serverLevel, new net.minecraft.world.level.ChunkPos(entity.blockPosition()), 20.0f);

        // 5. Dramatic creator spawn effects
        spawnCreatorEffects(serverLevel, entity);

        // 6. Full server announcement
        announceCreatorSpawn(serverLevel, entity, creatorAbility);

        // 7. Register in ecosystem
        EliteEcosystem.registerCreator(entity, creatorAbility.getIdString(), creatorLevel);

        // 8. Sync to clients
        EliteCapabilitySync.broadcastEliteDataUpdate(entity, cap.getEliteData());

        return true;
    }

    /**
     * Apply MYTHIC-quality attribute modifiers to an entity.
     * +100% max health, +50% attack damage, +20% movement speed
     */
    private void applyMythicModifiers(LivingEntity entity) {
        try {
            var healthAttr = entity.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
            if (healthAttr != null) {
                healthAttr.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                        java.util.UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890"),
                        "EliteForge Mythic Health",
                        1.0, // +100% using MULTIPLY_BASE (consistent with damage/speed)
                        net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.MULTIPLY_BASE
                ));
            }
            var damageAttr = entity.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
            if (damageAttr != null) {
                damageAttr.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                        java.util.UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901"),
                        "EliteForge Mythic Damage",
                        0.5, // +50%
                        net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.MULTIPLY_BASE
                ));
            }
            var speedAttr = entity.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
            if (speedAttr != null) {
                speedAttr.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                        java.util.UUID.fromString("c3d4e5f6-a7b8-9012-cdef-123456789012"),
                        "EliteForge Mythic Speed",
                        0.2, // +20%
                        net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.MULTIPLY_BASE
                ));
            }
        } catch (Exception e) {
            LOGGER.error("Error applying MYTHIC modifiers: {}", e.getMessage());
        }
    }

    /**
     * Dramatic spawn effects for creator-tier entities.
     * Lightning, dragon breath particles, screen shake simulation.
     */
    private void spawnCreatorEffects(ServerLevel level, LivingEntity entity) {
        double x = entity.getX();
        double y = entity.getY();
        double z = entity.getZ();

        // Lightning bolt at entity position
        var lightning = net.minecraft.world.entity.EntityType.LIGHTNING_BOLT.create(level);
        if (lightning != null) {
            lightning.moveTo(Vec3.atBottomCenterOf(entity.blockPosition()));
            lightning.setVisualOnly(true);
            level.addFreshEntity(lightning);
        }

        // Massive dragon breath burst
        level.sendParticles(ParticleTypes.DRAGON_BREATH,
                x, y + entity.getBbHeight() * 0.5, z,
                40, 2.0, entity.getBbHeight(), 2.0, 0.1);

        // End rod spiral
        for (int angle = 0; angle < 360; angle += 10) {
            double rad = Math.toRadians(angle);
            double radius = 3.0;
            double px = x + Math.cos(rad) * radius;
            double pz = z + Math.sin(rad) * radius;
            level.sendParticles(ParticleTypes.END_ROD, px, y, pz, 1, 0, 0.3, 0, 0);
        }

        // Soul fire flame column
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                x, y + entity.getBbHeight(), z,
                30, 0.5, entity.getBbHeight(), 0.5, 0.05);

        // Sound effects
        level.playSound(null, x, y, z,
                SoundEvents.WARDEN_ANGRY, SoundSource.HOSTILE, 1.5f, 0.5f);
        level.playSound(null, x, y, z,
                SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.HOSTILE, 1.0f, 0.8f);

        // Apply "fear" effect to nearby players (Slowness III for 5 seconds)
        for (ServerPlayer player : level.getPlayers(p -> p.distanceTo(entity) < 32.0)) {
            player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 100, 2, false, true));
        }
    }

    /**
     * Announce creator-tier spawn to all players on the server.
     * Q6: Kept the server-wide scope (all dimensions) since creator spawns are
     * rare server-wide events. Documented the intent for clarity.
     * C1: Now uses translatable key "message.eliteforge.creator.spawn_announce"
     * with parameters (entityName, posX, posZ) instead of hardcoded Chinese string.
     */
    private void announceCreatorSpawn(ServerLevel level, LivingEntity entity, Ability creatorAbility) {
        if (!com.eliteforge.config.EliteForgeConfig.SERVER.enableSpawnAnnouncement.get()) return;
         String entityName = entity.getName().getString();
        int posX = entity.blockPosition().getX();
        int posZ = entity.blockPosition().getZ();

        // C1: Use Component.translatable with positional args.
        // en_us: "⚔ Creator-tier elite %s has descended at [%d, %d]! ⚔"
        // zh_cn: "⚔ 造物主级精英 %s 已在 [%d, %d] 降临！ ⚔"
        net.minecraft.network.chat.MutableComponent message = Component.translatable(
                "message.eliteforge.creator.spawn_announce", entityName, posX, posZ)
                .withStyle(net.minecraft.ChatFormatting.DARK_RED, net.minecraft.ChatFormatting.BOLD);

        // Server-wide announcement: iterate ALL players across ALL dimensions.
        // Creator spawns are rare (< 1% of elite spawns) and represent major
        // server events, so every player should be notified regardless of dimension.
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            player.sendSystemMessage(message);
        }
    }
}
