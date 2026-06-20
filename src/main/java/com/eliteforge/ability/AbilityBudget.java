package com.eliteforge.ability;

import com.eliteforge.config.DifficultyMode;

// Note: DifficultyMode is imported from config package, NOT defined as an inner enum.

/**
 * Calculates ability budgets based on entity level and difficulty mode.
 * 
 * The five-category budget system allocates separate budgets for attack,
 * defense, control, legendary, and creator abilities. There are 54 abilities
 * in total (12 attack + 12 defense + 12 control + 10 legendary + 8 creator).
 * The difficulty mode scales how aggressively budgets grow with level.
 * 
 * Phase 6 optimizations:
 * - FORGE mode: Higher budgets, allows more powerful combinations
 * - CASUAL mode: Lower budgets, simpler combinations, max 2 abilities, no legendary
 * - MIXED mode: Variable budgets based on roll
 * - Creator budget: unlocks at level 5+ (except CASUAL mode), enabling powerful creative abilities
 * - Minimum ability guarantee: each elite gets at least 1 ability from a non-legendary category
 * - Level-scaling: budgets increase more aggressively at higher levels
 * 
 * Creator budget system:
 * - Creator abilities are the rarest and most powerful tier, each costing 5.0 budget
 * - Base creator budget is 0.0 at level 1, growing by 0.5 per level
 * - Only entities at level 5+ in non-CASUAL modes can afford a creator ability
 * - CASUAL mode zeroes out creator budget (creatorBudget = 0) to ensure
 *   creator-tier elites never spawn in casual difficulty
 * - The creator budget is intentionally separate from other categories to prevent
 *   normal ability budgets from being consumed by creator abilities
 */
public final class AbilityBudget {

    private AbilityBudget() {
        // Utility class - no instantiation
    }

    /**
     * Holds the budget values for each ability category.
     */
    public static class BudgetData {
        private final float attackBudget;
        private final float defenseBudget;
        private final float controlBudget;
        private final float legendaryBudget;

        private final float creatorBudget;

        public BudgetData(float attackBudget, float defenseBudget, float controlBudget, float legendaryBudget, float creatorBudget) {
            this.attackBudget = attackBudget;
            this.defenseBudget = defenseBudget;
            this.controlBudget = controlBudget;
            this.legendaryBudget = legendaryBudget;
            this.creatorBudget = creatorBudget;
        }

        public float getAttackBudget() { return attackBudget; }
        public float getDefenseBudget() { return defenseBudget; }
        public float getControlBudget() { return controlBudget; }
        public float getLegendaryBudget() { return legendaryBudget; }

        public float getCreatorBudget() { return creatorBudget; }

        public float getBudgetForCategory(AbilityCategory category) {
            return switch (category) {
                case ATTACK -> attackBudget;
                case DEFENSE -> defenseBudget;
                case CONTROL -> controlBudget;
                case LEGENDARY -> legendaryBudget;
                case CREATOR -> creatorBudget;
            };
        }

        @Override
        public String toString() {
            return String.format("BudgetData{attack=%.1f, defense=%.1f, control=%.1f, legendary=%.1f, creator=%.1f}",
                    attackBudget, defenseBudget, controlBudget, legendaryBudget, creatorBudget);
        }
    }

    // DifficultyMode is consolidated in com.eliteforge.config.DifficultyMode.
    // It provides getBudgetMultiplier(), getLevelMultiplier(), getMaxAbilitiesBase().

    // Base budget values at level 1
    private static final float BASE_ATTACK_BUDGET = 3.0f;
    private static final float BASE_DEFENSE_BUDGET = 3.0f;
    private static final float BASE_CONTROL_BUDGET = 2.5f;
    private static final float BASE_LEGENDARY_BUDGET = 1.0f;

    private static final float BASE_CREATOR_BUDGET = 0.0f;
    private static final float CREATOR_PER_LEVEL = 0.5f;

    // Budget gained per level - more aggressive scaling for higher levels
    private static final float ATTACK_PER_LEVEL = 2.5f;
    private static final float DEFENSE_PER_LEVEL = 2.5f;
    private static final float CONTROL_PER_LEVEL = 2.0f;
    private static final float LEGENDARY_PER_LEVEL = 0.8f;

    // Extra budget per level at level 4+ (accelerated scaling)
    private static final float HIGH_LEVEL_BONUS = 1.5f;

    // CASUAL mode caps
    private static final float CASUAL_ATTACK_CAP = 10.0f;
    private static final float CASUAL_DEFENSE_CAP = 10.0f;
    private static final float CASUAL_CONTROL_CAP = 8.0f;
    private static final float CASUAL_LEGENDARY_CAP = 0.0f; // No legendary in CASUAL

