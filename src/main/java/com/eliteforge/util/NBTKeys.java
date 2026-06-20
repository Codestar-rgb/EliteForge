package com.eliteforge.util;

/**
 * Centralized NBT key constants for EliteForge.
 * All NBT keys used throughout the mod are defined here to prevent
 * typos and ensure consistency. When adding new NBT keys, add them
 * to the appropriate section below.
 */
public final class NBTKeys {

    private NBTKeys() {
        // Utility class - no instantiation
    }

    // ==================== Dynamic Strengthening ====================
    public static final String TIME_STRENGTHEN_TICK = "EliteForgeTimeStrengthenTick";
    public static final String TIME_HEALTH_STACKS = "EliteForgeTimeHealthStacks";
    public static final String TIME_DAMAGE_STACKS = "EliteForgeTimeDamageStacks";
    public static final String TIME_LAST_HEALTH_STACKS = "EliteForgeTimeLastHealthStacks";
    public static final String TIME_LAST_DAMAGE_STACKS = "EliteForgeTimeLastDamageStacks";
    public static final String KILL_STRENGTHEN_COUNT = "EliteForgeKillStrengthenCount";
    public static final String KILL_STRENGTHEN_EXPIRY = "EliteForgeKillStrengthenExpiry";
    public static final String KILL_LAST_COUNT = "EliteForgeKillLastCount";
    public static final String HEAT_ABILITY_TIMER = "EliteForgeHeatAbilityTimer";
    public static final String TEMP_ABILITIES = "EliteForgeTempAbilities";
    /** P3: Tracks the last applied group strengthening bonus (encoded as bonus * 1000)
     *  to avoid unnecessary attribute modifier churn when the bonus hasn't changed. */
    public static final String GROUP_LAST_BONUS = "EliteForgeGroupLastBonus";

    // ==================== Nexus (C1) ====================
    public static final String NEXUS_COOLDOWN = "EliteForgeNexusCooldown";
    public static final String NEXUS_NURTURED = "EliteForgeNexusNurtured";
    public static final String NEXUS_BONUSES_APPLIED = "EliteForgeNexusBonusesApplied";
    public static final String NEXUS_ACTIVE = "EliteForgeNexusActive";

    // ==================== Dominion (C2) ====================
    public static final String DOMINION_ACTIVE = "EliteForgeDominionActive";
    public static final String DOMINION_TIMER = "EliteForgeDominionTimer";
    public static final String DOMINION_COOLDOWN = "EliteForgeDominionCooldown";
    public static final String DOMINION_NO_PLACE = "EliteForgeDominionNoPlace";

    // ==================== Evolution (C3) ====================
    public static final String EVOLUTION_COUNT = "EliteForgeEvolutionCount";
    public static final String EVOLUTION_DAMAGE_ACCUM = "EliteForgeEvolutionDamageAccum";
    public static final String EVOLUTION_APPLIED = "EliteForgeEvolutionApplied";

    // ==================== Assimilate (C4) ====================
    public static final String ASSIMILATE_COOLDOWN = "EliteForgeAssimilateCooldown";
    public static final String ASSIMILATE_APPLIED_COUNT = "EliteForgeAssimilateAppliedCount";
    public static final String ASSIMILATE_INVULN = "EliteForgeAssimilateInvuln";

    // ==================== Bestowal (C5) ====================
    public static final String BESTOWAL_COOLDOWN = "EliteForgeBestowalCooldown";
    public static final String BESTOWAL_REVERT = "EliteForgeBestowalRevert";

    // ==================== Annihilate (C6) ====================
    public static final String ANNIHILATE_WARNING = "EliteForgeAnnihilateWarning";
    public static final String ANNIHILATE_WARNING_TICKS = "EliteForgeAnnihilateWarningTicks";
    public static final String ANNIHILATE_TRIGGERED = "EliteForgeAnnihilateTriggered";
    public static final String ANNIHILATE_KILLER_UUID = "EliteForgeAnnihilateKillerUUID";
    public static final String ANNIHILATE_CHAIN_EXPLOSION = "EliteForgeAnnihilateChainExplosion";
    /** Game-tick value at which the chain-explosion flag should be considered expired.
     *  Paired with {@link #ANNIHILATE_CHAIN_EXPLOSION} so that a server restart inside
     *  the 5-tick cleanup window no longer leaves the flag permanently set. */
    public static final String ANNIHILATE_CHAIN_EXPLOSION_EXPIRY = "EliteForgeAnnihilateChainExplosionExpiry";

    // ==================== Reincarnation (C7) ====================
    public static final String REINCARNATION_REMAINING = "EliteForgeReincarnationRemaining";
    public static final String REINCARNATION_COUNT = "EliteForgeReincarnationCount";
    public static final String REINCARNATION_REVIVING = "EliteForgeReincarnationReviving";
    public static final String REINCARNATION_REVIVE_TIMER = "EliteForgeReincarnationReviveTimer";
    public static final String REINCARNATION_STORED_LEVEL = "EliteForgeReincarnationStoredLevel";
    public static final String REINCARNATION_INVULN = "EliteForgeReincarnationInvuln";

    // ==================== Commander (C8) ====================
    public static final String COMMANDER_COOLDOWN = "EliteForgeCommanderCooldown";
    public static final String COMMANDER_SQUAD = "EliteForgeCommanderSquad";

