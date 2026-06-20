package com.eliteforge.ability.defense;

import com.eliteforge.ability.Ability;
import com.eliteforge.ability.AbilityCategory;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

/**
 * AbilityBulwark (壁垒) — Grants temporary damage resistance when HP drops low.
 * <p>
 * When the elite's health drops below 30% of max, it gains:
 * <ul>
 *   <li>Resistance effect (level-based, 3-7 seconds)</li>
 *   <li>Damage reduction feedback particles</li>
 * </ul>
 * <p>
 * At level V: Resistance III for 7 seconds when below 30% HP.
 * <p>
 * Design notes:
 * <ul>
 *   <li>Triggers on hurt — checks HP after damage is applied.</li>
 *   <li>Cooldown: the Resistance effect has a natural cooldown via duration,
 *       so the elite won't spam-trigger. Each proc refreshes the duration.</li>
 *   <li>Threshold: 30% HP — low enough to be dramatic, high enough to save
 *       the elite from death.</li>
 *   <li>Synergy: pairs well with {@link AbilityRegen} — Bulwark delays death
 *       while Regen heals back up. Note: not mutually exclusive with Regen
 *       (Absorption ↔ Regen is the exclusion pair, not Bulwark ↔ Regen).</li>
 * </ul>
 */
public class AbilityBulwark extends Ability {

    /** HP percentage threshold below which Bulwark activates (30%). */
    private static final float ACTIVATION_THRESHOLD = 0.30f;
    /** Base resistance duration in ticks (3 seconds at level I). */
    private static final int BASE_DURATION = 60;
    /** Duration increase per level in ticks (1 second per level). */
    private static final int DURATION_PER_LEVEL = 20;

    public AbilityBulwark() {
        super(
            new ResourceLocation("eliteforge", "bulwark"),
            AbilityCategory.DEFENSE,
            2.6f
        );
    }

    @Override
    public void onHurt(LivingEntity entity, float damage, int level) {
        if (entity.level().isClientSide) return;

        float maxHealth = entity.getMaxHealth();
        float currentHealth = entity.getHealth();

        // Check if HP dropped below the activation threshold
        if (currentHealth > 0 && currentHealth <= maxHealth * ACTIVATION_THRESHOLD) {
            // Calculate resistance level: level I-III (capped at Resistance III)
            int resistanceLevel = Math.min(level, 3);
            // Calculate duration: 3s base + 1s per level (7s at level V)
            int duration = BASE_DURATION + (level - 1) * DURATION_PER_LEVEL;

            // Apply or refresh Resistance effect
            entity.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, duration, resistanceLevel - 1, false, true));

            // Shield burst particles
            if (entity.level() instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ParticleTypes.END_ROD,
                        entity.getX(), entity.getY() + entity.getBbHeight() * 0.5, entity.getZ(),
                        10 + level * 2,
                        entity.getBbWidth() * 0.5, entity.getBbHeight() * 0.5, entity.getBbWidth() * 0.5,
                        0.05);
            }
        }
    }

    @Override
    public void onTick(LivingEntity entity, int level) {
        // Bulwark is reactive (triggers on hurt), not passive — no tick behavior
    }
}
