package com.eliteforge.spawn;

import com.eliteforge.ability.Ability;
import com.eliteforge.ability.AbilityCategory;
import com.eliteforge.ability.AbilityRegistry;
import com.eliteforge.ability.MutualExclusion;
import com.eliteforge.capability.EliteCapability;
import com.eliteforge.capability.EliteData;
import com.eliteforge.capability.EliteCapabilitySync;
import com.eliteforge.config.DifficultyMode;
import com.eliteforge.config.EliteForgeConfig;
import com.eliteforge.difficulty.ChunkHeatManager;
import com.eliteforge.quality.QualityTier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * System that triggers when players farm elites too aggressively in the same chunk.
 * <p>
 * Tracks kills per chunk per time window and triggers revenge effects when
 * thresholds are exceeded:
 * <ul>
 *   <li>5+ kills in same chunk within 60 seconds, OR</li>
 *   <li>15+ total kills in same chunk</li>
 * </ul>
 * <p>
 * Revenge effects:
 * <ol>
 *   <li>Chunk heat surges to 90+</li>
 *   <li>Next elite spawn in this chunk is forced to LEGENDARY quality</li>
 *   <li>30% chance to trigger "elite squad" (3-5 simultaneous elite spawns)</li>
 *   <li>5% chance to directly spawn a creator-tier elite</li>
 *   <li>Server announcement: "☠ 该区域的精英们已被激怒！"</li>
 * </ol>
 */
public class EliteRevenge {

    private static final Logger LOGGER = LogManager.getLogger();

    // ==================== Kill Tracking ====================

    /** Thread-safe map of kill records keyed by chunk long position */
    private static final ConcurrentHashMap<Long, KillRecord> KILL_RECORDS = new ConcurrentHashMap<>();

    /** Chunks flagged for forced quality on next spawn */
    private static final ConcurrentHashMap<Long, QualityTier> FORCED_QUALITY = new ConcurrentHashMap<>();

    /** Chunks currently in revenge state */
    private static final Set<Long> REVENGE_CHUNKS = Collections.newSetFromMap(new ConcurrentHashMap<>());

    // ==================== Config Constants (fallback defaults) ====================
    /** Recent kills threshold for revenge (5 kills) — overridden by config */
    private static final int RECENT_KILL_THRESHOLD = 5;
    /** Total kills threshold for revenge (15 kills) */
    private static final int TOTAL_KILL_THRESHOLD = 15;
    /** Time window for recent kills (60 seconds in ticks) — overridden by config */
    private static final long RECENT_KILL_WINDOW_TICKS = 1200;
    /** Heat surge target on revenge */
    private static final float REVENGE_HEAT_SURGE = 90.0f;
    /** Chance to spawn elite squad on revenge (30%) */
    private static final float SQUAD_SPAWN_CHANCE = 0.30f;
    /** Chance to directly spawn creator on revenge (5%) */
    private static final float CREATOR_SPAWN_CHANCE = 0.05f;
    /** Minimum squad size */
    private static final int MIN_SQUAD_SIZE = 3;
    /** Maximum squad size */
    private static final int MAX_SQUAD_SIZE = 5;
    /** Revenge state duration in ticks (5 minutes) */
    private static final long REVENGE_DURATION_TICKS = 6000;
    /** Decay interval for tickRevenge (every 20 ticks) */
    private static final int DECAY_INTERVAL = 20;

    // ==================== Kill Record ====================

    /**
     * Tracks kill statistics per chunk for revenge calculation.
     */
    public static class KillRecord {
        /** Number of kills within the recent time window */
        public int recentKills;
        /** Game time when the recent kill window started */
        public long windowStartTick;
        /** Total kills in this chunk (all time, decays slowly) */
        public int totalKills;
        /** Game time when revenge state was triggered (0 = not in revenge) */
        public long revengeStartTime;

        public KillRecord() {
            this.recentKills = 0;
            this.windowStartTick = 0;
            this.totalKills = 0;
            this.revengeStartTime = 0;
        }

        public KillRecord(int recentKills, long windowStartTick, int totalKills) {
            this.recentKills = recentKills;
            this.windowStartTick = windowStartTick;
            this.totalKills = totalKills;
            this.revengeStartTime = 0;
        }
    }

    private EliteRevenge() {
        // Utility class - no instantiation
    }

    // ==================== Kill Event Handler ====================

