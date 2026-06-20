package com.eliteforge.init;

import com.eliteforge.EliteForge;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * Creative tab registration class for EliteForge.
 * Creates a single creative tab that displays all mod items and block items.
 * The tab uses the forging anvil as its icon.
 */
public class ModCreativeTabs {

    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, EliteForge.MODID);

    /**
     * EliteForge creative tab - displays all mod items with the forging anvil icon.
     * Items are ordered: blocks first, then tools, then materials, then consumables.
     */
    public static final RegistryObject<CreativeModeTab> ELITEFORGE_TAB = CREATIVE_MODE_TABS.register("eliteforge_tab",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.eliteforge"))
                    .icon(() -> new ItemStack(ModBlocks.ELITE_BEACON.get()))
                    .displayItems((parameters, output) -> {
                        // === Block Items ===
                        output.accept(ModItems.ELITE_SPAWNER.get());
                        output.accept(ModItems.ELITE_BEACON.get());

                        // === Tools ===
                        output.accept(ModItems.ABILITY_EXTRACTOR.get());
                        output.accept(ModItems.ABILITY_INFUSER.get());

                        // === Materials ===

                        // === Consumables ===
                        output.accept(ModItems.ELITE_NAME_TAG.get());
                        output.accept(ModItems.REROLL_SCROLL.get());
                        output.accept(ModItems.PURIFICATION_FLASK.get());

                        // === Guide Book + Heat Probe + Summon Totem ===
                        output.accept(ModItems.GUIDE_BOOK.get());
                        output.accept(ModItems.HEAT_PROBE.get());
                        output.accept(ModItems.SUMMON_TOTEM.get());

                        // === Accessories ===
                        output.accept(ModItems.ELITE_RING.get());
                        output.accept(ModItems.ELITE_NECKLACE.get());
                        output.accept(ModItems.ELITE_BELT.get());
                        output.accept(ModItems.ELITE_CHARM.get());
                        output.accept(ModItems.ELITE_CROWN.get());
                        output.accept(ModItems.ELITE_BRACER.get());
                        output.accept(ModItems.ELITE_CAPE.get());
                        output.accept(ModItems.ELITE_GAUNTLET.get());
                        output.accept(ModItems.ELITE_AMULET.get());
                        output.accept(ModItems.SIGIL_OF_WARDING.get());
                        output.accept(ModItems.INSIGNIA_OF_COMMAND.get());
                        output.accept(ModItems.TALISMAN_OF_GREED.get());
                        output.accept(ModItems.VOID_BAND.get());
                        output.accept(ModItems.SOLAR_PENDANT.get());
                        output.accept(ModItems.AETHER_ORB.get());
                        output.accept(ModItems.WARD_STONE.get());
                        output.accept(ModItems.PHANTOM_RING.get());
                        output.accept(ModItems.EMBER_CHARM.get());
                        output.accept(ModItems.TIDE_PENDANT.get());

                        // === Materials (v0.6.0 + v0.6.5 + v0.7.0) ===
                        output.accept(ModItems.ELITE_ESSENCE.get());
                        output.accept(ModItems.ELITE_SHARD.get());
                        output.accept(ModItems.ELITE_CORE.get());
                        output.accept(ModItems.ELITE_CRYSTAL.get());
                        output.accept(ModItems.ELITE_FRAGMENT.get());
                        output.accept(ModItems.FUSION_CATALYST.get());
                        output.accept(ModItems.ANCIENT_RELIC.get());
                        output.accept(ModItems.SOUL_RESIDUE.get());
                        output.accept(ModItems.DRAGON_SCALE.get());
                        output.accept(ModItems.SHADOW_FANG.get());

                        // === Tools (v0.7.0) ===
                        output.accept(ModItems.FORGE_HAMMER.get());
                        output.accept(ModItems.RESONANCE_TUNER.get());
                        output.accept(ModItems.LOOT_COMPASS.get());
                        // v0.7.5 accessories
                        output.accept(ModItems.RUNE_OF_HASTE.get());
                        output.accept(ModItems.AEGIS_PLATE.get());
                        output.accept(ModItems.SHADOW_CLOAK.get());
                        output.accept(ModItems.CRYSTAL_BAND.get());
                        output.accept(ModItems.PHOENIX_FEATHER.get());
                        output.accept(ModItems.ABYSSAL_CROWN.get());
                        // v0.7.5 materials
                        output.accept(ModItems.ARCANE_DUST.get());
                        output.accept(ModItems.PRIMAL_BONE.get());
                        output.accept(ModItems.VOID_EYE.get());
                        output.accept(ModItems.ETERNAL_EMBER.get());
                        // v0.7.5 equipment
                        output.accept(ModItems.ELITE_SWORD.get());
                        output.accept(ModItems.ELITE_BOW.get());
                        output.accept(ModItems.ELITE_SHIELD.get());
                        output.accept(ModItems.ELITE_PICKAXE.get());
                        output.accept(ModItems.BATTLE_WAND.get());
                        output.accept(ModItems.SOUL_REAPER.get());
                        output.accept(ModItems.STORM_CALLER.get());
                        output.accept(ModItems.VOID_BLADE.get());
                        // v0.7.5 consumables
                        output.accept(ModItems.ELITE_APPLE.get());
                        output.accept(ModItems.ENERGY_BREW.get());
                        output.accept(ModItems.SHIELD_POTION.get());
                        output.accept(ModItems.RAGE_ELIXIR.get());
                        output.accept(ModItems.PHOENIX_BREW.get());
                        // v0.7.5 system items
                        output.accept(ModItems.DISMANTLE_KIT.get());
                        output.accept(ModItems.WEAPON_ENHANCER.get());
                        // v0.8.0
                        output.accept(ModItems.MIRROR_OF_REFLECTION.get());
                        output.accept(ModItems.CATALYST_STONE.get());
                        output.accept(ModItems.FROST_BITE_RING.get());
                        output.accept(ModItems.THUNDER_CHAIN.get());
                        output.accept(ModItems.IRON_WILL_BELT.get());
                        output.accept(ModItems.SOUL_LINK_CHARM.get());
                        output.accept(ModItems.STORM_CROWN.get());
                        output.accept(ModItems.VOID_WALKER_BRACER.get());
                        output.accept(ModItems.CORRUPTED_SHARD.get());
                        output.accept(ModItems.CELESTIAL_FRAGMENT.get());
                        output.accept(ModItems.ABYSS_CRYSTAL.get());
                        output.accept(ModItems.PHOENIX_ASH.get());
                        output.accept(ModItems.ELITE_AXE.get());
                        output.accept(ModItems.ELITE_HOE.get());
                        output.accept(ModItems.ELITE_SHOVEL.get());
                        output.accept(ModItems.ELITE_HELMET.get());
                        output.accept(ModItems.ELITE_CHESTPLATE.get());
                        output.accept(ModItems.ELITE_LEGGINGS.get());
                        output.accept(ModItems.ELITE_BOOTS.get());
                        output.accept(ModItems.ELITE_CROSSBOW.get());
                        output.accept(ModItems.CORRUPTED_APPLE.get());
                        output.accept(ModItems.MANA_CRYSTAL.get());
                        output.accept(ModItems.SWIFT_BREW.get());
                        output.accept(ModItems.IRON_SKIN_POTION.get());
                        output.accept(ModItems.SHADOW_STEP_ELIXIR.get());
                        output.accept(ModItems.SOCKET_GEM.get());
                        output.accept(ModItems.REFINEMENT_CATALYST.get());
                        // v0.8.5
                        output.accept(ModItems.VEIL_OF_SHADOWS.get());
                        output.accept(ModItems.RESONANCE_CORE.get());
                        output.accept(ModItems.GALE_RING.get());
                        output.accept(ModItems.MOLTEN_CHAIN.get());
                        output.accept(ModItems.GRAVITY_BELT.get());
                        output.accept(ModItems.DREAMCATCHER.get());
                        output.accept(ModItems.DAWNBREAKER_CROWN.get());
                        output.accept(ModItems.TITAN_BRACER.get());
                        output.accept(ModItems.WITHERED_ROOT.get());
                        output.accept(ModItems.STARLIGHT_DUST.get());
                        output.accept(ModItems.INFERNO_INGOT.get());
                        output.accept(ModItems.NULL_SHARD.get());
                        output.accept(ModItems.REAVER_BLADE.get());
                        output.accept(ModItems.TEMPEST_BOW.get());
                        output.accept(ModItems.BULWARK_SHIELD.get());
                        output.accept(ModItems.MOUNTAIN_CRUSHER.get());
                        output.accept(ModItems.WHISPERWOOD_WAND.get());
                        output.accept(ModItems.SOULFIRE_STAFF.get());
                        output.accept(ModItems.ECLIPSE_ARMOR.get());
                        output.accept(ModItems.SKYFALL_BOOTS.get());
                        output.accept(ModItems.MENACE_APPLE.get());
                        output.accept(ModItems.STAMINA_CRYSTAL.get());
                        output.accept(ModItems.HASTE_TONIC.get());
                        output.accept(ModItems.BERSERK_BREW.get());
                        output.accept(ModItems.NULL_POTION.get());
                        output.accept(ModItems.AWAKENING_ESSENCE.get());
                        output.accept(ModItems.BINDING_STONE.get());
                        // v0.9.0
                        output.accept(ModItems.ANCIENT_RELIC_AMULET.get());
                        output.accept(ModItems.HUNTERS_COMPASS.get());
                        output.accept(ModItems.EMBER_RING.get());
                        output.accept(ModItems.TIDAL_PENDANT.get());
                        output.accept(ModItems.BERSERKER_BELT.get());
                        output.accept(ModItems.SPIRIT_TALISMAN.get());
                        output.accept(ModItems.FROST_CROWN.get());
                        output.accept(ModItems.ADAMANT_BRACER.get());
                        output.accept(ModItems.TWILIGHT_CORE.get());
                        output.accept(ModItems.MAGMA_SHARD.get());
                        output.accept(ModItems.FROST_CRYSTAL.get());
                        output.accept(ModItems.SPECTRAL_DUST.get());
                        output.accept(ModItems.VOID_CLEAVER.get());
                        output.accept(ModItems.STORM_PIKE.get());
                        output.accept(ModItems.PHANTOM_BOW.get());
                        output.accept(ModItems.ABYSSAL_TRIDENT.get());
                        output.accept(ModItems.MENACE_STEW.get());
                        output.accept(ModItems.GOLDEN_MENACE.get());
                        output.accept(ModItems.CRYSTAL_BERRY.get());
                        output.accept(ModItems.TELEPORT_SCROLL.get());
                        output.accept(ModItems.LIGHTNING_ROD.get());
                        output.accept(ModItems.SMOKE_BOMB.get());
                        output.accept(ModItems.HEALING_TOTEM.get());
                        output.accept(ModItems.XP_CRYSTAL.get());
                        output.accept(ModItems.REVEAL_COMPASS.get());
                        output.accept(ModItems.REFORGE_HAMMER.get());
                        output.accept(ModItems.TRANSMUTE_STONE.get());

                        // === Creator Drops ===
                        output.accept(ModItems.CREATOR_FRAGMENT.get());
                        output.accept(ModItems.REINCARNATION_CRYSTAL.get());
                        output.accept(ModItems.SCORCHED_CORE.get());
                        output.accept(ModItems.DOMINION_SCEPTER.get());
                        output.accept(ModItems.EVOLUTION_CORE.get());
                        output.accept(ModItems.COMMAND_BANNER.get());
                        output.accept(ModItems.NEXUS_ESSENCE.get());
                        output.accept(ModItems.BESTOWAL_SIGIL.get());
                    })
                    .build()
    );
}
