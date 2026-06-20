package com.eliteforge.kubejs;

import com.eliteforge.EliteForge;
import com.eliteforge.ability.Ability;
import com.eliteforge.ability.AbilityRegistry;
import com.eliteforge.capability.EliteCapability;
import com.eliteforge.capability.EliteData;
import com.eliteforge.config.DifficultyMode;
import com.eliteforge.quality.QualityTier;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.function.Consumer;

/**
 * KubeJS event integration for EliteForge.
 * Only loaded if KubeJS is present on the classpath.
 *
 * Custom events:
 * - eliteforge.elite_spawn - Fired when elite mob spawns
 * - eliteforge.elite_death - Fired when elite mob dies
 * - eliteforge.ability_apply - Fired when ability is applied
 *
 * Uses conditional loading via Class.forName() to check if KubeJS is loaded.
 */
@Mod.EventBusSubscriber(modid = EliteForge.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class EliteForgeEventsJS {

    private static final Logger LOGGER = LogManager.getLogger();
    private static final String KUBEJS_CLASS = "dev.latvian.mods.kubejs.KubeJS";
    private static Boolean kubeJSPresent = null;

    // Event type constants for KubeJS integration
    public static final String ELITE_SPAWN = "eliteforge.elite_spawn";
    public static final String ELITE_DEATH = "eliteforge.elite_death";
    public static final String ABILITY_APPLY = "eliteforge.ability_apply";

    // New event types for creator-tier
    public static final String CREATOR_SPAWN = "eliteforge.creator.spawn";
    public static final String CREATOR_DEATH = "eliteforge.creator.death";
    public static final String CREATOR_AWAKENING = "eliteforge.creator.awakening";
    public static final String ELITE_NURTURE = "eliteforge.elite.nurture";
    public static final String ELITE_ASSIMILATE = "eliteforge.elite.assimilate";
    public static final String ELITE_BESTOW = "eliteforge.elite.bestow";
    public static final String ELITE_REINCARNATE = "eliteforge.elite.reincarnate";
    public static final String ELITE_EVOLVE = "eliteforge.elite.evolve";
    public static final String ELITE_COMMAND = "eliteforge.elite.command";
    public static final String REVENGE_TRIGGER = "eliteforge.revenge.trigger";

    // Event handler lists (used when KubeJS is not present, provides a simple callback system)
    private static final List<Consumer<EliteSpawnEvent>> SPAWN_HANDLERS = new ArrayList<>();
    private static final List<Consumer<EliteDeathEvent>> DEATH_HANDLERS = new ArrayList<>();
    private static final List<Consumer<AbilityApplyEvent>> ABILITY_APPLY_HANDLERS = new ArrayList<>();
    private static final List<Consumer<CreatorSpawnEvent>> CREATOR_SPAWN_HANDLERS = new ArrayList<>();
    private static final List<Consumer<CreatorDeathEvent>> CREATOR_DEATH_HANDLERS = new ArrayList<>();
    private static final List<Consumer<CreatorAwakeningEvent>> CREATOR_AWAKENING_HANDLERS = new ArrayList<>();
    private static final List<Consumer<RevengeTriggerEvent>> REVENGE_TRIGGER_HANDLERS = new ArrayList<>();

    /**
     * Checks if KubeJS is present on the classpath.
     */
    public static boolean isKubeJSLoaded() {
        if (kubeJSPresent == null) {
            try {
                Class.forName(KUBEJS_CLASS, false, EliteForgeEventsJS.class.getClassLoader());
                kubeJSPresent = true;
                LOGGER.info("EliteForge: KubeJS detected, enabling integration");
            } catch (ClassNotFoundException e) {
                kubeJSPresent = false;
                LOGGER.info("EliteForge: KubeJS not found, integration disabled");
            }
        }
        return kubeJSPresent;
    }

    // ============ Event Data Classes ============

    /**
     * Event data for elite spawn events.
     * Cancelable: yes (prevents spawn)
     * Can modify abilities.
     */
    public static class EliteSpawnEvent {
        private final LivingEntity entity;
        private int level;
        private final DifficultyMode mode;
        private final Map<Ability, Integer> abilities;
        private boolean canceled = false;

        public EliteSpawnEvent(LivingEntity entity, int level, DifficultyMode mode, Map<Ability, Integer> abilities) {
            this.entity = entity;
            this.level = level;
            this.mode = mode;
            this.abilities = new LinkedHashMap<>(abilities);
        }

        public LivingEntity getEntity() { return entity; }
        public int getLevel() { return level; }
        public void setLevel(int level) { this.level = level; }
        public DifficultyMode getMode() { return mode; }
        public Map<Ability, Integer> getAbilities() { return abilities; }

        public void addAbility(Ability ability, int level) {
            abilities.put(ability, level);
        }

        public void removeAbility(Ability ability) {
            abilities.remove(ability);
        }

        public boolean isCanceled() { return canceled; }
        public void setCanceled(boolean canceled) { this.canceled = canceled; }
    }

    /**
     * Event data for elite death events.
     * Can modify drops.
     */
    public static class EliteDeathEvent {
        private final LivingEntity entity;
        private final LivingEntity killer;
        private final int level;
        private final Map<Ability, Integer> abilities;
        private final List<ItemStack> drops;
        private final QualityTier qualityTier;

        public EliteDeathEvent(LivingEntity entity, LivingEntity killer, int level,
                               Map<Ability, Integer> abilities, List<ItemStack> drops, QualityTier qualityTier) {
            this.entity = entity;
            this.killer = killer;
            this.level = level;
            this.abilities = abilities;
            this.drops = new ArrayList<>(drops);
            this.qualityTier = qualityTier;
        }

        public LivingEntity getEntity() { return entity; }
        public LivingEntity getKiller() { return killer; }
        public int getLevel() { return level; }
        public Map<Ability, Integer> getAbilities() { return abilities; }
        public List<ItemStack> getDrops() { return drops; }
        public QualityTier getQualityTier() { return qualityTier; }

        public void addDrop(ItemStack stack) {
            if (stack != null && !stack.isEmpty()) {
                drops.add(stack);
            }
        }

        public void removeDrop(ItemStack stack) {
            drops.remove(stack);
        }

        public void clearDrops() {
            drops.clear();
        }
    }

    /**
     * Event data for ability apply events.
     * Can modify level.
     */
    public static class AbilityApplyEvent {
        private final LivingEntity entity;
        private final Ability ability;
        private int level;

        public AbilityApplyEvent(LivingEntity entity, Ability ability, int level) {
            this.entity = entity;
            this.ability = ability;
            this.level = level;
        }

        public LivingEntity getEntity() { return entity; }
        public Ability getAbility() { return ability; }
        public int getLevel() { return level; }
        public void setLevel(int level) {
            if (level >= 1 && level <= 5) {
                this.level = level;
            }
        }
    }

    // ============ Event Registration ============

    /**
     * Register a handler for elite spawn events.
     */
    public static void onEliteSpawn(Consumer<EliteSpawnEvent> handler) {
        if (handler != null) {
            SPAWN_HANDLERS.add(handler);
        }
    }

    /**
     * Register a handler for elite death events.
     */
    public static void onEliteDeath(Consumer<EliteDeathEvent> handler) {
        if (handler != null) {
            DEATH_HANDLERS.add(handler);
        }
    }

    /**
     * Register a handler for ability apply events.
     */
    public static void onAbilityApply(Consumer<AbilityApplyEvent> handler) {
        if (handler != null) {
            ABILITY_APPLY_HANDLERS.add(handler);
        }
    }

    // ============ Event Firing ============

    /**
     * Fires the elite spawn event. Returns true if the spawn should be canceled.
     */
    public static boolean fireEliteSpawnEvent(LivingEntity entity, int level, DifficultyMode mode, Map<Ability, Integer> abilities) {
        EliteSpawnEvent event = new EliteSpawnEvent(entity, level, mode, abilities);

        // Fire to simple handlers
        for (Consumer<EliteSpawnEvent> handler : SPAWN_HANDLERS) {
            try {
                handler.accept(event);
            } catch (Exception e) {
                LOGGER.error("EliteForge: Error in elite spawn handler", e);
            }
        }

        // Fire to KubeJS if present
        if (isKubeJSLoaded()) {
            fireKubeJSEvent("eliteforge.elite_spawn", event);
        }

        // Apply any ability modifications back to the capability
        if (!event.isCanceled()) {
            EliteCapability cap = entity.getCapability(EliteCapability.CAPABILITY).orElse(null);
            if (cap != null) {
                EliteData data = cap.getEliteData();
                data.setLevel(event.getLevel());
                for (String id : new ArrayList<>(data.getAbilities().keySet())) {
                    data.removeAbility(id);
                }
                for (Map.Entry<Ability, Integer> entry : event.getAbilities().entrySet()) {
                    data.addAbility(entry.getKey().getIdString(), entry.getValue());
                }
                cap.setEliteData(data);
            }
        }

        return event.isCanceled();
    }

    /**
     * Fires the elite death event. Returns the (potentially modified) drops list.
     */
    public static List<ItemStack> fireEliteDeathEvent(LivingEntity entity, LivingEntity killer,
                                                       int level, Map<Ability, Integer> abilities,
                                                       List<ItemStack> drops, QualityTier tier) {
        EliteDeathEvent event = new EliteDeathEvent(entity, killer, level, abilities, drops, tier);

        // Fire to simple handlers
        for (Consumer<EliteDeathEvent> handler : DEATH_HANDLERS) {
            try {
                handler.accept(event);
            } catch (Exception e) {
                LOGGER.error("EliteForge: Error in elite death handler", e);
            }
        }

        // Fire to KubeJS if present
        if (isKubeJSLoaded()) {
            fireKubeJSEvent("eliteforge.elite_death", event);
        }

        return event.getDrops();
    }

    /**
     * Fires the ability apply event. Returns the (potentially modified) level.
     */
    public static int fireAbilityApplyEvent(LivingEntity entity, Ability ability, int level) {
        AbilityApplyEvent event = new AbilityApplyEvent(entity, ability, level);

        // Fire to simple handlers
        for (Consumer<AbilityApplyEvent> handler : ABILITY_APPLY_HANDLERS) {
            try {
                handler.accept(event);
            } catch (Exception e) {
                LOGGER.error("EliteForge: Error in ability apply handler", e);
            }
        }

        // Fire to KubeJS if present
        if (isKubeJSLoaded()) {
            fireKubeJSEvent("eliteforge.ability_apply", event);
        }

        return event.getLevel();
    }

    /**
     * Fires an event to KubeJS using reflection.
     * Only called when KubeJS is confirmed to be on the classpath.
     */
    private static void fireKubeJSEvent(String eventId, Object eventData) {
        try {
            // Use reflection to call KubeJS event system
            Class<?> eventClass = Class.forName("dev.latvian.mods.kubejs.KubeJSEvents");
            java.lang.reflect.Method postMethod = eventClass.getMethod("post", String.class, Object.class);
            postMethod.invoke(null, eventId, eventData);
        } catch (ClassNotFoundException e) {
            // KubeJS classes changed, ignore
            LOGGER.debug("EliteForge: KubeJS event class not found for {}", eventId);
        } catch (NoSuchMethodException e) {
            // Try alternative KubeJS API
            try {
                Class<?> scriptManagerClass = Class.forName("dev.latvian.mods.kubejs.script.ScriptManager");
                java.lang.reflect.Method postMethod = scriptManagerClass.getMethod("postEvent", String.class, Object.class);
                postMethod.invoke(null, eventId, eventData);
            } catch (Exception ex) {
                LOGGER.debug("EliteForge: Could not post KubeJS event {}: {}", eventId, ex.getMessage());
            }
        } catch (Exception e) {
            LOGGER.debug("EliteForge: Error posting KubeJS event {}: {}", eventId, e.getMessage());
        }
    }

    // ============ Creator-Tier Event Data Classes ============

    /**
     * Event data for creator-tier spawn events.
     * Fired when a creator-tier elite spawns.
     */
    public static class CreatorSpawnEvent {
        private final LivingEntity entity;
        private final Ability creatorAbility;
        private final int abilityLevel;
        private boolean canceled = false;

        public CreatorSpawnEvent(LivingEntity entity, Ability creatorAbility, int abilityLevel) {
            this.entity = entity;
            this.creatorAbility = creatorAbility;
            this.abilityLevel = abilityLevel;
        }

        public LivingEntity getEntity() { return entity; }
        public Ability getCreatorAbility() { return creatorAbility; }
        public int getAbilityLevel() { return abilityLevel; }
        public boolean isCanceled() { return canceled; }
        public void setCanceled(boolean canceled) { this.canceled = canceled; }
    }

    /**
     * Event data for creator-tier death events.
     * Fired when a creator-tier elite dies.
     */
    public static class CreatorDeathEvent {
        private final LivingEntity entity;
        private final LivingEntity killer;
        private final Ability creatorAbility;
        private final int abilityLevel;
        private final List<ItemStack> drops;
        private boolean droppedFragment = false;

        public CreatorDeathEvent(LivingEntity entity, LivingEntity killer, Ability creatorAbility,
                                  int abilityLevel, List<ItemStack> drops) {
            this.entity = entity;
            this.killer = killer;
            this.creatorAbility = creatorAbility;
            this.abilityLevel = abilityLevel;
            this.drops = new ArrayList<>(drops);
        }

        public LivingEntity getEntity() { return entity; }
        public LivingEntity getKiller() { return killer; }
        public Ability getCreatorAbility() { return creatorAbility; }
        public int getAbilityLevel() { return abilityLevel; }
        public List<ItemStack> getDrops() { return drops; }
        public boolean hasDroppedFragment() { return droppedFragment; }
        public void setDroppedFragment(boolean droppedFragment) { this.droppedFragment = droppedFragment; }

        public void addDrop(ItemStack stack) {
            if (stack != null && !stack.isEmpty()) {
                drops.add(stack);
            }
        }
    }

    /**
     * Event data for creator-tier awakening events.
     * Fired when a legendary elite awakens into creator-tier.
     */
    public static class CreatorAwakeningEvent {
        private final LivingEntity entity;
        private final Ability grantedAbility;
        private final int grantedLevel;
        private boolean canceled = false;

        public CreatorAwakeningEvent(LivingEntity entity, Ability grantedAbility, int grantedLevel) {
            this.entity = entity;
            this.grantedAbility = grantedAbility;
            this.grantedLevel = grantedLevel;
        }

        public LivingEntity getEntity() { return entity; }
        public Ability getGrantedAbility() { return grantedAbility; }
        public int getGrantedLevel() { return grantedLevel; }
        public boolean isCanceled() { return canceled; }
        public void setCanceled(boolean canceled) { this.canceled = canceled; }
    }

    /**
     * Event data for revenge trigger events.
     * Fired when the revenge system activates in a chunk.
     */
    public static class RevengeTriggerEvent {
        private final int chunkX;
        private final int chunkZ;
        private final int killCount;
        private final int threshold;
        private boolean canceled = false;

        public RevengeTriggerEvent(int chunkX, int chunkZ, int killCount, int threshold) {
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.killCount = killCount;
            this.threshold = threshold;
        }

        public int getChunkX() { return chunkX; }
        public int getChunkZ() { return chunkZ; }
        public int getKillCount() { return killCount; }
        public int getThreshold() { return threshold; }
        public boolean isCanceled() { return canceled; }
        public void setCanceled(boolean canceled) { this.canceled = canceled; }
    }

    // ============ Creator-Tier Event Registration ============

    /**
     * Register a handler for creator-tier spawn events.
     */
    public static void onCreatorSpawn(Consumer<CreatorSpawnEvent> handler) {
        if (handler != null) {
            CREATOR_SPAWN_HANDLERS.add(handler);
        }
    }

    /**
     * Register a handler for creator-tier death events.
     */
    public static void onCreatorDeath(Consumer<CreatorDeathEvent> handler) {
        if (handler != null) {
            CREATOR_DEATH_HANDLERS.add(handler);
        }
    }

    /**
     * Register a handler for creator-tier awakening events.
     */
    public static void onCreatorAwakening(Consumer<CreatorAwakeningEvent> handler) {
        if (handler != null) {
            CREATOR_AWAKENING_HANDLERS.add(handler);
        }
    }

    /**
     * Register a handler for revenge trigger events.
     */
    public static void onRevengeTrigger(Consumer<RevengeTriggerEvent> handler) {
        if (handler != null) {
            REVENGE_TRIGGER_HANDLERS.add(handler);
        }
    }

    // ============ Creator-Tier Event Firing ============

    /**
     * Fires the creator-tier spawn event. Returns true if the spawn should be canceled.
     */
    public static boolean fireCreatorSpawnEvent(LivingEntity entity, Ability creatorAbility, int abilityLevel) {
        CreatorSpawnEvent event = new CreatorSpawnEvent(entity, creatorAbility, abilityLevel);

        for (Consumer<CreatorSpawnEvent> handler : CREATOR_SPAWN_HANDLERS) {
            try {
                handler.accept(event);
            } catch (Exception e) {
                LOGGER.error("EliteForge: Error in creator spawn handler", e);
            }
        }

        if (isKubeJSLoaded()) {
            fireKubeJSEvent(CREATOR_SPAWN, event);
        }

        return event.isCanceled();
    }

    /**
     * Fires the creator-tier death event. Returns the (potentially modified) drops list.
     */
    public static List<ItemStack> fireCreatorDeathEvent(LivingEntity entity, LivingEntity killer,
                                                         Ability creatorAbility, int abilityLevel,
                                                         List<ItemStack> drops) {
        CreatorDeathEvent event = new CreatorDeathEvent(entity, killer, creatorAbility, abilityLevel, drops);

        for (Consumer<CreatorDeathEvent> handler : CREATOR_DEATH_HANDLERS) {
            try {
                handler.accept(event);
            } catch (Exception e) {
                LOGGER.error("EliteForge: Error in creator death handler", e);
            }
        }

        if (isKubeJSLoaded()) {
            fireKubeJSEvent(CREATOR_DEATH, event);
        }

        return event.getDrops();
    }

    /**
     * Fires the creator-tier awakening event. Returns true if the awakening should be canceled.
     */
    public static boolean fireCreatorAwakeningEvent(LivingEntity entity, Ability grantedAbility, int grantedLevel) {
        CreatorAwakeningEvent event = new CreatorAwakeningEvent(entity, grantedAbility, grantedLevel);

        for (Consumer<CreatorAwakeningEvent> handler : CREATOR_AWAKENING_HANDLERS) {
            try {
                handler.accept(event);
            } catch (Exception e) {
                LOGGER.error("EliteForge: Error in creator awakening handler", e);
            }
        }

        if (isKubeJSLoaded()) {
            fireKubeJSEvent(CREATOR_AWAKENING, event);
        }

        return event.isCanceled();
    }

    /**
     * Fires the revenge trigger event. Returns true if the revenge should be canceled.
     */
    public static boolean fireRevengeTriggerEvent(int chunkX, int chunkZ, int killCount, int threshold) {
        RevengeTriggerEvent event = new RevengeTriggerEvent(chunkX, chunkZ, killCount, threshold);

        for (Consumer<RevengeTriggerEvent> handler : REVENGE_TRIGGER_HANDLERS) {
            try {
                handler.accept(event);
            } catch (Exception e) {
                LOGGER.error("EliteForge: Error in revenge trigger handler", e);
            }
        }

        if (isKubeJSLoaded()) {
            fireKubeJSEvent(REVENGE_TRIGGER, event);
        }

        return event.isCanceled();
    }

    // ============ Generic KubeJS Event Posting Helpers ============

    /**
     * Post a generic KubeJS event by event type string.
     * Can be used for nurture, assimilate, bestow, reincarnate, evolve, command events.
     *
     * @param eventType the event type constant (e.g. ELITE_NURTURE, ELITE_ASSIMILATE)
     * @param eventData the event data object
     */
    public static void postKubeJSEvent(String eventType, Object eventData) {
        if (isKubeJSLoaded()) {
            fireKubeJSEvent(eventType, eventData);
        }
    }

    // ============ Helper Methods for KubeJS Scripts ============

    /**
     * Gets ability information by ID.
     * Can be called from KubeJS scripts.
     */
    public static AbilityInfo getAbility(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null) return null;

        Ability ability = AbilityRegistry.getAbility(rl);
        if (ability == null) return null;

        return new AbilityInfo(
                rl.toString(),
                Component.translatable(ability.getNameKey()).getString(),
                ability.getCategory().name(),
                ability.getMaxLevel(),
                ability.getBudgetCost(1)
        );
    }

    /**
     * Gets difficulty information for an entity.
     * Can be called from KubeJS scripts.
     */
    public static DifficultyInfo getDifficulty(LivingEntity entity) {
        if (entity == null) return null;

        EliteCapability cap = entity.getCapability(EliteCapability.CAPABILITY).orElse(null);
        if (cap == null || !cap.isElite()) return null;

        // Round 1 fix: EliteCapability only exposes getEliteData(); all elite
        // attributes (level, quality, abilities, spawn mode) live on EliteData.
        EliteData data = cap.getEliteData();
        return new DifficultyInfo(
                true,
                data.getLevel(),
                data.getSpawnMode().name(),
                data.getQualityTier().name(),
                data.getAbilities().size()
        );
    }

    // ============ Info Data Classes ============

    /**
     * Information about an ability, for use in KubeJS scripts.
     */
    public static class AbilityInfo {
        private final String id;
        private final String name;
        private final String category;
        private final int maxLevel;
        private final float budgetCost;

        public AbilityInfo(String id, String name, String category, int maxLevel, float budgetCost) {
            this.id = id;
            this.name = name;
            this.category = category;
            this.maxLevel = maxLevel;
            this.budgetCost = budgetCost;
        }

        public String getId() { return id; }
        public String getName() { return name; }
        public String getCategory() { return category; }
        public int getMaxLevel() { return maxLevel; }
        public float getBudgetCost() { return budgetCost; }

        @Override
        public String toString() {
            return String.format("AbilityInfo{id='%s', name='%s', category='%s', maxLevel=%d, budgetCost=%.1f}",
                    id, name, category, maxLevel, budgetCost);
        }
    }

    /**
     * Difficulty information about an entity, for use in KubeJS scripts.
     */
    public static class DifficultyInfo {
        private final boolean elite;
        private final int level;
        private final String mode;
        private final String qualityTier;
        private final int abilityCount;

        public DifficultyInfo(boolean elite, int level, String mode, String qualityTier, int abilityCount) {
            this.elite = elite;
            this.level = level;
            this.mode = mode;
            this.qualityTier = qualityTier;
            this.abilityCount = abilityCount;
        }

        public boolean isElite() { return elite; }
        public int getLevel() { return level; }
        public String getMode() { return mode; }
        public String getQualityTier() { return qualityTier; }
        public int getAbilityCount() { return abilityCount; }

        @Override
        public String toString() {
            return String.format("DifficultyInfo{elite=%s, level=%d, mode='%s', qualityTier='%s', abilityCount=%d}",
                    elite, level, mode, qualityTier, abilityCount);
        }
    }
}
