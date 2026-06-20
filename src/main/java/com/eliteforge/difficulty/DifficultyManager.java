package com.eliteforge.difficulty;

import com.eliteforge.ability.Ability;
import com.eliteforge.capability.EliteCapability;
import com.eliteforge.capability.EliteData;
import com.eliteforge.config.DifficultyMode;
import com.eliteforge.config.EliteForgeConfig;
import com.eliteforge.quality.QualityTier;
import com.mojang.datafixers.util.Pair;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Core difficulty calculation manager. Determines elite spawn eligibility,
 * difficulty levels, and applies elite modifiers to entities.
 * <p>
 * Phase 6 optimizations:
 * - Smooth difficulty interpolation instead of sharp level jumps
 * - Each 10 chunk heat = +0.4 level modifier
 * - Each 10 player exp = +0.3 level modifier
 * - Difficulty fatigue: reduce nearby heat by 5 on player death to elite
 */
public class DifficultyManager {

    /** Shared singleton instance for use across the mod. */
    public static final DifficultyManager INSTANCE = new DifficultyManager();

    private static final Logger LOGGER = LogManager.getLogger();

    /**
     * Result of a spawn eligibility check, including the rolled effective mode.
     * This ensures that the same effective mode is used for both the spawn
     * decision and subsequent difficulty calculation, preventing inconsistency
     * when the base mode is MIXED (which involves a random roll).
     */
    public record SpawnCheckResult(boolean shouldSpawn, DifficultyMode effectiveMode) {}

    // Glow color mappings per level (for the Glowing effect color)
    private static final int[] GLOW_COLORS = {
            0xFFFFFF, // Level 1 - White
            0x55FF55, // Level 2 - Green
            0x5555FF, // Level 3 - Blue
            0xFF55FF, // Level 4 - Magenta
            0xFFAA00  // Level 5 - Gold
    };

    // Level display symbols for custom names
    private static final String[] LEVEL_SYMBOLS = {
            "\u2605",           // Level 1 - ★
            "\u2605\u2605",     // Level 2 - ★★
            "\u2605\u2605\u2605", // Level 3 - ★★★
            "\u2605\u2605\u2605\u2605", // Level 4 - ★★★★
            "\u2605\u2605\u2605\u2605\u2605"  // Level 5 - ★★★★★
    };

    // Health bonus per level: +30% in FORGE, +20% in CASUAL
    private static final float FORGE_HEALTH_PER_LEVEL = 0.30f;
    private static final float CASUAL_HEALTH_PER_LEVEL = 0.15f;

    /**
     * Calculate the difficulty level for an entity in the given server level.
     * Rolls a new effective mode internally.
     *
     * @param entity the entity being evaluated
     * @param level  the server level
     * @return difficulty level (1-5)
     */
    public int calculateDifficultyLevel(LivingEntity entity, ServerLevel level) {
        return calculateDifficultyLevel(entity, level, null);
    }

