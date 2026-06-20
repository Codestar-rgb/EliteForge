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
 * Updated for v1.0.0: levels are 1-1500 (not 1-10), tier = ceil(level*10/maxLevel).
 * With default maxLevel=1500: tier 3 at level 450, tier 5 at level 750.
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
                "Legendary budget should be 0 below tier 3");
        assertEquals(0, data.getCreatorBudget(), 0.001f,
                "Creator budget should be 0 below tier 5");
    }

    @Test
    @DisplayName("FORGE level 450 (tier 3) unlocks legendary budget")
    void forgeLevelThreeUnlocksLegendary() {
        BudgetData data = AbilityBudget.calculateBudgets(450, DifficultyMode.FORGE);
        assertTrue(data.getLegendaryBudget() > 0,
                "Legendary budget should be positive at tier 3+ in FORGE mode");
    }

    @Test
    @DisplayName("FORGE level 750 (tier 5) unlocks creator budget")
    void forgeLevelFiveUnlocksCreator() {
        BudgetData data = AbilityBudget.calculateBudgets(750, DifficultyMode.FORGE);
        assertTrue(data.getCreatorBudget() > 0,
                "Creator budget should be positive at tier 5+ in FORGE mode");
    }

    @Test
    @DisplayName("Creator budget is 0 below tier 5 (level 750) in all modes")
    void creatorBudgetZeroBelowLevelFive() {
        for (DifficultyMode mode : DifficultyMode.values()) {
            for (int level : new int[]{1, 100, 300, 500}) {
                BudgetData data = AbilityBudget.calculateBudgets(level, mode);
                assertEquals(0, data.getCreatorBudget(), 0.001f,
                        "Creator budget should be 0 at level " + level + " in " + mode);
            }
        }
    }

    @Test
    @DisplayName("CASUAL mode: legendary budget is always 0 regardless of level")
    void casualModeLegendaryAlwaysZero() {
        for (int level = 1; level <= 1500; level += 150) {
            BudgetData data = AbilityBudget.calculateBudgets(level, DifficultyMode.CASUAL);
            assertEquals(0, data.getLegendaryBudget(), 0.001f,
                    "CASUAL legendary budget should be 0 at level " + level);
        }
    }

    @Test
    @DisplayName("CASUAL mode: creator budget is always 0 regardless of level")
    void casualModeCreatorAlwaysZero() {
        for (int level = 1; level <= 1500; level += 150) {
            BudgetData data = AbilityBudget.calculateBudgets(level, DifficultyMode.CASUAL);
            assertEquals(0, data.getCreatorBudget(), 0.001f,
                    "CASUAL creator budget should be 0 at level " + level);
        }
    }

    @Test
    @DisplayName("CASUAL mode: attack/defense/control budgets are capped")
    void casualModeBudgetsAreCapped() {
        BudgetData casualData = AbilityBudget.calculateBudgets(1500, DifficultyMode.CASUAL);
        BudgetData forgeData = AbilityBudget.calculateBudgets(1500, DifficultyMode.FORGE);
        assertTrue(casualData.getAttackBudget() <= forgeData.getAttackBudget(),
                "CASUAL attack should be <= FORGE at same level");
        assertTrue(casualData.getDefenseBudget() <= forgeData.getDefenseBudget(),
                "CASUAL defense should be <= FORGE at same level");
    }

    @Test
    @DisplayName("FORGE budgets grow with level (higher level = more budget)")
    void forgeBudgetsGrowWithLevel() {
        BudgetData low = AbilityBudget.calculateBudgets(10, DifficultyMode.FORGE);
        BudgetData high = AbilityBudget.calculateBudgets(1000, DifficultyMode.FORGE);
        assertTrue(high.getAttackBudget() >= low.getAttackBudget(),
                "Higher level should have >= attack budget");
        assertTrue(high.getDefenseBudget() >= low.getDefenseBudget(),
                "Higher level should have >= defense budget");
    }

    @Test
    @DisplayName("FORGE has higher budgets than CASUAL at same level")
    void forgeHasHigherBudgetsThanCasual() {
        int level = 500;
        BudgetData forge = AbilityBudget.calculateBudgets(level, DifficultyMode.FORGE);
        BudgetData casual = AbilityBudget.calculateBudgets(level, DifficultyMode.CASUAL);
        assertTrue(forge.getAttackBudget() >= casual.getAttackBudget(),
                "FORGE attack should be >= CASUAL at same level");
    }

    @Test
    @DisplayName("getMaxAbilities: FORGE grows with level (more abilities at higher levels)")
    void forgeMaxAbilitiesGrowsWithLevel() {
        int low = AbilityBudget.getMaxAbilities(10, DifficultyMode.FORGE);
        int high = AbilityBudget.getMaxAbilities(1000, DifficultyMode.FORGE);
        assertTrue(high >= low, "Higher level should have >= max abilities");
    }

    @Test
    @DisplayName("getMaxAbilities: CASUAL is capped at 2")
    void casualMaxAbilitiesCappedAtTwo() {
        int result = AbilityBudget.getMaxAbilities(1500, DifficultyMode.CASUAL);
        assertTrue(result <= 2, "CASUAL max abilities should be capped at 2, got " + result);
    }

    @Test
    @DisplayName("allowsCreatorAbilities(mode, level): requires both non-CASUAL AND level>=750")
    void allowsCreatorAbilitiesModeAndLevel() {
        assertFalse(AbilityBudget.allowsCreatorAbilities(DifficultyMode.FORGE, 100),
                "Should not allow creator below tier 5");
        assertTrue(AbilityBudget.allowsCreatorAbilities(DifficultyMode.FORGE, 800),
                "Should allow creator at tier 5+ in FORGE");
        assertFalse(AbilityBudget.allowsCreatorAbilities(DifficultyMode.CASUAL, 1500),
                "CASUAL should never allow creator");
    }

    @Test
    @DisplayName("BudgetData.getBudgetForCategory returns correct value for each category")
    void budgetDataGetBudgetForCategory() {
        BudgetData data = AbilityBudget.calculateBudgets(500, DifficultyMode.FORGE);
        assertTrue(data.getBudgetForCategory(AbilityCategory.ATTACK) > 0, "Attack budget should be > 0");
        assertTrue(data.getBudgetForCategory(AbilityCategory.DEFENSE) > 0, "Defense budget should be > 0");
    }

    @Test
    @DisplayName("BudgetData.toString contains all 5 budget values")
    void budgetDataToStringContainsAllValues() {
        BudgetData data = AbilityBudget.calculateBudgets(500, DifficultyMode.FORGE);
        String str = data.toString();
        assertTrue(str.contains("attack"), "toString should contain 'attack'");
        assertTrue(str.contains("defense"), "toString should contain 'defense'");
    }

    @Test
    @DisplayName("calculateBudgets clamps level < 1 to level 1 (no crash)")
    void calculateBudgetsClampsNegativeLevel() {
        BudgetData data = AbilityBudget.calculateBudgets(-5, DifficultyMode.FORGE);
        assertNotNull(data, "Should not crash on negative level");
        assertTrue(data.getAttackBudget() >= 0, "Budget should be non-negative");
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
                    mode + " should allow creator abilities");
        }
    }

    @ParameterizedTest
    @EnumSource(DifficultyMode.class)
    @DisplayName("allowsLegendaryAbilities: only CASUAL is false")
    void allowsLegendaryAbilitiesByMode(DifficultyMode mode) {
        if (mode == DifficultyMode.CASUAL) {
            assertFalse(AbilityBudget.allowsLegendaryAbilities(mode),
                    "CASUAL should not allow legendary abilities");
        } else {
            assertTrue(AbilityBudget.allowsLegendaryAbilities(mode),
                    mode + " should allow legendary abilities");
        }
    }

    @Test
    @DisplayName("getMinimumAbilityGuarantee returns 1")
    void getMinimumAbilityGuaranteeReturns1() {
        assertEquals(1, AbilityBudget.getMinimumAbilityGuarantee());
    }

    @Test
    @DisplayName("getMaxAbilityLevel returns 1-5, never exceeds 5")
    void getMaxAbilityLevelNeverExceeds5() {
        for (int level = 1; level <= 1500; level += 100) {
            int maxLvl = AbilityBudget.getMaxAbilityLevel(level, DifficultyMode.FORGE);
            assertTrue(maxLvl >= 1 && maxLvl <= 5,
                    "Max ability level should be 1-5, got " + maxLvl + " at level " + level);
        }
    }
}
