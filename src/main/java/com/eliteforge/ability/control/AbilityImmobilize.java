package com.eliteforge.ability.control;

import com.eliteforge.ability.Ability;
import com.eliteforge.ability.AbilityCategory;
import com.eliteforge.init.ModEffects;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

import java.util.concurrent.ThreadLocalRandom;

/**
 * AbilityImmobilize (禁锢) - Chance to completely freeze target on attack.
 * 
 * On attack: chance (10% + level * 5%) to completely freeze target for (1 + level * 0.5)s.
 * Frozen target can't move, attack, or use items.
 * Barrier particles around target.
 */
public class AbilityImmobilize extends Ability {

    public AbilityImmobilize() {
        super(
            new ResourceLocation("eliteforge", "immobilize"),
            AbilityCategory.CONTROL,
            2.5f
        );
    }

    @Override
    public void onTick(LivingEntity entity, int level) {
        // Immobilize triggers on attack, not passively
    }

    @Override
    public void onAttack(LivingEntity attacker, LivingEntity target, float damage, int level) {
        if (attacker.level().isClientSide) return;

        float chance = 0.10f + level * 0.05f;
        if (ThreadLocalRandom.current().nextFloat() > chance) return;

        // Freeze duration: (1 + level * 0.5) seconds
        int freezeDurationTicks = (int) ((1 + level * 0.5) * 20);

        // Apply custom Immobilize effect for proper movement freeze
        int immobilizeAmplifier = Math.max(0, level - 1);
        target.addEffect(new MobEffectInstance(ModEffects.IMMOBILIZE_EFFECT.get(), freezeDurationTicks, immobilizeAmplifier, false, true));

        // Barrier particles around target
        if (target.level() instanceof ServerLevel serverLevel) {
            // Ring of barrier particles
            for (int i = 0; i < 8 + level * 2; i++) {
                double angle = (Math.PI * 2 * i) / (8 + level * 2);
                double radius = 1.0;
                double px = target.getX() + Math.cos(angle) * radius;
                double pz = target.getZ() + Math.sin(angle) * radius;
                for (double y = 0; y < target.getBbHeight(); y += 0.5) {
                    serverLevel.sendParticles(ParticleTypes.CRIT,
                            px, target.getY() + y, pz, 1,
                            0, 0, 0, 0);
                }
            }
        }
    }
}