    /**
     * Calculate the difficulty level for an entity in the given server level,
     * optionally reusing a pre-rolled effective mode.
     * <p>
     * Smooth difficulty interpolation:
     * - Base from world/dimension config (1-3)
     * - + heat modifier: each 10 chunk heat = +0.4 level
     * - + exp modifier: each 10 player exp = +0.3 level
     * - Final level = round(baseLevel + heatMod + expMod), clamped 1-5
     *
     * @param entity         the entity being evaluated
     * @param level          the server level
     * @param preRolledMode  a pre-rolled effective mode (from SpawnCheckResult),
     *                       or null to roll a new one
     * @return difficulty tier (1-5 for CASUAL, 1-10 for FORGE/MIXED)
     */
    public int calculateDifficultyLevel(LivingEntity entity, ServerLevel level, DifficultyMode preRolledMode) {
        DifficultyMode effectiveMode = preRolledMode != null ? preRolledMode : rollEffectiveMode(getActiveDifficultyMode(level));

        // Base level from dimension (1-3) scaled to 10-30
        int dimBase = getBaseLevelForDimension(level);
        
        // Chunk heat contribution: heat * 3 (0-300 at max heat 100)
        ChunkHeatManager heatManager = ChunkHeatManager.get(level);
        ChunkPos chunkPos = new ChunkPos(entity.blockPosition());
        float heat = heatManager.getHeat(level, chunkPos);
        // Heat contribution is now config-driven (default 3.0 reproduces the shipped curve).
        float heatWeight = EliteForgeConfig.SERVER.levelHeatWeight.get().floatValue();
        float heatLevel = heat * heatWeight;

        // Player experience contribution is also config-driven (default 2.0).
        float expWeight = EliteForgeConfig.SERVER.levelExperienceWeight.get().floatValue();
        float expLevel = 0;
        @javax.annotation.Nullable
        Player nearestPlayer = level.getNearestPlayer(entity, 64.0);
        if (nearestPlayer != null) {
            PlayerExperienceManager expManager = PlayerExperienceManager.get(level);
            float playerExp = expManager.getPlayerExperience(nearestPlayer.getUUID());
            expLevel = playerExp * expWeight;
        }

        // CASUAL mode: reduced heat effect
        if (effectiveMode == DifficultyMode.CASUAL) {
            heatLevel *= 0.3f;
        }

        // Dimension contribution is config-driven (default 10.0).
        float dimWeight = EliteForgeConfig.SERVER.levelDimensionWeight.get().floatValue();
        // Calculate raw level
        float rawLevel = (dimBase * dimWeight) + heatLevel + expLevel;

        // Add variance (config-driven, default ±10%)
        float variancePct = EliteForgeConfig.SERVER.levelVariancePercent.get().floatValue();
        float variance = rawLevel * variancePct;
        rawLevel += (java.util.concurrent.ThreadLocalRandom.current().nextFloat() - 0.5f) * 2 * variance;
        
        int finalLevel = Math.max(1, Math.round(rawLevel));
        
        // Get max level from config (default 1500 for FORGE, 150 for CASUAL)
        int maxLevel = EliteForgeConfig.COMMON.maxEliteLevel.get();
        if (effectiveMode == DifficultyMode.CASUAL) {
            maxLevel = Math.min(maxLevel, EliteForgeConfig.COMMON.maxCasualLevel.get());
        }
        finalLevel = Math.min(finalLevel, maxLevel);

        LOGGER.debug("Calculated difficulty level {} for {} (dim={}, heat={}, exp={}, mode={}, max={})",
                finalLevel, entity.getName().getString(), dimBase, heatLevel, expLevel, effectiveMode, maxLevel);

        return finalLevel;
    }

    /**
     * Convert an internal tier (1-10) to a display level (large number).
     * Formula: tier * 10 + heat * 2 + random(0-4)
     * This gives numbers like 12, 43, 76, 345 for visual impact.
     *
     * @param tier  the internal difficulty tier (1-10)
     * @param heat  the chunk heat value (0-100)
     * @return display level (large number for name plate)
     */
    // Level is already in 1-1500 range, no conversion needed
    public static int toDisplayLevel(int level, float heat) {
        return level;
    }

    /**
     * Get the maximum difficulty level for a given difficulty mode.
     *
     * @param mode the difficulty mode
     * @return the max level (FORGE: 5, CASUAL: 3, MIXED: 5)
     */
    private int getMaxLevelForMode(DifficultyMode mode) {
        return switch (mode) {
            case FORGE -> 5;
            case CASUAL -> 3;
            case MIXED -> 5;
        };
    }

