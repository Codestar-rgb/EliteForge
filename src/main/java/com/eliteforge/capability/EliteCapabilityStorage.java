package com.eliteforge.capability;

import com.eliteforge.EliteForge;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side storage for elite entity data. Maintains a map of entity IDs to
 * EliteData instances for use in rendering (glow effects, name plates, etc.).
 * <p>
 * Data is populated from S2CEliteDataSyncPacket messages and cleared on
 * dimension change or disconnect.
 */
@Mod.EventBusSubscriber(modid = EliteForge.MODID, value = Dist.CLIENT)
public class EliteCapabilityStorage {

    private static final Map<Integer, EliteData> ELITE_DATA_MAP = new ConcurrentHashMap<>();

    /** Tick counter for throttling cleanup. Cleanup runs every 100 ticks (~5 seconds). */
    private static int cleanupTickCounter = 0;
    private static final int CLEANUP_INTERVAL = 100;

    /**
     * Get the elite data for a given entity ID.
     *
     * @param entityId the client-side entity ID
     * @return the EliteData, or null if no data is stored for this entity
     */
    public static EliteData getEliteData(int entityId) {
        return ELITE_DATA_MAP.get(entityId);
    }

    /**
     * Get the elite data for a given entity.
     *
     * @param entity the entity to look up
     * @return the EliteData, or null if no data is stored for this entity
     */
    public static EliteData getEliteData(Entity entity) {
        if (entity == null) {
            return null;
        }
        return ELITE_DATA_MAP.get(entity.getId());
    }

    /**
     * Update or set the elite data for a given entity ID.
     *
     * @param entityId the client-side entity ID
     * @param data     the elite data to store
     */
    public static void updateEliteData(int entityId, EliteData data) {
        if (data == null) {
            ELITE_DATA_MAP.remove(entityId);
            return;
        }
        ELITE_DATA_MAP.put(entityId, data);
    }

    /**
     * Remove elite data for a given entity ID.
     *
     * @param entityId the client-side entity ID
     */
    public static void removeEliteData(int entityId) {
        ELITE_DATA_MAP.remove(entityId);
    }

    /**
     * Check if an entity has elite data stored.
     *
     * @param entityId the client-side entity ID
     * @return true if elite data exists for this entity
     */
    public static boolean hasEliteData(int entityId) {
        return ELITE_DATA_MAP.containsKey(entityId);
    }

    /**
     * Get an unmodifiable view of all stored elite data.
     *
     * @return unmodifiable map of entity ID to EliteData
     */
    public static Map<Integer, EliteData> getAllEliteData() {
        return Collections.unmodifiableMap(ELITE_DATA_MAP);
    }

    /**
     * Clear all stored elite data. Called on dimension change or disconnect.
     */
    public static void clearAll() {
        ELITE_DATA_MAP.clear();
    }

    /**
     * Clear data when the player disconnects from a server.
     *
     * @param event the logged-out event
     */
    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        clearAll();
    }

    /**
     * Clear data when the player logs in (fresh state).
     *
     * @param event the logged-in event
     */
    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        clearAll();
    }

    /**
     * Clear data when the player respawns (death, or returning from the End).
     * The client will re-receive data for tracked entities as the new player starts
     * tracking them.
     *
     * @param event the respawn event
     */
    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        clearAll();
    }

    /**
     * Clear data when the player changes dimension. Entity IDs are NOT stable across
     * dimensions on the client, and the server re-sends tracking data for the new
     * dimension's entities anyway — so stale entries from the old dimension must be
     * purged to avoid rendering overlays for entities that no longer exist client-side.
     *
     * @param event the dimension-change event
     */
    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        clearAll();
    }

    /**
     * Client tick event handler that periodically cleans up stale entries.
     * Runs on END phase to avoid interfering with tick processing.
     *
     * @param event the client tick event
     */
    @SubscribeEvent
    public static void onClientTick(net.minecraftforge.event.TickEvent.ClientTickEvent event) {
        if (event.phase == net.minecraftforge.event.TickEvent.Phase.END) {
            cleanupTickCounter++;
            if (cleanupTickCounter >= CLEANUP_INTERVAL) {
                cleanupTickCounter = 0;
                tickCleanup();
            }
        }
    }

    /**
     * Periodically clean up stale entries by checking if entities still exist.
     * Throttled to run every {@link #CLEANUP_INTERVAL} ticks (~5 seconds) to avoid
     * expensive per-tick iteration and {@code Minecraft.getInstance()} calls.
     */
    public static void tickCleanup() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            clearAll();
            return;
        }

        // Remove entries for entities that no longer exist
        ELITE_DATA_MAP.entrySet().removeIf(entry -> {
            Entity entity = mc.level.getEntity(entry.getKey());
            if (entity == null) {
                return true;
            }
            // Also remove if the entity is no longer elite (data was cleared)
            EliteData data = entry.getValue();
            return data == null || !data.isElite();
        });
    }
}
