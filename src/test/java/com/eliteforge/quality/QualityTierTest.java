package com.eliteforge.quality;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link QualityTier}.
 * <p>
 * Pure-logic tests covering the 6-tier enum, weight constants, natural-roll
 * restrictions, weighted random selection, and parsing.
 * <p>
 * <b>Key invariants verified:</b>
 * <ul>
 *   <li>MYTHIC has weight 0 and canRollNaturally=false (only assigned via creator conversion)</li>
 *   <li>All other tiers canRollNaturally=true</li>
 *   <li>totalWeight() = 50+30+15+4+1 = 100 (excludes MYTHIC)</li>
 *   <li>weightedRandom never returns MYTHIC</li>
 *   <li>fromName is case-insensitive and defaults to NORMAL on failure</li>
 * </ul>
 *
 * @see QualityTier
 */
@DisplayName("QualityTier — 6 tiers, weighted random, MYTHIC exclusivity")
class QualityTierTest {

    @Test
    @DisplayName("exactly 6 quality tiers exist")
    void exactlySixTiers() {
        assertEquals(6, QualityTier.values().length,
                "Should have exactly 6 quality tiers: NORMAL, GOOD, FINE, EPIC, LEGENDARY, MYTHIC");
    }

    @Test
    @DisplayName("tier order is NORMAL < GOOD < FINE < EPIC < LEGENDARY < MYTHIC")
    void tierOrderIsCorrect() {
        QualityTier[] tiers = QualityTier.values();
        assertEquals(QualityTier.NORMAL, tiers[0]);
        assertEquals(QualityTier.GOOD, tiers[1]);
        assertEquals(QualityTier.FINE, tiers[2]);
        assertEquals(QualityTier.EPIC, tiers[3]);
        assertEquals(QualityTier.LEGENDARY, tiers[4]);
        assertEquals(QualityTier.MYTHIC, tiers[5]);
    }

    @Test
    @DisplayName("weights match design: NORMAL=50, GOOD=30, FINE=15, EPIC=4, LEGENDARY=1, MYTHIC=0")
    void weightsMatchDesign() {
        assertEquals(50, QualityTier.NORMAL.getWeight());
        assertEquals(30, QualityTier.GOOD.getWeight());
        assertEquals(15, QualityTier.FINE.getWeight());
        assertEquals(4, QualityTier.EPIC.getWeight());
        assertEquals(1, QualityTier.LEGENDARY.getWeight());
        assertEquals(0, QualityTier.MYTHIC.getWeight(), "MYTHIC must have weight 0");
    }

    @Test
    @DisplayName("loot bonuses are monotonically increasing")
    void lootBonusesIncrease() {
        assertTrue(QualityTier.NORMAL.getLootBonus() < QualityTier.GOOD.getLootBonus());
        assertTrue(QualityTier.GOOD.getLootBonus() < QualityTier.FINE.getLootBonus());
        assertTrue(QualityTier.FINE.getLootBonus() < QualityTier.EPIC.getLootBonus());
        assertTrue(QualityTier.EPIC.getLootBonus() < QualityTier.LEGENDARY.getLootBonus());
        assertTrue(QualityTier.LEGENDARY.getLootBonus() < QualityTier.MYTHIC.getLootBonus());
        assertEquals(10.0f, QualityTier.MYTHIC.getLootBonus(), 0.001f,
                "MYTHIC loot bonus should be 10.0x");
    }

    @Test
    @DisplayName("MYTHIC cannot be rolled naturally; all others can")
    void mythicCannotRollNaturally() {
        assertFalse(QualityTier.MYTHIC.canRollNaturally(),
                "MYTHIC must not be rollable (only assigned via creator conversion)");
        assertTrue(QualityTier.NORMAL.canRollNaturally());
        assertTrue(QualityTier.GOOD.canRollNaturally());
        assertTrue(QualityTier.FINE.canRollNaturally());
        assertTrue(QualityTier.EPIC.canRollNaturally());
        assertTrue(QualityTier.LEGENDARY.canRollNaturally());
    }

    @Test
    @DisplayName("totalWeight is 100 (50+30+15+4+1, excludes MYTHIC)")
    void totalWeightIsOneHundred() {
        assertEquals(100, QualityTier.totalWeight(),
                "Total weight of rollable tiers should be 100");
    }

