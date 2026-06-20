package com.eliteforge.ability;

import com.eliteforge.config.EliteForgeConfig;

import java.util.*;

/**
 * Defines mutual exclusion rules between abilities.
 * Mutually exclusive abilities cannot be assigned to the same elite entity.
 * 
 * 19 mutual exclusion pairs are defined based on thematic and balance considerations.
 * (Storm ↔ Lightning was removed to enable the "Thunder Lord" synergy.)
 * 
 * The {@code enableMutualExclusion} server config controls whether pair-based
 * exclusion rules are enforced. Creator-tier exclusivity (a single creator ability
 * cannot coexist with any other ability) is always enforced regardless of this config,
 * as it is a fundamental design rule.
 */
public final class MutualExclusion {

    private MutualExclusion() {
        // Utility class - no instantiation
    }

    /**
     * Represents a mutual exclusion pair between two abilities.
     */
    public record ExclusionPair(String abilityA, String abilityB) {
        public boolean contains(String abilityId) {
            return abilityA.equals(abilityId) || abilityB.equals(abilityId);
        }

        public String getOther(String abilityId) {
            if (abilityA.equals(abilityId)) return abilityB;
            if (abilityB.equals(abilityId)) return abilityA;
            return null;
        }
    }

    private static final List<ExclusionPair> EXCLUSION_PAIRS = new ArrayList<>();

    /** Normalized pair keys for O(1) lookup in isMutuallyExclusive(String, String).
     *  Each pair is stored in both orderings: "a|b" and "b|a". */
    private static final Set<String> EXCLUSION_KEYS = new HashSet<>();

    static {
        // 1. IronWall ↔ Void
        addPair("eliteforge:iron_wall", "eliteforge:void");
        // 2. Regen ↔ Immobilize
        addPair("eliteforge:regen", "eliteforge:immobilize");
        // 3. Immunity ↔ Chaos
        addPair("eliteforge:immunity", "eliteforge:chaos");
        // 4. Thorns ↔ Phase
        addPair("eliteforge:thorns", "eliteforge:phase");
        // 5. SpiritBurn ↔ Freeze
        addPair("eliteforge:spirit_burn", "eliteforge:freeze");
        // 6. Curse ↔ Siphon
        addPair("eliteforge:curse", "eliteforge:siphon");
        // 7. Rage ↔ IronWall
        addPair("eliteforge:rage", "eliteforge:iron_wall");
        // 8. Clone ↔ Mutation
        addPair("eliteforge:clone", "eliteforge:mutation");
        // 9. Storm ↔ Lightning — REMOVED: conflicts with "Thunder Lord" synergy
        // addPair("eliteforge:storm", "eliteforge:lightning");
        // 10. TimeWarp ↔ Immobilize
        addPair("eliteforge:time_warp", "eliteforge:immobilize");
        // 11. Berserk ↔ DeathTouch
        addPair("eliteforge:berserk", "eliteforge:death_touch");
        // 12. Shield ↔ Evade (both are defensive dodge/reduction mechanics - redundant together)
        addPair("eliteforge:shield", "eliteforge:evade");
        // 13. Absorption ↔ Regen (both are healing mechanics - redundant together)
        addPair("eliteforge:absorption", "eliteforge:regen");
        // 14. Web ↔ Knockback (web pulls in, knockback pushes out - counterproductive)
        addPair("eliteforge:web", "eliteforge:knockback");
        // 15. Curse ↔ Doom (both are debuff-on-death mechanics - redundant)
        addPair("eliteforge:curse", "eliteforge:doom");
        // 16. Reflect ↔ Phase (both are defensive evasion mechanics - redundant together)
        addPair("eliteforge:reflect", "eliteforge:phase");
        // 17. Shield ↔ Absorption (both add extra health - redundant together)
        addPair("eliteforge:shield", "eliteforge:absorption");
        // 18. Doom ↔ TimeWarp (doom is a countdown, time warp slows - conflicting tempo)
        addPair("eliteforge:doom", "eliteforge:time_warp");
        // 19. Explosion ↔ ArrowRain (both are AoE damage - redundant)
        addPair("eliteforge:explosion", "eliteforge:arrow_rain");
        // 20. Bloodthirst ↔ Siphon (both are sustain mechanics - redundant)
        addPair("eliteforge:bloodthirst", "eliteforge:siphon");
    }

    private static void addPair(String abilityA, String abilityB) {
        EXCLUSION_PAIRS.add(new ExclusionPair(abilityA, abilityB));
        // Add both orderings for O(1) lookup
        EXCLUSION_KEYS.add(abilityA + "|" + abilityB);
        EXCLUSION_KEYS.add(abilityB + "|" + abilityA);
    }

    /**
     * Checks whether pair-based mutual exclusion is enabled via server config.
     * Creator-tier exclusivity is always enforced regardless of this setting.
     *
     * @return true if pair-based mutual exclusion rules should be enforced;
     *         defaults to true if the config is not yet available (e.g., client side)
     */
    private static boolean isMutualExclusionEnabled() {
        try {
            return EliteForgeConfig.SERVER.enableMutualExclusion.get();
        } catch (Exception e) {
            // Config not available (e.g., client side or before server start).
            // Default to enabled for safety — servers will always have the config.
            return true;
        }
    }

