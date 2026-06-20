package com.eliteforge.capability;

import com.eliteforge.config.DifficultyMode;
import com.eliteforge.quality.QualityTier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Data class stored per elite entity. Contains all information about an elite mob's
 * status, abilities, quality tier, anti-farm tracking data, and engagement tracking.
 * <p>
 * Phase 6 additions:
 * - engagedPlayerUUID: tracks the player currently fighting this elite
 * - engagedTime: when engagement started
 * - hasBeenEngaged: prevents despawn after engagement
 * - despawnTimer: countdown for CASUAL mode despawn (0 = no despawn)
 */
public class EliteData {

    private boolean isElite;
    private final Map<String, Integer> abilities;
    private QualityTier qualityTier;
    private int level;
    private float chunkHeatAtSpawn;
    private DifficultyMode spawnMode;
    private boolean hasDroppedLoot;
    private int killCount;
    private long lastKillTime;
    private Component customName;

    // Engagement tracking fields
    private UUID engagedPlayerUUID;
    private long engagedTime;
    private boolean hasBeenEngaged;

    // CASUAL mode despawn timer (in ticks; 0 = no auto-despawn)
    private int despawnTimer;

    // Maximum number of abilities this elite can have. -1 means no limit (backward compatible default).
    // When set to a positive value, addAbility() will reject additions that would exceed this limit.
    // Should typically be set from AbilityBudget.getMaxAbilities() during elite creation.
    private int maxAbilities = -1;

    /**
     * Maximum abilities for creator-tier entities. Allows 1 creator ability + up to 5
     * assimilated abilities + up to 4 abilities from reincarnation = 10 total.
     * This higher limit only applies when {@link #isCreatorEntity} is true.
     */
    public static final int MAX_CREATOR_ABILITIES = 10;

    // Creator-tier fields
    private boolean isCreatorEntity;
    private String creatorAbilityId;
    private int creatorAbilityLevel;
    private List<String> assimilatedAbilities;
    private int evolutionCount;
    private int reincarnationRemaining;
    private UUID commanderUUID;
    private UUID nexusSourceUUID; // UUID of the C1 Nexus that is nurturing this entity
    private UUID bestowerUUID; // UUID of the C5 Bestowal creator that bestowed this entity
    private UUID summonerUUID; // UUID of the entity that summoned this one (Necromancy undead, Clone clones). Used for purple chain rendering + leash pull-back.

    public EliteData() {
        this.isElite = false;
        this.abilities = new LinkedHashMap<>();
        this.qualityTier = QualityTier.NORMAL;
        this.level = 1;
        this.chunkHeatAtSpawn = 0.0f;
        this.spawnMode = DifficultyMode.FORGE;
        this.hasDroppedLoot = false;
        this.killCount = 0;
        this.lastKillTime = 0L;
        this.customName = null;
        this.engagedPlayerUUID = null;
        this.engagedTime = 0L;
        this.hasBeenEngaged = false;
        this.despawnTimer = 0;
        // Creator-tier defaults
        this.isCreatorEntity = false;
        this.creatorAbilityId = null;
        this.creatorAbilityLevel = 0;
        this.assimilatedAbilities = new ArrayList<>();
        this.evolutionCount = 0;
        this.reincarnationRemaining = 0;
        this.commanderUUID = null;
        this.nexusSourceUUID = null;
        this.bestowerUUID = null;
        this.summonerUUID = null;
    }

    // ==================== Ability Management ====================

