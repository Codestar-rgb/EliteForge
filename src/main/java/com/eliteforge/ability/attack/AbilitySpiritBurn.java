package com.eliteforge.ability.attack;

import com.eliteforge.ability.Ability;
import com.eliteforge.ability.AbilityCategory;
import com.eliteforge.init.ModEffects;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

/**
 * AbilitySpiritBurn (灵燃) - Applies spirit burn that deals magic damage bypassing everything.
 * 
 * On attack: Applies spirit burn effect to target (duration: 4s + level * 2s).
 * Spirit burn deals magic damage (level * 0.5 per second), bypasses everything.
 * Purple/blue flame particles.
 */
public class AbilitySpiritBurn extends Ability {

    public AbilitySpiritBurn() {
        super(
            new ResourceLocation("eliteforge", "spirit_burn"),
            AbilityCategory.ATTACK,
            3.0f
        );
    }

    @Override
    public void onTick(LivingEntity entity, int level) {
        if (entity.level().isClientSide) return;
        if (entity.tickCount % 15 != 0) return;

        if (entity.level() instanceof ServerLevel serverLevel) {
            // Purple/blue flame particles
            serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                    entity.getX(), entity.getY() + entity.getBbHeight() * 0.5, entity.getZ(),
                    2 + level,
                    entity.getBbWidth() * 0.3, entity.getBbHeight() * 0.3, entity.getBbWidth() * 0.3,
                    0.02);
        }
    }

    @Override
    public void onAttack(LivingEntity attacker, LivingEntity target, float damage, int level) {
        if (attacker.level().isClientSide) return;

        int durationTicks = 80 + level * 40; // 4s + level * 2s

        // Apply custom Spirit Burn effect for proper magic damage that bypasses resistances
        int burnAmplifier = Math.max(0, level - 1);
        target.addEffect(new MobEffectInstance(ModEffects.SPIRIT_BURN_EFFECT.get(), durationTicks, burnAmplifier));

        // Apply immediate burst of magic damage
        DamageSource magicDamage = attacker.damageSources().magic();
        float burstDamage = level * 0.5f * 2; // Initial burst damage
        target.hurt(magicDamage, burstDamage);

        // Purple/blue flame particles at target
        if (target.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                    target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(),
                    12 + level * 3,
                    0.4, 0.4, 0.4,
                    0.05);
            // Additional DRAGON_BREATH particles for the purple/blue effect
            serverLevel.sendParticles(ParticleTypes.DRAGON_BREATH,
                    target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(),
                    6 + level * 2,
                    0.3, 0.3, 0.3,
                    0.02);
        }
    }
}
