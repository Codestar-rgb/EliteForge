package com.eliteforge.difficulty;

import com.eliteforge.EliteForge;
import com.eliteforge.config.EliteForgeConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player experience tracking (NOT Minecraft XP). This is a custom experience
 * system that tracks how much experience a player has accumulated from killing
 * elite mobs. Higher experience increases the difficulty level modifier for
 * future elite spawns near that player.
 * <p>
 * Experience affects difficulty: each 25 exp = +1 level modifier (max +2).
 * <p>
 * Persistence is handled via Minecraft's SavedData system.
 */
public class PlayerExperienceManager extends SavedData {

    private static final Logger LOGGER = LogManager.getLogger();
    private static final String DATA_NAME = EliteForge.MODID + "_player_experience";

    // Default values for config keys that may not exist
    private static final float DEFAULT_DECAY_RATE = 0.005f;
    private static final float DEFAULT_EXP_GAIN_KILL = 3.0f;
    private static final float DEFAULT_MAX_EXP = 100.0f;

    private final Map<UUID, Float> experienceMap = new ConcurrentHashMap<>();
    private int tickCounter = 0;

    public PlayerExperienceManager() {
    }

    /**
     * Create a PlayerExperienceManager from NBT data.
     */
    public static PlayerExperienceManager load(CompoundTag tag) {
        PlayerExperienceManager manager = new PlayerExperienceManager();
        ListTag expList = tag.getList("ExperienceEntries", Tag.TAG_COMPOUND);
        for (int i = 0; i < expList.size(); i++) {
            CompoundTag entry = expList.getCompound(i);
            UUID uuid = entry.getUUID("PlayerUUID");
            float exp = entry.getFloat("Experience");
            if (exp > 0.001f) {
                manager.experienceMap.put(uuid, exp);
            }
        }
        return manager;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag expList = new ListTag();
        for (Map.Entry<UUID, Float> entry : experienceMap.entrySet()) {
            if (entry.getValue() > 0.001f) { // Don't save negligible values
                CompoundTag entryTag = new CompoundTag();
                entryTag.putUUID("PlayerUUID", entry.getKey());
                entryTag.putFloat("Experience", entry.getValue());
                expList.add(entryTag);
            }
        }
        tag.put("ExperienceEntries", expList);
        return tag;
    }

    /**
     * Get or create the PlayerExperienceManager for a server level.
     * Always uses the overworld's data storage to ensure player experience
     * is global (not per-dimension).
     *
     * @param level the server level (used to access the server, but data is stored in overworld)
     * @return the PlayerExperienceManager (global, from overworld data storage)
     */
    public static PlayerExperienceManager get(ServerLevel level) {
        // Always use the overworld's data storage for global player experience
        ServerLevel overworld = level.getServer().getLevel(net.minecraft.world.level.Level.OVERWORLD);
        if (overworld != null) {
            return overworld.getDataStorage().computeIfAbsent(
                    PlayerExperienceManager::load,
                    PlayerExperienceManager::new,
                    DATA_NAME
            );
        }
        return level.getDataStorage().computeIfAbsent(
                PlayerExperienceManager::load,
                PlayerExperienceManager::new,
                DATA_NAME
        );
    }

    /**
     * Get the custom experience value for a player.
     *
     * @param player the server player
     * @return the experience value (0.0 or higher)
     */
    public float getPlayerExperience(ServerPlayer player) {
        if (player == null) {
            return 0.0f;
        }
        return experienceMap.getOrDefault(player.getUUID(), 0.0f);
    }

    /**
     * Get the custom experience value for a player by UUID.
     *
     * @param uuid the player's UUID
     * @return the experience value (0.0 or higher)
     */
    public float getPlayerExperience(UUID uuid) {
        if (uuid == null) {
            return 0.0f;
        }
        return experienceMap.getOrDefault(uuid, 0.0f);
    }

    /**
     * Add experience to a player. Called on elite kill.
     *
     * @param player the server player
     * @param amount the amount of experience to add (positive value)
     */
    public void addExperience(ServerPlayer player, float amount) {
        if (player == null || amount <= 0) {
            return;
        }

        UUID uuid = player.getUUID();
        float maxExp = getMaxExperience();
        // Use compute() for atomic read-modify-write — prevents lost updates when
        // multiple kills happen concurrently (e.g., multi-player party kills).
        float newExp = experienceMap.compute(uuid, (k, current) -> {
            float c = current == null ? 0.0f : current;
            return Math.min(maxExp, c + amount);
        });
        setDirty();

        LOGGER.debug("Added {} experience to player {} (now {})", amount, player.getName().getString(), newExp);
    }

