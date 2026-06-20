package com.eliteforge.init;

import com.eliteforge.EliteForge;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Sound event registration for EliteForge.
 * <p>
 * The registered event names MUST exactly match the keys in
 * {@code assets/eliteforge/sounds.json} — otherwise the engine logs
 * "Unable to play unknown soundEvent" and plays nothing.
 * <p>
 * v0.2.0 cleanup: removed the 5 sounds tied to the deleted forging system
 * (quench / forge_hammer / tempering / anvil_use / heat_collector) and
 * renamed elite_spawn_sound→elite_spawn / elite_death_sound→elite_death
 * to match sounds.json. Added ability_* events for future use by ability
 * onAttack/onTick hooks.
 */
public class ModSounds {

    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, EliteForge.MODID);

    /** Played when an elite mob spawns. */
    public static final RegistryObject<SoundEvent> ELITE_SPAWN = SOUND_EVENTS.register("elite_spawn",
            () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(EliteForge.MODID, "elite_spawn")));

    /** Played when an elite mob is defeated. */
    public static final RegistryObject<SoundEvent> ELITE_DEATH = SOUND_EVENTS.register("elite_death",
            () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(EliteForge.MODID, "elite_death")));

    /** Played when the Elite Beacon activates or changes suppression tier. */
    public static final RegistryObject<SoundEvent> BEACON_ACTIVATE = SOUND_EVENTS.register("beacon_activate",
            () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(EliteForge.MODID, "beacon_activate")));

    /** Played when an ability that deals fire damage fires. */
    public static final RegistryObject<SoundEvent> ABILITY_FIRE = SOUND_EVENTS.register("ability_fire",
            () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(EliteForge.MODID, "ability_fire")));

    /** Played when an ability that deals frost damage fires. */
    public static final RegistryObject<SoundEvent> ABILITY_FROST = SOUND_EVENTS.register("ability_frost",
            () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(EliteForge.MODID, "ability_frost")));

    /** Played when an ability that calls down lightning fires. */
    public static final RegistryObject<SoundEvent> ABILITY_LIGHTNING = SOUND_EVENTS.register("ability_lightning",
            () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(EliteForge.MODID, "ability_lightning")));

    /** Played when an ability that deals poison damage fires. */
    public static final RegistryObject<SoundEvent> ABILITY_POISON = SOUND_EVENTS.register("ability_poison",
            () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(EliteForge.MODID, "ability_poison")));

    /** Played when a defense ability (Shield/Reflect/etc.) procs. */
    public static final RegistryObject<SoundEvent> ABILITY_SHIELD = SOUND_EVENTS.register("ability_shield",
            () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(EliteForge.MODID, "ability_shield")));

    /** Played when a teleport-style ability fires. */
    public static final RegistryObject<SoundEvent> ABILITY_TELEPORT = SOUND_EVENTS.register("ability_teleport",
            () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(EliteForge.MODID, "ability_teleport")));

    /** Played when an explosion-style ability fires. */
    public static final RegistryObject<SoundEvent> ABILITY_EXPLOSION = SOUND_EVENTS.register("ability_explosion",
            () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(EliteForge.MODID, "ability_explosion")));

    /** Played when an accessory is successfully upgraded. */
    public static final RegistryObject<SoundEvent> QUALITY_UPGRADE = SOUND_EVENTS.register("quality_upgrade",
            () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(EliteForge.MODID, "quality_upgrade")));

    /** Played when an item infuses an ability (AbilityInfuser). */
    public static final RegistryObject<SoundEvent> ABILITY_INFUSE = SOUND_EVENTS.register("ability_infuse",
            () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(EliteForge.MODID, "ability_infuse")));

    /** Played when chunk heat crosses a high threshold (HeatProbe). */
    public static final RegistryObject<SoundEvent> HEAT_ALERT = SOUND_EVENTS.register("heat_alert",
            () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(EliteForge.MODID, "heat_alert")));

    /** Played when Necromancy raises undead. */
    public static final RegistryObject<SoundEvent> NECROMANCY_SUMMON = SOUND_EVENTS.register("necromancy_summon",
            () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(EliteForge.MODID, "necromancy_summon")));

    /** Played when the Storm legendary ability triggers. */
    public static final RegistryObject<SoundEvent> STORM_THUNDER = SOUND_EVENTS.register("storm_thunder",
            () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(EliteForge.MODID, "storm_thunder")));
}
