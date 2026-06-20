package com.eliteforge.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;

/**
 * CommandBanner (号令战旗) - 8% drop from C8 Commander creator.
 * Right-click on block: Places the banner (consumes item).
 *   - Attracts nearby friendly mobs within 32 blocks for 60 seconds
 *   - Hostile mobs within 8 blocks get Weakness II
 *   - Visual: red banner-like particle column
 * Has 1 use (consumed on placement).
 */
public class CommandBanner extends Item {

    public static final double DROP_CHANCE = 0.08;
    private static final int ATTRACT_RADIUS = 32;
    private static final int WEAKNESS_RADIUS = 8;
    private static final int DURATION_TICKS = 1200; // 60 seconds
    private static final int TICK_INTERVAL = 20; // refresh every second

    public CommandBanner() {
        super(new Item.Properties().stacksTo(1));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Player player = context.getPlayer();
        InteractionHand hand = context.getHand();
        ItemStack stack = player.getItemInHand(hand);

        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.PASS;
        }

        Vec3 bannerPos = Vec3.atCenterOf(pos);

        // Apply banner effects and start the 60-second duration
        activateBanner(serverLevel, bannerPos);

        // Consume the item (1 use only)
        stack.shrink(1);

        // Play banner placement sound.
        // In 1.20.1, SoundEvents.RAID_HORN is a Holder<SoundEvent> (Reference), so we
        // need .value() to unwrap it for the playSound(...) overload that takes a raw SoundEvent.
        serverLevel.playSound(null, bannerPos.x, bannerPos.y, bannerPos.z,
            SoundEvents.RAID_HORN.value(), SoundSource.PLAYERS, 1.5f, 0.8f);

        player.displayClientMessage(
            Component.translatable("message.eliteforge.command_banner.placed")
                .withStyle(ChatFormatting.RED),
            true
        );

        return InteractionResult.CONSUME;
    }

    /**
     * Activate the banner effects for 60 seconds at the given position.
     */
    private void activateBanner(ServerLevel serverLevel, Vec3 bannerPos) {
        // Apply initial effects
        applyBannerEffects(serverLevel, bannerPos);

        // Spawn initial particle column
        spawnBannerParticles(serverLevel, bannerPos);

        // Schedule repeating effects for 60 seconds
        int totalIterations = DURATION_TICKS / TICK_INTERVAL;
        for (int i = 1; i <= totalIterations; i++) {
            serverLevel.getServer().tell(new TickTask(
                serverLevel.getServer().getTickCount() + (i * TICK_INTERVAL),
                () -> {
                    if (serverLevel.isLoaded(net.minecraft.core.BlockPos.containing(bannerPos.x, bannerPos.y, bannerPos.z))) {
                        applyBannerEffects(serverLevel, bannerPos);
                        spawnBannerParticles(serverLevel, bannerPos);
                    }
                }
            ));
        }
    }

    /**
     * Apply the banner's mob effects: attract friendly mobs, weaken hostile mobs.
     */
    private void applyBannerEffects(ServerLevel serverLevel, Vec3 bannerPos) {
        // Attract friendly mobs within 32 blocks
        AABB attractBox = new AABB(
            bannerPos.x - ATTRACT_RADIUS, bannerPos.y - ATTRACT_RADIUS, bannerPos.z - ATTRACT_RADIUS,
            bannerPos.x + ATTRACT_RADIUS, bannerPos.y + ATTRACT_RADIUS, bannerPos.z + ATTRACT_RADIUS
        );

        List<Mob> nearbyMobs = serverLevel.getEntitiesOfClass(Mob.class, attractBox);

        for (Mob mob : nearbyMobs) {
            if (mob instanceof Monster monster) {
                // Hostile mobs within 8 blocks get Weakness II
                if (mob.distanceToSqr(bannerPos.x, bannerPos.y, bannerPos.z) <= WEAKNESS_RADIUS * WEAKNESS_RADIUS) {
                    monster.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, TICK_INTERVAL + 10, 1, false, false, true));
                }
            } else if (mob instanceof Animal animal) {
                // Attract friendly/animal mobs toward the banner
                animal.getNavigation().moveTo(bannerPos.x, bannerPos.y, bannerPos.z, 1.0);
            }
        }
    }

    /**
     * Spawn red banner-like particle column at the banner position.
     */
    private void spawnBannerParticles(ServerLevel serverLevel, Vec3 bannerPos) {
        // Red particle column (simulating a banner)
        for (double y = 0; y < 4.0; y += 0.3) {
            serverLevel.sendParticles(
                ParticleTypes.CRIMSON_SPORE,
                bannerPos.x, bannerPos.y + y + 1.0, bannerPos.z,
                2, 0.15, 0.0, 0.15, 0.01
            );
        }

        // Top of the "banner" - flame-like particle
        serverLevel.sendParticles(
            ParticleTypes.FLAME,
            bannerPos.x, bannerPos.y + 4.5, bannerPos.z,
            3, 0.2, 0.1, 0.2, 0.02
        );

        // Base ring
        for (int angle = 0; angle < 360; angle += 30) {
            double rad = Math.toRadians(angle);
            double radius = 1.0;
            double px = bannerPos.x + Math.cos(rad) * radius;
            double pz = bannerPos.z + Math.sin(rad) * radius;
            serverLevel.sendParticles(
                ParticleTypes.END_ROD,
                px, bannerPos.y + 0.1, pz,
                1, 0.0, 0.05, 0.0, 0.0
            );
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.eliteforge.command_banner.tagline")
            .withStyle(ChatFormatting.RED));
        tooltip.add(Component.translatable("item.eliteforge.command_banner.tooltip")
            .withStyle(ChatFormatting.RED));
        tooltip.add(Component.translatable("tooltip.eliteforge.command_banner.use")
            .withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("tooltip.eliteforge.command_banner.attract", ATTRACT_RADIUS)
            .withStyle(ChatFormatting.GREEN));
        tooltip.add(Component.translatable("tooltip.eliteforge.command_banner.weakness", WEAKNESS_RADIUS)
            .withStyle(ChatFormatting.RED));
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
