package com.eliteforge.ability;

import com.eliteforge.util.LevelRoman;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

import javax.annotation.Nullable;

/**
 * Abstract base class for all EliteForge abilities.
 * Provides the core contract and default implementations for ability behavior.
 *
 * Each ability has:
 * - A unique ResourceLocation ID
 * - A category (ATTACK, DEFENSE, CONTROL, LEGENDARY, CREATOR)
 * - A translation key for localization
 * - A max level (default 5)
 * - A budget cost per level
 *
 * Subclasses override onApply, onTick, onAttack, onHurt, onDeath, onPlayerKill
 * to implement their specific behavior.
 */
public abstract class Ability {

    protected final ResourceLocation id;
    protected final AbilityCategory category;
    protected final String nameKey;
    protected final int maxLevel;
    protected final float budgetCost;

    /**
     * Constructs a new Ability with default max level of 5.
     *
     * @param id         Unique resource location identifier
     * @param category   The ability category
     * @param budgetCost Cost per level in the budget system
     */
    protected Ability(ResourceLocation id, AbilityCategory category, float budgetCost) {
        this(id, category, budgetCost, 5);
    }

    /**
     * Constructs a new Ability.
     *
     * @param id         Unique resource location identifier
     * @param category   The ability category
     * @param budgetCost Cost per level in the budget system
     * @param maxLevel   Maximum level this ability can reach
     */
    protected Ability(ResourceLocation id, AbilityCategory category, float budgetCost, int maxLevel) {
        this.id = id;
        this.category = category;
        this.budgetCost = budgetCost;
        this.maxLevel = maxLevel;
        this.nameKey = "ability." + id.getNamespace() + "." + id.getPath();
    }

    // ========== Global Scaling Helpers ==========
    // These read the server-side abilityCooldownScale / abilityDamageScale config
    // knobs so ability subclasses can opt-in to global scaling without each
    // reimplementing the config lookup. Call cooldownScale(...) when writing a
    // cooldown NBT value (divide the base cooldown by the scale) and
    // damageScale(...) on every direct damage amount the ability applies.

    /**
     * Scale a cooldown duration by the global {@code abilityCooldownScale} config.
     * A scale &gt; 1 makes abilities fire less often; &lt; 1 makes them fire more often.
     *
     * @param baseCooldownTicks the cooldown in ticks before scaling
     * @return the scaled cooldown (clamped to ≥ 1 tick)
     */
    public static int cooldownScale(int baseCooldownTicks) {
        double scale = com.eliteforge.config.EliteForgeConfig.SERVER.abilityCooldownScale.get();
        if (scale <= 0) scale = 1.0;
        return Math.max(1, (int) Math.round(baseCooldownTicks / scale));
    }

    /**
     * Scale a damage amount by the global {@code abilityDamageScale} config.
     *
     * @param baseDamage the damage before scaling
     * @return the scaled damage (never negative)
     */
    public static float damageScale(float baseDamage) {
        double scale = com.eliteforge.config.EliteForgeConfig.SERVER.abilityDamageScale.get();
        if (scale < 0) scale = 0.0;
        return (float) (baseDamage * scale);
    }

    // ========== Abstract / Overridable Behavior Methods ==========

    /**
     * Called when this ability is first applied to an entity.
     * Use for initial setup, adding persistent effects, etc.
     *
     * @param entity The entity receiving the ability
     * @param level  The ability level (1 to maxLevel)
     */
    public void onApply(LivingEntity entity, int level) {
        // Default: do nothing
    }

    /**
     * Called every tick while this ability is active on an entity.
     * Use for periodic effects, aura checks, etc.
     *
     * @param entity The entity with the ability
     * @param level  The ability level
     */
    public void onTick(LivingEntity entity, int level) {
        // Default: do nothing
    }

    /**
     * Called when the entity with this ability attacks another entity.
     *
     * @param attacker The entity with this ability (the attacker)
     * @param target   The entity being attacked
     * @param damage   The raw damage amount of the attack
     * @param level    The ability level
     */
    public void onAttack(LivingEntity attacker, LivingEntity target, float damage, int level) {
        // Default: do nothing
    }

    /**
     * Called when the entity with this ability is hurt.
     *
     * @param entity The entity with this ability that was hurt
     * @param damage The damage amount after reductions
     * @param level  The ability level
     */
    public void onHurt(LivingEntity entity, float damage, int level) {
        // Default: do nothing
    }

