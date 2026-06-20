package com.eliteforge.render;

import com.eliteforge.ability.Ability;
import com.eliteforge.ability.AbilityCategory;
import com.eliteforge.ability.AbilityRegistry;
import com.eliteforge.capability.EliteData;
import com.eliteforge.config.EliteForgeConfig;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * AbilityIconRenderer — renders ability names as text above elite mobs.
 * L2Hostility-style: shows ability name + Roman numeral level in category color.
 */
public class AbilityIconRenderer {

    private static final float TEXT_SCALE = 0.022f;
    private static final float Y_OFFSET = 0.5f;
    private static final int MAX_PER_LINE = 4;
    private static final String[] ROMAN = {"", "I", "II", "III", "IV", "V"};

    public static void renderAbilityIcons(
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            LivingEntity entity,
            EliteData data,
            int packedLight
    ) {
        if (!EliteForgeConfig.CLIENT.showAbilityIcons.get()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        double distSq = mc.player.distanceToSqr(entity);
        double renderDist = EliteForgeConfig.CLIENT.iconRenderDistance.get();
        if (distSq > renderDist * renderDist) return;

        Map<String, Integer> abilityMap = data.getAbilities();
        if (abilityMap == null || abilityMap.isEmpty()) return;

        List<AbilityEntry> abilities = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : abilityMap.entrySet()) {
            Ability ability = AbilityRegistry.getAbility(entry.getKey());
            if (ability != null) abilities.add(new AbilityEntry(ability, entry.getValue()));
        }
        if (abilities.isEmpty()) return;

        abilities.sort((a, b) -> a.ability.getCategory().ordinal() - b.ability.getCategory().ordinal());

        // Build text lines
        List<MutableComponent> lines = new ArrayList<>();
        MutableComponent currentLine = Component.empty();
        int count = 0;

        for (AbilityEntry ae : abilities) {
            int color = getCategoryColor(ae.ability.getCategory());
            String roman = getRoman(ae.level);
            MutableComponent text = Component.literal("")
                    .append(Component.translatable(ae.ability.getNameKey()))
                    .append(Component.literal(" " + roman));
            text.withStyle(net.minecraft.network.chat.Style.EMPTY.withColor(color));

            if (count > 0) currentLine.append(Component.literal("  "));
            currentLine.append(text);
            count++;

            if (count >= MAX_PER_LINE) {
                lines.add(currentLine);
                currentLine = Component.empty();
                count = 0;
            }
        }
        if (count > 0) lines.add(currentLine);

        // Render
        EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();
        Font font = mc.font;

        poseStack.pushPose();
        // The pose stack from RenderLivingEvent.Pre is ALREADY camera-relative and
        // positioned at the entity's render origin. Translate only by the local offset.
        poseStack.translate(0.0, entity.getBbHeight() + Y_OFFSET, 0.0);
        poseStack.mulPose(dispatcher.cameraOrientation());
        poseStack.scale(-TEXT_SCALE, -TEXT_SCALE, TEXT_SCALE);

        for (int i = 0; i < lines.size(); i++) {
            MutableComponent line = lines.get(i);
            float lineWidth = font.width(line) / 2.0f;
            float yPos = -(lines.size() - i - 1) * (font.lineHeight + 1);

            font.drawInBatch(line, -lineWidth, yPos, 0xFFFFFF,
                    false, poseStack.last().pose(), bufferSource,
                    Font.DisplayMode.NORMAL, 0x40000000, packedLight);
        }

        poseStack.popPose();
    }

    private static int getCategoryColor(AbilityCategory category) {
        return switch (category) {
            case ATTACK -> 0xFF5555;
            case DEFENSE -> 0x55AAFF;
            case CONTROL -> 0xFFAA00;
            case LEGENDARY -> 0xAA55FF;
            case CREATOR -> 0xAA0000;
        };
    }

    private static String getRoman(int level) {
        if (level <= 0) return "";
        if (level < ROMAN.length) return ROMAN[level];
        return String.valueOf(level);
    }

    private record AbilityEntry(Ability ability, int level) {}
}
