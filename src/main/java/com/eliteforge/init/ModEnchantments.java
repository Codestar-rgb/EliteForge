package com.eliteforge.init;

import com.eliteforge.EliteForge;
import com.eliteforge.enchantment.*;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Enchantment registration class for EliteForge.
 * Registers all 6 custom enchantments that interact with the elite system.
 *
 * Enchantments provide players with tools to counter elite abilities
 * and enhance their forging capabilities.
 */
public class ModEnchantments {

    public static final DeferredRegister<Enchantment> ENCHANTMENTS =
            DeferredRegister.create(ForgeRegistries.ENCHANTMENTS, EliteForge.MODID);

    // ========================================================================
    // Enchantment Registrations (6 custom enchantments)
    // ========================================================================

    /**
     * Elite Bane (精英克星) - Extra damage to elite mobs, levels I-V.
     * Each level adds +20% damage against elite-tagged entities.
     * Compatible with all weapons.
     */
    public static final RegistryObject<Enchantment> ELITE_BANE = ENCHANTMENTS.register("elite_bane",
            EliteBaneEnchantment::new);

    /**
     * Heat Shield (热力屏障) - Reduces chunk heat influence on the player, levels I-III.
     * Each level reduces the negative effects of high chunk heat by 15%.
     * Can be applied to armor.
     */
    public static final RegistryObject<Enchantment> HEAT_SHIELD = ENCHANTMENTS.register("heat_shield",
            HeatShieldEnchantment::new);

    /**
     * Tempering Fortune (淬炼幸运) - Increases tempered material drop rate, levels I-III.
     * Each level adds +15% chance for elites to drop tempered materials.
     * Can be applied to weapons.
     */

    /**
     * Forging Master (锻造大师) - Better results on forging anvil, levels I-III.
     * Each level improves the quality outcome of forging operations by 10%.
     * Can be applied to the forging hammer.
     */

    /**
     * Soul Collector (灵魂收集) - Faster player experience growth, levels I-III.
     * Each level increases player experience gain from elite kills by 20%.
     * Can be applied to weapons.
     */
    public static final RegistryObject<Enchantment> SOUL_COLLECTOR = ENCHANTMENTS.register("soul_collector",
            SoulCollectorEnchantment::new);

    /**
     * Purifying Touch (净化之触) - Chance to remove one ability from elite on hit, levels I-II.
     * Level I: 10% chance per hit, Level II: 20% chance per hit.
     * Can be applied to weapons.
     */
    public static final RegistryObject<Enchantment> PURIFYING_TOUCH = ENCHANTMENTS.register("purifying_touch",
            PurifyingTouchEnchantment::new);
}
