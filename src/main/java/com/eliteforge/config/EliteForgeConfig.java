package com.eliteforge.config;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.Arrays;
import java.util.List;

/**
 * Comprehensive Forge configuration for EliteForge.
 * Split into COMMON (synced), SERVER (server-authoritative), and CLIENT (client-only) groups.
 *
 * COMMON: Core gameplay settings that affect both client and server.
 * SERVER: Balance settings controlled by server operators.
 * CLIENT: Visual and rendering settings for the client.
 */
public class EliteForgeConfig {

    // ========================================================================
    // COMMON CONFIG - Core gameplay settings
    // ========================================================================
    public static final Common COMMON;
    public static final ForgeConfigSpec COMMON_SPEC;

    // ========================================================================
    // SERVER CONFIG - Server-authoritative balance settings
    // ========================================================================
    public static final Server SERVER;
    public static final ForgeConfigSpec SERVER_SPEC;

    // ========================================================================
    // CLIENT CONFIG - Client-only visual settings
    // ========================================================================
    public static final Client CLIENT;
    public static final ForgeConfigSpec CLIENT_SPEC;

    static {
        ForgeConfigSpec.Builder commonBuilder = new ForgeConfigSpec.Builder();
        COMMON = new Common(commonBuilder);
        COMMON_SPEC = commonBuilder.build();

        ForgeConfigSpec.Builder serverBuilder = new ForgeConfigSpec.Builder();
        SERVER = new Server(serverBuilder);
        SERVER_SPEC = serverBuilder.build();

        ForgeConfigSpec.Builder clientBuilder = new ForgeConfigSpec.Builder();
        CLIENT = new Client(clientBuilder);
        CLIENT_SPEC = clientBuilder.build();
    }

    // ========================================================================
    // Common Configuration
    // ========================================================================
    public static class Common {

        // --- Core Settings ---
        public final ForgeConfigSpec.EnumValue<DifficultyMode> difficultyMode;
        public final ForgeConfigSpec.BooleanValue enableEliteMobs;
        public final ForgeConfigSpec.DoubleValue globalSpawnChance;
        public final ForgeConfigSpec.IntValue maxEliteLevel;
        public final ForgeConfigSpec.IntValue maxCasualLevel;
        public final ForgeConfigSpec.IntValue maxAbilitiesPerElite;

        // --- Feature Toggles ---
        public final ForgeConfigSpec.BooleanValue enableChunkHeat;
        public final ForgeConfigSpec.BooleanValue enablePlayerExperience;
        public final ForgeConfigSpec.BooleanValue enableQualitySystem;
        // enableSetBonuses removed in v0.2.0
        // enableTemperedMaterials removed in v0.2.0

        // --- Mode-Specific Spawn Chances ---
        public final ForgeConfigSpec.DoubleValue forgeModeSpawnChance;
        public final ForgeConfigSpec.DoubleValue casualModeSpawnChance;
        public final ForgeConfigSpec.DoubleValue mixedModeForgeChance;
        public final ForgeConfigSpec.DoubleValue mixedModeCasualChance;

        public Common(ForgeConfigSpec.Builder builder) {
            builder.comment("EliteForge Common Configuration", "Core gameplay settings that affect both client and server.")
                   .push("common");

            builder.comment("Core Settings").push("core");
            difficultyMode = builder
                    .comment("Difficulty mode: FORGE (hardcore), CASUAL (relaxed), MIXED (hybrid).",
                             "FORGE: High spawn rates, powerful elites.",
                             "CASUAL: Low spawn rates, weaker elites.",
                             "MIXED: Rare powerful elites + common weaker ones.")
                    .defineEnum("difficultyMode", DifficultyMode.FORGE);

            enableEliteMobs = builder
                    .comment("Enable or disable elite mob spawning entirely.")
                    .define("enableEliteMobs", true);

            globalSpawnChance = builder
                    .comment("Global chance for a mob to become elite (0.0 to 1.0).",
                             "This is the base chance before difficulty mode modifiers.")
                    .defineInRange("globalSpawnChance", 0.12, 0.0, 1.0);

            maxEliteLevel = builder
                    .comment("Maximum elite level in FORGE/MIXED mode.",
                             "Default 1500. Higher = harder elites. Range 100-9999.")
                    .defineInRange("maxEliteLevel", 1500, 100, 9999);
            maxCasualLevel = builder
                    .comment("Maximum elite level in CASUAL mode.",
                             "Default 150. Range 10-9999.")
                    .defineInRange("maxCasualLevel", 150, 10, 9999);

            maxAbilitiesPerElite = builder
                    .comment("Maximum number of abilities an elite mob can have.",
                             "Even if budget allows more, this is the hard cap.")
                    .defineInRange("maxAbilitiesPerElite", 5, 1, 10);
            builder.pop();

            builder.comment("Feature Toggles").push("features");
            enableChunkHeat = builder
                    .comment("Enable the chunk heat system.",
                             "Chunk heat increases when elites are killed, affecting spawn rates and tempered material quality.")
                    .define("enableChunkHeat", true);

            enablePlayerExperience = builder
                    .comment("Enable the player experience system.",
                             "Player experience grows as they fight elites, unlocking forging bonuses.")
                    .define("enablePlayerExperience", true);

            enableQualitySystem = builder
                    .comment("Enable the quality tier system for elite mobs and items.",
                             "Quality tiers: Common, Uncommon, Rare, Epic, Legendary.")
                    .define("enableQualitySystem", true);

            // enableSetBonuses / enableTemperedMaterials were removed in v0.2.0 phase 1
            // along with the forging/tempering system; their builder entries are gone too.
            builder.pop();

            builder.comment("Mode-Specific Spawn Chances").push("spawn_chances");
            forgeModeSpawnChance = builder
                    .comment("Elite spawn chance in FORGE difficulty mode (0.0 to 1.0).",
                             "Higher values mean more elites spawn.")
                    .defineInRange("forgeModeSpawnChance", 0.20, 0.0, 1.0);

            casualModeSpawnChance = builder
                    .comment("Elite spawn chance in CASUAL difficulty mode (0.0 to 1.0).",
                             "Lower values mean fewer elites spawn.")
                    .defineInRange("casualModeSpawnChance", 0.08, 0.0, 1.0);

            mixedModeForgeChance = builder
                    .comment("Chance for FORGE-tier elites in MIXED mode (0.0 to 1.0).",
                             "These are the rare, powerful elites.")
                    .defineInRange("mixedModeForgeChance", 0.15, 0.0, 1.0);

            mixedModeCasualChance = builder
                    .comment("Chance for CASUAL-tier elites in MIXED mode (0.0 to 1.0).",
                             "These are the common, weaker elites.")
                    .defineInRange("mixedModeCasualChance", 0.05, 0.0, 1.0);
            builder.pop();

            builder.pop();
        }
    }