    /**
     * Called when an elite is killed. Tracks the kill and checks for revenge triggers.
     *
     * @param pos   the block position where the elite was killed
     * @param level the server level
     */
    public static void onEliteKill(BlockPos pos, ServerLevel level) {
        long chunkKey = ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4);
        long currentTick = level.getGameTime();

        KILL_RECORDS.compute(chunkKey, (key, existing) -> {
            if (existing == null) {
                // First kill in this chunk
                KillRecord record = new KillRecord(1, currentTick, 1);
                return record;
            }

            // Check if the recent kill window has expired (config window in seconds, convert to ticks)
            long windowTicks = (long)(EliteForgeConfig.SERVER.revengeTimeWindow.get() * 20);
            if (currentTick - existing.windowStartTick > windowTicks) {
                // Reset the window
                existing.recentKills = 1;
                existing.windowStartTick = currentTick;
            } else {
                existing.recentKills++;
            }

            existing.totalKills++;
            return existing;
        });

        KillRecord record = KILL_RECORDS.get(chunkKey);

        // Check revenge triggers
        int killThreshold = EliteForgeConfig.SERVER.revengeKillThreshold.get();
        boolean shouldRevenge = false;
        if (record.recentKills >= killThreshold) {
            LOGGER.debug("Revenge triggered: {} recent kills in chunk {}",
                    record.recentKills, new ChunkPos(chunkKey));
            shouldRevenge = true;
        }
        if (record.totalKills >= TOTAL_KILL_THRESHOLD) {
            LOGGER.debug("Revenge triggered: {} total kills in chunk {}",
                    record.totalKills, new ChunkPos(chunkKey));
            shouldRevenge = true;
        }

