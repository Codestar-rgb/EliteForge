package com.eliteforge.accessory;

import com.eliteforge.quality.QualityTier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * AccessoryUpgrader — handles accessory upgrade logic.
 * <p>
 * Upgrading requires materials (identified by NBT tag "eliteforge:material")
 * and increases the accessory's upgrade level (0-5).
 * <p>
 * Upgrade costs:
 * - Level 0→1: 4 essence + 2 dust
 * - Level 1→2: 8 essence + 4 dust + 1 shard
 * - Level 2→3: 4 shard + 2 core
 * - Level 3→4: 8 core + 2 crystal + 1 fragment
 * - Level 4→5: 4 crystal + 2 fragment + 1 heart
 */
public class AccessoryUpgrader {

    private static final Logger LOGGER = LogManager.getLogger();

    /**
     * Attempt to upgrade an accessory.
     * @return true if upgrade succeeded
     */
    public static boolean tryUpgrade(ItemStack accessoryStack, Player player) {
        if (!(accessoryStack.getItem() instanceof EliteAccessory accessory)) return false;

        int currentLevel = accessory.getUpgradeLevel(accessoryStack);
        if (currentLevel >= 5) return false;

        // Check materials in player inventory
        int essence = countMaterial(player, "elite_essence");
        int dust = countMaterial(player, "elite_dust");
        int shard = countMaterial(player, "elite_shard");
        int core = countMaterial(player, "elite_core");
        int crystal = countMaterial(player, "elite_crystal");
        int fragment = countMaterial(player, "elite_fragment");
        int heart = countMaterial(player, "elite_heart");

        // Check if player has enough materials for this upgrade level
        boolean canUpgrade = switch (currentLevel) {
            case 0 -> essence >= 4 && dust >= 2;
            case 1 -> essence >= 8 && dust >= 4 && shard >= 1;
            case 2 -> shard >= 4 && core >= 2;
            case 3 -> core >= 8 && crystal >= 2 && fragment >= 1;
            case 4 -> crystal >= 4 && fragment >= 2 && heart >= 1;
            default -> false;
        };

        if (!canUpgrade) return false;

        // Consume materials
        switch (currentLevel) {
            case 0 -> { consumeMaterial(player, "elite_essence", 4); consumeMaterial(player, "elite_dust", 2); }
            case 1 -> { consumeMaterial(player, "elite_essence", 8); consumeMaterial(player, "elite_dust", 4); consumeMaterial(player, "elite_shard", 1); }
            case 2 -> { consumeMaterial(player, "elite_shard", 4); consumeMaterial(player, "elite_core", 2); }
            case 3 -> { consumeMaterial(player, "elite_core", 8); consumeMaterial(player, "elite_crystal", 2); consumeMaterial(player, "elite_fragment", 1); }
            case 4 -> { consumeMaterial(player, "elite_crystal", 4); consumeMaterial(player, "elite_fragment", 2); consumeMaterial(player, "elite_heart", 1); }
        }

        // Apply upgrade
        accessory.setUpgradeLevel(accessoryStack, currentLevel + 1);
        LOGGER.info("Upgraded accessory to level {} for {}", currentLevel + 1, player.getName().getString());
        return true;
    }

    /**
     * Count materials of a specific type in player's inventory.
     */
    private static int countMaterial(Player player, String materialId) {
        int count = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.isEmpty()) continue;
            if (matchesMaterial(stack, materialId)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    /**
     * Consume materials from player's inventory.
     */
    private static void consumeMaterial(Player player, String materialId, int amount) {
        int remaining = amount;
        for (int i = 0; i < player.getInventory().getContainerSize() && remaining > 0; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.isEmpty()) continue;
            if (matchesMaterial(stack, materialId)) {
                int take = Math.min(remaining, stack.getCount());
                stack.shrink(take);
                remaining -= take;
            }
        }
    }

