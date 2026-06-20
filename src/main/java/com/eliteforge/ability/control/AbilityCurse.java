package com.eliteforge.ability.control;

import com.eliteforge.ability.Ability;
import com.eliteforge.ability.AbilityCategory;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

/**
 * AbilityCurse (诅咒) - Applies multiple debuffs to target on attack.
 * 
 * On attack: applies Weakness + Slowness + Unluck to target.
 * Duration: 5s + level * 3s.
 * At level IV+: also prevents natural regen.
 * Witch particles.
 */
public class AbilityCurse extends Ability {

    public AbilityCurse() {
        super(
            new ResourceLocation("eliteforge", "curse"),
            AbilityCategory.CONTROL,
            2.5f
        );
    }

    @Override
    public void onTick(LivingEntity entity, int level) {
        if (entity.level().isClientSide) return;
        if (entity.tickCount % 30 != 0) return;

        if (entity.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.WITCH,
                    entity.getX(), entity.getY() + entity.getBbHeight() * 0.5, entity.getZ(),
                    1 + level / 2,
                    entity.getBbWidth() * 0.2, entity.getBbHeight() * 0.2, entity.getBbWidth() * 0.2,
                    0.01);
        }
    }

    @Override
    public void onAttack(LivingEntity attacker, LivingEntity target, float damage, int level) {
        if (attacker.level().isClientSide) return;

        int durationTicks = 100 + level * 60; // 5s + level * 3s
        int effectLevel = Math.max(0, level - 1);

        // Apply Weakness
        target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, durationTicks, effectLevel, false, true));

        // Apply Slowness
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, durationTicks, effectLevel, false, true));

        // Apply Unluck
        target.addEffect(new MobEffectInstance(MobEffects.UNLUCK, durationTicks, effectLevel, false, true));

        // At level IV+: prevent natural regen
        if (level >= 4) {
            // H5 fix: removed MobEffects.POISON with duration=0 (vanilla silently
            // rejects effects with duration <= 0, so it did nothing). The Hunger
            // effect below is what actually prevents natural regen in vanilla.
            target.addEffect(new MobEffectInstance(MobEffects.HUNGER, durationTicks, 2, false, true));
        }

        // Witch particles at target
        if (target.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.WITCH,
                    target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(),
                    12 + level * 3,
                    0.4, 0.4, 0.4,
                    0.03);
        }
    }
}
