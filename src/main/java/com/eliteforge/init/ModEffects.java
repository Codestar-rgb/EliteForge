package com.eliteforge.init;

import com.eliteforge.EliteForge;
import com.eliteforge.effect.*;
import net.minecraft.world.effect.MobEffect;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Mob effect registration class for EliteForge.
 * Registers all 6 custom mob effects used by the elite ability system.
 *
 * Effects are categorized by their role in the elite combat system:
 * - Damage-over-time effects (Corrosion, Spirit Burn)
 * - Movement control effects (Fear, Immobilize)
 * - Special effects (Mutation, Chaos)
 */
public class ModEffects {

    public static final DeferredRegister<MobEffect> MOB_EFFECTS =
            DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, EliteForge.MODID);

    // ========================================================================
    // Mob Effect Registrations (6 custom effects)
    // ========================================================================

    /**
     * Corrosion (腐蚀) - Damage over time effect that bypasses armor.
     * Deals percentage-based damage, making it effective against heavily armored targets.
     * Used by attack-ability elites.
     */
    public static final RegistryObject<MobEffect> CORROSION_EFFECT = MOB_EFFECTS.register("corrosion_effect",
            CorrosionEffect::new);

    /**
     * Spirit Burn (灵燃) - Magic damage over time effect.
     * Deals flat magic damage that ignores conventional resistances.
     * Used by attack-ability elites.
     */
    public static final RegistryObject<MobEffect> SPIRIT_BURN_EFFECT = MOB_EFFECTS.register("spirit_burn_effect",
            SpiritBurnEffect::new);

    /**
     * Fear (恐惧) - Forces the target to run away from the elite.
     * The affected entity is compelled to move in the opposite direction.
     * Used by control-ability elites.
     */
    public static final RegistryObject<MobEffect> FEAR_EFFECT = MOB_EFFECTS.register("fear_effect",
            FearEffect::new);

    /**
     * Immobilize (禁锢) - Complete movement freeze.
     * Prevents all forms of movement including teleportation.
     * Used by control-ability elites.
     */
    public static final RegistryObject<MobEffect> IMMOBILIZE_EFFECT = MOB_EFFECTS.register("immobilize_effect",
            ImmobilizeEffect::new);

    /**
     * Mutation (变异) - Applies random effects to the target.
     * Each tick has a chance to apply a random positive or negative effect.
     * Used by legendary-ability elites.
     */
    public static final RegistryObject<MobEffect> MUTATION_EFFECT = MOB_EFFECTS.register("mutation_effect",
            MutationEffect::new);

    /**
     * Chaos (混沌) - Applies random negative effects to the target.
     * Each tick has a chance to apply a random debuff.
     * Used by legendary-ability elites.
     */
    public static final RegistryObject<MobEffect> CHAOS_EFFECT = MOB_EFFECTS.register("chaos_effect",
            ChaosEffect::new);
}