    /**
     * Check whether a stack matches a material ID. v0.6.5: real registered items
     * (Essence, Shard, Core, Crystal, Fragment) match by item type; legacy placeholder
     * items (vanilla items with NBT tags) still match by tag for backward compat.
     */
    private static boolean matchesMaterial(ItemStack stack, String materialId) {
        if (stack.isEmpty()) return false;
        // Real registered items — match by item type directly.
        if ("elite_essence".equals(materialId)
                && stack.getItem() == com.eliteforge.init.ModItems.ELITE_ESSENCE.get()) return true;
        if ("elite_shard".equals(materialId)
                && stack.getItem() == com.eliteforge.init.ModItems.ELITE_SHARD.get()) return true;
        if ("elite_core".equals(materialId)
                && stack.getItem() == com.eliteforge.init.ModItems.ELITE_CORE.get()) return true;
        if ("elite_crystal".equals(materialId)
                && stack.getItem() == com.eliteforge.init.ModItems.ELITE_CRYSTAL.get()) return true;
        if ("elite_fragment".equals(materialId)
                && stack.getItem() == com.eliteforge.init.ModItems.ELITE_FRAGMENT.get()) return true;
        // v0.7.0: new real materials
        if ("elite_heart".equals(materialId)
                && stack.getItem() == com.eliteforge.init.ModItems.ANCIENT_RELIC.get()) return true;
        if ("elite_soul".equals(materialId)
                && stack.getItem() == com.eliteforge.init.ModItems.SOUL_RESIDUE.get()) return true;
        if ("elite_scale".equals(materialId)
                && stack.getItem() == com.eliteforge.init.ModItems.DRAGON_SCALE.get()) return true;
        if ("elite_dust".equals(materialId)
                && stack.getItem() == com.eliteforge.init.ModItems.ARCANE_DUST.get()) return true;
        if ("elite_bone".equals(materialId)
                && stack.getItem() == com.eliteforge.init.ModItems.PRIMAL_BONE.get()) return true;
        if ("elite_eye".equals(materialId)
                && stack.getItem() == com.eliteforge.init.ModItems.VOID_EYE.get()) return true;
        if ("elite_fang".equals(materialId)
                && stack.getItem() == com.eliteforge.init.ModItems.SHADOW_FANG.get()) return true;
        // Legacy placeholder items — match by NBT tag (backward compat for old drops).
        if (stack.hasTag()) {
            var tag = stack.getTag();
            return tag.contains("eliteforge:material")
                    && tag.getString("eliteforge:material").equals(materialId);
        }
        return false;
    }

    // ==================== v0.6.5: Fusion System ====================

    /**
     * Attempt to fuse two accessories of the same type into one of the next-higher
     * quality tier. Requires a Catalyst of Fusion in the player's inventory.
     * <p>
     * Fusion rules:
     * <ul>
     *   <li>Both accessories must be the same AccessoryType (e.g. two Rings).</li>
     *   <li>The result quality = one tier above the HIGHER of the two inputs.</li>
     *   <li>MYTHIC accessories cannot be fused (already max).</li>
     *   <li>Consumes both accessories + 1 Catalyst of Fusion.</li>
     *   <li>The result inherits the higher input's upgrade level.</li>
     * </ul>
     *
     * @param stack1 the first accessory (main hand)
     * @param stack2 the second accessory (off hand)
     * @param player the player performing the fusion
     * @return true if fusion succeeded
     */
    public static boolean tryFusion(ItemStack stack1, ItemStack stack2, Player player) {
        if (!(stack1.getItem() instanceof EliteAccessory acc1)) return false;
        if (!(stack2.getItem() instanceof EliteAccessory acc2)) return false;
        if (acc1.getAccessoryType() != acc2.getAccessoryType()) return false;

        QualityTier q1 = acc1.getQuality(stack1);
        QualityTier q2 = acc2.getQuality(stack2);
        if (q1 == QualityTier.MYTHIC || q2 == QualityTier.MYTHIC) return false;

        // Check for a Catalyst of Fusion in the player's inventory.
        int catalystSlot = findCatalyst(player);
        if (catalystSlot < 0) return false;

        // Determine result quality = one tier above the higher input.
        QualityTier higher = q1.ordinal() >= q2.ordinal() ? q1 : q2;
        QualityTier resultQuality = QualityTier.values()[Math.min(higher.ordinal() + 1, QualityTier.MYTHIC.ordinal())];

        // Inherit the higher upgrade level.
        int upgrade1 = acc1.getUpgradeLevel(stack1);
        int upgrade2 = acc2.getUpgradeLevel(stack2);
        int resultUpgrade = Math.max(upgrade1, upgrade2);

        // Consume inputs + catalyst.
        stack1.shrink(1);
        stack2.shrink(1);
        player.getInventory().getItem(catalystSlot).shrink(1);

        // Create the result: a new accessory of the same type with the upgraded quality.
        ItemStack result = new ItemStack(acc1);
        acc1.setQuality(result, resultQuality);
        acc1.setUpgradeLevel(result, resultUpgrade);

        // Give the result to the player (or drop it if inventory is full).
        if (!player.getInventory().add(result)) {
            player.drop(result, false);
        }

        LOGGER.info("Fused two {} accessories into {} (quality {}, upgrade {})",
                acc1.getAccessoryType().getId(), resultQuality, resultUpgrade);
        return true;
    }