    /**
     * Add or update an ability with the given ID and level.
     * If the ability already exists, the higher level is kept (merge with Math::max).
     * This prevents silent overwrites when the same ability is added from multiple sources
     * (e.g., Nexus nurturing + heat strengthening).
     * <p>
     * If {@link #maxAbilities} is set to a positive value and this would be a new ability
     * (i.e., the ID is not already in the map), the addition is silently rejected when
     * the limit has been reached. This prevents runtime additions (e.g., Nexus nurturing,
     * Heat strengthening) from exceeding the configured maximum.
     * <p>
     * Creator entities use a higher limit ({@link #MAX_CREATOR_ABILITIES}) instead of
     * the normal maxAbilities, allowing them to accumulate up to 10 abilities through
     * assimilation and reincarnation.
     * <p>
     * Updating an existing ability's level never triggers the limit check.
     *
     * @param id    the ability identifier
     * @param level the ability level (must be >= 1)
     * @return true if the ability was added or updated; false if rejected due to maxAbilities limit
     */
    public boolean addAbility(String id, int level) {
        if (id == null || id.isEmpty()) {
            throw new IllegalArgumentException("Ability ID cannot be null or empty");
        }
        if (level < 1) {
            throw new IllegalArgumentException("Ability level must be >= 1, got: " + level);
        }
        // Enforce max abilities limit for new additions only.
        // Creator entities use MAX_CREATOR_ABILITIES (10) instead of the normal maxAbilities,
        // allowing assimilation and reincarnation to exceed the normal limit of 5.
        if (!abilities.containsKey(id)) {
            int effectiveMax = isCreatorEntity ? MAX_CREATOR_ABILITIES : maxAbilities;
            if (effectiveMax > 0 && abilities.size() >= effectiveMax) {
                return false;
            }
        }
        abilities.merge(id, level, Math::max);
        return true;
    }

    /**
     * Remove an ability by ID.
     *
     * @param id the ability identifier to remove
     */
    public void removeAbility(String id) {
        abilities.remove(id);
    }

    /**
     * Get the level of an ability, or 0 if not present.
     *
     * @param id the ability identifier
     * @return the ability level, or 0 if the entity does not have this ability
     */
    public int getAbilityLevel(String id) {
        return abilities.getOrDefault(id, 0);
    }

    /**
     * Get an unmodifiable view of all abilities.
     *
     * @return unmodifiable map of ability ID to level
     */
    public Map<String, Integer> getAbilities() {
        return Collections.unmodifiableMap(abilities);
    }

    /**
     * Check if the entity has a specific ability.
     *
     * @param id the ability identifier
     * @return true if the entity has this ability
     */
    public boolean hasAbility(String id) {
        return abilities.containsKey(id);
    }

    // ==================== Getters and Setters ====================

    /**
     * Get the maximum number of abilities this elite can have.
     * A value of -1 means no limit (backward compatible default).
     *
     * @return the max abilities limit, or -1 for unlimited
     */
    public int getMaxAbilities() {
        return maxAbilities;
    }

    /**
     * Set the maximum number of abilities this elite can have.
     * Set to -1 for no limit. Should typically be set from
     * {@link com.eliteforge.ability.AbilityBudget#getMaxAbilities(int, com.eliteforge.config.DifficultyMode)}
     * during elite creation.
     *
     * @param maxAbilities the max abilities limit, or -1 for unlimited
     */
    public void setMaxAbilities(int maxAbilities) {
        this.maxAbilities = maxAbilities;
    }

    public boolean isElite() {
        return isElite;
    }

    public void setElite(boolean elite) {
        isElite = elite;
    }

    public QualityTier getQualityTier() {
        return qualityTier;
    }

    public void setQualityTier(QualityTier qualityTier) {
        this.qualityTier = qualityTier;
    }

    public int getLevel() {
        return level;
    }

    /**
     * Set the elite level.
     * v0.2.0 uses a 1-maxEliteLevel range (default 1-1500, configurable). The caller
     * (DifficultyManager / spawn pipeline) is responsible for clamping to the configured
     * max; here we only enforce the lower bound so a buggy caller can't store 0/-1.
     * (Was hardcoded Math.min(10, ...) which broke the 1-1500 level system.)
     */
    public void setLevel(int level) {
        this.level = Math.max(1, level);
    }

    public float getChunkHeatAtSpawn() {
        return chunkHeatAtSpawn;
    }

    public void setChunkHeatAtSpawn(float chunkHeatAtSpawn) {
        this.chunkHeatAtSpawn = chunkHeatAtSpawn;
    }

    public DifficultyMode getSpawnMode() {
        return spawnMode;
    }

    public void setSpawnMode(DifficultyMode spawnMode) {
        this.spawnMode = spawnMode;
    }

    public boolean hasDroppedLoot() {
        return hasDroppedLoot;
    }

    public void setHasDroppedLoot(boolean hasDroppedLoot) {
        this.hasDroppedLoot = hasDroppedLoot;
    }