    @Test
    @DisplayName("weightedRandom never returns MYTHIC")
    void weightedRandomNeverReturnsMythic() {
        Random fixedSeed = new Random(42);
        for (int i = 0; i < 10_000; i++) {
            QualityTier rolled = QualityTier.weightedRandom(fixedSeed);
            assertNotEquals(QualityTier.MYTHIC, rolled,
                    "weightedRandom must never return MYTHIC (iteration " + i + ")");
        }
    }

    @Test
    @DisplayName("weightedRandom distribution is roughly proportional to weights")
    void weightedRandomDistributionIsProportional() {
        // With 10000 rolls and totalWeight=100, expected counts:
        // NORMAL=5000, GOOD=3000, FINE=1500, EPIC=400, LEGENDARY=100
        // Allow ±20% tolerance for randomness.
        Random fixedSeed = new Random(12345);
        int[] counts = new int[6]; // index by ordinal
        int rolls = 10_000;
        for (int i = 0; i < rolls; i++) {
            counts[QualityTier.weightedRandom(fixedSeed).ordinal()]++;
        }
        // NORMAL weight=50 → expect ~5000
        assertTrue(counts[QualityTier.NORMAL.ordinal()] > 4000 && counts[QualityTier.NORMAL.ordinal()] < 6000,
                "NORMAL count " + counts[QualityTier.NORMAL.ordinal()] + " outside [4000, 6000]");
        // LEGENDARY weight=1 → expect ~100
        assertTrue(counts[QualityTier.LEGENDARY.ordinal()] > 50 && counts[QualityTier.LEGENDARY.ordinal()] < 200,
                "LEGENDARY count " + counts[QualityTier.LEGENDARY.ordinal()] + " outside [50, 200]");
        // MYTHIC should be 0
        assertEquals(0, counts[QualityTier.MYTHIC.ordinal()],
                "MYTHIC should never be rolled");
    }

    @ParameterizedTest
    @ValueSource(strings = {"NORMAL", "GOOD", "FINE", "EPIC", "LEGENDARY", "MYTHIC"})
    @DisplayName("fromName parses exact names correctly")
    void fromNameParsesExactNames(String name) {
        assertEquals(QualityTier.valueOf(name), QualityTier.fromName(name),
                "fromName should parse exact name: " + name);
    }

    @Test
    @DisplayName("fromName is case-insensitive")
    void fromNameIsCaseInsensitive() {
        assertEquals(QualityTier.LEGENDARY, QualityTier.fromName("legendary"),
                "fromName should be case-insensitive (lowercase)");
        assertEquals(QualityTier.MYTHIC, QualityTier.fromName("MyThIc"),
                "fromName should be case-insensitive (mixed case)");
    }

    @Test
    @DisplayName("fromName defaults to NORMAL on invalid input")
    void fromNameDefaultsToNormalOnInvalidInput() {
        assertEquals(QualityTier.NORMAL, QualityTier.fromName("INVALID"),
                "Invalid name should default to NORMAL");
        assertEquals(QualityTier.NORMAL, QualityTier.fromName(""),
                "Empty string should default to NORMAL");
        assertEquals(QualityTier.NORMAL, QualityTier.fromName(null),
                "Null should default to NORMAL");
    }

    @Test
    @DisplayName("next() returns the next higher tier, or self if already at max")
    void nextReturnsHigherTier() {
        assertEquals(QualityTier.GOOD, QualityTier.NORMAL.next());
        assertEquals(QualityTier.FINE, QualityTier.GOOD.next());
        assertEquals(QualityTier.MYTHIC, QualityTier.LEGENDARY.next());
        assertEquals(QualityTier.MYTHIC, QualityTier.MYTHIC.next(),
                "next() on MYTHIC should return MYTHIC (already at max)");
    }

    @Test
    @DisplayName("previous() returns the next lower tier, or self if already at min")
    void previousReturnsLowerTier() {
        assertEquals(QualityTier.NORMAL, QualityTier.GOOD.previous());
        assertEquals(QualityTier.LEGENDARY, QualityTier.MYTHIC.previous());
        assertEquals(QualityTier.NORMAL, QualityTier.NORMAL.previous(),
                "previous() on NORMAL should return NORMAL (already at min)");
    }

    @Test
    @DisplayName("MYTHIC has glow effect (visual distinction for creator-tier)")
    void mythicHasGlow() {
        assertTrue(QualityTier.MYTHIC.hasGlow(),
                "MYTHIC should have glow effect");
        assertFalse(QualityTier.NORMAL.hasGlow(),
                "NORMAL should not have glow");
        assertFalse(QualityTier.GOOD.hasGlow(),
                "GOOD should not have glow");
    }
}
