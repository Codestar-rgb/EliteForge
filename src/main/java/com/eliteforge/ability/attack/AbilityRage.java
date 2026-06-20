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
 * AbilityRage (狂暴) - Entity gains Speed and Strength effects.
 * 
 * Entity gains Speed and Strength effects at the ability level.
 * When health drops below 50%, effects double.
 * Anger particles.
 */
public class AbilityRage extends Ability {

    public AbilityRage() {
        super(
            new ResourceLocation("eliteforge", "rage"),
            AbilityCategory.ATTACK,
            2.5f
        );
    }

    @Override
    public void onApply(LivingEntity entity, int level) {
        if (entity.level().isClientSide) return;
        applyRageEffects(entity, level);
    }

    @Override
    public void onTick(LivingEntity entity, int level) {
        if (entity.level().isClientSide) return;
        if (entity.tickCount % 30 != 0) return;

        // Re-apply effects to keep them active
        applyRageEffects(entity, level);

        // Anger particles
        if (entity.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.ANGRY_VILLAGER,
                    entity.getX(), entity.getY() + entity.getBbHeight() + 0.2, entity.getZ(),
                    1 + level / 2,
                    entity.getBbWidth() * 0.3, 0.2, entity.getBbWidth() * 0.3,
                    0.0);
        }
    }

    @Override
    public void onHurt(LivingEntity entity, float damage, int level) {
        if (entity.level().isClientSide) return;

        // When health drops below 50%, effects double
        if (entity.getHealth() - damage < entity.getMaxHealth() * 0.5f) {
            applyEnragedEffects(entity, level);

            if (entity.level() instanceof ServerLevel serverLevel) {
                // Burst of anger particles when enraged
                serverLevel.sendParticles(ParticleTypes.ANGRY_VILLAGER,
                        entity.getX(), entity.getY() + entity.getBbHeight() + 0.3, entity.getZ(),
                        5 + level * 2,
                        entity.getBbWidth() * 0.5, 0.3, entity.getBbWidth() * 0.5,
                        0.02);
            }
        }
    }

    /**
     * Applies normal rage effects (Speed and Strength at ability level).
     */
    private void applyRageEffects(LivingEntity entity, int level) {
        int speedAmplifier = Math.max(0, level - 1); // Speed level I at ability level 1
        int strengthAmplifier = Math.max(0, level - 1); // Strength level I at ability level 1

        entity.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 60, speedAmplifier, false, false));
        entity.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 60, strengthAmplifier, false, false));
    }

    /**
     * Applies enraged effects (doubled) when health drops below 50%.
     */
    private void applyEnragedEffects(LivingEntity entity, int level) {
        int speedAmplifier = Math.min(Math.max(0, (level * 2) - 1), 4); // Doubled level, capped
        int strengthAmplifier = Math.min(Math.max(0, (level * 2) - 1), 4);

        entity.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 100, speedAmplifier, false, true));
        entity.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 100, strengthAmplifier, false, true));
    }
}
