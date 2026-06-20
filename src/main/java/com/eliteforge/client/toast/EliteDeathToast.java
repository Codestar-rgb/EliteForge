package com.eliteforge.client.toast;

import com.eliteforge.EliteForge;
import com.eliteforge.quality.QualityTier;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * EliteDeathToast — a client-side advancement-style toast notification shown
 * when the player kills an elite mob. Displays the elite's name, quality tier,
 * and level in a gold-bordered parchment card.
 * <p>
 * Uses the vanilla toast texture ({@code minecraft:textures/gui/toasts.png}) as
 * the background (160×32 px), with the content drawn on top.
 * <p>
 * Triggered by {@link EliteDeathToastHandler} when the client receives the
 * elite-death sync packet.
 */
@OnlyIn(Dist.CLIENT)
public class EliteDeathToast implements Toast {

    private static final ResourceLocation TOAST_TEXTURE =
            new ResourceLocation("minecraft", "textures/gui/toasts.png");

    private final Component title;
    private final Component subtitle;
    private final int titleColor;
    private final int subtitleColor;
    private boolean playedSound = false;

    public EliteDeathToast(String eliteName, QualityTier quality, int level) {
        this.title = Component.translatable("toast.eliteforge.elite_defeated")
                .withStyle(net.minecraft.ChatFormatting.GOLD, net.minecraft.ChatFormatting.BOLD);
        this.subtitle = Component.literal(eliteName + " Lv." + level)
                .withStyle(quality.getChatColor());
        this.titleColor = 0xFFAA00;
        this.subtitleColor = rgbOf(quality.getChatColor());
    }

    @Override
    public Toast.Visibility render(GuiGraphics g, ToastComponent component, long sinceFirstRender) {
        // Play a subtle sound the first frame the toast appears.
        if (!playedSound) {
            playedSound = true;
            Minecraft.getInstance().player.playSound(
                    net.minecraft.sounds.SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 1.2f);
        }

        // Draw the vanilla toast background (160x32).
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        g.blit(TOAST_TEXTURE, 0, 0, 0, 0, this.width(), this.height());

        Minecraft mc = component.getMinecraft();

        // Draw a gold diamond emblem on the left (where the item icon would go).
        g.drawString(mc.font, Component.literal("\u25C6").withStyle(net.minecraft.ChatFormatting.GOLD),
                8, 8, 0xFFD700, false);

        // Title (line 1).
        g.drawString(mc.font, this.title, 30, 7, this.titleColor, false);

        // Subtitle (line 2) — elite name + level, colored by quality.
        g.drawString(mc.font, this.subtitle, 30, 18, this.subtitleColor, false);

        // Visibility: 5 seconds (5000ms).
        return sinceFirstRender >= 5000L ? Toast.Visibility.HIDE : Toast.Visibility.SHOW;
    }

    /** Convert a ChatFormatting color to an RGB int for drawString. */
    private static int rgbOf(net.minecraft.ChatFormatting formatting) {
        Integer color = formatting.getColor();
        return color == null ? 0xFFFFFF : color;
    }

    /** Convenience: queue a toast on the client. */
    public static void show(String eliteName, QualityTier quality, int level) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getToasts() == null) return;
        mc.getToasts().addToast(new EliteDeathToast(eliteName, quality, level));
    }
}