    public int getKillCount() {
        return killCount;
    }

    public void setKillCount(int killCount) {
        this.killCount = killCount;
    }

    public long getLastKillTime() {
        return lastKillTime;
    }

    public void setLastKillTime(long lastKillTime) {
        this.lastKillTime = lastKillTime;
    }

    public Component getCustomName() {
        return customName;
    }

    public void setCustomName(Component customName) {
        this.customName = customName;
    }

    // ==================== Engagement Tracking ====================

    /**
     * Get the UUID of the player currently engaged with this elite.
     *
     * @return the engaged player UUID, or null if not engaged
     */
    public UUID getEngagedPlayerUUID() {
        return engagedPlayerUUID;
    }

    /**
     * Set the engaged player UUID. Called when a player starts fighting this elite.
     *
     * @param engagedPlayerUUID the player's UUID
     */
    public void setEngagedPlayerUUID(UUID engagedPlayerUUID) {
        this.engagedPlayerUUID = engagedPlayerUUID;
    }

    /**
     * Get the timestamp when engagement started.
     *
     * @return the engagement time in milliseconds
     */
    public long getEngagedTime() {
        return engagedTime;
    }

    /**
     * Set the engagement time.
     *
     * @param engagedTime the engagement timestamp in milliseconds
     */
    public void setEngagedTime(long engagedTime) {
        this.engagedTime = engagedTime;
    }

    /**
     * Check if this elite has been engaged by a player (prevents despawn after engagement).
     *
     * @return true if the elite has been engaged at least once
     */
    public boolean hasBeenEngaged() {
        return hasBeenEngaged;
    }

    /**
     * Set whether this elite has been engaged.
     *
     * @param hasBeenEngaged true if the elite has been engaged
     */
    public void setHasBeenEngaged(boolean hasBeenEngaged) {
        this.hasBeenEngaged = hasBeenEngaged;
    }

    /**
     * Engage this elite with a player. Sets the engaged player, time, and marks as engaged.
     *
     * @param playerUUID the player's UUID
     */
    public void engage(UUID playerUUID) {
        this.engagedPlayerUUID = playerUUID;
        this.engagedTime = System.currentTimeMillis();
        this.hasBeenEngaged = true;
    }

    /**
     * Disengage this elite (e.g., when the player dies or moves too far away).
     */
    public void disengage() {
        this.engagedPlayerUUID = null;
    }

    /**
     * Check if this elite is currently being engaged by a specific player.
     *
     * @param playerUUID the player's UUID to check
     * @return true if the player is currently engaged with this elite
     */
    public boolean isEngagedBy(UUID playerUUID) {
        return engagedPlayerUUID != null && engagedPlayerUUID.equals(playerUUID);
    }

    // ==================== Creator-Tier Data ====================

    /**
     * Check if this elite is a creator-tier entity.
     *
     * @return true if this is a creator-tier elite
     */
    public boolean isCreatorEntity() {
        return isCreatorEntity;
    }

    /**
     * Set whether this elite is a creator-tier entity.
     *
     * @param creatorEntity true if creator-tier
     */
    public void setCreatorEntity(boolean creatorEntity) {
        isCreatorEntity = creatorEntity;
    }

    /**
     * Get the creator ability ID.
     *
     * @return the creator ability ID, or null if not a creator
     */
    public String getCreatorAbilityId() {
        return creatorAbilityId;
    }

    /**
     * Set the creator ability ID.
     *
     * @param creatorAbilityId the creator ability ID
     */
    public void setCreatorAbilityId(String creatorAbilityId) {
        this.creatorAbilityId = creatorAbilityId;
    }

    /**
     * Get the creator ability level.
     *
     * @return the creator ability level
     */
    public int getCreatorAbilityLevel() {
        return creatorAbilityLevel;
    }

    /**
     * Set the creator ability level. Capped at 3 (max level for creator abilities).
     *
     * @param creatorAbilityLevel the creator ability level
     */
    public void setCreatorAbilityLevel(int creatorAbilityLevel) {
        this.creatorAbilityLevel = Math.max(1, Math.min(3, creatorAbilityLevel));
    }

