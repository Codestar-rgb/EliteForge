package com.eliteforge.ability.defense;

import com.eliteforge.ability.Ability;
import com.eliteforge.ability.AbilityCategory;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;

/**
 * AbilityIronWall (铁壁) - Reduces incoming damage by a percentage.
 * 
 * Reduces incoming damage by (10% + level * 8%).
 * At level V: 50% damage reduction.
 * Shield particle effect when hit.
 */
public class AbilityIronWall extends Ability {

    public AbilityIronWall() {
        super(
            new ResourceLocation("eliteforge", "iron_wall"),
            AbilityCategory.DEFENSE,
            3.0f
        );
    }

    @Override
    public void onApply(LivingEntity entity, int level) {
        if (entity.level().isClientSide) return;
        // No initial setup needed - damage reduction is applied in onHurt
    }

    @Override
    public void onTick(LivingEntity entity, int level) {
        // Passive ability - no periodic effects
    }

    @Override
    public void onHurt(LivingEntity entity, float damage, int level) {
        if (entity.level().isClientSide) return;

        // Damage reduction is now applied directly via LivingHurtEvent
        // in EliteEventHandler.onLivingHurt(). This method only handles
        // visual effects (shield particles when hit).

        // Shield particle effect when hit
        if (entity.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.ENCHANTED_HIT,
                    entity.getX(), entity.getY() + entity.getBbHeight() * 0.5, entity.getZ(),
                    8 + level * 3,
                    entity.getBbWidth() * 0.4, entity.getBbHeight() * 0.4, entity.getBbWidth() * 0.4,
                    0.05);
        }
    }

    /**
     * Gets the damage reduction percentage for a given level.
     * Utility method for external use.
     */
    public static float getDamageReduction(int level) {
        return 0.10f + level * 0.08f;
    }
}
