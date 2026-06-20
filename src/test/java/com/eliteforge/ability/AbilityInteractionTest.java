package com.eliteforge.ability;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link AbilityInteraction} — the synergy system.
 * <p>
 * Verifies the 20 synergy pairs, the bidirectional O(1) cache, and known
 * synergy metadata (IDs, bonus multipliers).
 * <p>
 * These tests use the string-based {@link AbilityInteraction#getSynergy} API,
 * which performs pure cache lookup and does NOT require the ability registry
 * to be initialized (synergy pairs are stored as string keys, not Ability refs).
 *
 * @see AbilityInteraction
 */
@DisplayName("AbilityInteraction — 20 synergies + bidirectional O(1) cache")
class AbilityInteractionTest {

    @Test
    @DisplayName("exactly 20 synergies are registered")
    void exactlyTwentySynergies() {
        assertEquals(20, AbilityInteraction.getAllSynergies().size(),
                "There should be 20 synergy pairs (7 base + 6 creator-assimilation + 5 additional + 2 creator-cross)");
    }

    @Test
    @DisplayName("known synergy: Fire + SpiritBurn = Inferno (1.5x)")
    void infernoSynergy() {
        AbilityInteraction.SynergyPair synergy =
                AbilityInteraction.getSynergy("eliteforge:fire", "eliteforge:spirit_burn");
        assertNotNull(synergy, "Fire + SpiritBurn should have a synergy");
        assertEquals("inferno", synergy.synergyId(), "Synergy ID should be 'inferno'");
        assertEquals(1.5f, synergy.bonusMultiplier(), 0.001f,
                "Inferno bonus multiplier should be 1.5x");
    }

    @Test
    @DisplayName("synergy lookup is bidirectional: getSynergy(A,B) == getSynergy(B,A)")
    void synergyLookupIsBidirectional() {
        AbilityInteraction.SynergyPair forward =
                AbilityInteraction.getSynergy("eliteforge:fire", "eliteforge:spirit_burn");
        AbilityInteraction.SynergyPair reverse =
                AbilityInteraction.getSynergy("eliteforge:spirit_burn", "eliteforge:fire");
        assertNotNull(forward, "Forward lookup should find the synergy");
        assertNotNull(reverse, "Reverse lookup should find the synergy");
        assertEquals(forward.synergyId(), reverse.synergyId(),
                "Forward and reverse lookup should return the same synergy");
    }

    @Test
    @DisplayName("non-synergistic ability pairs return null")
    void nonSynergisticPairsReturnNull() {
        assertNull(AbilityInteraction.getSynergy("eliteforge:fire", "eliteforge:lightning"),
                "Fire + Lightning have no synergy (they have a synergy with other abilities)");
        assertNull(AbilityInteraction.getSynergy("eliteforge:poison", "eliteforge:web"),
                "Poison + Web have no synergy");
    }

    @Test
    @DisplayName("Thunder Lord synergy exists (Storm + Lightning) — enabled by removing exclusion pair #9")
    void thunderLordSynergyExists() {
        // This synergy was made possible by removing the Storm↔Lightning mutual exclusion.
        // The test validates that the synergy is registered, complementing the
        // MutualExclusionTest.removedStormLightningPairNotExclusive test.
        AbilityInteraction.SynergyPair synergy =
                AbilityInteraction.getSynergy("eliteforge:lightning", "eliteforge:storm");
        assertNotNull(synergy, "Lightning + Storm should have 'Thunder Lord' synergy");
        assertEquals("thunder_lord", synergy.synergyId());
        assertEquals(1.5f, synergy.bonusMultiplier(), 0.001f);
    }

    @Test
    @DisplayName("all synergy pairs have valid metadata")
    void allSynergiesHaveValidMetadata() {
        for (AbilityInteraction.SynergyPair pair : AbilityInteraction.getAllSynergies()) {
            assertNotNull(pair.abilityA(), "abilityA must not be null");
            assertNotNull(pair.abilityB(), "abilityB must not be null");
            assertFalse(pair.abilityA().equals(pair.abilityB()),
                    "Synergy pair must not reference the same ability: " + pair.synergyId());
            assertNotNull(pair.synergyId(), "synergyId must not be null");
            assertFalse(pair.synergyId().isBlank(), "synergyId must not be blank");
            assertTrue(pair.bonusMultiplier() > 1.0f,
                    "Synergy bonus must be > 1.0 (otherwise it's not a bonus): " + pair.synergyId()
                            + " has " + pair.bonusMultiplier());
        }
    }

    @Test
    @DisplayName("getSynergy with null/empty inputs returns null (no exception)")
    void getSynergyHandlesNullInput() {
        assertNull(AbilityInteraction.getSynergy(null, "eliteforge:fire"),
                "Null abilityA should return null, not throw");
        assertNull(AbilityInteraction.getSynergy("eliteforge:fire", null),
                "Null abilityB should return null, not throw");
        assertNull(AbilityInteraction.getSynergy("", "eliteforge:fire"),
                "Empty abilityA should return null");
        assertNull(AbilityInteraction.getSynergy("eliteforge:fire", "nonexistent"),
                "Unknown abilityB should return null");
    }

    @Test
    @DisplayName("creator-tier cross synergies exist (Nexus+Commander, Dominion+Evolution)")
    void creatorCrossSynergiesExist() {
        // These synergies apply when creator abilities are on different entities
        // (since creators can't coexist on the same entity due to exclusivity).
        assertNotNull(AbilityInteraction.getSynergy("eliteforge:creator_nexus", "eliteforge:creator_commander"),
                "Nexus + Commander should have 'Hive Mind' synergy");
        assertNotNull(AbilityInteraction.getSynergy("eliteforge:creator_dominion", "eliteforge:creator_evolution"),
                "Dominion + Evolution should have 'Forged Domain' synergy");
    }

    @Test
    @DisplayName("all synergy bonus multipliers are within expected range (1.1x - 2.0x)")
    void allSynergyMultipliersInRange() {
        for (AbilityInteraction.SynergyPair pair : AbilityInteraction.getAllSynergies()) {
            assertTrue(pair.bonusMultiplier() >= 1.1f && pair.bonusMultiplier() <= 2.0f,
                    "Synergy " + pair.synergyId() + " multiplier " + pair.bonusMultiplier()
                            + " is outside expected range [1.1, 2.0]");
        }
    }
}
