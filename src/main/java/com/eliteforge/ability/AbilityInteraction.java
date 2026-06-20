package com.eliteforge.ability;

import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.*;

/**
 * Defines ability interaction system: synergies and counters.
 * <p>
 * Synergies are abilities that work better together, providing bonus effects
 * when both abilities are present on the same elite.
 * <p>
 * Counters are abilities that are weakened by specific player actions or items.
 */
public final class AbilityInteraction {

    private AbilityInteraction() {
        // Utility class - no instantiation
    }

    // ==================== Synergy System ====================

    /**
     * Represents a synergy between two abilities that provides a bonus effect.
     */
    public record SynergyPair(String abilityA, String abilityB, String synergyId, String description, float bonusMultiplier) {
    }

    private static final List<SynergyPair> SYNERGY_PAIRS = new ArrayList<>();

    /**
     * Bidirectional synergy lookup cache for O(1) access by ability ID pair.
     * Structure: outer map key = abilityA, inner map key = abilityB → SynergyPair.
     * Each pair is stored in both directions (a→b and b→a) so that
     * {@link #getSynergy(String, String)} returns the same result regardless of argument order.
     */
    private static final Map<String, Map<String, SynergyPair>> SYNERGY_CACHE = new HashMap<>();

    static {
        // Fire + SpiritBurn = "Inferno" — fire damage increases spirit burn damage by 50%
        addSynergy(new SynergyPair(
                "eliteforge:fire", "eliteforge:spirit_burn",
                "inferno", "Inferno: Fire damage increases Spirit Burn damage by 50%", 1.5f
        ));
        // IronWall + Armor = "Fortress" — additional 10% damage reduction
        addSynergy(new SynergyPair(
                "eliteforge:iron_wall", "eliteforge:armor",
                "fortress", "Fortress: Additional 10% damage reduction", 1.1f
        ));
        // Gravity + Slow = "Gravity Well" — double pull strength
        addSynergy(new SynergyPair(
                "eliteforge:gravity", "eliteforge:slow",
                "gravity_well", "Gravity Well: Double pull strength", 2.0f
        ));
        // Lightning + Storm = "Thunder Lord" — storm fires lightning bolts
        addSynergy(new SynergyPair(
                "eliteforge:lightning", "eliteforge:storm",
                "thunder_lord", "Thunder Lord: Storm fires lightning bolts", 1.5f
        ));
        // Poison + SpiritBurn = "Toxic Soul" — poison damage increases spirit burn damage by 40%
        addSynergy(new SynergyPair(
                "eliteforge:poison", "eliteforge:spirit_burn",
                "toxic_soul", "Toxic Soul: Poison damage increases Spirit Burn damage by 40%", 1.4f
        ));
        // Freeze + Slow = "Absolute Zero" — double slow strength
        addSynergy(new SynergyPair(
                "eliteforge:freeze", "eliteforge:slow",
                "absolute_zero", "Absolute Zero: Double slow strength", 2.0f
        ));
        // Necromancy + Clone = "Undead Legion" — clones also summon undead
        addSynergy(new SynergyPair(
                "eliteforge:necromancy", "eliteforge:clone",
                "undead_legion", "Undead Legion: Clones also summon undead", 1.5f
        ));

        // === Creator-Tier Synergies (abilities granted through assimilation/reincarnation) ===

        // Fire + Necromancy = "Infernal Dead" — undead summoned by necromancy are on fire
        addSynergy(new SynergyPair(
                "eliteforge:fire", "eliteforge:necromancy",
                "infernal_dead", "Infernal Dead: Undead summoned by Necromancy are on fire", 1.3f
        ));
        // Lightning + Clone = "Spark Split" — clones also trigger lightning on spawn
        addSynergy(new SynergyPair(
                "eliteforge:lightning", "eliteforge:clone",
                "spark_split", "Spark Split: Clones also trigger lightning on spawn", 1.4f
        ));
        // Thorns + IronWall = "Spiked Fortress" — thorns damage increased when Iron Wall is active
        addSynergy(new SynergyPair(
                "eliteforge:thorns", "eliteforge:iron_wall",
                "spiked_fortress", "Spiked Fortress: Thorns damage increased when Iron Wall is active", 1.25f
        ));
        // Regen + Absorption = "Life Drain" — absorption also heals when regen is active
        addSynergy(new SynergyPair(
                "eliteforge:regen", "eliteforge:absorption",
                "life_drain", "Life Drain: Absorption also heals when Regeneration is active", 1.3f
        ));
        // Storm + Doom = "Apocalypse" — doom countdown triggers storm projectiles
        addSynergy(new SynergyPair(
                "eliteforge:storm", "eliteforge:doom",
                "apocalypse", "Apocalypse: Doom countdown triggers Storm projectiles", 1.5f
        ));
        // SpiritBurn + Doom = "Soul Reaper" — spirit burn damage increases doom damage
        addSynergy(new SynergyPair(
                "eliteforge:spirit_burn", "eliteforge:doom",
                "soul_reaper", "Soul Reaper: Spirit Burn damage increases Doom damage", 1.4f
        ));

        // === Additional Synergies ===

        // Fire + Explosion = "Inferno Blast" — explosion damage ignites all affected targets
        addSynergy(new SynergyPair(
                "eliteforge:fire", "eliteforge:explosion",
                "inferno_blast", "Inferno Blast: Explosion damage ignites all affected targets", 1.3f
        ));
        // Regen + Immunity = "Immortal" — immune effect duration doubled while regen is active
        addSynergy(new SynergyPair(
                "eliteforge:regen", "eliteforge:immunity",
                "immortal", "Immortal: Immunity effect duration doubled while Regeneration is active", 1.5f
        ));
        // Web + Immobilize = "Death Prison" — immobilize duration extended by 50% when web is present
        addSynergy(new SynergyPair(
                "eliteforge:web", "eliteforge:immobilize",
                "death_prison", "Death Prison: Immobilize duration extended 50% when Web is present", 1.5f
        ));
        // Armor + IronWall = "Fortress" — iron wall reduction increased by armor bonus
        addSynergy(new SynergyPair(
                "eliteforge:armor", "eliteforge:iron_wall",
                "fortress_armor", "Fortress: Armor bonus increases Iron Wall reduction", 1.2f
        ));
        // Lightning + Wither = "Decay Storm" — wither effect spreads to nearby players during lightning
        addSynergy(new SynergyPair(
                "eliteforge:lightning", "eliteforge:wither",
                "decay_storm", "Decay Storm: Wither effect spreads to nearby players during Lightning", 1.3f
        ));

        // === Creator-Tier Synergies (abilities on different entities) ===

        // Nexus + Commander = "Hive Mind" — commander squad range increased by nexus range
        addSynergy(new SynergyPair(
                "eliteforge:creator_nexus", "eliteforge:creator_commander",
                "hive_mind", "Hive Mind: Commander squad range increased by Nexus range", 1.5f
        ));
        // Dominion + Evolution = "Forged Domain" — domain increases evolution damage threshold reduction
        addSynergy(new SynergyPair(
                "eliteforge:creator_dominion", "eliteforge:creator_evolution",
                "forged_domain", "Forged Domain: Dominion reduces Evolution damage threshold inside domain", 1.4f
        ));
    }

