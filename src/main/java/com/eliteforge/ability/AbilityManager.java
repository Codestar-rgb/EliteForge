package com.eliteforge.ability;

import com.eliteforge.ability.AbilityBudget.BudgetData;
import com.eliteforge.config.DifficultyMode;
import com.eliteforge.util.NBTKeys;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.*;

/**
 * Manages ability assignment, application, and removal for entities.
 * 
 * @deprecated This is a LEGACY/UTILITY class. The primary ability lifecycle is now managed through:
 * - {@link com.eliteforge.spawn.AbilityGenerator} for ability generation
 * - {@link com.eliteforge.spawn.EliteEventHandler} for ability ticking and event dispatch
 * - {@link com.eliteforge.capability.EliteCapability} for ability data storage
 * <p>
 * This class will be removed in a future version. Migrate all usages to the
 * capability-based system ({@link com.eliteforge.capability.EliteData},
 * {@link com.eliteforge.spawn.AbilityGenerator}, {@link com.eliteforge.ability.AbilityRegistry}).
 * 
 * This class provides NBT-based ability tracking as an alternative to the capability-based
 * system. It can be useful for cross-mod compatibility or when capability access is not
 * available. However, the spawn and event systems use the capability-based approach.
 * 
 * Key differences from capability-based approach:
 * - Stores abilities in entity persistent NBT under "EliteForgeAbilities"
 * - Uses NBT tick counters instead of in-memory tracking
 * - Does not integrate with the budget system or entity presets
 */
@Deprecated
public final class AbilityManager {

    private AbilityManager() {
        // Utility class - no instantiation
    }

    private static final String ABILITY_NBT_KEY = NBTKeys.LEGACY_ABILITIES;
    private static final String ABILITY_ID_KEY = "id";
    private static final String ABILITY_LEVEL_KEY = "level";
    private static final String ABILITY_TICK_COUNTER_KEY = "tickCounter";

    // ========== Ability Generation ==========

    /**
     * Generates a set of abilities for an entity based on level and difficulty mode.
     * Uses the dual-budget system and respects mutual exclusion rules.
     *
     * @param entity      The entity to generate abilities for
     * @param targetLevel The target level (affects budget allocation)
     * @param mode        The difficulty mode
     * @return List of ability-level pairs to apply
     * @deprecated Use {@link com.eliteforge.spawn.AbilityGenerator} instead.
     */
    @Deprecated
    public static List<Map.Entry<Ability, Integer>> generateAbilities(LivingEntity entity, int targetLevel, DifficultyMode mode) {
        if (entity == null || targetLevel < 1) return Collections.emptyList();

        List<Map.Entry<Ability, Integer>> result = new ArrayList<>();
        BudgetData budgets = AbilityBudget.calculateBudgets(targetLevel, mode);
        int maxAbilities = AbilityBudget.getMaxAbilities(targetLevel, mode);
        int maxLevel = AbilityBudget.getMaxAbilityLevel(targetLevel, mode);

        Random random = new Random(entity.getUUID().hashCode());
        List<Ability> selectedAbilities = new ArrayList<>();

        // Build weighted pool per category
        for (AbilityCategory category : AbilityCategory.values()) {
            // Creator abilities are never generated through the budget system
            // They are only assigned through the special creator conversion process
            if (category == AbilityCategory.CREATOR) continue;

            float remainingBudget = budgets.getBudgetForCategory(category);
            if (remainingBudget <= 0) continue;

            Collection<Ability> categoryAbilities = AbilityRegistry.getAbilitiesByCategory(category);
            List<Ability> pool = new ArrayList<>(categoryAbilities);
            Collections.shuffle(pool, random);

            for (Ability ability : pool) {
                if (result.size() >= maxAbilities) break;
                if (!ability.isEnabled()) continue;

                // Check mutual exclusion
                boolean exclusive = false;
                for (Ability existing : selectedAbilities) {
                    if (!ability.canCoexistWith(existing)) {
                        exclusive = true;
                        break;
                    }
                }
                if (exclusive) continue;

                // Determine level for this ability
                int abilityLevel = determineAbilityLevel(ability, maxLevel, remainingBudget, random);
                if (abilityLevel < 1) continue;

                float cost = ability.getBudgetCost(abilityLevel);
                if (cost > remainingBudget) {
                    // Try lower level
                    abilityLevel = findAffordableLevel(ability, maxLevel, remainingBudget, random);
                    if (abilityLevel < 1) continue;
                    cost = ability.getBudgetCost(abilityLevel);
                }

                remainingBudget -= cost;
                selectedAbilities.add(ability);
                result.add(new AbstractMap.SimpleEntry<>(ability, abilityLevel));
            }

        }

        return result;
    }

