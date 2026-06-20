package com.eliteforge.init;

import com.eliteforge.EliteForge;
import com.eliteforge.block.*;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Block registration class for EliteForge.
 * Registers all 5 mod blocks.
 *
 * BlockItems for these blocks are registered in ModItems,
 * following the standard Forge convention of keeping all item
 * registrations together.
 */
public class ModBlocks {

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, EliteForge.MODID);

    // ========================================================================
    // Block Registrations (5 custom blocks)
    // ========================================================================

    /**
     * Elite Spawner (精英刷怪笼) - Custom spawner that spawns elite mobs
     * with configurable abilities and quality tiers.
     */
    public static final RegistryObject<Block> ELITE_SPAWNER = BLOCKS.register("elite_spawner",
            EliteSpawnerBlock::new);

    /**
     * Forging Anvil (锻造铁砧) - Core crafting station for the forging system.
     * Accepts a Forging Hammer and materials to perform forging operations.
     */

    /**
     * Heat Collector (热量收集器) - Collects heat from nearby forge operations
     * and chunk heat. Stores heat energy used for the tempering process.
     */

    /**
     * Tempering Station (淬炼台) - Used to temper equipment and materials.
     * Requires heat from a Heat Collector and tempered materials.
     */

    /**
     * Elite Beacon (精英信标) - Enhanced beacon that provides powerful buffs
     * based on the quality of tempered materials placed in it.
     */
    public static final RegistryObject<Block> ELITE_BEACON = BLOCKS.register("elite_beacon",
            EliteBeaconBlock::new);
}