    // ========================================================================
    // Server Configuration
    // ========================================================================
    public static class Server {

        // --- Ability Budget Settings ---
        public final ForgeConfigSpec.IntValue maxAbilityLevel;
        public final ForgeConfigSpec.DoubleValue attackBudgetBase;
        public final ForgeConfigSpec.DoubleValue defenseBudgetBase;
        public final ForgeConfigSpec.DoubleValue controlBudgetBase;
        public final ForgeConfigSpec.DoubleValue legendaryBudgetBase;
        public final ForgeConfigSpec.DoubleValue budgetPerLevel;

        // --- Chunk Heat Settings ---
        public final ForgeConfigSpec.DoubleValue chunkHeatDecayRate;
        public final ForgeConfigSpec.DoubleValue chunkHeatMax;
        public final ForgeConfigSpec.DoubleValue chunkHeatGainOnEliteKill;
        public final ForgeConfigSpec.DoubleValue chunkHeatGainOnEliteSpawn;

        // --- Player Experience Settings ---
        public final ForgeConfigSpec.DoubleValue playerExperienceDecayRate;
        public final ForgeConfigSpec.DoubleValue playerExperienceMax;
        public final ForgeConfigSpec.DoubleValue playerExperienceGainOnEliteKill;

        // --- Anti-Farm Settings ---
        public final ForgeConfigSpec.BooleanValue enableAntiFarm;
        public final ForgeConfigSpec.IntValue antiFarmRadius;
        public final ForgeConfigSpec.IntValue antiFarmMaxKillsPerHour;

        // --- Blacklist Settings ---
        @SuppressWarnings("rawtypes")
        public final ForgeConfigSpec.ConfigValue dimensionBlacklist;
        @SuppressWarnings("rawtypes")
        public final ForgeConfigSpec.ConfigValue entityBlacklist;

        // --- Entity Filtering ---
        public final ForgeConfigSpec.BooleanValue hostileOnly;
        public final ForgeConfigSpec.BooleanValue includeWaterMobs;
        public final ForgeConfigSpec.BooleanValue includeAmbientMobs;

        // --- Loot Balance (for pack authors) ---
        public final ForgeConfigSpec.DoubleValue creatorFragmentDropChance;
        public final ForgeConfigSpec.DoubleValue rerollScrollDropChance;
        public final ForgeConfigSpec.DoubleValue quenchStoneDropChance;
        public final ForgeConfigSpec.DoubleValue lootMultiplier;

        // --- Particle Performance ---
        public final ForgeConfigSpec.IntValue maxAmbientParticles;
        public final ForgeConfigSpec.BooleanValue enableChainLinks;
        public final ForgeConfigSpec.BooleanValue enableCreatorAura;
        public final ForgeConfigSpec.BooleanValue enableSpawnAnnouncement;

        // --- Summon Leash (purple chain + pull-back) ---
        public final ForgeConfigSpec.BooleanValue enableSummonLeash;
        public final ForgeConfigSpec.IntValue summonLeashRange;
        public final ForgeConfigSpec.DoubleValue summonLeashPullSpeed;

        // --- Difficulty Formula Tuning (personalization: shape the power curve) ---
        public final ForgeConfigSpec.DoubleValue levelDimensionWeight;
        public final ForgeConfigSpec.DoubleValue levelHeatWeight;
        public final ForgeConfigSpec.DoubleValue levelExperienceWeight;
        public final ForgeConfigSpec.DoubleValue levelVariancePercent;
        public final ForgeConfigSpec.DoubleValue eliteHealthPerLevel;
        public final ForgeConfigSpec.DoubleValue eliteDamagePerLevel;
        public final ForgeConfigSpec.DoubleValue abilityCooldownScale;
        public final ForgeConfigSpec.DoubleValue abilityDamageScale;

        // --- Per-Entity-Type Overrides (personalization: tune elites per mob type) ---
        @SuppressWarnings("rawtypes")
        public final ForgeConfigSpec.ConfigValue entityOverrides;

        // --- v0.6.5 Accessory & Gameplay Tuning ---
        public final ForgeConfigSpec.DoubleValue accessoryBonusMultiplier;
        public final ForgeConfigSpec.DoubleValue accessoryUpgradeCostMultiplier;
        public final ForgeConfigSpec.DoubleValue eliteAggroRange;
        public final ForgeConfigSpec.DoubleValue fusionCatalystDropChance;
        // --- v0.7.0 Ability & Tool Tuning ---
        public final ForgeConfigSpec.DoubleValue corrosionStackingMaxDamage;
        public final ForgeConfigSpec.DoubleValue shieldOverloadThreshold;
        public final ForgeConfigSpec.DoubleValue stormChainJumpRange;
        public final ForgeConfigSpec.IntValue forgeHammerMaxUses;
        public final ForgeConfigSpec.DoubleValue lootCompassRange;
        // --- v0.7.5 Tuning ---
        public final ForgeConfigSpec.DoubleValue dismantleReturnRate;
        public final ForgeConfigSpec.IntValue weaponEnhancementMaxLevel;
        public final ForgeConfigSpec.DoubleValue eternalEmberDropChance;
        public final ForgeConfigSpec.IntValue particleReductionMode;
        public final ForgeConfigSpec.IntValue eliteDespawnDistance;
        // --- v0.8.0 ---
        public final ForgeConfigSpec.IntValue accessorySocketMax;
        public final ForgeConfigSpec.DoubleValue socketBonusMultiplier;
        public final ForgeConfigSpec.IntValue refinementMaterialCount;
        // --- v0.8.5 ---
        public final ForgeConfigSpec.BooleanValue enableAccessoryAwakening;
        public final ForgeConfigSpec.BooleanValue enableEquipmentBinding;
        public final ForgeConfigSpec.IntValue legendaryMinLevel;
        public final ForgeConfigSpec.DoubleValue nullShardDropChance;
        // --- v0.9.0 ---
        public final ForgeConfigSpec.BooleanValue enableReforge;
        public final ForgeConfigSpec.BooleanValue enableTransmute;
        public final ForgeConfigSpec.IntValue reforgeHammerDurability;
        public final ForgeConfigSpec.DoubleValue twilightCoreDropChance;