    /**
     * Calculates the budget allocation for a given level and difficulty mode.
     * 
     * Formula: (baseBudget + level * budgetPerLevel + highLevelBonus) * modeMultiplier
     * CASUAL mode additionally caps the total budget and sets legendary to 0.
     *
     * @param level The target level (1-10+)
     * @param mode  The difficulty mode
     * @return BudgetData with allocated budgets for each category
     */
    public static BudgetData calculateBudgets(int level, DifficultyMode mode) {
        // Scale level from 1-1500 to internal tier 1-10 for budget calculation
        int maxLevel = com.eliteforge.config.EliteForgeConfig.COMMON.maxEliteLevel.get();
        int tier = Math.max(1, Math.min(10, (int)Math.ceil(level * 10.0 / maxLevel)));
        if (tier < 1) tier = 1;
        // Use tier for budget calculation instead of raw level

        float modeMult = mode.getBudgetMultiplier();
        // Use tier (1-10) instead of raw level for budget scaling

        // High-level bonus: additional budget for levels 4+
        float highLevelBonus = tier >= 4 ? (level - 3) * HIGH_LEVEL_BONUS : 0;

        float attackBudget = (BASE_ATTACK_BUDGET + level * ATTACK_PER_LEVEL + highLevelBonus) * modeMult;
        float defenseBudget = (BASE_DEFENSE_BUDGET + level * DEFENSE_PER_LEVEL + highLevelBonus) * modeMult;
        float controlBudget = (BASE_CONTROL_BUDGET + level * CONTROL_PER_LEVEL + highLevelBonus * 0.8f) * modeMult;
        float legendaryBudget = (BASE_LEGENDARY_BUDGET + level * LEGENDARY_PER_LEVEL + highLevelBonus * 0.5f) * modeMult;

        float creatorBudget = (BASE_CREATOR_BUDGET + level * CREATOR_PER_LEVEL) * modeMult;

        // Creator budget is 0 unless entity tier >= 5 and mode is not CASUAL
        if (tier < 5 || mode == DifficultyMode.CASUAL) {
            creatorBudget = 0;
        }

        // Apply caps for CASUAL mode
        if (mode == DifficultyMode.CASUAL) {
            attackBudget = Math.min(attackBudget, CASUAL_ATTACK_CAP);
            defenseBudget = Math.min(defenseBudget, CASUAL_DEFENSE_CAP);
            controlBudget = Math.min(controlBudget, CASUAL_CONTROL_CAP);
            // CASUAL mode never gets legendary abilities
            legendaryBudget = CASUAL_LEGENDARY_CAP;
        }

        // Legendary budget is always more restricted — no legendary below level 3
        if (tier < 3) {
            legendaryBudget = 0;
        }

        return new BudgetData(attackBudget, defenseBudget, controlBudget, legendaryBudget, creatorBudget);
    }

    /**
     * Gets the maximum number of abilities an elite can have at a given level and mode.
     *
     * @param level The entity level
     * @param mode  The difficulty mode
     * @return Maximum number of abilities
     */
    public static int getMaxAbilities(int level, DifficultyMode mode) {
        int base = mode.getMaxAbilitiesBase();
        // Scale level to internal tier (1-10) so ability counts don't explode at high levels.
        int maxLevel = com.eliteforge.config.EliteForgeConfig.COMMON.maxEliteLevel.get();
        int tier = Math.max(1, Math.min(10, (int)Math.ceil(level * 10.0 / maxLevel)));
        // Add one ability per 2 levels in FORGE, per 3 levels in MIXED, per 4 levels in CASUAL
        int extra = switch (mode) {
            case FORGE -> tier / 2;
            case MIXED -> tier / 3;
            case CASUAL -> tier / 4;
        };
        // CASUAL mode hard cap of 2 abilities
        if (mode == DifficultyMode.CASUAL) {
            return Math.min(2, base + extra);
        }
        return base + extra;
    }

    /**
     * Gets the maximum level an ability can be assigned at a given entity level and mode.
     *
     * @param entityLevel The entity's level
     * @param mode        The difficulty mode
     * @return Maximum ability level (1-5)
     */
    public static int getMaxAbilityLevel(int entityLevel, DifficultyMode mode) {
        // Scale ability max level: 1-299=I, 300-599=II, 600-899=III, 900-1199=IV, 1200+=V
        int maxLevel = 1 + Math.floorDiv(entityLevel, 300);
        maxLevel = Math.min(maxLevel, 5);
        return Math.max(1, maxLevel);
    }

    /**
     * Check if a difficulty mode allows legendary abilities.
     *
     * @param mode the difficulty mode
     * @return true if legendary abilities can be assigned
     */
    public static boolean allowsLegendaryAbilities(DifficultyMode mode) {
        return mode != DifficultyMode.CASUAL;
    }

    /**
     * Check if a difficulty mode allows creator abilities.
     * This only checks the mode restriction; it does NOT check the entity level
     * requirement (level 5+). Use {@link #allowsCreatorAbilities(DifficultyMode, int)}
     * for a complete check that includes both restrictions.
     *
     * @param mode the difficulty mode
     * @return true if the mode permits creator abilities (i.e., not CASUAL)
     */
    public static boolean allowsCreatorAbilities(DifficultyMode mode) {
        return mode != DifficultyMode.CASUAL;
    }

    /**
     * Check if creator abilities are allowed for the given mode and entity level.
     * Both conditions must be met: mode must not be CASUAL, and entity level
     * must be at least 5. This matches the logic in {@link #calculateBudgets},
     * which zeroes out creatorBudget when either condition fails.
     *
     * @param mode  the difficulty mode
     * @param level the entity level
     * @return true if creator abilities can be assigned
     */
    public static boolean allowsCreatorAbilities(DifficultyMode mode, int level) {
        // Creator abilities require tier >= 5 (i.e. level in the top half of the 1-10 tier scale).
        int maxLevel = com.eliteforge.config.EliteForgeConfig.COMMON.maxEliteLevel.get();
        int tier = Math.max(1, Math.min(10, (int)Math.ceil(level * 10.0 / maxLevel)));
        return mode != DifficultyMode.CASUAL && tier >= 5;
    }

    /**
     * Get the minimum number of guaranteed abilities from non-legendary categories.
     * Each elite gets at least 1 ability from a non-legendary category.
     *
     * @return minimum guaranteed non-legendary abilities (always 1)
     */
    public static int getMinimumAbilityGuarantee() {
        return 1;
    }
}
