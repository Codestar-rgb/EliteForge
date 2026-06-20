package com.eliteforge.ability.attack;

import com.eliteforge.ability.Ability;
import com.eliteforge.ability.AbilityCategory;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

import java.util.concurrent.ThreadLocalRandom;

/**
 * AbilityLightning (雷击) - Chance to summon visual lightning on attack.
 * 
 * On attack: chance (10% + level * 5%) to summon lightning at target's position.
 * Lightning deals level * 2 damage (visual + damage, not actual lightning bolt entity).
 * Electric particles around entity.
 */
public class AbilityLightning extends Ability {

    public AbilityLightning() {
        super(
            new ResourceLocation("eliteforge", "lightning"),
            AbilityCategory.ATTACK,
            2.5f
        );
    }

    @Override
    public void onTick(LivingEntity entity, int level) {
        if (entity.level().isClientSide) return;
        if (entity.tickCount % 20 != 0) return;

        if (entity.level() instanceof ServerLevel serverLevel) {
            // Electric spark particles
            serverLevel.sendParticles(ParticleTypes.END_ROD,
                    entity.getX(), entity.getY() + entity.getBbHeight() * 0.75, entity.getZ(),
                    1 + level,
                    entity.getBbWidth() * 0.3, entity.getBbHeight() * 0.2, entity.getBbWidth() * 0.3,
                    0.01);
        }
    }

    @Override
    public void onAttack(LivingEntity attacker, LivingEntity target, float damage, int level) {
        if (attacker.level().isClientSide) return;

        float chance = 0.10f + level * 0.05f;
        if (ThreadLocalRandom.current().nextFloat() > chance) return;

        // Deal lightning damage (not actual lightning bolt to avoid fire/block damage)
        DamageSource lightningDamage = attacker.damageSources().lightningBolt();
        float lightningDamageAmount = level * 2.0f;
        target.hurt(lightningDamage, lightningDamageAmount);

        // Visual lightning effect at target
        if (target.level() instanceof ServerLevel serverLevel) {
            // Flash of electric particles
            serverLevel.sendParticles(ParticleTypes.END_ROD,
                    target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(),
                    15 + level * 5,
                    0.5, 0.5, 0.5,
                    0.1);

            // Simulated lightning bolt visual (vertical line of particles)
            for (int y = 0; y < 10; y++) {
                serverLevel.sendParticles(ParticleTypes.END_ROD,
                        target.getX() + (ThreadLocalRandom.current().nextDouble() - 0.5) * 0.3,
                        target.getY() + y * 1.5,
                        target.getZ() + (ThreadLocalRandom.current().nextDouble() - 0.5) * 0.3,
                        3,
                        0.1, 0.1, 0.1,
                        0.0);
            }

            // Thunder sound via level event
            serverLevel.levelEvent(2002, target.blockPosition(), 0);
        }
    }
}
