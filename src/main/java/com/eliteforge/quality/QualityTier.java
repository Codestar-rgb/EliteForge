package com.eliteforge.quality;

import net.minecraft.ChatFormatting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Quality tier enum representing the rarity/quality level of elite mobs and
 * their drops. Higher tiers have better stats, more dramatic visual effects,
 * and increased loot bonuses.
 * <p>
 * Tiers (in order of rarity):
 * <ul>
 *   <li>NORMAL (普通) - WHITE, weight 50, lootBonus 1.0</li>
 *   <li>GOOD (优良) - GREEN, weight 30, lootBonus 1.5</li>
 *   <li>FINE (精良) - BLUE, weight 15, lootBonus 2.0</li>
 *   <li>EPIC (史诗) - LIGHT_PURPLE, weight 4, lootBonus 3.0</li>
 *   <li>LEGENDARY (传说) - GOLD, weight 1, lootBonus 5.0</li>
 *   <li>MYTHIC (神话) - DARK_RED, weight 0 (cannot be rolled naturally), lootBonus 10.0 — only assigned via creator-tier conversion or awakening</li>
 * </ul>
 */
public enum QualityTier {

    NORMAL("普通", ChatFormatting.WHITE, 50, 1.0f, false, true),
    GOOD("优良", ChatFormatting.GREEN, 30, 1.5f, false, true),
    FINE("精良", ChatFormatting.BLUE, 15, 2.0f, true, true),
    EPIC("史诗", ChatFormatting.LIGHT_PURPLE, 4, 3.0f, true, true),
    LEGENDARY("传说", ChatFormatting.GOLD, 1, 5.0f, true, true),
    MYTHIC("神话", ChatFormatting.DARK_RED, 0, 10.0f, true, false);

    private final String displayName;
    private final ChatFormatting chatColor;
    private final int weight;
    private final float lootBonus;
    private final boolean hasGlow;
    /** Whether this tier can be obtained through weighted random selection. MYTHIC is only assigned explicitly. */
    private final boolean canRollNaturally;

    QualityTier(String displayName, ChatFormatting chatColor, int weight, float lootBonus, boolean hasGlow, boolean canRollNaturally) {
        this.displayName = displayName;
        this.chatColor = chatColor;
        this.weight = weight;
        this.lootBonus = lootBonus;
        this.hasGlow = hasGlow;
        this.canRollNaturally = canRollNaturally;
    }

    /**
     * Get the Chinese display name for this quality tier.
     *
     * @return the display name
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Get the chat formatting color for this quality tier.
     *
     * @return the chat color
     */
    public ChatFormatting getChatColor() {
        return chatColor;
    }

    /**
     * Get the weighted random selection weight for this tier.
     * Higher weight = more common.
     *
     * @return the weight
     */
    public int getWeight() {
        return weight;
    }

    /**
     * Get the loot bonus multiplier for this quality tier.
     * Applied to the base loot table drops.
     *
     * @return the loot bonus multiplier
     */
    public float getLootBonus() {
        return lootBonus;
    }

    /**
     * Check if items of this quality tier should have an enchantment glow effect.
     *
     * @return true if items should glow
     */
    public boolean hasGlow() {
        return hasGlow;
    }

    /**
     * Check if this quality tier can be obtained through weighted random selection.
     * MYTHIC quality cannot be rolled naturally — it is only assigned when an
     * entity becomes a creator-tier elite (via spawn conversion or awakening).
     *
     * @return true if this tier can appear in random quality rolls
     */
    public boolean canRollNaturally() {
        return canRollNaturally;
    }

    /**
     * Get the quality tier that is one step higher than this one.
     *
     * @return the next higher tier, or this tier if already at max
     */
    public QualityTier next() {
        int ordinal = ordinal();
        QualityTier[] tiers = values();
        return ordinal < tiers.length - 1 ? tiers[ordinal + 1] : this;
    }

    /**
     * Get the quality tier that is one step lower than this one.
     *
     * @return the next lower tier, or this tier if already at min
     */
    public QualityTier previous() {
        int ordinal = ordinal();
        return ordinal > 0 ? values()[ordinal - 1] : this;
    }

