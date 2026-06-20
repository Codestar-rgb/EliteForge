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
 * AbilityCorrosion (腐蚀) - Applies corrosion effect that deals armor-bypassing damage.
 * 
 * On attack: Applies a custom corrosion effect to the target.
 * Duration: 5s + level * 3s (100 + level * 60 ticks).
 * Corrosion deals 1 damage/second, bypasses armor.
 * Green particle effects around the entity.
 */
public class AbilityCorrosion extends Ability {

    public AbilityCorrosion() {
        super(
            new ResourceLocation("eliteforge", "corrosion"),
            AbilityCategory.ATTACK,
            2.5f
        );
    }

    @Override
    public void onTick(LivingEntity entity, int level) {
        if (entity.level().isClientSide) return;
        if (entity.tickCount % 30 != 0) return; // Every 1.5 seconds

        if (entity.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                    entity.getX(), entity.getY() + entity.getBbHeight() * 0.5, entity.getZ(),
                    2 + level,
                    entity.getBbWidth() * 0.3, entity.getBbHeight() * 0.3, entity.getBbWidth() * 0.3,
                    0.02);
        }
    }

    @Override
    public void onAttack(LivingEntity attacker, LivingEntity target, float damage, int level) {
        if (attacker.level().isClientSide) return;

        int durationTicks = 100 + level * 60; // 5s + level * 3s at 20 ticks/second

        // Apply custom Corrosion effect for proper armor-bypassing % health damage
        int corrosionAmplifier = Math.max(0, level - 1);
        target.addEffect(new MobEffectInstance(ModEffects.CORROSION_EFFECT.get(), durationTicks, corrosionAmplifier));

        // Direct armor-bypassing damage on hit
        DamageSource corrosionDamage = attacker.damageSources().magic();
        float corrosionDamageAmount = level; // level damage on initial hit
        target.hurt(corrosionDamage, corrosionDamageAmount);

        // Green particle burst at target
        if (target.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                    target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(),
                    10 + level * 3,
                    0.4, 0.4, 0.4,
                    0.03);
        }
    }
}
