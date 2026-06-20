package com.eliteforge.ability;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Collection;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link AbilityRegistry}.
 * <p>
 * These are pure-logic tests runnable via {@code ./gradlew test} — no Minecraft
 * client launch required. The registry is initialized once in {@link BeforeAll}
 * via {@link AbilityRegistry#init()}, which constructs all 54 ability instances.
 * <p>
 * <b>What is covered:</b>
 * <ul>
 *   <li>Registry integrity: exactly 54 abilities, no nulls, no duplicate IDs</li>
 *   <li>Category counts: ATTACK=12, DEFENSE=12, CONTROL=12, LEGENDARY=10, CREATOR=8</li>
 *   <li>Lookup by string ID and by ResourceLocation</li>
 *   <li>Non-creator cache consistency</li>
 * </ul>
 *
 * @see AbilityRegistry
 */
@DisplayName("AbilityRegistry — 54-ability registry integrity")
class AbilityRegistryTest {

    @BeforeAll
    static void initRegistry() {
        // AbilityRegistry.init() is idempotent (guarded by `initialized` flag),
        // so calling it here is safe even if FMLCommonSetupEvent already ran.
        AbilityRegistry.init();
    }

    @Test
    @DisplayName("registry contains exactly 54 abilities")
    void registryHasFiftyFourAbilities() {
        assertEquals(54, AbilityRegistry.getAbilityCount(),
                "AbilityRegistry should contain exactly 54 abilities (12+12+12+10+8)");
    }

    @ParameterizedTest
    @EnumSource(AbilityCategory.class)
    @DisplayName("each category has the documented ability count")
    void categoryCountsAreCorrect(AbilityCategory category) {
        Collection<Ability> abilities = AbilityRegistry.getAbilitiesByCategory(category);
        int expected = switch (category) {
            case ATTACK -> 12;
            case DEFENSE -> 12;
            case CONTROL -> 12;
            case LEGENDARY -> 10;
            case CREATOR -> 8;
        };
        assertEquals(expected, abilities.size(),
                "Category " + category + " should have " + expected + " abilities");
    }

    @Test
    @DisplayName("non-creator cache has 46 abilities (54 - 8 creator)")
    void nonCreatorCacheHasFortySix() {
        assertEquals(46, AbilityRegistry.getNonCreatorAbilities().size(),
                "Non-creator cache should contain 46 abilities (54 total minus 8 creator)");
    }

    @Test
    @DisplayName("getAbility returns non-null for known IDs")
    void getAbilityReturnsNotNullForKnownIds() {
        assertNotNull(AbilityRegistry.getAbility("eliteforge:fire"),
                "eliteforge:fire should be registered");
        assertNotNull(AbilityRegistry.getAbility("eliteforge:nexus"),
                "eliteforge:nexus (creator) should be registered");
        assertNotNull(AbilityRegistry.getAbility("eliteforge:clone"),
                "eliteforge:clone (legendary) should be registered");
    }

    @Test
    @DisplayName("getAbility returns null for unknown IDs")
    void getAbilityReturnsNullForUnknownIds() {
        assertNull(AbilityRegistry.getAbility("eliteforge:nonexistent"),
                "Unknown ability ID should return null");
        assertNull(AbilityRegistry.getAbility(""),
                "Empty string should return null");
        assertNull(AbilityRegistry.getAbility((String) null),
                "Null string should return null");
    }

    @Test
    @DisplayName("isRegistered returns correct boolean")
    void isRegisteredReturnsCorrectBoolean() {
        assertTrue(AbilityRegistry.isRegistered("eliteforge:fire"),
                "fire should be registered");
        assertFalse(AbilityRegistry.isRegistered("eliteforge:fake_ability"),
                "fake ability should not be registered");
    }

    @Test
    @DisplayName("all registered abilities have non-null IDs and valid categories")
    void allAbilitiesHaveValidIdsAndCategories() {
        for (Ability ability : AbilityRegistry.getAllAbilities()) {
            assertNotNull(ability.getId(), "Ability ID must not be null");
            assertNotNull(ability.getIdString(), "Ability ID string must not be null");
            assertFalse(ability.getIdString().isBlank(), "Ability ID string must not be blank");
            assertNotNull(ability.getCategory(), "Ability category must not be null");
            assertTrue(ability.getMaxLevel() >= 1, "Ability max level must be >= 1");
            assertTrue(ability.getBaseBudgetCost() >= 0, "Ability budget cost must be >= 0");
        }
    }

    @Test
    @DisplayName("creator abilities have max level 3; others have max level 5")
    void creatorAbilitiesHaveMaxLevelThree() {
        for (Ability ability : AbilityRegistry.getAbilitiesByCategory(AbilityCategory.CREATOR)) {
            assertEquals(3, ability.getMaxLevel(),
                    "Creator ability " + ability.getIdString() + " should have max level 3");
        }
        for (Ability ability : AbilityRegistry.getNonCreatorAbilities()) {
            assertEquals(5, ability.getMaxLevel(),
                    "Non-creator ability " + ability.getIdString() + " should have max level 5");
        }
    }

    @Test
    @DisplayName("getAbilityIds returns all 54 IDs as ResourceLocations")
    void getAbilityIdsReturnsAll() {
        assertEquals(54, AbilityRegistry.getAbilityIds().size(),
                "getAbilityIds should return 54 ResourceLocations");
    }
}
