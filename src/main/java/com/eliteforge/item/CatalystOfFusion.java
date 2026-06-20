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
 * CatalystOfFusion (融合催化剂) — a rare consumable used in the accessory fusion system.
 * <p>
 * When a player right-clicks an Elite Beacon while holding two accessories of the same
 * type (e.g. two Rings) plus a Catalyst of Fusion, the two are consumed and a single
 * accessory of the next-higher quality tier is produced. This is an alternative upgrade
 * path to the material-based AccessoryUpgrader — fusion sacrifices a duplicate accessory
 * for a guaranteed quality bump.
 * <p>
 * The fusion logic itself lives in {@link com.eliteforge.accessory.AccessoryUpgrader}
 * (tryFusion method), which is called from the Elite Beacon's use() handler when it
 * detects a catalyst + two same-type accessories in the player's hands/inventory.
 * <p>
 * Drops from EPIC+ elites at the configured {@code fusionCatalystDropChance} rate.
 */
public class CatalystOfFusion extends Item {

    public CatalystOfFusion() {
        super(new Item.Properties().stacksTo(16).rarity(net.minecraft.world.item.Rarity.EPIC));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.eliteforge.fusion_catalyst.1")
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.translatable("tooltip.eliteforge.fusion_catalyst.2")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.eliteforge.fusion_catalyst.3")
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }
}
