package com.eliteforge.difficulty;

import com.eliteforge.EliteForge;
import com.eliteforge.config.EliteForgeConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-chunk heat tracking system. Heat accumulates when elite mobs are killed or
 * spawn in a chunk, and decays over time. Higher heat increases the difficulty
 * level modifier for future elite spawns in that chunk.
 * <p>
 * Heat affects difficulty: each 25 heat = +1 level modifier (max +2).
 * <p>
 * Persistence is handled via Minecraft's SavedData system.
 */
public class ChunkHeatManager extends SavedData {

    private static final Logger LOGGER = LogManager.getLogger();
    private static final String DATA_NAME = EliteForge.MODID + "_chunk_heat";

    // Default values for config keys that may not exist
    private static final float DEFAULT_DECAY_RATE = 0.01f;
    private static final float DEFAULT_HEAT_GAIN_KILL = 2.0f;
    private static final float DEFAULT_HEAT_GAIN_SPAWN = 1.0f;
    private static final float DEFAULT_MAX_HEAT = 100.0f;

    private final Map<Long, Float> heatMap = new ConcurrentHashMap<>();
    private int tickCounter = 0;

    public ChunkHeatManager() {
    }

    /**
     * Create a ChunkHeatManager from NBT data.
     */
    public static ChunkHeatManager load(CompoundTag tag) {
        ChunkHeatManager manager = new ChunkHeatManager();
        ListTag heatList = tag.getList("HeatEntries", Tag.TAG_COMPOUND);
        for (int i = 0; i < heatList.size(); i++) {
            CompoundTag entry = heatList.getCompound(i);
            long chunkKey = entry.getLong("ChunkKey");
            float heat = entry.getFloat("Heat");
            if (heat > 0.001f) {
                manager.heatMap.put(chunkKey, heat);
            }
        }
        return manager;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag heatList = new ListTag();
        for (Map.Entry<Long, Float> entry : heatMap.entrySet()) {
            if (entry.getValue() > 0.001f) { // Don't save negligible values
                CompoundTag entryTag = new CompoundTag();
                entryTag.putLong("ChunkKey", entry.getKey());
                entryTag.putFloat("Heat", entry.getValue());
                heatList.add(entryTag);
            }
        }
        tag.put("HeatEntries", heatList);
        return tag;
    }

