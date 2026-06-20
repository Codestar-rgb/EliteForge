package com.eliteforge.blockentity;

import com.eliteforge.ability.Ability;
import com.eliteforge.ability.AbilityRegistry;
import com.eliteforge.block.EliteSpawnerBlock;
import com.eliteforge.capability.EliteCapability;
import com.eliteforge.capability.EliteCapabilitySync;
import com.eliteforge.capability.EliteData;
import com.eliteforge.config.DifficultyMode;
import com.eliteforge.difficulty.DifficultyManager;
import com.eliteforge.quality.QualityTier;
import com.eliteforge.spawn.AbilityGenerator;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * EliteSpawnerBlockEntity - Block entity for the Elite Spawner.
 * Manages spawning elite mobs with configurable difficulty, mode, and interval.
 * Emits redstone pulse when spawning.
 * Stores configuration: difficulty level (1-5), spawn mode, spawn interval, entity type.
 */
public class EliteSpawnerBlockEntity extends BlockEntity {

    private int difficulty = 1; // 1-5
    private String spawnMode = "FORGE"; // FORGE or CASUAL
    private int spawnInterval = 600; // ticks (30 seconds default)
    private String entityType = "minecraft:zombie";
    private int spawnTimer = 0;
    private boolean isSpawning = false;

    public EliteSpawnerBlockEntity(BlockPos pos, BlockState state) {
        // Delegate to the typed constructor so getType() returns the right value
        // (super(null, pos, state) would leave the BE's `type` field null).
        this(com.eliteforge.init.ModBlockEntities.ELITE_SPAWNER_BE.get(), pos, state);
    }

    public EliteSpawnerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /**
     * Main tick logic for the elite spawner.
     * Counts down to spawn and then creates an elite mob.
     */
    public void tick() {
        if (level == null || level.isClientSide) return;

        spawnTimer++;

        if (spawnTimer >= spawnInterval) {
            spawnTimer = 0;
            attemptSpawn();
        }
    }

    /**
     * Attempt to spawn an elite mob at the spawner location.
     */
    private void attemptSpawn() {
        if (!(level instanceof ServerLevel serverLevel)) return;

        // Resolve entity type
        net.minecraft.resources.ResourceLocation entityLoc =
            new net.minecraft.resources.ResourceLocation(entityType);
        EntityType<?> type = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.get(entityLoc);

        if (type == null || !(type.create(level) instanceof Mob)) {
            // Invalid entity type, default to zombie
            type = EntityType.ZOMBIE;
        }

        // Create the entity
        Mob entity = null;
        try {
            entity = (Mob) type.create(level);
        } catch (Exception e) {
            return;
        }

        if (entity == null) return;

        // Position the entity near the spawner
        double x = worldPosition.getX() + 0.5 + (level.random.nextDouble() - 0.5) * 2.0;
        double y = worldPosition.getY() + 1.0;
        double z = worldPosition.getZ() + 0.5 + (level.random.nextDouble() - 0.5) * 2.0;

        entity.moveTo(x, y, z, level.random.nextFloat() * 360.0f, 0.0f);

        // Apply elite properties
        applyEliteProperties(entity, difficulty, spawnMode);

        // Spawn the entity
        if (entity.checkSpawnRules(level, MobSpawnType.SPAWNER)) {
            level.addFreshEntity(entity);

            // Visual effects
            isSpawning = true;
            setSpawningState(true);

            // Emit redstone pulse
            level.blockUpdated(worldPosition, level.getBlockState(worldPosition).getBlock());

            // Spawn particles
            serverLevel.sendParticles(
                ParticleTypes.FLAME,
                x, y + 0.5, z,
                20, 0.5, 0.5, 0.5, 0.1
            );
            serverLevel.sendParticles(
                ParticleTypes.SOUL_FIRE_FLAME,
                x, y + 0.5, z,
                10, 0.3, 0.3, 0.3, 0.05
            );

            // Play spawn sound
            level.playSound(null, worldPosition,
                SoundEvents.EVOKER_PREPARE_SUMMON, SoundSource.BLOCKS, 0.8f, 1.0f);

            // Reset spawning state after a brief pulse
            if (level.getServer() == null) return;
            level.getServer().execute(() -> {
                isSpawning = false;
                setSpawningState(false);
            });
        }
    }

