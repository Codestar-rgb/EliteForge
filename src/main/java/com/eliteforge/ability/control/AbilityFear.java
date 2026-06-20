package com.eliteforge.ability.control;

import com.eliteforge.ability.Ability;
import com.eliteforge.ability.AbilityCategory;
import com.eliteforge.init.ModEffects;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * AbilityFear (恐惧) - Forces players to run away.
 * 
 * Applies custom Fear effect: forces players to run away.
 * Range: (4 + level * 2) blocks.
 * Duration: 3s + level * 1s.
 * Angry villager particles.
 */
public class AbilityFear extends Ability {

    public AbilityFear() {
        super(
            new ResourceLocation("eliteforge", "fear"),
            AbilityCategory.CONTROL,
            2.0f
        );
    }

    @Override
    public void onTick(LivingEntity entity, int level) {
        if (entity.level().isClientSide) return;
        if (entity.tickCount % 30 != 0) return; // Every 1.5 seconds

        double range = 4.0 + level * 2.0;
        AABB area = new AABB(
                entity.getX() - range, entity.getY() - range, entity.getZ() - range,
                entity.getX() + range, entity.getY() + range, entity.getZ() + range
        );

        List<Player> nearbyPlayers = entity.level().getEntitiesOfClass(Player.class, area,
                p -> p.isAlive() && !p.isSpectator() && !p.isCreative());

        int durationTicks = 60 + level * 20; // 3s + level * 1s

        for (Player player : nearbyPlayers) {
            // Apply custom Fear effect for proper flee behavior
            int fearAmplifier = Math.max(0, level - 1);
            player.addEffect(new MobEffectInstance(ModEffects.FEAR_EFFECT.get(), durationTicks, fearAmplifier, false, true));

            // Push player away from the entity (fear flight response).
            // Guard against zero distance — normalize() of the zero vector yields NaN.
            Vec3 delta = player.position().subtract(entity.position());
            Vec3 awayDirection = delta.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 1) : delta.normalize();
            double pushStrength = 0.3 + level * 0.15;
            player.setDeltaMovement(player.getDeltaMovement().add(awayDirection.scale(pushStrength)));
            player.hurtMarked = true;
        }

        // Angry villager particles
        if (!nearbyPlayers.isEmpty() && entity.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.ANGRY_VILLAGER,
                    entity.getX(), entity.getY() + entity.getBbHeight() + 0.3, entity.getZ(),
                    2 + level,
                    entity.getBbWidth() * 0.5, 0.3, entity.getBbWidth() * 0.5,
                    0.0);
        }
    }
}