    /**
     * Adds a synergy pair to both the list and the bidirectional cache.
     */
    private static void addSynergy(SynergyPair pair) {
        SYNERGY_PAIRS.add(pair);
        // Store in both directions for O(1) lookup
        SYNERGY_CACHE.computeIfAbsent(pair.abilityA(), k -> new HashMap<>()).put(pair.abilityB(), pair);
        SYNERGY_CACHE.computeIfAbsent(pair.abilityB(), k -> new HashMap<>()).put(pair.abilityA(), pair);
    }

    /**
     * Check if two abilities have a synergy. Uses the bidirectional cache for O(1) lookup,
     * consistent with MutualExclusion's approach.
     *
     * @param abilityA first ability ID
     * @param abilityB second ability ID
     * @return the synergy if found, or null
     */
    public static SynergyPair getSynergy(String abilityA, String abilityB) {
        Map<String, SynergyPair> inner = SYNERGY_CACHE.get(abilityA);
        return (inner != null) ? inner.get(abilityB) : null;
    }

    /**
     * Check if an entity has a specific synergy active.
     *
     * @param entity     the entity to check
     * @param synergyId  the synergy ID to check for
     * @return true if the entity has both abilities for the synergy
     */
    public static boolean hasSynergy(LivingEntity entity, String synergyId) {
        return entity.getCapability(com.eliteforge.capability.EliteCapability.CAPABILITY)
                .map(cap -> {
                    if (!cap.isElite()) return false;
                    Map<String, Integer> abilities = cap.getEliteData().getAbilities();
                    for (SynergyPair pair : SYNERGY_PAIRS) {
                        if (pair.synergyId().equals(synergyId) &&
                            abilities.containsKey(pair.abilityA()) &&
                            abilities.containsKey(pair.abilityB())) {
                            return true;
                        }
                    }
                    return false;
                })
                .orElse(false);
    }

