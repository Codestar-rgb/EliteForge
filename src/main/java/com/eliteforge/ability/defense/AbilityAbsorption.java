package com.eliteforge.ability.defense;

import com.eliteforge.ability.Ability;
import com.eliteforge.ability.AbilityCategory;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;

import java.util.concurrent.ThreadLocalRandom;

/**
 * AbilityAbsorption (汲取) - Chance to steal health from attacker on hit.
 * 
 * On being hit: chance (10% + level * 5%) to steal 1-2 hearts from attacker.
 * Red particle beam effect.
 */
public class AbilityAbsorption extends Ability {

    public AbilityAbsorption() {
        super(
            new ResourceLocation("eliteforge", "absorption"),
            AbilityCategory.DEFENSE,
            2.0f
        );
    }

    @Override
    public void onTick(LivingEntity entity, int level) {
        // Passive - activated on hurt
    }

    @Override
    public void onHurt(LivingEntity entity, float damage, int level) {
        if (entity.level().isClientSide) return;

        float chance = 0.10f + level * 0.05f;
        if (ThreadLocalRandom.current().nextFloat() > chance) return;

        // Find the attacker
        var lastSource = entity.getLastDamageSource();
        if (lastSource == null || !(lastSource.getEntity() instanceof LivingEntity attacker)) return;

        // Steal 1-2 hearts (2-4 HP) from attacker
        float stolenHearts = (1 + ThreadLocalRandom.current().nextInt(2)) * 2.0f; // 2 or 4 HP

        // Drain from attacker
        float attackerHealth = attacker.getHealth();
        if (attackerHealth > stolenHearts) {
            attacker.setHealth(attackerHealth - stolenHearts);
        } else {
            attacker.setHealth(1.0f); // Don't kill the attacker with absorption
        }

        // Heal self
        float newHealth = Math.min(entity.getHealth() + stolenHearts, entity.getMaxHealth());
        entity.setHealth(newHealth);

        // Red particle beam effect
        if (entity.level() instanceof ServerLevel serverLevel) {
            // Particles around entity (gaining health)
            serverLevel.sendParticles(ParticleTypes.DAMAGE_INDICATOR,
                    entity.getX(), entity.getY() + entity.getBbHeight() * 0.5, entity.getZ(),
                    8 + level * 2,
                    entity.getBbWidth() * 0.3, entity.getBbHeight() * 0.3, entity.getBbWidth() * 0.3,
                    0.05);

            // Particles around attacker (losing health)
            serverLevel.sendParticles(ParticleTypes.DAMAGE_INDICATOR,
                    attacker.getX(), attacker.getY() + attacker.getBbHeight() * 0.5, attacker.getZ(),
                    5 + level,
                    attacker.getBbWidth() * 0.3, attacker.getBbHeight() * 0.3, attacker.getBbWidth() * 0.3,
                    0.05);
        }
    }
}
