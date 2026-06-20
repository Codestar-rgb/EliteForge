package com.eliteforge.datapack;

import com.google.gson.*;
import com.google.gson.annotations.SerializedName;
import com.eliteforge.quality.QualityTier;
import net.minecraft.resources.ResourceLocation;

import java.util.*;

/**
 * Data class representing an entity preset loaded from datapacks.
 * Presets define configuration for how elite mobs of a specific entity type
 * are generated, including level ranges, ability weights, and budget overrides.
 */
public class EntityPreset {

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    // The entity type this preset applies to
    @SerializedName("entity")
    private String entityIdStr;

    private transient ResourceLocation entityId;

    // Base level for this entity type when spawned as elite
    @SerializedName("baseLevel")
    private int baseLevel = 1;

    // Maximum level this entity can reach
    @SerializedName("maxLevel")
    private int maxLevel = 5;

    // Weighted ability IDs for random selection (ability ID -> weight)
    @SerializedName("abilityWeights")
    private Map<String, Float> abilityWeights = new HashMap<>();

    // Ability IDs that cannot be assigned to this entity
    @SerializedName("abilityBlacklist")
    private Set<String> abilityBlacklist = new HashSet<>();

    // Override budget values by category (category name -> budget)
    @SerializedName("budgetOverrides")
    private Map<String, Float> budgetOverrides = new HashMap<>();

    // Override spawn chance for this entity (null = use global)
    @SerializedName("spawnChanceOverride")
    private Float spawnChanceOverride = null;

    // Abilities that are always applied (ability ID -> forced level)
    @SerializedName("forcedAbilities")
    private Map<String, Integer> forcedAbilities = new HashMap<>();

    // Quality tier weight overrides for this entity (tier name -> weight)
    // If present, these replace the default weights from QualityHelper.determineQuality
    // Example: {"NORMAL": 40, "GOOD": 35, "FINE": 15, "EPIC": 8, "LEGENDARY": 2}
    @SerializedName("qualityWeights")
    private Map<String, Integer> qualityWeights = null;

    // Whether this entity type can become a creator-tier elite (null = use global setting)
    @SerializedName("creatorAllowed")
    private Boolean creatorAllowed = null;

    // Multiplier applied to this entity's elite health bonus (null = 1.0, no override)
    // Example: 0.5 = half the normal health bonus, 2.0 = double
    @SerializedName("healthMultiplier")
    private Float healthMultiplier = null;

    // Multiplier applied to this entity's elite damage bonus (null = 1.0, no override)
    @SerializedName("damageMultiplier")
    private Float damageMultiplier = null;

    // Force a specific quality tier for this entity (null = use weighted random)
    // Example: "LEGENDARY" forces all elites of this type to be legendary
    @SerializedName("forcedQuality")
    private String forcedQuality = null;

    // Force a specific difficulty level for this entity (null = use calculated level)
    // Range: 1-5
    @SerializedName("forcedLevel")
    private Integer forcedLevel = null;

    // Disable this entity from becoming elite entirely (overrides everything)
    @SerializedName("disabled")
    private Boolean disabled = null;

    public EntityPreset() {
    }

    public EntityPreset(ResourceLocation entityId) {
        this.entityId = entityId;
        this.entityIdStr = entityId.toString();
    }

    // ============ Getters ============

    public ResourceLocation getEntityId() {
        if (entityId == null && entityIdStr != null) {
            entityId = ResourceLocation.tryParse(entityIdStr);
        }
        return entityId;
    }

    public int getBaseLevel() {
        return baseLevel;
    }

    public int getMaxLevel() {
        return maxLevel;
    }

    public Map<String, Float> getAbilityWeights() {
        return abilityWeights != null ? abilityWeights : Collections.emptyMap();
    }

    public Set<String> getAbilityBlacklist() {
        return abilityBlacklist != null ? abilityBlacklist : Collections.emptySet();
    }

    public Map<String, Float> getBudgetOverrides() {
        return budgetOverrides != null ? budgetOverrides : Collections.emptyMap();
    }