    /**
     * Get the synergy bonus multiplier for two abilities on the same entity.
     *
     * @param entity   the entity
     * @param abilityA first ability ID
     * @param abilityB second ability ID
     * @return the bonus multiplier, or 1.0 if no synergy
     */
    public static float getSynergyMultiplier(LivingEntity entity, String abilityA, String abilityB) {
        SynergyPair synergy = getSynergy(abilityA, abilityB);
        if (synergy == null) return 1.0f;

        return entity.getCapability(com.eliteforge.capability.EliteCapability.CAPABILITY)
                .map(cap -> {
                    if (!cap.isElite()) return 1.0f;
                    Map<String, Integer> abilities = cap.getEliteData().getAbilities();
                    if (abilities.containsKey(abilityA) && abilities.containsKey(abilityB)) {
                        return synergy.bonusMultiplier();
                    }
                    return 1.0f;
                })
                .orElse(1.0f);
    }

    /**
     * Get all active synergies for an entity.
     *
     * @param entity the entity
     * @return list of active synergies
     */
    public static List<SynergyPair> getActiveSynergies(LivingEntity entity) {
        List<SynergyPair> active = new ArrayList<>();
        entity.getCapability(com.eliteforge.capability.EliteCapability.CAPABILITY)
                .ifPresent(cap -> {
                    if (!cap.isElite()) return;
                    Map<String, Integer> abilities = cap.getEliteData().getAbilities();
                    for (SynergyPair pair : SYNERGY_PAIRS) {
                        if (abilities.containsKey(pair.abilityA()) && abilities.containsKey(pair.abilityB())) {
                            active.add(pair);
                        }
                    }
                });
        return active;
    }

    /**
     * Calculate the combined synergy damage bonus multiplier for an elite entity.
     * <p>
     * Iterates all active synergies on the entity and accumulates their bonus
     * contributions additively (each synergy's {@code bonusMultiplier - 1.0}),
     * capped at the configured {@code maxSynergyBonus} (default 50%) to prevent
     * runaway scaling.
     * <p>
     * Example: an entity with Inferno (1.5x) and Toxic Soul (1.4x) active
     * gets a combined bonus of (0.5 + 0.4) = 0.9, capped to 0.5, yielding
     * a final multiplier of 1.5x on outgoing damage.
     * <p>
     * Returns 1.0 (no bonus) when the {@code enableSynergyBonus} server config
     * is disabled, or when the entity has no active synergies.
     * <p>
     * Creator-tier synergies between two creator abilities (e.g. Nexus +
     * Commander) are excluded here because creator abilities are mutually
     * exclusive on a single entity (except C4 Assimilate + C7 Reincarnation).
     * Those cross-creator synergies are handled separately at the ecosystem level.
     *
     * @param entity the elite entity
     * @return combined damage multiplier (1.0 = no bonus, up to 1.0 + maxSynergyBonus)
     */
    public static float getCombinedSynergyBonus(LivingEntity entity) {
        // Check config toggle — default to enabled if config unavailable (client side)
        try {
            if (!com.eliteforge.config.EliteForgeConfig.SERVER.enableSynergyBonus.get()) {
                return 1.0f;
            }
        } catch (Exception e) {
            // Config not available (client side) — default to enabled
        }

        float[] bonus = {0.0f};
        entity.getCapability(com.eliteforge.capability.EliteCapability.CAPABILITY)
                .ifPresent(cap -> {
                    if (!cap.isElite()) return;
                    Map<String, Integer> abilities = cap.getEliteData().getAbilities();
                    for (SynergyPair pair : SYNERGY_PAIRS) {
                        if (abilities.containsKey(pair.abilityA()) && abilities.containsKey(pair.abilityB())) {
                            // Skip creator-creator synergies (they can't both be on one entity
                            // except C4+C7, which is handled separately)
                            if (pair.abilityA().startsWith("eliteforge:creator_")
                                    && pair.abilityB().startsWith("eliteforge:creator_")) {
                                continue;
                            }
                            bonus[0] += (pair.bonusMultiplier() - 1.0f);
                        }
                    }
                });
        // Cap the total additive bonus at the configured maximum
        float maxBonus;
        try {
            maxBonus = (float) com.eliteforge.config.EliteForgeConfig.SERVER.maxSynergyBonus.get().doubleValue();
        } catch (Exception e) {
            maxBonus = 0.5f; // Default fallback
        }
        bonus[0] = Math.min(bonus[0], maxBonus);
        return 1.0f + bonus[0];
    }

