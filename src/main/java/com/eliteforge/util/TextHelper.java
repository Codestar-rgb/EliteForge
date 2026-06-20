package com.eliteforge.util;

import com.eliteforge.ability.Ability;
import com.eliteforge.ability.AbilityCategory;
import com.eliteforge.quality.QualityTier;
// SetBonus removed in v0.2.0
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Text formatting utilities for EliteForge.
 * Provides consistent formatting for ability names, elite names,
 * quality tiers, and set bonus names across the mod.
 */
public final class TextHelper {

    private TextHelper() {
        // Utility class, no instantiation
    }

    // ========================================================================
    // Ability Name Formatting
    // ========================================================================

    /**
     * Format an ability name with its level as a colored component.
     * The ability name uses the ability's category color,
     * and the level is displayed as a Roman numeral.
     *
     * @param ability The ability to format
     * @param level   The ability level (1-5+)
     * @return A colored MutableComponent with the ability name and level
     */
    public static MutableComponent abilityName(Ability ability, int level) {
        ChatFormatting categoryColor = getCategoryColor(ability.getCategory());
        return ability.getDisplayName(1)
                .copy()
                .withStyle(categoryColor)
                .append(Component.literal(LevelRoman.formatSuffix(level))
                        .withStyle(ChatFormatting.GRAY));
    }

    /**
     * Get the ChatFormatting color for an ability category.
     */
    private static ChatFormatting getCategoryColor(AbilityCategory category) {
        return switch (category) {
            case ATTACK -> ChatFormatting.RED;
            case DEFENSE -> ChatFormatting.GREEN;
            case CONTROL -> ChatFormatting.BLUE;
            case LEGENDARY -> ChatFormatting.GOLD;
            case CREATOR -> ChatFormatting.DARK_RED;
        };
    }

    // ========================================================================
    // Elite Name Formatting
    // ========================================================================

    /**
     * Format an elite mob's display name with quality tier indicator.
     * Uses gold formatting for the title with a level indicator.
     *
     * @param baseName The base entity name component
     * @param level    The elite level
     * @return A formatted MutableComponent with gold title and level
     */
    public static MutableComponent eliteName(Component baseName, int level) {
        MutableComponent eliteTitle = Component.translatable("display.eliteforge.elite_prefix")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);

        eliteTitle.append(baseName.copy().withStyle(ChatFormatting.GOLD));

        if (level > 1) {
            eliteTitle.append(Component.literal(" Lv." + level)
                    .withStyle(ChatFormatting.YELLOW));
        }

        return eliteTitle;
    }

    /**
     * Format an elite mob's display name with quality tier and level.
     *
     * @param baseName The base entity name component
     * @param level    The elite level
     * @param tier     The quality tier
     * @return A formatted MutableComponent with quality-colored title and level
     */
    public static MutableComponent eliteName(Component baseName, int level, QualityTier tier) {
        ChatFormatting tierColor = getQualityColor(tier);
        MutableComponent eliteTitle = Component.translatable("display.eliteforge.elite_prefix")
                .withStyle(tierColor, ChatFormatting.BOLD);

        eliteTitle.append(baseName.copy().withStyle(tierColor));

        if (level > 1) {
            eliteTitle.append(Component.literal(" Lv." + level)
                    .withStyle(ChatFormatting.YELLOW));
        }

        return eliteTitle;
    }

    // ========================================================================
    // Quality Tier Formatting
    // ========================================================================

    /**
     * Format a quality tier name as a colored component.
     * Each tier has a distinct color:
     * - Normal: White
     * - Good: Green
     * - Fine: Aqua
     * - Epic: Light Purple
     * - Legendary: Gold
     * - Mythic: Dark Red (creator-tier only)
     *
     * @param tier The quality tier to format
     * @return A colored MutableComponent with the tier name
     */
    public static MutableComponent qualityName(QualityTier tier) {
        ChatFormatting color = getQualityColor(tier);
        return Component.translatable("quality.eliteforge." + tier.name().toLowerCase())
                .withStyle(color, ChatFormatting.BOLD);
    }

    /**
     * Get the ChatFormatting color for a quality tier.
     */
    private static ChatFormatting getQualityColor(QualityTier tier) {
        return switch (tier) {
            case NORMAL -> ChatFormatting.WHITE;
            case GOOD -> ChatFormatting.GREEN;
            case FINE -> ChatFormatting.AQUA;
            case EPIC -> ChatFormatting.LIGHT_PURPLE;
            case LEGENDARY -> ChatFormatting.GOLD;
            case MYTHIC -> ChatFormatting.DARK_RED;
        };
    }

    // ========================================================================
    // Budget Display Formatting
    // ========================================================================

    /**
     * Format a budget value for display in debug/UI screens.
     *
     * @param budgetName The name of the budget category
     * @param used       Budget points used
     * @param total      Total budget points available
     * @return A formatted MutableComponent with budget info
     */
    public static MutableComponent budgetDisplay(String budgetName, double used, double total) {
        ChatFormatting color = used >= total ? ChatFormatting.RED : ChatFormatting.GREEN;
        return Component.literal(budgetName + ": ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(String.format("%.1f/%.1f", used, total))
                        .withStyle(color));
    }
}
