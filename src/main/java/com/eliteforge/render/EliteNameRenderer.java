package com.eliteforge.render;

import com.eliteforge.capability.EliteData;
import com.eliteforge.config.EliteForgeConfig;
import com.eliteforge.quality.QualityTier;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;


public class EliteNameRenderer {

    private static final float NAME_PLATE_SCALE = 1.5F;
    private static final int HEALTH_BAR_WIDTH = 80;
    private static final int HEALTH_BAR_HEIGHT = 6;
    private static final float HEALTH_BAR_Y_OFFSET = 10.0F;

    // Quality tier diamond colors (text color codes)
    private static final int WHITE_DIAMOND = 0xFFFFFF;
    private static final int GREEN_DIAMOND = 0x55FF55;
    private static final int BLUE_DIAMOND = 0x5555FF;
    private static final int PURPLE_DIAMOND = 0xAA55FF;
    private static final int GOLD_DIAMOND = 0xFFAA00;
    private static final int MYTHIC_DIAMOND = 0xFF55FF;

    // Roman numerals for levels
    private static final String[] ROMAN_NUMERALS = {
            "", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"
    };

    public static void renderEliteNamePlate(
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            LivingEntity entity,
            EliteData data,
            int packedLight
    ) {
        if (!EliteForgeConfig.CLIENT.showEliteNamePlate.get()) {
            return;
        }
        if (data == null) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }

        double distanceSq = mc.player.distanceToSqr(entity);
        double renderDist = EliteForgeConfig.CLIENT.iconRenderDistance.get();
        if (distanceSq > renderDist * renderDist) {
            return;
        }

        float scale = EliteForgeConfig.CLIENT.eliteNamePlateScale.get().floatValue();
        if (scale <= 0) scale = NAME_PLATE_SCALE;

        EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();
        Font font = mc.font;

        poseStack.pushPose();

        // The pose stack from RenderNameTagEvent is ALREADY camera-relative and positioned
        // at the entity's render origin (vanilla EntityRenderer.renderNameTag only adds
        // (0, bbHeight+0.5, 0)). Subtracting the camera position again would put the plate
        // at 2×(entity − camera) — off-screen. Translate only by the local offset.
        poseStack.translate(0.0, entity.getBbHeight() + 0.5, 0.0);
        poseStack.mulPose(dispatcher.cameraOrientation());
        poseStack.scale(-scale * 0.025F, -scale * 0.025F, scale * 0.025F);

        // Build name components
        String levelRoman = getRomanNumeral(data.getLevel());
        String tierDiamond = getQualityDiamond(data.getQualityTier());
        String entityName = entity.getName().getString();

        // Format: "§6精英 §e[Ⅲ] §fZombie" with tier diamond
        // Round 2 fix: use translatable key instead of hardcoded "精英"
        MutableComponent nameComponent = Component.empty();
        nameComponent.append(Component.translatable("name.eliteforge.elite_prefix")
                .withStyle(style -> style.withColor(TextColor.fromRgb(0xFFAA00)))); // Gold
        nameComponent.append(Component.literal(" "));
        nameComponent.append(Component.literal("[" + levelRoman + "]").withStyle(style -> style.withColor(TextColor.fromRgb(0xFFFF55)))); // Yellow
        nameComponent.append(Component.literal(" "));
        nameComponent.append(Component.literal(tierDiamond).withStyle(style -> style.withColor(TextColor.fromRgb(getQualityColor(data.getQualityTier())))));
        nameComponent.append(Component.literal(" "));
        nameComponent.append(Component.literal(entityName).withStyle(style -> style.withColor(TextColor.fromRgb(0xFFFFFF)))); // White

        // Render name
        float nameWidth = font.width(nameComponent) / 2.0F;
        int nameColor = 0xFFFFFF;
        font.drawInBatch(nameComponent, -nameWidth, 0, nameColor, false, poseStack.last().pose(), bufferSource, Font.DisplayMode.NORMAL, 0, packedLight);

        // Render health bar below name
        renderHealthBar(poseStack, bufferSource, entity, data, font, packedLight);