    /**
     * Get the list of assimilated ability IDs.
     *
     * @return unmodifiable list of assimilated ability IDs
     */
    public List<String> getAssimilatedAbilities() {
        return Collections.unmodifiableList(assimilatedAbilities);
    }

    /**
     * Add an assimilated ability ID.
     *
     * @param abilityId the ability ID to add
     */
    public void addAssimilatedAbility(String abilityId) {
        if (abilityId != null && !abilityId.isEmpty()) {
            assimilatedAbilities.add(abilityId);
        }
    }

    /**
     * Get the evolution count for C3 Evolution.
     *
     * @return the number of times this entity has evolved
     */
    public int getEvolutionCount() {
        return evolutionCount;
    }

    /**
     * Set the evolution count.
     *
     * @param evolutionCount the evolution count
     */
    public void setEvolutionCount(int evolutionCount) {
        this.evolutionCount = evolutionCount;
    }

    /**
     * Get the remaining reincarnation count for C7 Reincarnation.
     *
     * @return remaining reincarnations
     */
    public int getReincarnationRemaining() {
        return reincarnationRemaining;
    }

    /**
     * Set the remaining reincarnation count.
     *
     * @param reincarnationRemaining remaining reincarnations
     */
    public void setReincarnationRemaining(int reincarnationRemaining) {
        this.reincarnationRemaining = reincarnationRemaining;
    }

    /**
     * Get the UUID of the commander entity (C8 Commander).
     *
     * @return the commander UUID, or null if not in a squad
     */
    public UUID getCommanderUUID() {
        return commanderUUID;
    }

    /**
     * Set the commander UUID.
     *
     * @param commanderUUID the commander's UUID
     */
    public void setCommanderUUID(UUID commanderUUID) {
        this.commanderUUID = commanderUUID;
    }

    /**
     * Get the UUID of the Nexus source entity (C1 Nexus).
     *
     * @return the nexus source UUID, or null if not being nurtured
     */
    public UUID getNexusSourceUUID() {
        return nexusSourceUUID;
    }

    /**
     * Set the nexus source UUID.
     *
     * @param nexusSourceUUID the nexus source UUID
     */
    public void setNexusSourceUUID(UUID nexusSourceUUID) {
        this.nexusSourceUUID = nexusSourceUUID;
    }

    /**
     * Get the UUID of the Bestower entity (C5 Bestowal).
     *
     * @return the bestower UUID, or null if not bestowed
     */
    public UUID getBestowerUUID() {
        return bestowerUUID;
    }

    /**
     * Set the bestower UUID.
     *
     * @param bestowerUUID the bestower's UUID
     */
    public void setBestowerUUID(UUID bestowerUUID) {
        this.bestowerUUID = bestowerUUID;
    }

    /**
     * Get the UUID of the entity that summoned this one (Necromancy undead, Clone clones).
     * Used for purple chain rendering and leash pull-back logic.
     *
     * @return the summoner UUID, or null if this entity was not summoned
     */
    public UUID getSummonerUUID() {
        return summonerUUID;
    }

    /**
     * Set the summoner UUID. Also persisted to the entity's persistent NBT
     * by the caller (Necromancy/Clone) for fast server-side leash checks.
     *
     * @param summonerUUID the summoner's UUID, or null to clear
     */
    public void setSummonerUUID(UUID summonerUUID) {
        this.summonerUUID = summonerUUID;
    }

    // ==================== Despawn Timer ====================

    /**
     * Get the despawn timer value in ticks.
     *
     * @return the despawn timer (0 = no auto-despawn)
     */
    public int getDespawnTimer() {
        return despawnTimer;
    }

    /**
     * Set the despawn timer in ticks. Set to 0 to disable auto-despawn.
     *
     * @param despawnTimer the despawn timer value
     */
    public void setDespawnTimer(int despawnTimer) {
        this.despawnTimer = despawnTimer;
    }

    /**
     * Tick the despawn timer by the given amount. Returns true if the timer has expired.
     * Does not tick if the elite has been engaged.
     *
     * @param decrement the number of ticks to decrement (should match the batch interval,
     *                  e.g., 20 since processEliteBatch runs every 20 server ticks)
     * @return true if the elite should despawn
     */
    public boolean tickDespawnTimer(int decrement) {
        if (hasBeenEngaged || despawnTimer <= 0) {
            return false;
        }
        despawnTimer -= decrement;
        return despawnTimer <= 0;
    }

