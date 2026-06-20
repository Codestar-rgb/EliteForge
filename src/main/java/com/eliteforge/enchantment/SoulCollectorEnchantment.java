package com.eliteforge.enchantment;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;

/**
 * Soul Collector (灵魂收集) - Faster player experience growth, levels I-III.
 * Each level increases player experience gain from elite kills by 20%.
 * Can be applied to weapons.
 */
public class SoulCollectorEnchantment extends Enchantment {

    private static final float EXPERIENCE_BONUS_PER_LEVEL = 0.20f;

    public SoulCollectorEnchantment() {
        super(Rarity.RARE, EnchantmentCategory.WEAPON, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
    }

    @Override
    public int getMinLevel() {
        return 1;
    }

    @Override
    public int getMaxLevel() {
        return 3;
    }

    @Override
    public int getMinCost(int level) {
        return 10 + (level - 1) * 10;
    }

    @Override
    public int getMaxCost(int level) {
        return this.getMinCost(level) + 20;
    }

    /**
     * Calculate the experience bonus multiplier for elite kills.
     *
     * @param level The enchantment level
     * @return The experience bonus multiplier (1.0 = normal, up to 1.6 = 60% bonus at level 3)
     */
    public static float getExperienceMultiplier(int level) {
        return 1.0f + (level * EXPERIENCE_BONUS_PER_LEVEL);
    }

    /**
     * Apply the experience bonus to a player after an elite kill.
     *
     * @param player           The player who killed the elite
     * @param baseExperience   The base experience to award
     * @param enchantmentLevel The enchantment level
     * @return The modified experience amount
     */
    public static int applyExperienceBonus(Player player, int baseExperience, int enchantmentLevel) {
        float multiplier = getExperienceMultiplier(enchantmentLevel);
        return Math.round(baseExperience * multiplier);
    }

    @Override
    public boolean checkCompatibility(Enchantment other) {
        return super.checkCompatibility(other);
    }
}
