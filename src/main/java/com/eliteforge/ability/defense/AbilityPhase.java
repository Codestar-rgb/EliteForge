package com.eliteforge.ability.defense;

import com.eliteforge.ability.Ability;
import com.eliteforge.ability.AbilityCategory;
import com.eliteforge.util.NBTKeys;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;

/**
 * AbilityPhase (相位) - Periodically become invisible and intangible.
 * 
 * Every (200 - level * 30) ticks, become invisible and intangible for (20 + level * 10) ticks.
 * Ender particles when phasing.
 */
public class AbilityPhase extends Ability {

    private static final String PHASE_ACTIVE_KEY = "phaseActive";
    private static final String PHASE_TIMER_KEY = "phaseTimer";
    private static final String PHASE_COOLDOWN_KEY = "phaseCooldown";

    public AbilityPhase() {
        super(
            new ResourceLocation("eliteforge", "phase"),
            AbilityCategory.DEFENSE,
            3.0f
        );
    }

    @Override
    public void onApply(LivingEntity entity, int level) {
        if (entity.level().isClientSide) return;
        CompoundTag phaseData = new CompoundTag();
        phaseData.putBoolean(PHASE_ACTIVE_KEY, false);
        phaseData.putInt(PHASE_TIMER_KEY, 0);
        phaseData.putInt(PHASE_COOLDOWN_KEY, 0);
        entity.getPersistentData().put(NBTKeys.PHASE_DATA, phaseData);
    }

    @Override
    public void onTick(LivingEntity entity, int level) {
        if (entity.level().isClientSide) return;

        CompoundTag entityData = entity.getPersistentData();
        if (!entityData.contains(NBTKeys.PHASE_DATA)) {
            onApply(entity, level); // Initialize if not present
            return;
        }

        CompoundTag phaseData = entityData.getCompound(NBTKeys.PHASE_DATA);
        boolean phaseActive = phaseData.getBoolean(PHASE_ACTIVE_KEY);
        int timer = phaseData.getInt(PHASE_TIMER_KEY);
        int cooldown = phaseData.getInt(PHASE_COOLDOWN_KEY);

        int phaseDuration = 20 + level * 10;
        int phaseCooldown = Math.max(40, 200 - level * 30);

        if (phaseActive) {
            timer--;
            if (timer <= 0) {
                // Deactivate phase
                phaseData.putBoolean(PHASE_ACTIVE_KEY, false);
                phaseData.putInt(PHASE_COOLDOWN_KEY, phaseCooldown);
                entity.setInvisible(false);
                entity.setInvulnerable(false);

                // De-phase particles
                if (entity.level() instanceof ServerLevel serverLevel) {
                    serverLevel.sendParticles(ParticleTypes.PORTAL,
                            entity.getX(), entity.getY() + entity.getBbHeight() * 0.5, entity.getZ(),
                            10 + level * 2,
                            entity.getBbWidth() * 0.5, entity.getBbHeight() * 0.5, entity.getBbWidth() * 0.5,
                            0.1);
                }
            } else {
                // Still phasing - maintain invisibility
                entity.setInvisible(true);
                entity.setInvulnerable(true);
                phaseData.putInt(PHASE_TIMER_KEY, timer);
            }
        } else {
            cooldown--;
            if (cooldown <= 0) {
                // Activate phase
                phaseData.putBoolean(PHASE_ACTIVE_KEY, true);
                phaseData.putInt(PHASE_TIMER_KEY, phaseDuration);
                entity.setInvisible(true);
                entity.setInvulnerable(true);

                // Phase particles
                if (entity.level() instanceof ServerLevel serverLevel) {
                    serverLevel.sendParticles(ParticleTypes.PORTAL,
                            entity.getX(), entity.getY() + entity.getBbHeight() * 0.5, entity.getZ(),
                            15 + level * 3,
                            entity.getBbWidth() * 0.5, entity.getBbHeight() * 0.5, entity.getBbWidth() * 0.5,
                            0.2);
                }
            } else {
                phaseData.putInt(PHASE_COOLDOWN_KEY, cooldown);
            }
        }
    }

    @Override
    public void onHurt(LivingEntity entity, float damage, int level) {
        // While phasing, entity is invulnerable - handled by setInvulnerable
    }

    @Override
    public void onRemove(LivingEntity entity, int level) {
        // Clean up phase state to prevent permanent invisibility/invulnerability
        if (entity.level().isClientSide) return;
        entity.setInvisible(false);
        entity.setInvulnerable(false);
        CompoundTag entityData = entity.getPersistentData();
        if (entityData.contains(NBTKeys.PHASE_DATA)) {
            CompoundTag phaseData = entityData.getCompound(NBTKeys.PHASE_DATA);
            phaseData.putBoolean(PHASE_ACTIVE_KEY, false);
            phaseData.putInt(PHASE_TIMER_KEY, 0);
            entityData.put(NBTKeys.PHASE_DATA, phaseData);
        }
    }
}