    // ==================== Copy ====================

    /**
     * Create a deep copy of this EliteData instance.
     *
     * @return a new EliteData with identical values
     */
    public EliteData copy() {
        EliteData copy = new EliteData();
        copy.isElite = this.isElite;
        copy.abilities.putAll(this.abilities);
        copy.qualityTier = this.qualityTier;
        copy.level = this.level;
        copy.chunkHeatAtSpawn = this.chunkHeatAtSpawn;
        copy.spawnMode = this.spawnMode;
        copy.hasDroppedLoot = this.hasDroppedLoot;
        copy.killCount = this.killCount;
        copy.lastKillTime = this.lastKillTime;
        copy.customName = this.customName != null ? this.customName.copy() : null;
        copy.engagedPlayerUUID = this.engagedPlayerUUID;
        copy.engagedTime = this.engagedTime;
        copy.hasBeenEngaged = this.hasBeenEngaged;
        copy.despawnTimer = this.despawnTimer;
        copy.maxAbilities = this.maxAbilities;
        // Creator-tier fields
        copy.isCreatorEntity = this.isCreatorEntity;
        copy.creatorAbilityId = this.creatorAbilityId;
        copy.creatorAbilityLevel = this.creatorAbilityLevel;
        copy.assimilatedAbilities = new ArrayList<>(this.assimilatedAbilities);
        copy.evolutionCount = this.evolutionCount;
        copy.reincarnationRemaining = this.reincarnationRemaining;
        copy.commanderUUID = this.commanderUUID;
        copy.nexusSourceUUID = this.nexusSourceUUID;
        copy.bestowerUUID = this.bestowerUUID;
        copy.summonerUUID = this.summonerUUID;
        return copy;
    }

    // ==================== NBT Serialization ====================

    private static final String KEY_IS_ELITE = "IsElite";
    private static final String KEY_ABILITIES = "Abilities";
    private static final String KEY_ABILITY_ID = "Id";
    private static final String KEY_ABILITY_LEVEL = "Level";
    private static final String KEY_QUALITY_TIER = "QualityTier";
    private static final String KEY_LEVEL = "EliteLevel";
    private static final String KEY_CHUNK_HEAT = "ChunkHeatAtSpawn";
    private static final String KEY_SPAWN_MODE = "SpawnMode";
    private static final String KEY_HAS_DROPPED_LOOT = "HasDroppedLoot";
    private static final String KEY_KILL_COUNT = "KillCount";
    private static final String KEY_LAST_KILL_TIME = "LastKillTime";
    private static final String KEY_CUSTOM_NAME = "CustomName";
    private static final String KEY_ENGAGED_PLAYER_UUID = "EngagedPlayerUUID";
    private static final String KEY_ENGAGED_TIME = "EngagedTime";
    private static final String KEY_HAS_BEEN_ENGAGED = "HasBeenEngaged";
    private static final String KEY_DESPAWN_TIMER = "DespawnTimer";
    private static final String KEY_MAX_ABILITIES = "MaxAbilities";
    private static final String KEY_IS_CREATOR = "IsCreator";
    private static final String KEY_CREATOR_ABILITY_ID = "CreatorAbilityId";
    private static final String KEY_CREATOR_ABILITY_LEVEL = "CreatorAbilityLevel";
    private static final String KEY_ASSIMILATED_ABILITIES = "AssimilatedAbilities";
    private static final String KEY_EVOLUTION_COUNT = "EvolutionCount";
    private static final String KEY_REINCARNATION_REMAINING = "ReincarnationRemaining";
    private static final String KEY_COMMANDER_UUID = "CommanderUUID";
    private static final String KEY_NEXUS_SOURCE_UUID = "NexusSourceUUID";
    private static final String KEY_BESTOWER_UUID = "BestowerUUID";
    private static final String KEY_SUMMONER_UUID = "SummonerUUID";

    /**
     * Serialize this EliteData to an NBT compound tag.
     *
     * @return the serialized NBT data
     */
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(KEY_IS_ELITE, isElite);