    public Float getSpawnChanceOverride() {
        return spawnChanceOverride;
    }

    public Map<String, Integer> getForcedAbilities() {
        return forcedAbilities != null ? forcedAbilities : Collections.emptyMap();
    }

    /**
     * Get quality tier weight overrides for this entity type.
     * If null or empty, the default weights from QualityHelper are used.
     * Keys are QualityTier enum names (NORMAL, GOOD, FINE, EPIC, LEGENDARY).
     * MYTHIC is excluded — it can only be assigned via creator conversion.
     *
     * @return map of tier name to weight, or null if no override
     */
    public Map<String, Integer> getQualityWeights() {
        return qualityWeights;
    }

    /**
     * Check if this entity type is allowed to become a creator-tier elite.
     * Returns null to use the global setting, true/false to override per-entity.
     *
     * @return Boolean.TRUE if allowed, Boolean.FALSE if disallowed, null if use global
     */
    public Boolean getCreatorAllowed() {
        return creatorAllowed;
    }

    /**
     * Get the health multiplier override for this entity type.
     * Applied to the elite's health bonus (not base health).
     * Returns null to use the default (1.0).
     *
     * @return health multiplier, or null if no override
     */
    public Float getHealthMultiplier() {
        return healthMultiplier;
    }

    /**
     * Get the damage multiplier override for this entity type.
     * Applied to the elite's damage bonus.
     * Returns null to use the default (1.0).
     *
     * @return damage multiplier, or null if no override
     */
    public Float getDamageMultiplier() {
        return damageMultiplier;
    }

    /**
     * Get the forced quality tier for this entity type.
     * Returns null to use weighted random, or a QualityTier name like "LEGENDARY".
     */
    public String getForcedQuality() {
        return forcedQuality;
    }

    /**
     * Get the forced difficulty level for this entity type.
     * Returns null to use calculated level, or 1-5.
     */
    public Integer getForcedLevel() {
        return forcedLevel;
    }

    /**
     * Check if this entity type is disabled from becoming elite.
     * Returns null to use normal checks, true to disable, false to force-enable.
     */
    public Boolean getDisabled() {
        return disabled;
    }

    // ============ Setters ============

    public void setBaseLevel(int baseLevel) {
        this.baseLevel = baseLevel;
    }

    public void setMaxLevel(int maxLevel) {
        this.maxLevel = maxLevel;
    }

    public void setAbilityWeights(Map<String, Float> abilityWeights) {
        this.abilityWeights = abilityWeights != null ? abilityWeights : new HashMap<>();
    }

    public void setAbilityBlacklist(Set<String> abilityBlacklist) {
        this.abilityBlacklist = abilityBlacklist != null ? abilityBlacklist : new HashSet<>();
    }

    public void setBudgetOverrides(Map<String, Float> budgetOverrides) {
        this.budgetOverrides = budgetOverrides != null ? budgetOverrides : new HashMap<>();
    }

    public void setSpawnChanceOverride(Float spawnChanceOverride) {
        this.spawnChanceOverride = spawnChanceOverride;
    }

    public void setForcedAbilities(Map<String, Integer> forcedAbilities) {
        this.forcedAbilities = forcedAbilities != null ? forcedAbilities : new HashMap<>();
    }

    // ============ Serialization ============

