package com.eliteforge.ability;

import com.eliteforge.ability.AbilityBudget.BudgetData;
import com.eliteforge.config.DifficultyMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link AbilityBudget}.
 * <p>
 * Pure-logic tests covering budget calculation, CASUAL-mode caps, the creator
 * level gate (level 5+), and max-ability formulas.
 * <p>
 * <b>Key invariants verified:</b>
 * <ul>
 *   <li>CASUAL mode: legendary budget = 0, creator budget = 0</li>
 *   <li>Creator budget is 0 below level 5 (in any non-CASUAL mode)</li>
 *   <li>Creator budget is positive at level 5+ in FORGE/MIXED</li>
 *   <li>CASUAL mode: max abilities capped at 2</li>
 *   <li>Budgets are always non-negative</li>
 * </ul>
 *
 * @see AbilityBudget
 */
@DisplayName("AbilityBudget — dual-budget allocation + CASUAL caps")
class AbilityBudgetTest {

    @Test
    @DisplayName("FORGE level 1 has positive attack/defense/control budgets, zero legendary/creator")
    void forgeLevelOneBudgets() {
        BudgetData data = AbilityBudget.calculateBudgets(1, DifficultyMode.FORGE);
        assertTrue(data.getAttackBudget() > 0, "Attack budget should be positive at level 1 FORGE");
        assertTrue(data.getDefenseBudget() > 0, "Defense budget should be positive at level 1 FORGE");
        assertTrue(data.getControlBudget() > 0, "Control budget should be positive at level 1 FORGE");
        assertEquals(0, data.getLegendaryBudget(), 0.001f,
                "Legendary budget should be 0 below level 3");
        assertEquals(0, data.getCreatorBudget(), 0.001f,
                "Creator budget should be 0 below level 5");
    }

    @Test
    @DisplayName("FORGE level 3 unlocks legendary budget")
    void forgeLevelThreeUnlocksLegendary() {
        BudgetData data = AbilityBudget.calculateBudgets(3, DifficultyMode.FORGE);
        assertTrue(data.getLegendaryBudget() > 0,
                "Legendary budget should be positive at level 3+ in FORGE mode");
    }

    @Test
    @DisplayName("FORGE level 5 unlocks creator budget")
    void forgeLevelFiveUnlocksCreator() {
        BudgetData data = AbilityBudget.calculateBudgets(5, DifficultyMode.FORGE);
        assertTrue(data.getCreatorBudget() > 0,
                "Creator budget should be positive at level 5+ in FORGE mode");
    }

    @Test
    @DisplayName("Creator budget is 0 below level 5 in all modes")
    void creatorBudgetZeroBelowLevelFive() {
        for (DifficultyMode mode : DifficultyMode.values()) {
            for (int level = 1; level < 5; level++) {
                BudgetData data = AbilityBudget.calculateBudgets(level, mode);
                assertEquals(0, data.getCreatorBudget(), 0.001f,
                        "Creator budget should be 0 at level " + level + " in " + mode
                                + " (got " + data.getCreatorBudget() + ")");
            }
        }
    }

    @Test
    @DisplayName("CASUAL mode: legendary budget is always 0 regardless of level")
    void casualModeLegendaryAlwaysZero() {
        for (int level = 1; level <= 10; level++) {
            BudgetData data = AbilityBudget.calculateBudgets(level, DifficultyMode.CASUAL);
            assertEquals(0, data.getLegendaryBudget(), 0.001f,
                    "CASUAL legendary budget should be 0 at level " + level);
        }
    }

    @Test
    @DisplayName("CASUAL mode: creator budget is always 0 regardless of level")
    void casualModeCreatorAlwaysZero() {
        for (int level = 1; level <= 10; level++) {
            BudgetData data = AbilityBudget.calculateBudgets(level, DifficultyMode.CASUAL);
            assertEquals(0, data.getCreatorBudget(), 0.001f,
                    "CASUAL creator budget should be 0 at level " + level);
        }
    }