        // Serialize abilities map
        ListTag abilitiesList = new ListTag();
        for (Map.Entry<String, Integer> entry : abilities.entrySet()) {
            CompoundTag abilityTag = new CompoundTag();
            abilityTag.putString(KEY_ABILITY_ID, entry.getKey());
            abilityTag.putInt(KEY_ABILITY_LEVEL, entry.getValue());
            abilitiesList.add(abilityTag);
        }
        tag.put(KEY_ABILITIES, abilitiesList);

        tag.putString(KEY_QUALITY_TIER, qualityTier.name());
        tag.putInt(KEY_LEVEL, level);
        tag.putFloat(KEY_CHUNK_HEAT, chunkHeatAtSpawn);
        tag.putString(KEY_SPAWN_MODE, spawnMode.getId());
        tag.putBoolean(KEY_HAS_DROPPED_LOOT, hasDroppedLoot);
        tag.putInt(KEY_KILL_COUNT, killCount);
        tag.putLong(KEY_LAST_KILL_TIME, lastKillTime);

        if (customName != null) {
            tag.putString(KEY_CUSTOM_NAME, Component.Serializer.toJson(customName));
        }

        // Engagement tracking
        if (engagedPlayerUUID != null) {
            tag.putUUID(KEY_ENGAGED_PLAYER_UUID, engagedPlayerUUID);
        }
        tag.putLong(KEY_ENGAGED_TIME, engagedTime);
        tag.putBoolean(KEY_HAS_BEEN_ENGAGED, hasBeenEngaged);
        tag.putInt(KEY_DESPAWN_TIMER, despawnTimer);
        tag.putInt(KEY_MAX_ABILITIES, maxAbilities);

        // Creator-tier serialization
        tag.putBoolean(KEY_IS_CREATOR, isCreatorEntity);
        if (creatorAbilityId != null) {
            tag.putString(KEY_CREATOR_ABILITY_ID, creatorAbilityId);
        }
        tag.putInt(KEY_CREATOR_ABILITY_LEVEL, creatorAbilityLevel);
        ListTag assimilatedList = new ListTag();
        for (String id : assimilatedAbilities) {
            CompoundTag aTag = new CompoundTag();
            aTag.putString(KEY_ABILITY_ID, id);
            assimilatedList.add(aTag);
        }
        tag.put(KEY_ASSIMILATED_ABILITIES, assimilatedList);
        tag.putInt(KEY_EVOLUTION_COUNT, evolutionCount);
        tag.putInt(KEY_REINCARNATION_REMAINING, reincarnationRemaining);
        if (commanderUUID != null) {
            tag.putUUID(KEY_COMMANDER_UUID, commanderUUID);
        }
        if (nexusSourceUUID != null) {
            tag.putUUID(KEY_NEXUS_SOURCE_UUID, nexusSourceUUID);
        }
        if (bestowerUUID != null) {
            tag.putUUID(KEY_BESTOWER_UUID, bestowerUUID);
        }
        if (summonerUUID != null) {
            tag.putUUID(KEY_SUMMONER_UUID, summonerUUID);
        }