    // ==================== Counter System ====================

    /**
     * Represents a counter: a player action or item that weakens an ability.
     */
    public record CounterEntry(String abilityId, String counterType, String description, float reductionMultiplier) {
    }

    private static final List<CounterEntry> COUNTER_ENTRIES = new ArrayList<>();

    static {
        // Fire → Quench Stone extinguishes and stuns briefly
        COUNTER_ENTRIES.add(new CounterEntry(
                "eliteforge:fire", "quench_stone",
                "Quench Stone extinguishes fire abilities and stuns briefly", 0.3f
        ));
        // Phase → Hits while invisible deal double damage back
        COUNTER_ENTRIES.add(new CounterEntry(
                "eliteforge:phase", "invisibility_hit",
                "Hits while invisible deal double damage back to the elite", 2.0f
        ));
        // Regen → Wither effect reduces regen by 50%
        COUNTER_ENTRIES.add(new CounterEntry(
                "eliteforge:regen", "wither_effect",
                "Wither effect reduces regeneration by 50%", 0.5f
        ));

        // === Creator-Tier Counters ===

        // Evolution → Purifying Touch enchantment: reduces evolution damage accumulation by 50%
        COUNTER_ENTRIES.add(new CounterEntry(
                "eliteforge:creator_evolution", "purifying_touch",
                "Purifying Touch reduces Evolution damage accumulation by 50%", 0.5f
        ));
        // Dominion → Quench Stone: quench stone can dispel dominion effects when used inside
        COUNTER_ENTRIES.add(new CounterEntry(
                "eliteforge:creator_dominion", "quench_stone",
                "Quench Stone dispels Dominion effects when used inside", 0.5f
        ));
        // Annihilate → Heat Shield enchantment: reduces annihilation explosion damage by 40%
        COUNTER_ENTRIES.add(new CounterEntry(
                "eliteforge:creator_annihilate", "heat_shield",
                "Heat Shield reduces Annihilation explosion damage by 40%", 0.6f
        ));
        // Reincarnation → Purifying Touch enchantment: reduces rebirth health by 30%
        COUNTER_ENTRIES.add(new CounterEntry(
                "eliteforge:creator_reincarnation", "purifying_touch",
                "Purifying Touch reduces Reincarnation rebirth health by 30%", 0.7f
        ));
        // Commander → Elite Bane enchantment: commander buffs reduced when attacker has Elite Bane
        COUNTER_ENTRIES.add(new CounterEntry(
                "eliteforge:creator_commander", "elite_bane",
                "Elite Bane reduces Commander buff effectiveness by 30%", 0.7f
        ));
        // IronWall → Piercing: piercing damage bypasses 25% of Iron Wall reduction
        COUNTER_ENTRIES.add(new CounterEntry(
                "eliteforge:iron_wall", "piercing",
                "Piercing bypasses 25% of Iron Wall damage reduction", 0.75f
        ));

        // === Additional Counters ===

        // Nexus → Forging Hammer: striking a nexus elite interrupts its nurturing pulse for 5 seconds
        COUNTER_ENTRIES.add(new CounterEntry(
                "eliteforge:creator_nexus", "forging_hammer",
                "Forging Hammer interrupts Nexus nurturing pulse for 5 seconds", 0.5f
        ));
        // Bestowal → Elite Bane: elite bane weapons prevent bestowed elites from being created near the player
        COUNTER_ENTRIES.add(new CounterEntry(
                "eliteforge:creator_bestowal", "elite_bane",
                "Elite Bane prevents Bestowal from creating elites near the wielder", 0.6f
        ));
        // Storm → Heat Shield: heat shield enchantment reduces storm projectile damage by 30%
        COUNTER_ENTRIES.add(new CounterEntry(
                "eliteforge:storm", "heat_shield",
                "Heat Shield reduces Storm projectile damage by 30%", 0.7f
        ));
        // Clone → Purifying Touch: purifying touch reveals the real elite among clones
        COUNTER_ENTRIES.add(new CounterEntry(
                "eliteforge:clone", "purifying_touch",
                "Purifying Touch reveals the real elite among clones", 0.4f
        ));
        // Necromancy → Soul Collector: soul collector enchantment banishes summoned undead faster
        COUNTER_ENTRIES.add(new CounterEntry(
                "eliteforge:necromancy", "soul_collector",
                "Soul Collector banishes Necromancy undead faster", 0.6f
        ));
        // Mutation → Purification Flask: purification flask can remove mutated abilities
        COUNTER_ENTRIES.add(new CounterEntry(
                "eliteforge:mutation", "purification_flask",
                "Purification Flask removes Mutation-granted abilities", 0.5f
        ));
    }

