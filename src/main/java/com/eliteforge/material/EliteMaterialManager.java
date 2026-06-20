package com.eliteforge.material;

import com.eliteforge.capability.EliteData;
import com.eliteforge.quality.QualityTier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.util.RandomSource;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * EliteMaterialManager — diverse material drop system for v0.2.0.
 * <p>
 * Materials are NOT bound to abilities. Instead, they drop based on:
 * - Elite quality tier (better quality = better materials)
 * - Elite level (higher level = more materials)
 * - Random selection from a pool of material types
 * <p>
 * Material types:
 * 1. Elite Essence (common) — used for basic accessory upgrades
 * 2. Elite Core (uncommon) — used for accessory tier upgrades
 * 3. Elite Crystal (rare) — used for legendary accessory crafting
 * 4. Elite Shard (uncommon) — used for accessory rerolling
 * 5. Elite Dust (common) — used for accessory enchanting
 * 6. Elite Fragment (epic+) — used for epic accessory upgrades
 * 7. Elite Heart (legendary+) — used for legendary accessory crafting
 * 8. Elite Soul (mythic) — used for creator accessory crafting
 * 9. Elite Bone (common) — decorative/crafting material
 * 10. Elite Scale (uncommon) — armor upgrade material
 * 11. Elite Fang (rare) — weapon upgrade material
 * 12. Elite Eye (epic+) — special accessory material
 */
public class EliteMaterialManager {

    private static final Logger LOGGER = LogManager.getLogger();

    /**
     * Generate material drops for an elite.
     *
     * @param entity the killed elite entity
     * @param data   the elite's data
     * @return list of ItemStack drops
     */
    public static List<ItemStack> generateMaterialDrops(LivingEntity entity, EliteData data) {
        List<ItemStack> drops = new ArrayList<>();
        if (data == null || !data.isElite()) return drops;

        QualityTier quality = data.getQualityTier();
        int level = data.getLevel();
        RandomSource random = entity.getRandom();
        float multiplier = com.eliteforge.config.EliteForgeConfig.SERVER.lootMultiplier.get().floatValue();

        // Base material count scales with level
        int baseCount = 1 + level / 100;
        baseCount = Math.max(1, (int)(baseCount * multiplier));

        // Quality determines which materials can drop
        switch (quality) {
            case NORMAL -> {
                drops.add(createDrop("elite_essence", baseCount, random));
                if (random.nextFloat() < 0.3f) drops.add(createDrop("elite_dust", 1 + random.nextInt(2), random));
                if (random.nextFloat() < 0.1f) drops.add(createDrop("elite_bone", 1, random));
            }
            case GOOD -> {
                drops.add(createDrop("elite_essence", baseCount + 1, random));
                drops.add(createDrop("elite_dust", 1 + random.nextInt(3), random));
                if (random.nextFloat() < 0.4f) drops.add(createDrop("elite_shard", 1, random));
                if (random.nextFloat() < 0.2f) drops.add(createDrop("elite_bone", 1 + random.nextInt(2), random));
                if (random.nextFloat() < 0.1f) drops.add(createDrop("elite_scale", 1, random));
            }
            case FINE -> {
                drops.add(createDrop("elite_essence", baseCount + 2, random));
                drops.add(createDrop("elite_dust", 2 + random.nextInt(4), random));
                drops.add(createDrop("elite_shard", 1 + random.nextInt(2), random));
                if (random.nextFloat() < 0.3f) drops.add(createDrop("elite_core", 1, random));
                if (random.nextFloat() < 0.2f) drops.add(createDrop("elite_scale", 1 + random.nextInt(2), random));
                if (random.nextFloat() < 0.15f) drops.add(createDrop("elite_fang", 1, random));
            }
            case EPIC -> {
                drops.add(createDrop("elite_essence", baseCount + 3, random));
                drops.add(createDrop("elite_shard", 2 + random.nextInt(3), random));
                drops.add(createDrop("elite_core", 1 + random.nextInt(2), random));
                if (random.nextFloat() < 0.4f) drops.add(createDrop("elite_fragment", 1, random));
                if (random.nextFloat() < 0.3f) drops.add(createDrop("elite_fang", 1 + random.nextInt(2), random));
                if (random.nextFloat() < 0.2f) drops.add(createDrop("elite_eye", 1, random));
                if (random.nextFloat() < 0.5f) drops.add(createDrop("elite_scale", 2 + random.nextInt(3), random));
                // v0.6.5: Catalyst of Fusion rare drop
                if (random.nextFloat() < getCatalystDropChance()) {
                    drops.add(new ItemStack(com.eliteforge.init.ModItems.FUSION_CATALYST.get()));
                }
            }
            case LEGENDARY -> {
                drops.add(createDrop("elite_core", 2 + random.nextInt(3), random));
                drops.add(createDrop("elite_fragment", 1 + random.nextInt(2), random));
                drops.add(createDrop("elite_crystal", 1, random));
                if (random.nextFloat() < 0.4f) drops.add(createDrop("elite_heart", 1, random));
                if (random.nextFloat() < 0.3f) drops.add(createDrop("elite_eye", 1 + random.nextInt(2), random));
                // v0.6.5: Catalyst of Fusion — higher chance for LEGENDARY
                if (random.nextFloat() < getCatalystDropChance() * 2.0f) {
                    drops.add(new ItemStack(com.eliteforge.init.ModItems.FUSION_CATALYST.get()));
                }
                if (random.nextFloat() < 0.2f) drops.add(createDrop("elite_soul", 1, random));
                // Consumables
                drops.add(new ItemStack(Items.GOLDEN_APPLE, 2 + random.nextInt(3)));
                drops.add(new ItemStack(Items.DIAMOND, 1 + random.nextInt(3)));
            }
            case MYTHIC -> {
                drops.add(createDrop("elite_core", 3 + random.nextInt(5), random));
                drops.add(createDrop("elite_crystal", 2 + random.nextInt(2), random));
                drops.add(createDrop("elite_heart", 2 + random.nextInt(2), random));
                drops.add(createDrop("elite_soul", 1 + random.nextInt(2), random));
                drops.add(createDrop("elite_fragment", 3 + random.nextInt(3), random));
                // Consumables
                drops.add(new ItemStack(Items.ENCHANTED_GOLDEN_APPLE, 1 + random.nextInt(2)));
                drops.add(new ItemStack(Items.NETHERITE_INGOT, 1));
                drops.add(new ItemStack(com.eliteforge.init.ModItems.ETERNAL_EMBER.get(), 1));
                drops.add(new ItemStack(Items.DIAMOND, 3 + random.nextInt(5)));
                if (random.nextFloat() < 0.3f) drops.add(new ItemStack(Items.TOTEM_OF_UNDYING));
            }
        }

        LOGGER.debug("Generated {} material drops for {} (quality={}, level={})",
                drops.size(), entity.getName().getString(), quality, level);
        return drops;
    }

