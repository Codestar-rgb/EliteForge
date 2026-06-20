package com.eliteforge.accessory;

import com.eliteforge.EliteForge;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * CuriosIntegration — applies accessory stat bonuses (HP/ATK/DEF) to the player.
 * <p>
 * Bonuses are applied as transient AttributeModifiers keyed by accessory-type UUIDs.
 * Same-type accessories do NOT stack: only the highest-quality one of each type
 * contributes (enforced in {@link #refreshPlayer}).
 * <p>
 * Wiring: a {@link TickEvent.PlayerTickEvent} listener runs every 40 ticks (2 s) per
 * player, re-deriving the best accessory of each type from the player's inventory
 * (and Curios slots, if Curios is installed). Previously this class was dead code —
 * {@code applyBonuses}/{@code removeBonuses} had zero call sites, so accessory
 * tooltips advertised bonuses that never applied.
 */
public class CuriosIntegration {

    private static final Logger LOGGER = LogManager.getLogger();
    private static boolean curiosLoaded = false;
    private static boolean checked = false;

    /**
     * Check if Curios API is loaded.
     */
    public static boolean isCuriosLoaded() {
        if (!checked) {
            try {
                Class.forName("top.theillusivec4.curios.api.CuriosApi");
                curiosLoaded = true;
                LOGGER.info("EliteForge: Curios API detected — accessories will also scan Curios slots");
            } catch (ClassNotFoundException e) {
                curiosLoaded = false;
                LOGGER.info("EliteForge: Curios API not found — accessories use main inventory scan only");
            }
            checked = true;
        }
        return curiosLoaded;
    }

    /**
     * Apply accessory bonuses to an entity.
     * Called when an accessory is equipped or ticked.
     */
    public static void applyBonuses(LivingEntity entity, ItemStack stack, EliteAccessory accessory) {
        if (entity.level().isClientSide) return;

        float healthBonus = accessory.getHealthBonus(stack);
        float damageBonus = accessory.getDamageBonus(stack);
        int armorBonus = accessory.getArmorBonus(stack);

        UUID healthUUID = UUID.nameUUIDFromBytes(("eliteforge:health:" + accessory.getAccessoryType().getId()).getBytes());
        UUID damageUUID = UUID.nameUUIDFromBytes(("eliteforge:damage:" + accessory.getAccessoryType().getId()).getBytes());
        UUID armorUUID = UUID.nameUUIDFromBytes(("eliteforge:armor:" + accessory.getAccessoryType().getId()).getBytes());

        try {
            // Health
            var healthAttr = entity.getAttribute(Attributes.MAX_HEALTH);
            if (healthAttr != null) {
                healthAttr.removeModifier(healthUUID);
                healthAttr.addTransientModifier(new AttributeModifier(healthUUID, "EliteForge Accessory HP", healthBonus, AttributeModifier.Operation.ADDITION));
            }

            // Damage
            var damageAttr = entity.getAttribute(Attributes.ATTACK_DAMAGE);
            if (damageAttr != null) {
                damageAttr.removeModifier(damageUUID);
                damageAttr.addTransientModifier(new AttributeModifier(damageUUID, "EliteForge Accessory ATK", damageBonus, AttributeModifier.Operation.ADDITION));
            }

            // Armor
            var armorAttr = entity.getAttribute(Attributes.ARMOR);
            if (armorAttr != null) {
                armorAttr.removeModifier(armorUUID);
                armorAttr.addTransientModifier(new AttributeModifier(armorUUID, "EliteForge Accessory DEF", armorBonus, AttributeModifier.Operation.ADDITION));
            }
        } catch (Exception e) {
            LOGGER.error("Error applying accessory bonuses: {}", e.getMessage());
        }
    }

    /**
     * Remove accessory bonuses from an entity.
     */
    public static void removeBonuses(LivingEntity entity, EliteAccessory accessory) {
        if (entity.level().isClientSide) return;

        UUID healthUUID = UUID.nameUUIDFromBytes(("eliteforge:health:" + accessory.getAccessoryType().getId()).getBytes());
        UUID damageUUID = UUID.nameUUIDFromBytes(("eliteforge:damage:" + accessory.getAccessoryType().getId()).getBytes());
        UUID armorUUID = UUID.nameUUIDFromBytes(("eliteforge:armor:" + accessory.getAccessoryType().getId()).getBytes());

        try {
            var healthAttr = entity.getAttribute(Attributes.MAX_HEALTH);
            if (healthAttr != null) healthAttr.removeModifier(healthUUID);

            var damageAttr = entity.getAttribute(Attributes.ATTACK_DAMAGE);
            if (damageAttr != null) damageAttr.removeModifier(damageUUID);

            var armorAttr = entity.getAttribute(Attributes.ARMOR);
            if (armorAttr != null) armorAttr.removeModifier(armorUUID);
        } catch (Exception e) {
            LOGGER.error("Error removing accessory bonuses: {}", e.getMessage());
        }
    }

    /**
     * Collect all EliteAccessory stacks the player is currently using — main + offhand
     * + Curios slots (if Curios is installed). Each stack is returned paired with its
     * EliteAccessory item so callers don't have to re-cast.
     */
    private static List<ItemStack> collectAccessories(Player player) {
        List<ItemStack> out = new ArrayList<>();
        // Main inventory + armor + offhand
        for (ItemStack stack : player.getInventory().items) {
            if (!stack.isEmpty() && stack.getItem() instanceof EliteAccessory) out.add(stack);
        }
        for (ItemStack stack : player.getInventory().offhand) {
            if (!stack.isEmpty() && stack.getItem() instanceof EliteAccessory) out.add(stack);
        }
        for (ItemStack stack : player.getInventory().armor) {
            if (!stack.isEmpty() && stack.getItem() instanceof EliteAccessory) out.add(stack);
        }
        // Curios slots (best-effort reflection — won't crash if Curios is absent)
        if (isCuriosLoaded()) {
            try {
                var curiosApi = Class.forName("top.theillusivec4.curios.api.CuriosApi");
                var getCuriosHelper = curiosApi.getMethod("getCuriosHelper");
                var helper = getCuriosHelper.invoke(null);
                var getEquippedCurios = helper.getClass().getMethod("getEquippedCurios", LivingEntity.class);
                var future = getEquippedCurios.invoke(helper, player);
                if (future instanceof java.util.concurrent.Future<?> f) {
                    Object result = f.get();
                    if (result instanceof Iterable<?> it) {
                        for (Object slotResult : it) {
                            var getStack = slotResult.getClass().getMethod("stack");
                            Object s = getStack.invoke(slotResult);
                            if (s instanceof ItemStack stack && !stack.isEmpty()
                                    && stack.getItem() instanceof EliteAccessory) {
                                out.add(stack);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                // Curios API shape changed or unavailable — silently fall back to inventory-only.
                // Logged at debug to avoid spamming on every tick.
                LOGGER.debug("Curios slot scan failed: {}", e.getMessage());
            }
        }
        return out;
    }

    /**
     * Re-derive the best-in-slot accessory of each type from the player's equipped
     * accessories and (re)apply their bonuses. Same-type accessories do NOT stack:
     * only the highest quality+upgrade one of each type contributes.
     * <p>
     * Idempotent — calling this on every tick is safe because {@code applyBonuses}
     * removes-then-re-adds the per-type modifier UUID.
     */
    private static void refreshPlayer(Player player) {
        if (player.level().isClientSide) return;
        List<ItemStack> equipped = collectAccessories(player);
        // For each AccessoryType, find the highest "power" stack.
        java.util.Map<EliteAccessory.AccessoryType, ItemStack> best = new java.util.HashMap<>();
        java.util.Map<EliteAccessory.AccessoryType, Integer> bestPower = new java.util.HashMap<>();
        for (ItemStack stack : equipped) {
            EliteAccessory acc = (EliteAccessory) stack.getItem();
            EliteAccessory.AccessoryType type = acc.getAccessoryType();
            int power = acc.getQuality(stack).ordinal() * 10 + acc.getUpgradeLevel(stack);
            if (!best.containsKey(type) || power > bestPower.get(type)) {
                best.put(type, stack);
                bestPower.put(type, power);
            }
        }
        // Clear ALL per-type modifiers first, then re-apply the winner of each type.
        for (EliteAccessory.AccessoryType type : EliteAccessory.AccessoryType.values()) {
            // Synthesize a dummy stack-less removal by UUID (no need for an EliteAccessory instance).
            UUID healthUUID = UUID.nameUUIDFromBytes(("eliteforge:health:" + type.getId()).getBytes());
            UUID damageUUID = UUID.nameUUIDFromBytes(("eliteforge:damage:" + type.getId()).getBytes());
            UUID armorUUID = UUID.nameUUIDFromBytes(("eliteforge:armor:" + type.getId()).getBytes());
            try {
                var h = player.getAttribute(Attributes.MAX_HEALTH); if (h != null) h.removeModifier(healthUUID);
                var d = player.getAttribute(Attributes.ATTACK_DAMAGE); if (d != null) d.removeModifier(damageUUID);
                var a = player.getAttribute(Attributes.ARMOR); if (a != null) a.removeModifier(armorUUID);
            } catch (Exception ignored) { }
        }
        for (ItemStack stack : best.values()) {
            EliteAccessory acc = (EliteAccessory) stack.getItem();
            applyBonuses(player, stack, acc);
        }
    }

    /**
     * Per-tick accessory refresh. Runs every 40 ticks (2 s) per player — frequent
     * enough to feel responsive when equipping/unequipping, cheap enough to not
     * matter for TPS.
     */
    @Mod.EventBusSubscriber(modid = EliteForge.MODID)
    public static class AccessoryTickHandler {
        private static final java.util.Map<UUID, Integer> TICK_COUNTERS = new java.util.concurrent.ConcurrentHashMap<>();

        @SubscribeEvent
        public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            Player player = event.player;
            if (player.level().isClientSide) return;
            int c = TICK_COUNTERS.getOrDefault(player.getUUID(), 0) + 1;
            if (c < 40) {
                TICK_COUNTERS.put(player.getUUID(), c);
                return;
            }
            TICK_COUNTERS.put(player.getUUID(), 0);
            try {
                refreshPlayer(player);
            } catch (Exception e) {
                LOGGER.error("Error refreshing accessory bonuses for {}: {}",
                        player.getName().getString(), e.getMessage());
            }
        }
    }
}
