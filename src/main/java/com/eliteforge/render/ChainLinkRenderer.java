package com.eliteforge.render;

import com.eliteforge.EliteForge;
import com.eliteforge.capability.EliteCapability;
import com.eliteforge.capability.EliteCapabilityStorage;
import com.eliteforge.capability.EliteData;
import com.eliteforge.config.EliteForgeConfig;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.UUID;

/**
 * ChainLinkRenderer — renders a glowing purple chain between a summon (召唤物)
 * and its owner (本体), plus the same visual for the other master-servant
 * links in the mod (Nexus nurture, Commander squad, Bestowal empowerment).
 * <p>
 * The chain is a series of line segments with a catenary sag, a chain-link
 * brightness texture, and a slow magical pulse. When the summon is far from
 * its owner (approaching the leash range) the chain straightens and brightens
 * to convey "tension", giving visual feedback for the leash pull-back.
 * <p>
 * <b>Link detection:</b> an entity is considered linked to an owner if its
 * EliteData (capability on the server, {@link EliteCapabilityStorage} on the
 * client) has any of these UUIDs set, checked in priority order:
 * <ol>
 *   <li>{@code summonerUUID} — Necromancy undead &amp; Clone clones (true summons)</li>
 *   <li>{@code nexusSourceUUID} — elites nurtured by a C1 Nexus creator</li>
 *   <li>{@code commanderUUID} — elites in a C8 Commander squad</li>
 *   <li>{@code bestowerUUID} — elites empowered by a C5 Bestowal creator</li>
 * </ol>
 * <p>
 * <b>Rendering note:</b> {@link RenderLivingEvent.Post} provides a pose stack
 * already translated to the entity's interpolated render origin (relative to
 * the camera). Vertices are therefore drawn in the summon's local space:
 * the summon chest is at {@code (0, bbHeight/2, 0)} and the owner chest is at
 * {@code ownerPos - summonPos}. This mirrors the pattern used by
 * {@link CreatorAuraRenderer}.
 */
