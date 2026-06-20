package com.eliteforge.network;

import com.eliteforge.EliteForge;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;


/**
 * Network handler for EliteForge.
 * Registers and manages all network packets using Forge's SimpleChannel system.
 *
 * Protocol version is checked between client and server to ensure compatibility.
 * All packets in this channel are server-to-client (S2C) for syncing data
 * needed for rendering and client-side display.
 */
public class NetworkHandler {

    // Wire-format version. Bumped to "2" when the explicit isElite flag + owner-link UUIDs
    // were added to S2CEliteDataSync. Old clients ("1") will fail the version check and
    // be refused — acceptable for a single-player mod, but the version must track the
    // wire format going forward, not the feature set.
    private static final String PROTOCOL_VERSION = "2";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(EliteForge.MODID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private static int packetId = 0;

    private static int nextId() {
        return packetId++;
    }

    /**
     * Register all network packets.
     * Must be called during mod construction.
     */
    public static void register() {
        // S2CEliteDataSync - Syncs elite entity data to client for rendering
        CHANNEL.messageBuilder(S2CEliteDataSync.class, nextId(), NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2CEliteDataSync::encode)
                .decoder(S2CEliteDataSync::new)
                .consumerMainThread(S2CEliteDataSync::handle)
                .add();

        // S2CChunkHeatSync - Syncs chunk heat value to client for overlay.
        // The decoder is a constructor reference (the (FriendlyByteBuf) ctor above)
        // since there is no static decode(...) factory method on the message class.
        CHANNEL.messageBuilder(S2CChunkHeatSync.class, nextId(), NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2CChunkHeatSync::encode)
                .decoder(S2CChunkHeatSync::new)
                .consumerMainThread(S2CChunkHeatSync::handle)
                .add();

        // S2CParticleEvent - Spawns particles on client.
        // Same pattern: use the (FriendlyByteBuf) ctor as the decoder.
        CHANNEL.messageBuilder(S2CParticleEvent.class, nextId(), NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2CParticleEvent::encode)
                .decoder(S2CParticleEvent::new)
                .consumerMainThread(S2CParticleEvent::handle)
                .add();
    }

    /**
     * Send a packet to all tracking clients of an entity.
     */
    public static void sendToTracking(Object message, net.minecraft.world.entity.Entity entity) {
        CHANNEL.send(net.minecraftforge.network.PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> entity), message);
    }

    /**
     * Send a packet to a specific player.
     */
    public static void sendToPlayer(Object message, net.minecraft.server.level.ServerPlayer player) {
        CHANNEL.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player), message);
    }

    /**
     * Send a packet to all players in a dimension.
     */
    public static void sendToDimension(Object message, net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension) {
        CHANNEL.send(net.minecraftforge.network.PacketDistributor.DIMENSION.with(() -> dimension), message);
    }

    /**
     * Send a packet to all connected players.
     */
    public static void sendToAll(Object message) {
        CHANNEL.send(net.minecraftforge.network.PacketDistributor.ALL.noArg(), message);
    }

    /**
     * Send a packet to all players tracking a chunk.
     */
    public static void sendToTrackingChunk(Object message, net.minecraft.world.level.chunk.LevelChunk chunk) {
        CHANNEL.send(net.minecraftforge.network.PacketDistributor.TRACKING_CHUNK.with(() -> chunk), message);
    }
}
