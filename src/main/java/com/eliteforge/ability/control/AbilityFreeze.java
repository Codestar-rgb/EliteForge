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
 * AbilityFreeze (冰冻) - Applies Slowness + Mining Fatigue, and freeze at higher levels.
 * 
 * Applies Slowness + Mining Fatigue to target on attack.
 * At level III+: also applies custom freeze (can't move for 1s).
 * Snowflake + ice block particles.
 */
public class AbilityFreeze extends Ability {

    public AbilityFreeze() {
        super(
            new ResourceLocation("eliteforge", "freeze"),
            AbilityCategory.CONTROL,
            2.0f
        );
    }

    @Override
    public void onTick(LivingEntity entity, int level) {
        if (entity.level().isClientSide) return;
        if (entity.tickCount % 25 != 0) return;

        if (entity.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.SNOWFLAKE,
                    entity.getX(), entity.getY() + entity.getBbHeight() * 0.5, entity.getZ(),
                    1 + level,
                    entity.getBbWidth() * 0.2, entity.getBbHeight() * 0.2, entity.getBbWidth() * 0.2,
                    0.01);
        }
    }

    @Override
    public void onAttack(LivingEntity attacker, LivingEntity target, float damage, int level) {
        if (attacker.level().isClientSide) return;

        int durationTicks = 60 + level * 20; // Base duration: 3s + level * 1s

        // Apply Slowness
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, durationTicks, Math.min(level, 4), false, true));

        // Apply Mining Fatigue
        target.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, durationTicks, Math.min(level, 4), false, true));

        // At level III+: apply custom freeze (can't move for 1s = 20 ticks)
        if (level >= 3) {
            // Use maximum slowness to simulate freeze.
            // Slowness amplifier 10 (level 11) reduces movement speed by ~100%,
            // effectively freezing the target in place.
            // Note: We do NOT apply JUMP boost — a high amplifier JUMP effect would
            // actually launch the target into the air (vanilla jump formula:
            // motionY = 0.42 + 0.1 * (amp+1)). The high slowness is sufficient
            // to prevent meaningful movement including jump distance.
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 10, false, true));
        }

        // Snowflake + ice block particles
        if (target.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.SNOWFLAKE,
                    target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(),
                    10 + level * 3,
                    0.4, 0.4, 0.4,
                    0.03);

            if (level >= 3) {
                // Ice block particles for freeze effect
                serverLevel.sendParticles(ParticleTypes.ITEM_SNOWBALL,
                        target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(),
                        15 + level * 3,
                        0.3, 0.5, 0.3,
                        0.05);
            }
        }
    }
}
