package com.eliteforge.item;

import com.eliteforge.capability.EliteCapability;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import java.util.List;

/**
 * LootCompass — right-click to scan for nearby elites that have dropped loot (i.e.
 * recently killed elites whose item entities are still on the ground). Points the
 * player toward the nearest loot drop within the configured range.
 * <p>
 * Has a 3-second cooldown to prevent spam.
 */
public class LootCompass extends Item {

    public LootCompass() {
        super(new Item.Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.UNCOMMON));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) return InteractionResultHolder.success(stack);
        if (!(level instanceof ServerLevel serverLevel)) return InteractionResultHolder.pass(stack);

        // Cooldown
        if (player.getCooldowns().isOnCooldown(this)) return InteractionResultHolder.pass(stack);
        player.getCooldowns().addCooldown(this, 60); // 3 seconds

        double range = 64.0;
        try {
            range = com.eliteforge.config.EliteForgeConfig.SERVER.lootCompassRange.get();
        } catch (Exception ignored) {}

        // Find nearest item entity within range
        net.minecraft.world.phys.AABB area = player.getBoundingBox().inflate(range);
        net.minecraft.world.entity.item.ItemEntity nearest = null;
        double nearestDist = Double.MAX_VALUE;

        for (net.minecraft.world.entity.item.ItemEntity item : serverLevel.getEntitiesOfClass(
                net.minecraft.world.entity.item.ItemEntity.class, area)) {
            if (item.getItem().isEmpty()) continue;
            // Check if the item was dropped by an elite (has the eliteforge:material tag or is a mod item)
            boolean isEliteDrop = item.getItem().hasTag()
                    && item.getItem().getTag().contains("eliteforge:material");
            if (!isEliteDrop && item.getItem().getItem() instanceof com.eliteforge.item.CatalystOfFusion) {
                isEliteDrop = true;
            }
            if (!isEliteDrop) continue;

            double dist = item.distanceToSqr(player);
            if (dist < nearestDist) {
                nearestDist = dist;
                nearest = item;
            }
        }

        if (nearest == null) {
            player.displayClientMessage(Component.translatable("message.eliteforge.compass.none")
                    .withStyle(ChatFormatting.GRAY), true);
        } else {
            int dist = (int) Math.sqrt(nearestDist);
            String direction = getDirection(player, nearest);
            player.displayClientMessage(Component.translatable("message.eliteforge.compass.found",
                    direction, dist, nearest.getItem().getHoverName().getString())
                    .withStyle(ChatFormatting.GOLD), true);
            // Particle beam toward the target
            serverLevel.sendParticles(ParticleTypes.END_ROD,
                    nearest.getX(), nearest.getY() + 0.5, nearest.getZ(),
                    5, 0.2, 0.5, 0.2, 0.02);
        }

        return InteractionResultHolder.consume(stack);
    }

    private String getDirection(Player player, net.minecraft.world.entity.Entity target) {
        double dx = target.getX() - player.getX();
        double dz = target.getZ() - player.getZ();
        double angle = Math.toDegrees(Math.atan2(-dx, dz));
        if (angle < 0) angle += 360;
        if (angle < 22.5 || angle >= 337.5) return "S";
        if (angle < 67.5) return "SW";
        if (angle < 112.5) return "W";
        if (angle < 157.5) return "NW";
        if (angle < 202.5) return "N";
        if (angle < 247.5) return "NE";
        if (angle < 292.5) return "E";
        return "SE";
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.eliteforge.loot_compass.1")
                .withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("tooltip.eliteforge.loot_compass.2")
                .withStyle(ChatFormatting.GRAY));
    }
}