    /**
     * Check if an ability has a counter.
     *
     * @param abilityId   the ability ID
     * @param counterType the counter type
     * @return the counter entry, or null
     */
    public static CounterEntry getCounter(String abilityId, String counterType) {
        for (CounterEntry entry : COUNTER_ENTRIES) {
            if (entry.abilityId().equals(abilityId) && entry.counterType().equals(counterType)) {
                return entry;
            }
        }
        return null;
    }

    /**
     * Check if a player's Quench Stone counters the entity's fire ability.
     * <p>
     * Delegates to {@link #getActiveCounters(LivingEntity, Player)} to avoid
     * duplicating the Quench Stone equipment-slot iteration logic.
     * <p>
     * NOTE: This helper remains for backward compatibility but could be replaced
     * by {@code getActiveCounters(entity, player).stream().anyMatch(c -> "quench_stone".equals(c.counterType()))}
     * in a future version.
     *
     * @param player the player with the Quench Stone
     * @param entity the elite entity
     * @return true if the fire ability is countered
     */
    public static boolean isFireCountered(Player player, LivingEntity entity) {
        return getActiveCounters(entity, player).stream()
                .anyMatch(c -> "quench_stone".equals(c.counterType()));
    }

    /**
     * Check if a player has the wither effect that counters regen.
     *
     * @param entity the elite entity
     * @return true if regen is reduced
     */
    public static boolean isRegenCountered(LivingEntity entity) {
        // If the entity has wither effect, regen is reduced
        return entity.hasEffect(MobEffects.WITHER);
    }

    /**
     * Get the regen reduction multiplier (0.5 = 50% reduction).
     * <p>
     * NOTE: This helper remains for backward compatibility but could be replaced
     * by {@code getActiveCounters(entity, player).stream().filter(c -> "wither_effect".equals(c.counterType())).findFirst()}
     * in a future version.
     *
     * @param entity the elite entity
     * @return regen multiplier
     */
    public static float getRegenReduction(LivingEntity entity) {
        if (!entity.getCapability(com.eliteforge.capability.EliteCapability.CAPABILITY)
                .map(cap -> cap.isElite() && cap.getEliteData().hasAbility("eliteforge:regen"))
                .orElse(false)) {
            return 1.0f;
        }
        return isRegenCountered(entity) ? 0.5f : 1.0f;
    }

