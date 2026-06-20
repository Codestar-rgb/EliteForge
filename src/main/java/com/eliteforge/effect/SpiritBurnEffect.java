package com.eliteforge.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

/**
 * Spirit Burn (灵燃) - Magic damage over time effect.
 * Deals flat magic damage that ignores conventional resistances.
 * The damage scales with the amplifier level.
 */
public class SpiritBurnEffect extends MobEffect {

    public SpiritBurnEffect() {
        super(MobEffectCategory.HARMFUL, 0x7B2FBE);
    }

    @Override
    public void applyEffectTick(LivingEntity entity, int amplifier) {
        if (!entity.level().isClientSide) {
            // Deal flat magic damage that bypasses armor
            // Base: 1.0 damage per tick, +0.5 per amplifier level
            float damage = 1.0f + (amplifier * 0.5f);
            entity.hurt(entity.level().damageSources().magic(), damage);
        }
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        // Apply effect every 30 ticks (1.5 seconds)
        int tickInterval = 30 >> amplifier;
        if (tickInterval < 10) tickInterval = 10;
        return duration % tickInterval == 0;
    }
}
