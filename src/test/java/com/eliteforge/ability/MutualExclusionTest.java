package com.eliteforge.ability;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link MutualExclusion}.
 * <p>
 * Verifies the 19 mutual-exclusion pairs, the O(1) symmetric lookup cache,
 * creator-tier exclusivity (always enforced), and the {@link MutualExclusion#areCompatible}
 * collection-level check.
 * <p>
 * <b>Note on config dependency:</b> {@code isMutuallyExclusive(String, String)} reads
 * {@code EliteForgeConfig.SERVER.enableMutualExclusion}. In a unit test environment
 * the config is not loaded, so the method's catch block defaults to {@code true}
 * (exclusion enabled). This is the intended safe default and means pair-based
 * exclusion rules are active during tests.
 *
 * @see MutualExclusion
 */
@DisplayName("MutualExclusion — 19 pairs + creator exclusivity")
class MutualExclusionTest {

    @BeforeAll
    static void initRegistry() {
        // MutualExclusion.isMutuallyExclusive(String, String) looks up abilities
        // via AbilityRegistry to check creator-tier status, so the registry must
        // be initialized before any exclusion check.
        AbilityRegistry.init();
    }

    @Test
    @DisplayName("exactly 19 exclusion pairs are registered")
    void exactlyNineteenExclusionPairs() {
        assertEquals(19, MutualExclusion.getExclusionPairs().size(),
                "There should be 19 mutual exclusion pairs (pair #9 Storm↔Lightning was removed)");
    }

    @Test
    @DisplayName("known exclusion pairs are correctly detected")
    void knownExclusionPairsDetected() {
        // Pair 1: IronWall ↔ Void
        assertTrue(MutualExclusion.isMutuallyExclusive("eliteforge:iron_wall", "eliteforge:void"),
                "IronWall and Void should be mutually exclusive");
        // Pair 4: Thorns ↔ Phase
        assertTrue(MutualExclusion.isMutuallyExclusive("eliteforge:thorns", "eliteforge:phase"),
                "Thorns and Phase should be mutually exclusive");
        // Pair 11: Berserk ↔ DeathTouch
        assertTrue(MutualExclusion.isMutuallyExclusive("eliteforge:berserk", "eliteforge:death_touch"),
                "Berserk and DeathTouch should be mutually exclusive");
        // Pair 20: Bloodthirst ↔ Siphon
        assertTrue(MutualExclusion.isMutuallyExclusive("eliteforge:bloodthirst", "eliteforge:siphon"),
                "Bloodthirst and Siphon should be mutually exclusive");
    }

    @Test
    @DisplayName("exclusion is symmetric: isMutuallyExclusive(a,b) == isMutuallyExclusive(b,a)")
    void exclusionIsSymmetric() {
        List<MutualExclusion.ExclusionPair> pairs = MutualExclusion.getExclusionPairs();
        for (MutualExclusion.ExclusionPair pair : pairs) {
            assertTrue(MutualExclusion.isMutuallyExclusive(pair.abilityA(), pair.abilityB()),
                    pair.abilityA() + " ↔ " + pair.abilityB() + " should be exclusive (A→B)");
            assertTrue(MutualExclusion.isMutuallyExclusive(pair.abilityB(), pair.abilityA()),
                    pair.abilityB() + " ↔ " + pair.abilityA() + " should be exclusive (B→A)");
        }
    }

    @Test
    @DisplayName("non-paired abilities are not mutually exclusive")
    void nonPairedAbilitiesNotExclusive() {
        assertFalse(MutualExclusion.isMutuallyExclusive("eliteforge:fire", "eliteforge:lightning"),
                "Fire and Lightning are not a pair and should not be exclusive");
        assertFalse(MutualExclusion.isMutuallyExclusive("eliteforge:poison", "eliteforge:web"),
                "Poison and Web are not a pair and should not be exclusive");
    }

    @Test
    @DisplayName("removed Storm↔Lightning pair is NOT exclusive (enables Thunder Lord synergy)")
    void removedStormLightningPairNotExclusive() {
        // This pair was explicitly removed to enable the "Thunder Lord" synergy.
        // If someone accidentally re-adds it, this test will catch the regression.
        assertFalse(MutualExclusion.isMutuallyExclusive("eliteforge:storm", "eliteforge:lightning"),
                "Storm ↔ Lightning must NOT be exclusive (removed to enable Thunder Lord synergy)");
    }

    @Test
    @DisplayName("creator abilities are exclusive with ALL other abilities (including other creators)")
    void creatorAbilitiesAreExclusiveWithAll() {
        Ability nexus = AbilityRegistry.getAbility("eliteforge:nexus");
        Ability fire = AbilityRegistry.getAbility("eliteforge:fire");
        Ability dominion = AbilityRegistry.getAbility("eliteforge:dominion");

        assertNotNull(nexus, "nexus must be registered");
        assertNotNull(fire, "fire must be registered");
        assertNotNull(dominion, "dominion must be registered");

        // Creator ↔ non-creator
        assertTrue(MutualExclusion.isMutuallyExclusive(nexus, fire),
                "Creator (Nexus) must be exclusive with non-creator (Fire)");
        // Creator ↔ Creator
        assertTrue(MutualExclusion.isMutuallyExclusive(nexus, dominion),
                "Creator (Nexus) must be exclusive with another creator (Dominion)");
        // String-based API
        assertTrue(MutualExclusion.isMutuallyExclusive("eliteforge:nexus", "eliteforge:fire"),
                "String API: Nexus must be exclusive with Fire");
        assertTrue(MutualExclusion.isMutuallyExclusive("eliteforge:nexus", "eliteforge:dominion"),
                "String API: Nexus must be exclusive with Dominion");
    }

    @Test
    @DisplayName("null abilities are not mutually exclusive (trivially compatible)")
    void nullAbilitiesNotExclusive() {
        Ability fire = AbilityRegistry.getAbility("eliteforge:fire");
        assertFalse(MutualExclusion.isMutuallyExclusive(null, fire),
                "null ↔ fire should not be exclusive");
        assertFalse(MutualExclusion.isMutuallyExclusive(fire, null),
                "fire ↔ null should not be exclusive");
        assertFalse(MutualExclusion.isMutuallyExclusive((Ability) null, null),
                "null ↔ null should not be exclusive");
    }

    @Test
    @DisplayName("areCompatible: compatible collection returns true")
    void areCompatibleWithCompatibleCollection() {
        Ability fire = AbilityRegistry.getAbility("eliteforge:fire");
        Ability lightning = AbilityRegistry.getAbility("eliteforge:lightning");
        // Fire and Lightning are not a pair → compatible
        assertTrue(MutualExclusion.areCompatible(Arrays.asList(fire, lightning)),
                "Fire + Lightning should be compatible");
    }

    @Test
    @DisplayName("areCompatible: exclusive pair returns false")
    void areCompatibleWithExclusivePair() {
        Ability ironWall = AbilityRegistry.getAbility("eliteforge:iron_wall");
        Ability voidAb = AbilityRegistry.getAbility("eliteforge:void");
        assertFalse(MutualExclusion.areCompatible(Arrays.asList(ironWall, voidAb)),
                "IronWall + Void should NOT be compatible");
    }

    @Test
    @DisplayName("areCompatible: creator + any other returns false")
    void areCompatibleCreatorPlusAnyReturnsFalse() {
        Ability nexus = AbilityRegistry.getAbility("eliteforge:nexus");
        Ability fire = AbilityRegistry.getAbility("eliteforge:fire");
        assertFalse(MutualExclusion.areCompatible(Arrays.asList(nexus, fire)),
                "Creator + any ability should NOT be compatible");
    }

    @Test
    @DisplayName("areCompatible: two creators returns false")
    void areCompatibleTwoCreatorsReturnsFalse() {
        Ability nexus = AbilityRegistry.getAbility("eliteforge:nexus");
        Ability dominion = AbilityRegistry.getAbility("eliteforge:dominion");
        assertFalse(MutualExclusion.areCompatible(Arrays.asList(nexus, dominion)),
                "Two creator abilities should NOT be compatible");
    }

    @Test
    @DisplayName("areCompatible: single creator alone is valid (returns true)")
    void areCompatibleSingleCreatorIsValid() {
        Ability nexus = AbilityRegistry.getAbility("eliteforge:nexus");
        assertTrue(MutualExclusion.areCompatible(List.of(nexus)),
                "A single creator ability alone should be compatible (valid configuration)");
    }

    @Test
    @DisplayName("areCompatible: empty/null/single collections are trivially compatible")
    void areCompatibleEdgeCases() {
        assertTrue(MutualExclusion.areCompatible(null), "null collection should be compatible");
        assertTrue(MutualExclusion.areCompatible(List.of()), "empty collection should be compatible");
        Ability fire = AbilityRegistry.getAbility("eliteforge:fire");
        assertTrue(MutualExclusion.areCompatible(List.of(fire)), "single ability should be compatible");
    }
}
