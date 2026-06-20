package com.eliteforge.render;

import com.eliteforge.EliteForge;
import com.eliteforge.capability.EliteCapability;
import com.eliteforge.capability.EliteData;
import com.eliteforge.config.EliteForgeConfig;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/**
 * CreatorAuraRenderer — renders the distinctive visual aura of creator-tier elites.
 * <p>
 * Creator-tier elites are the highest tier in EliteForge (see HANDOVER.md §6).
 * Previously they were only visually distinguished by a DARK_RED+BOLD name plate
 * and the MYTHIC quality glow effect. This class adds two additional cosmetic
 * effects to make creator-tier elites immediately recognizable:
 * <ul>
 *   <li><b>Pulsing red aura</b>: a translucent red vertical cylinder surrounding
 *       the entity, pulsing in opacity based on the game tick count.</li>
 *   <li><b>Ground ring</b>: a flat red ring on the ground beneath the entity,
 *       expanding and contracting to draw attention.</li>
 *   <li><b>Ascending flame particles</b>: occasional soul/flame particles rising
 *       along the aura cylinder.</li>
 * </ul>
 * <p>
 * <b>Design notes:</b>
 * <ul>
 *   <li>All rendering is client-side only ({@code Dist.CLIENT}).</li>
 *   <li>Controlled by {@link EliteForgeConfig.Client#showCreatorAura} (default true).</li>
 *   <li>Uses {@link RenderLivingEvent.Post} to render after the entity model so
 *       the aura is drawn on top.</li>
 *   <li>Performance: early-outs when the config is disabled, the entity is not
 *       a creator-tier elite, or the player is too far away.</li>
 *   <li>Does not modify any existing rendering code — this is a pure additive
 *       {@code @Mod.EventBusSubscriber} that hooks into Forge's render pipeline.</li>
 * </ul>
 * <p>
 * <b>Why a separate class:</b> The existing {@link EliteRenderHandler} already
 * handles name plates, ability icons, and particles. Creator aura is a distinct
 * visual concept (tier-based rather than ability-based) and is kept separate
 * for clarity and to avoid coupling to the existing handler's internal state.
 */
