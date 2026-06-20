package com.eliteforge.spawn;

import com.eliteforge.ability.Ability;
import com.eliteforge.ability.AbilityBudget;
import com.eliteforge.ability.AbilityCategory;
import com.eliteforge.ability.AbilityRegistry;
import com.eliteforge.ability.MutualExclusion;
import com.eliteforge.config.DifficultyMode;
import com.eliteforge.config.EliteForgeConfig;
import com.eliteforge.datapack.EntityPreset;
import com.eliteforge.datapack.EntityPresetLoader;
import com.mojang.datafixers.util.Pair;
import net.minecraft.world.entity.EntityType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * Dual-budget ability generation system. Generates a list of abilities for
 * elite mobs based on their target difficulty level, difficulty mode, and
 * entity type. Uses a budget system to ensure balanced ability distribution.
 * <p>
 * Algorithm:
 * 1. Calculate budgets from AbilityBudget based on target level
 * 2. Get available abilities (not blacklisted for entity type)
 * 3. Filter by mutual exclusion
 * 4. Randomly select abilities within budget:
 *    a. Start with mandatory abilities for entity type (from entity preset)
 *    b. Fill remaining budget from each category
 *    c. Ensure at least 1 ability from each non-empty category
 *    d. Level each ability: random 1 to min(targetLevel, ability.maxLevel)
 *    e. Total ability count must not exceed config max
 * 5. Validate no mutual exclusion conflicts
 * 6. Return list
 */
public class AbilityGenerator {

    private static final Logger LOGGER = LogManager.getLogger();

    /**
     * Non-CREATOR ability categories, used for iteration to avoid checking and
     * skipping CREATOR every time. CREATOR abilities are assigned separately
     * via the creator conversion path.
     */
    private static final AbilityCategory[] NON_CREATOR_CATEGORIES =
            Arrays.stream(AbilityCategory.values())
                    .filter(c -> c != AbilityCategory.CREATOR)
                    .toArray(AbilityCategory[]::new);

