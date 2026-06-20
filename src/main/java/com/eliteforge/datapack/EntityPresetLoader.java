package com.eliteforge.datapack;

import com.eliteforge.EliteForge;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.entity.EntityType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads entity presets from datapacks.
 * Path: data/&lt;namespace&gt;/eliteforge/entity_presets/&lt;entity_name&gt;.json
 *
 * Registered as a ReloadListener via AddReloadListenerEvent so that
 * presets are reloaded when /reload is executed.
 */
public class EntityPresetLoader implements ResourceManagerReloadListener {

    private static final Logger LOGGER = LogManager.getLogger();
    private static final String PRESET_PATH = "eliteforge/entity_presets";
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    // Singleton instance
    private static final EntityPresetLoader INSTANCE = new EntityPresetLoader();

    // Cached presets: entity type ResourceLocation -> EntityPreset
    private final Map<ResourceLocation, EntityPreset> presetCache = new ConcurrentHashMap<>();

    // Default preset for entity types without a configured preset
    private volatile EntityPreset defaultPreset;

    private EntityPresetLoader() {
        // Initialize default preset
        defaultPreset = EntityPreset.createDefault(new ResourceLocation("minecraft", "generic"));
    }

    public static EntityPresetLoader getInstance() {
        return INSTANCE;
    }

    @Override
    public void onResourceManagerReload(ResourceManager resourceManager) {
        LOGGER.info("EliteForge: Reloading entity presets from datapacks...");

        // Clear old cache
        presetCache.clear();

        // Scan all namespaces for entity presets
        Map<ResourceLocation, Resource> resources = resourceManager.listResources(
                PRESET_PATH,
                location -> location.getPath().endsWith(".json")
        );

        int loaded = 0;
        int failed = 0;

        for (Map.Entry<ResourceLocation, Resource> entry : resources.entrySet()) {
            ResourceLocation location = entry.getKey();
            Resource resource = entry.getValue();

            try {
                EntityPreset preset = loadPresetFromResource(location, resource);
                if (preset != null && preset.isValid()) {
                    ResourceLocation entityId = preset.getEntityId();
                    if (entityId != null) {
                        presetCache.put(entityId, preset);
                        loaded++;
                        LOGGER.debug("EliteForge: Loaded entity preset for {} from {}", entityId, location);
                    }
                } else {
                    failed++;
                    LOGGER.warn("EliteForge: Invalid entity preset at {}", location);
                }
            } catch (Exception e) {
                failed++;
                LOGGER.error("EliteForge: Failed to load entity preset at {}: {}", location, e.getMessage());
            }
        }

        LOGGER.info("EliteForge: Loaded {} entity presets ({} failed)", loaded, failed);
    }

    /**
     * Loads an entity preset from a resource.
     */
    private EntityPreset loadPresetFromResource(ResourceLocation location, Resource resource) throws IOException {
        try (InputStream stream = resource.open();
             BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {

            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }

            String jsonStr = builder.toString();

            // Parse JSON
            JsonObject json = JsonParser.parseString(jsonStr).getAsJsonObject();
            EntityPreset preset = EntityPreset.deserialize(json);

            // If entity ID is not specified in the JSON, derive it from the file path
            if (preset.getEntityId() == null) {
                ResourceLocation derivedId = deriveEntityIdFromPath(location);
                if (derivedId != null) {
                    preset = new EntityPreset(derivedId);
                    EntityPreset reparsed = EntityPreset.deserialize(json);
                    reparsed.validate();
                    preset = reparsed;
                }
            }

            preset.validate();
            return preset;

        } catch (JsonParseException e) {
            LOGGER.error("EliteForge: JSON parse error for preset at {}: {}", location, e.getMessage());
            throw new IOException("JSON parse error", e);
        } catch (IllegalStateException e) {
            LOGGER.error("EliteForge: Validation error for preset at {}: {}", location, e.getMessage());
            throw new IOException("Validation error", e);
        }
    }

    /**
     * Derives an entity ID from the resource path.
     * E.g., data/minecraft/eliteforge/entity_presets/zombie.json -> minecraft:zombie
     */
    private ResourceLocation deriveEntityIdFromPath(ResourceLocation location) {
        String path = location.getPath();
        // Remove prefix and suffix
        if (path.startsWith(PRESET_PATH + "/")) {
            path = path.substring((PRESET_PATH + "/").length());
        }
        if (path.endsWith(".json")) {
            path = path.substring(0, path.length() - ".json".length());
        }

        // The namespace from the resource location is the datapack namespace
        String namespace = location.getNamespace();

        // Try to construct a valid ResourceLocation
        return new ResourceLocation(namespace, path);
    }

    // ============ Public API ============

    /**
     * Gets the preset for a specific entity type.
     * Returns the default preset if no specific preset is configured.
     */
    public EntityPreset getPreset(EntityType<?> entityType) {
        if (entityType == null) {
            return getDefaultPreset();
        }

        ResourceLocation entityId = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entityType);
        return getPreset(entityId);
    }

    /**
     * Gets the preset for a specific entity type by ResourceLocation.
     * Returns the default preset if no specific preset is configured.
     */
    public EntityPreset getPreset(ResourceLocation entityId) {
        if (entityId == null) {
            return getDefaultPreset();
        }

        EntityPreset preset = presetCache.get(entityId);
        if (preset != null) {
            return preset;
        }

        // Try to find a preset with just the path part (cross-namespace fallback)
        for (Map.Entry<ResourceLocation, EntityPreset> entry : presetCache.entrySet()) {
            if (entry.getKey().getPath().equals(entityId.getPath())) {
                return entry.getValue();
            }
        }

        return getDefaultPreset();
    }

    /**
     * Gets the default preset used when no entity-specific preset is configured.
     */
    public EntityPreset getDefaultPreset() {
        return defaultPreset;
    }

    /**
     * Sets the default preset.
     */
    public void setDefaultPreset(EntityPreset preset) {
        if (preset != null) {
            this.defaultPreset = preset;
        }
    }

    /**
     * Checks if a preset exists for the given entity type.
     */
    public boolean hasPreset(EntityType<?> entityType) {
        if (entityType == null) return false;
        ResourceLocation entityId = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entityType);
        return presetCache.containsKey(entityId);
    }

    /**
     * Checks if a preset exists for the given entity ID.
     */
    public boolean hasPreset(ResourceLocation entityId) {
        return entityId != null && presetCache.containsKey(entityId);
    }

    /**
     * Gets all loaded presets.
     */
    public Collection<EntityPreset> getAllPresets() {
        return Collections.unmodifiableCollection(presetCache.values());
    }

    /**
     * Gets all loaded preset entity IDs.
     */
    public Set<ResourceLocation> getAllPresetIds() {
        return Collections.unmodifiableSet(presetCache.keySet());
    }

    /**
     * Gets the number of loaded presets.
     */
    public int getPresetCount() {
        return presetCache.size();
    }

    /**
     * Adds or replaces a preset programmatically (e.g., from API).
     */
    public void putPreset(EntityPreset preset) {
        if (preset != null && preset.isValid() && preset.getEntityId() != null) {
            presetCache.put(preset.getEntityId(), preset);
            LOGGER.debug("EliteForge: Added/updated entity preset for {}", preset.getEntityId());
        }
    }

    /**
     * Removes a preset by entity ID.
     */
    public void removePreset(ResourceLocation entityId) {
        if (entityId != null) {
            presetCache.remove(entityId);
        }
    }
}
