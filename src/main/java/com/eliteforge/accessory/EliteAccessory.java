package com.eliteforge.accessory;

import com.eliteforge.quality.QualityTier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Base class for all EliteForge accessories.
 * Curios API compatible — uses ICurioItem interface via reflection
 * (soft dependency, works without Curios installed).
 * <p>
 * Accessories provide stat bonuses based on quality tier.
 * Higher quality = stronger bonuses. Same-type accessories do NOT stack
 * (only the highest quality one takes effect).
 */
public class EliteAccessory extends Item {

    public enum AccessoryType {
        RING("elite_ring", 0),
        NECKLACE("elite_necklace", 1),
        BELT("elite_belt", 2),
        CHARM("elite_charm", 3),
        CROWN("elite_crown", 4),
        // v0.6.0 new types
        BRACER("elite_bracer", 5),
        CAPE("elite_cape", 6),
        // v0.6.5 new types
        SIGIL("sigil_of_warding", 7),
        INSIGNIA("insignia_of_command", 8),
        TALISMAN("talisman_of_greed", 9),
        // v0.7.0 new types
        ORB("aether_orb", 10),
        WARD_STONE("ward_stone", 11),
        // v0.7.5 new types
        RUNE("rune_of_haste", 12),
        AEGIS("aegis_plate", 13),
        // v0.8.0 new types
        MIRROR("mirror_of_reflection", 14),
        CATALYST("catalyst_stone", 15),
        // v0.8.5 new types
        VEIL("veil_of_shadows", 16),
        CORE_ACC("resonance_core", 17),
        // v0.9.0 new types
        RELIC("ancient_relic_amulet", 18),
        COMPASS("hunters_compass", 19);

        private final String id;
        private final int slotIndex;

        AccessoryType(String id, int slotIndex) {
            this.id = id;
            this.slotIndex = slotIndex;
        }

        public String getId() { return id; }
        public int getSlotIndex() { return slotIndex; }
    }

    private final AccessoryType accessoryType;

    public EliteAccessory(AccessoryType type, Properties properties) {
        super(properties.stacksTo(1));
        this.accessoryType = type;
    }

    public AccessoryType getAccessoryType() {
        return accessoryType;
    }

    /**
     * Get the quality tier stored in the item's NBT.
     */
    public QualityTier getQuality(ItemStack stack) {
        CompoundTag tag = stack.getOrCreateTag();
        if (tag.contains("eliteforge:quality")) {
            return QualityTier.fromName(tag.getString("eliteforge:quality"));
        }
        return QualityTier.NORMAL;
    }

    /**
     * Set the quality tier in the item's NBT.
     */
    public void setQuality(ItemStack stack, QualityTier quality) {
        stack.getOrCreateTag().putString("eliteforge:quality", quality.name());
    }

    /**
     * Get the upgrade level (0-5) stored in NBT.
     */
    public int getUpgradeLevel(ItemStack stack) {
        return stack.getOrCreateTag().getInt("eliteforge:upgrade");
    }

    /**
     * Set the upgrade level.
     */
    public void setUpgradeLevel(ItemStack stack, int level) {
        stack.getOrCreateTag().putInt("eliteforge:upgrade", level);
    }

    /**
     * Get the health bonus for this accessory. v0.6.5: type-aware multipliers —
     * CROWN/BELT get +50% HP, SIGIL gets +30% HP, others get base.
     */
    public float getHealthBonus(ItemStack stack) {
        QualityTier quality = getQuality(stack);
        int upgrade = getUpgradeLevel(stack);
        float base = switch (quality) {
            case NORMAL -> 2.0f;
            case GOOD -> 5.0f;
            case FINE -> 10.0f;
            case EPIC -> 20.0f;
            case LEGENDARY -> 40.0f;
            case MYTHIC -> 80.0f;
        };
        // Type-aware multiplier
        float typeMult = switch (accessoryType) {
            case CROWN, BELT -> 1.5f;
            case SIGIL -> 1.3f;
            case ORB -> 1.2f;
            case AEGIS -> 1.8f;
            case MIRROR -> 1.4f;
            case CATALYST -> 1.1f;
            case VEIL -> 1.3f; // v0.8.5: evasion
            case CORE_ACC -> 1.5f; // v0.8.5: resonance
            default -> 1.0f;
        };
        // Global config multiplier (v0.6.5)
        float globalMult = 1.0f;
        try { globalMult = com.eliteforge.config.EliteForgeConfig.SERVER.accessoryBonusMultiplier.get().floatValue(); } catch (Exception ignored) {}
        return base * typeMult * globalMult * (1.0f + upgrade * 0.2f);
    }

