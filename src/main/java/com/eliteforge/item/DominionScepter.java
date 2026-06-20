package com.eliteforge.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.TickTask;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * DominionScepter (支配权杖) - 5% drop from C2 Dominion creator.
 * Right-click: Creates a temporary safe zone (15 second duration, 8-block radius).
 *   - Removes all negative effects from players in zone
 *   - Gives players Resistance II and Regeneration I for 15s
 *   - Visual: beacon beam effect at center for 15s
 * Shift+right-click: Shows zone preview (particles only, no effect)
 * Has 3 durability uses.
 */
public class DominionScepter extends Item {

    private static final int MAX_DURABILITY = 3;
    private static final int ZONE_RADIUS = 8;
    private static final int ZONE_DURATION_TICKS = 300; // 15 seconds
    private static final int BEAM_PARTICLE_INTERVAL = 10; // ticks between beam particles

    public DominionScepter() {
        super(new Item.Properties().durability(MAX_DURABILITY));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (level.isClientSide()) {
            return InteractionResultHolder.success(stack);
        }

        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResultHolder.pass(stack);
        }

        Vec3 center = player.position();

        if (player.isShiftKeyDown()) {
            // Shift+right-click: Show zone preview (particles only, no effect)
            showZonePreview(serverLevel, center);
            player.displayClientMessage(
                Component.translatable("message.eliteforge.dominion_scepter.preview", ZONE_RADIUS)
                    .withStyle(ChatFormatting.YELLOW),
                true
            );
            return InteractionResultHolder.success(stack);
        }

        // Right-click: Create safe zone
        createSafeZone(serverLevel, player, center, hand);

        return InteractionResultHolder.consume(stack);
    }

    /**
     * Create a safe zone at the given center position.
     * Removes negative effects from nearby players and gives protective buffs.
     */
    private void createSafeZone(ServerLevel serverLevel, Player caster, Vec3 center, InteractionHand hand) {
        // Find all players within the zone radius
        AABB zoneBox = new AABB(
            center.x - ZONE_RADIUS, center.y - ZONE_RADIUS, center.z - ZONE_RADIUS,
            center.x + ZONE_RADIUS, center.y + ZONE_RADIUS, center.z + ZONE_RADIUS
        );

        List<Player> playersInZone = serverLevel.getEntitiesOfClass(Player.class, zoneBox);

        for (Player player : playersInZone) {
            // Remove all negative effects
            removeNegativeEffects(player);

            // Give Resistance II and Regeneration I for 15 seconds
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, ZONE_DURATION_TICKS, 1, false, true, true));
            player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, ZONE_DURATION_TICKS, 0, false, true, true));
        }

        // Damage the item
        caster.getItemInHand(hand).hurtAndBreak(1, caster, (p) -> p.broadcastBreakEvent(hand));

        // Play activation sound
        serverLevel.playSound(null, center.x, center.y, center.z,
            SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0f, 1.2f);

        // Start the beacon beam visual effect
        startBeaconBeam(serverLevel, center);

        caster.displayClientMessage(
            Component.translatable("message.eliteforge.dominion_scepter.activated")
                .withStyle(ChatFormatting.GOLD),
            true
        );
    }

    /**
     * Remove all non-beneficial effects from a living entity.
     * Uses isBeneficial() check to cover mod-added and future vanilla effects.
     */
    private static void removeNegativeEffects(LivingEntity entity) {
        List<MobEffectInstance> toRemove = new ArrayList<>();
        for (MobEffectInstance instance : entity.getActiveEffects()) {
            if (!instance.getEffect().isBeneficial()) {
                toRemove.add(instance);
            }
        }
        for (MobEffectInstance instance : toRemove) {
            entity.removeEffect(instance.getEffect());
        }
    }

    /**
     * Start a beacon beam visual effect at the center position.
     * Schedules repeated particle sends for 15 seconds.
     */
    private void startBeaconBeam(ServerLevel serverLevel, Vec3 center) {
        // Initial burst of particles
        spawnBeamParticles(serverLevel, center);

        // Schedule repeated beam particles for the duration
        int totalIterations = ZONE_DURATION_TICKS / BEAM_PARTICLE_INTERVAL;
        for (int i = 1; i <= totalIterations; i++) {
            final int iteration = i;
            serverLevel.getServer().tell(new TickTask(
                serverLevel.getServer().getTickCount() + (i * BEAM_PARTICLE_INTERVAL),
                () -> {
                    if (serverLevel.isLoaded(net.minecraft.core.BlockPos.containing(center.x, center.y, center.z))) {
                        spawnBeamParticles(serverLevel, center);

                        // Also refresh the ring particles at intervals
                        if (iteration % 3 == 0) {
                            spawnZoneRing(serverLevel, center);
                        }
                    }
                }
            ));
        }
    }

    /**
     * Spawn beacon beam-like particles at the center position.
     */
    private void spawnBeamParticles(ServerLevel serverLevel, Vec3 center) {
        // Vertical beam of end rod particles
        for (double y = 0; y < 10.0; y += 0.5) {
            serverLevel.sendParticles(
                ParticleTypes.END_ROD,
                center.x, center.y + y, center.z,
                1, 0.05, 0.0, 0.05, 0.01
            );
        }
    }

    /**
     * Spawn a ring of particles at the base showing the zone boundary.
     */
    private void spawnZoneRing(ServerLevel serverLevel, Vec3 center) {
        for (int angle = 0; angle < 360; angle += 20) {
            double rad = Math.toRadians(angle);
            double px = center.x + Math.cos(rad) * ZONE_RADIUS;
            double pz = center.z + Math.sin(rad) * ZONE_RADIUS;
            serverLevel.sendParticles(
                ParticleTypes.HAPPY_VILLAGER,
                px, center.y + 0.1, pz,
                1, 0.0, 0.1, 0.0, 0.01
            );
        }
    }

    /**
     * Show zone preview with particles only (no effects).
     */
    private void showZonePreview(ServerLevel serverLevel, Vec3 center) {
        // Show boundary ring
        spawnZoneRing(serverLevel, center);

        // Show a brief beam preview
        for (double y = 0; y < 5.0; y += 0.8) {
            serverLevel.sendParticles(
                ParticleTypes.END_ROD,
                center.x, center.y + y, center.z,
                1, 0.05, 0.0, 0.05, 0.02
            );
        }

        // Play a subtle sound
        serverLevel.playSound(null, center.x, center.y, center.z,
            SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, 0.5f, 1.5f);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.eliteforge.dominion_scepter.tagline")
            .withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("item.eliteforge.dominion_scepter.tooltip")
            .withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("tooltip.eliteforge.dominion_scepter.use", ZONE_RADIUS)
            .withStyle(ChatFormatting.YELLOW));
        tooltip.add(Component.translatable("tooltip.eliteforge.dominion_scepter.effects")
            .withStyle(ChatFormatting.YELLOW));
        tooltip.add(Component.translatable("tooltip.eliteforge.dominion_scepter.preview_hint")
            .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.eliteforge.dominion_scepter.uses", MAX_DURABILITY - stack.getDamageValue())
            .withStyle(ChatFormatting.DARK_GRAY));
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
