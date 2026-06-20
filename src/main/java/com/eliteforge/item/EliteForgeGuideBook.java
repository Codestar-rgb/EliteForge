package com.eliteforge.item;

import com.eliteforge.EliteForge;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * EliteForgeGuideBook (精英锻造指南) — A welcome guide book item.
 * <p>
 * Right-click opens a custom book GUI ({@link com.eliteforge.client.guide.GuideBookScreen})
 * that renders a two-page spread with navigation arrows, matching the vanilla
 * Written Book look but with EliteForge's gold/parchment styling.
 * <ul>
 *   <li>Page 1: Welcome & overview</li>
 *   <li>Page 2: Quality tiers (NORMAL → MYTHIC)</li>
 *   <li>Page 3: Ability categories (Attack/Defense/Control/Legendary/Creator)</li>
 *   <li>Page 4: Accessories & Upgrade Station</li>
 *   <li>Page 5: Creator-tier & Awakening</li>
 *   <li>Page 6: Tips & config</li>
 * </ul>
 * <p>
 * The page content is built by {@link #getPageContent(int)} (public so the screen can
 * call it without instantiating the item). The current page is stored in the item's NBT
 * so the book reopens on the last-read page.
 */
public class EliteForgeGuideBook extends Item {

    /** Total number of pages in the guide. */
    public static final int TOTAL_PAGES = 6;