    /** Cached total weight of all naturally-rollable quality tiers.
     *  Computed once in a static initializer since weights are constant. */
    private static final int TOTAL_WEIGHT;

    /** Cached unmodifiable list of tiers that can be rolled naturally.
     *  Avoids re-filtering values() on every call to weightedRandom / weightedRandomWithBonus. */
    private static final List<QualityTier> ROLLABLE_TIERS;

    static {
        List<QualityTier> rollable = new ArrayList<>();
        int total = 0;
        for (QualityTier tier : values()) {
            if (tier.canRollNaturally) {
                rollable.add(tier);
                total += tier.weight;
            }
        }
        ROLLABLE_TIERS = Collections.unmodifiableList(rollable);
        TOTAL_WEIGHT = total;
    }

    /**
     * Get the total weight of all naturally-rollable quality tiers combined.
     * MYTHIC is excluded since it has weight 0 and cannot be rolled.
     * The result is cached and does not recompute on each call.
     *
     * @return total weight of rollable tiers
     */
    public static int totalWeight() {
        return TOTAL_WEIGHT;
    }

    /**
     * Select a quality tier using weighted random selection.
     *
     * @param random a random number generator
     * @return the selected quality tier
     */
    public static QualityTier weightedRandom(java.util.Random random) {
        if (TOTAL_WEIGHT <= 0) return NORMAL;
        int roll = random.nextInt(TOTAL_WEIGHT);
        int cumulative = 0;

        for (QualityTier tier : ROLLABLE_TIERS) {
            cumulative += tier.weight;
            if (roll < cumulative) {
                return tier;
            }
        }

        return NORMAL;
    }

    /**
     * Select a quality tier using weighted random selection with bonus modifiers.
     * Higher level, chunk heat, and player experience shift the distribution
     * toward higher tiers.
     *
     * @param random    a random number generator
     * @param level     the elite mob difficulty level (1-5)
     * @param chunkHeat the chunk heat value
     * @param playerExp the player experience value
     * @return the selected quality tier
     */
    public static QualityTier weightedRandomWithBonus(java.util.Random random, int level, float chunkHeat, float playerExp) {
        // Calculate adjusted weights based on level, heat, and experience
        // MYTHIC is excluded from natural rolling — it is only assigned explicitly
        double[] adjustedWeights = new double[ROLLABLE_TIERS.size()];
        double totalAdjusted = 0;

        for (int i = 0; i < ROLLABLE_TIERS.size(); i++) {
            QualityTier tier = ROLLABLE_TIERS.get(i);
            double weight = tier.weight;

            // Level bonus: higher level shifts weight toward higher tiers
            double levelBonus = (level - 1) * 0.3 * i;
            weight *= (1.0 + levelBonus);

            // Chunk heat bonus: higher heat shifts weight toward higher tiers
            double heatBonus = (chunkHeat / 50.0) * 0.2 * i;
            weight *= (1.0 + heatBonus);

            // Player experience bonus: higher experience shifts weight toward higher tiers
            double expBonus = (playerExp / 50.0) * 0.15 * i;
            weight *= (1.0 + expBonus);

            adjustedWeights[i] = weight;
            totalAdjusted += weight;
        }

        if (totalAdjusted <= 0) return NORMAL;

        // Weighted random selection
        double roll = random.nextDouble() * totalAdjusted;
        double cumulative = 0;

        for (int i = 0; i < ROLLABLE_TIERS.size(); i++) {
            cumulative += adjustedWeights[i];
            if (roll < cumulative) {
                return ROLLABLE_TIERS.get(i);
            }
        }

        return NORMAL;
    }

    /**
     * Parse a QualityTier from a string name, defaulting to NORMAL on failure.
     *
     * @param name the string name to parse
     * @return the parsed QualityTier, or NORMAL if invalid
     */
    public static QualityTier fromName(String name) {
        if (name == null || name.isEmpty()) {
            return NORMAL;
        }
        try {
            return valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return NORMAL;
        }
    }

    /**
     * Get the formatting prefix string for use in text components.
     *
     * @return the §-based color code string
     */
    public String getColorCode() {
        return chatColor.toString();
    }
}