    /**
     * Get the damage bonus for this accessory. v0.6.5: type-aware —
     * RING/GAUNTLET/INSIGNIA get +50% ATK, TALISMAN gets +20%.
     */
    public float getDamageBonus(ItemStack stack) {
        QualityTier quality = getQuality(stack);
        int upgrade = getUpgradeLevel(stack);
        float base = switch (quality) {
            case NORMAL -> 0.5f;
            case GOOD -> 1.0f;
            case FINE -> 2.0f;
            case EPIC -> 4.0f;
            case LEGENDARY -> 8.0f;
            case MYTHIC -> 16.0f;
        };
        float typeMult = switch (accessoryType) {
            case RING, INSIGNIA -> 1.5f;
            case TALISMAN -> 1.2f;
            case ORB -> 1.2f;
            case RUNE -> 1.8f;
            case MIRROR -> 1.3f;
            case CATALYST -> 1.4f;
            case VEIL -> 1.6f; // v0.8.5: speed/evasion
            case CORE_ACC -> 1.3f;
            default -> 1.0f;
        };
        float globalMult = 1.0f;
        try { globalMult = com.eliteforge.config.EliteForgeConfig.SERVER.accessoryBonusMultiplier.get().floatValue(); } catch (Exception ignored) {}
        return base * typeMult * globalMult * (1.0f + upgrade * 0.2f);
    }

    /**
     * Get the armor bonus for this accessory. v0.6.5: type-aware —
     * BRACER/SIGIL get +50% DEF, CAPE gets +20%.
     */
    public int getArmorBonus(ItemStack stack) {
        QualityTier quality = getQuality(stack);
        int upgrade = getUpgradeLevel(stack);
        int base = switch (quality) {
            case NORMAL -> 1;
            case GOOD -> 2;
            case FINE -> 3;
            case EPIC -> 5;
            case LEGENDARY -> 8;
            case MYTHIC -> 12;
        };
        float typeMult = switch (accessoryType) {
            case BRACER, SIGIL -> 1.5f;
            case CAPE -> 1.2f;
            case WARD_STONE -> 2.0f;
            case ORB -> 1.2f;
            case AEGIS -> 2.5f;
            case MIRROR -> 1.6f;
            case CATALYST -> 1.3f;
            case VEIL -> 1.4f; // v0.8.5
            case CORE_ACC -> 1.6f; // v0.8.5
            default -> 1.0f;
        };
        float globalMult = 1.0f;
        try { globalMult = com.eliteforge.config.EliteForgeConfig.SERVER.accessoryBonusMultiplier.get().floatValue(); } catch (Exception ignored) {}
        return Math.round(base * typeMult * globalMult) + upgrade;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        QualityTier quality = getQuality(stack);
        int upgrade = getUpgradeLevel(stack);

        tooltip.add(Component.translatable("tooltip.eliteforge.quality")
                .append(": ")
                .append(Component.translatable("quality.eliteforge." + quality.name().toLowerCase()))
                .withStyle(quality.getChatColor()));

        if (upgrade > 0) {
            tooltip.add(Component.translatable("tooltip.eliteforge.upgrade")
                    .append(": +" + upgrade)
                    .withStyle(net.minecraft.ChatFormatting.GOLD));
        }

        tooltip.add(Component.translatable("tooltip.eliteforge.accessory.hp", getHealthBonus(stack))
                .withStyle(net.minecraft.ChatFormatting.RED));
        tooltip.add(Component.translatable("tooltip.eliteforge.accessory.atk", getDamageBonus(stack))
                .withStyle(net.minecraft.ChatFormatting.RED));
        tooltip.add(Component.translatable("tooltip.eliteforge.accessory.def", getArmorBonus(stack))
                .withStyle(net.minecraft.ChatFormatting.BLUE));

        super.appendHoverText(stack, level, tooltip, flag);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        QualityTier quality = getQuality(stack);
        return quality.ordinal() >= QualityTier.EPIC.ordinal();
    }
}