    /**
     * Determines the level for an ability based on budget and max level constraints.
     */
    private static int determineAbilityLevel(Ability ability, int maxLevel, float remainingBudget, Random random) {
        int levelCap = Math.min(maxLevel, ability.getMaxLevel());
        float baseCost = ability.getBaseBudgetCost();

        if (baseCost <= 0) return 1;

        int maxAffordable = (int) (remainingBudget / baseCost);
        int level = Math.min(levelCap, maxAffordable);

        // Add some randomness - don't always pick max level
        if (level > 1 && random.nextFloat() < 0.3f) {
            level = Math.max(1, level - 1);
        }

        return Math.max(1, level);
    }

    /**
     * Finds the highest affordable level for an ability.
     */
    private static int findAffordableLevel(Ability ability, int maxLevel, float remainingBudget, Random random) {
        int levelCap = Math.min(maxLevel, ability.getMaxLevel());
        for (int level = levelCap; level >= 1; level--) {
            if (ability.getBudgetCost(level) <= remainingBudget) {
                return level;
            }
        }
        return 0;
    }

    // ========== Ability Application ==========

    /**
     * Applies a list of abilities to an entity.
     * Stores ability data in the entity's NBT and calls onApply for each ability.
     *
     * @param entity   The entity to apply abilities to
     * @param abilities List of ability-level pairs
     * @deprecated Use {@link com.eliteforge.capability.EliteData#addAbility(String, int)} and
     *             {@link com.eliteforge.capability.EliteCapability#setEliteData(EliteData)} instead.
     */
    @Deprecated
    public static void applyAbilities(LivingEntity entity, List<Map.Entry<Ability, Integer>> abilities) {
        if (entity == null || abilities == null || abilities.isEmpty()) return;

        // Remove existing abilities first
        removeAbilities(entity);

        CompoundTag entityData = entity.getPersistentData();
        CompoundTag eliteForgeData = new CompoundTag();
        ListTag abilityList = new ListTag();

        for (Map.Entry<Ability, Integer> entry : abilities) {
            Ability ability = entry.getKey();
            int level = entry.getValue();

            // Store ability data
            CompoundTag abilityTag = new CompoundTag();
            abilityTag.putString(ABILITY_ID_KEY, ability.getIdString());
            abilityTag.putInt(ABILITY_LEVEL_KEY, level);
            abilityTag.putInt(ABILITY_TICK_COUNTER_KEY, 0);
            abilityList.add(abilityTag);

            // Call onApply
            try {
                ability.onApply(entity, level);
            } catch (Exception e) {
                // Log but don't crash
                com.eliteforge.EliteForge.LOGGER.error("Error applying ability {}: {}", ability.getIdString(), e.getMessage());
            }
        }

        eliteForgeData.put(ABILITY_NBT_KEY, abilityList);
        entityData.put(ABILITY_NBT_KEY, eliteForgeData);
    }

    /**
     * Removes all abilities from an entity.
     *
     * @param entity The entity to remove abilities from
     * @deprecated Use {@link com.eliteforge.capability.EliteData#removeAbility(String)} in a loop instead.
     */
    @Deprecated
    public static void removeAbilities(LivingEntity entity) {
        if (entity == null) return;

        // Call onRemove for all current abilities before clearing
        List<Map.Entry<Ability, Integer>> currentAbilities = getEntityAbilities(entity);
        for (Map.Entry<Ability, Integer> entry : currentAbilities) {
            try {
                entry.getKey().onRemove(entity, entry.getValue());
            } catch (Exception e) {
                com.eliteforge.EliteForge.LOGGER.error("Error removing ability {}: {}", entry.getKey().getIdString(), e.getMessage());
            }
        }

        CompoundTag entityData = entity.getPersistentData();
        entityData.remove(ABILITY_NBT_KEY);
    }

    // ========== Ability Querying ==========

    /**
     * Gets all abilities currently applied to an entity.
     *
     * @param entity The entity to query
     * @return List of ability-level pairs, or empty list if none
     * @deprecated Use {@link com.eliteforge.capability.EliteData#getAbilities()} via the capability system instead.
     */
    @Deprecated
    public static List<Map.Entry<Ability, Integer>> getEntityAbilities(LivingEntity entity) {
        if (entity == null) return Collections.emptyList();

        List<Map.Entry<Ability, Integer>> abilities = new ArrayList<>();
        CompoundTag entityData = entity.getPersistentData();

        if (!entityData.contains(ABILITY_NBT_KEY)) return abilities;

        CompoundTag eliteForgeData = entityData.getCompound(ABILITY_NBT_KEY);
        ListTag abilityList = eliteForgeData.getList(ABILITY_NBT_KEY, 10); // 10 = CompoundTag

        for (int i = 0; i < abilityList.size(); i++) {
            CompoundTag abilityTag = abilityList.getCompound(i);
            String id = abilityTag.getString(ABILITY_ID_KEY);
            int level = abilityTag.getInt(ABILITY_LEVEL_KEY);

            Ability ability = AbilityRegistry.getAbility(id);
            if (ability != null && level > 0) {
                abilities.add(new AbstractMap.SimpleEntry<>(ability, level));
            }
        }

        return abilities;
    }