        // --- Elite Equipment System ---
        public final ForgeConfigSpec.BooleanValue enableAutoEquipment;
        public final ForgeConfigSpec.BooleanValue enableAutoEnchantment;
        public final ForgeConfigSpec.DoubleValue equipmentDropChance;
        public final ForgeConfigSpec.DoubleValue moddedLootChance;

        // --- Biome & Structure Heat Modifiers ---
        public final ForgeConfigSpec.DoubleValue biomeHeatMultiplier;
        public final ForgeConfigSpec.DoubleValue structureHeatMultiplier;
        @SuppressWarnings("rawtypes")
        public final ForgeConfigSpec.ConfigValue highHeatBiomes;
        @SuppressWarnings("rawtypes")
        public final ForgeConfigSpec.ConfigValue lowHeatBiomes;

        // --- Kill Messages ---
        public final ForgeConfigSpec.BooleanValue enableKillMessage;

        // --- Third-Party Mod Compatibility ---
        public final ForgeConfigSpec.BooleanValue compatL2Hostility;
        public final ForgeConfigSpec.BooleanValue compatChampions;
        public final ForgeConfigSpec.BooleanValue compatInfernalMobs;
        @SuppressWarnings("rawtypes")
        public final ForgeConfigSpec.ConfigValue compatBlacklistedMods;

        // --- Mutual Exclusion ---
        public final ForgeConfigSpec.BooleanValue enableMutualExclusion;

        // --- Creator-Tier Settings ---
        public final ForgeConfigSpec.BooleanValue enableCreatorTier;
        public final ForgeConfigSpec.IntValue maxCreatorEntities;
        public final ForgeConfigSpec.IntValue minKillsForCreator;
        public final ForgeConfigSpec.DoubleValue minChunkHeatForCreator;
        public final ForgeConfigSpec.DoubleValue creatorSpawnChanceForge;
        public final ForgeConfigSpec.DoubleValue creatorSpawnChanceMixed;
        public final ForgeConfigSpec.BooleanValue enableAwakening;
        public final ForgeConfigSpec.IntValue awakeningCheckInterval;
        public final ForgeConfigSpec.DoubleValue awakeningChance;
        public final ForgeConfigSpec.BooleanValue enableRevengeSystem;
        public final ForgeConfigSpec.IntValue revengeKillThreshold;
        public final ForgeConfigSpec.IntValue revengeTimeWindow;
        public final ForgeConfigSpec.BooleanValue enableDynamicStrengthening;

        // --- Creator-Tier Gameplay Settings ---
        public final ForgeConfigSpec.DoubleValue dominionEliteSpawnBonus;
        public final ForgeConfigSpec.IntValue bestowalRevertTicks;
        public final ForgeConfigSpec.IntValue assimilateInvulnTicks;
        public final ForgeConfigSpec.IntValue scorchedEarthMaxTicks;
        public final ForgeConfigSpec.BooleanValue enableSynergyBonus;
        public final ForgeConfigSpec.DoubleValue maxSynergyBonus;

        // C2: Configurable thresholds (previously hardcoded)
        public final ForgeConfigSpec.IntValue awakeningMinAliveTicks;
        public final ForgeConfigSpec.DoubleValue awakeningMinHeat;
        public final ForgeConfigSpec.IntValue awakeningMinPlayerKills;
        public final ForgeConfigSpec.IntValue casualDespawnTicks;