    /** Read the configured Catalyst of Fusion drop chance. */
    private static float getCatalystDropChance() {
        try {
            return com.eliteforge.config.EliteForgeConfig.SERVER.fusionCatalystDropChance.get().floatValue();
        } catch (Exception e) {
            return 0.02f;
        }
    }

    /**
     * Create a material drop ItemStack.
     * Currently uses placeholder items — will be replaced with actual mod items.
     */
    private static ItemStack createDrop(String materialId, int count, RandomSource random) {
        // TODO: Replace with actual mod items once registered
        // For now, use vanilla items as placeholders with NBT tags
        ItemStack stack;
        switch (materialId) {
            case "elite_essence" -> stack = new ItemStack(com.eliteforge.init.ModItems.ELITE_ESSENCE.get());
            case "elite_dust" -> stack = new ItemStack(com.eliteforge.init.ModItems.ARCANE_DUST.get());
            case "elite_shard" -> stack = new ItemStack(com.eliteforge.init.ModItems.ELITE_SHARD.get());
            case "elite_core" -> stack = new ItemStack(com.eliteforge.init.ModItems.ELITE_CORE.get());
            case "elite_crystal" -> stack = new ItemStack(com.eliteforge.init.ModItems.ELITE_CRYSTAL.get());
            case "elite_fragment" -> stack = new ItemStack(com.eliteforge.init.ModItems.ELITE_FRAGMENT.get());
            case "elite_heart" -> stack = new ItemStack(com.eliteforge.init.ModItems.ANCIENT_RELIC.get());
            case "elite_soul" -> stack = new ItemStack(com.eliteforge.init.ModItems.SOUL_RESIDUE.get());
            case "elite_bone" -> stack = new ItemStack(com.eliteforge.init.ModItems.PRIMAL_BONE.get());
            case "elite_scale" -> stack = new ItemStack(com.eliteforge.init.ModItems.DRAGON_SCALE.get());
            case "elite_fang" -> stack = new ItemStack(com.eliteforge.init.ModItems.SHADOW_FANG.get());
            case "elite_eye" -> stack = new ItemStack(com.eliteforge.init.ModItems.VOID_EYE.get());
            default -> stack = new ItemStack(com.eliteforge.init.ModItems.ELITE_ESSENCE.get());
        }
        stack.setCount(Math.min(count, stack.getMaxStackSize()));
        // Tag with material ID for identification
        // NBT tag only needed for legacy placeholder items (real items are identified by type)
        return stack;
    }
}
