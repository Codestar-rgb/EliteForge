package com.eliteforge.ability.defense;

import com.eliteforge.ability.Ability;
import com.eliteforge.ability.AbilityCategory;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * AbilityArmor (铁甲) - Permanent Resistance effect and armor toughness.
 * 
 * Permanent Resistance effect (level - 1, min 0).
 * At level III+: also grants 2 armor toughness per level above II.
 * Passive ability - no particle effects.
 */
public class AbilityArmor extends Ability {

    public AbilityArmor() {
        super(
            new ResourceLocation("eliteforge", "armor"),
            AbilityCategory.DEFENSE,
            2.0f
        );
    }

    @Override
    public void onApply(LivingEntity entity, int level) {
        if (entity.level().isClientSide) return;
        applyArmorEffects(entity, level);
    }

    @Override
    public void onTick(LivingEntity entity, int level) {
        if (entity.level().isClientSide) return;
        if (entity.tickCount % 40 != 0) return; // Re-apply every 2 seconds

        applyArmorEffects(entity, level);
    }

    @Override
    public void onRemove(LivingEntity entity, int level) {
        // H8 fix: remove armor toughness modifier when Armor ability is purged/removed.
        // Without this, the ARMOR_TOUGHNESS modifier leaks permanently.
        if (entity.level().isClientSide) return;
        try {
            var toughnessAttr = entity.getAttribute(Attributes.ARMOR_TOUGHNESS);
            if (toughnessAttr != null) {
                toughnessAttr.removeModifier(getToughnessModifierId());
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * Applies Resistance and armor toughness effects.
     */
    private void applyArmorEffects(LivingEntity entity, int level) {
        int resistanceLevel = Math.max(0, level - 1);
        entity.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 60, resistanceLevel, false, false));

        // At level III+: grant additional armor toughness
        if (level >= 3) {
            float toughnessBonus = (level - 2) * 2.0f;
            try {
                var toughnessAttr = entity.getAttribute(Attributes.ARMOR_TOUGHNESS);
                if (toughnessAttr != null) {
                    // Remove old modifier if present
                    toughnessAttr.removeModifier(getToughnessModifierId());
                    // Add new modifier
                    toughnessAttr.addTransientModifier(new AttributeModifier(
                            getToughnessModifierId(),
                            "EliteForge Armor Toughness",
                            toughnessBonus,
                            AttributeModifier.Operation.ADDITION
                    ));
                }
            } catch (Exception e) {
                // Attribute may not be available for all entities
            }
        }
    }

    /**
     * Returns a unique UUID for the toughness attribute modifier.
     * <p>
     * Note: This UUID must be unique across ALL EliteForge attribute modifiers
     * to prevent modifier conflicts. See NBTKeys and other ability classes for
     * the full UUID registry.
     */
    private java.util.UUID getToughnessModifierId() {
        return java.util.UUID.fromString("a3b4c5d6-e7f8-9012-abcd-ef2345678901");
    }
}
