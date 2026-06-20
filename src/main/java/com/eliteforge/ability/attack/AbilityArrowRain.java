package com.eliteforge.ability.attack;

import com.eliteforge.ability.Ability;
import com.eliteforge.ability.AbilityCategory;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Arrow;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * AbilityArrowRain (箭雨) - Periodically rains arrows around the target.
 *
 * On attack: every (60 - level * 10) ticks, rain arrows in area around target.
 * Arrows deal level * 1 damage each.
 * 3 + level arrows per rain.
 */
public class AbilityArrowRain extends Ability {

    public AbilityArrowRain() {
        super(
            new ResourceLocation("eliteforge", "arrow_rain"),
            AbilityCategory.ATTACK,
            3.0f
        );
    }

    @Override
    public void onTick(LivingEntity entity, int level) {
        if (entity.level().isClientSide) return;

        // H1 fix: previously rained arrows on the elite itself (rainArrows(entity, entity, level)),
        // which damaged allies/pets/passive mobs that walked through the arrow zone.
        // Now: find the nearest player within range and rain arrows on them.
        // If no player is nearby, skip — the elite doesn't waste arrows on empty air.
        int cooldown = Math.max(20, 60 - level * 10);
        if (entity.tickCount > 20 && entity.tickCount % cooldown == 0) {
            double range = 16.0 + level * 2.0;
            net.minecraft.world.phys.AABB searchArea = entity.getBoundingBox().inflate(range);
            List<net.minecraft.world.entity.player.Player> nearbyPlayers =
                    entity.level().getEntitiesOfClass(net.minecraft.world.entity.player.Player.class, searchArea,
                            p -> p.isAlive() && !p.isSpectator() && !p.isCreative()
                                    && entity.distanceToSqr(p) <= range * range);
            if (!nearbyPlayers.isEmpty()) {
                // Target the nearest player
                net.minecraft.world.entity.player.Player target = nearbyPlayers.get(0);
                double closestDist = entity.distanceToSqr(target);
                for (net.minecraft.world.entity.player.Player p : nearbyPlayers) {
                    double dist = entity.distanceToSqr(p);
                    if (dist < closestDist) {
                        closestDist = dist;
                        target = p;
                    }
                }
                rainArrows(entity, target, level);
            }
        }
    }

    @Override
    public void onAttack(LivingEntity attacker, LivingEntity target, float damage, int level) {
        if (attacker.level().isClientSide) return;
        rainArrows(attacker, target, level);
    }

    /**
     * Spawns arrows raining down around the target position.
     */
    private void rainArrows(LivingEntity attacker, LivingEntity target, int level) {
        if (!(target.level() instanceof ServerLevel serverLevel)) return;

        int arrowCount = 3 + level;
        float arrowDamage = level * 1.0f;
        double radius = 2.0 + level * 0.5;

        for (int i = 0; i < arrowCount; i++) {
            double offsetX = (ThreadLocalRandom.current().nextDouble() - 0.5) * radius * 2;
            double offsetZ = (ThreadLocalRandom.current().nextDouble() - 0.5) * radius * 2;
            double spawnY = target.getY() + 10 + ThreadLocalRandom.current().nextDouble() * 5; // Rain from above

            Arrow arrow = new Arrow(target.level(), attacker);
            arrow.setOwner(attacker); // Ensure arrow ownership for kill credit
            arrow.setPos(target.getX() + offsetX, spawnY, target.getZ() + offsetZ);
            arrow.setNoGravity(true);
            arrow.setBaseDamage(arrowDamage);
            arrow.setDeltaMovement(0, -1.0, 0); // Shoot downward
            arrow.setSilent(true);

            target.level().addFreshEntity(arrow);
        }

        // Visual: particle indicator of arrow rain area
        serverLevel.sendParticles(ParticleTypes.CRIT,
                target.getX(), target.getY() + 1, target.getZ(),
                10 + level * 3,
                radius, 0.3, radius,
                0.1);
    }
}
