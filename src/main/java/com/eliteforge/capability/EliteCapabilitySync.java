package com.eliteforge.capability;

import com.eliteforge.network.NetworkHandler;
import com.eliteforge.network.S2CEliteDataSync;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.player.PlayerEvent;

/**
 * Handles syncing capability data from server to client.
 * Sends S2CEliteDataSync packets when:
 * <ul>
 *   <li>A player starts tracking an elite entity</li>
 *   <li>Capability data changes on an already-tracked elite</li>
 * </ul>
 * Client-side storage is managed by {@link EliteCapabilityStorage}.
 *
 * NOTE: This class is NOT registered via @Mod.EventBusSubscriber.
 * The onPlayerStartTracking method is called by EliteEventHandler which
 * IS registered. This avoids duplicate event handling.
 */
public class EliteCapabilitySync {

    /**
     * Called when a player starts tracking an entity. If the entity is elite,
     * sends the capability data to the tracking player's client.
     * Called by {@link com.eliteforge.spawn.EliteEventHandler#onPlayerStartTracking}.
     *
     * @param event the start tracking event
     */
    public static void onPlayerStartTracking(PlayerEvent.StartTracking event) {
        if (event.getEntity().level().isClientSide()) {
            return;
        }

        Entity target = event.getTarget();
        if (!(target instanceof LivingEntity livingTarget)) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer serverPlayer)) {
            return;
        }

        livingTarget.getCapability(EliteCapability.CAPABILITY).ifPresent(cap -> {
            EliteData data = cap.getEliteData();
            // Sync if the entity is elite OR is a summon (has summonerUUID set).
            // Summons (Necromancy undead) are not marked elite but still need their
            // summonerUUID on the client for purple chain rendering.
            if (cap.isElite() || data.getSummonerUUID() != null) {
                sendEliteDataToPlayer(serverPlayer, livingTarget, data);
            }
        });
    }

    /**
     * Send elite data for a specific entity to a specific player.
     *
     * @param player the server player to send to
     * @param entity the elite entity
     * @param data   the elite data to sync
     */
    public static void sendEliteDataToPlayer(ServerPlayer player, LivingEntity entity, EliteData data) {
        if (player == null || entity == null || data == null) {
            return;
        }
        S2CEliteDataSync packet = new S2CEliteDataSync(
                entity.getId(),
                data.getAbilities(),
                data.getQualityTier(),
                data.getLevel(),
                data.isElite(),
                data.isCreatorEntity(),
                data.getCreatorAbilityId(),
                data.getCreatorAbilityLevel(),
                data.getSummonerUUID(),
                data.getCommanderUUID(),
                data.getNexusSourceUUID(),
                data.getBestowerUUID()
        );
        NetworkHandler.sendToPlayer(packet, player);
    }

    /**
     * Send elite data for a specific entity to all tracking players.
     * Called when capability data changes.
     *
     * @param entity the elite entity whose data changed
     * @param data   the updated elite data
     */
    public static void broadcastEliteDataUpdate(LivingEntity entity, EliteData data) {
        if (entity == null || data == null || entity.level().isClientSide()) {
            return;
        }
        if (entity.level().getServer() == null) {
            return;
        }

        S2CEliteDataSync packet = new S2CEliteDataSync(
                entity.getId(),
                data.getAbilities(),
                data.getQualityTier(),
                data.getLevel(),
                data.isElite(),
                data.isCreatorEntity(),
                data.getCreatorAbilityId(),
                data.getCreatorAbilityLevel(),
                data.getSummonerUUID(),
                data.getCommanderUUID(),
                data.getNexusSourceUUID(),
                data.getBestowerUUID()
        );

        NetworkHandler.sendToTracking(packet, entity);
    }

    /**
     * Send an entity removal notification to clients when an elite entity is removed
     * or stops being elite.
     *
     * @param entity the entity that is no longer elite
     */
    public static void broadcastEliteRemoval(LivingEntity entity) {
        if (entity == null || entity.level().isClientSide()) {
            return;
        }
        if (entity.level().getServer() == null) {
            return;
        }

        // Send a packet with isElite=false + empty data to signal removal. Uses the
        // explicit isElite constructor so the client clears the EliteCapabilityStorage
        // entry's elite flag regardless of any stale level value.
        S2CEliteDataSync packet = new S2CEliteDataSync(
                entity.getId(),
                new java.util.HashMap<>(),
                com.eliteforge.quality.QualityTier.NORMAL,
                0,
                /*isElite*/ false,
                false, null, 0,
                null, null, null, null
        );

        NetworkHandler.sendToTracking(packet, entity);
    }
}
