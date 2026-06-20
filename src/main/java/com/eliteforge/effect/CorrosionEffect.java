package com.eliteforge.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

/**
 * Corrosion (腐蚀) - Damage over time effect that bypasses armor.
 * Deals percentage-based damage each tick, making it effective
 * against heavily armored targets. The damage ignores conventional
 * armor calculations.
 */
public class CorrosionEffect extends MobEffect {

    public CorrosionEffect() {
        super(MobEffectCategory.HARMFUL, 0x4E9425);
    }

    @Override
    public void applyEffectTick(LivingEntity entity, int amplifier) {
        if (!entity.level().isClientSide) {
            // Deal percentage-based damage that bypasses armor
            // Base: 2% of max health per tick, +0.5% per amplifier level
            float maxHealth = entity.getMaxHealth();
            float damagePercentage = 0.02f + (amplifier * 0.005f);
            float damage = maxHealth * damagePercentage;

            // Bypass armor by using magic damage source (also bypasses armor, semantically correct for corrosion)
            entity.hurt(entity.level().damageSources().magic(), damage);
        }
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        // Apply effect every 40 ticks (2 seconds)
        int tickInterval = 40 >> amplifier;
        if (tickInterval < 10) tickInterval = 10;
        return duration % tickInterval == 0;
    }
}
