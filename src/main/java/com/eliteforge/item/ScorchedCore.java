package com.eliteforge.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * ScorchedCore (焦土核心) - Found in C6 Annihilate explosion area (drops from ground after explosion).
 * Still burning with destructive energy.
 * Crafting material for explosive enchantments.
 * Stackable to 16, has enchantment glow effect with red styling.
 */
public class ScorchedCore extends Item {

    public ScorchedCore() {
        super(new Item.Properties().stacksTo(16));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.eliteforge.scorched_core.1")
            .withStyle(ChatFormatting.RED));
        tooltip.add(Component.translatable("item.eliteforge.scorched_core.tooltip")
            .withStyle(ChatFormatting.RED));
        tooltip.add(Component.translatable("tooltip.eliteforge.scorched_core.2")
            .withStyle(ChatFormatting.DARK_RED));
    }

    @Override
    public boolean isEnchantable(ItemStack stack) {
        return false;
    }
}