    public EliteForgeGuideBook() {
        super(new Item.Properties().stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        // Open the custom book GUI on the client. The server returns CONSUME so the
        // arm-swing animation plays; the actual screen opening is handled client-side
        // via the Forge client tick event in GuideBookClientOpen.
        if (level.isClientSide) {
            int currentPage = stack.getOrCreateTag().getInt("eliteforge:guide_page");
            if (currentPage < 1 || currentPage > TOTAL_PAGES) currentPage = 1;
            com.eliteforge.client.guide.GuideBookClientOpen.openBook(currentPage);
        }
        // success = swing arm + don't consume the item (the book is reusable).
        return InteractionResultHolder.success(stack);
    }

    /**
     * Get the content for a specific page. Public + static so the client-side
     * {@link com.eliteforge.client.guide.GuideBookScreen} can call it without an item
     * instance (the screen only needs the page number).
     *
     * @param page the page number (1-based)
     * @return list of Component lines to render on the page
     */
    public static List<Component> getPageContent(int page) {
        List<Component> lines = new ArrayList<>();

        switch (page) {
            case 1 -> { // Welcome
                lines.add(Component.literal("═══════════════════════════════")
                        .withStyle(ChatFormatting.GOLD));
                lines.add(Component.translatable("guide.eliteforge.page1.title")
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
                lines.add(Component.literal("═══════════════════════════════")
                        .withStyle(ChatFormatting.GOLD));
                lines.add(Component.empty());
                lines.add(Component.translatable("guide.eliteforge.page1.line1")
                        .withStyle(ChatFormatting.WHITE));
                lines.add(Component.translatable("guide.eliteforge.page1.line2")
                        .withStyle(ChatFormatting.WHITE));
                lines.add(Component.empty());
                lines.add(Component.translatable("guide.eliteforge.page1.line3")
                        .withStyle(ChatFormatting.YELLOW));
                lines.add(Component.translatable("guide.eliteforge.page1.line4")
                        .withStyle(ChatFormatting.YELLOW));
                lines.add(Component.translatable("guide.eliteforge.page1.line5")
                        .withStyle(ChatFormatting.GRAY));
                lines.add(Component.translatable("guide.eliteforge.page1.line6")
                        .withStyle(ChatFormatting.GRAY));
                lines.add(Component.empty());
                lines.add(Component.translatable("guide.eliteforge.next_page")
                        .withStyle(ChatFormatting.AQUA));
            }
            case 2 -> { // Quality Tiers
                lines.add(Component.translatable("guide.eliteforge.page2.title")
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
                lines.add(Component.literal("───────────────────────────────")
                        .withStyle(ChatFormatting.GOLD));
                lines.add(Component.empty());
                lines.add(Component.translatable("guide.eliteforge.page2.line1")
                        .withStyle(ChatFormatting.WHITE));
                lines.add(Component.empty());
                lines.add(Component.literal("◆ ").withStyle(ChatFormatting.WHITE)
                        .append(Component.translatable("quality.eliteforge.normal").withStyle(ChatFormatting.WHITE))
                        .append(Component.translatable("guide.eliteforge.page2.normal_desc").withStyle(ChatFormatting.GRAY)));
                lines.add(Component.literal("◆ ").withStyle(ChatFormatting.GREEN)
                        .append(Component.translatable("quality.eliteforge.good").withStyle(ChatFormatting.GREEN))
                        .append(Component.translatable("guide.eliteforge.page2.good_desc").withStyle(ChatFormatting.GRAY)));
                lines.add(Component.literal("◆ ").withStyle(ChatFormatting.BLUE)
                        .append(Component.translatable("quality.eliteforge.fine").withStyle(ChatFormatting.BLUE))
                        .append(Component.translatable("guide.eliteforge.page2.fine_desc").withStyle(ChatFormatting.GRAY)));
                lines.add(Component.literal("◆ ").withStyle(ChatFormatting.LIGHT_PURPLE)
                        .append(Component.translatable("quality.eliteforge.epic").withStyle(ChatFormatting.LIGHT_PURPLE))
                        .append(Component.translatable("guide.eliteforge.page2.epic_desc").withStyle(ChatFormatting.GRAY)));
                lines.add(Component.literal("◆ ").withStyle(ChatFormatting.GOLD)
                        .append(Component.translatable("quality.eliteforge.legendary").withStyle(ChatFormatting.GOLD))
                        .append(Component.translatable("guide.eliteforge.page2.legendary_desc").withStyle(ChatFormatting.GRAY)));
                lines.add(Component.literal("◆ ").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)
                        .append(Component.translatable("quality.eliteforge.mythic").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD))
                        .append(Component.translatable("guide.eliteforge.page2.mythic_desc").withStyle(ChatFormatting.GRAY)));
                lines.add(Component.empty());
                lines.add(Component.translatable("guide.eliteforge.next_page")
                        .withStyle(ChatFormatting.AQUA));
            }
            case 3 -> { // Abilities
                lines.add(Component.translatable("guide.eliteforge.page3.title")
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
                lines.add(Component.literal("───────────────────────────────")
                        .withStyle(ChatFormatting.GOLD));
                lines.add(Component.empty());
                lines.add(Component.translatable("guide.eliteforge.page3.line1")
                        .withStyle(ChatFormatting.WHITE));
                lines.add(Component.empty());
                lines.add(Component.literal("⚔ ").withStyle(ChatFormatting.RED)
                        .append(Component.translatable("guide.eliteforge.page3.attack").withStyle(ChatFormatting.RED))
                        .append(Component.translatable("guide.eliteforge.page3.attack_desc").withStyle(ChatFormatting.GRAY)));
                lines.add(Component.literal("🛡 ").withStyle(ChatFormatting.AQUA)
                        .append(Component.translatable("guide.eliteforge.page3.defense").withStyle(ChatFormatting.AQUA))
                        .append(Component.translatable("guide.eliteforge.page3.defense_desc").withStyle(ChatFormatting.GRAY)));
                lines.add(Component.literal("⛓ ").withStyle(ChatFormatting.GOLD)
                        .append(Component.translatable("guide.eliteforge.page3.control").withStyle(ChatFormatting.GOLD))
                        .append(Component.translatable("guide.eliteforge.page3.control_desc").withStyle(ChatFormatting.GRAY)));
                lines.add(Component.literal("★ ").withStyle(ChatFormatting.LIGHT_PURPLE)
                        .append(Component.translatable("guide.eliteforge.page3.legendary").withStyle(ChatFormatting.LIGHT_PURPLE))
                        .append(Component.translatable("guide.eliteforge.page3.legendary_desc").withStyle(ChatFormatting.GRAY)));
                lines.add(Component.literal("☠ ").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)
                        .append(Component.translatable("guide.eliteforge.page3.creator").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD))
                        .append(Component.translatable("guide.eliteforge.page3.creator_desc").withStyle(ChatFormatting.GRAY)));
                lines.add(Component.empty());
                lines.add(Component.translatable("guide.eliteforge.page3.note")
                        .withStyle(ChatFormatting.YELLOW));
                lines.add(Component.empty());
                lines.add(Component.translatable("guide.eliteforge.next_page")
                        .withStyle(ChatFormatting.AQUA));
            }
            case 4 -> { // Accessories & Upgrade Station (v0.2.0: replaced the removed forging system)
                lines.add(Component.translatable("guide.eliteforge.page4.title")
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
                lines.add(Component.literal("───────────────────────────────")
                        .withStyle(ChatFormatting.GOLD));
                lines.add(Component.empty());
                lines.add(Component.translatable("guide.eliteforge.page4.line1")
                        .withStyle(ChatFormatting.WHITE));
                lines.add(Component.empty());
                lines.add(Component.literal("💎 ").withStyle(ChatFormatting.GOLD)
                        .append(Component.translatable("guide.eliteforge.page4.accessory").withStyle(ChatFormatting.GOLD))
                        .append(Component.translatable("guide.eliteforge.page4.accessory_desc").withStyle(ChatFormatting.GRAY)));
                lines.add(Component.literal("   ").append(Component.translatable("guide.eliteforge.page4.accessory_types").withStyle(ChatFormatting.DARK_GRAY)));
                lines.add(Component.empty());
                lines.add(Component.literal("⬆ ").withStyle(ChatFormatting.AQUA)
                        .append(Component.translatable("guide.eliteforge.page4.upgrade").withStyle(ChatFormatting.AQUA))
                        .append(Component.translatable("guide.eliteforge.page4.upgrade_desc").withStyle(ChatFormatting.GRAY)));
                lines.add(Component.literal("   ").append(Component.translatable("guide.eliteforge.page4.upgrade_material").withStyle(ChatFormatting.DARK_GRAY)));
                lines.add(Component.literal("   ").append(Component.translatable("guide.eliteforge.page4.upgrade_location").withStyle(ChatFormatting.DARK_GRAY)));
                lines.add(Component.empty());
                lines.add(Component.translatable("guide.eliteforge.page4.tip")
                        .withStyle(ChatFormatting.YELLOW));
                lines.add(Component.empty());
                lines.add(Component.translatable("guide.eliteforge.next_page")
                        .withStyle(ChatFormatting.AQUA));
            }
            case 5 -> { // Creator Tier & Awakening
                lines.add(Component.translatable("guide.eliteforge.page5.title")
                        .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
                lines.add(Component.literal("───────────────────────────────")
                        .withStyle(ChatFormatting.DARK_RED));
                lines.add(Component.empty());
                lines.add(Component.translatable("guide.eliteforge.page5.line1")
                        .withStyle(ChatFormatting.WHITE));
                lines.add(Component.empty());
                lines.add(Component.translatable("guide.eliteforge.page5.line2")
                        .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
                lines.add(Component.translatable("guide.eliteforge.page5.line3")
                        .withStyle(ChatFormatting.GRAY));
                lines.add(Component.translatable("guide.eliteforge.page5.line4")
                        .withStyle(ChatFormatting.GRAY));
                lines.add(Component.translatable("guide.eliteforge.page5.line5")
                        .withStyle(ChatFormatting.GRAY));
                lines.add(Component.empty());
                lines.add(Component.translatable("guide.eliteforge.page5.line6")
                        .withStyle(ChatFormatting.YELLOW));
                lines.add(Component.translatable("guide.eliteforge.page5.line7")
                        .withStyle(ChatFormatting.GRAY));
                lines.add(Component.translatable("guide.eliteforge.page5.line8")
                        .withStyle(ChatFormatting.GRAY));
                lines.add(Component.empty());
                lines.add(Component.translatable("guide.eliteforge.next_page")
                        .withStyle(ChatFormatting.AQUA));
            }
            case 6 -> { // Tips & Config
                lines.add(Component.translatable("guide.eliteforge.page6.title")
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
                lines.add(Component.literal("───────────────────────────────")
                        .withStyle(ChatFormatting.GOLD));
                lines.add(Component.empty());
                lines.add(Component.translatable("guide.eliteforge.page6.line1")
                        .withStyle(ChatFormatting.WHITE));
                lines.add(Component.empty());
                lines.add(Component.translatable("guide.eliteforge.page6.tip1")
                        .withStyle(ChatFormatting.YELLOW));
                lines.add(Component.translatable("guide.eliteforge.page6.tip2")
                        .withStyle(ChatFormatting.YELLOW));
                lines.add(Component.translatable("guide.eliteforge.page6.tip3")
                        .withStyle(ChatFormatting.YELLOW));
                lines.add(Component.translatable("guide.eliteforge.page6.tip4")
                        .withStyle(ChatFormatting.YELLOW));
                lines.add(Component.translatable("guide.eliteforge.page6.tip5")
                        .withStyle(ChatFormatting.YELLOW));
                lines.add(Component.empty());
                lines.add(Component.translatable("guide.eliteforge.page6.config")
                        .withStyle(ChatFormatting.AQUA));
                lines.add(Component.literal("   /eliteforge info").withStyle(ChatFormatting.GRAY));
                lines.add(Component.empty());
                lines.add(Component.translatable("guide.eliteforge.page6.end")
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
                lines.add(Component.translatable("guide.eliteforge.page6.restart")
                        .withStyle(ChatFormatting.AQUA));
            }
            default -> {
                lines.add(Component.translatable("guide.eliteforge.error")
                        .withStyle(ChatFormatting.RED));
            }
        }

        return lines;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        int currentPage = stack.getOrCreateTag().getInt("eliteforge:guide_page");
        if (currentPage < 1 || currentPage > TOTAL_PAGES) {
            currentPage = 1;
        }
        tooltip.add(Component.translatable("guide.eliteforge.tooltip.1")
                .withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("guide.eliteforge.tooltip.2", currentPage, TOTAL_PAGES)
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("guide.eliteforge.tooltip.3")
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true; // Always show enchantment glow
    }
}
