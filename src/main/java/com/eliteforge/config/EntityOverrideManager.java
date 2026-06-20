package com.eliteforge.config;

import com.eliteforge.quality.QualityTier;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * EntityOverrideManager — parses the {@code entityOverrides} config list into a
 * fast lookup table of per-entity-type overrides.
 * <p>
 * Format (one entry per string in the config list):
 * <pre>
 *   "entity_id|disabled|forcedQuality|forcedLevel|healthMult|damageMult"
 * </pre>
 * Any field may be {@code *} to mean "use the default". Example:
 * <pre>
 *   "minecraft:creeper|false|EPIC|*|1.5|2.0"
 *   "minecraft:enderman|true|*|*|*|*"
 * </pre>
 * <p>
 * The table is rebuilt lazily on first access and whenever the config value's
 * string representation changes (cheap — operators don't edit it every tick).
 * Entries are applied in order, so later entries for the same entity_id win.
 */
public final class EntityOverrideManager {

    private static final Logger LOGGER = LogManager.getLogger();

    /** Parsed override record. {@code null} fields mean "use default". */
    public static final class Override {
        public final boolean disabled;
        public final QualityTier forcedQuality;   // null = default
        public final Integer forcedLevel;          // null = default
        public final Float healthMultiplier;       // null = default (1.0)
        public final Float damageMultiplier;       // null = default (1.0)

        Override(boolean disabled, QualityTier forcedQuality, Integer forcedLevel,
                 Float healthMultiplier, Float damageMultiplier) {
            this.disabled = disabled;
            this.forcedQuality = forcedQuality;
            this.forcedLevel = forcedLevel;
            this.healthMultiplier = healthMultiplier;
            this.damageMultiplier = damageMultiplier;
        }

        static final Override DEFAULT = new Override(false, null, null, null, null);
    }

    private static volatile Map<ResourceLocation, Override> overrideMap = null;
    private static volatile String lastRawValue = null;

    private EntityOverrideManager() {
    }

    /** Rebuild the map if the config value changed since the last parse. */
    private static void ensureFresh() {
        Object raw = EliteForgeConfig.SERVER.entityOverrides.get();
        String rawStr = raw == null ? "" : raw.toString();
        if (rawStr.equals(lastRawValue) && overrideMap != null) return;
        synchronized (EntityOverrideManager.class) {
            // Double-check under lock.
            rawStr = raw == null ? "" : raw.toString();
            if (rawStr.equals(lastRawValue) && overrideMap != null) return;
            Map<ResourceLocation, Override> map = new HashMap<>();
            if (raw instanceof List<?> list) {
                for (Object o : list) {
                    if (!(o instanceof String s) || s.isBlank()) continue;
                    Override parsed = parse(s);
                    if (parsed == null) continue;
                    ResourceLocation id = parseEntityId(s);
                    if (id == null) continue;
                    map.put(id, parsed);
                }
            }
            overrideMap = map;
            lastRawValue = rawStr;
            LOGGER.debug("EntityOverrideManager rebuilt: {} overrides", map.size());
        }
    }

    private static ResourceLocation parseEntityId(String entry) {
        int pipe = entry.indexOf('|');
        String idStr = pipe < 0 ? entry.trim() : entry.substring(0, pipe).trim();
        return ResourceLocation.tryParse(idStr);
    }

    @SuppressWarnings("unchecked")
    private static Override parse(String entry) {
        String[] parts = entry.split("\\|");
        if (parts.length < 2) {
            LOGGER.warn("entityOverrides entry has too few fields (need ≥2): {}", entry);
            return null;
        }
        try {
            boolean disabled = Boolean.parseBoolean(parts[1].trim());
            QualityTier quality = parseQuality(parts.length > 2 ? parts[2].trim() : "*");
            Integer level = parseLevel(parts.length > 3 ? parts[3].trim() : "*");
            Float health = parseFloat(parts.length > 4 ? parts[4].trim() : "*");
            Float damage = parseFloat(parts.length > 5 ? parts[5].trim() : "*");
            return new Override(disabled, quality, level, health, damage);
        } catch (Exception e) {
            LOGGER.warn("entityOverrides entry failed to parse '{}': {}", entry, e.getMessage());
            return null;
        }
    }

    private static QualityTier parseQuality(String s) {
        if (s == null || s.equals("*")) return null;
        try {
            return QualityTier.valueOf(s.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            LOGGER.warn("entityOverrides: unknown quality tier '{}', ignoring", s);
            return null;
        }
    }

    private static Integer parseLevel(String s) {
        if (s == null || s.equals("*")) return null;
        try {
            int v = Integer.parseInt(s);
            return v > 0 ? v : null;
        } catch (NumberFormatException e) {
            LOGGER.warn("entityOverrides: bad forcedLevel '{}', ignoring", s);
            return null;
        }
    }

    private static Float parseFloat(String s) {
        if (s == null || s.equals("*")) return null;
        try {
            return Float.parseFloat(s);
        } catch (NumberFormatException e) {
            LOGGER.warn("entityOverrides: bad multiplier '{}', ignoring", s);
            return null;
        }
    }

    /** Get the override for an entity type, or {@link Override#DEFAULT} if none. */
    public static Override get(EntityType<?> type) {
        ensureFresh();
        ResourceLocation id = net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(type);
        if (id == null) return Override.DEFAULT;
        Override o = overrideMap.get(id);
        return o == null ? Override.DEFAULT : o;
    }

    /** Convenience: is this entity type disabled from ever becoming elite? */
    public static boolean isDisabled(EntityType<?> type) {
        return get(type).disabled;
    }
}
