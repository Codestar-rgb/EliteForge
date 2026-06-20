package com.eliteforge.ability.defense;

import com.eliteforge.ability.Ability;
import com.eliteforge.ability.AbilityCategory;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;

/**
 * AbilityRegen (再生) - Regenerates health over time.
 * 
 * Regenerates (level * 0.5) health per second.
 * At level V: 2.5 HP/s.
 * Heart particles.
 */
public class AbilityRegen extends Ability {

    public AbilityRegen() {
        super(
            new ResourceLocation("eliteforge", "regen"),
            AbilityCategory.DEFENSE,
            2.5f
        );
    }

    @Override
    public void onTick(LivingEntity entity, int level) {
        if (entity.level().isClientSide) return;
        if (entity.tickCount % 20 != 0) return; // Once per second

        // Heal level * 0.5 HP per second
        float healAmount = level * 0.5f;
        if (entity.getHealth() < entity.getMaxHealth()) {
            float newHealth = Math.min(entity.getHealth() + healAmount, entity.getMaxHealth());
            entity.setHealth(newHealth);

            // Heart particles when healing
            if (entity.level() instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ParticleTypes.HEART,
                        entity.getX(), entity.getY() + entity.getBbHeight() + 0.3, entity.getZ(),
                        1 + level / 2,
                        entity.getBbWidth() * 0.3, 0.2, entity.getBbWidth() * 0.3,
                        0.0);
            }
        }
    }

    @Override
    public void onHurt(LivingEntity entity, float damage, int level) {
        // No special behavior on hurt - regeneration is passive
    }
}