    /**
     * Get or create the ChunkHeatManager for a server level.
     *
     * @param level the server level
     * @return the ChunkHeatManager for this level
     */
    public static ChunkHeatManager get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                ChunkHeatManager::load,
                ChunkHeatManager::new,
                DATA_NAME
        );
    }

    /**
     * Get the heat value for a chunk.
     *
     * @param level the server level
     * @param pos   the chunk position
     * @return the heat value (0.0 or higher)
     */
    public float getHeat(ServerLevel level, ChunkPos pos) {
        return heatMap.getOrDefault(pos.toLong(), 0.0f);
    }

    /**
     * Get the heat value for a chunk by block position.
     *
     * @param level    the server level
     * @param blockPos the block position (will be converted to chunk)
     * @return the heat value (0.0 or higher)
     */
    public float getHeat(ServerLevel level, BlockPos blockPos) {
        return getHeat(level, new ChunkPos(blockPos));
    }

    /**
     * Add heat to a chunk. Called on elite kill or elite spawn.
     * <p>
     * Note: The max heat cap is applied dynamically from config on every call
     * via {@link #getMaxHeat()}. This means that if a server operator changes
     * the {@code chunkHeatMax} config value at runtime (e.g., via /forge config),
     * the new cap takes effect immediately — existing heat values that exceed
     * the new cap will NOT be retroactively clamped, but any subsequent
     * {@code addHeat} call will apply the new cap. Heat values above the current
     * config cap will naturally decay over time.
     *
     * @param level  the server level
     * @param pos    the chunk position
     * @param amount the amount of heat to add (positive value)
     */
    public void addHeat(ServerLevel level, ChunkPos pos, float amount) {
        if (amount <= 0) {
            return;
        }

        long key = pos.toLong();
        float maxHeat = getMaxHeat();
        // Use compute() for atomic read-modify-write — prevents lost updates when
        // multiple threads (e.g., spawn handler + heat collector) modify heat concurrently.
        float newHeat = heatMap.compute(key, (k, current) -> {
            float c = current == null ? 0.0f : current;
            return Math.min(maxHeat, c + amount);
        });
        setDirty();

        LOGGER.debug("Added {} heat to chunk {} (now {})", amount, pos, newHeat);
    }

    /**
     * Reduce heat in a chunk. Called by Heat Collector and other heat-consuming mechanisms.
     *
     * @param level  the server level
     * @param pos    the chunk position
     * @param amount the amount of heat to reduce (positive value)
     */
    public void reduceHeat(ServerLevel level, ChunkPos pos, float amount) {
        if (amount <= 0) {
            return;
        }

        long key = pos.toLong();
        // Use compute() for atomic read-modify-write — prevents lost updates.
        // compute() returns null when the entry is removed; use Float to allow null.
        Float newHeat = heatMap.compute(key, (k, current) -> {
            float c = current == null ? 0.0f : current;
            if (c <= 0.001f) return null; // remove entry when fully consumed
            float result = Math.max(0.0f, c - amount);
            return result <= 0.001f ? null : result;
        });
        if (newHeat == null) {
            LOGGER.debug("Heat fully consumed for chunk {}", pos);
        } else {
            LOGGER.debug("Reduced {} heat from chunk {} (now {})", amount, pos, newHeat);
        }
        setDirty();
    }

    /**
     * Decay heat for a specific chunk. Called periodically.
     * Decay rate is configurable (default 0.01 per tick = 0.2/s).
     * Natural decay toward 0.
     *
     * @param level the server level
     * @param pos   the chunk position
     */
    public void decayHeat(ServerLevel level, ChunkPos pos) {
        long key = pos.toLong();
        float decayRate = getDecayRate();
        // Use compute() for atomic read-modify-write — prevents lost updates.
        heatMap.compute(key, (k, current) -> {
            if (current == null || current <= 0.001f) return null;
            float newHeat = current - decayRate;
            if (newHeat <= 0.001f) {
                LOGGER.debug("Heat fully decayed for chunk {}", pos);
                return null;
            }
            return newHeat;
        });
        setDirty();
    }

    /**
     * Called every server tick. Decays heat in all chunks.
     * Optimized to run every 20 ticks (1 second) to reduce overhead.
     *
     * @param level the server level
     */
    public void tick(ServerLevel level) {
        tickCounter++;

        // Only decay every 20 ticks (1 second) for performance
        if (tickCounter % 20 != 0) {
            return;
        }

        if (heatMap.isEmpty()) {
            return;
        }

        float decayRate = getDecayRate();
        // Multiply by 20 because we only tick every 20 server ticks
        float decayAmount = decayRate * 20.0f;

        Iterator<Map.Entry<Long, Float>> iterator = heatMap.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Long, Float> entry = iterator.next();
            float current = entry.getValue();
            float newHeat = current - decayAmount;

            if (newHeat <= 0.001f) {
                iterator.remove();
            } else {
                entry.setValue(newHeat);
            }
        }
        setDirty();
    }

    /**
     * Save heat data for a level (called explicitly or during auto-save).
     *
     * @param level the server level
     */
    public void saveHeatData(ServerLevel level) {
        this.setDirty();
        LOGGER.debug("Saved chunk heat data for level {} ({} entries)", level.dimension(), heatMap.size());
    }

    /**
     * Load heat data for a level. This is handled automatically by the SavedData system.
     *
     * @param level the server level
     */
    public void loadHeatData(ServerLevel level) {
        LOGGER.debug("Chunk heat data loaded for level {} ({} entries)", level.dimension(), heatMap.size());
    }

    /**
     * Get the difficulty level modifier from chunk heat.
     * Each 25 heat = +1 level modifier (max +2).
     *
     * @param level the server level
     * @param pos   the chunk position
     * @return the level modifier (0-2)
     */
    public int getDifficultyModifier(ServerLevel level, ChunkPos pos) {
        float heat = getHeat(level, pos);
        return Math.min(2, (int) (heat / 25.0f));
    }

    /**
     * Called when an elite mob is killed. Gains heat for the chunk.
     * Default gain: 2.0 (configurable).
     *
     * @param level    the server level
     * @param blockPos the block position where the kill occurred
     */
    public void onEliteKill(ServerLevel level, BlockPos blockPos) {
        float heatGain = getHeatGainOnKill();
        ChunkPos chunkPos = new ChunkPos(blockPos);
        addHeat(level, chunkPos, heatGain);
    }

    /**
     * Variant that applies the Heat Shield enchantment's heat-gain reduction for the
     * killing player. The enchantment scales the chunk-heat gain down by up to 15%/level
     * based on the total Heat Shield level across the player's armor pieces. Previously
     * {@code reduceHeatInfluence} was dead code with zero callers.
     *
     * @param level    the server level
     * @param blockPos the block position where the kill occurred
     * @param killer   the player who killed the elite (may be null → no enchantment reduction)
     */
    public void onEliteKill(ServerLevel level, BlockPos blockPos, @javax.annotation.Nullable net.minecraft.server.level.ServerPlayer killer) {
        float heatGain = getHeatGainOnKill();
        if (killer != null) {
            try {
                // Sum Heat Shield level across all armor slots.
                int totalLevel = 0;
                for (net.minecraft.world.entity.EquipmentSlot slot : new net.minecraft.world.entity.EquipmentSlot[]{
                        net.minecraft.world.entity.EquipmentSlot.HEAD,
                        net.minecraft.world.entity.EquipmentSlot.CHEST,
                        net.minecraft.world.entity.EquipmentSlot.LEGS,
                        net.minecraft.world.entity.EquipmentSlot.FEET
                }) {
                    totalLevel += killer.getItemBySlot(slot).getEnchantmentLevel(
                            com.eliteforge.init.ModEnchantments.HEAT_SHIELD.get());
                }
                if (totalLevel > 0) {
                    // reduceHeatInfluence(base, lvl) returns base * (1 - reduction);
                    // apply the reduction fraction to the heat gain.
                    float retained = com.eliteforge.enchantment.HeatShieldEnchantment.reduceHeatInfluence(1.0f, totalLevel);
                    heatGain *= retained;
                }
            } catch (Exception ignored) {
                // Enchantment not loaded / safe fallback: use base gain.
            }
        }
        ChunkPos chunkPos = new ChunkPos(blockPos);
        addHeat(level, chunkPos, heatGain);
    }

    /**
     * Called when an elite mob spawns in a chunk. Gains heat for the chunk.
     * Default gain: 1.0 (configurable).
     *
     * @param level    the server level
     * @param blockPos the block position where the elite spawned
     */
    public void onEliteSpawn(ServerLevel level, BlockPos blockPos) {
        float heatGain = getHeatGainOnSpawn();
        ChunkPos chunkPos = new ChunkPos(blockPos);
        addHeat(level, chunkPos, heatGain);
    }

    /**
     * Get the total number of chunks with active heat.
     *
     * @return the number of heated chunks
     */
    public int getHeatedChunkCount() {
        return heatMap.size();
    }

    /**
     * Get a copy of the heat map for debugging/display purposes.
     *
     * @return an unmodifiable copy of the heat map
     */
    public Map<Long, Float> getHeatMapSnapshot() {
        return Map.copyOf(heatMap);
    }

    // ==================== Config Helpers ====================

    private float getDecayRate() {
        try {
            return EliteForgeConfig.SERVER.chunkHeatDecayRate.get().floatValue();
        } catch (Exception e) {
            return DEFAULT_DECAY_RATE;
        }
    }

    private float getHeatGainOnKill() {
        try {
            return EliteForgeConfig.SERVER.chunkHeatGainOnEliteKill.get().floatValue();
        } catch (Exception e) {
            return DEFAULT_HEAT_GAIN_KILL;
        }
    }

    private float getHeatGainOnSpawn() {
        try {
            return EliteForgeConfig.SERVER.chunkHeatGainOnEliteSpawn.get().floatValue();
        } catch (Exception e) {
            return DEFAULT_HEAT_GAIN_SPAWN;
        }
    }

    private float getMaxHeat() {
        try {
            return EliteForgeConfig.SERVER.chunkHeatMax.get().floatValue();
        } catch (Exception e) {
            return DEFAULT_MAX_HEAT;
        }
    }
}
