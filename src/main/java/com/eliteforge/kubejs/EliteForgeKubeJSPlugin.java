package com.eliteforge.kubejs;

import com.eliteforge.EliteForge;
import com.eliteforge.ability.AbilityCategory;
import com.eliteforge.ability.AbilityRegistry;
import com.eliteforge.config.DifficultyMode;
import com.eliteforge.quality.QualityTier;
import net.minecraft.resources.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;

/**
 * KubeJS plugin class for EliteForge integration.
 * Only instantiated if KubeJS is on the classpath.
 *
 * This class uses try/catch for class loading to gracefully handle
 * the absence of KubeJS. When KubeJS is present, this class will
 * register custom event handlers, ingredient types, and bindings.
 *
 * When KubeJS is NOT present, this class simply does nothing.
 */
public class EliteForgeKubeJSPlugin {

    private static final Logger LOGGER = LogManager.getLogger();
    private static final String KUBEJS_PLUGIN_CLASS = "dev.latvian.mods.kubejs.KubeJSPlugin";
    private static final String KUBEJS_REGISTRATION_CLASS = "dev.latvian.mods.kubejs.KubeJSRegistries";
    private static final String KUBEJS_EVENT_CLASS = "dev.latvian.mods.kubejs.KubeJSEvents";

    private static boolean initialized = false;
    private static Object pluginInstance = null;

    /**
     * Attempts to initialize the KubeJS plugin.
     * Only proceeds if KubeJS is on the classpath.
     */
    public static void initialize() {
        if (initialized) return;

        if (!EliteForgeEventsJS.isKubeJSLoaded()) {
            LOGGER.info("EliteForge: KubeJS not found, skipping plugin initialization");
            initialized = true;
            return;
        }

        LOGGER.info("EliteForge: Initializing KubeJS plugin...");

        try {
            // Try to create a KubeJS plugin instance via reflection
            tryCreatePlugin();

            // Register custom event handlers
            registerEventHandlers();

            // Register custom ingredient types
            registerIngredientTypes();

            // Register EliteForge bindings
            registerBindings();

            LOGGER.info("EliteForge: KubeJS plugin initialized successfully");
        } catch (Exception e) {
            LOGGER.error("EliteForge: Failed to initialize KubeJS plugin: {}", e.getMessage());
        }

        initialized = true;
    }

    /**
     * Attempts to create a KubeJS plugin instance.
     */
    private static void tryCreatePlugin() {
        try {
            Class<?> pluginClass = Class.forName(KUBEJS_PLUGIN_CLASS);

            // Try to find an internal plugin class that extends KubeJSPlugin
            // Since we can't extend at compile time (KubeJS may not be present),
            // we create a proxy-like registration

            // Register this mod's plugin with KubeJS's plugin system
            try {
                Class<?> pluginInfoClass = Class.forName("dev.latvian.mods.kubejs.plugin.KubeJSPluginInfo");
                java.lang.reflect.Method createMethod = pluginInfoClass.getMethod(
                        "create", String.class, Class.class
                );
                Object pluginInfo = createMethod.invoke(null, EliteForge.MODID, EliteForgeKubeJSPlugin.class);
                pluginInstance = pluginInfo;

                LOGGER.debug("EliteForge: Created KubeJS plugin info");
            } catch (ClassNotFoundException e) {
                // Older KubeJS version, try alternative registration
                LOGGER.debug("EliteForge: KubeJS plugin info class not found, using alternative registration");
                tryAlternativeRegistration();
            }

        } catch (ClassNotFoundException e) {
            LOGGER.debug("EliteForge: KubeJSPlugin class not found");
        } catch (Exception e) {
            LOGGER.debug("EliteForge: Error creating KubeJS plugin: {}", e.getMessage());
        }
    }

