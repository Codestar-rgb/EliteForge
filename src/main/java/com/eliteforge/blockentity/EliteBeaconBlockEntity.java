package com.eliteforge.blockentity;

import com.eliteforge.block.EliteBeaconBlock;
import com.eliteforge.capability.EliteCapability;
import com.eliteforge.capability.EliteCapabilitySync;
import com.eliteforge.capability.EliteData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * EliteBeaconBlockEntity - Block entity for the Elite Beacon.
 * Stores suppression mode (0-2).
 * Stores active state.
 * tick(): check for nearby Heat Collector, consume heat, apply effects.
 * Apply suppression effect to area based on mode.
 *
 * Suppression Modes:
 *   0 (Level I): Suppress elite spawns in 16-block radius
 *   1 (Level II): Reduce elite level by 1 in 32-block radius
 *   2 (Level III): Prevent all elite spawns in 48-block radius
 *
 * Consumes 1 heat unit per 20 ticks from nearby Heat Collector.
 */
public class EliteBeaconBlockEntity extends BlockEntity {

    private static final int HEAT_CONSUME_INTERVAL = 20; // 1 heat per second
    private static final int HEAT_COLLECTOR_RANGE = 8;
    private static final int[] SUPPRESSION_RADIUS = {16, 32, 48};

    private static final String ELITE_TAG = "eliteforge:elite";
    private static final String ELITE_LEVEL_TAG = "eliteforge:level";
    private static final String ELITE_SUPPRESSED_TAG = "eliteforge:suppressed";
    private static final String BEACON_ACTIVE_TAG = "eliteforge:beacon_active";

    /**
     * In-memory storage of Elite Beacon suppression data per chunk position,
     * keyed by dimension ResourceLocation. Replaces the legacy
     * {@code LevelChunk.persistentDataContainer} field that was removed in
     * Forge 1.20.1. Each CompoundTag maps a chunk-long-key to its suppression
     * metadata (mode, radius, beacon xyz, timestamp).
     */
    private static final java.util.concurrent.ConcurrentHashMap<
            net.minecraft.resources.ResourceLocation,
            java.util.concurrent.ConcurrentHashMap<Long, CompoundTag>> BEACON_SUPPRESSION =
            new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Public read accessor used by the spawn pipeline to check whether a given
     * chunk (in a given dimension) is currently inside an active Elite Beacon
     * suppression zone.
     */
    public static CompoundTag getBeaconSuppression(
            net.minecraft.resources.ResourceLocation dimension, long chunkKey) {
        java.util.concurrent.ConcurrentHashMap<Long, CompoundTag> map = BEACON_SUPPRESSION.get(dimension);
        return map == null ? null : map.get(chunkKey);
    }

    private int suppressionMode = 0;
    private boolean active = false;
    private int tickCounter = 0;
    private boolean hasHeatSource = true; // Always true in v0.2.0
    private int checkLinkTimer = 0;
    private int suppressionTickCounter = 0;

    public EliteBeaconBlockEntity(BlockPos pos, BlockState state) {
        // Delegate to the typed constructor so getType() returns the right value.
        // Forge's BlockEntityType.Builder.of(EliteBeaconBlockEntity::new, ...) resolves ::new
        // to THIS (BlockPos, BlockState) ctor, then calls super(null, pos, state) — leaving
        // the BE's `type` field null. ModBlockEntities.ELITE_BEACON_BE.get() is safe at
        // BE-creation time (the registry has fired by then).
        this(com.eliteforge.init.ModBlockEntities.ELITE_BEACON_BE.get(), pos, state);
    }

