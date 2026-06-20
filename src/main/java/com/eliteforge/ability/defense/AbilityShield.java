package com.eliteforge.ability.defense;

import com.eliteforge.ability.Ability;
import com.eliteforge.ability.AbilityCategory;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

/**
 * AbilityShield (护盾) - Periodically gains absorption health.
 *
 * Every (300 - level * 40) ticks, gains absorption health equal to level * 2 hearts.
 * Golden particle effect when shield activates.
 */
public class AbilityShield extends Ability {

    public AbilityShield() {
        super(
            new ResourceLocation("eliteforge", "shield"),
            AbilityCategory.DEFENSE,
            2.5f
        );
    }

    @Override
    public void onApply(LivingEntity entity, int level) {
        if (entity.level().isClientSide) return;
        // Apply initial shield
        applyShield(entity, level);
    }

    @Override
    public void onTick(LivingEntity entity, int level) {
        if (entity.level().isClientSide) return;

        int cooldown = Math.max(60, 300 - level * 40);
        // Use entity.tickCount (Minecraft's built-in tick counter) instead of the
        // deprecated AbilityManager NBT-based counter (which always returned 0).
        if (entity.tickCount > 20 && entity.tickCount % cooldown == 0) {
            applyShield(entity, level);
        }
    }

    /**
     * Applies absorption shield to the entity.
     */
    private void applyShield(LivingEntity entity, int level) {
        float shieldHearts = level * 2.0f;
        int durationTicks = cooldownTicks(level) + 100; // Lasts until next refresh + buffer

        entity.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, durationTicks, level - 1, false, true));

        // Golden particle effect
        if (entity.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.ENCHANT,
                    entity.getX(), entity.getY() + entity.getBbHeight() * 0.5, entity.getZ(),
                    10 + level * 3,
                    entity.getBbWidth() * 0.5, entity.getBbHeight() * 0.5, entity.getBbWidth() * 0.5,
                    0.5);
        }
    }

    private int cooldownTicks(int level) {
        return Math.max(60, 300 - level * 40);
    }
}