    @Test
    @DisplayName("CASUAL mode: attack/defense/control budgets are capped")
    void casualModeBudgetsAreCapped() {
        // At very high levels, CASUAL budgets should hit their caps
        BudgetData data = AbilityBudget.calculateBudgets(20, DifficultyMode.CASUAL);
        assertTrue(data.getAttackBudget() <= 10.0f + 0.001f,
                "CASUAL attack budget should be capped at 10.0 (got " + data.getAttackBudget() + ")");
        assertTrue(data.getDefenseBudget() <= 10.0f + 0.001f,
                "CASUAL defense budget should be capped at 10.0 (got " + data.getDefenseBudget() + ")");
        assertTrue(data.getControlBudget() <= 8.0f + 0.001f,
                "CASUAL control budget should be capped at 8.0 (got " + data.getControlBudget() + ")");
    }

    @Test
    @DisplayName("FORGE budgets grow with level (higher level = more budget)")
    void forgeBudgetsGrowWithLevel() {
        BudgetData low = AbilityBudget.calculateBudgets(2, DifficultyMode.FORGE);
        BudgetData high = AbilityBudget.calculateBudgets(8, DifficultyMode.FORGE);
        assertTrue(high.getAttackBudget() > low.getAttackBudget(),
                "Higher level should have more attack budget");
        assertTrue(high.getDefenseBudget() > low.getDefenseBudget(),
                "Higher level should have more defense budget");
        assertTrue(high.getControlBudget() > low.getControlBudget(),
                "Higher level should have more control budget");
    }

    @Test
    @DisplayName("FORGE mode has higher budgets than CASUAL at same level")
    void forgeHasHigherBudgetsThanCasual() {
        int level = 5;
        BudgetData forge = AbilityBudget.calculateBudgets(level, DifficultyMode.FORGE);
        BudgetData casual = AbilityBudget.calculateBudgets(level, DifficultyMode.CASUAL);
        assertTrue(forge.getAttackBudget() > casual.getAttackBudget(),
                "FORGE attack budget should exceed CASUAL at level " + level);
        assertTrue(forge.getDefenseBudget() > casual.getDefenseBudget(),
                "FORGE defense budget should exceed CASUAL at level " + level);
    }

    @Test
    @DisplayName("getMaxAbilities: CASUAL is capped at 2")
    void casualMaxAbilitiesCappedAtTwo() {
        for (int level = 1; level <= 20; level++) {
            int max = AbilityBudget.getMaxAbilities(level, DifficultyMode.CASUAL);
            assertTrue(max <= 2,
                    "CASUAL max abilities should be <= 2 at level " + level + " (got " + max + ")");
            assertTrue(max >= 1, "Max abilities should be >= 1");
        }
    }

    @Test
    @DisplayName("getMaxAbilities: FORGE grows with level (more abilities at higher levels)")
    void forgeMaxAbilitiesGrowsWithLevel() {
        int low = AbilityBudget.getMaxAbilities(1, DifficultyMode.FORGE);
        int high = AbilityBudget.getMaxAbilities(10, DifficultyMode.FORGE);
        assertTrue(high > low,
                "FORGE max abilities at level 10 (" + high + ") should exceed level 1 (" + low + ")");
    }

    @ParameterizedTest
    @EnumSource(DifficultyMode.class)
    @DisplayName("allowsLegendaryAbilities: only CASUAL is false")
    void allowsLegendaryAbilities(DifficultyMode mode) {
        if (mode == DifficultyMode.CASUAL) {
            assertFalse(AbilityBudget.allowsLegendaryAbilities(mode),
                    "CASUAL should not allow legendary abilities");
        } else {
            assertTrue(AbilityBudget.allowsLegendaryAbilities(mode),
                    mode + " should allow legendary abilities");
        }
    }

    @ParameterizedTest
    @EnumSource(DifficultyMode.class)
    @DisplayName("allowsCreatorAbilities(mode): only CASUAL is false")
    void allowsCreatorAbilitiesByMode(DifficultyMode mode) {
        if (mode == DifficultyMode.CASUAL) {
            assertFalse(AbilityBudget.allowsCreatorAbilities(mode),
                    "CASUAL should not allow creator abilities");
        } else {
            assertTrue(AbilityBudget.allowsCreatorAbilities(mode),
                    mode + " should allow creator abilities (mode check only)");
        }
    }

