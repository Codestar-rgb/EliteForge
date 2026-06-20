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
 * AbilityDeathTouch (死触) - Chance to deal massive damage based on target's max health.
 * 
 * On attack: chance (5% + level * 3%) to deal massive damage (50% + level * 10% of target max health).
 * Instant kill at level V for mobs with <50% health.
 * Wither particles on hit.
 */
public class AbilityDeathTouch extends Ability {

    public AbilityDeathTouch() {
        super(
            new ResourceLocation("eliteforge", "death_touch"),
            AbilityCategory.ATTACK,
            4.0f
        );
    }

    @Override
    public void onTick(LivingEntity entity, int level) {
        if (entity.level().isClientSide) return;
        if (entity.tickCount % 25 != 0) return;

        if (entity.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.SQUID_INK,
                    entity.getX(), entity.getY() + entity.getBbHeight() * 0.5, entity.getZ(),
                    1 + level,
                    entity.getBbWidth() * 0.3, entity.getBbHeight() * 0.3, entity.getBbWidth() * 0.3,
                    0.01);
        }
    }

    @Override
    public void onAttack(LivingEntity attacker, LivingEntity target, float damage, int level) {
        if (attacker.level().isClientSide) return;

        float chance = 0.05f + level * 0.03f;
        if (ThreadLocalRandom.current().nextFloat() > chance) return;

        float targetMaxHealth = target.getMaxHealth();
        float targetCurrentHealth = target.getHealth();

        // Instant kill at level V for mobs with <50% health
        if (level >= 5 && targetCurrentHealth < targetMaxHealth * 0.5f) {
            DamageSource deathDamage = attacker.damageSources().wither();
            target.hurt(deathDamage, targetCurrentHealth + 100); // Overkill to ensure death
        } else {
            // Massive damage: 50% + level * 10% of target max health
            float healthPercentage = 0.5f + level * 0.1f;
            float deathTouchDamage = targetMaxHealth * healthPercentage;

            DamageSource deathDamage = attacker.damageSources().wither();
            target.hurt(deathDamage, deathTouchDamage);
        }

        // Wither particles burst
        if (target.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.SQUID_INK,
                    target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(),
                    20 + level * 5,
                    0.6, 0.6, 0.6,
                    0.05);
        }
    }
}