@Mod.EventBusSubscriber(modid = EliteForge.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class CreatorAuraRenderer {

    /** Aura radius in blocks (cylinder half-width). Slightly larger than the entity. */
    private static final float AURA_RADIUS = 1.2f;
    /** Aura height multiplier relative to entity bounding box height. */
    private static final float AURA_HEIGHT_MULT = 1.1f;
    /** Ring inner radius multiplier (relative to AURA_RADIUS). */
    private static final float RING_INNER = 0.85f;
    /** Ring outer radius multiplier (relative to AURA_RADIUS). */
    private static final float RING_OUTER = 1.15f;
    /** Pulse speed (radians per tick). Controls how fast the aura pulses. */
    private static final float PULSE_SPEED = 0.15f;
    /** Minimum opacity (trough of the pulse). */
    private static final float PULSE_MIN = 0.10f;
    /** Maximum opacity (peak of the pulse). */
    private static final float PULSE_MAX = 0.35f;
    /** Particle spawn rate (1 particle every N ticks). */
    private static final int PARTICLE_RATE = 5;
    /** Maximum render distance (squared) for the aura. */
    private static final double MAX_RENDER_DIST_SQ = 32.0 * 32.0;

    /**
     * Render the creator aura after the entity itself has been rendered.
     * <p>
     * Uses {@link RenderLivingEvent.Post} to ensure the aura is drawn on top of
     * the entity model. The aura is rendered in world space relative to the
     * entity's interpolated position (the pose stack from the event is already
     * positioned at the entity's render origin).
     *
     * @param event the post-render event
     */
    @SubscribeEvent
    public static void onRenderLivingPost(RenderLivingEvent.Post<?, ?> event) {
        if (!EliteForgeConfig.CLIENT.showCreatorAura.get() ||
            !com.eliteforge.config.EliteForgeConfig.SERVER.enableCreatorAura.get()) {
            return;
        }

        LivingEntity entity = event.getEntity();
        if (entity == null) {
            return;
        }

        // Only render on the client side
        if (!entity.level().isClientSide) {
            return;
        }

        // Check creator-tier status. Prefer the client-synced storage copy (safe to
        // read from the render thread) and only fall back to the live capability on
        // the integrated server before the first sync arrives. Without the storage
        // fallback the aura never renders on dedicated clients.
        EliteData data = com.eliteforge.capability.EliteCapabilityStorage.getEliteData(entity);
        if (data == null) {
            data = entity.getCapability(EliteCapability.CAPABILITY)
                    .map(EliteCapability::getEliteData)
                    .orElse(null);
        }
        if (data == null || !data.isCreatorEntity()) {
            return;
        }

        // Distance culling: skip if the player is too far away
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        double distSq = mc.player.distanceToSqr(entity);
        if (distSq > MAX_RENDER_DIST_SQ) {
            return;
        }

        // Compute the pulse opacity: sinusoidal between PULSE_MIN and PULSE_MAX
        float partialTick = event.getPartialTick();
        long tickCount = entity.tickCount;
        float pulsePhase = (tickCount + partialTick) * PULSE_SPEED;
        float pulse = (Mth.sin(pulsePhase) + 1.0f) * 0.5f; // 0.0 to 1.0
        float opacity = PULSE_MIN + pulse * (PULSE_MAX - PULSE_MIN);

        float radius = AURA_RADIUS;
        float height = (float) entity.getBbHeight() * AURA_HEIGHT_MULT;

        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource bufferSource = event.getMultiBufferSource();

        poseStack.pushPose();
        renderCylinder(poseStack, bufferSource, radius, height, opacity, pulse);
        renderGroundRing(poseStack, bufferSource, radius, opacity, pulse);
        poseStack.popPose();

        // Spawn occasional ascending particles (purely cosmetic)
        if (tickCount % PARTICLE_RATE == 0) {
            spawnAuraParticles(entity, radius, height);
        }
    }

    /**
     * Render a translucent red cylinder (vertical tube) around the entity.
     * Uses quad strips to form a hollow cylinder.
     * <p>
     * Uses {@link RenderType#lightning()} which is a known translucent render
     * type in 1.20.1 that renders without writing to the depth buffer, ensuring
     * the aura is visible through the entity.
     *
     * @param poseStack     the pose stack (already positioned at the entity)
     * @param bufferSource  the multi-buffer source
     * @param radius        the cylinder radius
     * @param height        the cylinder height
     * @param opacity       the base opacity (0.0 - 1.0)
     * @param pulse         the current pulse value (0.0 - 1.0)
     */
    private static void renderCylinder(PoseStack poseStack, MultiBufferSource bufferSource,
                                       float radius, float height, float opacity, float pulse) {
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.lightning());
        PoseStack.Pose pose = poseStack.last();
        Matrix4f matrix = pose.pose();

        int segments = 16;
        float red = 0.8f + pulse * 0.2f;  // 0.8 - 1.0
        float green = 0.05f;
        float blue = 0.05f;
        float alpha = opacity;

        for (int i = 0; i < segments; i++) {
            float angle1 = (i / (float) segments) * Mth.TWO_PI;
            float angle2 = ((i + 1) / (float) segments) * Mth.TWO_PI;

            float x1 = Mth.cos(angle1) * radius;
            float z1 = Mth.sin(angle1) * radius;
            float x2 = Mth.cos(angle2) * radius;
            float z2 = Mth.sin(angle2) * radius;

            // Quad: bottom-right, bottom-left, top-left, top-right (CCW winding)
            consumer.vertex(matrix, x1, 0, z1).color(red, green, blue, alpha).normal(pose.normal(), 0, 1, 0).endVertex();
            consumer.vertex(matrix, x2, 0, z2).color(red, green, blue, alpha).normal(pose.normal(), 0, 1, 0).endVertex();
            consumer.vertex(matrix, x2, height, z2).color(red, green, blue, alpha).normal(pose.normal(), 0, 1, 0).endVertex();
            consumer.vertex(matrix, x1, height, z1).color(red, green, blue, alpha).normal(pose.normal(), 0, 1, 0).endVertex();
        }
    }

    /**
     * Render a flat ring on the ground beneath the entity.
     * The ring expands and contracts slightly with the pulse.
     *
     * @param poseStack     the pose stack (already positioned at the entity)
     * @param bufferSource  the multi-buffer source
     * @param radius        the base radius
     * @param opacity       the base opacity (0.0 - 1.0)
     * @param pulse         the current pulse value (0.0 - 1.0)
     */
    private static void renderGroundRing(PoseStack poseStack, MultiBufferSource bufferSource,
                                         float radius, float opacity, float pulse) {
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.lightning());
        PoseStack.Pose pose = poseStack.last();
        Matrix4f matrix = pose.pose();

        float innerR = radius * RING_INNER;
        float outerR = radius * RING_OUTER * (1.0f + pulse * 0.1f); // slight expansion on pulse peak

        int segments = 32;
        float red = 1.0f;
        float green = 0.1f;
        float blue = 0.1f;
        float alpha = Math.min(1.0f, opacity * 1.5f); // ring is more visible than the cylinder

        for (int i = 0; i < segments; i++) {
            float angle1 = (i / (float) segments) * Mth.TWO_PI;
            float angle2 = ((i + 1) / (float) segments) * Mth.TWO_PI;

            float ix1 = Mth.cos(angle1) * innerR;
            float iz1 = Mth.sin(angle1) * innerR;
            float ix2 = Mth.cos(angle2) * innerR;
            float iz2 = Mth.sin(angle2) * innerR;

            float ox1 = Mth.cos(angle1) * outerR;
            float oz1 = Mth.sin(angle1) * outerR;
            float ox2 = Mth.cos(angle2) * outerR;
            float oz2 = Mth.sin(angle2) * outerR;

            // Quad: inner-1, inner-2, outer-2, outer-1
            consumer.vertex(matrix, ix1, 0.02f, iz1).color(red, green, blue, alpha).normal(pose.normal(), 0, 1, 0).endVertex();
            consumer.vertex(matrix, ix2, 0.02f, iz2).color(red, green, blue, alpha).normal(pose.normal(), 0, 1, 0).endVertex();
            consumer.vertex(matrix, ox2, 0.02f, oz2).color(red, green, blue, alpha).normal(pose.normal(), 0, 1, 0).endVertex();
            consumer.vertex(matrix, ox1, 0.02f, oz1).color(red, green, blue, alpha).normal(pose.normal(), 0, 1, 0).endVertex();
        }
    }

    /**
     * Spawn ascending flame and soul particles along the aura cylinder.
     * Called periodically (every {@link #PARTICLE_RATE} ticks).
     *
     * @param entity the creator-tier entity
     * @param radius the aura radius
     * @param height the aura height
     */
    private static void spawnAuraParticles(LivingEntity entity, float radius, float height) {
        double angle = entity.tickCount * 0.3; // rotate around the entity
        for (int i = 0; i < 2; i++) {
            double a = angle + (i * Math.PI);
            double x = entity.getX() + Math.cos(a) * radius;
            double z = entity.getZ() + Math.sin(a) * radius;
            double y = entity.getY() + entity.getRandom().nextDouble() * height;

            entity.level().addParticle(ParticleTypes.FLAME, x, y, z,
                    0.0, 0.05, 0.0);
        }
        // Occasional soul particle for dramatic effect
        if (entity.tickCount % (PARTICLE_RATE * 4) == 0) {
            double x = entity.getX() + (entity.getRandom().nextDouble() - 0.5) * radius * 2;
            double z = entity.getZ() + (entity.getRandom().nextDouble() - 0.5) * radius * 2;
            entity.level().addParticle(ParticleTypes.SOUL_FIRE_FLAME,
                    x, entity.getY() + 0.5, z,
                    0.0, 0.1, 0.0);
        }
    }
}
