package com.eliteforge.client.config;

import com.eliteforge.config.EliteForgeConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.common.ForgeConfigSpec;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * EliteForgeConfigScreen — in-game configuration GUI for client-side visual
 * preferences. Replaces the previous stub.
 * <p>
 * The screen presents a list of toggle buttons for each CLIENT config flag
 * (show ability icons / name plate / particles / creator aura / heat overlay),
 * plus stepper buttons for numeric settings (icon render distance, name-plate
 * scale). Changes apply immediately to the in-memory config and are saved to
 * {@code eliteforge-client.toml} when the player clicks Done.
 * <p>
 * Server-side config is NOT editable here — use {@code /eliteforge config} for that.
 */
public class EliteForgeConfigScreen extends Screen {

    private static final int TOGGLE_WIDTH = 180;
    private static final int WIDGET_HEIGHT = 20;
    private static final int ROW_HEIGHT = 24;
    private static final int HEADER_HEIGHT = 44;

    private final Screen parent;
    private int scrollOffset = 0;
    private final List<ConfigRow<?>> rows = new ArrayList<>();
    /** Track row widgets separately so we can rebuild them on scroll without touching the Done button. */
    private final List<AbstractWidget> rowWidgets = new ArrayList<>();

    public EliteForgeConfigScreen(Screen parent) {
        super(Component.translatable("config.eliteforge.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        rows.clear();
        rows.add(new BooleanRow("config.eliteforge.show_ability_icons",
                EliteForgeConfig.CLIENT.showAbilityIcons));
        rows.add(new BooleanRow("config.eliteforge.show_nameplate",
                EliteForgeConfig.CLIENT.showEliteNamePlate));
        rows.add(new BooleanRow("config.eliteforge.show_particles",
                EliteForgeConfig.CLIENT.showAbilityParticles));
        rows.add(new BooleanRow("config.eliteforge.show_creator_aura",
                EliteForgeConfig.CLIENT.showCreatorAura));
        rows.add(new BooleanRow("config.eliteforge.show_elite_death_toast",
                EliteForgeConfig.CLIENT.showEliteDeathToast));
        rows.add(new BooleanRow("config.eliteforge.show_heat_overlay",
                EliteForgeConfig.CLIENT.showHeatOverlay));
        rows.add(new IntRow("config.eliteforge.icon_render_distance",
                EliteForgeConfig.CLIENT.iconRenderDistance, 8, 128, 4));
        rows.add(new DoubleRow("config.eliteforge.nameplate_scale",
                EliteForgeConfig.CLIENT.eliteNamePlateScale, 0.5, 3.0, 0.1));

        // Done button (bottom-center, always visible)
        this.addRenderableWidget(Button.builder(
                Component.translatable("gui.done"),
                button -> {
                    EliteForgeConfig.CLIENT_SPEC.save();
                    this.minecraft.setScreen(this.parent);
                }
        ).bounds(this.width / 2 - 100, this.height - 28, 200, WIDGET_HEIGHT).build());

        rebuildRowWidgets();
    }

    private void rebuildRowWidgets() {
        // Remove previously-added row widgets (Screen.removeWidget handles renderables,
        // children, and narratables in one call — the underlying collections are private).
        for (AbstractWidget w : rowWidgets) {
            this.removeWidget(w);
        }
        rowWidgets.clear();

        int y = HEADER_HEIGHT - scrollOffset;
        for (ConfigRow<?> row : rows) {
            if (y + ROW_HEIGHT > HEADER_HEIGHT && y < this.height - 36) {
                AbstractWidget control = row.createWidget(this.width / 2 - TOGGLE_WIDTH / 2, y);
                this.addRenderableWidget(control);
                rowWidgets.add(control);
            }
            y += ROW_HEIGHT;
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);
        g.drawCenteredString(this.font, this.title, this.width / 2, 15, 0xFFFFFF);
        g.drawCenteredString(this.font,
                Component.translatable("config.eliteforge.client_hint"),
                this.width / 2, 28, 0xA0A0A0);
        // Render row labels manually (labels aren't interactive widgets).
        // Clamp the label X so it stays on-screen at high GUI scales / narrow windows.
        int y = HEADER_HEIGHT - scrollOffset;
        int labelX = Math.max(4, this.width / 2 - TOGGLE_WIDTH / 2 - 210);
        for (ConfigRow<?> row : rows) {
            if (y + ROW_HEIGHT > HEADER_HEIGHT && y < this.height - 36) {
                g.drawString(this.font, Component.translatable(row.labelKey),
                        labelX, y + 6, 0xFFFFFF);
            }
            y += ROW_HEIGHT;
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        EliteForgeConfig.CLIENT_SPEC.save();
        this.minecraft.setScreen(this.parent);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int maxScroll = Math.max(0, rows.size() * ROW_HEIGHT - (this.height - HEADER_HEIGHT - 40));
        scrollOffset = (int) Math.max(0, Math.min(maxScroll, scrollOffset - delta * 16));
        rebuildRowWidgets();
        return true;
    }

    // ==================== Row model ====================

    private static abstract class ConfigRow<T> {
        final String labelKey;
        final Supplier<T> getter;
        final Consumer<T> setter;
        ConfigRow(String labelKey, Supplier<T> getter, Consumer<T> setter) {
            this.labelKey = labelKey; this.getter = getter; this.setter = setter;
        }
        abstract AbstractWidget createWidget(int x, int y);
    }

    private static class BooleanRow extends ConfigRow<Boolean> {
        BooleanRow(String label, ForgeConfigSpec.ConfigValue<Boolean> cv) {
            super(label, cv::get, cv::set);
        }
        @Override
        AbstractWidget createWidget(int x, int y) {
            return Button.builder(
                    Component.translatable(getter.get() ? "options.on" : "options.off"),
                    b -> { setter.accept(!getter.get()); b.setMessage(Component.translatable(getter.get() ? "options.on" : "options.off")); }
            ).bounds(x, y, TOGGLE_WIDTH, WIDGET_HEIGHT).build();
        }
    }

    private static class IntRow extends ConfigRow<Integer> {
        final int min, max, step;
        IntRow(String label, ForgeConfigSpec.IntValue cv, int min, int max, int step) {
            super(label, cv::get, cv::set);
            this.min = min; this.max = max; this.step = step;
        }
        @Override
        AbstractWidget createWidget(int x, int y) {
            return Button.builder(
                    Component.literal(String.valueOf(getter.get())),
                    b -> {
                        // Wrap-around so the value can be decremented by cycling past max → min.
                        // (Without this the stepper only ever increases, stranding users at max.)
                        int v = getter.get() + step;
                        if (v > max) v = min;
                        if (v < min) v = max;
                        setter.accept(v);
                        b.setMessage(Component.literal(String.valueOf(v)));
                    }
            ).bounds(x, y, TOGGLE_WIDTH, WIDGET_HEIGHT).build();
        }
    }

    private static class DoubleRow extends ConfigRow<Double> {
        final double min, max, step;
        DoubleRow(String label, ForgeConfigSpec.DoubleValue cv, double min, double max, double step) {
            super(label, cv::get, cv::set);
            this.min = min; this.max = max; this.step = step;
        }
        @Override
        AbstractWidget createWidget(int x, int y) {
            return Button.builder(
                    Component.literal(String.format("%.1f", getter.get())),
                    b -> {
                        // Wrap-around (see IntRow).
                        double v = getter.get() + step;
                        if (v > max) v = min;
                        if (v < min) v = max;
                        setter.accept(v);
                        b.setMessage(Component.literal(String.format("%.1f", v)));
                    }
            ).bounds(x, y, TOGGLE_WIDTH, WIDGET_HEIGHT).build();
        }
    }
}