        return tag;
    }

    /**
     * Deserialize EliteData from an NBT compound tag, overwriting all fields.
     *
     * @param tag the NBT data to read from
     */
    public void deserializeNBT(CompoundTag tag) {
        isElite = tag.getBoolean(KEY_IS_ELITE);

        // Deserialize abilities map
        abilities.clear();
        ListTag abilitiesList = tag.getList(KEY_ABILITIES, Tag.TAG_COMPOUND);
        for (int i = 0; i < abilitiesList.size(); i++) {
            CompoundTag abilityTag = abilitiesList.getCompound(i);
            String id = abilityTag.getString(KEY_ABILITY_ID);
            int lvl = abilityTag.getInt(KEY_ABILITY_LEVEL);
            if (!id.isEmpty() && lvl > 0) {
                abilities.put(id, lvl);
            }
        }

        try {
            qualityTier = QualityTier.valueOf(tag.getString(KEY_QUALITY_TIER));
        } catch (IllegalArgumentException e) {
            qualityTier = QualityTier.NORMAL;
        }

        // v0.2.0: level is 1-maxEliteLevel (configurable). Old saves may have the
        // legacy 1-10 value; new saves have the full range. Only enforce the lower
        // bound here — the spawn pipeline clamps to maxEliteLevel at creation time.
        // (Was Math.min(10, ...) which broke the 1-1500 level system on save/load.)
        level = Math.max(1, tag.getInt(KEY_LEVEL));
        chunkHeatAtSpawn = tag.getFloat(KEY_CHUNK_HEAT);

        String modeId = tag.getString(KEY_SPAWN_MODE);
        spawnMode = DifficultyMode.byId(modeId);

        hasDroppedLoot = tag.getBoolean(KEY_HAS_DROPPED_LOOT);
        killCount = tag.getInt(KEY_KILL_COUNT);
        lastKillTime = tag.getLong(KEY_LAST_KILL_TIME);

        if (tag.contains(KEY_CUSTOM_NAME, Tag.TAG_STRING)) {
            try {
                customName = Component.Serializer.fromJson(tag.getString(KEY_CUSTOM_NAME));
            } catch (Exception e) {
                customName = null;
            }
        } else {
            customName = null;
        }

        // Engagement tracking
        engagedPlayerUUID = tag.hasUUID(KEY_ENGAGED_PLAYER_UUID) ? tag.getUUID(KEY_ENGAGED_PLAYER_UUID) : null;
        engagedTime = tag.getLong(KEY_ENGAGED_TIME);
        hasBeenEngaged = tag.getBoolean(KEY_HAS_BEEN_ENGAGED);
        despawnTimer = tag.getInt(KEY_DESPAWN_TIMER);
        // Q5: maxAbilities backward-compat — Legacy saves may not contain this key.
        // tag.getInt() returns 0 for absent keys, but 0 is not a valid limit
        // (it would block all ability additions). Treat 0 as "unlimited" (-1)
        // to preserve old behavior. New saves always write a real value.
        maxAbilities = tag.getInt(KEY_MAX_ABILITIES);
        if (maxAbilities == 0) maxAbilities = -1;

        // Creator-tier deserialization
        isCreatorEntity = tag.getBoolean(KEY_IS_CREATOR);
        creatorAbilityId = tag.contains(KEY_CREATOR_ABILITY_ID, Tag.TAG_STRING) ? tag.getString(KEY_CREATOR_ABILITY_ID) : null;
        creatorAbilityLevel = tag.getInt(KEY_CREATOR_ABILITY_LEVEL);
        assimilatedAbilities.clear();
        ListTag assimilatedList = tag.getList(KEY_ASSIMILATED_ABILITIES, Tag.TAG_COMPOUND);
        for (int i = 0; i < assimilatedList.size(); i++) {
            CompoundTag aTag = assimilatedList.getCompound(i);
            String id = aTag.getString(KEY_ABILITY_ID);
            if (!id.isEmpty()) {
                assimilatedAbilities.add(id);
            }
        }
        evolutionCount = tag.getInt(KEY_EVOLUTION_COUNT);
        reincarnationRemaining = tag.getInt(KEY_REINCARNATION_REMAINING);
        commanderUUID = tag.hasUUID(KEY_COMMANDER_UUID) ? tag.getUUID(KEY_COMMANDER_UUID) : null;
        nexusSourceUUID = tag.hasUUID(KEY_NEXUS_SOURCE_UUID) ? tag.getUUID(KEY_NEXUS_SOURCE_UUID) : null;
        bestowerUUID = tag.hasUUID(KEY_BESTOWER_UUID) ? tag.getUUID(KEY_BESTOWER_UUID) : null;
        summonerUUID = tag.hasUUID(KEY_SUMMONER_UUID) ? tag.getUUID(KEY_SUMMONER_UUID) : null;
    }

    @Override
    public String toString() {
        return "EliteData{" +
                "isElite=" + isElite +
                ", abilities=" + abilities +
                ", qualityTier=" + qualityTier +
                ", level=" + level +
                ", chunkHeatAtSpawn=" + chunkHeatAtSpawn +
                ", spawnMode=" + spawnMode +
                ", hasBeenEngaged=" + hasBeenEngaged +
                ", despawnTimer=" + despawnTimer +
                ", isCreator=" + isCreatorEntity +
                ", creatorAbility=" + creatorAbilityId +
                '}';
    }
}
