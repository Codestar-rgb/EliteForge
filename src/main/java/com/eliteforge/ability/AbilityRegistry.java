package com.eliteforge.ability;

import com.eliteforge.ability.attack.*;
import com.eliteforge.ability.control.*;
import com.eliteforge.ability.creator.*;
import com.eliteforge.ability.defense.*;
import com.eliteforge.ability.legendary.*;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Registry for all EliteForge abilities.
 * Uses a simple Map-based approach since Forge doesn't have a built-in ability registry.
 * 
 * All 54 abilities are registered statically during mod initialization:
 * - 12 Attack abilities
 * - 12 Defense abilities
 * - 12 Control abilities
 * - 10 Legendary abilities
 * - 8 Creator abilities
 */
public final class AbilityRegistry {

    private AbilityRegistry() {
        // Utility class - no instantiation
    }

    private static final Map<String, Ability> ABILITIES = new LinkedHashMap<>();
    private static final Map<AbilityCategory, List<Ability>> CATEGORY_CACHE = new EnumMap<>(AbilityCategory.class);
    /** Q3: Cached unmodifiable list of all non-creator abilities.
     *  Avoids repeated stream filtering for hot paths like AbilityGenerator,
     *  EliteEcosystem.nurtureNearbyElites, and DynamicStrengthening.grantTemporaryAbility. */
    private static volatile List<Ability> NON_CREATOR_CACHE = Collections.emptyList();
    private static boolean initialized = false;

    // ========== Registration ==========

    /**
     * Registers an ability. Throws if an ability with the same ID is already registered.
     *
     * @param ability The ability to register
     * @throws IllegalArgumentException if an ability with the same ID already exists
     */
    public static void registerAbility(Ability ability) {
        if (ability == null) {
            throw new IllegalArgumentException("Cannot register null ability");
        }
        String key = ability.getIdString();
        if (ABILITIES.containsKey(key)) {
            throw new IllegalArgumentException("Ability already registered: " + key);
        }
        ABILITIES.put(key, ability);
    }

    // ========== Lookup ==========

    /**
     * Gets an ability by its string ID (e.g., "eliteforge:fire").
     *
     * @param id The ability ID string
     * @return The ability, or null if not found
     */
    @Nullable
    public static Ability getAbility(String id) {
        return ABILITIES.get(id);
    }

    /**
     * Gets an ability by its ResourceLocation ID.
     *
     * @param id The ability ResourceLocation
     * @return The ability, or null if not found
     */
    @Nullable
    public static Ability getAbility(ResourceLocation id) {
        return ABILITIES.get(id.toString());
    }

    /**
     * Gets all abilities in a given category.
     *
     * @param category The category to filter by
     * @return Unmodifiable collection of abilities in that category
     */
    public static Collection<Ability> getAbilitiesByCategory(AbilityCategory category) {
        return CATEGORY_CACHE.getOrDefault(category, Collections.emptyList());
    }

    /**
     * Gets all registered abilities.
     *
     * @return Unmodifiable collection of all abilities
     */
    public static Collection<Ability> getAllAbilities() {
        return Collections.unmodifiableCollection(ABILITIES.values());
    }

    /**
     * Gets all registered non-creator abilities (ATTACK, DEFENSE, CONTROL, LEGENDARY).
     * <p>
     * Q3: This is a cached, O(1) accessor intended for hot paths that previously
     * called {@code getAllAbilities().stream().filter(a -> a.getCategory() != CREATOR)}
     * on every invocation (e.g. {@link com.eliteforge.spawn.AbilityGenerator#generateAbilities},
     * {@link com.eliteforge.spawn.EliteEcosystem#nurtureNearbyElites},
     * {@link com.eliteforge.spawn.DynamicStrengthening#grantTemporaryAbility}).
     * The list is built once during {@link #init()} and is safe to iterate
     * concurrently.
     *
     * @return Unmodifiable list of all non-creator abilities
     */
    public static List<Ability> getNonCreatorAbilities() {
        return NON_CREATOR_CACHE;
    }

