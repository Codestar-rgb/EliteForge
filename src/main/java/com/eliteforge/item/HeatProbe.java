package com.eliteforge.item;

import com.eliteforge.difficulty.ChunkHeatManager;
import com.eliteforge.difficulty.PlayerExperienceManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ChunkPos;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * HeatProbe (热度探针) — Portable chunk heat viewer.
 * <p>
 * Right-click on any block to check the current chunk's heat value,
 * trend, and nearby elite activity. Displays in action bar for quick
 * reference without opening any GUI.
 * <p>
 * Display format:
 * <pre>
 * Chunk Heat: 45/100 (▲ Rising)
 * Nearby Elites: 2 | Player Exp: 12.5
 * </pre>
 */
public class HeatProbe extends Item {

    public HeatProbe() {
        super(new Item.Properties().stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            return InteractionResultHolder.success(stack);
        }

        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.pass(stack);
        }

        // Cooldown: 20 ticks (1s) to prevent spam-click lag from the 64-block entity scan.
        if (player.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.pass(stack);
        }
        player.getCooldowns().addCooldown(this, 20);

        ChunkPos chunkPos = new ChunkPos(player.blockPosition());
        ChunkHeatManager heatManager = ChunkHeatManager.get(serverLevel);
        float currentHeat = heatManager.getHeat(serverLevel, chunkPos);
        // Use the configured max heat so the "X/Y" display matches the server's heat curve.
        float maxHeat = com.eliteforge.config.EliteForgeConfig.SERVER.chunkHeatMax.get().floatValue();

        // Determine heat trend by comparing with stored previous value. The trend label
        // is localized via message.eliteforge.heat_probe.trend.{stable,rising,falling}.
        String trendKey;
        ChatFormatting trendColor;
        float previousHeat = stack.getOrCreateTag().getFloat("eliteforge:last_heat");
        if (previousHeat == 0) {
            trendKey = "message.eliteforge.heat_probe.trend.stable";
            trendColor = ChatFormatting.GRAY;
        } else if (currentHeat > previousHeat + 1) {
            trendKey = "message.eliteforge.heat_probe.trend.rising";
            trendColor = ChatFormatting.RED;
        } else if (currentHeat < previousHeat - 1) {
            trendKey = "message.eliteforge.heat_probe.trend.falling";
            trendColor = ChatFormatting.GREEN;
        } else {
            trendKey = "message.eliteforge.heat_probe.trend.stable";
            trendColor = ChatFormatting.GRAY;
        }

        // Store current heat for next comparison
        stack.getOrCreateTag().putFloat("eliteforge:last_heat", currentHeat);

        // Get player experience
        PlayerExperienceManager expManager = PlayerExperienceManager.get(serverLevel);
        float playerExp = expManager.getPlayerExperience(serverPlayer);

        // Count nearby elites
        int nearbyElites = serverLevel.getEntitiesOfClass(
                net.minecraft.world.entity.LivingEntity.class,
                player.getBoundingBox().inflate(64.0),
                e -> e.getCapability(com.eliteforge.capability.EliteCapability.CAPABILITY)
                        .map(com.eliteforge.capability.EliteCapability::isElite)
                        .orElse(false)
        ).size();

        // Build display
        ChatFormatting heatColor = currentHeat > 75 ? ChatFormatting.RED
                : currentHeat > 50 ? ChatFormatting.GOLD
                : currentHeat > 25 ? ChatFormatting.YELLOW
                : ChatFormatting.GREEN;

        player.displayClientMessage(
                Component.translatable("message.eliteforge.heat_probe.chunk_heat")
                        .withStyle(ChatFormatting.AQUA)
                        .append(Component.literal(String.format("%.0f/%.0f", currentHeat, maxHeat))
                                .withStyle(heatColor))
                        .append(Component.literal(" (")
                                .append(Component.translatable(trendKey))
                                .append(Component.literal(")"))
                                .withStyle(trendColor)),
                true
        );

        player.displayClientMessage(
                Component.translatable("message.eliteforge.heat_probe.nearby").append(Component.literal(String.valueOf(nearbyElites)))
                        .withStyle(ChatFormatting.WHITE)
                        .append(Component.translatable("message.eliteforge.heat_probe.player_exp").append(Component.literal(String.format("%.1f", playerExp)))
                                .withStyle(ChatFormatting.GRAY)),
                true
        );

        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.eliteforge.heat_probe.tooltip.1")
                .withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("item.eliteforge.heat_probe.tooltip.2")
                .withStyle(ChatFormatting.GRAY));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }
}
