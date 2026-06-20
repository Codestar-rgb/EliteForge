package com.eliteforge.client.toast;

import com.eliteforge.quality.QualityTier;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * EliteDeathToastHandler — client-side bridge between the server death event
 * and the {@link EliteDeathToast} notification.
 * <p>
 * {@link com.eliteforge.spawn.EliteEventHandler#onLivingDeath} calls
 * {@link #showToast} via a {@code runWhenOn(Dist.CLIENT, ...)} lambda, so this
 * class is only loaded on the client. The method schedules the toast on the
 * Minecraft main thread via {@code execute} to avoid threading issues.
 */
@OnlyIn(Dist.CLIENT)
public final class EliteDeathToastHandler {

    private EliteDeathToastHandler() {
    }

    /**
     * Queue an elite-death toast on the client main thread. Does nothing if the
     * player has disabled the toast in their client config
     * ({@code showEliteDeathToast}).
     *
     * @param eliteName the display name of the killed elite
     * @param quality   the elite's quality tier (drives the subtitle color)
     * @param level     the elite's level
     */
    public static void showToast(String eliteName, QualityTier quality, int level) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        // Respect the client config — players can disable the toast popup.
        if (!com.eliteforge.config.EliteForgeConfig.CLIENT.showEliteDeathToast.get()) return;
        mc.execute(() -> EliteDeathToast.show(eliteName, quality, level));
    }
}
