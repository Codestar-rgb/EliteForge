package com.eliteforge.ability.control;

import com.eliteforge.ability.Ability;
import com.eliteforge.ability.AbilityCategory;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * AbilityConfusion (混乱) - Applies Nausea + Blindness, reverses controls at high levels.
 * 
 * Applies Nausea + Blindness to nearby players.
 * Range: (3 + level) blocks, Duration: 3s + level * 1s.
 * At level IV+: also reverses controls.
 * Swirling color particles.
 */
public class AbilityConfusion extends Ability {

    public AbilityConfusion() {
        super(
            new ResourceLocation("eliteforge", "confusion"),
            AbilityCategory.CONTROL,
            2.0f
        );
    }

    @Override
    public void onTick(LivingEntity entity, int level) {
        if (entity.level().isClientSide) return;
        if (entity.tickCount % 35 != 0) return; // Every ~1.75 seconds

        double range = 3.0 + level;
        AABB area = new AABB(
                entity.getX() - range, entity.getY() - range, entity.getZ() - range,
                entity.getX() + range, entity.getY() + range, entity.getZ() + range
        );

        List<Player> nearbyPlayers = entity.level().getEntitiesOfClass(Player.class, area,
                p -> p.isAlive() && !p.isSpectator() && !p.isCreative());

        if (nearbyPlayers.isEmpty()) return;

        int durationTicks = 60 + level * 20; // 3s + level * 1s

        for (Player player : nearbyPlayers) {
            // Apply Nausea
            player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, durationTicks, 0, false, true));

            // Apply Blindness
            player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, durationTicks, 0, false, true));

            // At level IV+: reverse controls by applying random velocity changes
            if (level >= 4) {
                // Simulate reversed controls by applying opposite movement forces
                Vec3 lookDirection = player.getLookAngle();
                Vec3 reverseForce = lookDirection.scale(-0.3);
                player.setDeltaMovement(player.getDeltaMovement().add(reverseForce));
                player.hurtMarked = true;

                // Apply Slowness to make it harder to move deliberately
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, durationTicks, 1, false, true));
            }
        }

        // Swirling color particles
        if (entity.level() instanceof ServerLevel serverLevel) {
            for (int i = 0; i < 4 + level; i++) {
                double angle = (entity.tickCount * 0.15) + (Math.PI * 2 * i) / (4 + level);
                double px = entity.getX() + Math.cos(angle) * range * 0.6;
                double py = entity.getY() + entity.getBbHeight() * 0.5 + Math.sin(entity.tickCount * 0.1 + i) * 0.5;
                double pz = entity.getZ() + Math.sin(angle) * range * 0.6;
                serverLevel.sendParticles(ParticleTypes.ENTITY_EFFECT,
                        px, py, pz, 1,
                        0.1, 0.1, 0.1, 0);
            }
        }
    }
}
