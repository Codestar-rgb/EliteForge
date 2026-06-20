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
 * EvolutionCore (进化晶核) - Drop from C3 Evolution creator (only if evolved 5+ times).
 * Pulses with ever-growing vitality.
 * Crafting material for equipment with growth properties.
 * Stackable to 16, has enchantment glow effect.
 */
public class EvolutionCore extends Item {

    public static final int MIN_EVOLUTION_COUNT = 5;

    public EvolutionCore() {
        super(new Item.Properties().stacksTo(16));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.eliteforge.evolution_core.vitality")
            .withStyle(ChatFormatting.GREEN));
        tooltip.add(Component.translatable("item.eliteforge.evolution_core.tooltip")
            .withStyle(ChatFormatting.GREEN));
        tooltip.add(Component.translatable("tooltip.eliteforge.evolution_core.growth_material")
            .withStyle(ChatFormatting.GRAY));
    }

    @Override
    public boolean isEnchantable(ItemStack stack) {
        return false;
    }
}
