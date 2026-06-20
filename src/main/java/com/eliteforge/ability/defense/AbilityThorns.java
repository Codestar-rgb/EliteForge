package com.eliteforge.ability.defense;

import com.eliteforge.ability.Ability;
import com.eliteforge.ability.AbilityCategory;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * AbilityThorns (荆棘) - Reflects a percentage of damage back to the attacker.
 * 
 * Reflects (20% + level * 10%) of damage back to attacker.
 * At level V: 70% reflection.
 * Thorns particle effect.
 */
public class AbilityThorns extends Ability {

    public AbilityThorns() {
        super(
            new ResourceLocation("eliteforge", "thorns"),
            AbilityCategory.DEFENSE,
            2.5f
        );
    }

    @Override
    public void onTick(LivingEntity entity, int level) {
        // Passive ability - no periodic effects
    }

    @Override
    public void onHurt(LivingEntity entity, float damage, int level) {
        if (entity.level().isClientSide) return;

        // Calculate reflection: 20% + level * 10%
        float reflectPercentage = 0.20f + level * 0.10f;
        float reflectDamage = damage * reflectPercentage;

        // Find the last damage source attacker
        DamageSource lastSource = entity.getLastDamageSource();
        if (lastSource != null && lastSource.getEntity() instanceof LivingEntity attacker) {
            attacker.hurt(entity.damageSources().thorns(entity), reflectDamage);

            // Thorns particle effect
            if (entity.level() instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ParticleTypes.CRIT,
                        entity.getX(), entity.getY() + entity.getBbHeight() * 0.5, entity.getZ(),
                        6 + level * 2,
                        entity.getBbWidth() * 0.4, entity.getBbHeight() * 0.4, entity.getBbWidth() * 0.4,
                        0.1);
            }
        }
    }
}
