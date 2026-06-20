package com.eliteforge.network;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Packet for syncing chunk heat value from server to client.
 * Sent when a player enters a new chunk or when chunk heat changes significantly.
 *
 * The client uses this data to render the heat overlay on the HUD
 * (if enabled in client config) and for visual feedback near heat sources.
 */
public class S2CChunkHeatSync {

    private int chunkX;
    private int chunkZ;
    private double heatValue;
    private double maxHeat;

    /**
     * Create a new chunk heat sync packet.
     *
     * @param chunkX    The X coordinate of the chunk
     * @param chunkZ    The Z coordinate of the chunk
     * @param heatValue The current heat value of the chunk
     * @param maxHeat   The maximum heat value (from config)
     */
    public S2CChunkHeatSync(int chunkX, int chunkZ, double heatValue, double maxHeat) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.heatValue = heatValue;
        this.maxHeat = maxHeat;
    }

    /**
     * Decode a packet from the network buffer.
     */
    public S2CChunkHeatSync(FriendlyByteBuf buf) {
        this.chunkX = buf.readVarInt();
        this.chunkZ = buf.readVarInt();
        this.heatValue = buf.readDouble();
        this.maxHeat = buf.readDouble();
    }

    /**
     * Encode the packet to the network buffer.
     */
    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(chunkX);
        buf.writeVarInt(chunkZ);
        buf.writeDouble(heatValue);
        buf.writeDouble(maxHeat);
    }

    /**
     * Handle the packet on the client side.
     * Stores the chunk heat data in a client-side cache for overlay rendering.
     */
    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            // Store chunk heat data for client-side overlay rendering
            ChunkHeatClientCache.updateHeat(chunkX, chunkZ, heatValue, maxHeat);
        });
        context.setPacketHandled(true);
    }

    // ========================================================================
    // Getters
    // ========================================================================

    public int getChunkX() {
        return chunkX;
    }

    public int getChunkZ() {
        return chunkZ;
    }

    public double getHeatValue() {
        return heatValue;
    }

    public double getMaxHeat() {
        return maxHeat;
    }

    /**
     * Get the heat as a percentage (0.0 to 1.0).
     */
    public double getHeatPercentage() {
        return maxHeat > 0 ? heatValue / maxHeat : 0.0;
    }

    // ========================================================================
    // Client-Side Cache
    // ========================================================================

    /**
     * Client-side cache for chunk heat values.
     * Used by the HUD overlay to display current chunk heat.
     */
    public static class ChunkHeatClientCache {

        private static int currentChunkX = 0;
        private static int currentChunkZ = 0;
        private static double currentHeat = 0.0;
        private static double currentMaxHeat = 100.0;

        /**
         * Update the cached heat value for a chunk.
         */
        public static void updateHeat(int chunkX, int chunkZ, double heat, double maxHeat) {
            currentChunkX = chunkX;
            currentChunkZ = chunkZ;
            currentHeat = heat;
            currentMaxHeat = maxHeat;
        }

        /**
         * Get the cached heat value for the player's current chunk.
         */
        public static double getCurrentHeat() {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                int playerChunkX = mc.player.blockPosition().getX() >> 4;
                int playerChunkZ = mc.player.blockPosition().getZ() >> 4;
                if (playerChunkX == currentChunkX && playerChunkZ == currentChunkZ) {
                    return currentHeat;
                }
            }
            return 0.0;
        }

        /**
         * Get the maximum heat value from the last received packet.
         */
        public static double getCurrentMaxHeat() {
            return currentMaxHeat;
        }

        /**
         * Get the heat as a percentage for the current chunk.
         */
        public static double getCurrentHeatPercentage() {
            return currentMaxHeat > 0 ? currentHeat / currentMaxHeat : 0.0;
        }
    }
}
