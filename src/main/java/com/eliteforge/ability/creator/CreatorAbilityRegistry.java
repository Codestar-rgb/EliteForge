package com.eliteforge.ability.creator;

import com.eliteforge.ability.Ability;
import com.eliteforge.ability.AbilityCategory;
import com.eliteforge.ability.AbilityRegistry;
import net.minecraft.resources.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * CreatorAbilityRegistry — forward-compatible extension point for creator-tier abilities.
 * <p>
 * <b>Background:</b> Currently, the 8 creator abilities are hardcoded in
 * {@link AbilityRegistry#init()}. This class provides a structured extension
 * layer that allows future data packs, KubeJS scripts, or add-on mods to
 * register <em>additional</em> creator abilities at runtime — without modifying
 * the core {@link AbilityRegistry} source.
 * <p>
 * <b>Design philosophy:</b>
 * <ul>
 *   <li><b>Non-destructive:</b> Does not modify {@link AbilityRegistry#init()}.
 *       The 8 built-in creator abilities continue to be registered exactly as
 *       before. This class is an <em>additive</em> layer.</li>
 *   <li><b>Delegates to AbilityRegistry:</b> All registrations made through
 *       this class are forwarded to {@link AbilityRegistry#registerAbility(Ability)},
 *       so they appear in all existing lookups ({@code getAbilitiesByCategory(CREATOR)},
 *       {@code getAbility(id)}, etc.). No changes to consuming code are needed.</li>
 *   <li><b>Tracks extensions:</b> Maintains a separate list of <em>extension</em>
 *       creator abilities (those registered via this class, not the built-in 8).
 *       This allows data packs to enumerate and manage their own additions.</li>
 * </ul>
 * <p>
 * <b>Usage example (KubeJS / data pack loader):</b>
 * <pre>{@code
 * // After AbilityRegistry.init() has run (e.g. in a KubeJS startup event):
 * CreatorAbility customCreator = new CreatorAbility(
 *     new ResourceLocation("mymod", "custom_creator"),
 *     6.0f  // display-only budget cost
 * ) {
 *     @Override
 *     public void onApply(LivingEntity entity, int level) {
 *         // ... custom behavior ...
 *     }
 *     // ... override other lifecycle methods as needed ...
 * };
 * CreatorAbilityRegistry.registerExtension(customCreator);
 * }</pre>
 * <p>
 * <b>Future migration path:</b> When the project moves to a fully
 * registry-driven creator system (see HANDOVER.md §18 "medium priority"),
 * the built-in 8 abilities will be migrated to data pack JSON definitions
 * that call {@link #registerExtension(Ability)} during reload. At that point,
 * {@link AbilityRegistry#init()} will no longer hardcode creator abilities,
 * and this class will become the single source of truth.
 * <p>
 * <b>Thread safety:</b> Registration methods are synchronized. Lookup methods
 * return unmodifiable views and are safe for concurrent reads.
 *
 * @see AbilityRegistry
 * @see CreatorAbility
 */
public final class CreatorAbilityRegistry {

    private static final Logger LOGGER = LogManager.getLogger();

    private CreatorAbilityRegistry() {
        // Utility class — no instantiation
    }

    /**
     * Extension creator abilities registered at runtime (excluding the 8 built-in ones).
     * Uses a LinkedHashMap to preserve registration order for deterministic iteration.
     */
    private static final Map<String, Ability> EXTENSIONS = new LinkedHashMap<>();

    private static volatile boolean extensionsSealed = false;

    /**
     * Register an extension creator ability.
     * <p>
     * The ability is:
     * <ol>
     *   <li>Validated (must be non-null, CREATOR category, not already registered)</li>
     *   <li>Forwarded to {@link AbilityRegistry#registerAbility(Ability)} so it
     *       appears in all standard lookups</li>
     *   <li>Tracked in this registry's extension list</li>
     * </ol>
     *
     * @param ability the extension creator ability to register
     * @throws IllegalArgumentException if the ability is null, not CREATOR category,
     *         or already registered with the same ID
     * @throws IllegalStateException if {@link #sealExtensions()} has been called
     */
    public static synchronized void registerExtension(Ability ability) {
        if (extensionsSealed) {
            throw new IllegalStateException(
                    "Cannot register extension creator ability after extensions are sealed: "
                            + (ability != null ? ability.getIdString() : "null"));
        }
        if (ability == null) {
            throw new IllegalArgumentException("Cannot register null extension ability");
        }
        if (ability.getCategory() != AbilityCategory.CREATOR) {
            throw new IllegalArgumentException(
                    "Extension ability must be CREATOR category, got " + ability.getCategory()
                            + " for " + ability.getIdString());
        }
        String id = ability.getIdString();
        if (EXTENSIONS.containsKey(id)) {
            throw new IllegalArgumentException("Extension creator ability already registered: " + id);
        }
        if (AbilityRegistry.isRegistered(id)) {
            throw new IllegalArgumentException(
                    "Ability ID already in use by a built-in ability: " + id);
        }

        // Delegate to the main registry
        AbilityRegistry.registerAbility(ability);
        EXTENSIONS.put(id, ability);

        LOGGER.info("Registered extension creator ability: {} (total extensions: {})",
                id, EXTENSIONS.size());
    }

    /**
     * Seal the extension registry, preventing further registrations.
     * <p>
     * Called after all data pack / KubeJS registrations are complete (typically
     * during {@code AddReloadListenerEvent} finalization). This ensures a stable
     * snapshot for the duration of a server session.
     * <p>
     * Sealing is one-way: once sealed, {@link #registerExtension(Ability)} will
     * throw. Call {@link #unsealExtensions()} to allow re-registration during a
     * data pack reload.
     */
    public static synchronized void sealExtensions() {
        extensionsSealed = true;
        LOGGER.debug("Creator ability extensions sealed ({} extensions registered)",
                EXTENSIONS.size());
    }

    /**
     * Unseal the extension registry, allowing re-registration.
     * <p>
     * Called during data pack reload to allow extensions to be re-registered
     * after a {@code /reload}. Clears all existing extensions first, since
     * data pack reload re-registers everything from scratch.
     */
    public static synchronized void unsealExtensions() {
        EXTENSIONS.clear();
        extensionsSealed = false;
        LOGGER.debug("Creator ability extensions unsealed and cleared for reload");
    }

    /**
     * Get all extension creator abilities (excluding the 8 built-in ones).
     *
     * @return unmodifiable collection of extension creator abilities
     */
    public static List<Ability> getExtensions() {
        return Collections.unmodifiableList(new ArrayList<>(EXTENSIONS.values()));
    }

    /**
     * Get the total number of creator abilities (built-in + extensions).
     *
     * @return total creator ability count (always ≥ 8)
     */
    public static int getTotalCreatorCount() {
        return 8 + EXTENSIONS.size();
    }

    /**
     * Check if an ability ID is an extension creator (not one of the built-in 8).
     *
     * @param id the ability ID string
     * @return true if the ID is a registered extension creator ability
     */
    public static boolean isExtension(String id) {
        return EXTENSIONS.containsKey(id);
    }
}