    /**
     * Generate a list of abilities for an elite mob.
     *
     * @param targetLevel the target difficulty level (1-5)
     * @param mode        the difficulty mode
     * @param entityType  the entity type of the mob
     * @return list of ability-level pairs
     */
    public static List<Pair<Ability, Integer>> generateAbilities(int targetLevel, DifficultyMode mode, EntityType<?> entityType) {
        List<Pair<Ability, Integer>> result = new ArrayList<>();

        // 1. Calculate budgets from AbilityBudget
        AbilityBudget.BudgetData budget = AbilityBudget.calculateBudgets(targetLevel, mode);
        int maxAbilities = Math.min(
                EliteForgeConfig.COMMON.maxAbilitiesPerElite.get(),
                AbilityBudget.getMaxAbilities(targetLevel, mode)
        );

        // 2. Get available abilities, filtered by entity preset blacklist.
        // P5: Use the cached non-creator ability list from AbilityRegistry instead
        // of streaming AbilityRegistry.getAllAbilities() and filtering CREATOR every spawn.
        EntityPreset preset = EntityPresetLoader.getInstance().getPreset(entityType);
        Set<String> abilityBlacklist = preset != null ? preset.getAbilityBlacklist() : Collections.emptySet();

        List<Ability> allAvailable = AbilityRegistry.getNonCreatorAbilities().stream()
                .filter(ability -> !abilityBlacklist.contains(ability.getIdString()))
                .filter(Ability::isEnabled)
                .filter(ability -> AbilityBudget.allowsLegendaryAbilities(mode) || ability.getCategory() != AbilityCategory.LEGENDARY)
                .collect(Collectors.toList());

        if (allAvailable.isEmpty()) {
            LOGGER.warn("No available abilities for entity type {}", EntityType.getKey(entityType));
            return result;
        }

        // 3. Group available abilities by category (excluding CREATOR, which is
        //    assigned separately via the creator conversion path)
        Map<AbilityCategory, List<Ability>> abilitiesByCategory = new EnumMap<>(AbilityCategory.class);
        for (AbilityCategory category : NON_CREATOR_CATEGORIES) {
            List<Ability> categoryAbilities = allAvailable.stream()
                    .filter(a -> a.getCategory() == category)
                    .collect(Collectors.toList());
            if (!categoryAbilities.isEmpty()) {
                abilitiesByCategory.put(category, categoryAbilities);
            }
        }

        // Track selected abilities for mutual exclusion checking
        Set<String> selectedIds = new HashSet<>();

        // Running budget tracker: avoids O(n) stream re-scan of result list
        // for every candidate ability. Updated whenever an ability is added.
        Map<AbilityCategory, Float> categorySpendTracker = new EnumMap<>(AbilityCategory.class);
        // Running count tracker: avoids stream-based count/existence checks.
        Map<AbilityCategory, Integer> categoryCountTracker = new EnumMap<>(AbilityCategory.class);

        // 4a. Start with mandatory/forced abilities for entity type (from entity preset)
        if (preset != null) {
            for (Map.Entry<String, Integer> forcedEntry : preset.getForcedAbilities().entrySet()) {
                Ability ability = AbilityRegistry.getAbility(forcedEntry.getKey());
                if (ability != null && allAvailable.contains(ability)) {
                    // Check mutual exclusion with already-selected abilities
                    boolean canAdd = true;
                    for (String selectedId : selectedIds) {
                        if (MutualExclusion.isMutuallyExclusive(ability.getIdString(), selectedId)) {
                            canAdd = false;
                            break;
                        }
                    }
                    if (canAdd) {
                        int level = Math.min(forcedEntry.getValue(), ability.getMaxLevel());
                        result.add(Pair.of(ability, level));
                        selectedIds.add(ability.getIdString());
                        float cost = ability.getBudgetCost(level);
                        categorySpendTracker.merge(ability.getCategory(), cost, Float::sum);
                        categoryCountTracker.merge(ability.getCategory(), 1, Integer::sum);
                    }
                }
            }
        }

        // 4b. Fill remaining budget from each category, ensuring at least 1 per non-legendary category
        int remainingBudget = maxAbilities - result.size();
        if (remainingBudget <= 0) {
            return validateAndReturn(result);
        }

        // Ensure at least 1 ability from a non-legendary category (minimum guarantee)
        // Use categoryCountTracker for O(1) check instead of streaming result list
        boolean hasNonLegendaryAbility = false;
        for (Map.Entry<AbilityCategory, Integer> entry : categoryCountTracker.entrySet()) {
            if (entry.getKey() != AbilityCategory.LEGENDARY && entry.getValue() > 0) {
                hasNonLegendaryAbility = true;
                break;
            }
        }
        
        if (!hasNonLegendaryAbility) {
            // Pick one ability from a non-legendary category
            for (AbilityCategory category : new AbilityCategory[]{AbilityCategory.ATTACK, AbilityCategory.DEFENSE, AbilityCategory.CONTROL}) {
                List<Ability> categoryAbilities = abilitiesByCategory.getOrDefault(category, List.of()).stream()
                        .filter(a -> !selectedIds.contains(a.getIdString()))
                        .filter(a -> !isMutuallyExclusiveWithAny(a, selectedIds))
                        .collect(Collectors.toList());
                
                if (!categoryAbilities.isEmpty()) {
                    Ability chosen = weightedRandomSelect(categoryAbilities);
                    int level = randomAbilityLevel(targetLevel, chosen);
                    result.add(Pair.of(chosen, level));
                    selectedIds.add(chosen.getIdString());
                    remainingBudget--;
                    float cost = chosen.getBudgetCost(level);
                    categorySpendTracker.merge(chosen.getCategory(), cost, Float::sum);
                    categoryCountTracker.merge(chosen.getCategory(), 1, Integer::sum);
                    break;
                }
            }
        }

        // Ensure at least 1 ability from each non-empty category
        List<AbilityCategory> categories = new ArrayList<>(abilitiesByCategory.keySet());
        Collections.shuffle(categories);

        for (AbilityCategory category : categories) {
            if (remainingBudget <= 0) break;

            List<Ability> categoryAbilities = abilitiesByCategory.get(category).stream()
                    .filter(a -> !selectedIds.contains(a.getIdString()))
                    .filter(a -> !isMutuallyExclusiveWithAny(a, selectedIds))
                    .collect(Collectors.toList());

            if (categoryAbilities.isEmpty()) continue;

            // Check category budget (O(1) via tracker instead of O(n) stream)
            float categoryBudget = budget.getBudgetForCategory(category);
            float currentCategorySpend = categorySpendTracker.getOrDefault(category, 0f);

            if (currentCategorySpend >= categoryBudget) continue;

            // Ensure at least 1 ability from this category (O(1) via tracker)
            boolean hasCategoryAbility = categoryCountTracker.getOrDefault(category, 0) > 0;

            if (!hasCategoryAbility && remainingBudget > 0) {
                Ability chosen = weightedRandomSelect(categoryAbilities);
                int level = randomAbilityLevel(targetLevel, chosen);
                float cost = chosen.getBudgetCost(level);
                if (currentCategorySpend + cost <= categoryBudget || categoryBudget <= 0) {
                    result.add(Pair.of(chosen, level));
                    selectedIds.add(chosen.getIdString());
                    remainingBudget--;
                    categorySpendTracker.merge(chosen.getCategory(), cost, Float::sum);
                    categoryCountTracker.merge(chosen.getCategory(), 1, Integer::sum);
                }
            }
        }

        // 4c. Fill remaining budget with random abilities from any category
        List<Ability> remainingCandidates = allAvailable.stream()
                .filter(a -> !selectedIds.contains(a.getIdString()))
                .filter(a -> !isMutuallyExclusiveWithAny(a, selectedIds))
                .collect(Collectors.toList());

        Collections.shuffle(remainingCandidates);

        // Prioritize abilities from under-represented categories
        // Uses categoryCountTracker for O(1) per-comparison instead of O(n) stream
        remainingCandidates.sort((a, b) -> {
            int countA = categoryCountTracker.getOrDefault(a.getCategory(), 0);
            int countB = categoryCountTracker.getOrDefault(b.getCategory(), 0);
            return Integer.compare(countA, countB);
        });

        for (Ability candidate : remainingCandidates) {
            if (remainingBudget <= 0) break;

            // Check category budget (O(1) via tracker instead of O(n) stream)
            AbilityCategory cat = candidate.getCategory();
            float catBudget = budget.getBudgetForCategory(cat);
            float currentCatSpend = categorySpendTracker.getOrDefault(cat, 0f);

            if (catBudget > 0 && currentCatSpend >= catBudget) continue;

            int level = randomAbilityLevel(targetLevel, candidate);
            float cost = candidate.getBudgetCost(level);

            if (catBudget <= 0 || currentCatSpend + cost <= catBudget) {
                result.add(Pair.of(candidate, level));
                selectedIds.add(candidate.getIdString());
                remainingBudget--;
                categorySpendTracker.merge(cat, cost, Float::sum);
                categoryCountTracker.merge(cat, 1, Integer::sum);
            }
        }

        // 5. Validate no mutual exclusion conflicts
        return validateAndReturn(result);
    }

