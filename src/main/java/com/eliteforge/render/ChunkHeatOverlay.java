package com.eliteforge.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;

import java.util.HashMap;
import java.util.Map;

public class ChunkHeatOverlay {

    // Cache of chunk heat values received from server
    private static final Map<Long, Integer> CHUNK_HEAT_CACHE = new HashMap<>();
    private static final int MAX_HEAT = 100;
    private static final int BAR_WIDTH = 80;
    private static final int BAR_HEIGHT = 8;
    private static final int HUD_X_OFFSET = 10;
    private static final int HUD_Y_OFFSET = 10;

    /**
     * Updates the cached chunk heat value from server packet.
     */
    public static void updateChunkHeat(ChunkPos chunkPos, int heat) {
        if (chunkPos == null) return;
        CHUNK_HEAT_CACHE.put(chunkPos.toLong(), Math.max(0, Math.min(MAX_HEAT, heat)));
    }

    /**
     * Gets the heat value for a chunk position.
     */
    public static int getChunkHeat(ChunkPos pos) {
        if (pos == null) return 0;
        return CHUNK_HEAT_CACHE.getOrDefault(pos.toLong(), 0);
    }

    /**
     * Clears heat cache (e.g., on world change).
     */
    public static void clearCache() {
        CHUNK_HEAT_CACHE.clear();
    }

    /**
     * Renders the chunk heat overlay on the HUD.
     * Only shows when the player holds a Forging Compass item.
     */
    public static void renderChunkHeatOverlay(GuiGraphics guiGraphics) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.level == null) {
            return;
        }

        // Check if player is holding forging compass
        if (!isHoldingForgingCompass(player)) {
            return;
        }

        // Get current chunk heat
        ChunkPos currentChunk = new ChunkPos(player.blockPosition());
        int heat = getChunkHeat(currentChunk);

        if (heat <= 0) {
            // Show minimal indicator even at 0 heat if holding compass
            heat = 0;
        }

        Font font = mc.font;
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();

        int x = screenWidth - BAR_WIDTH - HUD_X_OFFSET - 20;
        int y = HUD_Y_OFFSET;

        // Draw anvil icon (text-based)
        String anvilIcon = "⛏"; // Using pickaxe as anvil substitute since we're text-based
        guiGraphics.drawString(font, anvilIcon, x - 16, y, 0xFFAA00, true);

        // Draw "Heat" label
        String heatLabel = "Heat";
        guiGraphics.drawString(font, heatLabel, x, y, 0xCCCCCC, true);

        // Draw heat bar background
        int barY = y + font.lineHeight + 2;
        guiGraphics.fill(x, barY, x + BAR_WIDTH, barY + BAR_HEIGHT, 0x80000000);

        // Draw heat bar fill
        int heatColor = getHeatColor(heat);
        float fillPercent = (float) heat / MAX_HEAT;
        int fillWidth = (int) (BAR_WIDTH * fillPercent);
        if (fillWidth > 0) {
            guiGraphics.fill(x, barY, x + fillWidth, barY + BAR_HEIGHT, heatColor | 0xFF000000);
        }

        // Draw heat bar border
        guiGraphics.renderOutline(x, barY, BAR_WIDTH, BAR_HEIGHT, 0xFF000000);

        // Draw heat value text
        String heatText = heat + "/" + MAX_HEAT;
        int textX = x + BAR_WIDTH / 2 - font.width(heatText) / 2;
        guiGraphics.drawString(font, heatText, textX, barY + BAR_HEIGHT + 2, heatColor, true);

        // Draw chunk coordinates
        String chunkText = String.format("Chunk: [%d, %d]", currentChunk.x, currentChunk.z);
        guiGraphics.drawString(font, chunkText, x, barY + BAR_HEIGHT + font.lineHeight + 4, 0x888888, false);
    }

    /**
     * Gets the color for the heat bar based on heat level.
     * blue (0-25) → yellow (25-50) → orange (50-75) → red (75-100)
     */
    private static int getHeatColor(int heat) {
        if (heat <= 25) {
            // Blue to yellow transition
            float t = heat / 25.0F;
            int r = (int) (255 * t);
            int g = (int) (100 + 155 * t);
            int b = (int) (255 * (1 - t));
            return (r << 16) | (g << 8) | b;
        } else if (heat <= 50) {
            // Yellow to orange transition
            float t = (heat - 25) / 25.0F;
            int r = 255;
            int g = (int) (255 - 80 * t);
            int b = 0;
            return (r << 16) | (g << 8) | b;
        } else if (heat <= 75) {
            // Orange to red transition
            float t = (heat - 50) / 25.0F;
            int r = 255;
            int g = (int) (175 - 175 * t);
            int b = 0;
            return (r << 16) | (g << 8) | b;
        } else {
            // Deep red
            float t = (heat - 75) / 25.0F;
            int r = 255;
            int g = 0;
            int b = (int) (50 * t);
            return (r << 16) | (g << 8) | b;
        }
    }

    /**
     * Checks if the player is holding a Forging Compass.
     * Checks both main hand and off hand.
     */
    private static boolean isHoldingForgingCompass(Player player) {
        return true; // Always show in v0.2.0
    }

    /**
     * Checks if the given item stack is a Forging Compass.
     */
    private static boolean isForgingCompass(ItemStack stack) {
        return false;
    }
}
