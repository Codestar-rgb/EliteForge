package com.eliteforge.ability.attack;

import com.eliteforge.ability.Ability;
import com.eliteforge.ability.AbilityCategory;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;

/**
 * AbilityFire (烈焰) - Sets target on fire and displays fire particles.
 * 
 * On attack: Sets target on fire for (3 + level * 2) seconds.
 * On tick: Fire particle effects around the entity.
 */
public class AbilityFire extends Ability {

    public AbilityFire() {
        super(
            new ResourceLocation("eliteforge", "fire"),
            AbilityCategory.ATTACK,
            2.0f
        );
    }

    @Override
    public void onApply(LivingEntity entity, int level) {
        if (entity.level().isClientSide) return;
        spawnFireParticles(entity, level, 10);
    }

    @Override
    public void onTick(LivingEntity entity, int level) {
        if (entity.level().isClientSide) return;
        if (entity.tickCount % 30 != 0) return; // v0.6.0: every 1.5s (was 1s) — less oppressive in long fights

        if (entity.level() instanceof ServerLevel serverLevel) {
            double x = entity.getX();
            double y = entity.getY() + entity.getBbHeight() * 0.5;
            double z = entity.getZ();
            serverLevel.sendParticles(ParticleTypes.FLAME,
                    x, y, z, Math.min(3 + level, 12),
                    entity.getBbWidth() * 0.3, entity.getBbHeight() * 0.3, entity.getBbWidth() * 0.3,
                    0.02);
        }
    }

    @Override
    public void onAttack(LivingEntity attacker, LivingEntity target, float damage, int level) {
        if (attacker.level().isClientSide) return;

        int fireSeconds = 3 + level * 2;
        target.setSecondsOnFire(fireSeconds);

        // Fire burst particles at target
        if (target.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.FLAME,
                    target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(),
                    8 + level * 2,
                    0.3, 0.3, 0.3,
                    0.05);
        }
    }

    private void spawnFireParticles(LivingEntity entity, int level, int count) {
        if (!(entity.level() instanceof ServerLevel serverLevel)) return;
        serverLevel.sendParticles(ParticleTypes.FLAME,
                entity.getX(), entity.getY() + entity.getBbHeight() * 0.5, entity.getZ(),
                count,
                entity.getBbWidth() * 0.5, entity.getBbHeight() * 0.5, entity.getBbWidth() * 0.5,
                0.05);
    }
}