    public EliteBeaconBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /**
     * Main tick logic for the Elite Beacon.
     * Checks for nearby Heat Collector, consumes heat, and applies suppression effects.
     */
    public void tick() {
        if (level == null || level.isClientSide) return;

        tickCounter++;
        checkLinkTimer++;

        // Periodically check for nearby Heat Collectors
        if (checkLinkTimer >= 100) {
            checkLinkTimer = 0;
            // HeatCollector removed in v0.2.0
            // Stale-suppression cleanup: remove entries older than 5 minutes (6000 ticks).
            // Without this, breaking a beacon leaves its suppression entries in BEACON_SUPPRESSION
            // forever, permanently suppressing elite spawns in those chunks.
            long now = level.getGameTime();
            long maxAge = 6000;
            java.util.Iterator<java.util.Map.Entry<net.minecraft.resources.ResourceLocation,
                    java.util.concurrent.ConcurrentHashMap<Long, CompoundTag>>> dimIter =
                    BEACON_SUPPRESSION.entrySet().iterator();
            while (dimIter.hasNext()) {
                var dimEntry = dimIter.next();
                var chunkMap = dimEntry.getValue();
                chunkMap.entrySet().removeIf(e -> {
                    CompoundTag tag = e.getValue();
                    return tag == null || (tag.contains("time") && now - tag.getLong("time") > maxAge);
                });
                if (chunkMap.isEmpty()) dimIter.remove();
            }
        }

        // HeatCollector block was removed in v0.2.0 phase 1; hasHeatSource is never
        // set true anymore (the link-discovery method was removed with it). The flag
        // is still persisted for save-file backward compatibility. While a stale
        // "linked" flag is present we keep the beacon active and applying suppression;
        // otherwise the beacon is inactive.
        if (hasHeatSource) {
            if (tickCounter >= HEAT_CONSUME_INTERVAL) {
                tickCounter = 0;
                setActive(true);
                applySuppressionEffects();
            }
        } else {
            setActive(false);
        }
    }

    /**
     * Find the nearest Heat Collector within range.
     */

    /**
     * Apply suppression effects based on the current mode.
     */
    private void applySuppressionEffects() {
        if (level == null || !(level instanceof ServerLevel serverLevel)) return;

        int radius = SUPPRESSION_RADIUS[suppressionMode];
        BlockPos beaconPos = getBlockPos();

        // Mark the area as suppressed in chunk data
        // This prevents the elite spawn system from spawning in this area
        // In 1.20.1, LevelChunk.persistentDataContainer was removed; we use an
        // in-memory static map keyed by dimension + chunk-long-key instead.
        net.minecraft.resources.ResourceLocation dimKey = serverLevel.dimension().location();
        java.util.concurrent.ConcurrentHashMap<Long, CompoundTag> dimMap =
                BEACON_SUPPRESSION.computeIfAbsent(dimKey, k -> new java.util.concurrent.ConcurrentHashMap<>());

        for (int cx = -radius / 16; cx <= radius / 16; cx++) {
            for (int cz = -radius / 16; cz <= radius / 16; cz++) {
                BlockPos chunkPos = beaconPos.offset(cx * 16, 0, cz * 16);
                long chunkKey = net.minecraft.world.level.ChunkPos.asLong(chunkPos.getX() >> 4, chunkPos.getZ() >> 4);

                // Store beacon suppression data
                CompoundTag suppression = new CompoundTag();
                suppression.putInt("mode", suppressionMode);
                suppression.putInt("radius", radius);
                suppression.putInt("x", beaconPos.getX());
                suppression.putInt("y", beaconPos.getY());
                suppression.putInt("z", beaconPos.getZ());
                suppression.putLong("time", level.getGameTime());

                dimMap.put(chunkKey, suppression);
            }
        }

        // Apply immediate effects to existing elites in range
        AABB effectBox = new AABB(
            beaconPos.getX() - radius, beaconPos.getY() - radius, beaconPos.getZ() - radius,
            beaconPos.getX() + radius, beaconPos.getY() + radius, beaconPos.getZ() + radius
        );

        List<Mob> nearbyElites = level.getEntitiesOfClass(Mob.class, effectBox, this::isElite);

        for (Mob elite : nearbyElites) {
            // Use the EliteCapability to read/modify elite data (NOT persistent NBT,
            // which is not kept in sync with the capability-based system).
            EliteCapability cap = elite.getCapability(EliteCapability.CAPABILITY).orElse(null);
            if (cap == null || !cap.isElite()) continue;
            EliteData eliteDataCap = cap.getEliteData();
            boolean modified = false;

            switch (suppressionMode) {
                case 0 -> {
                    // Level I: Suppress spawns (mark area, doesn't affect existing elites directly)
                    // Weaken existing elites slightly — set a suppressed flag on persistent data
                    elite.getPersistentData().putBoolean(ELITE_SUPPRESSED_TAG, true);
                }
                case 1 -> {
                    // Level II: Reduce elite level by 1
                    int currentLevel = eliteDataCap.getLevel();
                    int newLevel = Math.max(1, currentLevel - 1);
                    eliteDataCap.setLevel(newLevel);
                    elite.getPersistentData().putBoolean(ELITE_SUPPRESSED_TAG, true);
                    modified = true;

                    // Notify nearby players
                    notifyNearbyPlayers(Component.translatable("message.eliteforge.elite_beacon.difficulty_reduced")
                        .withStyle(ChatFormatting.YELLOW));
                }
                case 2 -> {
                    // Level III: Prevent all elite spawns + heavily weaken existing elites
                    elite.getPersistentData().putBoolean(ELITE_SUPPRESSED_TAG, true);
                    int currentLevel = eliteDataCap.getLevel();
                    eliteDataCap.setLevel(Math.max(1, currentLevel - 2));

                    // Remove one ability from suppressed elites (one per application)
                    // Skip creator-tier abilities (too powerful to suppress)
                    for (String abilityId : new java.util.ArrayList<>(eliteDataCap.getAbilities().keySet())) {
                        com.eliteforge.ability.Ability ability = com.eliteforge.ability.AbilityRegistry.getAbility(abilityId);
                        if (ability != null && ability.getCategory() != com.eliteforge.ability.AbilityCategory.CREATOR) {
                            int lvl = eliteDataCap.getAbilityLevel(abilityId);
                            try {
                                ability.onRemove(elite, lvl);
                            } catch (Exception e) {
                                // ignore
                            }
                            eliteDataCap.removeAbility(abilityId);
                            break; // only remove one per application
                        }
                    }
                    modified = true;
                }
            }

            if (modified) {
                cap.setEliteData(eliteDataCap);
                EliteCapabilitySync.broadcastEliteDataUpdate(elite, eliteDataCap);
            }
        }
    }