    // ==================== Awakening ====================
    public static final String AWAKENING_TIMER = "EliteForgeAwakeningTimer";
    public static final String AWAKENING_IS_AWAKENING = "EliteForgeIsAwakening";
    public static final String AWAKENING_FREEZE_TICKS = "EliteForgeAwakeningFreezeTicks";

    // ==================== Berserk ====================
    public static final String BERSERK_REVIVED = "EliteForge_BerserkRevived";
    public static final String BERSERK_LEVEL = "EliteForge_BerserkLevel";

    // ==================== Clone ====================
    public static final String CLONE_SPAWNED = "EliteForgeCloneSpawned";

    // ==================== Summon Linking ====================
    /** UUID of the entity that summoned this entity (Necromancy undead, Clone clones).
     *  Stored both on the entity's persistent data (for fast server-side leash checks)
     *  and in the EliteData capability (for client-side chain rendering via sync). */
    public static final String SUMMONER_UUID = "EliteForgeSummonerUUID";

    // ==================== Doom ====================
    public static final String DOOM_ACTIVATED = "EliteForgeDoomActivated";
    public static final String DOOM_TIMER = "EliteForgeDoomTimer";
    public static final String DOOM_ENGAGED = "EliteForgeDoomEngaged";

    // ==================== Mutation ====================
    public static final String MUTATION_COOLDOWN = "EliteForgeMutationCooldown";
    public static final String MUTATION_ACTIVE = "EliteForgeMutationActive";
    public static final String MUTATION_ABILITY = "EliteForgeMutationAbility";
    public static final String MUTATION_LEVEL = "EliteForgeMutationLevel";
    public static final String MUTATION_TIMER = "EliteForgeMutationTimer";

    // ==================== Necromancy ====================
    public static final String NECROMANCY_COOLDOWN = "EliteForgeNecroCooldown";

    // ==================== Phase Shift ====================
    public static final String PHASE_SHIFT_COOLDOWN = "EliteForgePhaseShiftCooldown";
    public static final String PHASE_SHIFT_INVISIBLE = "EliteForgePhaseShiftInvisible";
    public static final String PHASE_SHIFT_INVIS_TIMER = "EliteForgePhaseShiftInvisTimer";

    // ==================== Storm ====================
    public static final String STORM_COOLDOWN = "EliteForgeStormCooldown";

    // ==================== Time Warp ====================
    public static final String TIME_WARP_COOLDOWN = "EliteForgeTimeWarpCooldown";

    // ==================== Chaos ====================
    public static final String CHAOS_COOLDOWN = "EliteForgeChaosCooldown";

    // ==================== Void ====================
    public static final String VOID_COOLDOWN = "EliteForgeVoidCooldown";

    // ==================== Phase ====================
    public static final String PHASE_DATA = "EliteForgePhaseData";

    // ==================== Level Data ====================
    public static final String SCORCHED_ZONES = "EliteForgeScorchedZones";

    // ==================== Legacy Ability Manager (deprecated) ====================
    /** Legacy NBT key used by the deprecated AbilityManager class. Retained for backward compatibility with old save data. */
    public static final String LEGACY_ABILITIES = "EliteForgeAbilities";

    // ==================== General Entity Data (underscore convention) ====================
    // These keys are stored directly on the entity's persistent data (Forge's getPersistentData())
    // and use the EliteForge_ prefix convention. They are shared across handlers,
    // enchantments, and renderers for fast is-elite checks and quality-tier lookups
    // without needing to deserialize the full EliteData capability.
    public static final String ENTITY_IS_ELITE = "EliteForge_IsElite";
    public static final String ENTITY_LEVEL = "EliteForge_Level";
    public static final String ENTITY_QUALITY_TIER = "EliteForge_QualityTier";
    public static final String ENTITY_ABILITIES = "EliteForge_Abilities";
    public static final String ENTITY_ABILITY_COUNT = "EliteForge_AbilityCount";
    public static final String PLAYER_EXPERIENCE = "EliteForge_Experience";
    public static final String CHUNK_HEAT = "EliteForge_ChunkHeat";

    // ==================== Player Kill Statistics ====================
    public static final String PLAYER_ELITE_KILLS = "EliteForgeEliteKills";
    public static final String PLAYER_LEGENDARY_KILLS = "EliteForgeLegendaryKills";
    public static final String PLAYER_MYTHIC_KILLS = "EliteForgeMythicKills";
    public static final String PLAYER_HIGH_LEVEL_KILLS = "EliteForgeHighLevelKills";

    // ==================== Item Tags ====================
    public static final String ITEM_CREATOR_FRAGMENT = "EliteForgeCreatorFragment";
    public static final String ITEM_REROLL_SCROLL = "EliteForgeRerollScroll";
    public static final String ITEM_ENHANCEMENT = "EliteForgeEnhancement";
    public static final String ITEM_QUALITY = "EliteForgeQuality";
    public static final String ITEM_QUALITY_NAME = "EliteForgeQualityName";
    public static final String ITEM_QUALITY_COLOR = "EliteForgeQualityColor";
    public static final String ITEM_SET_TYPE = "EliteForgeSetType";
    public static final String ITEM_REINCARNATION_CRYSTAL = "EliteForgeReincarnationCrystal";
}
