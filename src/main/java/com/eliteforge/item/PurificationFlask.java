package com.eliteforge.item;

import com.eliteforge.ability.Ability;
import com.eliteforge.ability.AbilityRegistry;
import com.eliteforge.capability.EliteCapability;
import com.eliteforge.capability.EliteCapabilitySync;
import com.eliteforge.capability.EliteData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * PurificationFlask (净化烧瓶) - Splash potion that removes one random ability
 * from each elite in 5-block radius.
 * Throwable, consumed on use.
 */
public class PurificationFlask extends Item {

    private static final Logger LOGGER = LogManager.getLogger();
    private static final int EFFECT_RADIUS = 5;

    public PurificationFlask() {
        super(new Item.Properties().stacksTo(16));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (level.isClientSide()) {
            return InteractionResultHolder.success(stack);
        }

        // Throw the flask as a custom projectile
        PurificationFlaskEntity flaskEntity = new PurificationFlaskEntity(level, player);
        flaskEntity.setItem(stack);
        flaskEntity.shootFromRotation(player, player.getXRot(), player.getYRot(), -20.0f, 0.5f, 1.0f);
        level.addFreshEntity(flaskEntity);

        // Consume the item
        if (!player.isCreative()) {
            stack.shrink(1);
        }

        return InteractionResultHolder.consume(stack);
    }

    /**
     * Apply the purification effect at the impact location.
     * Removes one random ability from each elite in radius.
     */
    public static void applyPurification(Level level, double x, double y, double z) {
        if (level.isClientSide()) return;

        // Play splash sound
        level.playSound(null, x, y, z,
            SoundEvents.SPLASH_POTION_BREAK, SoundSource.PLAYERS, 1.0f, 1.0f);

        // Spawn splash particles
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(
                net.minecraft.core.particles.ParticleTypes.EFFECT,
                x, y, z, 30, 2.5, 2.5, 2.5, 0.1
            );
        }

        // Find all mobs in radius
        AABB searchBox = new AABB(
            x - EFFECT_RADIUS, y - EFFECT_RADIUS, z - EFFECT_RADIUS,
            x + EFFECT_RADIUS, y + EFFECT_RADIUS, z + EFFECT_RADIUS
        );

        List<Mob> nearbyMobs = level.getEntitiesOfClass(Mob.class, searchBox);
        int affectedCount = 0;

        for (Mob mob : nearbyMobs) {
            // Check elite status and read abilities via the EliteCapability (the canonical elite
            // system). The old code read from a "forge" sub-compound that no system ever writes
            // to, so purification never actually removed any abilities.
            EliteCapability cap = mob.getCapability(EliteCapability.CAPABILITY).orElse(null);
            if (cap == null || !cap.isElite()) {
                continue;
            }

            EliteData data = cap.getEliteData();
            Map<String, Integer> abilities = data.getAbilities();
            if (abilities.isEmpty()) {
                continue;
            }

            // Remove one random ability. Build a list of IDs so we can pick by index.
            List<String> abilityIds = new ArrayList<>(abilities.keySet());
            ThreadLocalRandom random = ThreadLocalRandom.current();
            String removedId = abilityIds.get(random.nextInt(abilityIds.size()));
            int removedLevel = abilities.get(removedId);

            // Call onRemove for cleanup of attribute modifiers / persistent effects.
            Ability ability = AbilityRegistry.getAbility(removedId);
            if (ability != null) {
                try {
                    ability.onRemove(mob, removedLevel);
                } catch (Exception e) {
                    LOGGER.error("Error in onRemove for {} during purification: {}",
                            removedId, e.getMessage());
                }
            }

            data.removeAbility(removedId);
            cap.setEliteData(data);
            EliteCapabilitySync.broadcastEliteDataUpdate(mob, data);

            affectedCount++;

            // Visual feedback on the mob
            if (level instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(
                    net.minecraft.core.particles.ParticleTypes.WITCH,
                    mob.getX(), mob.getY() + mob.getBbHeight() / 2.0, mob.getZ(),
                    10, 0.3, 0.3, 0.3, 0.05
                );
            }
        }

        // Notify nearby players
        List<Player> nearbyPlayers = level.getEntitiesOfClass(Player.class, searchBox);
        for (Player p : nearbyPlayers) {
            p.displayClientMessage(
                Component.translatable("message.eliteforge.purification_flask.affected", affectedCount)
                    .withStyle(ChatFormatting.AQUA),
                true
            );
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.eliteforge.purification_flask")
            .withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("tooltip.eliteforge.purification_flask.radius", EFFECT_RADIUS)
            .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.eliteforge.purification_flask.note")
            .withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    /**
     * Custom thrown entity for the purification flask.
     * Handles impact detection and applies purification effect.
     */
    public static class PurificationFlaskEntity extends ThrownPotion {

        public PurificationFlaskEntity(Level level, Player thrower) {
            super(level, thrower);
        }

        public PurificationFlaskEntity(net.minecraft.world.entity.EntityType<? extends ThrownPotion> type, Level level) {
            super(type, level);
        }

        @Override
        protected void onHit(HitResult result) {
            if (!this.level().isClientSide) {
                applyPurification(this.level(), this.getX(), this.getY(), this.getZ());
            }
            this.discard();
        }

        @Override
        protected void onHitBlock(BlockHitResult result) {
            super.onHitBlock(result);
            if (!this.level().isClientSide) {
                applyPurification(this.level(), this.getX(), this.getY(), this.getZ());
                this.discard();
            }
        }

        @Override
        protected void onHitEntity(EntityHitResult result) {
            super.onHitEntity(result);
            if (!this.level().isClientSide) {
                applyPurification(this.level(), this.getX(), this.getY(), this.getZ());
                this.discard();
            }
        }
    }
}
