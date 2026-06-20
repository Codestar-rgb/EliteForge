package com.eliteforge.ability.defense;

import com.eliteforge.ability.Ability;
import com.eliteforge.ability.AbilityCategory;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

import java.util.Set;

/**
 * AbilityImmunity (免疫) - Immune to random negative effects based on level.
 * 
 * Level I: immune to Poison
 * Level II: +Wither
 * Level III: +Slowness
 * Level IV: +Weakness
 * Level V: all negative effects
 * Sparkle particles when blocking effect.
 */
public class AbilityImmunity extends Ability {

    public AbilityImmunity() {
        super(
            new ResourceLocation("eliteforge", "immunity"),
            AbilityCategory.DEFENSE,
            3.0f
        );
    }

    @Override
    public void onApply(LivingEntity entity, int level) {
        if (entity.level().isClientSide) return;
        // Immunity is checked reactively in onTick
    }

    @Override
    public void onTick(LivingEntity entity, int level) {
        if (entity.level().isClientSide) return;
        if (entity.tickCount % 10 != 0) return; // Check every 0.5 seconds

        boolean blocked = false;

        if (level >= 5) {
            // Level V: Remove ALL negative effects
            Set<MobEffect> activeEffects = entity.getActiveEffectsMap().keySet();
            for (MobEffect effect : Set.copyOf(activeEffects)) {
                if (!effect.isBeneficial()) {
                    entity.removeEffect(effect);
                    blocked = true;
                }
            }
        } else {
            // Levels I-IV: Remove specific effects
            if (level >= 1 && entity.hasEffect(MobEffects.POISON)) {
                entity.removeEffect(MobEffects.POISON);
                blocked = true;
            }
            if (level >= 2 && entity.hasEffect(MobEffects.WITHER)) {
                entity.removeEffect(MobEffects.WITHER);
                blocked = true;
            }
            if (level >= 3 && entity.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)) {
                entity.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
                blocked = true;
            }
            if (level >= 4 && entity.hasEffect(MobEffects.WEAKNESS)) {
                entity.removeEffect(MobEffects.WEAKNESS);
                blocked = true;
            }
        }

        // Sparkle particles when blocking an effect
        if (blocked && entity.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.END_ROD,
                    entity.getX(), entity.getY() + entity.getBbHeight() * 0.5, entity.getZ(),
                    6 + level * 2,
                    entity.getBbWidth() * 0.3, entity.getBbHeight() * 0.3, entity.getBbWidth() * 0.3,
                    0.02);
        }
    }

    @Override
    public void onHurt(LivingEntity entity, float damage, int level) {
        // Immunity check is handled in onTick, not onHurt
    }
}
