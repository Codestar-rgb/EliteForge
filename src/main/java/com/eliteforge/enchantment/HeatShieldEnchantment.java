package com.eliteforge.enchantment;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;

/**
 * Heat Shield (热力屏障) - Reduces chunk heat influence on the player, levels I-III.
 * Each level reduces the negative effects of high chunk heat by 15%.
 * Can be applied to chestplate armor.
 */
public class HeatShieldEnchantment extends Enchantment {

    private static final float HEAT_REDUCTION_PER_LEVEL = 0.15f;

    public HeatShieldEnchantment() {
        super(Rarity.RARE, EnchantmentCategory.ARMOR_CHEST, new EquipmentSlot[]{EquipmentSlot.CHEST});
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
        return 10 + (level - 1) * 8;
    }

    @Override
    public int getMaxCost(int level) {
        return this.getMinCost(level) + 15;
    }

    /**
     * Calculate the heat reduction factor for a given enchantment level.
     *
     * @param level The enchantment level
     * @return The reduction factor (0.0 = no reduction, up to 0.45 = 45% reduction at level 3)
     */
    public static float getHeatReduction(int level) {
        return level * HEAT_REDUCTION_PER_LEVEL;
    }

    /**
     * Calculate the effective heat influence on a player after reduction.
     *
     * @param baseHeatInfluence The base heat influence (0.0 to 1.0)
     * @param enchantmentLevel The enchantment level
     * @return The reduced heat influence
     */
    public static float reduceHeatInfluence(float baseHeatInfluence, int enchantmentLevel) {
        float reduction = getHeatReduction(enchantmentLevel);
        return baseHeatInfluence * (1.0f - reduction);
    }

    @Override
    public boolean checkCompatibility(Enchantment other) {
        return super.checkCompatibility(other);
    }
}