    /**
     * Serializes this preset to JSON.
     */
    public JsonObject serialize() {
        JsonObject json = new JsonObject();

        json.addProperty("entity", getEntityId() != null ? getEntityId().toString() : entityIdStr);
        json.addProperty("baseLevel", baseLevel);
        json.addProperty("maxLevel", maxLevel);

        // Ability weights
        JsonObject weightsObj = new JsonObject();
        for (Map.Entry<String, Float> entry : getAbilityWeights().entrySet()) {
            weightsObj.addProperty(entry.getKey(), entry.getValue());
        }
        json.add("abilityWeights", weightsObj);

        // Ability blacklist
        JsonArray blacklistArr = new JsonArray();
        for (String id : getAbilityBlacklist()) {
            blacklistArr.add(id);
        }
        json.add("abilityBlacklist", blacklistArr);

        // Budget overrides
        JsonObject budgetObj = new JsonObject();
        for (Map.Entry<String, Float> entry : getBudgetOverrides().entrySet()) {
            budgetObj.addProperty(entry.getKey(), entry.getValue());
        }
        json.add("budgetOverrides", budgetObj);

        // Spawn chance override
        if (spawnChanceOverride != null) {
            json.addProperty("spawnChanceOverride", spawnChanceOverride);
        }

        // Forced abilities
        JsonObject forcedObj = new JsonObject();
        for (Map.Entry<String, Integer> entry : getForcedAbilities().entrySet()) {
            forcedObj.addProperty(entry.getKey(), entry.getValue());
        }
        json.add("forcedAbilities", forcedObj);

        // Optional override fields — only serialized when set, so defaults stay clean.
        if (qualityWeights != null) {
            JsonObject qwObj = new JsonObject();
            for (Map.Entry<String, Integer> entry : qualityWeights.entrySet()) {
                qwObj.addProperty(entry.getKey(), entry.getValue());
            }
            json.add("qualityWeights", qwObj);
        }
        if (creatorAllowed != null) json.addProperty("creatorAllowed", creatorAllowed);
        if (healthMultiplier != null) json.addProperty("healthMultiplier", healthMultiplier);
        if (damageMultiplier != null) json.addProperty("damageMultiplier", damageMultiplier);
        if (forcedQuality != null) json.addProperty("forcedQuality", forcedQuality);
        if (forcedLevel != null) json.addProperty("forcedLevel", forcedLevel);
        if (disabled != null) json.addProperty("disabled", disabled);

        return json;
    }

    /**
     * Deserializes a preset from JSON.
     */
    public static EntityPreset deserialize(JsonObject json) {
        EntityPreset preset = new EntityPreset();

        // Entity ID
        if (json.has("entity")) {
            preset.entityIdStr = json.get("entity").getAsString();
            preset.entityId = ResourceLocation.tryParse(preset.entityIdStr);
        }

        // Base level
        if (json.has("baseLevel")) {
            preset.baseLevel = json.get("baseLevel").getAsInt();
        }

        // Max level
        if (json.has("maxLevel")) {
            preset.maxLevel = json.get("maxLevel").getAsInt();
        }

        // Ability weights
        if (json.has("abilityWeights") && json.get("abilityWeights").isJsonObject()) {
            preset.abilityWeights = new HashMap<>();
            JsonObject weightsObj = json.getAsJsonObject("abilityWeights");
            for (Map.Entry<String, JsonElement> entry : weightsObj.entrySet()) {
                preset.abilityWeights.put(entry.getKey(), entry.getValue().getAsFloat());
            }
        }

        // Ability blacklist
        if (json.has("abilityBlacklist") && json.get("abilityBlacklist").isJsonArray()) {
            preset.abilityBlacklist = new HashSet<>();
            for (JsonElement element : json.getAsJsonArray("abilityBlacklist")) {
                preset.abilityBlacklist.add(element.getAsString());
            }
        }

        // Budget overrides
        if (json.has("budgetOverrides") && json.get("budgetOverrides").isJsonObject()) {
            preset.budgetOverrides = new HashMap<>();
            JsonObject budgetObj = json.getAsJsonObject("budgetOverrides");
            for (Map.Entry<String, JsonElement> entry : budgetObj.entrySet()) {
                preset.budgetOverrides.put(entry.getKey(), entry.getValue().getAsFloat());
            }
        }

        // Spawn chance override
        if (json.has("spawnChanceOverride") && !json.get("spawnChanceOverride").isJsonNull()) {
            preset.spawnChanceOverride = json.get("spawnChanceOverride").getAsFloat();
        }

        // Forced abilities
        if (json.has("forcedAbilities") && json.get("forcedAbilities").isJsonObject()) {
            preset.forcedAbilities = new HashMap<>();
            JsonObject forcedObj = json.getAsJsonObject("forcedAbilities");
            for (Map.Entry<String, JsonElement> entry : forcedObj.entrySet()) {
                preset.forcedAbilities.put(entry.getKey(), entry.getValue().getAsInt());
            }
        }

        // Quality weights (overrides QualityTier weighted random). Keys must be valid
        // QualityTier names; values are integer weights.
        if (json.has("qualityWeights") && json.get("qualityWeights").isJsonObject()) {
            preset.qualityWeights = new HashMap<>();
            JsonObject qwObj = json.getAsJsonObject("qualityWeights");
            for (Map.Entry<String, JsonElement> entry : qwObj.entrySet()) {
                preset.qualityWeights.put(entry.getKey(), entry.getValue().getAsInt());
            }
        }

        // Whether this entity type is allowed to become a creator-tier elite.
        if (json.has("creatorAllowed") && !json.get("creatorAllowed").isJsonNull()) {
            preset.creatorAllowed = json.get("creatorAllowed").getAsBoolean();
        }

        // Health/damage multipliers applied on top of the normal elite scaling.
        if (json.has("healthMultiplier") && !json.get("healthMultiplier").isJsonNull()) {
            preset.healthMultiplier = json.get("healthMultiplier").getAsFloat();
        }
        if (json.has("damageMultiplier") && !json.get("damageMultiplier").isJsonNull()) {
            preset.damageMultiplier = json.get("damageMultiplier").getAsFloat();
        }

        // Forced quality tier (skips the weighted-random roll).
        if (json.has("forcedQuality") && !json.get("forcedQuality").isJsonNull()) {
            preset.forcedQuality = json.get("forcedQuality").getAsString();
        }

        // Forced elite level (skips the heat/experience-based level calculation).
        if (json.has("forcedLevel") && !json.get("forcedLevel").isJsonNull()) {
            preset.forcedLevel = json.get("forcedLevel").getAsInt();
        }

        // Disable this entity type from ever becoming elite (spawn-pipeline short-circuit).
        if (json.has("disabled") && !json.get("disabled").isJsonNull()) {
            preset.disabled = json.get("disabled").getAsBoolean();
        }

        return preset;
    }