    private boolean isElite(Mob mob) {
        // Use the EliteCapability to detect elite status (NOT the "forge" sub-compound NBT,
        // which is never written by the modern capability-based system).
        return mob.getCapability(EliteCapability.CAPABILITY)
                .map(EliteCapability::isElite)
                .orElse(false);
    }

    private void notifyNearbyPlayers(Component message) {
        if (level == null) return;
        int radius = 32;
        AABB box = new AABB(
            worldPosition.getX() - radius, worldPosition.getY() - radius, worldPosition.getZ() - radius,
            worldPosition.getX() + radius, worldPosition.getY() + radius, worldPosition.getZ() + radius
        );
        List<Player> players = level.getEntitiesOfClass(Player.class, box);
        for (Player player : players) {
            player.displayClientMessage(message, true);
        }
    }

    private void setActive(boolean newActive) {
        if (this.active != newActive) {
            this.active = newActive;
            if (level != null) {
                BlockState state = level.getBlockState(worldPosition);
                if (state.hasProperty(EliteBeaconBlock.ACTIVE)) {
                    level.setBlock(worldPosition, state.setValue(EliteBeaconBlock.ACTIVE, newActive), 3);
                }
            }
            setChanged();
        }
    }

    /**
     * Check if there is a nearby Heat Collector.
     * HeatCollector was removed in v0.2.0; always returns true for backward compat.
     */
    public boolean hasNearbyHeatCollector() {
        return true;
    }

    public int getSuppressionMode() {
        return suppressionMode;
    }

    public void setSuppressionMode(int mode) {
        this.suppressionMode = Math.max(0, Math.min(2, mode));
        setChanged();
    }

    public boolean isActive() {
        return active;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("SuppressionMode", suppressionMode);
        tag.putBoolean("Active", active);
        tag.putInt("TickCounter", tickCounter);
        tag.putInt("CheckLinkTimer", checkLinkTimer);
        if (hasHeatSource) {
            // HeatCollector block was removed in v0.2.0 phase 1; only the boolean
            // link flag is persisted (the LinkedX/Y/Z position fields no longer exist).
            tag.putBoolean("HasLinkedCollector", true);
        } else {
            tag.putBoolean("HasLinkedCollector", false);
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        suppressionMode = tag.getInt("SuppressionMode");
        active = tag.getBoolean("Active");
        tickCounter = tag.getInt("TickCounter");
        checkLinkTimer = tag.getInt("CheckLinkTimer");
        if (tag.getBoolean("HasLinkedCollector")) {
            hasHeatSource = true;
        } else {
            hasHeatSource = false;
        }
    }
}
