package com.eliteforge.ability.creator;

import com.eliteforge.ability.Ability;
import com.eliteforge.ability.AbilityCategory;
import com.eliteforge.capability.EliteCapability;
import com.eliteforge.capability.EliteData;
import com.eliteforge.capability.EliteCapabilitySync;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Abstract base class for all Creator-tier abilities.
 * <p>
 * Creator abilities:
 * - Are mutually exclusive with ALL other abilities (including other Creator abilities)
 * - Have max level of 3 (not 5 like normal abilities)
 * - Cannot be assigned in CASUAL mode
 * - Represent the highest tier of elite mob power
 * <p>
 * <b>Budget cost note:</b> Although each creator ability defines a {@code budgetCost} value
 * (ranging from 5.0 to 6.5), this cost is <em>purely informational/display-only</em>.
 * Creator abilities are never allocated through the budget system — they are assigned
 * exclusively via the creator-tier conversion process in {@code EliteSpawnHandler.convertToCreator()}.
 * The budget system explicitly skips creator abilities (see {@code AbilityManager.generateAbilities}
 * and {@code AbilityGenerator.generateAbilities}, both filter out
 * {@code AbilityCategory.CREATOR}). The budget cost field exists for tooltip display
 * and documentation purposes only.
 * <p>
 * <b>Budget cost ranking (intentional, reflects relative power level):</b>
 * <ul>
 *   <li>6.5 — Reincarnation (multiple rebirths + legendary ability grants = highest impact)</li>
 *   <li>6.0 — Nexus (sustained passive aura strengthening multiple elites simultaneously)</li>
 *   <li>6.0 — Annihilate (devastating death explosion with chain reactions)</li>
 *   <li>5.5 — Dominion (territory control with zone buffs/debuffs)</li>
 *   <li>5.5 — Assimilate (absorbs abilities from dead elites + stat growth)</li>
 *   <li>5.5 — Commander (coordinates squad of elites into tactical formation)</li>
 *   <li>5.0 — Evolution (requires taking damage to evolve, slower buildup)</li>
 *   <li>5.0 — Bestowal (converts normal mobs at health cost, weaker individual impact)</li>
 * </ul>
 * <p>
 * <b>UUID uniqueness:</b> Each creator ability uses unique UUIDs for its AttributeModifiers.
 * A total of 25 UUIDs are used across the codebase (14 in creator abilities, 3 in
 * EliteSpawnHandler mythic modifiers, 6 in DynamicStrengthening, 1 in EliteAwakening,
 * 1 in EliteRevenge). All have been verified as globally unique with zero collisions.
 * <p>
 * <b>Q4 helper methods:</b> Subclasses should prefer {@link #safeRemoveModifier},
 * {@link #safeAddMultiplierModifier}, and {@link #safeAddFlatModifier} over writing
 * their own try/catch blocks around attribute access. These helpers centralize the
 * null-checking and exception-swallowing pattern that is repeated across all 8
 * creator ability implementations.
 */
public abstract class CreatorAbility extends Ability {

    protected CreatorAbility(ResourceLocation id, float budgetCost) {
        super(id, AbilityCategory.CREATOR, budgetCost, 3); // Max level III
    }

    @Override
    public boolean canCoexistWith(Ability other) {
        // Creator abilities are mutually exclusive with everything
        return false;
    }

    @Override
    public int getWeight() {
        return 1; // Extremely low generation weight
    }

    /**
     * Common setup for creator-tier entities. Called by subclasses in onApply().
     * Sets creator entity fields in EliteData and broadcasts the update.
     * <p>
     * <b>Idempotency:</b> This method is safe to call multiple times. Each invocation
     * overwrites the creator data fields with the same values (creatorEntity=true,
     * creatorAbilityId, creatorAbilityLevel), so repeated calls produce no harmful
     * side effects. This is important because many creator abilities follow a
     * defensive pattern in {@code onTick()} where they call {@code onApply(entity, level)}
     * when their NBT keys are not found — which can happen if NBT data is lost or
     * the entity is loaded before capabilities are attached. The onApply call
     * re-initializes NBT keys and re-calls this method, so it must be idempotent.
     *
     * @param entity the entity receiving the creator ability
     * @param level  the ability level
     */
    protected void setupCreatorData(LivingEntity entity, int level) {
        entity.getCapability(EliteCapability.CAPABILITY).ifPresent(cap -> {
            EliteData eliteData = cap.getEliteData();
            eliteData.setCreatorEntity(true);
            eliteData.setCreatorAbilityId(this.getIdString());
            eliteData.setCreatorAbilityLevel(level);
            cap.setEliteData(eliteData);
            EliteCapabilitySync.broadcastEliteDataUpdate(entity, eliteData);
        });
    }

    // ==================== Q4: Safe Attribute Modifier Helpers ====================

    /**
     * Remove an attribute modifier by UUID, swallowing exceptions if the
     * attribute is unavailable (some entities lack MAX_HEALTH, ATTACK_DAMAGE, etc.).
     *
     * @param entity the entity whose attribute to modify
     * @param attribute the attribute (e.g. {@link Attributes#MAX_HEALTH})
     * @param uuid the modifier UUID to remove
     */
    protected static void safeRemoveModifier(LivingEntity entity,
                                             net.minecraft.world.entity.ai.attributes.Attribute attribute,
                                             java.util.UUID uuid) {
        try {
            AttributeInstance inst = entity.getAttribute(attribute);
            if (inst != null) {
                inst.removeModifier(uuid);
            }
        } catch (Exception ignored) {
            // Attribute may not be registered for this entity type
        }
    }

    /**
     * Add a transient (non-persistent) attribute modifier, after first removing
     * any existing modifier with the same UUID. Uses MULTIPLY_BASE operation.
     *
     * @param entity the entity whose attribute to modify
     * @param attribute the attribute (e.g. {@link Attributes#ATTACK_DAMAGE})
     * @param uuid the modifier UUID (must be globally unique)
     * @param name the human-readable modifier name
     * @param amount the modifier amount (for MULTIPLY_BASE, 0.20 = +20%)
     */
    protected static void safeAddMultiplierModifier(LivingEntity entity,
                                                    net.minecraft.world.entity.ai.attributes.Attribute attribute,
                                                    java.util.UUID uuid, String name, double amount) {
        safeAddTransientModifier(entity, attribute, uuid, name, amount, AttributeModifier.Operation.MULTIPLY_BASE);
    }

    /**
     * Add a transient (non-persistent) attribute modifier, after first removing
     * any existing modifier with the same UUID. Uses ADDITION operation (flat bonus).
     *
     * @param entity the entity whose attribute to modify
     * @param attribute the attribute (e.g. {@link Attributes#MAX_HEALTH})
     * @param uuid the modifier UUID (must be globally unique)
     * @param name the human-readable modifier name
     * @param amount the flat bonus amount (for ADDITION on MAX_HEALTH, 20.0 = +20 HP)
     */
    protected static void safeAddFlatModifier(LivingEntity entity,
                                              net.minecraft.world.entity.ai.attributes.Attribute attribute,
                                              java.util.UUID uuid, String name, double amount) {
        safeAddTransientModifier(entity, attribute, uuid, name, amount, AttributeModifier.Operation.ADDITION);
    }

    /**
     * Internal helper: add a transient modifier with arbitrary operation,
     * removing any existing modifier with the same UUID first.
     */
    private static void safeAddTransientModifier(LivingEntity entity,
                                                 net.minecraft.world.entity.ai.attributes.Attribute attribute,
                                                 java.util.UUID uuid, String name, double amount,
                                                 AttributeModifier.Operation operation) {
        try {
            AttributeInstance inst = entity.getAttribute(attribute);
            if (inst != null) {
                inst.removeModifier(uuid);
                inst.addTransientModifier(new AttributeModifier(uuid, name, amount, operation));
            }
        } catch (Exception ignored) {
            // Attribute may not be registered for this entity type
        }
    }

    /**
     * Get the base value of an attribute, or 0 if the attribute is unavailable.
     * Useful for computing ADDITION modifiers that scale with the base value
     * (e.g. +20% of base health as a flat bonus).
     *
     * @param entity the entity
     * @param attribute the attribute
     * @return the base value, or 0.0 if unavailable
     */
    protected static double safeGetBaseValue(LivingEntity entity,
                                             net.minecraft.world.entity.ai.attributes.Attribute attribute) {
        try {
            AttributeInstance inst = entity.getAttribute(attribute);
            return inst != null ? inst.getBaseValue() : 0.0;
        } catch (Exception e) {
            return 0.0;
        }
    }
}
