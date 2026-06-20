package com.eliteforge.client.jade;

import com.eliteforge.EliteForge;
import com.eliteforge.ability.Ability;
import com.eliteforge.ability.AbilityRegistry;
import com.eliteforge.capability.EliteCapability;
import com.eliteforge.capability.EliteData;
import com.eliteforge.quality.QualityTier;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * EliteForgeJadeProvider — provides elite mob data for Jade mod display.
 * <p>
 * This class provides tooltip lines that can be used by Jade or any other
 * HUD overlay mod. It is called via reflection from the Jade plugin.
 * <p>
 * The provider checks both the entity capability (for integrated server)
 * and EliteCapabilityStorage (for dedicated server client) to get elite data.
 */
public class EliteForgeJadeProvider {

    /**
     * Get the elite tooltip lines for Jade display.
     * Called by Jade when looking at an entity.
     *
     * @param entity the entity being looked at
     * @return list of tooltip lines, or empty list if not elite
     */
    public static List<Component> getTooltip(LivingEntity entity) {
        List<Component> lines = new ArrayList<>();
        if (entity == null) return lines;

        // Try capability first, then storage
        EliteData data = entity.getCapability(EliteCapability.CAPABILITY)
                .map(EliteCapability::getEliteData)
                .orElse(null);
        if (data == null || !data.isElite()) {
            // Fall back to client-side storage
            data = com.eliteforge.capability.EliteCapabilityStorage.getEliteData(entity);
        }
        if (data == null || !data.isElite()) return lines;

        return buildTooltip(data);
    }

    /**
     * Build tooltip lines from EliteData.
     */
    public static List<Component> buildTooltip(EliteData eliteData) {
        List<Component> lines = new ArrayList<>();
        if (eliteData == null || !eliteData.isElite()) return lines;

        QualityTier tier = eliteData.getQualityTier();
        ChatFormatting tierColor = tier.getChatColor();
        int level = eliteData.getLevel();

        // Header: localized quality name + level. Previously hardcoded raw enum name
        // and English " Lv." prefix.
        lines.add(Component.translatable("message.eliteforge.jade.header",
                        Component.translatable("quality.eliteforge." + tier.name().toLowerCase()), level)
                .withStyle(tierColor, ChatFormatting.BOLD));
        lines.add(Component.translatable("message.eliteforge.jade.separator").withStyle(ChatFormatting.DARK_GRAY));

        // Abilities
        Map<String, Integer> abilities = eliteData.getAbilities();
        if (abilities != null && !abilities.isEmpty()) {
            List<Map.Entry<String, Integer>> sorted = new ArrayList<>(abilities.entrySet());
            sorted.sort((a, b) -> {
                Ability abA = AbilityRegistry.getAbility(a.getKey());
                Ability abB = AbilityRegistry.getAbility(b.getKey());
                if (abA == null && abB == null) return 0;
                if (abA == null) return 1;
                if (abB == null) return -1;
                return abA.getCategory().ordinal() - abB.getCategory().ordinal();
            });

            for (Map.Entry<String, Integer> entry : sorted) {
                Ability ability = AbilityRegistry.getAbility(entry.getKey());
                if (ability == null) continue;
                ChatFormatting catColor = ability.getCategory().getChatColor();
                String roman = toRoman(entry.getValue());
                lines.add(Component.translatable(ability.getNameKey())
                        .append(Component.literal(" " + roman))
                        .withStyle(catColor));
            }
        } else {
            lines.add(Component.translatable("message.eliteforge.jade.no_abilities").withStyle(ChatFormatting.GRAY));
        }

        if (eliteData.isCreatorEntity()) {
            lines.add(Component.translatable("message.eliteforge.jade.creator_tier")
                    .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
        }

        return lines;
    }

    private static String toRoman(int level) {
        if (level <= 0) return "";
        return switch (level) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            default -> level >= 5 ? "V" : String.valueOf(level);
        };
    }
}
