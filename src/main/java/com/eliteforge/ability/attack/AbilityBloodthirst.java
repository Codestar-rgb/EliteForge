package com.eliteforge.ability.attack;

import com.eliteforge.ability.Ability;
import com.eliteforge.ability.AbilityCategory;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;

/**
 * AbilityBloodthirst (嗜血) - Heals a percentage of damage dealt.
 * 
 * Heals (10% + level * 5%) of damage dealt.
 * Blood particle effects on heal.
 */
public class AbilityBloodthirst extends Ability {

    public AbilityBloodthirst() {
        super(
            new ResourceLocation("eliteforge", "bloodthirst"),
            AbilityCategory.ATTACK,
            2.0f
        );
    }

    @Override
    public void onTick(LivingEntity entity, int level) {
        if (entity.level().isClientSide) return;
        if (entity.tickCount % 40 != 0) return;

        // Subtle blood aura particles
        if (entity.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.DAMAGE_INDICATOR,
                    entity.getX(), entity.getY() + entity.getBbHeight() * 0.5, entity.getZ(),
                    1,
                    entity.getBbWidth() * 0.3, entity.getBbHeight() * 0.3, entity.getBbWidth() * 0.3,
                    0.01);
        }
    }

    @Override
    public void onAttack(LivingEntity attacker, LivingEntity target, float damage, int level) {
        if (attacker.level().isClientSide) return;

        // Calculate heal amount: 10% + level * 5% of damage dealt
        float healPercentage = 0.10f + level * 0.05f;
        float healAmount = damage * healPercentage;

        if (healAmount > 0) {
            float newHealth = Math.min(attacker.getHealth() + healAmount, attacker.getMaxHealth());
            attacker.setHealth(newHealth);

            // Blood particle effects on heal
            if (attacker.level() instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ParticleTypes.DAMAGE_INDICATOR,
                        attacker.getX(), attacker.getY() + attacker.getBbHeight() * 0.5, attacker.getZ(),
                        5 + level * 2,
                        0.3, 0.3, 0.3,
                        0.02);
                serverLevel.sendParticles(ParticleTypes.HEART,
                        attacker.getX(), attacker.getY() + attacker.getBbHeight() + 0.3, attacker.getZ(),
                        2 + level,
                        0.2, 0.1, 0.2,
                        0.0);
            }
        }
    }
}