        public Server(ForgeConfigSpec.Builder builder) {
            builder.comment("EliteForge Server Configuration", "Balance settings controlled by server operators.")
                   .push("server");

            builder.comment("Ability Budget Settings", "Controls how many and how powerful abilities elites get.")
                   .push("ability_budget");
            maxAbilityLevel = builder
                    .comment("Maximum level for any individual ability (1-10).")
                    .defineInRange("maxAbilityLevel", 5, 1, 10);

            attackBudgetBase = builder
                    .comment("Base budget points for attack abilities.",
                             "Higher values allow more attack abilities per elite.")
                    .defineInRange("attackBudgetBase", 3.0, 0.0, 20.0);

            defenseBudgetBase = builder
                    .comment("Base budget points for defense abilities.",
                             "Higher values allow more defense abilities per elite.")
                    .defineInRange("defenseBudgetBase", 3.0, 0.0, 20.0);

            controlBudgetBase = builder
                    .comment("Base budget points for control abilities.",
                             "Higher values allow more control abilities per elite.")
                    .defineInRange("controlBudgetBase", 2.5, 0.0, 20.0);

            legendaryBudgetBase = builder
                    .comment("Base budget points for legendary abilities.",
                             "Higher values allow more legendary abilities per elite.",
                             "Legendary abilities are very powerful and should be limited.")
                    .defineInRange("legendaryBudgetBase", 1.5, 0.0, 20.0);

            budgetPerLevel = builder
                    .comment("Additional budget points gained per elite level.",
                             "Higher-level elites get more budget for abilities.")
                    .defineInRange("budgetPerLevel", 0.5, 0.0, 5.0);
            builder.pop();

            builder.comment("Chunk Heat Settings", "Controls the chunk heat system.").push("chunk_heat");
            chunkHeatDecayRate = builder
                    .comment("Rate at which chunk heat decays per tick.",
                             "Lower values mean heat persists longer.")
                    .defineInRange("chunkHeatDecayRate", 0.01, 0.0, 1.0);

            chunkHeatMax = builder
                    .comment("Maximum chunk heat value.",
                             "At max heat, tempered material quality is at its best.")
                    .defineInRange("chunkHeatMax", 100.0, 10.0, 1000.0);

            chunkHeatGainOnEliteKill = builder
                    .comment("Heat gained in a chunk when an elite is killed there.",
                             "Encourages players to farm in concentrated areas.")
                    .defineInRange("chunkHeatGainOnEliteKill", 2.0, 0.0, 50.0);

            chunkHeatGainOnEliteSpawn = builder
                    .comment("Heat gained in a chunk when an elite mob spawns there.",
                             "Lower than kill gain since spawns are more frequent.")
                    .defineInRange("chunkHeatGainOnEliteSpawn", 1.0, 0.0, 50.0);
            builder.pop();

            builder.comment("Player Experience Settings", "Controls the player experience system.").push("player_experience");
            playerExperienceDecayRate = builder
                    .comment("Rate at which player experience decays per tick.",
                             "Experience represents recent combat activity.")
                    .defineInRange("playerExperienceDecayRate", 0.005, 0.0, 1.0);

            playerExperienceMax = builder
                    .comment("Maximum player experience value.",
                             "At max experience, forging bonuses are at their best.")
                    .defineInRange("playerExperienceMax", 100.0, 10.0, 1000.0);

            playerExperienceGainOnEliteKill = builder
                    .comment("Experience gained when a player kills an elite.",
                             "More experience means better forging outcomes.")
                    .defineInRange("playerExperienceGainOnEliteKill", 3.0, 0.0, 50.0);
            builder.pop();

            builder.comment("Anti-Farm Settings", "Prevents players from exploiting spawn mechanics.").push("anti_farm");
            enableAntiFarm = builder
                    .comment("Enable the anti-farm system.",
                             "Limits elite kills in a small area to prevent exploit farming.")
                    .define("enableAntiFarm", true);

            antiFarmRadius = builder
                    .comment("Radius (in blocks) for the anti-farm check.",
                             "Kills within this radius count toward the hourly limit.")
                    .defineInRange("antiFarmRadius", 64, 16, 256);

            antiFarmMaxKillsPerHour = builder
                    .comment("Maximum elite kills allowed per hour within the anti-farm radius.",
                             "Once exceeded, elite spawns are suppressed in that area.")
                    .defineInRange("antiFarmMaxKillsPerHour", 20, 1, 200);
            builder.pop();

            builder.comment("Blacklist Settings", "Controls which dimensions and entities are excluded.").push("blacklist");
            // Use raw types to bypass Forge's generic inference issue with defineList
            // The defineList method returns ConfigValue<List<? extends T>> which is
            // incompatible with ConfigValue<List<String>> due to Java's generic variance.
            // Assigning to raw ConfigValue (no generic param) bypasses this entirely.
            ForgeConfigSpec.ConfigValue dimensionBlacklistRaw = builder.defineList(
                    "dimensionBlacklist", java.util.Collections.emptyList(),
                    entry -> entry instanceof String);
            ForgeConfigSpec.ConfigValue entityBlacklistRaw = builder.defineList(
                    "entityBlacklist", java.util.Collections.emptyList(),
                    entry -> entry instanceof String);
            dimensionBlacklist = dimensionBlacklistRaw;
            entityBlacklist = entityBlacklistRaw;
            builder.pop();

            builder.comment("Entity Filtering", "Controls which entity categories can become elites.").push("entity_filter");
            hostileOnly = builder
                    .comment("When true, only hostile mobs (Monster classification) can become elites.",
                             "This includes modded mobs that extend Monster or are in the MONSTER MobCategory.",
                             "Set to false to allow passive mobs (animals, villagers, etc.) to also become elites.",
                             "Note: Players are always excluded regardless of this setting.")
                    .define("hostileOnly", true);

            includeWaterMobs = builder
                    .comment("When true, water hostile mobs (Drowned, Guardians, etc.) can also become elites.",
                             "Only relevant when hostileOnly=true. Water mobs are sometimes classified separately.",
                             "Default true: includes Drowned, Guardian, Elder Guardian, etc.")
                    .define("includeWaterMobs", true);

            includeAmbientMobs = builder
                    .comment("When true, ambient hostile mobs (Phantoms, etc.) can also become elites.",
                             "Only relevant when hostileOnly=true. Ambient mobs like Bats are excluded.",
                             "Default true: includes Phantom, etc. (Bats remain excluded as passive).")
                    .define("includeAmbientMobs", true);
            builder.pop();

            builder.comment("Loot Balance", "Fine-tune drop rates for pack authors.").push("loot_balance");
            creatorFragmentDropChance = builder
                    .comment("Chance for creator-tier elites to drop Creator Fragment (0-1).",
                             "Default 0.15 (15%).")
                    .defineInRange("creatorFragmentDropChance", 0.15, 0.0, 1.0);
            rerollScrollDropChance = builder
                    .comment("Base chance for reroll scroll drop (0-1).",
                             "Actual chance = this + level * 0.01. Default 0.03 (3%).")
                    .defineInRange("rerollScrollDropChance", 0.03, 0.0, 1.0);
            quenchStoneDropChance = builder
                    .comment("Base chance for quench stone drop (0-1).",
                             "Actual chance = this + level * 0.005. Default 0.02 (2%).")
                    .defineInRange("quenchStoneDropChance", 0.02, 0.0, 1.0);
            lootMultiplier = builder
                    .comment("Global multiplier applied to all elite loot drops (1.0 = normal).",
                             "Set to 2.0 for double loot, 0.5 for half. Default 1.0.")
                    .defineInRange("lootMultiplier", 1.0, 0.0, 10.0);
            builder.pop();

            builder.comment("Particle Performance", "Control visual effects for performance.").push("particle_performance");
            maxAmbientParticles = builder
                    .comment("Maximum ambient particles per elite per tick (0 = disable ambient).",
                             "Default 1. Lower for better performance.")
                    .defineInRange("maxAmbientParticles", 1, 0, 10);
            enableChainLinks = builder
                    .comment("Enable chain link visual between creators and minions.",
                             "Disable for performance on low-end machines.")
                    .define("enableChainLinks", true);
            enableCreatorAura = builder
                    .comment("Enable pulsing aura around creator-tier elites.",
                             "This is a server-side flag; client showCreatorAura also applies.")
                    .define("enableCreatorAura", true);
            enableSpawnAnnouncement = builder
                    .comment("Enable chat announcements for EPIC+ elite spawns.",
                             "Disable for quieter gameplay.")
                    .define("enableSpawnAnnouncement", false);
            builder.pop();

            builder.comment("Summon Leash", "Control the purple chain visual and pull-back behavior for summons (Necromancy undead, Clone clones).",
                    "The chain renders between a summon and its owner; the leash pulls the summon back when it wanders too far.").push("summon_leash");
            enableSummonLeash = builder
                    .comment("Enable leash pull-back for summons.",
                             "When true, summons that wander beyond summonLeashRange are navigated back to their owner,",
                             "and teleported back if they exceed 1.6x that range. Default true.")
                    .define("enableSummonLeash", true);
            summonLeashRange = builder
                    .comment("Maximum distance (in blocks) a summon can wander from its owner",
                             "before being pulled back. Default 24. Range 8-64.")
                    .defineInRange("summonLeashRange", 24, 8, 64);
            summonLeashPullSpeed = builder
                    .comment("Navigation speed multiplier when pulling a summon back to its owner.",
                             "Default 1.4 (faster than normal walking). Range 0.5-3.0.")
                    .defineInRange("summonLeashPullSpeed", 1.4, 0.5, 3.0);
            builder.pop();

            builder.comment("Elite Equipment System", "Auto-equip elites with scaled gear.").push("equipment");
            enableAutoEquipment = builder
                    .comment("When true, elites are automatically equipped with weapons and armor",
                             "based on their quality tier (leather→netherite).",
                             "Default true.")
                    .define("enableAutoEquipment", true);
            enableAutoEnchantment = builder
                    .comment("When true, elite equipment is automatically enchanted",
                             "based on difficulty level (L1=1 enchant, L5=3 enchants).",
                             "Auto-discovers modded enchantments. Default true.")
                    .define("enableAutoEnchantment", true);
            equipmentDropChance = builder
                    .comment("Chance for elite equipment to drop on death (0-1).",
                             "Default 0.0 (no drop — equipment is visual only).",
                             "Set to 0.1 for 10% drop chance.")
                    .defineInRange("equipmentDropChance", 0.0, 0.0, 1.0);
            moddedLootChance = builder
                    .comment("Base chance for modded item drops from elites (0-1).",
                             "Scales with quality tier. Default 0.05 (5% base).",
                             "Set to 0 for no modded loot drops.")
                    .defineInRange("moddedLootChance", 0.05, 0.0, 1.0);
            builder.pop();

            builder.comment("Biome & Structure Heat", "Customize heat by biome/structure.").push("biome_heat");
            biomeHeatMultiplier = builder
                    .comment("Global multiplier for biome heat gain (1.0 = normal).",
                             "Set to 2.0 for double heat in all biomes.")
                    .defineInRange("biomeHeatMultiplier", 1.0, 0.0, 10.0);
            structureHeatMultiplier = builder
                    .comment("Heat bonus applied when elites spawn near structures.",
                             "Default 1.5 — structures are 50% hotter.")
                    .defineInRange("structureHeatMultiplier", 1.5, 0.0, 10.0);
            highHeatBiomes = builder
                    .comment("Biomes that generate extra heat (higher elite spawn rate).",
                             "List of biome IDs. Example: [\"minecraft:desert\", \"minecraft:nether_wastes\"]")
                    .defineList("highHeatBiomes", java.util.Collections.emptyList(),
                            entry -> entry instanceof String);
            lowHeatBiomes = builder
                    .comment("Biomes with reduced heat (lower elite spawn rate).",
                             "List of biome IDs. Example: [\"minecraft:plains\", \"minecraft:forest\"]")
                    .defineList("lowHeatBiomes", java.util.Collections.emptyList(),
                            entry -> entry instanceof String);
            builder.pop();

            builder.comment("Kill Messages", "Control elite kill notifications.").push("kill_messages");
            enableKillMessage = builder
                    .comment("Show kill message in action bar when a player kills an elite.",
                             "Includes quality, level, and entity name.",
                             "Default true. Set false to disable all kill messages.")
                    .define("enableKillMessage", false);
            builder.pop();

            builder.comment("Third-Party Mod Compatibility",
                    "Control interaction with other elite mob mods.",
                    "When a mod is enabled, EliteForge will NOT convert entities that are",
                    "already marked as elite by that mod, preventing double-elite bugs.").push("compat");
            compatL2Hostility = builder
                    .comment("Enable compatibility with L2Hostility (LightLand Hostility).",
                             "When true, EliteForge skips entities already processed by L2Hostility.",
                             "Default true. Disable if you want both mods to stack on the same entity.")
                    .define("compatL2Hostility", true);
            compatChampions = builder
                    .comment("Enable compatibility with Champions mod.",
                             "When true, EliteForge skips entities already marked as Champions.",
                             "Default true.")
                    .define("compatChampions", true);
            compatInfernalMobs = builder
                    .comment("Enable compatibility with Infernal Mobs mod.",
                             "When true, EliteForge skips entities already marked as Infernal.",
                             "Default true.")
                    .define("compatInfernalMobs", true);
            compatBlacklistedMods = builder
                    .comment("List of mod IDs whose entities should NEVER become EliteForge elites.",
                             "Useful for mods that have their own elite systems.",
                             "Example: [\"l2hostility\", \"champions\", \"infernalmobs\"]")
                    .defineList("compatBlacklistedMods", java.util.Collections.emptyList(),
                            entry -> entry instanceof String);
            builder.pop();

            builder.comment("Mutual Exclusion Settings").push("mutual_exclusion");
            enableMutualExclusion = builder
                    .comment("Enable mutual exclusion for abilities.",
                             "Some abilities are mutually exclusive and cannot appear together.",
                             "Example: 'phase' and 'armored' are mutually exclusive.",
                             "This prevents creating trivially easy or impossible elites.")
                    .define("enableMutualExclusion", true);
            builder.pop();

            builder.comment("Creator-Tier Settings", "Controls the rarest and most powerful elite tier.")
                   .push("creator");

            enableCreatorTier = builder
                .comment("Enable creator-tier elite mobs.",
                         "Creator-tier mobs are the most powerful entities in the mod.",
                         "They have unique abilities that affect the battlefield around them.")
                .define("enableCreatorTier", true);

            maxCreatorEntities = builder
                .comment("Maximum number of creator-tier entities that can exist simultaneously.",
                         "Prevents the world from being overrun by creator-tier mobs.")
                .defineInRange("maxCreatorEntities", 2, 1, 10);

            minKillsForCreator = builder
                .comment("Minimum number of elite kills required before creator-tier mobs can spawn.",
                         "This prevents creator-tier mobs from appearing too early.")
                .defineInRange("minKillsForCreator", 50, 0, 500);

            minChunkHeatForCreator = builder
                .comment("Minimum chunk heat required for creator-tier mob generation.",
                         "Higher values mean creators only appear in heavily contested areas.")
                .defineInRange("minChunkHeatForCreator", 75.0, 0.0, 1000.0);

            creatorSpawnChanceForge = builder
                .comment("Base spawn chance for creator-tier mobs in FORGE mode.",
                         "Extremely low by default. 0.005 = 0.5% chance when conditions are met.")
                .defineInRange("creatorSpawnChanceForge", 0.005, 0.0, 1.0);

            creatorSpawnChanceMixed = builder
                .comment("Base spawn chance for creator-tier mobs in MIXED mode.",
                         "Lower than FORGE mode.")
                .defineInRange("creatorSpawnChanceMixed", 0.002, 0.0, 1.0);

            enableAwakening = builder
                .comment("Allow legendary elites to awaken into creator-tier.",
                         "When enabled, legendary elites that survive long enough may transform.")
                .define("enableAwakening", true);

            awakeningCheckInterval = builder
                .comment("Interval in seconds between awakening checks for legendary elites.",
                         "Lower values mean more frequent checks but more CPU usage.")
                .defineInRange("awakeningCheckInterval", 60, 10, 600);

            awakeningChance = builder
                .comment("Probability of awakening per check interval when all conditions are met.",
                         "0.05 = 5% chance per check.")
                .defineInRange("awakeningChance", 0.05, 0.0, 1.0);

            enableRevengeSystem = builder
                .comment("Enable the revenge system.",
                         "When players kill too many elites in one area, the area fights back.")
                .define("enableRevengeSystem", true); // System enabled but messages off by default

            enableDynamicStrengthening = builder
                .comment("Enable dynamic strengthening system.",
                         "When true, elites gain power over time (time/kill/group/heat-based).",
                         "Disable for static elites that don't grow stronger.")
                .define("enableDynamicStrengthening", true);

            revengeKillThreshold = builder
                .comment("Number of elite kills within the time window to trigger revenge.",
                         "Lower values make revenge easier to trigger.")
                .defineInRange("revengeKillThreshold", 5, 1, 50);

            revengeTimeWindow = builder
                .comment("Time window in seconds for counting kills toward revenge.",
                         "Kills older than this are not counted.")
                .defineInRange("revengeTimeWindow", 60, 10, 600);

            builder.comment("Creator Gameplay Settings", "Fine-tune creator-tier ability parameters.")
                   .push("creator_gameplay");

            dominionEliteSpawnBonus = builder
                .comment("Chance for mobs to become elite inside dominion zones.",
                         "0.20 = 20% chance. This is applied on top of the normal spawn check.",
                         "Only applies when the normal spawn check fails and the mob is inside an active dominion zone.")
                .defineInRange("dominionEliteSpawnBonus", 0.20, 0.0, 1.0);

            bestowalRevertTicks = builder
                .comment("Ticks before bestowed elites revert after their creator dies.",
                         "600 = 30 seconds. After the timer expires, the elite reverts to a normal mob.",
                         "Weakening effects are applied in the last 5 seconds regardless of this value.")
                .defineInRange("bestowalRevertTicks", 600, 60, 6000);

            assimilateInvulnTicks = builder
                .comment("Ticks of invulnerability after assimilation at Level III.",
                         "60 = 3 seconds. The assimilator becomes invulnerable briefly after absorbing an ability.",
                         "Only applies to Level III assimilation.")
                .defineInRange("assimilateInvulnTicks", 60, 0, 600);

            scorchedEarthMaxTicks = builder
                .comment("Maximum lifetime of scorched earth zones in ticks.",
                         "200 = 10 seconds. Zones created by Annihilate (Level III) last this long.",
                         "A safety cap of 3x this value prevents stale zones from server crashes.")
                .defineInRange("scorchedEarthMaxTicks", 200, 20, 6000);

            enableSynergyBonus = builder
                .comment("Enable the synergy damage bonus system.",
                         "When true, elites with two abilities that have a defined synergy (e.g. Fire + SpiritBurn = Inferno)",
                         "deal bonus outgoing damage based on the combined synergy multiplier.",
                         "Set to false to disable all synergy-based damage bonuses.")
                .define("enableSynergyBonus", true);

            maxSynergyBonus = builder
                .comment("Maximum additive synergy damage bonus.",
                         "0.5 = up to +50% damage from synergies. Multiple active synergies accumulate",
                         "additively up to this cap. Lower values make synergies less impactful.",
                         "Range: 0.0 (no bonus) to 2.0 (up to +200% damage).")
                .defineInRange("maxSynergyBonus", 0.5, 0.0, 2.0);

            // C2: Configurable awakening thresholds (previously hardcoded in EliteAwakening)
            awakeningMinAliveTicks = builder
                .comment("Minimum ticks an elite must be alive before it can awaken.",
                         "12000 = 10 minutes (2x the previous hardcoded 6000 = 5 minutes default).",
                         "Higher values delay awakening, giving players more time to defeat",
                         "legendary elites before they transform into creator-tier.")
                .defineInRange("awakeningMinAliveTicks", 6000, 1200, 72000);

            awakeningMinHeat = builder
                .comment("Minimum chunk heat required for an elite to awaken.",
                         "80 = the previous hardcoded default. Higher values make awakening rarer.")
                .defineInRange("awakeningMinHeat", 80.0, 0.0, 1000.0);

            awakeningMinPlayerKills = builder
                .comment("Minimum number of players an elite must have killed to awaken.",
                         "2 = the previous hardcoded default. Higher values require more player deaths",
                         "before an elite can transform, making awakening rarer on PvP-heavy servers.")
                .defineInRange("awakeningMinPlayerKills", 2, 0, 20);

            casualDespawnTicks = builder
                .comment("Ticks before an unengaged CASUAL-mode elite despawns.",
                         "6000 = 5 minutes (the previous hardcoded default).",
                         "Set to 0 to disable CASUAL despawn entirely.",
                         "Lower values keep the world cleaner but may frustrate players who",
                         "lose track of CASUAL elites; higher values are more lenient.")
                .defineInRange("casualDespawnTicks", 6000, 0, 60000);

            builder.pop();

            builder.comment("Difficulty Formula Tuning",
                    "Shape the elite power curve without touching code.",
                    "The level formula is: (dimBase * levelDimensionWeight) + (heat * levelHeatWeight) + (exp * levelExperienceWeight),",
                    "then ±levelVariancePercent% variance, clamped to maxEliteLevel/maxCasualLevel.",
                    "Defaults reproduce the shipped curve (10/3/2, 10% variance).")
                    .push("difficulty_formula");
            levelDimensionWeight = builder
                    .comment("Weight of the dimension base value in the level formula. Default 10.0.")
                    .defineInRange("levelDimensionWeight", 10.0, 0.0, 100.0);
            levelHeatWeight = builder
                    .comment("Weight of chunk heat in the level formula. Default 3.0.")
                    .defineInRange("levelHeatWeight", 3.0, 0.0, 50.0);
            levelExperienceWeight = builder
                    .comment("Weight of nearby-player experience in the level formula. Default 2.0.")
                    .defineInRange("levelExperienceWeight", 2.0, 0.0, 50.0);
            levelVariancePercent = builder
                    .comment("Random variance applied to the calculated level, as a fraction (0.1 = ±10%). Default 0.1.")
                    .defineInRange("levelVariancePercent", 0.1, 0.0, 1.0);
            eliteHealthPerLevel = builder
                    .comment("Health bonus per elite level, as a fraction of max health added per level (0.03 = +3% per level).",
                             "Applied cumulatively. Default 0.03.")
                    .defineInRange("eliteHealthPerLevel", 0.03, 0.0, 1.0);
            eliteDamagePerLevel = builder
                    .comment("Damage bonus per elite level, as a fraction (0.05 = +5% per level). Default 0.05.")
                    .defineInRange("eliteDamagePerLevel", 0.05, 0.0, 1.0);
            abilityCooldownScale = builder
                    .comment("Global multiplier applied to ALL ability cooldowns (1.0 = normal, 0.5 = half cooldown = more active, 2.0 = double = less active). Default 1.0.")
                    .defineInRange("abilityCooldownScale", 1.0, 0.1, 5.0);
            abilityDamageScale = builder
                    .comment("Global multiplier applied to ability-dealt damage (1.0 = normal, 2.0 = double). Default 1.0.",
                             "Does not affect attribute modifiers — only direct ability damage calls.")
                    .defineInRange("abilityDamageScale", 1.0, 0.0, 10.0);
            builder.pop();

            builder.comment("Per-Entity-Type Overrides",
                    "Customize elite behavior for specific mob types without writing a datapack.",
                    "Format: \"entity_id|disabled|forcedQuality|forcedLevel|healthMult|damageMult\"",
                    "  entity_id     — vanilla or modded, e.g. minecraft:zombie",
                    "  disabled      — true/false: if true, this mob NEVER becomes elite",
                    "  forcedQuality — NORMAL/GOOD/FINE/EPIC/LEGENDARY/MYTHIC or * for default",
                    "  forcedLevel   — integer 1-9999 or * for default (heat-based)",
                    "  healthMult    — multiplier on the elite's max health (1.0 = normal) or * for default",
                    "  damageMult    — multiplier on the elite's outgoing damage or * for default",
                    "Example: [\"minecraft:creeper|false|EPIC|*|1.5|2.0\", \"minecraft:enderman|true|*|*|*|*\"]",
                    "Entries are applied in order; later entries for the same entity_id override earlier ones.")
                    .push("entity_overrides");
            entityOverrides = builder
                    .comment("List of per-entity override strings (see section comment for format).")
                    .defineList("entityOverrides", java.util.Collections.emptyList(),
                            o -> o instanceof String s && s != null && !s.isBlank());
            builder.pop();

            builder.comment("Accessory & Gameplay Tuning (v0.6.5)",
                    "Fine-tune accessory power, upgrade costs, elite behavior, and rare drops.")
                    .push("gameplay_tuning");
            accessoryBonusMultiplier = builder
                    .comment("Global multiplier on ALL accessory stat bonuses (HP/ATK/DEF).",
                             "1.0 = normal, 2.0 = double, 0.5 = half. Default 1.0.")
                    .defineInRange("accessoryBonusMultiplier", 1.0, 0.0, 10.0);
            accessoryUpgradeCostMultiplier = builder
                    .comment("Multiplier on material costs for upgrading accessories.",
                             "1.0 = normal, 0.5 = half cost, 2.0 = double. Default 1.0.")
                    .defineInRange("accessoryUpgradeCostMultiplier", 1.0, 0.1, 5.0);
            eliteAggroRange = builder
                    .comment("Range (in blocks) at which elites detect and aggro players.",
                             "Default 16. Higher = elites notice you from further away.")
                    .defineInRange("eliteAggroRange", 16.0, 4.0, 64.0);
            fusionCatalystDropChance = builder
                    .comment("Chance (0-1) that an EPIC+ elite drops a Catalyst of Fusion.",
                             "Default 0.02 (2%). Set to 0 to disable fusion catalyst drops.")
                    .defineInRange("fusionCatalystDropChance", 0.02, 0.0, 1.0);
            // v0.7.0 ability + tool tuning
            corrosionStackingMaxDamage = builder
                    .comment("Max bonus damage from Corrosion ability stacking (per hit adds +1, capped).",
                             "Default 10.0. Higher = corrosion gets more lethal in long fights.")
                    .defineInRange("corrosionStackingMaxDamage", 10.0, 0.0, 100.0);
            shieldOverloadThreshold = builder
                    .comment("Health fraction below which Shield ability enters overload (2x shield).",
                             "Default 0.3 (30% HP). Set to 0 to disable overload.")
                    .defineInRange("shieldOverloadThreshold", 0.3, 0.0, 1.0);
            stormChainJumpRange = builder
                    .comment("Range (in blocks) for Storm ability's lightning chain jump.",
                             "Default 8.0. Higher = lightning reaches more distant targets.")
                    .defineInRange("stormChainJumpRange", 8.0, 2.0, 32.0);
            forgeHammerMaxUses = builder
                    .comment("Max durability of the Forge Hammer item (used for accessory fusion).",
                             "Default 64. Higher = more fusions before the hammer breaks.")
                    .defineInRange("forgeHammerMaxUses", 64, 1, 1024);
            lootCompassRange = builder
                    .comment("Scan range (in blocks) for the Loot Compass item.",
                             "Default 64. Higher = finds elite loot drops from further away.")
                    .defineInRange("lootCompassRange", 64.0, 8.0, 256.0);
            // v0.7.5 tuning
            dismantleReturnRate = builder
                    .comment("Multiplier on material returns when dismantling accessories.",
                             "1.0 = normal, 2.0 = double materials back. Default 1.0.")
                    .defineInRange("dismantleReturnRate", 1.0, 0.0, 3.0);
            weaponEnhancementMaxLevel = builder
                    .comment("Maximum weapon enhancement level at the Elite Beacon.",
                             "Default 5. Each level adds +1 attack damage.")
                    .defineInRange("weaponEnhancementMaxLevel", 5, 1, 20);
            eternalEmberDropChance = builder
                    .comment("Chance (0-1) that a MYTHIC elite drops an Eternal Ember.",
                             "Default 0.5 (50%).")
                    .defineInRange("eternalEmberDropChance", 0.5, 0.0, 1.0);
            particleReductionMode = builder
                    .comment("Particle reduction: 0 = off (all particles), 1 = reduce counts (default), 2 = minimal (perf mode).",
                             "Lower values = more particles = prettier but slower. Higher = fewer = faster.")
                    .defineInRange("particleReductionMode", 1, 0, 2);
            eliteDespawnDistance = builder
                    .comment("Distance (in blocks) at which unengaged CASUAL elites despawn.",
                             "Default 128. Lower = elites clean up sooner when players leave.")
                    .defineInRange("eliteDespawnDistance", 128, 16, 512);
            accessorySocketMax = builder
                    .comment("Maximum number of socket gems an accessory can hold.","Default 3.")
                    .defineInRange("accessorySocketMax", 3, 0, 10);
            socketBonusMultiplier = builder
                    .comment("Multiplier on socket gem bonus stats.","1.0 = normal, 2.0 = double. Default 1.0.")
                    .defineInRange("socketBonusMultiplier", 1.0, 0.0, 10.0);
            refinementMaterialCount = builder
                    .comment("Number of lower-tier materials needed to refine into 1 higher.","Default 3.")
                    .defineInRange("refinementMaterialCount", 3, 2, 10);
            enableAccessoryAwakening = builder
                    .comment("Enable the accessory awakening system (MYTHIC → ASCENDED).","Default true.")
                    .define("enableAccessoryAwakening", true);
            enableEquipmentBinding = builder
                    .comment("Enable equipment binding at the Elite Beacon.","Default true.")
                    .define("enableEquipmentBinding", true);
            legendaryMinLevel = builder
                    .comment("Minimum elite level for LEGENDARY+ abilities to appear.","Default 300.")
                    .defineInRange("legendaryMinLevel", 1, 1, 1500);
            nullShardDropChance = builder
                    .comment("Chance (0-1) that a MYTHIC elite drops a Null Shard.","Default 0.3.")
                    .defineInRange("nullShardDropChance", 0.3, 0.0, 1.0);
            enableReforge = builder.comment("Enable accessory reforging at the Elite Beacon.","Default true.").define("enableReforge", true);
            enableTransmute = builder.comment("Enable material transmutation at the Elite Beacon.","Default true.").define("enableTransmute", true);
            reforgeHammerDurability = builder.comment("Durability of the Reforge Hammer.","Default 32.").defineInRange("reforgeHammerDurability", 32, 1, 256);
            twilightCoreDropChance = builder.comment("Chance (0-1) that a LEGENDARY+ elite drops a Twilight Core.","Default 0.1.").defineInRange("twilightCoreDropChance", 0.1, 0.0, 1.0);
            builder.pop();

            builder.pop();

            builder.pop();
        }
    }

