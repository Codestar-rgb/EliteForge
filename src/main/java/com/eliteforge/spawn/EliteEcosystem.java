package com.eliteforge.spawn;

import com.eliteforge.ability.Ability;
import com.eliteforge.ability.AbilityCategory;
import com.eliteforge.ability.AbilityRegistry;
import com.eliteforge.ability.MutualExclusion;
import com.eliteforge.capability.EliteCapability;
import com.eliteforge.capability.EliteData;
import com.eliteforge.capability.EliteCapabilitySync;
import com.eliteforge.config.DifficultyMode;
import com.eliteforge.config.EliteForgeConfig;
import com.eliteforge.difficulty.ChunkHeatManager;
import com.eliteforge.difficulty.PlayerExperienceManager;
import com.eliteforge.quality.QualityTier;
import com.eliteforge.util.MobHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ecosystem awareness system that manages relationships between creator and
 * non-creator elites. Tracks active creator entities, provides spawn conditions
 * for creator-tier conversion, and handles creator ability effects such as
 * nurturing and commanding.
 * <p>
 * Thread-safe via {@link ConcurrentHashMap} for creator tracking.
 */
public class EliteEcosystem {

    private static final Logger LOGGER = LogManager.getLogger();

    // ==================== Creator Tracking ====================

    /** Thread-safe map of active creator entities keyed by their UUID */
    private static final ConcurrentHashMap<UUID, CreatorInfo> ACTIVE_CREATORS = new ConcurrentHashMap<>();

    /** Default maximum active creator entities server-wide */
    private static final int DEFAULT_MAX_CREATORS = 2;

    /** Range for counting nearby elites during creator spawn check */
    private static final double SPAWN_CHECK_ELITE_RANGE = 32.0;

    /** Minimum nearby elites for creator spawn eligibility */
    private static final int MIN_NEARBY_ELITES = 3;

    // Creator spawn chances are now configured via EliteForgeConfig.SERVER
    // creatorSpawnChanceForge, creatorSpawnChanceMixed

    private EliteEcosystem() {
        // Utility class - no instantiation
    }

    // ==================== Creator Info Record ====================

    /**
     * Immutable record holding information about an active creator entity.
     *
     * @param entityUUID   the creator entity's UUID
     * @param abilityId    the creator ability ID string (e.g., "eliteforge:creator_nexus")
     * @param level        the creator ability level (1-3)
     * @param lastKnownPos the last known block position of the creator
     * @param dimension    the dimension key where the creator was last seen
     */
    public record CreatorInfo(UUID entityUUID, String abilityId, int level, BlockPos lastKnownPos,
                               ResourceKey<Level> dimension) {
    }

    // ==================== Registration ====================

    /**
     * Register a new creator entity in the ecosystem tracker.
     * Called when an entity becomes a creator (via awakening, spawn, or evolution).
     *
     * @param entity          the creator entity
     * @param creatorAbilityId the creator ability ID
     * @param level           the creator ability level
     */
    public static void registerCreator(LivingEntity entity, String creatorAbilityId, int level) {
        if (entity == null || creatorAbilityId == null) return;

        UUID uuid = entity.getUUID();
        CreatorInfo info = new CreatorInfo(uuid, creatorAbilityId, level, entity.blockPosition(),
                entity.level().dimension());
        ACTIVE_CREATORS.put(uuid, info);

        LOGGER.info("Registered creator entity {} with ability {} at level {}",
                entity.getName().getString(), creatorAbilityId, level);
    }

    /**
     * Unregister a creator entity from the ecosystem tracker.
     * Called when a creator dies or despawns.
     *
     * @param entity the creator entity to remove
     */
    public static void unregisterCreator(LivingEntity entity) {
        if (entity == null) return;

        CreatorInfo removed = ACTIVE_CREATORS.remove(entity.getUUID());
        if (removed != null) {
            LOGGER.info("Unregistered creator entity {} (ability: {})",
                    entity.getName().getString(), removed.abilityId());
        }
    }

