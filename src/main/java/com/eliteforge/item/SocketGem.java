package com.eliteforge.item;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import java.util.List;

public class SocketGem extends Item {
    public SocketGem() { super(new Item.Properties().stacksTo(64).rarity(net.minecraft.world.item.Rarity.RARE)); }
    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.eliteforge.socket_gem.1").withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.translatable("tooltip.eliteforge.socket_gem.2").withStyle(ChatFormatting.GRAY));
    }
    @Override
    public boolean isFoil(ItemStack stack) { return true; }
}