    /**
     * Check if an ability is mutually exclusive with any of the selected abilities.
     *
     * @param ability     the ability to check
     * @param selectedIds the set of already-selected ability IDs
     * @return true if mutually exclusive with any selected ability
     */
    private static boolean isMutuallyExclusiveWithAny(Ability ability, Set<String> selectedIds) {
        for (String selectedId : selectedIds) {
            if (MutualExclusion.isMutuallyExclusive(ability.getIdString(), selectedId)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Generate a random ability level between 1 and the minimum of the target
     * level and the ability's max level.
     *
     * @param targetLevel the target difficulty level
     * @param ability     the ability
     * @return the random ability level
     */
    private static int randomAbilityLevel(int targetLevel, Ability ability) {
        int maxLevel = Math.min(targetLevel, ability.getMaxLevel());
        if (maxLevel <= 1) return 1;
        return 1 + ThreadLocalRandom.current().nextInt(maxLevel);
    }

    /**
     * Select an ability using weighted random selection based on ability weights.
     *
     * @param abilities the list of abilities to choose from
     * @return the selected ability
     */
    private static Ability weightedRandomSelect(List<Ability> abilities) {
        if (abilities.size() == 1) {
            return abilities.get(0);
        }

        double totalWeight = abilities.stream()
                .mapToDouble(Ability::getWeight)
                .sum();

        if (totalWeight <= 0) {
            return abilities.get(ThreadLocalRandom.current().nextInt(abilities.size()));
        }

        double roll = ThreadLocalRandom.current().nextDouble() * totalWeight;
        double currentSum = 0;

        for (Ability ability : abilities) {
            currentSum += ability.getWeight();
            if (roll <= currentSum) {
                return ability;
            }
        }

        return abilities.get(abilities.size() - 1);
    }

    /**
     * Validate that the result has no mutual exclusion conflicts and return it.
     * If conflicts are found, remove the later-added ability.
     *
     * @param abilities the list of ability-level pairs to validate
     * @return the validated list
     */
    private static List<Pair<Ability, Integer>> validateAndReturn(List<Pair<Ability, Integer>> abilities) {
        Set<String> seen = new HashSet<>();
        List<Pair<Ability, Integer>> validated = new ArrayList<>();

        for (Pair<Ability, Integer> pair : abilities) {
            Ability ability = pair.getFirst();

            // Check for mutual exclusion conflicts with already-selected abilities
            boolean hasConflict = false;
            for (String seenId : seen) {
                if (MutualExclusion.isMutuallyExclusive(ability.getIdString(), seenId)) {
                    hasConflict = true;
                    break;
                }
            }

            // Check for duplicate
            if (seen.contains(ability.getIdString())) {
                hasConflict = true;
            }

            if (!hasConflict) {
                validated.add(pair);
                seen.add(ability.getIdString());
            } else {
                LOGGER.debug("Removed conflicting ability: {}", ability.getIdString());
            }
        }

        LOGGER.debug("Generated {} abilities for elite mob", validated.size());
        return validated;
    }
}
