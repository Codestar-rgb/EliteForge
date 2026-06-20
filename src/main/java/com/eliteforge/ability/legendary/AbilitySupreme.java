package com.eliteforge.ability.legendary;

import com.eliteforge.ability.Ability;
import com.eliteforge.ability.AbilityCategory;
import com.eliteforge.ability.AbilityRegistry;
import com.eliteforge.capability.EliteCapability;
import com.eliteforge.capability.EliteData;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.List;
import java.util.Map;

/**
 * AbilitySupreme (至高) - Boosts all other abilities and the entity's base stats.
 * 
 * Passive: +level to all other ability levels (capped at V).
 * Each other ability's effective level = min(originalLevel + level, 5).
 * Also: +10% max health per level, +5% damage per level.
 * Golden aura particles.
 */
public class AbilitySupreme extends Ability {

    private static final java.util.UUID HEALTH_MODIFIER_UUID = java.util.UUID.fromString("b1c2d3e4-f5a6-7890-bcde-f12345678901");
    private static final java.util.UUID DAMAGE_MODIFIER_UUID = java.util.UUID.fromString("c2d3e4f5-a6b7-8901-cdef-123456789012");

    public AbilitySupreme() {
        super(
            new ResourceLocation("eliteforge", "supreme"),
            AbilityCategory.LEGENDARY,
            5.0f
        );
    }

    @Override
    public void onApply(LivingEntity entity, int level) {
        if (entity.level().isClientSide) return;

        // Apply max health bonus: +10% per level
        applyMaxHealthBonus(entity, level);

        // Apply damage bonus: +5% per level
        applyDamageBonus(entity, level);
    }

    @Override
    public void onTick(LivingEntity entity, int level) {
        if (entity.level().isClientSide) return;
        if (entity.tickCount % 20 != 0) return;

        // Maintain attribute modifiers
        applyMaxHealthBonus(entity, level);
        applyDamageBonus(entity, level);

        // Golden aura particles
        if (entity.level() instanceof ServerLevel serverLevel) {
            // Outer golden ring
            for (int i = 0; i < 4 + level; i++) {
                double angle = (entity.tickCount * 0.05) + (Math.PI * 2 * i) / (4 + level);
                double radius = 1.0 + level * 0.2;
                double px = entity.getX() + Math.cos(angle) * radius;
                double pz = entity.getZ() + Math.sin(angle) * radius;
                serverLevel.sendParticles(ParticleTypes.END_ROD,
                        px, entity.getY() + 0.2, pz, 1,
                        0, 0.05, 0, 0);
            }

            // Center golden sparkle
            if (entity.tickCount % 40 == 0) {
                serverLevel.sendParticles(ParticleTypes.ENCHANT,
                        entity.getX(), entity.getY() + entity.getBbHeight() * 0.5, entity.getZ(),
                        3 + level,
                        entity.getBbWidth() * 0.4, entity.getBbHeight() * 0.4, entity.getBbWidth() * 0.4,
                        0.3);
            }
        }
    }

    @Override
    public void onHurt(LivingEntity entity, float damage, int level) {
        // Supreme is passive - no special onHurt behavior
    }

    @Override
    public void onAttack(LivingEntity attacker, LivingEntity target, float damage, int level) {
        // Supreme boosts other abilities passively - no special onAttack behavior
        // The effective level boost is handled by getEffectiveLevel()
    }

    @Override
    public void onRemove(LivingEntity entity, int level) {
        // H7 fix: remove attribute modifiers when Supreme is purged/removed.
        // Without this, the health and damage modifiers leak permanently
        // (e.g., when Purifying Touch enchant removes Supreme).
        if (entity.level().isClientSide) return;
        try {
            var healthAttr = entity.getAttribute(Attributes.MAX_HEALTH);
            if (healthAttr != null) {
                healthAttr.removeModifier(HEALTH_MODIFIER_UUID);
            }
        } catch (Exception ignored) {
        }
        try {
            var damageAttr = entity.getAttribute(Attributes.ATTACK_DAMAGE);
            if (damageAttr != null) {
                damageAttr.removeModifier(DAMAGE_MODIFIER_UUID);
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * Gets the effective level of another ability when this Supreme ability is active.
     * Each other ability's effective level = min(originalLevel + supremeLevel, 5).
     *
     * @param otherAbility  The other ability
     * @param otherLevel    The other ability's base level
     * @param supremeLevel  The Supreme ability's level
     * @return The effective level of the other ability
     */
    public static int getEffectiveLevel(Ability otherAbility, int otherLevel, int supremeLevel) {
        if (otherAbility instanceof AbilitySupreme) return otherLevel; // Don't boost self
        return Math.min(otherLevel + supremeLevel, 5);
    }

    /**
     * Gets the effective level of an ability for an entity, accounting for Supreme.
     *
     * @param entity   The entity
     * @param ability  The ability
     * @param baseLevel The base level of the ability
     * @return The effective level, potentially boosted by Supreme
     */
    public static int getEffectiveLevelForEntity(LivingEntity entity, Ability ability, int baseLevel) {
        // Use the capability-based system instead of the deprecated AbilityManager NBT approach.
        // AbilityManager.getEntityAbilities reads from a legacy NBT key that is never written
        // by the modern spawn/event system, so it always returned an empty list — meaning
        // Supreme's level-boost passive was effectively dead code.
        EliteData data = entity.getCapability(EliteCapability.CAPABILITY)
                .map(EliteCapability::getEliteData)
                .orElse(null);
        return getEffectiveLevelForData(data, ability, baseLevel);
    }

    /**
     * Variant that takes an already-resolved {@link EliteData} reference, avoiding a
     * redundant capability lookup when the caller (e.g. a dispatch helper in
     * EliteEventHandler) already holds the data. Use this in hot paths.
     */
    public static int getEffectiveLevelForData(EliteData data, Ability ability, int baseLevel) {
        if (data == null) return baseLevel;
        Integer supremeLevel = data.getAbilities().get("eliteforge:supreme");
        if (supremeLevel != null && supremeLevel > 0) {
            return getEffectiveLevel(ability, baseLevel, supremeLevel);
        }
        return baseLevel;
    }

    /**
     * Applies the max health bonus attribute modifier.
     */
    private void applyMaxHealthBonus(LivingEntity entity, int level) {
        try {
            var healthAttr = entity.getAttribute(Attributes.MAX_HEALTH);
            if (healthAttr != null) {
                healthAttr.removeModifier(HEALTH_MODIFIER_UUID);
                double healthBonus = entity.getMaxHealth() * 0.10 * level;
                healthAttr.addTransientModifier(new AttributeModifier(
                        HEALTH_MODIFIER_UUID,
                        "EliteForge Supreme Health",
                        healthBonus,
                        AttributeModifier.Operation.ADDITION
                ));
            }
        } catch (Exception e) {
            // Attribute may not be available
        }
    }

    /**
     * Applies the damage bonus attribute modifier.
     */
    private void applyDamageBonus(LivingEntity entity, int level) {
        try {
            var damageAttr = entity.getAttribute(Attributes.ATTACK_DAMAGE);
            if (damageAttr != null) {
                damageAttr.removeModifier(DAMAGE_MODIFIER_UUID);
                double damageBonus = 0.05 * level; // 5% per level as multiplier
                damageAttr.addTransientModifier(new AttributeModifier(
                        DAMAGE_MODIFIER_UUID,
                        "EliteForge Supreme Damage",
                        damageBonus,
                        AttributeModifier.Operation.MULTIPLY_BASE
                ));
            }
        } catch (Exception e) {
            // Attribute may not be available for all entities
        }
    }
}