    /**
     * Gets the level of a specific ability on an entity.
     *
     * @param entity  The entity to query
     * @param ability The ability to check
     * @return The ability level, or 0 if not present
     * @deprecated Use {@link com.eliteforge.capability.EliteData#getAbilityLevel(String)} instead.
     */
    @Deprecated
    public static int getEntityAbilityLevel(LivingEntity entity, Ability ability) {
        if (entity == null || ability == null) return 0;

        for (Map.Entry<Ability, Integer> entry : getEntityAbilities(entity)) {
            if (entry.getKey().getIdString().equals(ability.getIdString())) {
                return entry.getValue();
            }
        }
        return 0;
    }

    // ========== Event Dispatching ==========

    /**
     * Ticks all abilities on an entity. Should be called from a Forge event handler
     * on LivingTickEvent or similar.
     *
     * @param entity The entity to tick abilities for
     * @deprecated Ability ticking is handled by {@link com.eliteforge.spawn.EliteEventHandler}.
     */
    @Deprecated
    public static void tickAbilities(LivingEntity entity) {
        if (entity == null || entity.level().isClientSide) return;

        List<Map.Entry<Ability, Integer>> abilities = getEntityAbilities(entity);
        CompoundTag entityData = entity.getPersistentData();
        if (!entityData.contains(ABILITY_NBT_KEY)) return;

        CompoundTag eliteForgeData = entityData.getCompound(ABILITY_NBT_KEY);
        ListTag abilityList = eliteForgeData.getList(ABILITY_NBT_KEY, 10);

        for (int i = 0; i < abilityList.size(); i++) {
            CompoundTag abilityTag = abilityList.getCompound(i);
            String id = abilityTag.getString(ABILITY_ID_KEY);
            int level = abilityTag.getInt(ABILITY_LEVEL_KEY);
            int tickCounter = abilityTag.getInt(ABILITY_TICK_COUNTER_KEY);

            Ability ability = AbilityRegistry.getAbility(id);
            if (ability != null && level > 0) {
                try {
                    ability.onTick(entity, level);
                } catch (Exception e) {
                    com.eliteforge.EliteForge.LOGGER.error("Error ticking ability {}: {}", id, e.getMessage());
                }
            }

            // Increment tick counter
            abilityTag.putInt(ABILITY_TICK_COUNTER_KEY, tickCounter + 1);
        }
    }

    /**
     * Dispatches an attack event to all abilities on the attacker.
     *
     * @param attacker The attacking entity
     * @param target   The target entity
     * @param damage   The damage dealt
     * @deprecated Attack dispatch is handled by {@link com.eliteforge.spawn.EliteEventHandler}.
     */
    @Deprecated
    public static void onAttack(LivingEntity attacker, LivingEntity target, float damage) {
        if (attacker == null || target == null) return;

        for (Map.Entry<Ability, Integer> entry : getEntityAbilities(attacker)) {
            try {
                entry.getKey().onAttack(attacker, target, damage, entry.getValue());
            } catch (Exception e) {
                com.eliteforge.EliteForge.LOGGER.error("Error in onAttack for {}: {}", entry.getKey().getIdString(), e.getMessage());
            }
        }
    }

    /**
     * Dispatches a hurt event to all abilities on the hurt entity.
     *
     * @param entity The entity that was hurt
     * @param damage The damage taken
     * @deprecated Hurt dispatch is handled by {@link com.eliteforge.spawn.EliteEventHandler}.
     */
    @Deprecated
    public static void onHurt(LivingEntity entity, float damage) {
        if (entity == null) return;

        for (Map.Entry<Ability, Integer> entry : getEntityAbilities(entity)) {
            try {
                entry.getKey().onHurt(entity, damage, entry.getValue());
            } catch (Exception e) {
                com.eliteforge.EliteForge.LOGGER.error("Error in onHurt for {}: {}", entry.getKey().getIdString(), e.getMessage());
            }
        }
    }

    /**
     * Dispatches a death event to all abilities on the dying entity.
     *
     * @param entity The entity that died
     * @deprecated Death dispatch is handled by {@link com.eliteforge.spawn.EliteEventHandler}.
     */
    @Deprecated
    public static void onDeath(LivingEntity entity) {
        if (entity == null) return;

        for (Map.Entry<Ability, Integer> entry : getEntityAbilities(entity)) {
            try {
                entry.getKey().onDeath(entity, entry.getValue());
            } catch (Exception e) {
                com.eliteforge.EliteForge.LOGGER.error("Error in onDeath for {}: {}", entry.getKey().getIdString(), e.getMessage());
            }
        }
    }

