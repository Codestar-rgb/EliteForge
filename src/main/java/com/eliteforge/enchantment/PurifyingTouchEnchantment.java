package com.eliteforge.enchantment;

import com.eliteforge.EliteForge;
import com.eliteforge.ability.Ability;
import com.eliteforge.ability.AbilityRegistry;
import com.eliteforge.capability.EliteCapability;
import com.eliteforge.capability.EliteCapabilitySync;
import com.eliteforge.capability.EliteData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Purifying Touch (净化之触) - Chance to remove one ability from elite on hit, levels I-II.
 * Level I: 10% chance per hit to remove a random ability.
 * Level II: 20% chance per hit to remove a random ability.
 * Can be applied to weapons.
 * <p>
 * The enchantment has two effects:
 * 1. Counter effect: reduces damage accumulation from specific creator abilities (Evolution, Reincarnation)
 *    and reveals the real elite among clones — handled by AbilityInteraction.
 * 2. Direct effect: on hit, chance to remove a random non-creator ability from the elite — handled by tryPurify(),
 *    which is called from EliteEventHandler.onLivingHurt when the attacker has this enchantment.
 */
public class PurifyingTouchEnchantment extends Enchantment {

    private static final float PURIFY_CHANCE_PER_LEVEL = 0.10f;

    public PurifyingTouchEnchantment() {
        super(Rarity.VERY_RARE, EnchantmentCategory.WEAPON, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
    }

    @Override
    public int getMinLevel() {
        return 1;
    }

    @Override
    public int getMaxLevel() {
        return 2;
    }

    @Override
    public int getMinCost(int level) {
        return 20 + (level - 1) * 15;
    }

    @Override
    public int getMaxCost(int level) {
        return this.getMinCost(level) + 30;
    }

    /**
     * Calculate the purification chance for a given level.
     *
     * @param level The enchantment level
     * @return The chance to purify on hit (0.0 to 0.20)
     */
    public static float getPurifyChance(int level) {
        return level * PURIFY_CHANCE_PER_LEVEL;
    }

    /**
     * Attempt to purify an elite mob on hit.
     * If successful, removes one random non-creator ability from the elite.
     * <p>
     * Uses the EliteCapability to read/modify ability data (NOT persistent NBT,
     * which is not kept in sync with the capability-based system).
     * Creator-tier abilities cannot be purified (they are too powerful to remove).
     *
     * @param target           The elite entity being hit
     * @param enchantmentLevel The enchantment level
     * @return True if an ability was successfully removed
     */
    public static boolean tryPurify(LivingEntity target, int enchantmentLevel) {
        if (target == null || target.level().isClientSide) return false;

        EliteCapability cap = target.getCapability(EliteCapability.CAPABILITY).orElse(null);
        if (cap == null || !cap.isElite()) return false;

        // Creator-tier elites are immune to purification
        EliteData data = cap.getEliteData();
        if (data.isCreatorEntity()) return false;

        float chance = getPurifyChance(enchantmentLevel);
        if (ThreadLocalRandom.current().nextFloat() >= chance) return false;

        return removeRandomAbility(target, cap, data);
    }

    /**
     * Remove a random non-creator ability from an elite mob.
     * Calls the ability's onRemove() for proper cleanup of attribute modifiers and persistent effects.
     *
     * @param elite The elite entity
     * @param cap   The elite capability
     * @param data  The elite data
     * @return True if an ability was removed
     */
    private static boolean removeRandomAbility(LivingEntity elite, EliteCapability cap, EliteData data) {
        Map<String, Integer> abilities = data.getAbilities();
        if (abilities.isEmpty()) return false;

        // Build a list of purifiable abilities (exclude creator abilities — they can't be removed)
        List<String> purifiable = new ArrayList<>();
        for (String abilityId : abilities.keySet()) {
            Ability ability = AbilityRegistry.getAbility(abilityId);
            if (ability != null && ability.getCategory() != com.eliteforge.ability.AbilityCategory.CREATOR) {
                purifiable.add(abilityId);
            }
        }
        if (purifiable.isEmpty()) return false;

        // Pick a random ability and remove it
        String removedId = purifiable.get(ThreadLocalRandom.current().nextInt(purifiable.size()));
        int removedLevel = abilities.get(removedId);

        Ability ability = AbilityRegistry.getAbility(removedId);
        if (ability != null) {
            try {
                ability.onRemove(elite, removedLevel);
            } catch (Exception e) {
                EliteForge.LOGGER.error("Error in onRemove for purified ability {}: {}", removedId, e.getMessage());
            }
        }

        data.removeAbility(removedId);
        cap.setEliteData(data);
        EliteCapabilitySync.broadcastEliteDataUpdate(elite, data);

        // Visual feedback: end rod particles
        if (elite.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,
                    elite.getX(), elite.getY() + elite.getBbHeight() * 0.5, elite.getZ(),
                    12, 0.3, 0.5, 0.3, 0.05);
        }

        EliteForge.LOGGER.debug("Purified ability {} (level {}) from elite {}",
                removedId, removedLevel, elite.getName().getString());
        return true;
    }

    @Override
    public boolean checkCompatibility(Enchantment other) {
        return super.checkCompatibility(other);
    }
}
