package com.eliteforge.ability;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Defines the five categories of abilities in EliteForge: ATTACK, DEFENSE, CONTROL,
 * LEGENDARY, and CREATOR.
 * Each category has a translation key for localization, a chat color for visual distinction,
 * and a budget key used in the dual-budget allocation system.
 */
public enum AbilityCategory {

    ATTACK("ability.category.eliteforge.attack", ChatFormatting.RED, "attack"),
    DEFENSE("ability.category.eliteforge.defense", ChatFormatting.AQUA, "defense"),
    CONTROL("ability.category.eliteforge.control", ChatFormatting.GOLD, "control"),
    LEGENDARY("ability.category.eliteforge.legendary", ChatFormatting.LIGHT_PURPLE, "legendary"),
    CREATOR("ability.category.eliteforge.creator", ChatFormatting.DARK_RED, "creator");

    private final String translationKey;
    private final ChatFormatting chatColor;
    private final String budgetKey;

    AbilityCategory(String translationKey, ChatFormatting chatColor, String budgetKey) {
        this.translationKey = translationKey;
        this.chatColor = chatColor;
        this.budgetKey = budgetKey;
    }

    /**
     * Returns the translation key for this category, to be resolved via
     * the Minecraft localization system (e.g. lang JSON files).
     *
     * @return the translation key (e.g. "ability.category.eliteforge.attack")
     */
    public String getTranslationKey() {
        return translationKey;
    }

    public ChatFormatting getChatColor() {
        return chatColor;
    }

    public String getBudgetKey() {
        return budgetKey;
    }

    /**
     * Creates a colored, translatable display name component for this category.
     */
    public MutableComponent getDisplayNameComponent() {
        return Component.translatable(translationKey).withStyle(chatColor);
    }
}
