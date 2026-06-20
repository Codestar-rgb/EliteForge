package com.eliteforge.init;

import com.eliteforge.EliteForge;
import com.eliteforge.blockentity.*;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Block entity type registration class for EliteForge.
 * Registers all 4 block entity types with their associated blocks.
 */
public class ModBlockEntities {

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, EliteForge.MODID);

    // ========================================================================
    // Block Entity Registrations (4 custom block entities)
    // ========================================================================

    /**
     * Forging Anvil Block Entity - Handles the forging process:
     * combining materials with abilities to create tempered equipment.
     */
    /**
     * Heat Collector Block Entity - Accumulates heat over time
     * and from nearby forge operations. Provides heat for the tempering process.
     */
    /**
     * Tempering Station Block Entity - Processes materials using heat
     * from nearby Heat Collectors. Transforms raw materials into tempered materials.
     */
    /**
     * Elite Beacon Block Entity - Provides buffs to nearby players
     * based on the quality of tempered materials placed in it.
     */
    public static final RegistryObject<BlockEntityType<EliteBeaconBlockEntity>> ELITE_BEACON_BE =
            BLOCK_ENTITY_TYPES.register("elite_beacon_be",
                    () -> BlockEntityType.Builder.of(EliteBeaconBlockEntity::new,
                                    ModBlocks.ELITE_BEACON.get())
                            .build(null));

    /**
     * Elite Spawner Block Entity - Custom spawner that spawns elite mobs
     * with configurable abilities and quality tiers.
     */
    public static final RegistryObject<BlockEntityType<EliteSpawnerBlockEntity>> ELITE_SPAWNER_BE =
            BLOCK_ENTITY_TYPES.register("elite_spawner_be",
                    () -> BlockEntityType.Builder.of(EliteSpawnerBlockEntity::new,
                                    ModBlocks.ELITE_SPAWNER.get())
                            .build(null));
}