    /**
     * Roll the effective difficulty mode. For MIXED, there's an 80% chance
     * of FORGE rules and 20% chance of CASUAL rules.
     *
     * <p>The inconsistency issue (where {@link #calculateDifficultyLevel} and
     * {@link #shouldSpawnAsElite} could roll different effective modes for the
     * same spawn event) has been resolved by introducing {@link SpawnCheckResult}
     * and the overloaded {@code calculateDifficultyLevel(entity, level, preRolledMode)}
     * method. Callers should use {@link #shouldSpawnAsElite} to obtain a
     * {@code SpawnCheckResult}, then pass its {@code effectiveMode} to
     * {@code calculateDifficultyLevel}.</p>
     *
     * @param mode the base difficulty mode
     * @return the effective difficulty mode for this spawn
     */
    private DifficultyMode rollEffectiveMode(DifficultyMode mode) {
        if (mode == DifficultyMode.MIXED) {
            return ThreadLocalRandom.current().nextFloat() < 0.8f ? DifficultyMode.FORGE : DifficultyMode.CASUAL;
        }
        return mode;
    }

    /**
     * Get the spawn chance for elites based on the server level and difficulty mode.
     *
     * @param level the server level
     * @param mode  the difficulty mode
     * @return spawn chance as a float (0.0 to 1.0)
     */
    public float getSpawnChance(ServerLevel level, DifficultyMode mode) {
        return (float) mode.getSpawnChance();
    }

    /**
     * Determine whether an entity should spawn as an elite, returning a
     * {@link SpawnCheckResult} that includes the rolled effective mode.
     * <p>
     * Checks:
     * - Config enable flag
     * - Entity blacklist
     * - Dimension blacklist
     * - Spawn chance
     * - Mode-specific constraints
     * <p>
     * CASUAL mode: only 1 elite within 48 blocks (vs 32 previously)
     * <p>
     * The returned effective mode should be passed to
     * {@link #calculateDifficultyLevel(LivingEntity, ServerLevel, DifficultyMode)}
     * to ensure the same mode is used for both the spawn decision and difficulty
     * calculation.
     *
     * @param entity the candidate entity
     * @param level  the server level
     * @return SpawnCheckResult containing the spawn decision and the effective mode
     */
    public SpawnCheckResult shouldSpawnAsElite(LivingEntity entity, ServerLevel level) {
        // Check config enable
        if (!EliteForgeConfig.COMMON.enableEliteMobs.get()) {
            return new SpawnCheckResult(false, getActiveDifficultyMode(level));
        }

        // Check entity blacklist
        @SuppressWarnings("unchecked") List<String> entityBlacklist = (List<String>) EliteForgeConfig.SERVER.entityBlacklist.get();
        String entityTypeId = net.minecraft.world.entity.EntityType.getKey(entity.getType()).toString();
        if (entityBlacklist.contains(entityTypeId)) {
            return new SpawnCheckResult(false, getActiveDifficultyMode(level));
        }

        // Check per-entity-type config override (entityOverrides list).
        if (com.eliteforge.config.EntityOverrideManager.isDisabled(entity.getType())) {
            return new SpawnCheckResult(false, getActiveDifficultyMode(level));
        }

        // ===== Entity Category Filtering =====
        // When hostileOnly is enabled (default), only hostile mobs can become elites.
        // This uses two checks for maximum mod compatibility:
        //   1. instanceof Monster — catches mobs that extend the Monster class
        //      (covers most modded hostile mobs that properly extend Monster)
        //   2. MobCategory check — catches mobs that are in MONSTER/WATER_CREATURE/
        //      AMBIENT categories even if they don't extend Monster
        if (EliteForgeConfig.SERVER.hostileOnly.get()) {
            boolean isHostile = isHostileEntity(entity);
            if (!isHostile) {
                return new SpawnCheckResult(false, getActiveDifficultyMode(level));
            }
        }

        // Check per-entity preset for disabled flag
        com.eliteforge.datapack.EntityPreset preset =
                com.eliteforge.datapack.EntityPresetLoader.getInstance().getPreset(entity.getType());
        if (preset != null && preset.getDisabled() != null && preset.getDisabled()) {
            return new SpawnCheckResult(false, getActiveDifficultyMode(level));
        }

        // ===== Third-Party Mod Compatibility =====
        // Skip entities already processed by other elite mob mods
        if (isAlreadyEliteFromOtherMod(entity)) {
            return new SpawnCheckResult(false, getActiveDifficultyMode(level));
        }

        // Check dimension blacklist
        @SuppressWarnings("unchecked") List<String> dimensionBlacklist = (List<String>) EliteForgeConfig.SERVER.dimensionBlacklist.get();
        String dimensionKey = level.dimension().location().toString();
        if (dimensionBlacklist.contains(dimensionKey)) {
            return new SpawnCheckResult(false, getActiveDifficultyMode(level));
        }

        // Don't convert players
        if (entity instanceof Player) {
            return new SpawnCheckResult(false, getActiveDifficultyMode(level));
        }

        // Check if already elite
        if (entity.getCapability(EliteCapability.CAPABILITY).map(EliteCapability::isElite).orElse(false)) {
            return new SpawnCheckResult(false, getActiveDifficultyMode(level));
        }

        // Roll effective mode ONCE and use it consistently throughout this method.
        // This ensures spawn chance and mode-specific constraints use the same mode.
        DifficultyMode mode = getActiveDifficultyMode(level);
        DifficultyMode effectiveMode = rollEffectiveMode(mode);

        // Check spawn chance using the effective mode
        float spawnChance = getSpawnChance(level, effectiveMode);

        if (level.random.nextFloat() > spawnChance) {
            return new SpawnCheckResult(false, effectiveMode);
        }

        // Mode-specific constraints (using the same effectiveMode as above)
        if (effectiveMode == DifficultyMode.CASUAL) {
            // CASUAL mode: only spawn if no other elite within 48 blocks
            List<LivingEntity> nearbyElites = level.getEntitiesOfClass(
                    LivingEntity.class,
                    entity.getBoundingBox().inflate(48.0),
                    e -> e.getCapability(EliteCapability.CAPABILITY).map(EliteCapability::isElite).orElse(false)
            );
            if (!nearbyElites.isEmpty()) {
                return new SpawnCheckResult(false, effectiveMode);
            }
        }

        return new SpawnCheckResult(true, effectiveMode);
    }

