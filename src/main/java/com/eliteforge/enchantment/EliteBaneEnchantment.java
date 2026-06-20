package com.eliteforge.enchantment;

import com.eliteforge.capability.EliteCapability;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;

/**
 * Elite Bane (精英克星) - Extra damage to elite mobs, levels I-V.
 * Each level adds +20% damage against elite-tagged entities.
 * Compatible with all melee weapons.
 * <p>
 * The damage bonus is applied in EliteEventHandler.onLivingHurt via
 * getEliteDamageMultiplier(), NOT through the vanilla getDamageBonus() hook
 * (which is mob-type-based, not entity-flag-based).
 */
public class EliteBaneEnchantment extends Enchantment {

    private static final float DAMAGE_BONUS_PER_LEVEL = 0.20f;

    public EliteBaneEnchantment() {
        super(Rarity.UNCOMMON, EnchantmentCategory.WEAPON, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
    }

    @Override
    public int getMinLevel() {
        return 1;
    }

    @Override
    public int getMaxLevel() {
        return 5;
    }

    @Override
    public int getMinCost(int level) {
        return 5 + (level - 1) * 8;
    }

    @Override
    public int getMaxCost(int level) {
        return this.getMinCost(level) + 20;
    }

    @Override
    public float getDamageBonus(int level, net.minecraft.world.entity.MobType mobType) {
        return 0.0f; // We handle damage bonus in the attack event
    }

    /**
     * Calculate the damage multiplier against elite entities.
     * Uses the EliteCapability to detect elite status (NOT persistent NBT,
     * which is not kept in sync with the capability-based system).
     *
     * @param level  The enchantment level
     * @param target The target entity
     * @return The damage multiplier (1.0 = normal, 1.2 = +20% per level, higher = more damage)
     */
    public static float getEliteDamageMultiplier(int level, LivingEntity target) {
        if (target == null) return 1.0f;
        boolean isElite = target.getCapability(EliteCapability.CAPABILITY)
                .map(EliteCapability::isElite)
                .orElse(false);
        if (isElite) {
            return 1.0f + (level * DAMAGE_BONUS_PER_LEVEL);
        }
        return 1.0f;
    }

    @Override
    public boolean checkCompatibility(Enchantment other) {
        return super.checkCompatibility(other);
    }

    @Override
    public boolean canEnchant(ItemStack stack) {
        return super.canEnchant(stack);
    }
}

