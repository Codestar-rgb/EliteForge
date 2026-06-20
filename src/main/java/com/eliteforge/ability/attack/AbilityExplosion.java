package com.eliteforge.ability.attack;

import com.eliteforge.ability.Ability;
import com.eliteforge.ability.AbilityCategory;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import java.util.concurrent.ThreadLocalRandom;

/**
 * AbilityExplosion (爆破) - Chance to create non-block-damaging explosion on attack.
 * 
 * On attack: chance (8% + level * 3%) to create explosion at target position.
 * Explosion power: level * 0.5 (not block-damaging).
 * Smoke/large smoke particles.
 */
public class AbilityExplosion extends Ability {

    public AbilityExplosion() {
        super(
            new ResourceLocation("eliteforge", "explosion"),
            AbilityCategory.ATTACK,
            2.5f
        );
    }

    @Override
    public void onTick(LivingEntity entity, int level) {
        if (entity.level().isClientSide) return;
        if (entity.tickCount % 30 != 0) return;

        if (entity.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.SMOKE,
                    entity.getX(), entity.getY() + entity.getBbHeight() * 0.5, entity.getZ(),
                    2 + level,
                    entity.getBbWidth() * 0.2, entity.getBbHeight() * 0.2, entity.getBbWidth() * 0.2,
                    0.01);
        }
    }

    @Override
    public void onAttack(LivingEntity attacker, LivingEntity target, float damage, int level) {
        if (attacker.level().isClientSide) return;

        float chance = 0.08f + level * 0.03f;
        if (ThreadLocalRandom.current().nextFloat() > chance) return;

        Level level0 = target.level();
        float explosionPower = level * 0.5f;

        // Create non-block-damaging explosion
        // Uses the 7-argument overload: (entity, x, y, z, power, causesFire, interaction)
        level0.explode(null,
                target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(),
                explosionPower,
                false,
                Level.ExplosionInteraction.NONE); // No block damage, no fire

        // Additional direct damage from explosion
        DamageSource explosionDamage = attacker.damageSources().explosion(null, attacker);
        float explosionDamageAmount = level * 1.5f;
        target.hurt(explosionDamage, explosionDamageAmount);

        // Smoke particles
        if (level0 instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE,
                    target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(),
                    20 + level * 5,
                    0.5, 0.5, 0.5,
                    0.05);
            serverLevel.sendParticles(ParticleTypes.SMOKE,
                    target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(),
                    15 + level * 3,
                    0.4, 0.4, 0.4,
                    0.03);
        }
    }
}