    /**
     * Check if an entity is considered "hostile" for elite spawn filtering purposes.
     * <p>
     * Uses a multi-layered check for maximum mod compatibility:
     * <ul>
     *   <li><b>Monster class check</b>: {@code entity instanceof Monster} — catches
     *       vanilla hostile mobs and modded mobs that properly extend Monster.
     *       This is the primary check and covers most cases.</li>
     *   <li><b>MobCategory check</b>: {@code entity.getType().getCategory()} —
     *       catches mobs in MONSTER, WATER_CREATURE (if includeWaterMobs), or
     *       AMBIENT (if includeAmbientMobs) categories. This handles modded mobs
     *       that might not extend Monster but are categorized as hostile.</li>
     * </ul>
     * <p>
     * <b>Configuration:</b>
     * <ul>
     *   <li>{@code hostileOnly=false}: always returns true (all LivingEntity can be elite)</li>
     *   <li>{@code hostileOnly=true}: requires one of the above checks to pass</li>
     *   <li>{@code includeWaterMobs=true}: WATER_CREATURE category counts as hostile</li>
     *   <li>{@code includeAmbientMobs=true}: AMBIENT category counts as hostile (Phantom)</li>
     * </ul>
     *
     * @param entity the entity to check
     * @return true if the entity is considered hostile (or if filtering is disabled)
     */
    private boolean isHostileEntity(LivingEntity entity) {
        // Primary check: Monster or Enemy class (covers vanilla + most modded hostile mobs)
        // Enemy interface is implemented by all hostile mobs including water mobs like Guardian
        if (entity instanceof net.minecraft.world.entity.monster.Monster) {
            return true;
        }
        if (entity instanceof net.minecraft.world.entity.monster.Enemy) {
            return true;
        }

        // Secondary check: MobCategory for mobs that don't implement Enemy
        // but are still categorized as hostile by Minecraft's spawning system
        net.minecraft.world.entity.MobCategory category = entity.getType().getCategory();
        if (category == net.minecraft.world.entity.MobCategory.MONSTER) {
            return true;
        }
        // Only include water mobs that are actually hostile (Enemy interface check above
        // catches Guardian/Drowned). Don't include WATER_CREATURE category blanket —
        // it includes passive squid and axolotl.
        // WATER_AMBIENT (tropical fish, pufferfish) are also passive, so excluded by default.
        if (EliteForgeConfig.SERVER.includeWaterMobs.get()) {
            // Only allow WATER_CREATURE if the entity is also an Enemy
            // (Guardian, Elder Guardian, Drowned are Monsters/Enemies)
            if (category == net.minecraft.world.entity.MobCategory.WATER_CREATURE
                    && entity instanceof net.minecraft.world.entity.monster.Enemy) {
                return true;
            }
        }
        if (EliteForgeConfig.SERVER.includeAmbientMobs.get()) {
            // Only allow AMBIENT if the entity is also an Enemy (Phantom)
            if (category == net.minecraft.world.entity.MobCategory.AMBIENT
                    && entity instanceof net.minecraft.world.entity.monster.Enemy) {
                return true;
            }
        }

        return false;
    }