    /**
     * Decay experience for a specific player. Called periodically.
     * Decay rate is configurable. Natural decay toward 0.
     *
     * @param player the server player
     */
    public void decayExperience(ServerPlayer player) {
        if (player == null) {
            return;
        }

        UUID uuid = player.getUUID();
        float decayRate = getDecayRate();
        // Use compute() for atomic read-modify-write — prevents lost updates.
        experienceMap.compute(uuid, (k, current) -> {
            if (current == null || current <= 0.001f) return null;
            float newExp = current - decayRate;
            if (newExp <= 0.001f) {
                LOGGER.debug("Experience fully decayed for player {}", player.getName().getString());
                return null;
            }
            return newExp;
        });
        setDirty();
    }

    /**
     * Tick all online players - decay their experience periodically.
     * Optimized to run every 100 ticks (5 seconds) to reduce overhead.
     *
     * @param level the server level
     */
    public void tick(ServerLevel level) {
        tickCounter++;

        // Only decay every 100 ticks (5 seconds) for performance
        if (tickCounter % 100 != 0) {
            return;
        }

        if (experienceMap.isEmpty()) {
            return;
        }

        float decayRate = getDecayRate();
        // Multiply by 100 because we only tick every 100 server ticks
        float decayAmount = decayRate * 100.0f;

        Iterator<Map.Entry<UUID, Float>> iterator = experienceMap.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Float> entry = iterator.next();
            float current = entry.getValue();
            float newExp = current - decayAmount;

            if (newExp <= 0.001f) {
                iterator.remove();
            } else {
                entry.setValue(newExp);
            }
        }
        setDirty();
    }

    /**
     * Save experience data for a level.
     *
     * @param level the server level
     */
    public void saveExperienceData(ServerLevel level) {
        this.setDirty();
        LOGGER.debug("Saved player experience data for level {} ({} entries)", level.dimension(), experienceMap.size());
    }

    /**
     * Load experience data for a level. Handled automatically by SavedData.
     *
     * @param level the server level
     */
    public void loadExperienceData(ServerLevel level) {
        LOGGER.debug("Player experience data loaded for level {} ({} entries)", level.dimension(), experienceMap.size());
    }

    /**
     * Get the difficulty level modifier from player experience.
     * Each 25 exp = +1 level modifier (max +2).
     *
     * @param player the server player
     * @return the level modifier (0-2)
     */
    public int getDifficultyModifier(ServerPlayer player) {
        float exp = getPlayerExperience(player);
        return Math.min(2, (int) (exp / 25.0f));
    }

    /**
     * Called when a player kills an elite mob. Gains experience for the player.
     * Default gain: 3.0 (configurable).
     *
     * @param player the server player who killed the elite
     */
    public void onEliteKill(ServerPlayer player) {
        float expGain = getExpGainOnKill();
        // Soul Collector enchantment: bonus XP per level on the killer's weapon.
        // Was previously dead code — applyExperienceBonus had zero callers.
        try {
            net.minecraft.world.item.ItemStack weapon = player.getMainHandItem();
            int soulLevel = weapon.getEnchantmentLevel(
                    com.eliteforge.init.ModEnchantments.SOUL_COLLECTOR.get());
            if (soulLevel > 0) {
                expGain = com.eliteforge.enchantment.SoulCollectorEnchantment.applyExperienceBonus(
                        player, Math.round(expGain), soulLevel);
            }
        } catch (Exception ignored) {
            // Enchantment not loaded / safe fallback: use base gain.
        }
        addExperience(player, expGain);
    }

    /**
     * Get the total number of players with active experience.
     *
     * @return the number of players with experience
     */
    public int getPlayerCount() {
        return experienceMap.size();
    }

    /**
     * Get a copy of the experience map for debugging/display purposes.
     *
     * @return an unmodifiable copy of the experience map
     */
    public Map<UUID, Float> getExperienceMapSnapshot() {
        return Map.copyOf(experienceMap);
    }

    /**
     * Reset a specific player's experience to 0.
     *
     * @param uuid the player's UUID
     */
    public void resetPlayerExperience(UUID uuid) {
        experienceMap.remove(uuid);
        setDirty();
    }

    /**
     * Reset all player experience data.
     */
    public void resetAll() {
        experienceMap.clear();
        setDirty();
    }

    // ==================== Config Helpers ====================

    private float getDecayRate() {
        try {
            return EliteForgeConfig.SERVER.playerExperienceDecayRate.get().floatValue();
        } catch (Exception e) {
            return DEFAULT_DECAY_RATE;
        }
    }

    private float getExpGainOnKill() {
        try {
            return EliteForgeConfig.SERVER.playerExperienceGainOnEliteKill.get().floatValue();
        } catch (Exception e) {
            return DEFAULT_EXP_GAIN_KILL;
        }
    }

    private float getMaxExperience() {
        try {
            return EliteForgeConfig.SERVER.playerExperienceMax.get().floatValue();
        } catch (Exception e) {
            return DEFAULT_MAX_EXP;
        }
    }
}
