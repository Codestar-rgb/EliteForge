package com.eliteforge.ability.defense;

import com.eliteforge.ability.Ability;
import com.eliteforge.ability.AbilityCategory;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;

/**
 * AbilityEvade (闪避) - Chance to completely dodge an attack.
 * 
 * Chance (5% + level * 7%) to completely dodge an attack.
 * At level V: 40% dodge chance.
 * Cloud particles on dodge.
 */
public class AbilityEvade extends Ability {

    public AbilityEvade() {
        super(
            new ResourceLocation("eliteforge", "evade"),
            AbilityCategory.DEFENSE,
            2.5f
        );
    }

    @Override
    public void onTick(LivingEntity entity, int level) {
        // Passive ability - dodge is checked in onHurt
    }

    @Override
    public void onHurt(LivingEntity entity, float damage, int level) {
        if (entity.level().isClientSide) return;

        // Dodge chance is now handled directly via LivingHurtEvent
        // in EliteEventHandler.onLivingHurt(). This method only handles
        // visual effects (cloud particles on successful dodge).
        // Note: Particles are shown by the event handler when dodge triggers.

        // No additional logic needed here — the event handler cancels
        // the damage and calls this method for particle effects only.
        if (entity.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.CLOUD,
                    entity.getX(), entity.getY() + entity.getBbHeight() * 0.5, entity.getZ(),
                    8 + level * 2,
                    entity.getBbWidth() * 0.4, entity.getBbHeight() * 0.4, entity.getBbWidth() * 0.4,
                    0.05);
        }
    }

    /**
     * Gets the dodge chance for a given level.
     */
    public static float getDodgeChance(int level) {
        return 0.05f + level * 0.07f;
    }
}