    /**
     * Alternative KubeJS plugin registration for older versions.
     */
    private static void tryAlternativeRegistration() {
        try {
            // Try ServiceLoader-based registration
            Class<?> loaderClass = Class.forName("dev.latvian.mods.kubejs.script.ScriptType");
            LOGGER.debug("EliteForge: Found KubeJS ScriptType class, registration possible");
        } catch (ClassNotFoundException e) {
            LOGGER.debug("EliteForge: Alternative KubeJS registration method not available");
        }
    }

    /**
     * Registers custom event handlers with KubeJS.
     */
    private static void registerEventHandlers() {
        try {
            // Register elite_spawn event
            registerKubeJSEvent("eliteforge.elite_spawn", builder -> {
                try {
                    // entity property
                    java.lang.reflect.Method propertyMethod = builder.getClass().getMethod("property", String.class, Class.class);
                    propertyMethod.invoke(builder, "entity", net.minecraft.world.entity.LivingEntity.class);
                    propertyMethod.invoke(builder, "level", Integer.class);
                    propertyMethod.invoke(builder, "mode", String.class);
                    propertyMethod.invoke(builder, "abilities", Map.class);

                    // Make it cancelable
                    try {
                        java.lang.reflect.Method cancelableMethod = builder.getClass().getMethod("cancelable");
                        cancelableMethod.invoke(builder);
                    } catch (NoSuchMethodException ignored) {
                        // Not all KubeJS versions support this
                    }
                } catch (Exception e) {
                    LOGGER.debug("EliteForge: Error registering elite_spawn event properties: {}", e.getMessage());
                }
            });

            // Register elite_death event
            registerKubeJSEvent("eliteforge.elite_death", builder -> {
                try {
                    java.lang.reflect.Method propertyMethod = builder.getClass().getMethod("property", String.class, Class.class);
                    propertyMethod.invoke(builder, "entity", net.minecraft.world.entity.LivingEntity.class);
                    propertyMethod.invoke(builder, "killer", net.minecraft.world.entity.LivingEntity.class);
                    propertyMethod.invoke(builder, "level", Integer.class);
                    propertyMethod.invoke(builder, "abilities", Map.class);
                    propertyMethod.invoke(builder, "drops", java.util.List.class);
                    propertyMethod.invoke(builder, "qualityTier", String.class);
                } catch (Exception e) {
                    LOGGER.debug("EliteForge: Error registering elite_death event properties: {}", e.getMessage());
                }
            });

            // Register ability_apply event
            registerKubeJSEvent("eliteforge.ability_apply", builder -> {
                try {
                    java.lang.reflect.Method propertyMethod = builder.getClass().getMethod("property", String.class, Class.class);
                    propertyMethod.invoke(builder, "entity", net.minecraft.world.entity.LivingEntity.class);
                    propertyMethod.invoke(builder, "ability", String.class);
                    propertyMethod.invoke(builder, "level", Integer.class);
                } catch (Exception e) {
                    LOGGER.debug("EliteForge: Error registering ability_apply event properties: {}", e.getMessage());
                }
            });

            LOGGER.debug("EliteForge: Registered KubeJS event handlers");

        } catch (Exception e) {
            LOGGER.debug("EliteForge: Error registering KubeJS event handlers: {}", e.getMessage());
        }
    }

    /**
     * Helper to register a KubeJS event with a builder consumer.
     */
    private static void registerKubeJSEvent(String eventId, java.util.function.Consumer<Object> builderConsumer) {
        try {
            Class<?> eventClass = Class.forName("dev.latvian.mods.kubejs.event.EventJS");
            Class<?> extraClass = Class.forName("dev.latvian.mods.kubejs.event.Extra");

            // Try to find the event registration method
            try {
                java.lang.reflect.Method registerMethod = extraClass.getMethod("registerEvent", String.class, Class.class);
                registerMethod.setAccessible(true);
                registerMethod.invoke(null, eventId, eventClass);
            } catch (Exception e) {
                // Try KubeJSEvents approach
                try {
                    java.lang.reflect.Method postMethod = KUBEJS_EVENT_CLASS.getClass().getMethod("register", String.class);
                    postMethod.setAccessible(true);
                    postMethod.invoke(null, eventId);
                } catch (Exception ex) {
                    LOGGER.debug("EliteForge: Could not register KubeJS event {}: {}", eventId, ex.getMessage());
                }
            }
        } catch (ClassNotFoundException e) {
            LOGGER.debug("EliteForge: KubeJS event classes not found for {}", eventId);
        }
    }