    /**
     * Gets all registered ability IDs as ResourceLocations.
     * Used by command system for tab completion.
     *
     * @return Unmodifiable collection of ability ResourceLocation IDs
     */
    public static Collection<ResourceLocation> getAbilityIds() {
        return ABILITIES.values().stream()
                .map(Ability::getId)
                .collect(Collectors.toUnmodifiableList());
    }

    /**
     * Gets the total number of registered abilities.
     */
    public static int getAbilityCount() {
        return ABILITIES.size();
    }

    /**
     * Checks if an ability with the given ID is registered.
     */
    public static boolean isRegistered(String id) {
        return ABILITIES.containsKey(id);
    }

    // ========== Initialization ==========

    /**
     * Initializes the registry by registering all 54 abilities.
     * Should be called during mod construction.
     */
    public static synchronized void init() {
        if (initialized) return;
        initialized = true;

        // ===== Attack Abilities (12) =====
        registerAbility(new AbilityFire());
        registerAbility(new AbilityCorrosion());
        registerAbility(new AbilitySpiritBurn());
        registerAbility(new AbilityLightning());
        registerAbility(new AbilityDeathTouch());
        registerAbility(new AbilityExplosion());
        registerAbility(new AbilityArrowRain());
        registerAbility(new AbilityBloodthirst());
        registerAbility(new AbilitySweep());
        registerAbility(new AbilityPoison());
        registerAbility(new AbilityWither());
        registerAbility(new AbilityRage());

        // ===== Defense Abilities (12) =====
        registerAbility(new AbilityIronWall());
        registerAbility(new AbilityRegen());
        registerAbility(new AbilityImmunity());
        registerAbility(new AbilityThorns());
        registerAbility(new AbilityShield());
        registerAbility(new AbilityEvade());
        registerAbility(new AbilityArmor());
        registerAbility(new AbilityAbsorption());
        registerAbility(new AbilityReflect());
        registerAbility(new AbilityPhase());
        registerAbility(new AbilityLeech());
        registerAbility(new AbilityBulwark());

        // ===== Control Abilities (12) =====
        registerAbility(new AbilityWeb());
        registerAbility(new AbilityGravity());
        registerAbility(new AbilitySlow());
        registerAbility(new AbilityBlind());
        registerAbility(new AbilityFear());
        registerAbility(new AbilitySiphon());
        registerAbility(new AbilityKnockback());
        registerAbility(new AbilityFreeze());
        registerAbility(new AbilityCurse());
        registerAbility(new AbilityImmobilize());
        registerAbility(new AbilityVoid());
        registerAbility(new AbilityConfusion());

        // ===== Legendary Abilities (10) =====
        registerAbility(new AbilityClone());
        registerAbility(new AbilityPhaseShift());
        registerAbility(new AbilityStorm());
        registerAbility(new AbilityNecromancy());
        registerAbility(new AbilityBerserk());
        registerAbility(new AbilityTimeWarp());
        registerAbility(new AbilityMutation());
        registerAbility(new AbilityChaos());
        registerAbility(new AbilityDoom());
        registerAbility(new AbilitySupreme());

        // ===== Creator Abilities (8) =====
        registerAbility(new AbilityNexus());
        registerAbility(new AbilityDominion());
        registerAbility(new AbilityEvolution());
        registerAbility(new AbilityAssimilate());
        registerAbility(new AbilityBestowal());
        registerAbility(new AbilityAnnihilate());
        registerAbility(new AbilityReincarnation());
        registerAbility(new AbilityCommander());

        // Populate category cache after all registrations
        for (AbilityCategory category : AbilityCategory.values()) {
            CATEGORY_CACHE.put(category, ABILITIES.values().stream()
                    .filter(a -> a.getCategory() == category)
                    .collect(Collectors.toUnmodifiableList()));
        }
        // Q3: Build the non-creator cache once for O(1) hot-path access
        NON_CREATOR_CACHE = ABILITIES.values().stream()
                .filter(a -> a.getCategory() != AbilityCategory.CREATOR)
                .collect(Collectors.toUnmodifiableList());
    }
}