        poseStack.popPose();
    }

    private static void renderHealthBar(
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            LivingEntity entity,
            EliteData data,
            Font font,
            int packedLight
    ) {
        float health = entity.getHealth();
        float maxHealth = entity.getMaxHealth();
        if (maxHealth <= 0) return;

        float healthPercent = Math.min(1.0F, health / maxHealth);

        float barX = -HEALTH_BAR_WIDTH / 2.0F;
        float barY = HEALTH_BAR_Y_OFFSET;

        Matrix4f matrix = poseStack.last().pose();

        // Background (dark gray)
        renderQuad(matrix, bufferSource, barX, barY, HEALTH_BAR_WIDTH, HEALTH_BAR_HEIGHT, 0.2f, 0.2f, 0.2f, 0.8f, packedLight);

        // Health fill (colored by percentage)
        int healthColor = getHealthColor(healthPercent);
        float red = ((healthColor >> 16) & 0xFF) / 255.0f;
        float green = ((healthColor >> 8) & 0xFF) / 255.0f;
        float blue = (healthColor & 0xFF) / 255.0f;
        float fillWidth = HEALTH_BAR_WIDTH * healthPercent;
        renderQuad(matrix, bufferSource, barX, barY, fillWidth, HEALTH_BAR_HEIGHT, red, green, blue, 1.0f, packedLight);

        // Border
        renderOutline(matrix, bufferSource, barX, barY, HEALTH_BAR_WIDTH, HEALTH_BAR_HEIGHT, 0.0f, 0.0f, 0.0f, 1.0f, packedLight);

        // HP text: "§a150/150"
        String hpText = (int) health + "/" + (int) maxHealth;
        float hpTextX = -font.width(hpText) / 2.0F;
        float hpTextY = barY + HEALTH_BAR_HEIGHT + 2;

        // Shadow
        font.drawInBatch(hpText, hpTextX + 1, hpTextY + 1, 0x000000, false, matrix, bufferSource, Font.DisplayMode.NORMAL, 0, packedLight);
        // Green text
        font.drawInBatch(hpText, hpTextX, hpTextY, 0x55FF55, false, matrix, bufferSource, Font.DisplayMode.NORMAL, 0, packedLight);
    }

    private static void renderQuad(
            Matrix4f matrix, MultiBufferSource bufferSource,
            float x, float y, float width, float height,
            float red, float green, float blue, float alpha,
            int packedLight
    ) {
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.gui());
        consumer.vertex(matrix, x, y, 0).color(red, green, blue, alpha).uv(0, 0).endVertex();
        consumer.vertex(matrix, x + width, y, 0).color(red, green, blue, alpha).uv(1, 0).endVertex();
        consumer.vertex(matrix, x + width, y + height, 0).color(red, green, blue, alpha).uv(1, 1).endVertex();
        consumer.vertex(matrix, x, y + height, 0).color(red, green, blue, alpha).uv(0, 1).endVertex();
    }

    private static void renderOutline(
            Matrix4f matrix, MultiBufferSource bufferSource,
            float x, float y, float width, float height,
            float red, float green, float blue, float alpha,
            int packedLight
    ) {
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.gui());
        // Top
        consumer.vertex(matrix, x, y, 0.01f).color(red, green, blue, alpha).uv(0, 0).endVertex();
        consumer.vertex(matrix, x + width, y, 0.01f).color(red, green, blue, alpha).uv(0, 0).endVertex();
        consumer.vertex(matrix, x + width, y + 1, 0.01f).color(red, green, blue, alpha).uv(0, 0).endVertex();
        consumer.vertex(matrix, x, y + 1, 0.01f).color(red, green, blue, alpha).uv(0, 0).endVertex();
        // Bottom
        consumer.vertex(matrix, x, y + height - 1, 0.01f).color(red, green, blue, alpha).uv(0, 0).endVertex();
        consumer.vertex(matrix, x + width, y + height - 1, 0.01f).color(red, green, blue, alpha).uv(0, 0).endVertex();
        consumer.vertex(matrix, x + width, y + height, 0.01f).color(red, green, blue, alpha).uv(0, 0).endVertex();
        consumer.vertex(matrix, x, y + height, 0.01f).color(red, green, blue, alpha).uv(0, 0).endVertex();
        // Left
        consumer.vertex(matrix, x, y, 0.01f).color(red, green, blue, alpha).uv(0, 0).endVertex();
        consumer.vertex(matrix, x + 1, y, 0.01f).color(red, green, blue, alpha).uv(0, 0).endVertex();
        consumer.vertex(matrix, x + 1, y + height, 0.01f).color(red, green, blue, alpha).uv(0, 0).endVertex();
        consumer.vertex(matrix, x, y + height, 0.01f).color(red, green, blue, alpha).uv(0, 0).endVertex();
        // Right
        consumer.vertex(matrix, x + width - 1, y, 0.01f).color(red, green, blue, alpha).uv(0, 0).endVertex();
        consumer.vertex(matrix, x + width, y, 0.01f).color(red, green, blue, alpha).uv(0, 0).endVertex();
        consumer.vertex(matrix, x + width, y + height, 0.01f).color(red, green, blue, alpha).uv(0, 0).endVertex();
        consumer.vertex(matrix, x + width - 1, y + height, 0.01f).color(red, green, blue, alpha).uv(0, 0).endVertex();
    }

    public static int getHealthColor(float healthPercent) {
        if (healthPercent > 0.66F) {
            // Green to yellow gradient (0.66 - 1.0)
            float t = (healthPercent - 0.66F) / 0.34F;
            int r = (int) (255 * (1 - t));
            int g = 255;
            int b = 0;
            return (r << 16) | (g << 8) | b;
        } else if (healthPercent > 0.33F) {
            // Yellow to orange gradient (0.33 - 0.66)
            float t = (healthPercent - 0.33F) / 0.33F;
            int r = 255;
            int g = (int) (100 + 155 * t);
            int b = 0;
            return (r << 16) | (g << 8) | b;
        } else {
            // Red
            int r = 255;
            int g = (int) (100 * (healthPercent / 0.33F));
            int b = 0;
            return (r << 16) | (g << 8) | b;
        }
    }

    /**
     * Get the diamond symbol for a quality tier.
     * All tiers use the same "◆" symbol; the visual distinction comes from
     * the color applied via {@link #getQualityColor}.
     *
     * @param tier the quality tier (unused, but kept for API compatibility)
     * @return the diamond symbol "◆"
     */
    public static String getQualityDiamond(QualityTier tier) {
        return "◆";
    }

    /**
     * Get the ARGB color for a quality tier's diamond symbol.
     *
     * @param tier the quality tier
     * @return the color int, or WHITE_DIAMOND if tier is null
     */
    public static int getQualityColor(QualityTier tier) {
        if (tier == null) return WHITE_DIAMOND;
        return switch (tier) {
            case NORMAL -> WHITE_DIAMOND;
            case GOOD -> GREEN_DIAMOND;
            case FINE -> BLUE_DIAMOND;
            case EPIC -> PURPLE_DIAMOND;
            case LEGENDARY -> GOLD_DIAMOND;
            case MYTHIC -> MYTHIC_DIAMOND;
        };
    }

    public static String getRomanNumeral(int level) {
        if (level <= 0) return "";
        if (level < ROMAN_NUMERALS.length) return ROMAN_NUMERALS[level];
        return String.valueOf(level);
    }

    /**
     * Renders a direction indicator on the HUD pointing toward the nearest elite entity.
     */
    public static void renderDirectionIndicator(GuiGraphics guiGraphics, Player player, LivingEntity elite) {
        Minecraft mc = Minecraft.getInstance();
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();

        // Calculate angle from player to elite
        Vec3 playerPos = player.position();
        Vec3 elitePos = elite.position();
        double dx = elitePos.x - playerPos.x;
        double dz = elitePos.z - playerPos.z;
        double angle = Math.atan2(dz, dx);

        // Convert to screen angle (MC uses different coordinate system)
        float playerYaw = player.getYRot() * ((float) Math.PI / 180F);
        float screenAngle = (float) (angle - playerYaw + Math.PI);

        // Position on screen edge (circular indicator)
        int centerX = screenWidth / 2;
        int centerY = screenHeight / 2;
        int indicatorRadius = Math.min(screenWidth, screenHeight) / 2 - 30;

        int indicatorX = centerX + (int) (Math.cos(screenAngle) * indicatorRadius);
        int indicatorY = centerY + (int) (Math.sin(screenAngle) * indicatorRadius);

        // Clamp to screen bounds
        indicatorX = Math.max(10, Math.min(screenWidth - 10, indicatorX));
        indicatorY = Math.max(10, Math.min(screenHeight - 10, indicatorY));

        // Draw arrow indicator
        String arrow = "▶";
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(indicatorX, indicatorY, 0);
        guiGraphics.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees((float)(screenAngle * 180.0 / Math.PI)));
        guiGraphics.drawString(mc.font, arrow, -4, -4, 0xFFAA00, true);
        guiGraphics.pose().popPose();

        // Draw distance
        double distance = Math.sqrt(player.distanceToSqr(elite));
        String distText = String.format("%.0fm", distance);
        guiGraphics.drawString(mc.font, distText, indicatorX - mc.font.width(distText) / 2, indicatorY + 8, 0xFFAA00, true);
    }
}
