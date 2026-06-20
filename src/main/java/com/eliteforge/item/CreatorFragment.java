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
 * CreatorFragment (造物主残片) - Rare drop from killing creator-tier entities (10% chance).
 * A fragment of immense power, radiating dark energy.
 * Crafting material for forging recipes for ultimate equipment.
 * Stackable to 16, has enchantment glow effect.
 */
public class CreatorFragment extends Item {

    // Drop chance is controlled by EliteForgeConfig.SERVER.creatorFragmentDropChance
    // This constant is for documentation only; the config value takes precedence at runtime
    public static final double DROP_CHANCE = 0.10; // Default, actual value from config

    public CreatorFragment() {
        super(new Item.Properties().stacksTo(16));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.eliteforge.creator_fragment.1")
            .withStyle(ChatFormatting.DARK_PURPLE));
        tooltip.add(Component.translatable("item.eliteforge.creator_fragment.tooltip")
            .withStyle(ChatFormatting.DARK_PURPLE));
        tooltip.add(Component.translatable("tooltip.eliteforge.creator_fragment.2")
            .withStyle(ChatFormatting.GRAY));
    }

    @Override
    public boolean isEnchantable(ItemStack stack) {
        return false;
    }
}
