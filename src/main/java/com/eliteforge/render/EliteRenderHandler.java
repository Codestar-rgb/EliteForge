package com.eliteforge.render;

import com.eliteforge.EliteForge;
import com.eliteforge.capability.EliteCapability;
import com.eliteforge.capability.EliteData;
import com.eliteforge.config.EliteForgeConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.event.RenderNameTagEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Client-side render handler for EliteForge.
 * Handles elite name plates, ability icons, chunk heat overlay,
 * and particle effect rendering.
 *
 * Auto-registered via @Mod.EventBusSubscriber on the CLIENT Forge bus.
 */
@Mod.EventBusSubscriber(modid = EliteForge.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class EliteRenderHandler {

    private static final Minecraft MC = Minecraft.getInstance();
    private static final org.apache.logging.log4j.Logger LOGGER = org.apache.logging.log4j.LogManager.getLogger();

    private static LivingEntity cachedNearestElite = null;
    private static long lastCacheUpdate = 0;

    /**
     * Handle elite name plate rendering.
     * Replaces the default name plate with a custom one showing
     * the elite's quality tier, level, and health bar.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRenderNameTag(RenderNameTagEvent event) {
        if (!EliteForgeConfig.CLIENT.showEliteNamePlate.get()) {
            return;
        }

        Entity entity = event.getEntity();
        if (!(entity instanceof LivingEntity livingEntity)) {
            return;
        }

        // Check if entity is elite using capability, then fall back to storage.
        // On the integrated server the live capability holds the SERVER-side EliteData
        // (mutated by the server thread); reading it from the render thread can throw
        // ConcurrentModificationException on the abilities map. So always prefer the
        // client-synced EliteCapabilityStorage copy.
        EliteData data = com.eliteforge.capability.EliteCapabilityStorage.getEliteData(livingEntity);
        if (data == null || !data.isElite()) {
            // Fall back to the live capability (single-player integrated server where the
            // storage may not yet have been populated for this entity).
            EliteCapability cap = livingEntity.getCapability(EliteCapability.CAPABILITY).orElse(null);
            if (cap != null && cap.isElite()) {
                data = cap.getEliteData();
            }
        }
        if (data == null || !data.isElite()) {
            return;
        }

        // Cancel vanilla name plate rendering - we handle it ourselves
        event.setCanceled(true);

        // Render custom elite name plate (wrapped in try-catch to prevent
        // rendering crashes from taking down the entire client)
        try {
            EliteNameRenderer.renderEliteNamePlate(
                    event.getPoseStack(),
                    event.getMultiBufferSource(),
                    livingEntity,
                    data,
                    event.getPackedLight()
            );
        } catch (Exception e) {
            // Log but don't crash — rendering errors should be non-fatal
            LOGGER.error("Error rendering elite name plate: {}", e.getMessage());
        }
    }

    /**
     * Handle living entity rendering for ability icons and ambient particles.
     */
    @SubscribeEvent
    public static void onRenderLivingPre(RenderLivingEvent.Pre<?, ?> event) {
        // Resolve EliteData ONCE: prefer the client-synced storage copy (safe to read
        // from the render thread) and only fall back to the live capability on the
        // integrated server before the first sync arrives.
        LivingEntity entity = event.getEntity();
        EliteData data = com.eliteforge.capability.EliteCapabilityStorage.getEliteData(entity);
        if (data == null || !data.isElite()) {
            data = entity.getCapability(EliteCapability.CAPABILITY)
                    .map(EliteCapability::getEliteData)
                    .orElse(null);
        }
        if (data == null || !data.isElite()) {
            return;
        }

        // Render ability icons above the entity (wrapped in try-catch)
        if (EliteForgeConfig.CLIENT.showAbilityIcons.get()) {
            try {
                AbilityIconRenderer.renderAbilityIcons(
                        event.getPoseStack(),
                        event.getMultiBufferSource(),
                        entity,
                        data,
                        event.getPackedLight()
                );
            } catch (Exception e) {
                LOGGER.error("Error rendering ability icons: {}", e.getMessage());
            }
        }

        // Spawn ambient particles around elite entity (client-side only)
        // Previously gated by showAbilityIcons; now correctly gated by showAbilityParticles.
        if (EliteForgeConfig.CLIENT.showAbilityParticles.get()) {
            try {
                EliteParticleRenderer.spawnAmbientParticles(entity, data);
            } catch (Exception e) {
                LOGGER.error("Error spawning ambient particles: {}", e.getMessage());
            }
        }
    }

    /**
     * Handle HUD overlay rendering for chunk heat display and elite direction indicator.
     */
    @SubscribeEvent
    public static void onRenderGuiOverlay(RenderGuiEvent.Post event) {
        if (MC.player == null || MC.level == null) {
            return;
        }

        GuiGraphics guiGraphics = event.getGuiGraphics();

        // Chunk heat text overlay is rendered by HeatOverlayRenderer (its own
        // @SubscribeEvent on the same RenderGuiEvent.Post). The old ChunkHeatOverlay
        // bar widget was removed — it read from a cache that was never populated,
        // so it always showed 0/100.

        // Render elite direction indicator
        renderEliteDirectionIndicator(guiGraphics);
    }

    /**
     * Render direction indicator pointing toward nearest elite.
     * Caches the nearest elite and only re-scans every 20 ticks (1 second)
     * to avoid iterating all entities every frame.
     */
    private static void renderEliteDirectionIndicator(GuiGraphics guiGraphics) {
        if (MC.player == null || MC.level == null) return;

        double renderDistSq = EliteForgeConfig.CLIENT.iconRenderDistance.get()
                * EliteForgeConfig.CLIENT.iconRenderDistance.get();

        // Update cache every 20 ticks (1 second)
        long gameTime = MC.level.getGameTime();
        if (gameTime - lastCacheUpdate >= 20 || cachedNearestElite == null || !cachedNearestElite.isAlive()) {
            lastCacheUpdate = gameTime;
            cachedNearestElite = null;
            double nearestDistSq = Double.MAX_VALUE;

            for (Entity entity : MC.level.entitiesForRendering()) {
                if (!(entity instanceof LivingEntity living)) continue;
                if (living == MC.player) continue;

                EliteData data = com.eliteforge.capability.EliteCapabilityStorage.getEliteData(living);
                if (data == null || !data.isElite()) {
                    data = living.getCapability(EliteCapability.CAPABILITY)
                            .map(EliteCapability::getEliteData)
                            .orElse(null);
                }
                if (data == null || !data.isElite()) continue;

                double distSq = MC.player.distanceToSqr(living);
                if (distSq < nearestDistSq && distSq <= renderDistSq) {
                    nearestDistSq = distSq;
                    cachedNearestElite = living;
                }
            }
        }

        if (cachedNearestElite != null) {
            EliteNameRenderer.renderDirectionIndicator(guiGraphics, MC.player, cachedNearestElite);
        }
    }
}