    /**
     * Called when the entity with this ability dies.
     *
     * @param entity The entity that died
     * @param level  The ability level
     */
    public void onDeath(LivingEntity entity, int level) {
        // Default: do nothing
    }

    /**
     * Called when a player kills the entity with this ability.
     *
     * @param entity The entity that was killed
     * @param player The player who killed the entity
     * @param level  The ability level
     */
    public void onPlayerKill(LivingEntity entity, net.minecraft.world.entity.player.Player player, int level) {
        // Default: do nothing
    }

    /**
     * Called when this ability is removed from an entity.
     * Use for cleanup, removing persistent effects, attribute modifiers, etc.
     *
     * @param entity The entity losing the ability
     * @param level  The ability level at time of removal
     */
    public void onRemove(LivingEntity entity, int level) {
        // Default: do nothing
    }

    // ========== Utility Methods ==========

    /**
     * Calculates the total budget cost for this ability at the given level.
     *
     * @param level The ability level
     * @return The total budget cost (base cost × level)
     */
    public float getBudgetCost(int level) {
        return budgetCost * level;
    }

    /**
     * Creates a translatable display name component with the ability level
     * shown as a Roman numeral.
     * <p>
     * Q1: Now uses the centralized {@link LevelRoman} utility (extended to
     * support levels 1-20 in ASCII mode) instead of a duplicated local array.
     * Levels 1-5 use Unicode Roman numerals (Ⅰ-Ⅴ) for aesthetic consistency
     * with the rest of the mod; levels 6-20 use ASCII Roman numerals; levels
     * above 20 fall back to the raw integer.
     *
     * @param level The ability level
     * @return A MutableComponent with the ability name and level
     */
    public MutableComponent getDisplayName(int level) {
        String romanNumeral = LevelRoman.format(level);
        return Component.translatable(nameKey)
                .append(Component.literal(" " + romanNumeral)
                        .withStyle(category.getChatColor()));
    }

    /**
     * Determines if this ability can coexist with another ability.
     * Delegates to {@link MutualExclusion#isMutuallyExclusive} as the single
     * source of truth for all mutual exclusion rules, including creator-tier
     * exclusivity.
     * <p>
     * Q2 (clarified): This method is consulted by the normal ability-add
     * pipeline (e.g. {@code AbilityGenerator}, {@code EliteEcosystem.nurtureNearbyElites},
     * {@code DynamicStrengthening.grantTemporaryAbility}) to decide whether a
     * candidate ability may be added to an entity that already has existing
     * abilities. Creator-tier abilities always return {@code false} here, so
     * they are never added through that pipeline.
     * <p>
     * The C4 Assimilate and C7 Reincarnation abilities are the <em>only</em>
     * two systems that can grant additional non-creator abilities to a
     * creator-tier entity, and they do so by calling
     * {@link com.eliteforge.capability.EliteData#addAbility} directly —
     * bypassing this {@code canCoexistWith} check. This is by design and is
     * the documented exception to the creator exclusivity rule.
     *
     * @param other The other ability to check
     * @return true if both abilities can be on the same entity
     */
    public boolean canCoexistWith(Ability other) {
        if (other == null) return true; // Null ability is not mutually exclusive — trivially compatible
        if (other == this) return true; // Same ability can coexist with itself (trivially)
        return !MutualExclusion.isMutuallyExclusive(this, other);
    }

    /**
     * Whether this ability is currently enabled.
     * Can be overridden for config-based enabling/disabling.
     *
     * @return true if the ability is enabled
     */
    public boolean isEnabled() {
        return true;
    }

    /**
     * The weight of this ability for random selection during ability generation.
     * Higher weight = more likely to be selected.
     * Legendary abilities should have lower weight.
     *
     * @return Selection weight
     */
    public int getWeight() {
        return switch (category) {
            case ATTACK -> 10;
            case DEFENSE -> 10;
            case CONTROL -> 8;
            case LEGENDARY -> 3;
            case CREATOR -> 1; // Informational only — creator abilities are never selected through the budget/generation pipeline; they are assigned separately
        };
    }

    // ========== Getters ==========

    public ResourceLocation getId() {
        return id;
    }

    public String getIdString() {
        return id.toString();
    }

    public AbilityCategory getCategory() {
        return category;
    }

    public String getNameKey() {
        return nameKey;
    }

    public int getMaxLevel() {
        return maxLevel;
    }

    public float getBaseBudgetCost() {
        return budgetCost;
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Ability ability = (Ability) obj;
        return id.equals(ability.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "Ability{" + id + ", category=" + category + ", maxLevel=" + maxLevel + "}";
    }
}