    /**
     * Checks if two abilities are mutually exclusive.
     * Creator-tier exclusivity is always enforced (fundamental design rule).
     * Pair-based exclusions are only enforced when the server config enables them.
     *
     * @param a First ability
     * @param b Second ability
     * @return true if the abilities cannot coexist
     */
    public static boolean isMutuallyExclusive(Ability a, Ability b) {
        if (a == null || b == null) return false;
        // Creator abilities are mutually exclusive with ALL other abilities (including other creator abilities).
        // This is always enforced regardless of the enableMutualExclusion config.
        if (a.getCategory() == AbilityCategory.CREATOR || b.getCategory() == AbilityCategory.CREATOR) {
            return true;
        }
        return isMutuallyExclusive(a.getIdString(), b.getIdString());
    }

    /**
     * Checks if two ability IDs are mutually exclusive via pair-based rules
     * and creator-ability exclusivity.
     * <p>
     * Creator-tier exclusivity is always enforced regardless of the
     * {@code enableMutualExclusion} server config: if either ability ID belongs
     * to the {@link AbilityCategory#CREATOR} category, it is mutually exclusive
     * with ALL other abilities (including other creator abilities). This matches
     * the behavior of {@link #isMutuallyExclusive(Ability, Ability)}.
     * <p>
     * Pair-based exclusions respect the config — returns false (not exclusive)
     * when the config is disabled, unless creator exclusivity applies.
     *
     * @param a First ability ID string
     * @param b Second ability ID string
     * @return true if the abilities cannot coexist
     */
    public static boolean isMutuallyExclusive(String a, String b) {
        // Creator abilities are mutually exclusive with ALL other abilities (including other creator abilities).
        // This is always enforced regardless of the enableMutualExclusion config.
        Ability abilityA = AbilityRegistry.getAbility(a);
        Ability abilityB = AbilityRegistry.getAbility(b);
        if (abilityA != null && abilityA.getCategory() == AbilityCategory.CREATOR) return true;
        if (abilityB != null && abilityB.getCategory() == AbilityCategory.CREATOR) return true;
        // Pair-based exclusions respect the config toggle
        if (!isMutualExclusionEnabled()) return false;
        return EXCLUSION_KEYS.contains(a + "|" + b);
    }

    /**
     * Gets the mutually exclusive partner for an ability, if one exists.
     * Note: An ability may be in multiple exclusion pairs, so this returns the first match.
     *
     * @param ability The ability to check
     * @return Optional containing the excluded ability, or empty if none
     */
    public static Optional<Ability> getMutualExclusionPair(Ability ability) {
        if (ability == null) return Optional.empty();
        return getMutualExclusionPair(ability.getIdString());
    }

    /**
     * Gets the mutually exclusive partner ability by ID string.
     * Returns empty if mutual exclusion is disabled via config.
     *
     * @param abilityId The ability ID to check
     * @return Optional containing the excluded ability, or empty if none or disabled
     */
    public static Optional<Ability> getMutualExclusionPair(String abilityId) {
        if (!isMutualExclusionEnabled()) return Optional.empty();
        for (ExclusionPair pair : EXCLUSION_PAIRS) {
            if (pair.contains(abilityId)) {
                String otherId = pair.getOther(abilityId);
                return Optional.ofNullable(AbilityRegistry.getAbility(otherId));
            }
        }
        return Optional.empty();
    }

    /**
     * Gets all mutually exclusive partners for an ability.
     * Returns an empty list if mutual exclusion is disabled via config.
     *
     * @param abilityId The ability ID to check
     * @return List of all abilities that are mutually exclusive with the given ability
     */
    public static List<Ability> getAllMutualExclusions(String abilityId) {
        List<Ability> exclusions = new ArrayList<>();
        if (!isMutualExclusionEnabled()) return exclusions;
        for (ExclusionPair pair : EXCLUSION_PAIRS) {
            if (pair.contains(abilityId)) {
                String otherId = pair.getOther(abilityId);
                Ability other = AbilityRegistry.getAbility(otherId);
                if (other != null) {
                    exclusions.add(other);
                }
            }
        }
        return exclusions;
    }

    /**
     * Checks if a collection of abilities is internally compatible
     * (no pair within the collection is mutually exclusive).
     *
     * @param abilities The collection of abilities to check
     * @return true if all abilities in the collection can coexist
     */
    public static boolean areCompatible(Collection<Ability> abilities) {
        if (abilities == null || abilities.size() <= 1) return true;

        // Creator abilities are mutually exclusive with ALL other abilities (including other creator abilities)
        // However, a single creator ability alone is compatible (valid)
        long creatorCount = abilities.stream()
                .filter(a -> a.getCategory() == AbilityCategory.CREATOR)
                .count();
        if (creatorCount > 1) {
            return false; // Multiple creators are never compatible
        }
        if (creatorCount == 1 && abilities.size() > 1) {
            return false; // Creator + any other ability is not compatible
        }

        List<Ability> list = new ArrayList<>(abilities);
        for (int i = 0; i < list.size(); i++) {
            for (int j = i + 1; j < list.size(); j++) {
                if (isMutuallyExclusive(list.get(i), list.get(j))) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Gets all exclusion pairs for display or debugging.
     *
     * @return Unmodifiable list of all exclusion pairs
     */
    public static List<ExclusionPair> getExclusionPairs() {
        return Collections.unmodifiableList(EXCLUSION_PAIRS);
    }
}
