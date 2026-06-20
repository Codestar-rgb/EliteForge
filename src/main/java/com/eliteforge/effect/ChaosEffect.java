package com.eliteforge.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;



/**
 * Chaos (混沌) - Applies random negative effects to the target each tick.
 * Unlike Mutation, this only applies harmful effects, making it purely detrimental.
 * Used by legendary-ability elites to overwhelm players with debuffs.
 *
 * Possible effects: Slowness, Mining Fatigue, Weakness, Blindness,
 * Hunger, Nausea, Poison, Wither, Levitation, Darkness
 */
public class ChaosEffect extends MobEffect {

    private static final MobEffect[] CHAOS_EFFECTS = {
            MobEffects.MOVEMENT_SLOWDOWN,
            MobEffects.DIG_SLOWDOWN,
            MobEffects.WEAKNESS,
            MobEffects.BLINDNESS,
            MobEffects.HUNGER,
            MobEffects.CONFUSION,
            MobEffects.POISON,
            MobEffects.WITHER,
            MobEffects.LEVITATION,
            MobEffects.DARKNESS
    };

    public ChaosEffect() {
        super(MobEffectCategory.HARMFUL, 0x4B0082);
    }

    @Override
    public void applyEffectTick(LivingEntity entity, int amplifier) {
        if (!entity.level().isClientSide) {
            // Apply 1-2 random negative effects each interval
            int effectCount = 1 + (amplifier > 2 ? 1 : 0);

            for (int i = 0; i < effectCount; i++) {
                MobEffect chosenEffect = CHAOS_EFFECTS[entity.getRandom().nextInt(CHAOS_EFFECTS.length)];

                // Don't stack the same effect - check if entity already has it
                if (entity.hasEffect(chosenEffect)) {
                    continue;
                }

                // Duration: 40-100 ticks, Level: 0 to amplifier
                int duration = 40 + entity.getRandom().nextInt(60);
                int level = Math.min(amplifier, entity.getRandom().nextInt(Math.max(1, amplifier + 1)));

                entity.addEffect(new MobEffectInstance(chosenEffect, duration, level));
            }
        }
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        // Apply effect every 40 ticks (2 seconds)
        int tickInterval = Math.max(10, 40 - (amplifier * 5));
        return duration % tickInterval == 0;
    }
}
