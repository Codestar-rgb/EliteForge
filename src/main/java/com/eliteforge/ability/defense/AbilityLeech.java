package com.eliteforge.ability.defense;

import com.eliteforge.ability.Ability;
import com.eliteforge.ability.AbilityCategory;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;

/**
 * AbilityLeech (吸血) — Heals the elite for a percentage of damage dealt to targets.
 * <p>
 * On attack: heals the elite for (level * 5%) of the damage dealt.
 * At level V: 25% lifesteal.
 * <p>
 * Design notes:
 * <ul>
 *   <li>Complements {@link AbilityRegen} — Regen is passive sustain, Leech is
 *       active sustain that rewards aggressive elites.</li>
 *   <li>Capped at 50% lifesteal (level 10) to prevent full healing on one-shot.</li>
 *   <li>Heal amount is capped at the elite's max health to prevent overhealing.</li>
 *   <li>Particles: heart particles rise from the elite on successful leech.</li>
 * </ul>
 * <p>
 * Synergy: pairs well with {@code eliteforge:bloodthirst} (attack) for double
 * sustain, but note that Bloodthirst and Siphon are mutually exclusive.
 */
public class AbilityLeech extends Ability {

    /** Heal percentage per level (5% at level I, 25% at level V). */
    private static final float HEAL_PERCENT_PER_LEVEL = 0.05f;
    /** Cap to prevent absurd healing at very high levels. */
    private static final float HEAL_CAP = 0.50f;

    public AbilityLeech() {
        super(
            new ResourceLocation("eliteforge", "leech"),
            AbilityCategory.DEFENSE,
            2.8f
        );
    }

    @Override
    public void onAttack(LivingEntity attacker, LivingEntity target, float damage, int level) {
        if (attacker.level().isClientSide) return;
        if (damage <= 0) return;

        // Calculate lifesteal: min(level * 5%, 50%)
        float healPercent = Math.min(level * HEAL_PERCENT_PER_LEVEL, HEAL_CAP);
        float healAmount = damage * healPercent;

        // Apply heal, capped at max health
        if (attacker.getHealth() < attacker.getMaxHealth()) {
            float newHealth = Math.min(attacker.getHealth() + healAmount, attacker.getMaxHealth());
            attacker.setHealth(newHealth);

            // Heart particles on the attacker
            if (attacker.level() instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ParticleTypes.HEART,
                        attacker.getX(), attacker.getY() + attacker.getBbHeight() + 0.3, attacker.getZ(),
                        1 + level / 2,
                        attacker.getBbWidth() * 0.3, 0.2, attacker.getBbWidth() * 0.3,
                        0.0);
            }
        }
    }

    @Override
    public void onHurt(LivingEntity entity, float damage, int level) {
        // Leech is triggered on attack, not on hurt — no passive behavior
    }
}