        if (shouldRevenge) {
            triggerRevenge(chunkKey, pos, level);
        }
    }

    // ==================== Revenge Trigger ====================

    /**
     * Trigger revenge effects for a chunk.
     *
     * @param chunkKey the chunk long key
     * @param pos      the block position that triggered revenge
     * @param level    the server level
     */
    private static void triggerRevenge(long chunkKey, BlockPos pos, ServerLevel level) {
        ChunkPos chunkPos = new ChunkPos(chunkKey);

        // Mark chunk as in revenge state
        REVENGE_CHUNKS.add(chunkKey);

        // Set revenge start time in kill record
        KILL_RECORDS.computeIfPresent(chunkKey, (key, record) -> {
            record.revengeStartTime = level.getGameTime();
            return record;
        });

        // 1. Chunk heat surges to 90+
        ChunkHeatManager heatManager = ChunkHeatManager.get(level);
        float currentHeat = heatManager.getHeat(level, chunkPos);
        if (currentHeat < REVENGE_HEAT_SURGE) {
            float heatToAdd = REVENGE_HEAT_SURGE - currentHeat;
            heatManager.addHeat(level, chunkPos, heatToAdd);
        }

        // 2. Next elite spawn in this chunk is forced to LEGENDARY quality
        forceNextEliteQuality(chunkPos, QualityTier.LEGENDARY);

        // 3. 30% chance to trigger "elite squad" (3-5 simultaneous elite spawns)
        if (ThreadLocalRandom.current().nextFloat() < SQUAD_SPAWN_CHANCE) {
            spawnEliteSquad(pos, level);
        }

        // 4. 5% chance to directly spawn a creator-tier elite
        if (ThreadLocalRandom.current().nextFloat() < CREATOR_SPAWN_CHANCE) {
            spawnCreatorElite(pos, level);
        }

        // 5. Server announcement
        Component announcement = Component.literal("")
                .append(Component.literal("☠ ").withStyle(net.minecraft.ChatFormatting.RED,
                        net.minecraft.ChatFormatting.BOLD))
                .append(Component.translatable("message.eliteforge.revenge.activated").withStyle(net.minecraft.ChatFormatting.DARK_RED,
                        net.minecraft.ChatFormatting.BOLD));

        // Send to nearby players (within 128 blocks)
        for (ServerPlayer player : level.getPlayers(p -> p.distanceToSqr(pos.getX(), pos.getY(), pos.getZ()) < 128.0 * 128.0)) {
            if (com.eliteforge.config.EliteForgeConfig.SERVER.enableKillMessage.get()) player.sendSystemMessage(announcement);
            // Warning sound
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 1.0f, 1.2f);
        }

        // Revenge particles
        level.sendParticles(ParticleTypes.DRAGON_BREATH,
                pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5,
                40, 3.0, 2.0, 3.0, 0.05);

        LOGGER.info("Revenge triggered in chunk {} at {}", chunkPos, pos);
    }

    // ==================== Elite Squad Spawning ====================

    /**
     * Spawn an elite squad (3-5 mobs) near the given position.
     *
     * @param pos   the center position
     * @param level the server level
     */
    private static void spawnEliteSquad(BlockPos pos, ServerLevel level) {
        int squadSize = MIN_SQUAD_SIZE + ThreadLocalRandom.current().nextInt(MAX_SQUAD_SIZE - MIN_SQUAD_SIZE + 1);

        // Pick random mob types for the squad
        List<EntityType<? extends Mob>> mobTypes = List.of(
                EntityType.ZOMBIE, EntityType.SKELETON, EntityType.CREEPER, EntityType.SPIDER
        );

        for (int i = 0; i < squadSize; i++) {
            // Pick a random mob type
            EntityType<? extends Mob> mobType = mobTypes.get(ThreadLocalRandom.current().nextInt(mobTypes.size()));

            // Find a valid spawn position near the trigger point
            BlockPos spawnPos = findValidSpawnPos(pos, level, 8);
            if (spawnPos == null) continue;

            // Spawn the mob
            Mob mob = mobType.create(level);
            if (mob == null) continue;

            mob.moveTo(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5,
                    ThreadLocalRandom.current().nextFloat() * 360.0f, 0.0f);

            // Finalize spawn
            level.addFreshEntityWithPassengers(mob);
                if (true) {

                // Convert to elite via capability
                mob.getCapability(EliteCapability.CAPABILITY).ifPresent(cap -> {
                    EliteData data = new EliteData();
                    data.setElite(true);
                    data.setLevel(2 + ThreadLocalRandom.current().nextInt(3)); // Level 2-4
                    data.setQualityTier(QualityTier.EPIC); // EPIC quality squad members

                    // Generate some abilities with mutual exclusion checking
                    var availableAbilities = AbilityRegistry.getAllAbilities().stream()
                            .filter(a -> a.getCategory() != AbilityCategory.CREATOR)
                            .filter(Ability::isEnabled)
                            .toList();

                    List<String> selectedAbilityIds = new ArrayList<>();
                    int numAbilities = 1 + ThreadLocalRandom.current().nextInt(3); // 1-3 abilities
                    for (int j = 0; j < numAbilities && !availableAbilities.isEmpty(); j++) {
                        // Try to find an ability that isn't mutually exclusive with
                        // any already-selected ability. Limit attempts to avoid infinite loops.
                        Ability chosen = null;
                        for (int attempt = 0; attempt < 10; attempt++) {
                            Ability candidate = availableAbilities.get(ThreadLocalRandom.current().nextInt(availableAbilities.size()));
                            boolean isExclusive = false;
                            for (String selectedId : selectedAbilityIds) {
                                if (MutualExclusion.isMutuallyExclusive(candidate.getIdString(), selectedId)) {
                                    isExclusive = true;
                                    break;
                                }
                            }
                            if (!isExclusive) {
                                chosen = candidate;
                                break;
                            }
                        }
                        if (chosen == null) continue; // Skip if no compatible ability found

                        int abilityLevel = 1 + ThreadLocalRandom.current().nextInt(Math.min(3, chosen.getMaxLevel()));
                        data.addAbility(chosen.getIdString(), abilityLevel);
                        selectedAbilityIds.add(chosen.getIdString());
                    }

                    data.setHasBeenEngaged(true); // Prevent despawn
                    cap.setEliteData(data);
                    EliteCapabilitySync.broadcastEliteDataUpdate(mob, data);

                    // Immediately track the squad member so its abilities
                    // start ticking on the next tick without waiting for
                    // the periodic full scan.
                    EliteEventHandler.trackElite(mob);
                });

                LOGGER.debug("Spawned revenge squad member {} at {}", mob.getName().getString(), spawnPos);
            }
        }

        LOGGER.info("Spawned revenge elite squad of {} members near {}", squadSize, pos);
    }

    /**
     * Spawn a creator-tier elite at the given position.
     *
     * @param pos   the spawn position
     * @param level the server level
     */
    private static void spawnCreatorElite(BlockPos pos, ServerLevel level) {
        // CASUAL mode: creators should never spawn
        if (EliteForgeConfig.COMMON.difficultyMode.get() == DifficultyMode.CASUAL) return;

        // Check creator count limit (use config value instead of hardcoded 2)
        if (EliteEcosystem.getActiveCreatorCount(level) >= EliteForgeConfig.SERVER.maxCreatorEntities.get()) {
            LOGGER.debug("Cannot spawn revenge creator: max creator count reached");
            return;
        }

        // Pick a random mob type for the creator
        EntityType<? extends Mob> mobType = EntityType.ZOMBIE; // Default to zombie for creator
        BlockPos spawnPos = findValidSpawnPos(pos, level, 6);
        if (spawnPos == null) return;

        Mob mob = mobType.create(level);
        if (mob == null) return;

        mob.moveTo(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5,
                ThreadLocalRandom.current().nextFloat() * 360.0f, 0.0f);

        level.addFreshEntityWithPassengers(mob);
        if (true) {

            // Pick a random creator ability
            var creatorAbilities = AbilityRegistry.getAbilitiesByCategory(AbilityCategory.CREATOR)
                    .stream()
                    .filter(Ability::isEnabled)
                    .toList();

            if (creatorAbilities.isEmpty()) {
                LOGGER.warn("No creator abilities available for revenge creator spawn");
                return;
            }

            Ability chosenAbility = creatorAbilities.get(ThreadLocalRandom.current().nextInt(creatorAbilities.size()));

            mob.getCapability(EliteCapability.CAPABILITY).ifPresent(cap -> {
                EliteData data = new EliteData();
                data.setElite(true);
                data.setLevel(5); // Max level
                data.setQualityTier(QualityTier.MYTHIC);
                data.setCreatorEntity(true);
                data.setCreatorAbilityId(chosenAbility.getIdString());
                data.setCreatorAbilityLevel(1);
                data.addAbility(chosenAbility.getIdString(), 1);
                data.setHasBeenEngaged(true);

                // Apply mythic health boost using AttributeModifier (consistent with applyMythicModifiers)
                try {
                    var healthAttr = mob.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
                    if (healthAttr != null) {
                        healthAttr.addPermanentModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                                UUID.fromString("f6a7b8c9-d0e1-2345-6789-abcdef012345"),
                                "EliteForge Revenge Creator Health",
                                1.0, // MULTIPLY_BASE: +100% = 2x health (consistent with applyMythicModifiers)
                                net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.MULTIPLY_BASE
                        ));
                        mob.setHealth((float) healthAttr.getValue());
                    }
                } catch (Exception e) {
                    // Attribute may not be available
                }

                cap.setEliteData(data);
                EliteCapabilitySync.broadcastEliteDataUpdate(mob, data);

                // Apply the creator ability
                try {
                    chosenAbility.onApply(mob, 1);
                } catch (Exception e) {
                    LOGGER.error("Error applying revenge creator ability {}: {}",
                            chosenAbility.getIdString(), e.getMessage());
                }

                // Register in ecosystem
                EliteEcosystem.registerCreator(mob, chosenAbility.getIdString(), 1);

                // Immediately track the creator elite so its abilities
                // start ticking on the next tick without waiting for
                // the periodic full scan.
                EliteEventHandler.trackElite(mob);
            });

            // Dramatic spawn effects
            level.sendParticles(ParticleTypes.DRAGON_BREATH,
                    spawnPos.getX() + 0.5, spawnPos.getY() + 1.0, spawnPos.getZ() + 0.5,
                    60, 3.0, 2.0, 3.0, 0.05);
            level.playSound(null, spawnPos, SoundEvents.WARDEN_ANGRY, SoundSource.HOSTILE, 2.0f, 0.8f);

            LOGGER.info("Spawned revenge creator elite at {}", spawnPos);
        }
    }

    // ==================== Spawn Position Helper ====================

    /**
     * Find a valid spawn position near the given center position.
     *
     * @param center  the center position to search around
     * @param level   the server level
     * @param range   the search range
     * @return a valid spawn position, or null if none found
     */
    private static BlockPos findValidSpawnPos(BlockPos center, ServerLevel level, int range) {
        for (int attempts = 0; attempts < 10; attempts++) {
            int offsetX = ThreadLocalRandom.current().nextInt(range * 2 + 1) - range;
            int offsetZ = ThreadLocalRandom.current().nextInt(range * 2 + 1) - range;
            BlockPos candidate = center.offset(offsetX, 0, offsetZ);

            // Find a valid Y position
            BlockPos validPos = level.getHeightmapPos(
                    net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,
                    candidate);

            // Check if the position is safe for spawning
            BlockState below = level.getBlockState(validPos.below());
            if (!below.isAir() && below.isSolidRender(level, validPos.below())) {
                return validPos;
            }
        }
        // Fallback: use the center position
        return level.getHeightmapPos(
                net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,
                center);
    }

    // ==================== Tick / Decay ====================

    /**
     * Called every 20 ticks to decay kill records and manage revenge state.
     *
     * @param level the server level
     */
    public static void tickRevenge(ServerLevel level) {
        long currentTick = level.getGameTime();

        // Use compute() on each entry to ensure atomicity of field modifications.
        // Previously, direct field writes on KillRecord during iteration were not
        // atomic with respect to other threads accessing KILL_RECORDS, which could
        // lead to inconsistent reads of recentKills, totalKills, or revengeStartTime.
        Set<Long> keysToRemove = new HashSet<>();

        for (Long chunkKey : KILL_RECORDS.keySet()) {
            KILL_RECORDS.compute(chunkKey, (key, record) -> {
                if (record == null) return null;

                // Check if the recent kill window has expired - reset recent kills (config window in seconds, convert to ticks)
                long windowTicks = (long)(EliteForgeConfig.SERVER.revengeTimeWindow.get() * 20);
                if (currentTick - record.windowStartTick > windowTicks) {
                    record.recentKills = 0;
                    record.windowStartTick = currentTick;
                }

                // Slowly decay total kills (reduce by 1 every 20 ticks when total > 0)
                if (record.totalKills > 0) {
                    record.totalKills = Math.max(0, record.totalKills - 1);
                }

                // Check if revenge state has expired
                if (record.revengeStartTime > 0 && currentTick - record.revengeStartTime > REVENGE_DURATION_TICKS) {
                    record.revengeStartTime = 0;
                    REVENGE_CHUNKS.remove(key);
                    LOGGER.debug("Revenge state expired for chunk {}", new ChunkPos(key));
                }

                // Mark empty records for removal
                if (record.recentKills <= 0 && record.totalKills <= 0 && record.revengeStartTime <= 0) {
                    keysToRemove.add(key);
                    return null; // Remove from map
                }

                return record;
            });
        }

        // Clean up REVENGE_CHUNKS for removed records
        for (Long chunkKey : keysToRemove) {
            REVENGE_CHUNKS.remove(chunkKey);
        }

        // Clean up forced quality entries that are no longer needed
        FORCED_QUALITY.entrySet().removeIf(entry -> {
            // Remove forced quality if chunk is no longer in revenge or after some time
            return !REVENGE_CHUNKS.contains(entry.getKey());
        });
    }

    // ==================== Query Methods ====================

    /**
     * Check if a chunk is currently in revenge state.
     *
     * @param pos the chunk position
     * @return true if the chunk is in revenge state
     */
    public static boolean isChunkUnderRevenge(ChunkPos pos) {
        return REVENGE_CHUNKS.contains(pos.toLong());
    }

    /**
     * Flag the next elite spawn in a chunk to be a specific quality tier.
     * Called by revenge system to force LEGENDARY spawns.
     *
     * @param pos   the chunk position
     * @param tier  the quality tier to force
     */
    public static void forceNextEliteQuality(ChunkPos pos, QualityTier tier) {
        FORCED_QUALITY.put(pos.toLong(), tier);
        LOGGER.debug("Forced next elite quality to {} in chunk {}", tier, pos);
    }

    /**
     * Get and consume the forced quality tier for a chunk, if any.
     * Returns null if no forced quality is set.
     *
     * @param pos the chunk position
     * @return the forced quality tier, or null if none
     */
    public static QualityTier consumeForcedQuality(ChunkPos pos) {
        return FORCED_QUALITY.remove(pos.toLong());
    }

    /**
     * Get the current kill record for a chunk.
     *
     * @param chunkKey the chunk long key
     * @return the kill record, or null if no record exists
     */
    public static KillRecord getKillRecord(long chunkKey) {
        return KILL_RECORDS.get(chunkKey);
    }

    /**
     * Get the number of chunks with active kill records.
     *
     * @return the count of tracked chunks
     */
    public static int getTrackedChunkCount() {
        return KILL_RECORDS.size();
    }

    /**
     * Get the number of chunks currently in revenge state.
     *
     * @return the count of revenge chunks
     */
    public static int getRevengeChunkCount() {
        return REVENGE_CHUNKS.size();
    }

    // ==================== Cleanup ====================

    /**
     * Clear all revenge tracking data. Called on server stop.
     */
    public static void clearAll() {
        KILL_RECORDS.clear();
        FORCED_QUALITY.clear();
        REVENGE_CHUNKS.clear();
        LOGGER.info("Cleared all revenge tracking data");
    }
}
