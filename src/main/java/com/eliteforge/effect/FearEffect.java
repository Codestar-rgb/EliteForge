package com.eliteforge.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Fear (恐惧) - Forces the target to run away from the source.
 * Applies a movement speed boost in the opposite direction of the nearest threat.
 * For players, applies movement away from the nearest hostile entity.
 * For mobs, triggers a panic response.
 */
public class FearEffect extends MobEffect {

    public FearEffect() {
        super(MobEffectCategory.HARMFUL, 0x1E1E2E);
    }

    @Override
    public void applyEffectTick(LivingEntity entity, int amplifier) {
        if (!entity.level().isClientSide) {
            if (entity instanceof Mob mob) {
                // Force mob to panic and run away
                mob.setLastHurtByMob(null);
                mob.getNavigation().stop();

                // Apply random fleeing direction
                double angle = entity.level().random.nextDouble() * Math.PI * 2;
                double speed = 1.5 + (amplifier * 0.3);
                double dx = Math.cos(angle) * speed;
                double dz = Math.sin(angle) * speed;
                mob.getMoveControl().setWantedPosition(
                        mob.getX() + dx,
                        mob.getY(),
                        mob.getZ() + dz,
                        speed
                );
            } else if (entity instanceof Player player) {
                // For players, apply a knockback-like effect away from nearest threat
                LivingEntity nearestThreat = findNearestThreat(player);
                if (nearestThreat != null) {
                    Vec3 fleeDirection = player.position()
                            .subtract(nearestThreat.position())
                            .normalize();
                    double knockbackStrength = 0.4 + (amplifier * 0.15);
                    player.setDeltaMovement(player.getDeltaMovement()
                            .add(fleeDirection.x * knockbackStrength, 0.1, fleeDirection.z * knockbackStrength));
                }
            }
        }
    }

    /**
     * Find the nearest hostile entity that could be the source of fear.
     * Only considers Monster-type entities (hostile mobs) to avoid
     * players fleeing from passive animals.
     */
    private LivingEntity findNearestThreat(Player player) {
        double range = 16.0;
        LivingEntity nearest = null;
        double nearestDist = Double.MAX_VALUE;

        for (LivingEntity entity : player.level().getEntitiesOfClass(
                LivingEntity.class,
                player.getBoundingBox().inflate(range),
                e -> e != player && (e instanceof Monster)
        )) {
            double dist = player.distanceToSqr(entity);
            if (dist < nearestDist) {
                nearestDist = dist;
                nearest = entity;
            }
        }
        return nearest;
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        // Apply effect every 20 ticks (1 second)
        return duration % 20 == 0;
    }
}