    /**
     * Get all active counters for an entity.
     *
     * @param entity the elite entity
     * @param player the player attacking the entity
     * @return list of active counters
     */
    public static List<CounterEntry> getActiveCounters(LivingEntity entity, Player player) {
        List<CounterEntry> active = new ArrayList<>();
        entity.getCapability(com.eliteforge.capability.EliteCapability.CAPABILITY)
                .ifPresent(cap -> {
                    if (!cap.isElite()) return;
                    Map<String, Integer> abilities = cap.getEliteData().getAbilities();

                    for (CounterEntry entry : COUNTER_ENTRIES) {
                        if (!abilities.containsKey(entry.abilityId())) continue;

                        switch (entry.counterType()) {
                            case "quench_stone" -> {
                                // Quench Stone was removed in v0.2.0; counter is dead code.
                            }
                            case "wither_effect" -> {
                                if (entity.hasEffect(MobEffects.WITHER)) {
                                    active.add(entry);
                                }
                            }
                            case "invisibility_hit" -> {
                                // This is checked on-hit; if entity is invisible (Phase active)
                                if (entity.isInvisible()) {
                                    active.add(entry);
                                }
                            }
                            case "purifying_touch" -> {
                                // Check if player's held weapon has Purifying Touch enchantment
                                for (net.minecraft.world.entity.EquipmentSlot slot : new net.minecraft.world.entity.EquipmentSlot[]{
                                        net.minecraft.world.entity.EquipmentSlot.MAINHAND,
                                        net.minecraft.world.entity.EquipmentSlot.OFFHAND
                                }) {
                                    net.minecraft.world.item.ItemStack stack = player.getItemBySlot(slot);
                                    if (stack.getEnchantmentLevel(
                                            com.eliteforge.init.ModEnchantments.PURIFYING_TOUCH.get()) > 0) {
                                        active.add(entry);
                                        break;
                                    }
                                }
                            }
                            case "heat_shield" -> {
                                // Check if player's armor has Heat Shield enchantment
                                for (net.minecraft.world.entity.EquipmentSlot slot : net.minecraft.world.entity.EquipmentSlot.values()) {
                                    if (slot.getType() == net.minecraft.world.entity.EquipmentSlot.Type.ARMOR) {
                                        net.minecraft.world.item.ItemStack stack = player.getItemBySlot(slot);
                                        if (stack.getEnchantmentLevel(
                                                com.eliteforge.init.ModEnchantments.HEAT_SHIELD.get()) > 0) {
                                            active.add(entry);
                                            break;
                                        }
                                    }
                                }
                            }
                            case "elite_bane" -> {
                                // Check if player's held weapon has Elite Bane enchantment
                                net.minecraft.world.item.ItemStack mainHand = player.getMainHandItem();
                                if (mainHand.getEnchantmentLevel(
                                        com.eliteforge.init.ModEnchantments.ELITE_BANE.get()) > 0) {
                                    active.add(entry);
                                }
                            }
                            case "piercing" -> {
                                // Piercing: always active for projectile weapons with Piercing enchantment
                                // or for players using crossbows
                                net.minecraft.world.item.ItemStack mainHand = player.getMainHandItem();
                                if (mainHand.getEnchantmentLevel(
                                        net.minecraft.world.item.enchantment.Enchantments.PIERCING) > 0) {
                                    active.add(entry);
                                }
                            }
                            case "soul_collector" -> {
                                // Check if player's weapon has Soul Collector enchantment
                                net.minecraft.world.item.ItemStack mainHand = player.getMainHandItem();
                                if (mainHand.getEnchantmentLevel(
                                        com.eliteforge.init.ModEnchantments.SOUL_COLLECTOR.get()) > 0) {
                                    active.add(entry);
                                }
                            }
                            case "purification_flask" -> {
                                // Purification Flask: checked on throw (active if player has flask in inventory)
                                for (net.minecraft.world.entity.EquipmentSlot slot : net.minecraft.world.entity.EquipmentSlot.values()) {
                                    net.minecraft.world.item.ItemStack stack = player.getItemBySlot(slot);
                                    if (stack.is(com.eliteforge.init.ModItems.PURIFICATION_FLASK.get())) {
                                        active.add(entry);
                                        break;
                                    }
                                }
                            }
                        }
                    }
                });
        return active;
    }

    /**
     * Get all synergy pairs for display or debugging.
     *
     * @return unmodifiable list of all synergy pairs
     */
    public static List<SynergyPair> getAllSynergies() {
        return Collections.unmodifiableList(SYNERGY_PAIRS);
    }

    /**
     * Get all counter entries for display or debugging.
     *
     * @return unmodifiable list of all counter entries
     */
    public static List<CounterEntry> getAllCounters() {
        return Collections.unmodifiableList(COUNTER_ENTRIES);
    }
}
