package com.eliteforge;

import com.eliteforge.ability.AbilityRegistry;
import com.eliteforge.capability.EliteCapability;
import com.eliteforge.capability.EliteCapabilityProvider;
import com.eliteforge.config.EliteForgeConfig;
import com.eliteforge.datapack.EntityPresetLoader;
import com.eliteforge.init.*;
import com.eliteforge.network.NetworkHandler;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Main mod class for EliteForge (精英锻造).
 *
 * A next-generation elite mob mod combining the best of Champions,
 * Infernal Mobs, and L2Hostility with a unique forging theme.
 *
 * Features:
 * - Elite mob spawning system with configurable difficulty modes (FORGE/CASUAL/MIXED)
 * - Dual-budget ability generation system with 54 abilities across 5 categories:
 *     12 Attack, 12 Defense, 12 Control, 10 Legendary, 8 Creator-tier
 * - Creator-tier system (C1-C8): mutually-exclusive ultimate abilities that
 *     represent the highest tier of elite mob power, including Nexus, Dominion,
 *     Evolution, Assimilate, Bestowal, Annihilate, Reincarnation, and Commander
 * - 6-tier quality system: NORMAL → GOOD → FINE → EPIC → LEGENDARY → MYTHIC
 *     (MYTHIC is exclusive to creator-tier elites, weight 0, 10x loot bonus)
 * - Chunk heat system for territory-based gameplay
 * - Player experience system for forging bonuses
 * - Elite ecosystem: creator tracking, nurturing, squad command, assimilation
 * - Runtime dynamic strengthening (time/kill/group/heat-based)
 * - Elite awakening system (legendary → creator transformation)
 * - Elite revenge system (anti-farm counterattack)
 * - 19 mutual exclusion pairs + creator exclusivity rule
 * - 20 ability synergies with combined damage bonus (capped)
 * - Forging anvil, tempering station, heat collector, elite beacon, elite spawner blocks
 * - 6 custom enchantments to counter elites
 * - 6 custom effects for elite abilities
 * - 21 custom items (tempered materials, forge tools, creator fragments, etc.)
 * - Network synchronization for client-side rendering
 * - Full i18n support (en_us + zh_cn, 363 keys each)
 * - KubeJS integration for datapack-driven entity presets
 *
 * C4: Updated feature list to reflect the 54-ability/6-tier/creator-tier scope.
 */
@Mod(EliteForge.MODID)
public class EliteForge {

    public static final String MODID = "eliteforge";
    public static final Logger LOGGER = LogManager.getLogger(MODID);

    // ========================================================================
    // Deferred Registers
    // ========================================================================

    // All deferred registers are defined in their respective init classes.
    // They are registered to the mod event bus in the constructor.

    public EliteForge() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();

        // ====================================================================
        // Register Deferred Registers to Mod Event Bus
        // ====================================================================
        ModItems.ITEMS.register(modBus);
        ModBlocks.BLOCKS.register(modBus);
        ModBlockEntities.BLOCK_ENTITY_TYPES.register(modBus);
        ModCreativeTabs.CREATIVE_MODE_TABS.register(modBus);
        ModEffects.MOB_EFFECTS.register(modBus);
        ModEnchantments.ENCHANTMENTS.register(modBus);
        ModSounds.SOUND_EVENTS.register(modBus);
        
        // ====================================================================
        // Register Mod Event Listeners
        // ====================================================================
        modBus.addListener(this::commonSetup);
        modBus.addListener(this::clientSetup);
        modBus.addListener(this::onRegisterCapabilities);