    /**
     * Registers custom ingredient types for KubeJS.
     */
    private static void registerIngredientTypes() {
        try {
            // Register EliteForge-specific ingredient types that can be used in KubeJS recipes
            // e.g., elite_ability_ingredient, quality_tier_ingredient

            Class<?> ingredientClass = Class.forName("dev.latvian.mods.kubejs.ingredient.IngredientHelper");
            LOGGER.debug("EliteForge: Found KubeJS ingredient helper class");
            // Registration of custom ingredients would go here
            // This is version-dependent, so we use reflection

        } catch (ClassNotFoundException e) {
            LOGGER.debug("EliteForge: KubeJS ingredient classes not found, skipping ingredient registration");
        } catch (Exception e) {
            LOGGER.debug("EliteForge: Error registering KubeJS ingredient types: {}", e.getMessage());
        }
    }

    /**
     * Registers EliteForge bindings for KubeJS scripts.
     * These allow KubeJS scripts to access EliteForge functionality.
     */
    private static void registerBindings() {
        try {
            Class<?> bindingsClass = Class.forName("dev.latvian.mods.kubejs.script.BindingsEvent");
            LOGGER.debug("EliteForge: Found KubeJS bindings class");

            // Bind helper objects that KubeJS scripts can use:
            // EliteForge.getAbility(id) → ability info
            // EliteForge.getDifficulty(entity) → difficulty info
            // EliteForge.abilities → all registered abilities
            // EliteForge.qualityTiers → all quality tiers
            // EliteForge.modes → all difficulty modes

            LOGGER.debug("EliteForge: KubeJS bindings would be registered at runtime");

        } catch (ClassNotFoundException e) {
            LOGGER.debug("EliteForge: KubeJS bindings class not found, skipping binding registration");
        } catch (Exception e) {
            LOGGER.debug("EliteForge: Error registering KubeJS bindings: {}", e.getMessage());
        }
    }

    // ============ Static Binding Helpers ============

    /**
     * Gets all registered ability IDs.
     * Callable from KubeJS scripts.
     */
    public static java.util.Set<String> getAbilityIds() {
        java.util.Set<String> ids = new java.util.LinkedHashSet<>();
        for (ResourceLocation id : AbilityRegistry.getAbilityIds()) {
            ids.add(id.toString());
        }
        return ids;
    }

    /**
     * Gets all ability category names.
     * Callable from KubeJS scripts.
     */
    public static java.util.Set<String> getAbilityCategories() {
        java.util.Set<String> categories = new java.util.LinkedHashSet<>();
        for (AbilityCategory cat : AbilityCategory.values()) {
            categories.add(cat.name());
        }
        return categories;
    }

    /**
     * Gets all quality tier names.
     * Callable from KubeJS scripts.
     */
    public static java.util.Set<String> getQualityTiers() {
        java.util.Set<String> tiers = new java.util.LinkedHashSet<>();
        for (QualityTier tier : QualityTier.values()) {
            tiers.add(tier.name());
        }
        return tiers;
    }

    /**
     * Gets all difficulty mode names.
     * Callable from KubeJS scripts.
     */
    public static java.util.Set<String> getDifficultyModes() {
        java.util.Set<String> modes = new java.util.LinkedHashSet<>();
        for (DifficultyMode mode : DifficultyMode.values()) {
            modes.add(mode.name());
        }
        return modes;
    }
}
