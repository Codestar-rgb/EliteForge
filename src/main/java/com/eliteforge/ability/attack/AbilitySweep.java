package com.eliteforge.ability.attack;

import com.eliteforge.ability.Ability;
import com.eliteforge.ability.AbilityCategory;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * AbilitySweep (横扫) - Attacks hit all entities in a radius.
 * 
 * Attacks hit all entities in (2 + level * 0.5) block radius.
 * Sweep damage is (50% + level * 10%) of original damage.
 */
public class AbilitySweep extends Ability {

    public AbilitySweep() {
        super(
            new ResourceLocation("eliteforge", "sweep"),
            AbilityCategory.ATTACK,
            2.0f
        );
    }

    @Override
    public void onTick(LivingEntity entity, int level) {
        if (entity.level().isClientSide) return;
        // No passive particle effect for sweep
    }

    @Override
    public void onAttack(LivingEntity attacker, LivingEntity target, float damage, int level) {
        if (attacker.level().isClientSide) return;

        double radius = 2.0 + level * 0.5;
        float sweepDamagePercentage = 0.50f + level * 0.10f;
        float sweepDamage = damage * sweepDamagePercentage;

        // Find all entities in radius around the target
        AABB area = new AABB(
                target.getX() - radius, target.getY() - radius, target.getZ() - radius,
                target.getX() + radius, target.getY() + target.getBbHeight() + radius, target.getZ() + radius
        );

        List<LivingEntity> nearbyEntities = target.level().getEntitiesOfClass(LivingEntity.class, area,
                e -> e != attacker && e != target && e.isAlive() && attacker.canAttack(e));

        for (LivingEntity nearby : nearbyEntities) {
            nearby.hurt(attacker.damageSources().mobAttack(attacker), sweepDamage);
        }

        // Sweep particle effect
        if (attacker.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.SWEEP_ATTACK,
                    target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(),
                    1 + level / 2,
                    radius * 0.3, 0.2, radius * 0.3,
                    0.0);

            // Additional slash particles around the area
            for (int i = 0; i < 4 + level; i++) {
                double angle = (Math.PI * 2 * i) / (4 + level);
                double px = target.getX() + Math.cos(angle) * radius * 0.7;
                double pz = target.getZ() + Math.sin(angle) * radius * 0.7;
                serverLevel.sendParticles(ParticleTypes.SWEEP_ATTACK,
                        px, target.getY() + target.getBbHeight() * 0.4, pz,
                        1, 0, 0, 0, 0);
            }
        }
    }
}