    /**
     * Check if an entity has already been marked as elite by another mod
     * (L2Hostility, Champions, Infernal Mobs).
     * Uses NBT tag checks to detect other mods' elite markers.
     *
     * @param entity the entity to check
     * @return true if the entity is already elite from another mod
     */
    private boolean isAlreadyEliteFromOtherMod(LivingEntity entity) {
        net.minecraft.nbt.CompoundTag nbt = entity.getPersistentData();

        // L2Hostility: stores data under "l2hostility:..." NBT keys
        if (EliteForgeConfig.SERVER.compatL2Hostility.get()) {
            if (nbt.contains("l2hostility:difficulty") || nbt.contains("l2hostility:mob_difficulty")) {
                return true;
            }
        }

        // Champions: stores data under "champions:..." NBT keys
        if (EliteForgeConfig.SERVER.compatChampions.get()) {
            if (nbt.contains("champions:champion") || nbt.contains("champions:tier")) {
                return true;
            }
        }

        // Infernal Mobs: stores data under "infernal_mobs" or similar
        if (EliteForgeConfig.SERVER.compatInfernalMobs.get()) {
            if (nbt.contains("infernal_mobs") || nbt.contains("infernalmob")) {
                return true;
            }
        }

        // Check compatBlacklistedMods — skip entities from mods with their own elite systems
        @SuppressWarnings("unchecked")
        List<String> blacklistedMods = (List<String>) EliteForgeConfig.SERVER.compatBlacklistedMods.get();
        if (!blacklistedMods.isEmpty()) {
            String entityModId = net.minecraft.world.entity.EntityType.getKey(entity.getType()).getNamespace();
            if (blacklistedMods.contains(entityModId)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Apply elite modifiers to an entity based on its difficulty level and abilities.
     * <p>
     * Effects applied:
     * - Custom name with level indicator and quality tier coloring
     * - Health scaling: base * (1 + level * healthPerLevel) [30% FORGE, 20% CASUAL]
     * - Damage scaling: base * (1 + level * 0.15)
     * - Experience drop scaling: base * (1 + level * 0.5)
     * - Glow effect (color based on level)
     * - Ability application via Ability.onApply
     *
     * @param entity    the entity to modify
     * @param level     the difficulty level (1-5)
     * @param abilities the list of abilities with their levels
     */
    public void applyEliteModifiers(LivingEntity entity, int level, List<Pair<Ability, Integer>> abilities) {
        // Set custom name with level indicator
        setEliteCustomName(entity, level);

        // Get difficulty mode for health scaling
        DifficultyMode mode = entity.getCapability(EliteCapability.CAPABILITY)
                .map(cap -> cap.getEliteData().getSpawnMode())
                .orElse(DifficultyMode.FORGE);

        // Scale health: config-driven per-level bonus with progressive diminishing.
        // v0.6.0: low levels grow at the full rate, but high levels use a square-root
        // curve so a Lv.1500 elite isn't 450x base HP (which was unwinnable). The
        // formula: multiplier = 1 + sqrt(level/10) * healthPerLevel, so Lv.10 = +3%,
        // Lv.100 = +9.5%, Lv.500 = +21%, Lv.1500 = +36% (at default 0.03).
        float healthPerLevel = EliteForgeConfig.SERVER.eliteHealthPerLevel.get().floatValue();
        if (mode == DifficultyMode.CASUAL) healthPerLevel *= 0.5f;
        float healthMultiplier = 1.0f + (float)Math.sqrt(level / 10.0f) * healthPerLevel;

        // Apply per-entity health multiplier override from preset (datapack) if present
        com.eliteforge.datapack.EntityPreset preset =
                com.eliteforge.datapack.EntityPresetLoader.getInstance().getPreset(entity.getType());
        if (preset != null && preset.getHealthMultiplier() != null) {
            healthMultiplier *= preset.getHealthMultiplier();
        }
        // Apply per-entity config override (entityOverrides list) — takes precedence.
        com.eliteforge.config.EntityOverrideManager.Override cfgOverride =
                com.eliteforge.config.EntityOverrideManager.get(entity.getType());
        if (cfgOverride.healthMultiplier != null) {
            healthMultiplier *= cfgOverride.healthMultiplier;
        }

        float baseMaxHealth = entity.getMaxHealth();
        float newMaxHealth = baseMaxHealth * healthMultiplier;
        if (entity instanceof Mob mob) {
            // In 1.20.1, Mob.getAttribute(Attribute) returns AttributeInstance (not Optional),
            // so we use a null check instead of ifPresent(...).
            net.minecraft.world.entity.ai.attributes.AttributeInstance maxHealthAttr = mob.getAttribute(Attributes.MAX_HEALTH);
            if (maxHealthAttr != null) {
                maxHealthAttr.setBaseValue(newMaxHealth);
            }
            // Scale damage: same progressive square-root curve as health so high-level
            // elites hit hard but not one-shot-kill hard. Lv.1500 = +135% at default 0.05.
            float damagePerLevel = EliteForgeConfig.SERVER.eliteDamagePerLevel.get().floatValue();
            float damageMultiplier = 1.0f + (float)Math.sqrt(level / 10.0f) * damagePerLevel;
            // Apply per-entity damage multiplier override from preset (datapack) if present
            if (preset != null && preset.getDamageMultiplier() != null) {
                damageMultiplier *= preset.getDamageMultiplier();
            }
            // Apply per-entity config override (entityOverrides list).
            if (cfgOverride.damageMultiplier != null) {
                damageMultiplier *= cfgOverride.damageMultiplier;
            }
            net.minecraft.world.entity.ai.attributes.AttributeInstance attackDamageAttr = mob.getAttribute(Attributes.ATTACK_DAMAGE);
            if (attackDamageAttr != null) {
                attackDamageAttr.setBaseValue(attackDamageAttr.getBaseValue() * damageMultiplier);
            }
        }
        entity.setHealth(newMaxHealth);

        // Apply glow effect with color based on level
        // Removed GLOWING effect per user feedback — all elites glowed, which was undesirable

        // Apply each ability
        for (Pair<Ability, Integer> abilityPair : abilities) {
            Ability ability = abilityPair.getFirst();
            int abilityLevel = abilityPair.getSecond();
            try {
                ability.onApply(entity, abilityLevel);
            } catch (Exception e) {
                LOGGER.error("Error applying ability {} to entity {}: {}",
                        ability.getIdString(), entity.getName().getString(), e.getMessage());
            }
        }

        LOGGER.debug("Applied elite modifiers to {} (level={}, health={}, mode={})",
                entity.getName().getString(), level, newMaxHealth, mode);
    }

    /**
     * Set a custom name for an elite entity with level indicator and quality coloring.
     *
     * @param entity the elite entity
     * @param level  the difficulty level
     */
    private void setEliteCustomName(LivingEntity entity, int level) {
        if (entity.hasCustomName()) {
            return; // Don't override existing custom names
        }

        // Get quality tier from capability
        net.minecraft.world.entity.LivingEntity capEntity = entity;
        java.util.Optional<EliteCapability> capOpt = entity.getCapability(EliteCapability.CAPABILITY).resolve();
        if (capOpt.isEmpty()) return;
        EliteCapability cap = capOpt.get();
        EliteData data = cap.getEliteData();
        QualityTier tier = data.getQualityTier();
        ChatFormatting tierColor = tier.getChatColor();

        String baseName = entity.getName().getString();

        // Name format: "Zombie Lv.1234" — name in tier color, level bold
        MutableComponent customName = Component.literal("")
                .append(Component.literal(baseName).withStyle(tierColor))
                .append(Component.literal(" Lv." + level).withStyle(ChatFormatting.GRAY, ChatFormatting.BOLD));

        // Creator-tier gets extra emphasis
        if (data.isCreatorEntity()) {
            customName = customName.withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD);
        }

        entity.setCustomName(customName);
        entity.setCustomNameVisible(true);

        // Store in capability (use final copy for lambda)
        final MutableComponent finalCustomName = customName;
        entity.getCapability(EliteCapability.CAPABILITY).ifPresent(capStore -> {
            capStore.getEliteData().setCustomName(finalCustomName);
        });
    }

    /**
     * Get the base difficulty level for a dimension.
     *
     * @param level the server level
     * @return base level (1-3)
     */
    private int getBaseLevelForDimension(ServerLevel level) {
        String dimensionKey = level.dimension().location().toString();

        // Nether is harder
        if (dimensionKey.contains("nether") || dimensionKey.contains("the_nether")) {
            return 2;
        }
        // End is hardest
        if (dimensionKey.contains("end") || dimensionKey.contains("the_end")) {
            return 3;
        }
        // Overworld default
        return 1;
    }

    /**
     * Get the active difficulty mode from config.
     *
     * @param level the server level
     * @return the active DifficultyMode
     */
    public DifficultyMode getActiveDifficultyMode(ServerLevel level) {
        return EliteForgeConfig.COMMON.difficultyMode.get();
    }

    /**
     * Get the glow color for a given difficulty level.
     *
     * @param level the difficulty level (1-5)
     * @return the RGB glow color
     */
    public static int getGlowColor(int level) {
        int colorIdx = Math.max(0, Math.min((int)(level / 300.0), GLOW_COLORS.length - 1));
        return GLOW_COLORS[colorIdx];
    }

    /**
     * Apply difficulty fatigue when a player dies to an elite.
     * Reduces chunk heat by 5 near the death location.
     *
     * @param level      the server level
     * @param deathPos   the position where the player died
     */
    public void applyDifficultyFatigue(ServerLevel level, BlockPos deathPos) {
        ChunkHeatManager heatManager = ChunkHeatManager.get(level);
        ChunkPos chunkPos = new ChunkPos(deathPos);
        heatManager.reduceHeat(level, chunkPos, 5.0f);
        LOGGER.debug("Applied difficulty fatigue: reduced heat near {}", deathPos);
    }
}