    /**
     * Update the last known position of a creator entity.
     * Called periodically to keep position data fresh.
     *
     * @param entity the creator entity
     */
    public static void updateCreatorPosition(LivingEntity entity) {
        if (entity == null) return;

        CreatorInfo existing = ACTIVE_CREATORS.get(entity.getUUID());
        if (existing != null) {
            CreatorInfo updated = new CreatorInfo(
                    existing.entityUUID(), existing.abilityId(), existing.level(),
                    entity.blockPosition(), entity.level().dimension());
            ACTIVE_CREATORS.put(entity.getUUID(), updated);
        }
    }

    // ==================== Queries ====================

    /**
     * Find all active creators near a given position within the specified range.
     *
     * @param level the server level
     * @param pos   the center position to search from
     * @param range the search range in blocks
     * @return list of CreatorInfo for nearby creators
     */
    public static List<CreatorInfo> getNearbyCreators(ServerLevel level, BlockPos pos, double range) {
        List<CreatorInfo> result = new ArrayList<>();
        double rangeSq = range * range;

        for (CreatorInfo info : ACTIVE_CREATORS.values()) {
            BlockPos creatorPos = info.lastKnownPos();
            double distSq = pos.distSqr(creatorPos);
            if (distSq <= rangeSq) {
                // Only verify entity existence if the creator is in the SAME dimension.
                // Cross-dimension entities can't be looked up via level.getEntity(),
                // so we trust the cached data and skip cleanup here.
                if (info.dimension() != null && !info.dimension().equals(level.dimension())) {
                    // Creator is in another dimension — include using cached position,
                    // but do NOT remove (entity is alive in its own dimension).
                    result.add(info);
                    continue;
                }
                var entity = level.getEntity(info.entityUUID());
                if (entity instanceof LivingEntity living && living.isAlive()) {
                    // Check actual entity position since cached lastKnownPos may be stale
                    // due to entity movement. Use the real position for accuracy.
                    BlockPos actualPos = living.blockPosition();
                    double actualDistSq = pos.distSqr(actualPos);
                    if (actualDistSq <= rangeSq) {
                        // Update cached position to keep it fresh for future queries
                        if (!actualPos.equals(creatorPos)) {
                            CreatorInfo updated = new CreatorInfo(
                                    info.entityUUID(), info.abilityId(), info.level(),
                                    actualPos, info.dimension());
                            ACTIVE_CREATORS.put(info.entityUUID(), updated);
                            result.add(updated);
                        } else {
                            result.add(info);
                        }
                    }
                } else {
                    // Clean up stale entry (same dimension but entity gone)
                    ACTIVE_CREATORS.remove(info.entityUUID());
                }
            }
        }
        return result;
    }

