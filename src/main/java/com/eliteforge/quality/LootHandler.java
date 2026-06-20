package com.eliteforge.quality;

import com.eliteforge.capability.EliteData;
import com.eliteforge.material.EliteMaterialManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * LootHandler — handles elite mob loot drops for v0.2.0.
 * Uses EliteMaterialManager for material drops.
 * Also drops vanilla consumables scaled by quality tier.
 */
public class LootHandler {

    private static final Logger LOGGER = LogManager.getLogger();

    /**
     * Generate all loot drops for a killed elite.
     */
    public static List<ItemStack> generateLoot(LivingEntity entity, EliteData data, ServerPlayer killer) {
        List<ItemStack> loot = new ArrayList<>();
        if (data == null || !data.isElite()) return loot;
        if (data.hasDroppedLoot()) return loot;

        // 1. Material drops (the main loot)
        loot.addAll(EliteMaterialManager.generateMaterialDrops(entity, data));

        // 2. Modded item drops (chance to drop random modded items)
        float moddedChance = com.eliteforge.config.EliteForgeConfig.SERVER.moddedLootChance.get().floatValue();
        float baseChance = moddedChance + data.getQualityTier().ordinal() * 0.03f;
        if (entity.getRandom().nextFloat() < baseChance) {
            try {
                var registry = net.minecraft.core.registries.BuiltInRegistries.ITEM;
                int size = registry.size();
                if (size > 0) {
                    int idx = entity.getRandom().nextInt(size);
                    net.minecraft.world.item.Item item = registry.stream().skip(idx).findFirst().orElse(null);
                    if (item != null) {
                        var key = registry.getKey(item);
                        if (key != null && !key.getNamespace().equals("minecraft")) {
                            ItemStack stack = new ItemStack(item);
                            if (!stack.isEmpty()) {
                                stack.setCount(1 + entity.getRandom().nextInt(Math.min(4, stack.getMaxStackSize())));
                                loot.add(stack);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                LOGGER.error("Error generating modded loot: {}", e.getMessage());
            }
        }

        // Mark as having dropped loot
        data.setHasDroppedLoot(true);

        LOGGER.debug("Generated {} total loot items for elite {} (quality={}, level={})",
                loot.size(), entity.getName().getString(), data.getQualityTier(), data.getLevel());

        return loot;
    }

    /**
     * Drop loot at the entity's position.
     */
    public static void dropLoot(ServerLevel level, LivingEntity entity, List<ItemStack> loot) {
        for (ItemStack stack : loot) {
            if (!stack.isEmpty()) {
                net.minecraft.world.entity.item.ItemEntity itemEntity = new net.minecraft.world.entity.item.ItemEntity(
                        level, entity.getX(), entity.getY() + 0.5, entity.getZ(), stack);
                itemEntity.setDeltaMovement(
                        (level.random.nextDouble() - 0.5) * 0.3,
                        0.2,
                        (level.random.nextDouble() - 0.5) * 0.3
                );
                level.addFreshEntity(itemEntity);
            }
        }
    }
}