    /**
     * Apply elite properties to a mob using the capability-based system.
     * Uses AbilityGenerator for proper ability generation and DifficultyManager for stat scaling.
     */
    private void applyEliteProperties(Mob mob, int difficulty, String mode) {
        EliteCapability cap = mob.getCapability(EliteCapability.CAPABILITY).orElse(null);
        if (cap == null) return;

        cap.setElite(true);
        EliteData data = cap.getEliteData();
        data.setLevel(difficulty);

        // Parse difficulty mode
        DifficultyMode difficultyMode;
        try {
            difficultyMode = DifficultyMode.valueOf(mode.toUpperCase());
        } catch (IllegalArgumentException e) {
            difficultyMode = DifficultyMode.FORGE;
        }
        data.setSpawnMode(difficultyMode);

        // Roll quality tier based on difficulty
        QualityTier tier = QualityTier.weightedRandomWithBonus(ThreadLocalRandom.current(), difficulty, 0.0f, 0.0f);
        data.setQualityTier(tier);

        // Generate abilities using the real AbilityGenerator
        List<Pair<Ability, Integer>> generatedAbilities = AbilityGenerator.generateAbilities(
                difficulty, difficultyMode, mob.getType());
        for (Pair<Ability, Integer> pair : generatedAbilities) {
            data.addAbility(pair.getFirst().getId().toString(), pair.getSecond());
            try {
                pair.getFirst().onApply(mob, pair.getSecond());
            } catch (Exception e) {
                // ignore ability application errors
            }
        }

        cap.setEliteData(data);

        // Apply stat scaling (health/damage/speed)
        DifficultyManager.INSTANCE.applyEliteModifiers(mob, difficulty, generatedAbilities);

        // Set custom name — use the localized "elite_prefix" pattern (matches BestowalSigil)
        // instead of the raw enum display name + hardcoded English " Elite ".
        mob.setCustomName(Component.translatable("name.eliteforge.elite_prefix")
                .append(" ")
                .append(mob.getName())
                .withStyle(tier.getChatColor()));
        mob.setCustomNameVisible(true);

        // Register with the elite tracker so the spawned elite's abilities tick and it
        // participates in dynamic strengthening / awakening / revenge.
        com.eliteforge.spawn.EliteEventHandler.trackElite(mob);

        // Sync to clients
        EliteCapabilitySync.broadcastEliteDataUpdate(mob, data);
    }

    /**
     * Generate abilities for an elite based on difficulty and mode.
     * @deprecated Use {@link AbilityGenerator#generateAbilities} instead. Retained for NBT backward-compat.
     */
    @Deprecated
    private ListTag generateAbilities(int difficulty, String mode) {
        // Legacy NBT-based generation — no longer used by applyEliteProperties.
        // Kept for backward compatibility with old saved block entity NBT data.
        ListTag abilities = new ListTag();
        return abilities;
    }

    @Deprecated
    private String rollAbilityRarity(int difficulty, Random random, String mode) {
        // Legacy — no longer used. Retained for backward compat.
        return "COMMON";
    }

    private void setSpawningState(boolean spawning) {
        if (level == null) return;
        BlockState state = level.getBlockState(worldPosition);
        if (state.hasProperty(EliteSpawnerBlock.ACTIVE)) {
            level.setBlock(worldPosition, state.setValue(EliteSpawnerBlock.ACTIVE, spawning), 3);
        }
    }

    // Configuration getters and setters

    public int getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(int difficulty) {
        // v0.2.0: difficulty is the elite level (1-maxEliteLevel, default 1-1500).
        // Was hardcoded 1-5, which made the spawner block unable to produce high-level elites.
        int maxLvl = com.eliteforge.config.EliteForgeConfig.COMMON.maxEliteLevel.get();
        this.difficulty = Math.max(1, Math.min(maxLvl, difficulty));
        setChanged();

        // Update block state
        if (level != null) {
            BlockState state = level.getBlockState(worldPosition);
            if (state.hasProperty(EliteSpawnerBlock.DIFFICULTY)) {
                level.setBlock(worldPosition, state.setValue(EliteSpawnerBlock.DIFFICULTY, this.difficulty), 3);
            }
        }
    }

    public String getSpawnMode() {
        return spawnMode;
    }

    public void setSpawnMode(String mode) {
        if ("FORGE".equals(mode) || "CASUAL".equals(mode)) {
            this.spawnMode = mode;
            setChanged();
        }
    }

    public int getSpawnInterval() {
        return spawnInterval / 20; // Convert ticks to seconds
    }

    public void setSpawnInterval(int seconds) {
        this.spawnInterval = Math.max(5, Math.min(600, seconds)) * 20; // 5s to 5min, convert to ticks
        setChanged();
    }

    public String getEntityType() {
        return entityType;
    }

    public void setEntityType(String entityType) {
        this.entityType = entityType;
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("Difficulty", difficulty);
        tag.putString("SpawnMode", spawnMode);
        tag.putInt("SpawnInterval", spawnInterval);
        tag.putString("EntityType", entityType);
        tag.putInt("SpawnTimer", spawnTimer);
        tag.putBoolean("IsSpawning", isSpawning);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        difficulty = tag.getInt("Difficulty");
        spawnMode = tag.getString("SpawnMode");
        spawnInterval = tag.getInt("SpawnInterval");
        entityType = tag.getString("EntityType");
        spawnTimer = tag.getInt("SpawnTimer");
        isSpawning = tag.getBoolean("IsSpawning");

        // Validate loaded data. Difficulty is 1-maxEliteLevel (was hardcoded 1-5).
        int maxLvl = com.eliteforge.config.EliteForgeConfig.COMMON.maxEliteLevel.get();
        difficulty = Math.max(1, Math.min(maxLvl, difficulty));
        if (!"FORGE".equals(spawnMode) && !"CASUAL".equals(spawnMode)) {
            spawnMode = "FORGE";
        }
        spawnInterval = Math.max(100, spawnInterval);
        if (entityType == null || entityType.isEmpty()) {
            entityType = "minecraft:zombie";
        }
    }
}