    /**
     * Count the number of active creator entities in a given server level.
     *
     * @param level the server level
     * @return the count of active creators
     */
    public static int getActiveCreatorCount(ServerLevel level) {
        // Clean up stale entries while counting.
        // Only verify entities in the SAME dimension — cross-dimension creators
        // can't be looked up via level.getEntity() and would be wrongly removed.
        int count = 0;
        Iterator<Map.Entry<UUID, CreatorInfo>> iterator = ACTIVE_CREATORS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, CreatorInfo> entry = iterator.next();
            CreatorInfo info = entry.getValue();
            // Skip cleanup for creators in other dimensions — they are tracked elsewhere
            if (info.dimension() != null && !info.dimension().equals(level.dimension())) {
                count++;
                continue;
            }
            var entity = level.getEntity(entry.getKey());
            if (entity instanceof LivingEntity living && living.isAlive()) {
                count++;
            } else {
                iterator.remove();
            }
        }
        return count;
    }

    /**
     * Get the total number of tracked creators across all levels.
     *
     * @return total tracked creator count
     */
    public static int getTotalCreatorCount() {
        return ACTIVE_CREATORS.size();
    }

    // ==================== Spawn Condition Check ====================

    /**
     * Check whether an entity should spawn as a creator-tier elite.
     * <p>
     * All conditions must be met:
     * <ul>
     *   <li>Entity quality >= LEGENDARY</li>
     *   <li>Chunk heat >= 75</li>
     *   <li>At least 3 active elites within 32 blocks</li>
     *   <li>Server-wide creator count < configurable max (default 2)</li>
     *   <li>Player experience >= configurable minimum threshold (derived from minKillsForCreator * 3.0 exp/kill)</li>
     *   <li>Random chance passes: 0.5% FORGE, 0% CASUAL, 0.2% MIXED</li>
     * </ul>
     *
     * @param entity  the candidate entity
     * @param level   the server level
     * @param mode    the active difficulty mode (should come from DifficultyManager, not config directly)
     * @param quality the quality tier determined before this call (passed explicitly because capability data is not yet set)
     * @return true if all creator spawn conditions are met
     */
    public static boolean shouldSpawnAsCreator(LivingEntity entity, ServerLevel level, DifficultyMode mode, QualityTier quality) {
        // 1. Check if entity quality is at least LEGENDARY
        // Quality is passed as a parameter because this method is called BEFORE
        // the capability data is set, so reading from capability would always return NORMAL.
        if (quality.ordinal() < QualityTier.LEGENDARY.ordinal()) {
            return false;
        }

        // 2. Check chunk heat >= configurable minimum (default 75)
        ChunkHeatManager heatManager = ChunkHeatManager.get(level);
        ChunkPos chunkPos = new ChunkPos(entity.blockPosition());
        float heat = heatManager.getHeat(level, chunkPos);
        float minHeat = EliteForgeConfig.SERVER.minChunkHeatForCreator.get().floatValue();
        if (heat < minHeat) {
            return false;
        }

        // 3. Check at least 3 active elites within 32 blocks
        AABB area = new AABB(
                entity.getX() - SPAWN_CHECK_ELITE_RANGE,
                entity.getY() - SPAWN_CHECK_ELITE_RANGE,
                entity.getZ() - SPAWN_CHECK_ELITE_RANGE,
                entity.getX() + SPAWN_CHECK_ELITE_RANGE,
                entity.getY() + SPAWN_CHECK_ELITE_RANGE,
                entity.getZ() + SPAWN_CHECK_ELITE_RANGE
        );
        List<LivingEntity> nearbyElites = level.getEntitiesOfClass(LivingEntity.class, area,
                e -> e != entity && e.isAlive()
                        && e.getCapability(EliteCapability.CAPABILITY).map(EliteCapability::isElite).orElse(false));
        if (nearbyElites.size() < MIN_NEARBY_ELITES) {
            return false;
        }

        // 4. Server-wide creator count < max
        int maxCreators = getMaxCreators();
        if (getActiveCreatorCount(level) >= maxCreators) {
            return false;
        }

        // 5. Player experience >= minimum threshold (check nearest player's experience)
        // This is a server-wide check: at least one nearby player must have enough experience
        // fighting elites. We use PlayerExperienceManager as the single source of truth
        // instead of reading from NBT, which may be inconsistent or stale.
        // B3: Use the configurable playerExperienceGainOnEliteKill value (default 3.0)
        // instead of the hardcoded 3.0f, so server operators who tune the exp gain
        // config also get a correctly-scaled creator-spawn threshold.
        int minPlayerKills = EliteForgeConfig.SERVER.minKillsForCreator.get();
        PlayerExperienceManager expManager = PlayerExperienceManager.get(level);
        float expPerKill = (float) EliteForgeConfig.SERVER.playerExperienceGainOnEliteKill.get().doubleValue();
        float minExpThreshold = minPlayerKills * expPerKill;
        boolean hasQualifiedPlayer = false;
        for (var player : level.getPlayers(p -> p.distanceTo(entity) < 64.0)) {
            float playerExp = expManager.getPlayerExperience(player.getUUID());
            if (playerExp >= minExpThreshold) {
                hasQualifiedPlayer = true;
                break;
            }
        }
        if (!hasQualifiedPlayer) {
            return false;
        }

        // 6. Random chance based on difficulty mode (use the parameter, not config directly)
        double chance = switch (mode) {
            case FORGE -> EliteForgeConfig.SERVER.creatorSpawnChanceForge.get();
            case CASUAL -> 0.0; // CASUAL never spawns creators
            case MIXED -> EliteForgeConfig.SERVER.creatorSpawnChanceMixed.get();
        };

        return level.random.nextDouble() < chance;
    }

    // ==================== Creator Ability Effects ====================

    /**
     * Nurture nearby elites — for C1 Nexus creator ability.
     * <p>
     * Effects on nearby non-creator elites:
     * <ul>
     *   <li>Level +1 (capped at 5)</li>
     *   <li>Random ability level +1</li>
     *   <li>30% chance to grant a new I-level ability (at creator level II+)</li>
     *   <li>Equipment quality upgrade, capped at EPIC (at creator level III)</li>
     *   <li>Set nexusSourceUUID on nurtured elites</li>
     * </ul>
     *
     * @param creator    the creator entity (Nexus holder)
     * @param level      the creator ability level (1-3)
     * @param range      the nurturing range
     * @param maxTargets maximum number of elites to nurture per invocation
     * @return list of LivingEntity targets that were successfully nurtured
     */
    public static List<LivingEntity> nurtureNearbyElites(LivingEntity creator, int level, double range, int maxTargets) {
        if (!(creator.level() instanceof ServerLevel serverLevel)) return List.of();

        AABB area = new AABB(
                creator.getX() - range, creator.getY() - range, creator.getZ() - range,
                creator.getX() + range, creator.getY() + range, creator.getZ() + range
        );

        List<LivingEntity> nearbyElites = serverLevel.getEntitiesOfClass(LivingEntity.class, area,
                e -> e != creator && e.isAlive()
                        && e.getCapability(EliteCapability.CAPABILITY).isPresent());

        List<LivingEntity> nurtured = new ArrayList<>();
        for (LivingEntity target : nearbyElites) {
            if (nurtured.size() >= maxTargets) break;

            final boolean[] wasNurtured = {false};
            target.getCapability(EliteCapability.CAPABILITY).ifPresent(cap -> {
                EliteData targetData = cap.getEliteData();
                if (!targetData.isElite()) return;
                // Don't nurture other creators
                if (targetData.isCreatorEntity()) return;

                wasNurtured[0] = true;

                // Mark as nurtured by this nexus
                targetData.setNexusSourceUUID(creator.getUUID());

                // Level +1 (capped at 5)
                int currentLevel = targetData.getLevel();
                targetData.setLevel(Math.min(5, currentLevel + 1));

                // Random ability level +1
                Map<String, Integer> abilities = targetData.getAbilities();
                if (!abilities.isEmpty()) {
                    String[] abilityIds = abilities.keySet().toArray(new String[0]);
                    String randomAbilityId = abilityIds[creator.getRandom().nextInt(abilityIds.length)];
                    int currentAbilityLevel = abilities.get(randomAbilityId);
                    int newLevel = Math.min(5, currentAbilityLevel + 1);
                    targetData.addAbility(randomAbilityId, newLevel);
                }

                // 30% chance to grant new I-level ability (at creator level II+)
                if (level >= 2 && creator.getRandom().nextFloat() < 0.30f) {
                    // P2: Use cached non-creator ability list instead of streaming
                    // AbilityRegistry.getAllAbilities() every call.
                    var available = AbilityRegistry.getNonCreatorAbilities().stream()
                            .filter(a -> !targetData.hasAbility(a.getIdString()))
                            .filter(Ability::isEnabled)
                            .filter(a -> {
                                // Check mutual exclusion with existing abilities
                                for (String existingId : targetData.getAbilities().keySet()) {
                                    if (MutualExclusion.isMutuallyExclusive(a.getIdString(), existingId)) {
                                        return false;
                                    }
                                }
                                return true;
                            })
                            .toList();
                    if (!available.isEmpty()) {
                        var chosen = available.get(creator.getRandom().nextInt(available.size()));
                        targetData.addAbility(chosen.getIdString(), 1);
                    }
                }

                // Equipment quality upgrade (at creator level III), capped at EPIC
                // Nurtured elites cannot exceed EPIC quality through nurturing alone
                if (level >= 3) {
                    QualityTier currentQuality = targetData.getQualityTier();
                    if (currentQuality.ordinal() < QualityTier.EPIC.ordinal()) {
                        targetData.setQualityTier(currentQuality.next());
                    }
                }

                cap.setEliteData(targetData);
                EliteCapabilitySync.broadcastEliteDataUpdate(target, targetData);
            });

            if (wasNurtured[0]) {
                nurtured.add(target);
                // Ensure the nurtured elite is tracked immediately so that
                // any new/changed abilities are ticked without waiting for
                // the periodic full scan.
                EliteEventHandler.trackElite(target);
            }
        }

        LOGGER.debug("Nexus {} nurtured {} nearby elites (level={}, range={})",
                creator.getName().getString(), nurtured.size(), level, range);

        return nurtured;
    }

    /**
     * Command nearby elites — for C8 Commander creator ability.
     * <p>
     * Effects on nearby non-creator elites:
     * <ul>
     *   <li>Set their target to the commander's target</li>
     *   <li>Assign surround/protect/focus fire roles</li>
     *   <li>Set commanderUUID on commanded elites</li>
     *   <li>Level II+: Surround tactic + protect commander (2 closest move to commander when health &lt; 50%)</li>
     *   <li>Level III: Ranged/melee role division + Speed I + Strength I buffs</li>
     * </ul>
     *
     * @param commander the commander entity
     * @param squadSize maximum number of elites to command
     * @param range     the command range
     * @param level     the commander ability level
     * @return list of LivingEntity squad members that were successfully commanded
     */
    public static List<LivingEntity> commandSquad(LivingEntity commander, int squadSize, double range, int level) {
        if (!(commander.level() instanceof ServerLevel serverLevel)) return List.of();

        AABB area = new AABB(
                commander.getX() - range, commander.getY() - range, commander.getZ() - range,
                commander.getX() + range, commander.getY() + range, commander.getZ() + range
        );

        List<LivingEntity> nearbyElites = serverLevel.getEntitiesOfClass(LivingEntity.class, area,
                e -> e != commander && e.isAlive()
                        && e.getCapability(EliteCapability.CAPABILITY).isPresent());

        // Safe target acquisition — only Mob has getTarget(); non-Mob creators
        // (e.g., a Player given creator abilities via /eliteforge) would otherwise
        // throw ClassCastException here.
        LivingEntity primaryTarget = null;
        if (commander instanceof net.minecraft.world.entity.Mob mobCommander) {
            primaryTarget = mobCommander.getTarget();
        }
        if (primaryTarget == null) primaryTarget = commander.getLastHurtByMob();
        boolean hasTarget = primaryTarget != null;

        List<LivingEntity> squad = new ArrayList<>();
        for (LivingEntity elite : nearbyElites) {
            if (squad.size() >= squadSize) break;

            final boolean[] isSquadMember = {false};
            elite.getCapability(EliteCapability.CAPABILITY).ifPresent(cap -> {
                EliteData eliteData = cap.getEliteData();
                if (!eliteData.isElite()) return;
                // Don't command other creators
                if (eliteData.isCreatorEntity()) return;

                isSquadMember[0] = true;

                // Set commander UUID
                eliteData.setCommanderUUID(commander.getUUID());
                cap.setEliteData(eliteData);
                EliteCapabilitySync.broadcastEliteDataUpdate(elite, eliteData);
            });

            if (!isSquadMember[0]) continue;

            squad.add(elite);

            // Set target to commander's target
            if (hasTarget) {
                if (elite instanceof Mob mob) {
                    mob.setTarget(primaryTarget);
                } else {
                    elite.setLastHurtByMob(primaryTarget);
                }

                // Level II+: Surround tactic
                if (level >= 2 && elite instanceof Mob mob) {
                    int memberIndex = squad.size() - 1; // 0-based index of this squad member
                    double angle = (Math.PI * 2 * memberIndex) / Math.min(squadSize, nearbyElites.size());
                    double surroundDist = 3.0;
                    double destX = primaryTarget.getX() + Math.cos(angle) * surroundDist;
                    double destZ = primaryTarget.getZ() + Math.sin(angle) * surroundDist;
                    mob.getNavigation().moveTo(destX, primaryTarget.getY(), destZ, 1.2);
                }

                // Level III: Role division - ranged keep distance, melee charge
                if (level >= 3 && elite instanceof Mob mob) {
                    if (MobHelper.isRangedMob(mob)) {
                        double dist = mob.distanceTo(primaryTarget);
                        if (dist < 8.0) {
                            double ang = Math.atan2(mob.getZ() - primaryTarget.getZ(),
                                    mob.getX() - primaryTarget.getX());
                            double fleeX = primaryTarget.getX() + Math.cos(ang) * 12.0;
                            double fleeZ = primaryTarget.getZ() + Math.sin(ang) * 12.0;
                            mob.getNavigation().moveTo(fleeX, primaryTarget.getY(), fleeZ, 1.0);
                        }
                    } else {
                        mob.getNavigation().moveTo(primaryTarget, 1.5);
                    }
                }
            }
        }

        // Level II+: Protect commander — if health below 50%, move closest squad members toward commander
        if (level >= 2 && commander.getHealth() < commander.getMaxHealth() * 0.5f && !squad.isEmpty()) {
            int protectCount = Math.min(2, squad.size());
            // Sort squad by distance to commander (closest first)
            List<LivingEntity> sortedByDist = new ArrayList<>(squad);
            sortedByDist.sort((a, b) -> Double.compare(a.distanceTo(commander), b.distanceTo(commander)));
            for (int i = 0; i < protectCount; i++) {
                LivingEntity protector = sortedByDist.get(i);
                if (protector instanceof Mob mob) {
                    mob.getNavigation().moveTo(commander, 1.3);
                }
            }
        }

        // Level III: Apply Speed I and Strength I buffs to squad members
        if (level >= 3) {
            for (LivingEntity member : squad) {
                if (!member.hasEffect(MobEffects.MOVEMENT_SPEED) || member.getEffect(MobEffects.MOVEMENT_SPEED).getDuration() < 40) {
                    member.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 60, 0, false, true));
                }
                if (!member.hasEffect(MobEffects.DAMAGE_BOOST) || member.getEffect(MobEffects.DAMAGE_BOOST).getDuration() < 40) {
                    member.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 60, 0, false, true));
                }
            }
        }

        LOGGER.debug("Commander {} commanded {} elites (level={}, range={})",
                commander.getName().getString(), squad.size(), level, range);

        return squad;
    }

    // ==================== Config Helpers ====================

    /**
     * Get the maximum number of active creators allowed server-wide.
     *
     * @return max creator count (default 2)
     */
    private static int getMaxCreators() {
        try {
            return EliteForgeConfig.SERVER.maxCreatorEntities.get();
        } catch (Exception e) {
            return DEFAULT_MAX_CREATORS;
        }
    }

    // minPlayerKills is now read from EliteForgeConfig.SERVER.minKillsForCreator

    // ==================== Cleanup ====================

    /**
     * Tick all tracked creators in a server level: update their positions
     * and remove dead/removed ones. Should be called periodically from
     * EliteEventHandler's server tick (every 100 ticks / 5 seconds).
     *
     * @param level the server level
     */
    public static void tickAllCreators(ServerLevel level) {
        Iterator<Map.Entry<UUID, CreatorInfo>> iterator = ACTIVE_CREATORS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, CreatorInfo> entry = iterator.next();
            CreatorInfo info = entry.getValue();
            // Skip cross-dimension creators — they are tracked by their own dimension's tick.
            // Removing them here would break tracking on multi-dimension servers.
            if (info.dimension() != null && !info.dimension().equals(level.dimension())) {
                continue;
            }
            var entity = level.getEntity(entry.getKey());
            if (entity instanceof LivingEntity living && living.isAlive()) {
                // Update cached position to the entity's actual position
                BlockPos actualPos = living.blockPosition();
                if (!actualPos.equals(info.lastKnownPos())) {
                    CreatorInfo updated = new CreatorInfo(
                            info.entityUUID(), info.abilityId(), info.level(),
                            actualPos, info.dimension());
                    ACTIVE_CREATORS.put(entry.getKey(), updated);
                }
            } else {
                // Remove dead or removed creators (same dimension only)
                iterator.remove();
                LOGGER.debug("Removed dead/removed creator {} from ecosystem tracker", entry.getKey());
            }
        }
    }

    /**
     * Clear all tracked creators. Called on server stop.
     */
    public static void clearAll() {
        ACTIVE_CREATORS.clear();
        LOGGER.info("Cleared all tracked creator entities");
    }

    /**
     * Best-effort cleanup of stale cross-dimension creator entries.
     * <p>
     * {@link #getNearbyCreators} and {@link #getActiveCreatorCount} only verify
     * entity existence for creators in the SAME dimension as the queried level,
     * because {@code level.getEntity(uuid)} cannot look up entities in other
     * dimensions. This means a creator that died in another dimension (and was
     * removed without triggering our death event, e.g. due to a chunk-unload
     * despawn) would leave a stale entry in {@link #ACTIVE_CREATORS} indefinitely.
     * <p>
     * This method iterates ALL tracked creators and removes entries whose entity
     * can no longer be found in ANY loaded level. It should be called periodically
     * (e.g. every 5 minutes) from a server-wide tick. The cost is O(creators ×
     * loaded_levels), which is small because creator counts are capped by config.
     *
     * @param server the Minecraft server instance
     */
    public static void cleanupStaleCrossDimensionCreators(net.minecraft.server.MinecraftServer server) {
        if (server == null || ACTIVE_CREATORS.isEmpty()) return;
        Iterator<Map.Entry<UUID, CreatorInfo>> iterator = ACTIVE_CREATORS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, CreatorInfo> entry = iterator.next();
            CreatorInfo info = entry.getValue();
            // Try to find the entity in its home dimension first
            boolean found = false;
            if (info.dimension() != null) {
                ServerLevel homeLevel = server.getLevel(info.dimension());
                if (homeLevel != null) {
                    var entity = homeLevel.getEntity(entry.getKey());
                    if (entity instanceof LivingEntity living && living.isAlive()) {
                        found = true;
                    }
                }
            }
            // If not found in home dimension, the entry is stale (entity dead/despawned
            // OR the home dimension is not loaded — in which case we keep the entry
            // only if the dimension key matches a registered dimension that's just
            // temporarily unloaded; but distinguishing that is expensive, so we
            // only remove when the home dimension IS loaded but the entity is gone).
            if (!found && info.dimension() != null) {
                ServerLevel homeLevel = server.getLevel(info.dimension());
                if (homeLevel != null) {
                    // Home dimension is loaded but entity not found → definitely stale
                    iterator.remove();
                    LOGGER.debug("Removed stale cross-dimension creator {} (ability: {}, dimension: {})",
                            entry.getKey(), info.abilityId(), info.dimension().location());
                }
            }
        }
    }
}
