package com.eliteforge.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;



/**
 * Mutation (变异) - Applies random effects to the target each tick.
 * Can apply both positive and negative effects, creating unpredictable
 * outcomes. Used by legendary-ability elites to create chaos.
 *
 * Positive effects: Speed, Jump Boost, Strength, Resistance, Regeneration
 * Negative effects: Slowness, Weakness, Poison, Wither, Nausea
 */
public class MutationEffect extends MobEffect {

    private static final MobEffect[] POSITIVE_EFFECTS = {
            MobEffects.MOVEMENT_SPEED,
            MobEffects.JUMP,
            MobEffects.DAMAGE_BOOST,
            MobEffects.DAMAGE_RESISTANCE,
            MobEffects.REGENERATION
    };

    private static final MobEffect[] NEGATIVE_EFFECTS = {
            MobEffects.MOVEMENT_SLOWDOWN,
            MobEffects.WEAKNESS,
            MobEffects.POISON,
            MobEffects.WITHER,
            MobEffects.CONFUSION
    };

    public MutationEffect() {
        super(MobEffectCategory.HARMFUL, 0x8B4513);
    }

    @Override
    public void applyEffectTick(LivingEntity entity, int amplifier) {
        if (!entity.level().isClientSide) {
            // Chance to apply a random effect, capped at 100%
            float chance = Math.min(1.0f, 0.3f + (amplifier * 0.1f));
            if (entity.getRandom().nextFloat() < chance) {
                boolean isPositive = entity.getRandom().nextFloat() < 0.3f; // 30% positive, 70% negative

                MobEffect[] pool = isPositive ? POSITIVE_EFFECTS : NEGATIVE_EFFECTS;
                MobEffect chosenEffect = pool[entity.getRandom().nextInt(pool.length)];

                // Duration: 60-120 ticks, Level: 0 to amplifier
                int duration = 60 + entity.getRandom().nextInt(60);
                int level = Math.min(amplifier, entity.getRandom().nextInt(Math.max(1, amplifier + 1)));

                entity.addEffect(new MobEffectInstance(chosenEffect, duration, level));
            }
        }
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        // Apply effect every 30 ticks (1.5 seconds)
        int tickInterval = Math.max(10, 30 - (amplifier * 5));
        return duration % tickInterval == 0;
    }
}
