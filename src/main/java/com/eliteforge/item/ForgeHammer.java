package com.eliteforge.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import java.util.List;

/**
 * ForgeHammer — a durable tool used at the Elite Beacon as an alternative to the
 * Catalyst of Fusion. Instead of consuming a Catalyst, the hammer loses durability
 * each time a fusion is performed. When the durability reaches 0, the hammer breaks.
 * <p>
 * This gives players a reusable (but finite) path to accessory fusion without
 * relying on rare Catalyst drops — at the cost of crafting a new hammer periodically.
 */
public class ForgeHammer extends Item {

    public ForgeHammer() {
        super(new Item.Properties().stacksTo(1).durability(64).rarity(net.minecraft.world.item.Rarity.RARE));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        int durability = stack.getMaxDamage() - stack.getDamageValue();
        tooltip.add(Component.translatable("tooltip.eliteforge.forge_hammer.1")
                .withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("tooltip.eliteforge.forge_hammer.2")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.eliteforge.forge_hammer.uses", durability)
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public boolean isRepairable(ItemStack stack) {
        return true;
    }
}