    @Test
    @DisplayName("allowsCreatorAbilities(mode, level): requires both non-CASUAL AND level>=5")
    void allowsCreatorAbilitiesModeAndLevel() {
        // CASUAL never allows, regardless of level
        assertFalse(AbilityBudget.allowsCreatorAbilities(DifficultyMode.CASUAL, 5));
        assertFalse(AbilityBudget.allowsCreatorAbilities(DifficultyMode.CASUAL, 10));

        // FORGE requires level 5+
        assertFalse(AbilityBudget.allowsCreatorAbilities(DifficultyMode.FORGE, 4),
                "FORGE level 4 should not allow creator abilities");
        assertTrue(AbilityBudget.allowsCreatorAbilities(DifficultyMode.FORGE, 5),
                "FORGE level 5 should allow creator abilities");
        assertTrue(AbilityBudget.allowsCreatorAbilities(DifficultyMode.FORGE, 10),
                "FORGE level 10 should allow creator abilities");

        // MIXED requires level 5+
        assertFalse(AbilityBudget.allowsCreatorAbilities(DifficultyMode.MIXED, 4));
        assertTrue(AbilityBudget.allowsCreatorAbilities(DifficultyMode.MIXED, 5));
    }

    @Test
    @DisplayName("getMinimumAbilityGuarantee returns 1")
    void minimumAbilityGuaranteeIsOne() {
        assertEquals(1, AbilityBudget.getMinimumAbilityGuarantee(),
                "Each elite should be guaranteed at least 1 ability");
    }

    @Test
    @DisplayName("getMaxAbilityLevel returns 1-5, never exceeds 5")
    void maxAbilityLevelInRange() {
        for (DifficultyMode mode : DifficultyMode.values()) {
            for (int level = 1; level <= 20; level++) {
                int maxLevel = AbilityBudget.getMaxAbilityLevel(level, mode);
                assertTrue(maxLevel >= 1 && maxLevel <= 5,
                        "Max ability level " + maxLevel + " out of range [1,5] for "
                                + mode + " level " + level);
            }
        }
    }

    @Test
    @DisplayName("BudgetData.getBudgetForCategory returns correct value for each category")
    void budgetDataGetBudgetForCategory() {
        BudgetData data = AbilityBudget.calculateBudgets(5, DifficultyMode.FORGE);
        assertEquals(data.getAttackBudget(), data.getBudgetForCategory(AbilityCategory.ATTACK), 0.001f);
        assertEquals(data.getDefenseBudget(), data.getBudgetForCategory(AbilityCategory.DEFENSE), 0.001f);
        assertEquals(data.getControlBudget(), data.getBudgetForCategory(AbilityCategory.CONTROL), 0.001f);
        assertEquals(data.getLegendaryBudget(), data.getBudgetForCategory(AbilityCategory.LEGENDARY), 0.001f);
        assertEquals(data.getCreatorBudget(), data.getBudgetForCategory(AbilityCategory.CREATOR), 0.001f);
    }

    @Test
    @DisplayName("BudgetData.toString contains all 5 budget values")
    void budgetDataToStringContainsAllValues() {
        BudgetData data = AbilityBudget.calculateBudgets(5, DifficultyMode.FORGE);
        String str = data.toString();
        assertTrue(str.contains("attack="), "toString should contain 'attack='");
        assertTrue(str.contains("defense="), "toString should contain 'defense='");
        assertTrue(str.contains("control="), "toString should contain 'control='");
        assertTrue(str.contains("legendary="), "toString should contain 'legendary='");
        assertTrue(str.contains("creator="), "toString should contain 'creator='");
    }

    @Test
    @DisplayName("calculateBudgets clamps level < 1 to level 1 (no crash)")
    void calculateBudgetsClampsNegativeLevel() {
        // Should not throw; should treat as level 1
        BudgetData data = AbilityBudget.calculateBudgets(0, DifficultyMode.FORGE);
        assertNotNull(data, "Budget calculation with level 0 should not return null");
        BudgetData dataNeg = AbilityBudget.calculateBudgets(-5, DifficultyMode.FORGE);
        assertNotNull(dataNeg, "Budget calculation with negative level should not return null");
        // Level 0 and level 1 should produce identical results (both clamped to 1)
        BudgetData dataOne = AbilityBudget.calculateBudgets(1, DifficultyMode.FORGE);
        assertEquals(dataOne.getAttackBudget(), data.getAttackBudget(), 0.001f);
    }
}
