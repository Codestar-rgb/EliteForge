package com.eliteforge.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Immobilize (禁锢) - Complete movement freeze.
 * Sets movement speed to zero, preventing all forms of movement.
 * The entity cannot walk, sprint, or be knocked back.
 * Does NOT prevent turning or attacking.
 */
public class ImmobilizeEffect extends MobEffect {

    private static final String MOVEMENT_SPEED_MODIFIER_UUID = "D5C4E3A2-B1F0-4982-8765-4321FEDCBA98";

    public ImmobilizeEffect() {
        super(MobEffectCategory.HARMFUL, 0x2D2D4E);
        // Add a movement speed modifier that sets speed to 0
        this.addAttributeModifier(Attributes.MOVEMENT_SPEED,
                MOVEMENT_SPEED_MODIFIER_UUID,
                -1.0,
                AttributeModifier.Operation.MULTIPLY_TOTAL);
    }

    @Override
    public void applyEffectTick(LivingEntity entity, int amplifier) {
        if (!entity.level().isClientSide) {
            // Freeze all horizontal movement
            entity.setDeltaMovement(0, entity.getDeltaMovement().y, 0);
            // Prevent jumping
            if (entity.onGround()) {
                entity.setDeltaMovement(entity.getDeltaMovement().x, 0, entity.getDeltaMovement().z);
            }
        }
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        // Apply continuously
        return true;
    }
}