@Mod.EventBusSubscriber(modid = EliteForge.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ChainLinkRenderer {

    /** Maximum distance (in blocks) at which a chain is still rendered. Beyond this the link is culled. */
    private static final double MAX_CHAIN_DISTANCE = 40.0;
    /** Distance (in blocks) at which the chain reaches full "tension" (straight + brightest). Roughly matches the default leash range. */
    private static final float TENSION_DISTANCE = 24.0f;
    /** World-units per chain segment. Smaller = smoother chain, more vertices. */
    private static final float SEGMENT_LENGTH = 0.35f;
    /** Maximum sag (in blocks) at the midpoint of a slack chain. */
    private static final float MAX_SAG = 0.6f;
    /** Pulse speed (radians per tick). */
    private static final float PULSE_SPEED = 0.18f;

    @SubscribeEvent
    public static void onRenderLivingPost(RenderLivingEvent.Post<?, ?> event) {
        if (!EliteForgeConfig.SERVER.enableChainLinks.get()) return;

        LivingEntity entity = event.getEntity();
        if (entity == null) return;
        // Render only on the client; the event itself is client-side but guard anyway.
        if (!entity.level().isClientSide) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        // Resolve the owner UUID for this entity. Try the live capability first
        // (present on integrated servers / single-host), then fall back to the
        // client-synced storage (the canonical source on dedicated clients).
        UUID ownerUUID = resolveOwnerUUID(entity);
        if (ownerUUID == null) return;

        // Client-level (ClientLevel) has no getEntity(UUID) in 1.20.1, so we scan
        // the rendered entities for the matching UUID. This is O(n) per summon per
        // frame, but summons are few and the scan short-circuits on the first match.
        LivingEntity owner = null;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof LivingEntity le && le.getUUID().equals(ownerUUID)) {
                owner = le;
                break;
            }
        }
        if (owner == null || !owner.isAlive()) return;

        // Cull if the link is too long or the owner is too far from the player.
        double dist = entity.distanceTo(owner);
        if (dist > MAX_CHAIN_DISTANCE) return;
        if (mc.player.distanceToSqr(owner) > MAX_CHAIN_DISTANCE * MAX_CHAIN_DISTANCE) return;

        renderChain(event.getPoseStack(), event.getMultiBufferSource(),
                entity, owner, event.getPartialTick());
    }

    /**
     * Resolve the owner UUID for the given entity by checking the four
     * master-servant link fields in priority order. Returns {@code null}
     * if the entity is not linked to any owner.
     */
    private static UUID resolveOwnerUUID(LivingEntity entity) {
        // Prefer the client-synced storage copy (safe to read from the render thread).
        // The live capability holds server-side EliteData whose maps can be mutated
        // concurrently by the server thread.
        EliteData data = EliteCapabilityStorage.getEliteData(entity);
        if (data == null) {
            data = entity.getCapability(EliteCapability.CAPABILITY)
                    .map(EliteCapability::getEliteData)
                    .orElse(null);
        }
        if (data == null) return null;

        UUID u = data.getSummonerUUID();
        if (u != null) return u;
        u = data.getNexusSourceUUID();
        if (u != null) return u;
        u = data.getCommanderUUID();
        if (u != null) return u;
        u = data.getBestowerUUID();
        return u;
    }

    /**
     * Render the purple chain between {@code summon} and {@code owner}.
     * The pose stack is already at the summon's render origin, so vertices
     * are drawn in summon-local space.
     */
    private static void renderChain(PoseStack poseStack, MultiBufferSource bufferSource,
                                     LivingEntity summon, LivingEntity owner, float partialTick) {
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.lines());

        // Summon chest in local space (origin = summon render position)
        float startY = summon.getBbHeight() * 0.5f;
        // Owner chest relative to summon: ownerPos - summonPos, then lift to chest height.
        Vec3 summonPos = summon.getPosition(partialTick);
        Vec3 ownerPos = owner.getPosition(partialTick);
        double dx = ownerPos.x - summonPos.x;
        double dy = (ownerPos.y + owner.getBbHeight() * 0.5) - (summonPos.y + startY);
        double dz = ownerPos.z - summonPos.z;

        double totalLength = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (totalLength < 0.1) return; // overlapping — nothing to draw

        int segments = Mth.clamp((int) (totalLength / SEGMENT_LENGTH), 5, 90);

        // Tension: 0 when close, 1 when at/over TENSION_DISTANCE.
        float tension = Mth.clamp((float) (totalLength / TENSION_DISTANCE), 0.0f, 1.0f);
        // Slack chains sag; taut chains straighten.
        float sagAmount = MAX_SAG * (1.0f - tension * 0.75f);

        // Magical pulse
        float partial = partialTick;
        float pulse = 0.85f + 0.15f * Mth.sin((summon.tickCount + partial) * PULSE_SPEED);

        // Purple base color
        final float baseR = 0.60f;
        final float baseG = 0.18f;
        final float baseB = 0.98f;
        // Alpha rises with tension so a straining leash is more visible.
        float alpha = 0.70f + 0.25f * tension;

        poseStack.pushPose();
        PoseStack.Pose pose = poseStack.last();
        Matrix4f matrix = pose.pose();

        for (int i = 0; i < segments; i++) {
            float t1 = (float) i / segments;
            float t2 = (float) (i + 1) / segments;

            double x1 = dx * t1;
            double y1 = startY + dy * t1;
            double z1 = dz * t1;
            double x2 = dx * t2;
            double y2 = startY + dy * t2;
            double z2 = dz * t2;

            // Catenary sag: downward parabola peaking at t=0.5
            float sag1 = -sagAmount * 4.0f * t1 * (1.0f - t1);
            float sag2 = -sagAmount * 4.0f * t2 * (1.0f - t2);

            // Chain-link texture: alternate bright/dim links
            float link = (i % 2 == 0) ? 1.0f : 0.65f;
            float b = pulse * link;

            float r = baseR * b;
            float g = baseG * b;
            float bl = baseB * b;

            consumer.vertex(matrix, (float) x1, (float) (y1 + sag1), (float) z1)
                    .color(r, g, bl, alpha)
                    .normal(pose.normal(), 0, 1, 0)
                    .endVertex();
            consumer.vertex(matrix, (float) x2, (float) (y2 + sag2), (float) z2)
                    .color(r, g, bl, alpha)
                    .normal(pose.normal(), 0, 1, 0)
                    .endVertex();
        }

        poseStack.popPose();
    }
}