    /**
     * Deserializes a preset from a JSON string.
     */
    public static EntityPreset fromJson(String jsonStr) throws JsonParseException {
        JsonObject json = GSON.fromJson(jsonStr, JsonObject.class);
        if (json == null) {
            throw new JsonParseException("Failed to parse entity preset JSON");
        }
        EntityPreset preset = deserialize(json);
        preset.validate();
        return preset;
    }

    /**
     * Serializes this preset to a JSON string.
     */
    public String toJson() {
        return GSON.toJson(serialize());
    }

    // ============ Validation ============

    /**
     * Validates this preset and throws if invalid.
     */
    public void validate() throws IllegalStateException {
        // Entity ID must be valid
        if (getEntityId() == null) {
            throw new IllegalStateException("Entity preset has invalid or missing entity ID: " + entityIdStr);
        }

        // Level constraints
        if (baseLevel < 1) {
            throw new IllegalStateException("Entity preset for " + entityId + " has baseLevel < 1: " + baseLevel);
        }
        if (maxLevel < baseLevel) {
            throw new IllegalStateException("Entity preset for " + entityId + " has maxLevel < baseLevel: " + maxLevel + " < " + baseLevel);
        }

        // Validate ability weights
        for (Map.Entry<String, Float> entry : getAbilityWeights().entrySet()) {
            if (entry.getValue() < 0) {
                throw new IllegalStateException("Entity preset for " + entityId + " has negative weight for ability " + entry.getKey());
            }
            ResourceLocation rl = ResourceLocation.tryParse(entry.getKey());
            if (rl == null) {
                throw new IllegalStateException("Entity preset for " + entityId + " has invalid ability ID in weights: " + entry.getKey());
            }
        }

        // Validate ability blacklist
        for (String id : getAbilityBlacklist()) {
            ResourceLocation rl = ResourceLocation.tryParse(id);
            if (rl == null) {
                throw new IllegalStateException("Entity preset for " + entityId + " has invalid ability ID in blacklist: " + id);
            }
        }

        // Validate budget overrides
        for (Map.Entry<String, Float> entry : getBudgetOverrides().entrySet()) {
            if (entry.getValue() < 0) {
                throw new IllegalStateException("Entity preset for " + entityId + " has negative budget override for " + entry.getKey());
            }
        }

        // Validate spawn chance override
        if (spawnChanceOverride != null && (spawnChanceOverride < 0 || spawnChanceOverride > 1)) {
            throw new IllegalStateException("Entity preset for " + entityId + " has spawnChanceOverride out of range [0,1]: " + spawnChanceOverride);
        }

        // Validate forced abilities
        for (Map.Entry<String, Integer> entry : getForcedAbilities().entrySet()) {
            if (entry.getValue() < 1 || entry.getValue() > 5) {
                throw new IllegalStateException("Entity preset for " + entityId + " has forced ability level out of range [1,5] for " + entry.getKey() + ": " + entry.getValue());
            }
            ResourceLocation rl = ResourceLocation.tryParse(entry.getKey());
            if (rl == null) {
                throw new IllegalStateException("Entity preset for " + entityId + " has invalid ability ID in forced abilities: " + entry.getKey());
            }
        }

        // Validate the round-2 override fields so a malformed datapack fails loudly at
        // load rather than silently producing negative-HP or 0-damage elites at runtime.
        if (healthMultiplier != null && healthMultiplier < 0) {
            throw new IllegalStateException("Entity preset for " + entityId + " has negative healthMultiplier: " + healthMultiplier);
        }
        if (damageMultiplier != null && damageMultiplier < 0) {
            throw new IllegalStateException("Entity preset for " + entityId + " has negative damageMultiplier: " + damageMultiplier);
        }
        if (forcedLevel != null && (forcedLevel < 1 || forcedLevel > 9999)) {
            throw new IllegalStateException("Entity preset for " + entityId + " has forcedLevel out of range [1,9999]: " + forcedLevel);
        }
        if (forcedQuality != null) {
            try {
                QualityTier.valueOf(forcedQuality.toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new IllegalStateException("Entity preset for " + entityId + " has invalid forcedQuality: " + forcedQuality);
            }
        }
        if (qualityWeights != null) {
            for (String tierName : qualityWeights.keySet()) {
                try {
                    QualityTier.valueOf(tierName.toUpperCase(java.util.Locale.ROOT));
                } catch (IllegalArgumentException e) {
                    throw new IllegalStateException("Entity preset for " + entityId + " has invalid qualityWeights key: " + tierName);
                }
            }
        }
    }

    /**
     * Returns true if this preset is valid (does not throw).
     */
    public boolean isValid() {
        try {
            validate();
            return true;
        } catch (IllegalStateException e) {
            return false;
        }
    }

    @Override
    public String toString() {
        return "EntityPreset{" +
                "entityId=" + getEntityId() +
                ", baseLevel=" + baseLevel +
                ", maxLevel=" + maxLevel +
                ", abilityWeights=" + getAbilityWeights().size() + " entries" +
                ", abilityBlacklist=" + getAbilityBlacklist().size() + " entries" +
                ", budgetOverrides=" + getBudgetOverrides().size() + " entries" +
                ", spawnChanceOverride=" + spawnChanceOverride +
                ", forcedAbilities=" + getForcedAbilities().size() + " entries" +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EntityPreset that = (EntityPreset) o;
        return Objects.equals(getEntityId(), that.getEntityId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getEntityId());
    }

    /**
     * Creates a default preset with sensible defaults.
     */
    public static EntityPreset createDefault(ResourceLocation entityId) {
        EntityPreset preset = new EntityPreset(entityId);
        preset.baseLevel = 1;
        preset.maxLevel = 5;
        preset.abilityWeights = new HashMap<>();
        preset.abilityBlacklist = new HashSet<>();
        preset.budgetOverrides = new HashMap<>();
        preset.spawnChanceOverride = null;
        preset.forcedAbilities = new HashMap<>();
        return preset;
    }
}
