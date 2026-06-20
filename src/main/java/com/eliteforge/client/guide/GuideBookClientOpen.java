package com.eliteforge.client.guide;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * GuideBookClientOpen — thin client-side helper that opens the {@link GuideBookScreen}.
 * <p>
 * This indirection exists so {@link com.eliteforge.item.EliteForgeGuideBook#use} can
 * call a static method without directly referencing the Screen class (which would
 * crash a dedicated server that loads the item class). The method body is stripped on
 * the server side by the {@code @OnlyIn(Dist.CLIENT)} annotation.
 */
@OnlyIn(Dist.CLIENT)
public final class GuideBookClientOpen {

    private GuideBookClientOpen() {
    }

    /**
     * Open the guide book screen on the given starting page.
     *
     * @param startPage the page to open on (1-based, clamped to the page range)
     */
    public static void openBook(int startPage) {
        Minecraft mc = Minecraft.getInstance();
        mc.setScreen(new GuideBookScreen(startPage));
    }
}
