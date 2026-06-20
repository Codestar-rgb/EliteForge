package com.eliteforge.init;

import com.eliteforge.EliteForge;
import com.eliteforge.item.*;
// Forging system items removed in v0.2.0
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;


/**
 * Item registration class for EliteForge.
 * Registers all 12 forging items, 8 creator drop items, and block items for all 5 mod blocks.
 *
 * Block items are registered here (not in ModBlocks) to keep all item
 * registrations in one place, as is standard Forge practice.
 */
public class ModItems {

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, EliteForge.MODID);

    // ========================================================================
    // Forging Items (12 custom forging items)
    // ========================================================================

    public static final RegistryObject<Item> ELITE_NAME_TAG = ITEMS.register("elite_name_tag",
            EliteNameTag::new);

    public static final RegistryObject<Item> ABILITY_EXTRACTOR = ITEMS.register("ability_extractor",
            AbilityExtractor::new);

    public static final RegistryObject<Item> ABILITY_INFUSER = ITEMS.register("ability_infuser",
            AbilityInfuser::new);

    public static final RegistryObject<Item> REROLL_SCROLL = ITEMS.register("reroll_scroll",
            RerollScroll::new);

    public static final RegistryObject<Item> PURIFICATION_FLASK = ITEMS.register("purification_flask",
            PurificationFlask::new);

    // ========================================================================
    // Creator Drop Items (8 creator-tier drops)
    // ========================================================================

    public static final RegistryObject<Item> CREATOR_FRAGMENT = ITEMS.register("creator_fragment",
            CreatorFragment::new);

    public static final RegistryObject<Item> REINCARNATION_CRYSTAL = ITEMS.register("reincarnation_crystal",
            ReincarnationCrystal::new);

    public static final RegistryObject<Item> SCORCHED_CORE = ITEMS.register("scorched_core",
            ScorchedCore::new);

    public static final RegistryObject<Item> DOMINION_SCEPTER = ITEMS.register("dominion_scepter",
            DominionScepter::new);

    public static final RegistryObject<Item> EVOLUTION_CORE = ITEMS.register("evolution_core",
            EvolutionCore::new);

    public static final RegistryObject<Item> COMMAND_BANNER = ITEMS.register("command_banner",
            CommandBanner::new);

    public static final RegistryObject<Item> NEXUS_ESSENCE = ITEMS.register("nexus_essence",
            NexusEssence::new);

    public static final RegistryObject<Item> BESTOWAL_SIGIL = ITEMS.register("bestowal_sigil",
            BestowalSigil::new);

    // ========================================================================
    // Guide Book (welcome item)
    // ========================================================================

    public static final RegistryObject<Item> GUIDE_BOOK = ITEMS.register("guide_book",
            EliteForgeGuideBook::new);

    public static final RegistryObject<Item> HEAT_PROBE = ITEMS.register("heat_probe",
            HeatProbe::new);

    // ========================================================================
    // Elite Summon Totem (v0.5.0 — testing tool)
    // ========================================================================

    public static final RegistryObject<Item> SUMMON_TOTEM = ITEMS.register("summon_totem",
            com.eliteforge.item.EliteSummonTotem::new);

    
    // ========================================================================
    // Accessories (Curios API compatible, v0.2.0)
    // ========================================================================

    public static final RegistryObject<Item> ELITE_RING = ITEMS.register("elite_ring",
            () -> new com.eliteforge.accessory.EliteAccessory(
                    com.eliteforge.accessory.EliteAccessory.AccessoryType.RING,
                    new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> ELITE_NECKLACE = ITEMS.register("elite_necklace",
            () -> new com.eliteforge.accessory.EliteAccessory(
                    com.eliteforge.accessory.EliteAccessory.AccessoryType.NECKLACE,
                    new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> ELITE_BELT = ITEMS.register("elite_belt",
            () -> new com.eliteforge.accessory.EliteAccessory(
                    com.eliteforge.accessory.EliteAccessory.AccessoryType.BELT,
                    new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> ELITE_CHARM = ITEMS.register("elite_charm",
            () -> new com.eliteforge.accessory.EliteAccessory(
                    com.eliteforge.accessory.EliteAccessory.AccessoryType.CHARM,
                    new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> ELITE_CROWN = ITEMS.register("elite_crown",
            () -> new com.eliteforge.accessory.EliteAccessory(
                    com.eliteforge.accessory.EliteAccessory.AccessoryType.CROWN,
                    new Item.Properties().stacksTo(1)));

    // ========================================================================
    // v0.6.0 — New Accessories (2 new types + 2 variants)
    // ========================================================================

    public static final RegistryObject<Item> ELITE_BRACER = ITEMS.register("elite_bracer",
            () -> new com.eliteforge.accessory.EliteAccessory(
                    com.eliteforge.accessory.EliteAccessory.AccessoryType.BRACER,
                    new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> ELITE_CAPE = ITEMS.register("elite_cape",
            () -> new com.eliteforge.accessory.EliteAccessory(
                    com.eliteforge.accessory.EliteAccessory.AccessoryType.CAPE,
                    new Item.Properties().stacksTo(1)));

    // Variants of existing types — themed stat spreads (RING→attack, CHARM→defense).
    public static final RegistryObject<Item> ELITE_GAUNTLET = ITEMS.register("elite_gauntlet",
            () -> new com.eliteforge.accessory.EliteAccessory(
                    com.eliteforge.accessory.EliteAccessory.AccessoryType.RING,
                    new Item.Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.RARE)));

    public static final RegistryObject<Item> ELITE_AMULET = ITEMS.register("elite_amulet",
            () -> new com.eliteforge.accessory.EliteAccessory(
                    com.eliteforge.accessory.EliteAccessory.AccessoryType.CHARM,
                    new Item.Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.RARE)));

    // ========================================================================
    // v0.6.5 — New Accessories (3 new types + 2 themed variants)
    // ========================================================================

    public static final RegistryObject<Item> SIGIL_OF_WARDING = ITEMS.register("sigil_of_warding",
            () -> new com.eliteforge.accessory.EliteAccessory(
                    com.eliteforge.accessory.EliteAccessory.AccessoryType.SIGIL,
                    new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> INSIGNIA_OF_COMMAND = ITEMS.register("insignia_of_command",
            () -> new com.eliteforge.accessory.EliteAccessory(
                    com.eliteforge.accessory.EliteAccessory.AccessoryType.INSIGNIA,
                    new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> TALISMAN_OF_GREED = ITEMS.register("talisman_of_greed",
            () -> new com.eliteforge.accessory.EliteAccessory(
                    com.eliteforge.accessory.EliteAccessory.AccessoryType.TALISMAN,
                    new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> VOID_BAND = ITEMS.register("void_band",
            () -> new com.eliteforge.accessory.EliteAccessory(
                    com.eliteforge.accessory.EliteAccessory.AccessoryType.RING,
                    new Item.Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.EPIC)));

    public static final RegistryObject<Item> SOLAR_PENDANT = ITEMS.register("solar_pendant",
            () -> new com.eliteforge.accessory.EliteAccessory(
                    com.eliteforge.accessory.EliteAccessory.AccessoryType.NECKLACE,
                    new Item.Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.EPIC)));

    // ========================================================================
    // v0.7.0 — New Accessories (2 new types + 3 variants)
    // ========================================================================

    public static final RegistryObject<Item> AETHER_ORB = ITEMS.register("aether_orb",
            () -> new com.eliteforge.accessory.EliteAccessory(
                    com.eliteforge.accessory.EliteAccessory.AccessoryType.ORB,
                    new Item.Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.RARE)));

    public static final RegistryObject<Item> WARD_STONE = ITEMS.register("ward_stone",
            () -> new com.eliteforge.accessory.EliteAccessory(
                    com.eliteforge.accessory.EliteAccessory.AccessoryType.WARD_STONE,
                    new Item.Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.RARE)));

    public static final RegistryObject<Item> PHANTOM_RING = ITEMS.register("phantom_ring",
            () -> new com.eliteforge.accessory.EliteAccessory(
                    com.eliteforge.accessory.EliteAccessory.AccessoryType.RING,
                    new Item.Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.EPIC)));

    public static final RegistryObject<Item> EMBER_CHARM = ITEMS.register("ember_charm",
            () -> new com.eliteforge.accessory.EliteAccessory(
                    com.eliteforge.accessory.EliteAccessory.AccessoryType.CHARM,
                    new Item.Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.EPIC)));

    public static final RegistryObject<Item> TIDE_PENDANT = ITEMS.register("tide_pendant",
            () -> new com.eliteforge.accessory.EliteAccessory(
                    com.eliteforge.accessory.EliteAccessory.AccessoryType.NECKLACE,
                    new Item.Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.EPIC)));

    // ========================================================================
    // v0.7.0 — New Material Items (real items, non-elite names)
    // ========================================================================

    public static final RegistryObject<Item> ANCIENT_RELIC = ITEMS.register("ancient_relic",
            () -> new Item(new Item.Properties().rarity(net.minecraft.world.item.Rarity.EPIC)));

    public static final RegistryObject<Item> SOUL_RESIDUE = ITEMS.register("soul_residue",
            () -> new Item(new Item.Properties().rarity(net.minecraft.world.item.Rarity.RARE)));

    public static final RegistryObject<Item> DRAGON_SCALE = ITEMS.register("dragon_scale",
            () -> new Item(new Item.Properties().rarity(net.minecraft.world.item.Rarity.RARE)));

    public static final RegistryObject<Item> SHADOW_FANG = ITEMS.register("shadow_fang",
            () -> new Item(new Item.Properties()));

    // ========================================================================
    // v0.7.0 — Equipment / Tool Items
    // ========================================================================

    public static final RegistryObject<Item> FORGE_HAMMER = ITEMS.register("forge_hammer",
            com.eliteforge.item.ForgeHammer::new);

    public static final RegistryObject<Item> RESONANCE_TUNER = ITEMS.register("resonance_tuner",
            com.eliteforge.item.ResonanceTuner::new);

    public static final RegistryObject<Item> LOOT_COMPASS = ITEMS.register("loot_compass",
            com.eliteforge.item.LootCompass::new);

    // ========================================================================
    // v0.7.5 — 6 New Accessories (2 new types + 4 variants)
    // ========================================================================

    public static final RegistryObject<Item> RUNE_OF_HASTE = ITEMS.register("rune_of_haste",
            () -> new com.eliteforge.accessory.EliteAccessory(
                    com.eliteforge.accessory.EliteAccessory.AccessoryType.RUNE,
                    new Item.Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.RARE)));
    public static final RegistryObject<Item> AEGIS_PLATE = ITEMS.register("aegis_plate",
            () -> new com.eliteforge.accessory.EliteAccessory(
                    com.eliteforge.accessory.EliteAccessory.AccessoryType.AEGIS,
                    new Item.Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.RARE)));
    public static final RegistryObject<Item> SHADOW_CLOAK = ITEMS.register("shadow_cloak",
            () -> new com.eliteforge.accessory.EliteAccessory(
                    com.eliteforge.accessory.EliteAccessory.AccessoryType.CAPE,
                    new Item.Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.EPIC)));
    public static final RegistryObject<Item> CRYSTAL_BAND = ITEMS.register("crystal_band",
            () -> new com.eliteforge.accessory.EliteAccessory(
                    com.eliteforge.accessory.EliteAccessory.AccessoryType.RING,
                    new Item.Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.EPIC)));
    public static final RegistryObject<Item> PHOENIX_FEATHER = ITEMS.register("phoenix_feather",
            () -> new com.eliteforge.accessory.EliteAccessory(
                    com.eliteforge.accessory.EliteAccessory.AccessoryType.CHARM,
                    new Item.Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.EPIC)));
    public static final RegistryObject<Item> ABYSSAL_CROWN = ITEMS.register("abyssal_crown",
            () -> new com.eliteforge.accessory.EliteAccessory(
                    com.eliteforge.accessory.EliteAccessory.AccessoryType.CROWN,
                    new Item.Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.EPIC)));

    // ========================================================================
    // v0.7.5 — 4 New Materials (real items, non-elite names)
    // ========================================================================

    public static final RegistryObject<Item> ARCANE_DUST = ITEMS.register("arcane_dust", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> PRIMAL_BONE = ITEMS.register("primal_bone", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> VOID_EYE = ITEMS.register("void_eye", () -> new Item(new Item.Properties().rarity(net.minecraft.world.item.Rarity.RARE)));
    public static final RegistryObject<Item> ETERNAL_EMBER = ITEMS.register("eternal_ember", () -> new Item(new Item.Properties().rarity(net.minecraft.world.item.Rarity.EPIC).fireResistant()));

    // ========================================================================
    // v0.7.5 — 8 Equipment / Weapon Items
    // ========================================================================

    public static final RegistryObject<Item> ELITE_SWORD = ITEMS.register("elite_sword", () -> new Item(new Item.Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.RARE)));
    public static final RegistryObject<Item> ELITE_BOW = ITEMS.register("elite_bow", () -> new Item(new Item.Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.RARE)));
    public static final RegistryObject<Item> ELITE_SHIELD = ITEMS.register("elite_shield", () -> new Item(new Item.Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.RARE)));
    public static final RegistryObject<Item> ELITE_PICKAXE = ITEMS.register("elite_pickaxe", () -> new Item(new Item.Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.RARE)));
    public static final RegistryObject<Item> BATTLE_WAND = ITEMS.register("battle_wand", com.eliteforge.item.BattleWand::new);
    public static final RegistryObject<Item> SOUL_REAPER = ITEMS.register("soul_reaper", () -> new Item(new Item.Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.EPIC)));
    public static final RegistryObject<Item> STORM_CALLER = ITEMS.register("storm_caller", com.eliteforge.item.StormCaller::new);
    public static final RegistryObject<Item> VOID_BLADE = ITEMS.register("void_blade", () -> new Item(new Item.Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.EPIC)));

    // ========================================================================
    // v0.7.5 — 5 Food / Consumable Items
    // ========================================================================

    public static final RegistryObject<Item> ELITE_APPLE = ITEMS.register("elite_apple", com.eliteforge.item.EliteConsumables.EliteApple::new);
    public static final RegistryObject<Item> ENERGY_BREW = ITEMS.register("energy_brew", com.eliteforge.item.EliteConsumables.EnergyBrew::new);
    public static final RegistryObject<Item> SHIELD_POTION = ITEMS.register("shield_potion", com.eliteforge.item.EliteConsumables.ShieldPotion::new);
    public static final RegistryObject<Item> RAGE_ELIXIR = ITEMS.register("rage_elixir", com.eliteforge.item.EliteConsumables.RageElixir::new);
    public static final RegistryObject<Item> PHOENIX_BREW = ITEMS.register("phoenix_brew", com.eliteforge.item.EliteConsumables.PhoenixBrew::new);

    // ========================================================================
    // v0.7.5 — 2 New System Items (dismantle + weapon enhancement)
    // ========================================================================

    public static final RegistryObject<Item> DISMANTLE_KIT = ITEMS.register("dismantle_kit", com.eliteforge.item.DismantleKit::new);
    public static final RegistryObject<Item> WEAPON_ENHANCER = ITEMS.register("weapon_enhancer", com.eliteforge.item.WeaponEnhancer::new);

    // ========================================================================
    // v0.8.0 — 8 New Accessories (2 new types + 6 variants)
    // ========================================================================

    public static final RegistryObject<Item> MIRROR_OF_REFLECTION = ITEMS.register("mirror_of_reflection",
            () -> new com.eliteforge.accessory.EliteAccessory(com.eliteforge.accessory.EliteAccessory.AccessoryType.MIRROR, new Item.Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.RARE)));
    public static final RegistryObject<Item> CATALYST_STONE = ITEMS.register("catalyst_stone",
            () -> new com.eliteforge.accessory.EliteAccessory(com.eliteforge.accessory.EliteAccessory.AccessoryType.CATALYST, new Item.Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.RARE)));
    public static final RegistryObject<Item> FROST_BITE_RING = ITEMS.register("frost_bite_ring",
            () -> new com.eliteforge.accessory.EliteAccessory(com.eliteforge.accessory.EliteAccessory.AccessoryType.RING, new Item.Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.EPIC)));
    public static final RegistryObject<Item> THUNDER_CHAIN = ITEMS.register("thunder_chain",
            () -> new com.eliteforge.accessory.EliteAccessory(com.eliteforge.accessory.EliteAccessory.AccessoryType.NECKLACE, new Item.Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.EPIC)));
    public static final RegistryObject<Item> IRON_WILL_BELT = ITEMS.register("iron_will_belt",
            () -> new com.eliteforge.accessory.EliteAccessory(com.eliteforge.accessory.EliteAccessory.AccessoryType.BELT, new Item.Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.EPIC)));
    public static final RegistryObject<Item> SOUL_LINK_CHARM = ITEMS.register("soul_link_charm",
            () -> new com.eliteforge.accessory.EliteAccessory(com.eliteforge.accessory.EliteAccessory.AccessoryType.CHARM, new Item.Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.EPIC)));
    public static final RegistryObject<Item> STORM_CROWN = ITEMS.register("storm_crown",
            () -> new com.eliteforge.accessory.EliteAccessory(com.eliteforge.accessory.EliteAccessory.AccessoryType.CROWN, new Item.Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.EPIC)));
    public static final RegistryObject<Item> VOID_WALKER_BRACER = ITEMS.register("void_walker_bracer",
            () -> new com.eliteforge.accessory.EliteAccessory(com.eliteforge.accessory.EliteAccessory.AccessoryType.BRACER, new Item.Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.EPIC)));

    // ========================================================================
    // v0.8.0 — 4 New Materials
    // ========================================================================

    public static final RegistryObject<Item> CORRUPTED_SHARD = ITEMS.register("corrupted_shard", () -> new Item(new Item.Properties().rarity(net.minecraft.world.item.Rarity.UNCOMMON)));
    public static final RegistryObject<Item> CELESTIAL_FRAGMENT = ITEMS.register("celestial_fragment", () -> new Item(new Item.Properties().rarity(net.minecraft.world.item.Rarity.RARE)));
    public static final RegistryObject<Item> ABYSS_CRYSTAL = ITEMS.register("abyss_crystal", () -> new Item(new Item.Properties().rarity(net.minecraft.world.item.Rarity.EPIC)));
    public static final RegistryObject<Item> PHOENIX_ASH = ITEMS.register("phoenix_ash", () -> new Item(new Item.Properties().rarity(net.minecraft.world.item.Rarity.RARE).fireResistant()));

    // ========================================================================
    // v0.8.0 — 8 Equipment Items
    // ========================================================================

    public static final RegistryObject<Item> ELITE_AXE = ITEMS.register("elite_axe", () -> new Item(new Item.Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.RARE)));
    public static final RegistryObject<Item> ELITE_HOE = ITEMS.register("elite_hoe", () -> new Item(new Item.Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.RARE)));
    public static final RegistryObject<Item> ELITE_SHOVEL = ITEMS.register("elite_shovel", () -> new Item(new Item.Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.RARE)));
    public static final RegistryObject<Item> ELITE_HELMET = ITEMS.register("elite_helmet", () -> new Item(new Item.Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.RARE)));
    public static final RegistryObject<Item> ELITE_CHESTPLATE = ITEMS.register("elite_chestplate", () -> new Item(new Item.Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.RARE)));
    public static final RegistryObject<Item> ELITE_LEGGINGS = ITEMS.register("elite_leggings", () -> new Item(new Item.Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.RARE)));
    public static final RegistryObject<Item> ELITE_BOOTS = ITEMS.register("elite_boots", () -> new Item(new Item.Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.RARE)));
    public static final RegistryObject<Item> ELITE_CROSSBOW = ITEMS.register("elite_crossbow", () -> new Item(new Item.Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.RARE)));

    // ========================================================================
    // v0.8.0 — 5 Consumables
    // ========================================================================

    public static final RegistryObject<Item> CORRUPTED_APPLE = ITEMS.register("corrupted_apple", com.eliteforge.item.V08Consumables.CorruptedApple::new);
    public static final RegistryObject<Item> MANA_CRYSTAL = ITEMS.register("mana_crystal", com.eliteforge.item.V08Consumables.ManaCrystal::new);
    public static final RegistryObject<Item> SWIFT_BREW = ITEMS.register("swift_brew", com.eliteforge.item.V08Consumables.SwiftBrew::new);
    public static final RegistryObject<Item> IRON_SKIN_POTION = ITEMS.register("iron_skin_potion", com.eliteforge.item.V08Consumables.IronSkinPotion::new);
    public static final RegistryObject<Item> SHADOW_STEP_ELIXIR = ITEMS.register("shadow_step_elixir", com.eliteforge.item.V08Consumables.ShadowStepElixir::new);

    // ========================================================================
    // v0.8.0 — 2 New System Items (socketing + refinement)
    // ========================================================================

    public static final RegistryObject<Item> SOCKET_GEM = ITEMS.register("socket_gem", com.eliteforge.item.SocketGem::new);
    public static final RegistryObject<Item> REFINEMENT_CATALYST = ITEMS.register("refinement_catalyst", com.eliteforge.item.RefinementCatalyst::new);

    // ========================================================================
    // v0.8.5 — 8 New Accessories (2 new types + 6 variants)
    // ========================================================================

    public static final RegistryObject<Item> VEIL_OF_SHADOWS = ITEMS.register("veil_of_shadows",
            () -> new com.eliteforge.accessory.EliteAccessory(com.eliteforge.accessory.EliteAccessory.AccessoryType.VEIL, new Item.Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.RARE)));
    public static final RegistryObject<Item> RESONANCE_CORE = ITEMS.register("resonance_core",
            () -> new com.eliteforge.accessory.EliteAccessory(com.eliteforge.accessory.EliteAccessory.AccessoryType.CORE_ACC, new Item.Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.RARE)));
    public static final RegistryObject<Item> GALE_RING = ITEMS.register("gale_ring",
            () -> new com.eliteforge.accessory.EliteAccessory(com.eliteforge.accessory.EliteAccessory.AccessoryType.RING, new Item.Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.EPIC)));
    public static final RegistryObject<Item> MOLTEN_CHAIN = ITEMS.register("molten_chain",
            () -> new com.eliteforge.accessory.EliteAccessory(com.eliteforge.accessory.EliteAccessory.AccessoryType.NECKLACE, new Item.Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.EPIC)));
    public static final RegistryObject<Item> GRAVITY_BELT = ITEMS.register("gravity_belt",
            () -> new com.eliteforge.accessory.EliteAccessory(com.eliteforge.accessory.EliteAccessory.AccessoryType.BELT, new Item.Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.EPIC)));
    public static final RegistryObject<Item> DREAMCATCHER = ITEMS.register("dreamcatcher",
            () -> new com.eliteforge.accessory.EliteAccessory(com.eliteforge.accessory.EliteAccessory.AccessoryType.CHARM, new Item.Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.EPIC)));
    public static final RegistryObject<Item> DAWNBREAKER_CROWN = ITEMS.register("dawnbreaker_crown",
            () -> new com.eliteforge.accessory.EliteAccessory(com.eliteforge.accessory.EliteAccessory.AccessoryType.CROWN, new Item.Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.EPIC)));
    public static final RegistryObject<Item> TITAN_BRACER = ITEMS.register("titan_bracer",
            () -> new com.eliteforge.accessory.EliteAccessory(com.eliteforge.accessory.EliteAccessory.AccessoryType.BRACER, new Item.Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.EPIC)));

    // ========================================================================
    // v0.8.5 — 4 New Materials
    // ========================================================================

    public static final RegistryObject<Item> WITHERED_ROOT = ITEMS.register("withered_root", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> STARLIGHT_DUST = ITEMS.register("starlight_dust", () -> new Item(new Item.Properties().rarity(net.minecraft.world.item.Rarity.UNCOMMON)));
    public static final RegistryObject<Item> INFERNO_INGOT = ITEMS.register("inferno_ingot", () -> new Item(new Item.Properties().rarity(net.minecraft.world.item.Rarity.RARE).fireResistant()));
    public static final RegistryObject<Item> NULL_SHARD = ITEMS.register("null_shard", () -> new Item(new Item.Properties().rarity(net.minecraft.world.item.Rarity.EPIC)));

    // ========================================================================
    // v0.8.5 — 8 Equipment / Weapon Items (non-elite names)
    // ========================================================================

    public static final RegistryObject<Item> REAVER_BLADE = ITEMS.register("reaver_blade", () -> new Item(new Item.Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.RARE)));
    public static final RegistryObject<Item> TEMPEST_BOW = ITEMS.register("tempest_bow", () -> new Item(new Item.Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.RARE)));
    public static final RegistryObject<Item> BULWARK_SHIELD = ITEMS.register("bulwark_shield", () -> new Item(new Item.Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.RARE)));
    public static final RegistryObject<Item> MOUNTAIN_CRUSHER = ITEMS.register("mountain_crusher", () -> new Item(new Item.Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.RARE)));
    public static final RegistryObject<Item> WHISPERWOOD_WAND = ITEMS.register("whisperwood_wand", () -> new Item(new Item.Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.RARE)));
    public static final RegistryObject<Item> SOULFIRE_STAFF = ITEMS.register("soulfire_staff", () -> new Item(new Item.Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.EPIC)));
    public static final RegistryObject<Item> ECLIPSE_ARMOR = ITEMS.register("eclipse_armor", () -> new Item(new Item.Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.EPIC)));
    public static final RegistryObject<Item> SKYFALL_BOOTS = ITEMS.register("skyfall_boots", () -> new Item(new Item.Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.EPIC)));

    // ========================================================================
    // v0.8.5 — 5 Consumables
    // ========================================================================

    public static final RegistryObject<Item> MENACE_APPLE = ITEMS.register("menace_apple", com.eliteforge.item.V085Consumables.MenaceApple::new);
    public static final RegistryObject<Item> STAMINA_CRYSTAL = ITEMS.register("stamina_crystal", com.eliteforge.item.V085Consumables.StaminaCrystal::new);
    public static final RegistryObject<Item> HASTE_TONIC = ITEMS.register("haste_tonic", com.eliteforge.item.V085Consumables.HasteTonic::new);
    public static final RegistryObject<Item> BERSERK_BREW = ITEMS.register("berserk_brew", com.eliteforge.item.V085Consumables.BerserkBrew::new);
    public static final RegistryObject<Item> NULL_POTION = ITEMS.register("null_potion", com.eliteforge.item.V085Consumables.NullPotion::new);

    // ========================================================================
    // v0.8.5 — 2 New System Items (awakening + binding)
    // ========================================================================

    public static final RegistryObject<Item> AWAKENING_ESSENCE = ITEMS.register("awakening_essence", com.eliteforge.item.AwakeningEssence::new);
    public static final RegistryObject<Item> BINDING_STONE = ITEMS.register("binding_stone", com.eliteforge.item.BindingStone::new);

    // ========================================================================
    // v0.9.0 — 8 New Accessories (2 new types + 6 variants)
    // ========================================================================
    public static final RegistryObject<Item> ANCIENT_RELIC_AMULET = ITEMS.register("ancient_relic_amulet",
            () -> new com.eliteforge.accessory.EliteAccessory(com.eliteforge.accessory.EliteAccessory.AccessoryType.RELIC, new Item.Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.RARE)));
    public static final RegistryObject<Item> HUNTERS_COMPASS = ITEMS.register("hunters_compass",
            () -> new com.eliteforge.accessory.EliteAccessory(com.eliteforge.accessory.EliteAccessory.AccessoryType.COMPASS, new Item.Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.RARE)));
    public static final RegistryObject<Item> EMBER_RING = ITEMS.register("ember_ring",
            () -> new com.eliteforge.accessory.EliteAccessory(com.eliteforge.accessory.EliteAccessory.AccessoryType.RING, new Item.Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.EPIC)));
    public static final RegistryObject<Item> TIDAL_PENDANT = ITEMS.register("tidal_pendant",
            () -> new com.eliteforge.accessory.EliteAccessory(com.eliteforge.accessory.EliteAccessory.AccessoryType.NECKLACE, new Item.Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.EPIC)));
    public static final RegistryObject<Item> BERSERKER_BELT = ITEMS.register("berserker_belt",
            () -> new com.eliteforge.accessory.EliteAccessory(com.eliteforge.accessory.EliteAccessory.AccessoryType.BELT, new Item.Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.EPIC)));
    public static final RegistryObject<Item> SPIRIT_TALISMAN = ITEMS.register("spirit_talisman",
            () -> new com.eliteforge.accessory.EliteAccessory(com.eliteforge.accessory.EliteAccessory.AccessoryType.CHARM, new Item.Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.EPIC)));
    public static final RegistryObject<Item> FROST_CROWN = ITEMS.register("frost_crown",
            () -> new com.eliteforge.accessory.EliteAccessory(com.eliteforge.accessory.EliteAccessory.AccessoryType.CROWN, new Item.Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.EPIC)));
    public static final RegistryObject<Item> ADAMANT_BRACER = ITEMS.register("adamant_bracer",
            () -> new com.eliteforge.accessory.EliteAccessory(com.eliteforge.accessory.EliteAccessory.AccessoryType.BRACER, new Item.Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.EPIC)));

    // ========================================================================
    // v0.9.0 — 4 New Materials
    // ========================================================================
    public static final RegistryObject<Item> TWILIGHT_CORE = ITEMS.register("twilight_core", () -> new Item(new Item.Properties().rarity(net.minecraft.world.item.Rarity.RARE)));
    public static final RegistryObject<Item> MAGMA_SHARD = ITEMS.register("magma_shard", () -> new Item(new Item.Properties().rarity(net.minecraft.world.item.Rarity.UNCOMMON).fireResistant()));
    public static final RegistryObject<Item> FROST_CRYSTAL = ITEMS.register("frost_crystal", () -> new Item(new Item.Properties().rarity(net.minecraft.world.item.Rarity.UNCOMMON)));
    public static final RegistryObject<Item> SPECTRAL_DUST = ITEMS.register("spectral_dust", () -> new Item(new Item.Properties().rarity(net.minecraft.world.item.Rarity.RARE)));

    // ========================================================================
    // v0.9.0 — 4 Equipment / Weapon Items
    // ========================================================================
    public static final RegistryObject<Item> VOID_CLEAVER = ITEMS.register("void_cleaver", () -> new Item(new Item.Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.EPIC)));
    public static final RegistryObject<Item> STORM_PIKE = ITEMS.register("storm_pike", () -> new Item(new Item.Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.RARE)));
    public static final RegistryObject<Item> PHANTOM_BOW = ITEMS.register("phantom_bow", () -> new Item(new Item.Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.RARE)));
    public static final RegistryObject<Item> ABYSSAL_TRIDENT = ITEMS.register("abyssal_trident", () -> new Item(new Item.Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.EPIC)));

    // ========================================================================
    // v0.9.0 — 9 Consumables / Utility Items
    // ========================================================================
    public static final RegistryObject<Item> MENACE_STEW = ITEMS.register("menace_stew", () -> new Item(new Item.Properties().stacksTo(16).food(new net.minecraft.world.food.FoodProperties.Builder().nutrition(10).saturationMod(1.5f).build())));
    public static final RegistryObject<Item> GOLDEN_MENACE = ITEMS.register("golden_menace", () -> new Item(new Item.Properties().stacksTo(8).rarity(net.minecraft.world.item.Rarity.RARE).food(new net.minecraft.world.food.FoodProperties.Builder().nutrition(12).saturationMod(2.0f).effect(() -> new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.REGENERATION, 200, 1), 1.0f).build())));
    public static final RegistryObject<Item> CRYSTAL_BERRY = ITEMS.register("crystal_berry", () -> new Item(new Item.Properties().stacksTo(32).food(new net.minecraft.world.food.FoodProperties.Builder().nutrition(4).saturationMod(0.6f).build())));
    public static final RegistryObject<Item> TELEPORT_SCROLL = ITEMS.register("teleport_scroll", com.eliteforge.item.V09Items.TeleportScroll::new);
    public static final RegistryObject<Item> LIGHTNING_ROD = ITEMS.register("lightning_rod", com.eliteforge.item.V09Items.LightningRod::new);
    public static final RegistryObject<Item> SMOKE_BOMB = ITEMS.register("smoke_bomb", com.eliteforge.item.V09Items.SmokeBomb::new);
    public static final RegistryObject<Item> HEALING_TOTEM = ITEMS.register("healing_totem", com.eliteforge.item.V09Items.HealingTotem::new);
    public static final RegistryObject<Item> XP_CRYSTAL = ITEMS.register("xp_crystal", com.eliteforge.item.V09Items.XPCrystal::new);
    public static final RegistryObject<Item> REVEAL_COMPASS = ITEMS.register("reveal_compass", com.eliteforge.item.V09Items.RevealCompass::new);

    // ========================================================================
    // v0.9.0 — 2 New System Items (reforge + transmute)
    // ========================================================================
    public static final RegistryObject<Item> REFORGE_HAMMER = ITEMS.register("reforge_hammer", com.eliteforge.item.V09Items.ReforgeHammer::new);
    public static final RegistryObject<Item> TRANSMUTE_STONE = ITEMS.register("transmute_stone", com.eliteforge.item.V09Items.TransmuteStone::new);

    // ========================================================================
    // v0.6.5 — New Material Items (replacing more vanilla placeholders)
    // ========================================================================

    public static final RegistryObject<Item> ELITE_CORE = ITEMS.register("elite_core",
            () -> new Item(new Item.Properties().rarity(net.minecraft.world.item.Rarity.RARE)));

    public static final RegistryObject<Item> ELITE_CRYSTAL = ITEMS.register("elite_crystal",
            () -> new Item(new Item.Properties().rarity(net.minecraft.world.item.Rarity.RARE)));

    public static final RegistryObject<Item> ELITE_FRAGMENT = ITEMS.register("elite_fragment",
            () -> new Item(new Item.Properties().rarity(net.minecraft.world.item.Rarity.RARE)));

    // ========================================================================
    // v0.6.5 — Catalyst of Fusion (accessory fusion system)
    // ========================================================================

    public static final RegistryObject<Item> FUSION_CATALYST = ITEMS.register("fusion_catalyst",
            com.eliteforge.item.CatalystOfFusion::new);

    // ========================================================================
    // v0.6.0 — Real Material Items (replacing vanilla placeholders)
    // ========================================================================

    public static final RegistryObject<Item> ELITE_ESSENCE = ITEMS.register("elite_essence",
            () -> new Item(new Item.Properties()));

    public static final RegistryObject<Item> ELITE_SHARD = ITEMS.register("elite_shard",
            () -> new Item(new Item.Properties()));

// ========================================================================
    // Block Items (for all mod blocks)
    // ========================================================================

    public static final RegistryObject<Item> ELITE_SPAWNER = ITEMS.register("elite_spawner",
            () -> new BlockItem(ModBlocks.ELITE_SPAWNER.get(), new Item.Properties()));

    // FORGING_ANVIL / HEAT_COLLECTOR / TEMPERING_STATION block-items were removed in
    // v0.2.0 phase 1 along with the forging system; only Elite Beacon remains as a block item.

    public static final RegistryObject<Item> ELITE_BEACON = ITEMS.register("elite_beacon",
            () -> new BlockItem(ModBlocks.ELITE_BEACON.get(), new Item.Properties()));

    // ========================================================================
    // Helper Methods
    // ========================================================================

    /**
     * Register a block item with standard properties.
     * This is a convenience method for creating block items consistently.
     */
    private static RegistryObject<Item> registerBlockItem(String name, RegistryObject<Block> block) {
        return ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties()));
    }
}
