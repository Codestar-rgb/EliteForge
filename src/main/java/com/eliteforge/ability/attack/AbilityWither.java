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
 * AbilityWither (凋零) - Applies Wither effect to target on attack.
 * 
 * Applies Wither effect to target (level * 4s duration, Wither level min(level-1, 2)).
 * Dark particle effects.
 */
public class AbilityWither extends Ability {

    public AbilityWither() {
        super(
            new ResourceLocation("eliteforge", "wither"),
            AbilityCategory.ATTACK,
            2.0f
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
                    entity.getBbWidth() * 0.2, entity.getBbHeight() * 0.2, entity.getBbWidth() * 0.2,
                    0.005);
        }
    }

    @Override
    public void onAttack(LivingEntity attacker, LivingEntity target, float damage, int level) {
        if (attacker.level().isClientSide) return;

        int durationTicks = level * 80; // level * 4 seconds at 20 ticks/second
        int witherLevel = Math.min(level - 1, 2); // Wither amplifier 0, 1, or 2 (min level 1 → amp 0)

        if (durationTicks > 0 && witherLevel >= 0) {
            target.addEffect(new MobEffectInstance(MobEffects.WITHER, durationTicks, witherLevel));
        }

        // Dark particle burst at target
        if (target.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.SQUID_INK,
                    target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(),
                    10 + level * 3,
                    0.4, 0.4, 0.4,
                    0.03);
            serverLevel.sendParticles(ParticleTypes.SMOKE,
                    target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(),
                    5 + level * 2,
                    0.3, 0.3, 0.3,
                    0.02);
        }
    }
}