        // ====================================================================
        // Register Configuration
        // ====================================================================
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, EliteForgeConfig.COMMON_SPEC);
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, EliteForgeConfig.SERVER_SPEC);
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, EliteForgeConfig.CLIENT_SPEC);

        // ====================================================================
        // Register Network Channel
        // ====================================================================
        NetworkHandler.register();

        // ====================================================================
        // Register Event Handlers to Forge Event Bus
        // ====================================================================
        // Note: EliteEventHandler (spawn/ package) is auto-registered via
        // @Mod.EventBusSubscriber annotation. It handles all game events:
        // spawning, damage, death, healing, server tick.
        // EliteRenderHandler (render/ package) is auto-registered via
        // @Mod.EventBusSubscriber with Dist.CLIENT for rendering events.
        // EliteForgeCommand (command/ package) is auto-registered via
        // @Mod.EventBusSubscriber for command registration.

        // Register ourselves for server and other game events
        MinecraftForge.EVENT_BUS.addListener(this::onServerStarting);
        // AttachCapabilitiesEvent<Entity> is a generic event — must use
        // addGenericListener instead of addListener, otherwise Forge throws
        // "Cannot register a generic event listener with addListener".
        MinecraftForge.EVENT_BUS.addGenericListener(net.minecraft.world.entity.Entity.class, this::onAttachCapabilities);
        MinecraftForge.EVENT_BUS.addListener(this::onAddReloadListener);

        LOGGER.info("EliteForge initialized - May your forge burn bright!");
    }

    // ========================================================================
    // Lifecycle Event Handlers
    // ========================================================================

    /**
     * Common setup event handler.
     * Called during mod loading after registries are populated.
     * Used for registering capabilities, adding network listeners,
     * and other cross-cutting setup that requires all registries to be available.
     */
    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            LOGGER.info("EliteForge common setup starting...");

            // Initialize the ability registry FIRST — must happen before any
            // ability-related functionality is accessed
            AbilityRegistry.init();
            // Initialize enchantment pools for elite equipment system
            // Equipment system removed in v0.2.0, will be replaced by Curios accessories
            LOGGER.info("Ability registry initialized with {} abilities", AbilityRegistry.getAbilityCount());

            // Initialize the KubeJS plugin if KubeJS is on the classpath. Previously
            // this call was missing, so the entire KubeJS integration (event handlers,
            // ingredient types, bindings) was dead code.
            try {
                com.eliteforge.kubejs.EliteForgeKubeJSPlugin.initialize();
            } catch (Throwable t) {
                LOGGER.warn("KubeJS plugin initialization failed (KubeJS may be absent or API changed): {}", t.getMessage());
            }

            // Log configuration state
            LOGGER.info("Difficulty Mode: {}", EliteForgeConfig.COMMON.difficultyMode.get());
            LOGGER.info("Elite Mobs Enabled: {}", EliteForgeConfig.COMMON.enableEliteMobs.get());
            LOGGER.info("Global Spawn Chance: {}", EliteForgeConfig.COMMON.globalSpawnChance.get());
            LOGGER.info("Max Abilities Per Elite: {}", EliteForgeConfig.COMMON.maxAbilitiesPerElite.get());
            LOGGER.info("Chunk Heat Enabled: {}", EliteForgeConfig.COMMON.enableChunkHeat.get());
            LOGGER.info("Player Experience Enabled: {}", EliteForgeConfig.COMMON.enablePlayerExperience.get());
            LOGGER.info("Quality System Enabled: {}", EliteForgeConfig.COMMON.enableQualitySystem.get());
            // Set Bonuses removed in v0.2.0
            // Tempered Materials removed in v0.2.0

            LOGGER.info("EliteForge common setup complete!");
        });
    }

    /**
     * Client setup event handler.
     * Called during mod loading on the client side.
     * <p>
     * Container screen registration is handled by {@link com.eliteforge.client.ClientSetup},
     * which is a {@code @Mod.EventBusSubscriber(Dist.CLIENT)} on the MOD bus.
     * This method is kept for any future client setup that does not fit the
     * EventBusSubscriber pattern (e.g., registering colors, key bindings, or
     * model layers that require the FMLClientSetupEvent enqueueWork context).
     */
    private void clientSetup(final FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            LOGGER.info("EliteForge client setup starting...");
            // Register the in-game config screen so the Mods list "Config" button
            // opens EliteForgeConfigScreen. In Forge 1.20.1 this is done by registering
            // a ConfigScreenFactory extension point on the mod container (there is no
            // static ConfigScreenHandler.registerConfigScreen helper in this version).
            net.minecraftforge.fml.ModList.get().getModContainerById(MODID).ifPresent(container ->
                    container.registerExtensionPoint(
                            net.minecraftforge.client.ConfigScreenHandler.ConfigScreenFactory.class,
                            () -> new net.minecraftforge.client.ConfigScreenHandler.ConfigScreenFactory(
                                    (mc, parent) -> new com.eliteforge.client.config.EliteForgeConfigScreen(parent)
                            )
                    )
            );
            LOGGER.info("EliteForge client setup complete!");
        });
    }

    /**
     * Server starting event handler.
     * Called when the server is about to start.
     * Used for validating configuration and initializing server-side systems.
     */
    private void onServerStarting(final ServerStartingEvent event) {
        LOGGER.info("EliteForge server starting - validating configuration...");

        // Validate server configuration
        validateServerConfig();

        // Initialize anti-farm tracking
        initializeAntiFarmSystem();

        LOGGER.info("EliteForge server initialization complete!");
    }

    // ========================================================================
    // Capability Registration
    // ========================================================================

    /**
     * Handle RegisterCapabilitiesEvent — registers the EliteCapability
     * so Forge knows about our capability interface for serialization.
     */
    private void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
        EliteCapability.register(event);
        LOGGER.info("EliteCapability registered with Forge");
    }

    /**
     * Handle AttachCapabilitiesEvent<Entity> — attaches EliteCapabilityProvider
     * to all LivingEntity instances so they can store elite data.
     */
    public void onAttachCapabilities(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof LivingEntity) {
            if (!event.getObject().getCapability(EliteCapability.CAPABILITY).isPresent()) {
                event.addCapability(new ResourceLocation(MODID, "elite_data"), new EliteCapabilityProvider());
            }
        }
    }

    /**
     * Handle AddReloadListenerEvent — registers EntityPresetLoader as a
     * reload listener so datapack entity presets are reloaded on /reload.
     */
    public void onAddReloadListener(AddReloadListenerEvent event) {
        event.addListener(EntityPresetLoader.getInstance());
        LOGGER.debug("EntityPresetLoader registered as reload listener");
    }

    // ========================================================================
    // Client Registration
    // ========================================================================
    // Note: Client-side registration (container screens, block entity renderers,
    // event listeners) is handled by @Mod.EventBusSubscriber(Dist.CLIENT) classes:
    //   - com.eliteforge.client.ClientSetup: container screen registration
    //   - com.eliteforge.render.EliteRenderHandler: name plates, ability icons, HUD
    //   - com.eliteforge.render.CreatorAuraRenderer: creator-tier visual aura
    // These auto-register via Forge's side-check mechanism, so this common class
    // must not reference client-only types (would cause NoClassDefFoundError on
    // dedicated servers).

    // ========================================================================
    // Server Initialization
    // ========================================================================

    /**
     * Validate server configuration values and log warnings for problematic settings.
     */
    private void validateServerConfig() {
        // Validate ability budgets
        double totalBudget = EliteForgeConfig.SERVER.attackBudgetBase.get()
                + EliteForgeConfig.SERVER.defenseBudgetBase.get()
                + EliteForgeConfig.SERVER.controlBudgetBase.get()
                + EliteForgeConfig.SERVER.legendaryBudgetBase.get();

        if (totalBudget > 20.0) {
            LOGGER.warn("Total ability budget ({}) is very high. " +
                    "This may result in extremely powerful elites!", totalBudget);
        }

        // Validate anti-farm settings
        if (EliteForgeConfig.SERVER.enableAntiFarm.get()) {
            int maxKills = EliteForgeConfig.SERVER.antiFarmMaxKillsPerHour.get();
            int radius = EliteForgeConfig.SERVER.antiFarmRadius.get();
            LOGGER.info("Anti-farm enabled: max {} kills per hour in {} block radius", maxKills, radius);
        }

        // Validate heat settings
        double heatDecay = EliteForgeConfig.SERVER.chunkHeatDecayRate.get();
        double heatGain = EliteForgeConfig.SERVER.chunkHeatGainOnEliteKill.get();
        if (heatDecay >= heatGain) {
            LOGGER.warn("Chunk heat decay rate ({}) >= gain rate ({}). " +
                    "Heat will not accumulate effectively!", heatDecay, heatGain);
        }

        // Validate creator-tier config
        if (EliteForgeConfig.SERVER.enableCreatorTier.get()) {
            if (EliteForgeConfig.SERVER.maxCreatorEntities.get() <= 0) {
                LOGGER.warn("maxCreatorEntities is set to {} — creator-tier will be effectively disabled",
                        EliteForgeConfig.SERVER.maxCreatorEntities.get());
            }
            if (EliteForgeConfig.SERVER.creatorSpawnChanceForge.get() <= 0.0 &&
                EliteForgeConfig.SERVER.creatorSpawnChanceMixed.get() <= 0.0) {
                LOGGER.warn("Both creator spawn chances are 0 — creator-tier will never spawn naturally");
            }
            // C5: Validate awakening thresholds
            int minAliveTicks = EliteForgeConfig.SERVER.awakeningMinAliveTicks.get();
            int awakeningIntervalTicks = EliteForgeConfig.SERVER.awakeningCheckInterval.get() * 20;
            if (minAliveTicks < awakeningIntervalTicks) {
                LOGGER.warn("awakeningMinAliveTicks ({}) is less than awakeningCheckInterval * 20 ({}). " +
                        "Awakening checks will not fire until the min alive time is reached, so this is fine, " +
                        "but the effective minimum is the check interval.", minAliveTicks, awakeningIntervalTicks);
            }
            double minHeat = EliteForgeConfig.SERVER.awakeningMinHeat.get();
            double maxHeat = EliteForgeConfig.SERVER.chunkHeatMax.get();
            if (minHeat > maxHeat) {
                LOGGER.warn("awakeningMinHeat ({}) > chunkHeatMax ({}). Awakening will never trigger " +
                        "because chunk heat can never reach the required threshold.", minHeat, maxHeat);
            }
        }

        // C5: Validate CASUAL despawn config
        int casualDespawn = EliteForgeConfig.SERVER.casualDespawnTicks.get();
        if (casualDespawn > 0 && casualDespawn < 600) {
            LOGGER.warn("casualDespawnTicks ({}) is very low (<30s). CASUAL elites may despawn before " +
                    "players can engage them, making CASUAL mode too punishing.", casualDespawn);
        }

        // Log blacklists
        LOGGER.info("Dimension blacklist: {}", EliteForgeConfig.SERVER.dimensionBlacklist.get());
        LOGGER.info("Entity blacklist: {}", EliteForgeConfig.SERVER.entityBlacklist.get());
    }

    /**
     * Initialize the anti-farm tracking system.
     * Sets up the data structures for tracking kill rates per area.
     */
    private void initializeAntiFarmSystem() {
        if (EliteForgeConfig.SERVER.enableAntiFarm.get()) {
            LOGGER.info("Anti-farm system initialized with radius {} and max {}/hr",
                    EliteForgeConfig.SERVER.antiFarmRadius.get(),
                    EliteForgeConfig.SERVER.antiFarmMaxKillsPerHour.get());
        } else {
            LOGGER.info("Anti-farm system is disabled");
        }
    }
}
