package com.eliteforge.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * NexusEssence (源核精华) - Drop from C1 Nexus creator.
 * Right-click on item: Repairs held item by 25% durability (consumes essence).
 * Stackable to 32, has enchantment glow effect.
 */
public class NexusEssence extends Item {

    private static final float REPAIR_PERCENTAGE = 0.25f;

    public NexusEssence() {
        super(new Item.Properties().stacksTo(32));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack essenceStack = player.getItemInHand(hand);

        if (level.isClientSide()) {
            return InteractionResultHolder.success(essenceStack);
        }

        // Find the item in the other hand to repair
        InteractionHand otherHand = (hand == InteractionHand.MAIN_HAND)
            ? InteractionHand.OFF_HAND
            : InteractionHand.MAIN_HAND;
        ItemStack targetStack = player.getItemInHand(otherHand);

        // If the other hand is empty, try main hand off-hand logic
        if (targetStack.isEmpty()) {
            player.displayClientMessage(
                Component.translatable("message.eliteforge.nexus_essence.empty_hand")
                    .withStyle(ChatFormatting.RED),
                true
            );
            return InteractionResultHolder.fail(essenceStack);
        }

        // Check if the target item is damageable and damaged
        if (!targetStack.isDamageableItem()) {
            player.displayClientMessage(
                Component.translatable("message.eliteforge.nexus_essence.not_repairable")
                    .withStyle(ChatFormatting.RED),
                true
            );
            return InteractionResultHolder.fail(essenceStack);
        }

        if (targetStack.getDamageValue() <= 0) {
            player.displayClientMessage(
                Component.translatable("message.eliteforge.nexus_essence.full_durability")
                    .withStyle(ChatFormatting.RED),
                true
            );
            return InteractionResultHolder.fail(essenceStack);
        }

        // Calculate repair amount (25% of max durability)
        int maxDamage = targetStack.getMaxDamage();
        int repairAmount = Math.max(1, (int) (maxDamage * REPAIR_PERCENTAGE));
        int currentDamage = targetStack.getDamageValue();
        int actualRepair = Math.min(repairAmount, currentDamage);

        // Apply repair
        targetStack.setDamageValue(currentDamage - actualRepair);

        // Consume one essence
        if (!player.isCreative()) {
            essenceStack.shrink(1);
        }

        // Play repair sound and particles
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ANVIL_USE, SoundSource.PLAYERS, 0.8f, 1.5f);

            // Green spark particles around the player
            serverLevel.sendParticles(
                net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER,
                player.getX(), player.getY() + 1.0, player.getZ(),
                15, 0.5, 0.5, 0.5, 0.1
            );
        }

        player.displayClientMessage(
            Component.translatable("message.eliteforge.nexus_essence.repaired", actualRepair, targetStack.getHoverName())
                .withStyle(ChatFormatting.GREEN),
            true
        );

        return InteractionResultHolder.consume(essenceStack);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.eliteforge.nexus_essence.nurturing")
            .withStyle(ChatFormatting.GREEN));
        tooltip.add(Component.translatable("item.eliteforge.nexus_essence.tooltip")
            .withStyle(ChatFormatting.GREEN));
        tooltip.add(Component.translatable("tooltip.eliteforge.nexus_essence.repair_action")
            .withStyle(ChatFormatting.YELLOW));
        tooltip.add(Component.translatable("tooltip.eliteforge.nexus_essence.hint")
            .withStyle(ChatFormatting.GRAY));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public boolean isEnchantable(ItemStack stack) {
        return false;
    }
}
