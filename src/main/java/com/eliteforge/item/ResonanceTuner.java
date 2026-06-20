package com.eliteforge.item;

import com.eliteforge.capability.EliteCapability;
import com.eliteforge.capability.EliteData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import java.util.List;
import java.util.Map;

/**
 * ResonanceTuner — right-click an elite to inspect its full ability list, quality,
 * level, and creator status. More detailed than /ef nearby (shows individual ability
 * names + levels). Does not consume the item (reusable scanning tool).
 */
public class ResonanceTuner extends Item {

    public ResonanceTuner() {
        super(new Item.Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.UNCOMMON));
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (player.level().isClientSide) return InteractionResult.SUCCESS;

        EliteData data = target.getCapability(EliteCapability.CAPABILITY)
                .map(EliteCapability::getEliteData)
                .orElse(null);
        if (data == null || !data.isElite()) {
            player.displayClientMessage(Component.translatable("message.eliteforge.tuner.not_elite")
                    .withStyle(ChatFormatting.GRAY), true);
            return InteractionResult.CONSUME;
        }

        // Display full elite details
        ChatFormatting qc = data.getQualityTier().getChatColor();
        player.displayClientMessage(Component.literal("═════════════════════════")
                .withStyle(ChatFormatting.GOLD), false);
        player.displayClientMessage(Component.literal(target.getName().getString())
                .append(" [" + data.getQualityTier().name() + "] Lv." + data.getLevel())
                .withStyle(qc, ChatFormatting.BOLD), false);
        if (data.isCreatorEntity()) {
            player.displayClientMessage(Component.literal("★ CREATOR: " + data.getCreatorAbilityId())
                    .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD), false);
        }
        player.displayClientMessage(Component.literal("Abilities (" + data.getAbilities().size() + "):")
                .withStyle(ChatFormatting.YELLOW), false);
        for (Map.Entry<String, Integer> entry : data.getAbilities().entrySet()) {
            player.displayClientMessage(Component.literal("  • " + entry.getKey() + " Lv." + entry.getValue())
                    .withStyle(ChatFormatting.GRAY), false);
        }
        player.displayClientMessage(Component.literal("═════════════════════════")
                .withStyle(ChatFormatting.GOLD), false);

        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.eliteforge.resonance_tuner.1")
                .withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("tooltip.eliteforge.resonance_tuner.2")
                .withStyle(ChatFormatting.GRAY));
    }
}
