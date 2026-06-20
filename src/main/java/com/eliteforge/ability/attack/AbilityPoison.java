package com.eliteforge.ability.attack;

import com.eliteforge.ability.Ability;
import com.eliteforge.ability.AbilityCategory;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

/**
 * AbilityPoison (剧毒) - Applies Poison effect to target on attack.
 * 
 * Applies Poison effect to target (level * 3s duration, Poison level min(level, 2)).
 * Green particle cloud around entity.
 */
public class AbilityPoison extends Ability {

    public AbilityPoison() {
        super(
            new ResourceLocation("eliteforge", "poison"),
            AbilityCategory.ATTACK,
            1.5f
        );
    }

    @Override
    public void onTick(LivingEntity entity, int level) {
        if (entity.level().isClientSide) return;
        if (entity.tickCount % 30 != 0) return; // v0.6.0: every 1.5s (was 1s) — gives players breathing room

        if (entity.level() instanceof ServerLevel serverLevel) {
            // Green particle cloud
            serverLevel.sendParticles(ParticleTypes.ENTITY_EFFECT,
                    entity.getX(), entity.getY() + entity.getBbHeight() * 0.5, entity.getZ(),
                    2 + level,
                    entity.getBbWidth() * 0.3, entity.getBbHeight() * 0.3, entity.getBbWidth() * 0.3,
                    0.0); // Green color via entity effect
        }
    }

    @Override
    public void onAttack(LivingEntity attacker, LivingEntity target, float damage, int level) {
        if (attacker.level().isClientSide) return;

        int durationTicks = level * 60; // level * 3 seconds at 20 ticks/second
        int poisonLevel = Math.min(level, 2); // Poison amplifier 0, 1, or 2

        target.addEffect(new MobEffectInstance(MobEffects.POISON, durationTicks, poisonLevel));

        // Green particle burst at target
        if (target.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.ENTITY_EFFECT,
                    target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(),
                    10 + level * 3,
                    0.4, 0.4, 0.4,
                    0.0);
            serverLevel.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                    target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(),
                    8 + level * 2,
                    0.3, 0.3, 0.3,
                    0.02);
        }
    }
}
