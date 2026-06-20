package com.eliteforge.item;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import java.util.List;

public class BindingStone extends Item {
    public BindingStone() { super(new Item.Properties().stacksTo(16).rarity(net.minecraft.world.item.Rarity.RARE)); }
    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.eliteforge.binding_stone.1").withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("tooltip.eliteforge.binding_stone.2").withStyle(ChatFormatting.GRAY));
    }
}