    /** Find the first Catalyst of Fusion in the player's inventory. Returns -1 if none. */
    private static int findCatalyst(Player player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.getItem() == com.eliteforge.init.ModItems.FUSION_CATALYST.get()) {
                return i;
            }
        }
        return -1;
    }

    // ==================== v0.7.5: Dismantle System ====================

    /**
     * Dismantle an accessory into materials. Returns materials based on the
     * accessory's quality tier + upgrade level. Higher quality = more materials.
     * Consumes the accessory + 1 Dismantle Kit.
     */
    public static boolean tryDismantle(ItemStack accessoryStack, Player player) {
        if (!(accessoryStack.getItem() instanceof EliteAccessory accessory)) return false;
        // Find a Dismantle Kit in the player's inventory.
        int kitSlot = -1;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (!s.isEmpty() && s.getItem() == com.eliteforge.init.ModItems.DISMANTLE_KIT.get()) { kitSlot = i; break; }
        }
        if (kitSlot < 0) return false;

        QualityTier quality = accessory.getQuality(accessoryStack);
        int upgrade = accessory.getUpgradeLevel(accessoryStack);

        // Dismantle yields: quality-based materials + upgrade bonus.
        int essenceCount = quality.ordinal() * 2 + upgrade;
        int shardCount = quality.ordinal() + upgrade / 2;
        int coreCount = quality.ordinal() >= 3 ? 1 + upgrade / 3 : 0;

        // Consume accessory + kit.
        accessoryStack.shrink(1);
        player.getInventory().getItem(kitSlot).shrink(1);

        // Give materials.
        if (essenceCount > 0) giveItem(player, com.eliteforge.init.ModItems.ELITE_ESSENCE.get(), essenceCount);
        if (shardCount > 0) giveItem(player, com.eliteforge.init.ModItems.ELITE_SHARD.get(), shardCount);
        if (coreCount > 0) giveItem(player, com.eliteforge.init.ModItems.ELITE_CORE.get(), coreCount);
        // High-quality dismantles also yield rare materials.
        if (quality.ordinal() >= 4) giveItem(player, com.eliteforge.init.ModItems.ELITE_CRYSTAL.get(), 1);
        if (quality == QualityTier.MYTHIC) giveItem(player, com.eliteforge.init.ModItems.ANCIENT_RELIC.get(), 1);

        LOGGER.info("Dismantled {} accessory → {} essence, {} shard, {} core",
                quality, essenceCount, shardCount, coreCount);
        return true;
    }

    /**
     * Enhance a weapon by adding +1 attack damage (stored in NBT). Each enhancement
     * consumes 1 Weapon Enhancer + materials. Max 5 enhancements.
     */
    public static boolean tryEnhanceWeapon(ItemStack weaponStack, Player player) {
        if (weaponStack.isEmpty() || !(weaponStack.getItem() instanceof net.minecraft.world.item.Item)) return false;
        // Find a Weapon Enhancer.
        int enhSlot = -1;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (!s.isEmpty() && s.getItem() == com.eliteforge.init.ModItems.WEAPON_ENHANCER.get()) { enhSlot = i; break; }
        }
        if (enhSlot < 0) return false;

        int currentEnh = weaponStack.getOrCreateTag().getInt("eliteforge:enhancement");
        if (currentEnh >= 5) return false;

        // Check materials: needs shards scaling with enhancement level.
        int shardCost = (currentEnh + 1) * 2;
        if (countMaterial(player, "elite_shard") < shardCost) return false;

        // Consume.
        consumeMaterial(player, "elite_shard", shardCost);
        player.getInventory().getItem(enhSlot).shrink(1);

        // Apply enhancement.
        weaponStack.getOrCreateTag().putInt("eliteforge:enhancement", currentEnh + 1);

        LOGGER.info("Enhanced weapon to +{}", currentEnh + 1);
        return true;
    }

    private static void giveItem(Player player, net.minecraft.world.item.Item item, int count) {
        ItemStack stack = new ItemStack(item, count);
        if (!player.getInventory().add(stack)) player.drop(stack, false);
    }
}
