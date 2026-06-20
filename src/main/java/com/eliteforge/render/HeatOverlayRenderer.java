package com.eliteforge.render;

import com.eliteforge.config.EliteForgeConfig;
import com.eliteforge.difficulty.ChunkHeatManager;
import com.eliteforge.difficulty.PlayerExperienceManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * HeatOverlayRenderer — displays chunk heat and player heat above the
 * player's hotbar/inventory area.
 * <p>
 * Format: "Chunk: 45 | Player: 320 | x0.9"
 * Colors: green (<30), yellow (30-60), red (>60)
 */
@Mod.EventBusSubscriber(modid = com.eliteforge.EliteForge.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class HeatOverlayRenderer {

    private static int tickCounter = 0;
    private static float cachedChunkHeat = 0;
    private static float cachedPlayerHeat = 0;

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        // Respect the client config flag — previously this overlay rendered unconditionally,
        // ignoring showHeatOverlay.
        if (!com.eliteforge.config.EliteForgeConfig.CLIENT.showHeatOverlay.get()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        // Update heat values every 20 ticks (1 second)
        tickCounter++;
        if (tickCounter >= 20) {
            tickCounter = 0;
            // On the client, mc.level is always a ClientLevel and can never be a
            // ServerLevel, so ChunkHeatManager (server-only) cannot be queried here.
            // The heat value is synced to the client via S2CChunkHeatSync and cached
            // in ChunkHeatClientCache; read it from there.
            cachedChunkHeat = (float) com.eliteforge.network.S2CChunkHeatSync.ChunkHeatClientCache.getCurrentHeat();
            // Player heat (experience)
            cachedPlayerHeat = 0; // Will be synced
        }

        // Render heat display above hotbar
        GuiGraphics g = event.getGuiGraphics();
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();

        // Position: center, just above hotbar
        int x = screenWidth / 2;
        int y = screenHeight - 70;

        // Colors based on heat level
        int chunkColor = cachedChunkHeat > 60 ? 0xFF5555 : cachedChunkHeat > 30 ? 0xFFFF55 : 0x55FF55;
        int playerColor = cachedPlayerHeat > 500 ? 0xFF5555 : cachedPlayerHeat > 200 ? 0xFFFF55 : 0x55FF55;
        float multiplier = cachedPlayerHeat > 0 ? (cachedPlayerHeat / 500.0f) : 1.0f;

        String text = String.format("Chunk: %.0f | Player: %.0f | x%.1f", cachedChunkHeat, cachedPlayerHeat, multiplier);

        // Draw with shadow
        g.drawString(mc.font, text, x - mc.font.width(text) / 2, y, 0xFFFFFFFF);
    }
}