    /**
     * Dispatches a playerKill event to all abilities on the killed entity.
     *
     * @param entity The entity that was killed
     * @param player The player who killed it
     * @deprecated PlayerKill dispatch is handled by {@link com.eliteforge.spawn.EliteEventHandler}.
     */
    @Deprecated
    public static void onPlayerKill(LivingEntity entity, Player player) {
        if (entity == null || player == null) return;

        for (Map.Entry<Ability, Integer> entry : getEntityAbilities(entity)) {
            try {
                entry.getKey().onPlayerKill(entity, player, entry.getValue());
            } catch (Exception e) {
                com.eliteforge.EliteForge.LOGGER.error("Error in onPlayerKill for {}: {}", entry.getKey().getIdString(), e.getMessage());
            }
        }
    }

    // ========== Tick Counter Helpers ==========

    /**
     * Gets the current tick counter for a specific ability on an entity.
     *
     * @param entity  The entity
     * @param ability The ability
     * @return The tick counter value, or 0 if not found
     * @deprecated Tick counters are no longer stored in NBT. Use capability-based tracking instead.
     */
    @Deprecated
    public static int getTickCounter(LivingEntity entity, Ability ability) {
        if (entity == null || ability == null) return 0;

        CompoundTag entityData = entity.getPersistentData();
        if (!entityData.contains(ABILITY_NBT_KEY)) return 0;

        CompoundTag eliteForgeData = entityData.getCompound(ABILITY_NBT_KEY);
        ListTag abilityList = eliteForgeData.getList(ABILITY_NBT_KEY, 10);

        for (int i = 0; i < abilityList.size(); i++) {
            CompoundTag abilityTag = abilityList.getCompound(i);
            if (abilityTag.getString(ABILITY_ID_KEY).equals(ability.getIdString())) {
                return abilityTag.getInt(ABILITY_TICK_COUNTER_KEY);
            }
        }
        return 0;
    }

    /**
     * Sets the tick counter for a specific ability on an entity.
     *
     * @param entity      The entity
     * @param ability     The ability
     * @param tickCounter The new tick counter value
     * @deprecated Tick counters are no longer stored in NBT. Use capability-based tracking instead.
     */
    @Deprecated
    public static void setTickCounter(LivingEntity entity, Ability ability, int tickCounter) {
        if (entity == null || ability == null) return;

        CompoundTag entityData = entity.getPersistentData();
        if (!entityData.contains(ABILITY_NBT_KEY)) return;

        CompoundTag eliteForgeData = entityData.getCompound(ABILITY_NBT_KEY);
        ListTag abilityList = eliteForgeData.getList(ABILITY_NBT_KEY, 10);

        for (int i = 0; i < abilityList.size(); i++) {
            CompoundTag abilityTag = abilityList.getCompound(i);
            if (abilityTag.getString(ABILITY_ID_KEY).equals(ability.getIdString())) {
                abilityTag.putInt(ABILITY_TICK_COUNTER_KEY, tickCounter);
                return;
            }
        }
    }

    /**
     * Checks if an entity has any abilities (is an elite).
     *
     * @param entity The entity to check
     * @return true if the entity has at least one ability
     * @deprecated Use {@link com.eliteforge.capability.EliteCapability#isElite()} or
     *             {@link com.eliteforge.capability.EliteData#hasAbility(String)} instead.
     */
    @Deprecated
    public static boolean hasAbilities(LivingEntity entity) {
        if (entity == null) return false;
        return !getEntityAbilities(entity).isEmpty();
    }

    /**
     * Sends a spawn notification message to nearby players about an elite entity's abilities.
     *
     * @param entity The elite entity
     * @deprecated Broadcast is handled by {@link com.eliteforge.spawn.EliteEventHandler} and
     *             {@link com.eliteforge.capability.EliteCapabilitySync}.
     */
    @Deprecated
    public static void broadcastAbilityInfo(LivingEntity entity) {
        if (entity == null || entity.level().isClientSide) return;

        List<Map.Entry<Ability, Integer>> abilities = getEntityAbilities(entity);
        if (abilities.isEmpty()) return;

        ServerLevel serverLevel = (ServerLevel) entity.level();

        // Build the ability display. Use MutableComponent so we can call append(...)
        // (Component itself is read-only and does not expose append).
        MutableComponent nameComponent = entity.getName().copy();
        for (Map.Entry<Ability, Integer> entry : abilities) {
            nameComponent.append(" ").append(entry.getKey().getDisplayName(entry.getValue()));
        }

        // Send to nearby players
        for (Player player : serverLevel.players()) {
            if (player.distanceTo(entity) < 64) {
                player.sendSystemMessage(nameComponent);
            }
        }
    }
}
