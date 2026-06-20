package com.eliteforge.config;

/**
 * Difficulty modes for EliteForge.
 * Each mode controls how elite mobs spawn and behave.
 *
 * FORGE - Hardcore mode: higher spawn rates, more abilities, stronger elites.
 *         Designed for players who want a challenging experience.
 *
 * CASUAL - Relaxed mode: lower spawn rates, fewer abilities, weaker elites.
 *          Designed for players who want to enjoy the forging system without
 *          overwhelming difficulty.
 *
 * MIXED - Hybrid mode: combines both approaches. Forge-mode elites spawn
 *         less frequently but are very powerful, while casual-mode elites
 *         are more common but weaker. Creates a varied experience.
 */
public enum DifficultyMode {

    FORGE("forge", "EliteForge Difficulty: Forge",
            "Hardcore mode with high spawn rates and powerful elites. For veteran players.",
            1.5f, 2.0f, 3),
    CASUAL("casual", "EliteForge Difficulty: Casual",
            "Relaxed mode with lower spawn rates and weaker elites. For casual play.",
            0.7f, 1.0f, 2),
    MIXED("mixed", "EliteForge Difficulty: Mixed",
            "Hybrid mode with rare powerful elites and common weaker ones. Balanced experience.",
            1.1f, 1.5f, 3);

    private final String id;
    private final String displayName;
    private final String description;

    // Budget scaling fields (moved from AbilityBudget.DifficultyMode)
    private final float budgetMultiplier;
    private final float levelMultiplier;
    private final int maxAbilitiesBase;

    DifficultyMode(String id, String displayName, String description,
                   float budgetMultiplier, float levelMultiplier, int maxAbilitiesBase) {
        this.id = id;
        this.displayName = displayName;
        this.description = description;
        this.budgetMultiplier = budgetMultiplier;
        this.levelMultiplier = levelMultiplier;
        this.maxAbilitiesBase = maxAbilitiesBase;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * Get the translation key for this difficulty mode's display name.
     * Use with Component.translatable() for proper i18n in player-facing messages.
     *
     * @return the translation key (e.g., "difficulty.eliteforge.forge")
     */
    public String getDisplayNameKey() {
        return "difficulty.eliteforge." + id;
    }

    /**
     * Get the translation key for this difficulty mode's description.
     *
     * @return the translation key (e.g., "difficulty.eliteforge.forge.description")
     */
    public String getDescriptionKey() {
        return "difficulty.eliteforge." + id + ".description";
    }

    public String getDescription() {
        return description;
    }

    /**
     * Budget multiplier for this difficulty mode.
     * Higher = more budget for abilities per level.
     */
    public float getBudgetMultiplier() {
        return budgetMultiplier;
    }

    /**
     * Level multiplier for this difficulty mode.
     * Higher = ability levels can be higher.
     */
    public float getLevelMultiplier() {
        return levelMultiplier;
    }

    /**
     * Base maximum number of abilities for this difficulty mode.
     * Additional abilities are added based on entity level.
     */
    public int getMaxAbilitiesBase() {
        return maxAbilitiesBase;
    }

    /**
     * Get a DifficultyMode by its string ID.
     *
     * @param id The mode ID string
     * @return The matching DifficultyMode, or FORGE if not found
     */
    public static DifficultyMode byId(String id) {
        for (DifficultyMode mode : values()) {
            if (mode.id.equalsIgnoreCase(id)) {
                return mode;
            }
        }
        return FORGE;
    }

    /**
     * Get the spawn chance for this difficulty mode based on config values.
     */
    public double getSpawnChance() {
        return switch (this) {
            case FORGE -> EliteForgeConfig.COMMON.forgeModeSpawnChance.get();
            case CASUAL -> EliteForgeConfig.COMMON.casualModeSpawnChance.get();
            case MIXED -> EliteForgeConfig.COMMON.mixedModeForgeChance.get() +
                          EliteForgeConfig.COMMON.mixedModeCasualChance.get();
        };
    }
}