    // ========================================================================
    // Client Configuration
    // ========================================================================
    public static class Client {

        // --- Display Settings ---
        public final ForgeConfigSpec.BooleanValue showAbilityIcons;
        public final ForgeConfigSpec.BooleanValue showEliteNamePlate;
        public final ForgeConfigSpec.BooleanValue showAbilityParticles;
        public final ForgeConfigSpec.BooleanValue showCreatorAura;
        public final ForgeConfigSpec.BooleanValue showEliteDeathToast;

        // --- Render Settings ---
        public final ForgeConfigSpec.DoubleValue eliteNamePlateScale;
        public final ForgeConfigSpec.IntValue iconRenderDistance;
        public final ForgeConfigSpec.BooleanValue showHeatOverlay;

        public Client(ForgeConfigSpec.Builder builder) {
            builder.comment("EliteForge Client Configuration", "Visual and rendering settings for the client.")
                   .push("client");

            builder.comment("Display Settings").push("display");
            showAbilityIcons = builder
                    .comment("Show ability icons above elite mobs.",
                             "Icons indicate which abilities the elite possesses.")
                    .define("showAbilityIcons", true);

            showEliteNamePlate = builder
                    .comment("Show elite name plates with quality tier and level.",
                             "Name plates use color coding based on quality tier.")
                    .define("showEliteNamePlate", true);

            showAbilityParticles = builder
                    .comment("Show particle effects for active abilities.",
                             "Some abilities produce ambient particles around the elite.")
                    .define("showAbilityParticles", true);

            showCreatorAura = builder
                    .comment("Show the pulsing red aura and ground ring around creator-tier elites.",
                             "This is a purely cosmetic client-side effect.")
                    .define("showCreatorAura", true);

            showEliteDeathToast = builder
                    .comment("Show a toast notification (advancement-style popup) when you kill an elite.",
                             "Disable if you find the popups distracting.")
                    .define("showEliteDeathToast", true);
            builder.pop();

            builder.comment("Render Settings").push("rendering");
            eliteNamePlateScale = builder
                    .comment("Scale of the elite name plate (1.0 = normal, 2.0 = double).",
                             "Adjust for better visibility at different GUI scales.")
                    .defineInRange("eliteNamePlateScale", 1.5, 0.5, 3.0);

            iconRenderDistance = builder
                    .comment("Maximum distance (in blocks) at which ability icons are rendered.",
                             "Icons beyond this distance are not shown for performance.")
                    .defineInRange("iconRenderDistance", 32, 8, 128);

            showHeatOverlay = builder
                    .comment("Show the chunk heat overlay on the HUD.",
                             "Displays current chunk heat value when enabled.")
                    .define("showHeatOverlay", true);
            builder.pop();

            builder.pop();
        }
    }
}
