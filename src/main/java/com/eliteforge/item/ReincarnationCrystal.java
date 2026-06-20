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
 * ReincarnationCrystal (轮回结晶) - Drop from C7 Reincarnation creator (on final death only).
 * A crystal holding the essence of rebirth.
 * Crafting material for equipment with "rebirth" effect.
 * Stackable to 8, has enchantment glow effect.
 */
public class ReincarnationCrystal extends Item {

    public ReincarnationCrystal() {
        super(new Item.Properties().stacksTo(8));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.eliteforge.reincarnation_crystal.essence")
            .withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.translatable("item.eliteforge.reincarnation_crystal.tooltip")
            .withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.translatable("tooltip.eliteforge.reincarnation_crystal.rebirth_material")
            .withStyle(ChatFormatting.GRAY));
    }

    @Override
    public boolean isEnchantable(ItemStack stack) {
        return false;
    }
}
