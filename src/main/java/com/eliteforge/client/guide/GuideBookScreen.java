package com.eliteforge.client.guide;

import com.eliteforge.EliteForge;
import com.eliteforge.item.EliteForgeGuideBook;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.List;

/**
 * GuideBookScreen — a custom book GUI that renders EliteForge's guide content
 * on a two-page parchment spread with gold-trimmed leather binding.
 * <p>
 * Layout:
 * <ul>
 *   <li>256×200 background texture ({@code eliteforge:textures/gui/guide_book_gui.png})
 *       centered on screen.</li>
 *   <li>Left page = current page content; right page = next page content
 *       (or a "fin" marker on the last page).</li>
 *   <li>Navigation: &lt; / &gt; arrow buttons at the bottom corners; page counter
 *       centered at the bottom. ESC / Done closes.</li>
 * </ul>
 * <p>
 * Text is word-wrapped to fit the ~104px-wide page columns using the vanilla font's
 * {@code split} helper. Long pages that exceed the column height are clipped.
 * <p>
 * Client-only ({@code Dist.CLIENT}). Opened by {@link GuideBookClientOpen} when the
 * player right-clicks the guide book item.
 */
@OnlyIn(Dist.CLIENT)
public class GuideBookScreen extends Screen {

    private static final ResourceLocation TEXTURE =
            new ResourceLocation(EliteForge.MODID, "textures/gui/guide_book_gui.png");

    /** Background texture dimensions. */
    private static final int BG_W = 256;
    private static final int BG_H = 200;

    /** Page text area (relative to the background's top-left). */
    private static final int LEFT_PAGE_X = 20;
    private static final int LEFT_PAGE_Y = 26;
    private static final int RIGHT_PAGE_X = 138;
    private static final int RIGHT_PAGE_Y = 26;
    private static final int PAGE_WIDTH = 104;
    private static final int PAGE_HEIGHT = 150;
    private static final int LINE_HEIGHT = 9;

    private int currentPage = 1;
    private int bgLeft;
    private int bgTop;

    public GuideBookScreen(int startPage) {
        super(Component.translatable("guide.eliteforge.title"));
        this.currentPage = Math.max(1, Math.min(EliteForgeGuideBook.TOTAL_PAGES, startPage));
    }

    @Override
    protected void init() {
        this.bgLeft = (this.width - BG_W) / 2;
        this.bgTop = (this.height - BG_H) / 2;

        // Back arrow (bottom-left of left page)
        this.addRenderableWidget(Button.builder(
                Component.literal("<"),
                b -> turnPage(-1)
        ).bounds(bgLeft + 16, bgTop + BG_H - 22, 20, 16).build());

        // Forward arrow (bottom-right of right page)
        this.addRenderableWidget(Button.builder(
                Component.literal(">"),
                b -> turnPage(1)
        ).bounds(bgLeft + BG_W - 36, bgTop + BG_H - 22, 20, 16).build());

        // Done button (bottom-center, outside the book)
        this.addRenderableWidget(Button.builder(
                Component.translatable("gui.done"),
                b -> this.onClose()
        ).bounds(this.width / 2 - 40, bgTop + BG_H + 6, 80, 18).build());
    }

    private void turnPage(int delta) {
        int next = currentPage + delta;
        if (next < 1) next = 1;
        if (next > EliteForgeGuideBook.TOTAL_PAGES) next = EliteForgeGuideBook.TOTAL_PAGES;
        if (next != currentPage) {
            currentPage = next;
            // Play the page-flip sound for tactile feedback.
            Minecraft.getInstance().player.playSound(
                    net.minecraft.sounds.SoundEvents.BOOK_PAGE_TURN, 1.0f, 1.0f);
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);

        // Bind + draw the book background texture.
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.setShaderTexture(0, TEXTURE);
        g.blit(TEXTURE, bgLeft, bgTop, 0, 0, BG_W, BG_H);

        // Render the left page content (current page).
        renderPageContent(g, LEFT_PAGE_X, LEFT_PAGE_Y, currentPage);

        // Render the right page content (next page, or "fin" marker on the last page).
        int rightPage = currentPage + 1;
        if (rightPage <= EliteForgeGuideBook.TOTAL_PAGES) {
            renderPageContent(g, RIGHT_PAGE_X, RIGHT_PAGE_Y, rightPage);
        } else {
            // Last page — render a centered "fin" / restart marker on the right page.
            Component fin = Component.translatable("guide.eliteforge.fin")
                    .withStyle(net.minecraft.ChatFormatting.GOLD, net.minecraft.ChatFormatting.BOLD);
            int finWidth = this.font.width(fin);
            g.drawString(this.font, fin,
                    bgLeft + RIGHT_PAGE_X + (PAGE_WIDTH - finWidth) / 2,
                    bgTop + RIGHT_PAGE_Y + PAGE_HEIGHT / 2 - 4,
                    0xFFD700, true);
            Component restart = Component.translatable("guide.eliteforge.restart_hint")
                    .withStyle(net.minecraft.ChatFormatting.AQUA);
            int rw = this.font.width(restart);
            g.drawString(this.font, restart,
                    bgLeft + RIGHT_PAGE_X + (PAGE_WIDTH - rw) / 2,
                    bgTop + RIGHT_PAGE_Y + PAGE_HEIGHT / 2 + 8,
                    0x55FFFF, true);
        }

        // Page counter (centered at the bottom of the book, between the arrows).
        String counter = currentPage + " / " + EliteForgeGuideBook.TOTAL_PAGES;
        Component counterComp = Component.literal(counter)
                .withStyle(net.minecraft.ChatFormatting.DARK_GRAY);
        int cw = this.font.width(counterComp);
        g.drawString(this.font, counterComp,
                bgLeft + (BG_W - cw) / 2,
                bgTop + BG_H - 17,
                0x555555, true);

        // Render buttons + tooltips on top.
        super.render(g, mouseX, mouseY, partialTick);
    }

    /**
     * Render a page's content lines into the given column. Lines are drawn
     * left-aligned and word-wrapped if they exceed the column width.
     */
    private void renderPageContent(GuiGraphics g, int pageX, int pageY, int page) {
        List<Component> lines = EliteForgeGuideBook.getPageContent(page);
        int y = bgTop + pageY;
        for (Component line : lines) {
            if (line.getString().isEmpty()) {
                // Blank line — just advance the cursor.
                y += LINE_HEIGHT;
                continue;
            }
            // Word-wrap long lines to the page width.
            List<FormattedCharSequence> wrapped = this.font.split(line, PAGE_WIDTH);
            for (FormattedCharSequence row : wrapped) {
                if (y < bgTop + pageY + PAGE_HEIGHT) {
                    // drawShadow=true so text is readable against the parchment texture.
                    g.drawString(this.font, row, bgLeft + pageX, y, 0x2A1A0A, true);
                }
                y += LINE_HEIGHT;
            }
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Arrow keys / page-up/down for navigation, ESC to close.
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            this.onClose();
            return true;
        }
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT
                || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_PAGE_DOWN) {
            turnPage(1);
            return true;
        }
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT
                || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_PAGE_UP) {
            turnPage(-1);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
