package com.eliteforge.ability.legendary;

import com.eliteforge.EliteForge;
import com.eliteforge.ability.Ability;
import com.eliteforge.ability.AbilityCategory;
import com.eliteforge.ability.AbilityRegistry;
import com.eliteforge.util.NBTKeys;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;

import java.util.*;
import java.util.stream.Collectors;

/**
 * AbilityMutation (变异) - Periodically gain a random additional ability.
 * 
 * Every (500 - level * 80) ticks, gain a random additional ability for 30s.
 * Random ability level: 1 + floor(level/2).
 * Cannot gain legendary abilities through mutation.
 * Green swirl + enchant glyphs particles.
 */
public class AbilityMutation extends Ability {

    private static final int MUTATION_DURATION = 600; // 30 seconds in ticks

    public AbilityMutation() {
        super(
            new ResourceLocation("eliteforge", "mutation"),
            AbilityCategory.LEGENDARY,
            4.0f
        );
    }

    @Override
    public void onApply(LivingEntity entity, int level) {
        if (entity.level().isClientSide) return;
        CompoundTag data = entity.getPersistentData();
        data.putInt(NBTKeys.MUTATION_COOLDOWN, 0);
        data.putBoolean(NBTKeys.MUTATION_ACTIVE, false);
        data.putString(NBTKeys.MUTATION_ABILITY, "");
        data.putInt(NBTKeys.MUTATION_LEVEL, 0);
        data.putInt(NBTKeys.MUTATION_TIMER, 0);
    }

    @Override
    public void onRemove(LivingEntity entity, int level) {
        if (entity.level().isClientSide) return;
        // When Mutation itself is purged (PurifyingTouch / bestowal reversion / awakening),
        // we must also clean up the active mutated ability — otherwise its attribute
        // modifiers leak permanently and the dispatch helpers keep ticking it.
        CompoundTag data = entity.getPersistentData();
        if (data.getBoolean(NBTKeys.MUTATION_ACTIVE)) {
            String activeAbilityId = data.getString(NBTKeys.MUTATION_ABILITY);
            removeMutatedAbility(entity, activeAbilityId);
        }
        // Always clear the cooldown key so a re-applied Mutation starts fresh.
        data.remove(NBTKeys.MUTATION_COOLDOWN);
    }

    @Override
    public void onTick(LivingEntity entity, int level) {
        if (entity.level().isClientSide) return;
        if (entity.tickCount % 10 != 0) return;

        CompoundTag data = entity.getPersistentData();
        if (!data.contains(NBTKeys.MUTATION_COOLDOWN)) {
            onApply(entity, level);
            return;
        }

        // Handle active mutation timer
        boolean mutationActive = data.getBoolean(NBTKeys.MUTATION_ACTIVE);
        if (mutationActive) {
            int timer = data.getInt(NBTKeys.MUTATION_TIMER);
            timer -= 10;

            if (timer <= 0) {
                // Remove the mutated ability
                String activeAbilityId = data.getString(NBTKeys.MUTATION_ABILITY);
                int activeLevel = data.getInt(NBTKeys.MUTATION_LEVEL);
                removeMutatedAbility(entity, activeAbilityId);

                data.putBoolean(NBTKeys.MUTATION_ACTIVE, false);
                data.putString(NBTKeys.MUTATION_ABILITY, "");
                data.putInt(NBTKeys.MUTATION_LEVEL, 0);
                data.putInt(NBTKeys.MUTATION_TIMER, 0);
            } else {
                data.putInt(NBTKeys.MUTATION_TIMER, timer);

                // Tick the active mutated ability. Apply the Supreme passive boost
                // so the mutated ability's effective level is consistent whether
                // Mutation itself is still in the ability map or has been purged
                // (the dispatchMutatedAbilityTick helper applies the same boost).
                String activeAbilityId = data.getString(NBTKeys.MUTATION_ABILITY);
                int activeLevel = data.getInt(NBTKeys.MUTATION_LEVEL);
                Ability activeAbility = AbilityRegistry.getAbility(activeAbilityId);
                if (activeAbility != null) {
                    int effectiveLevel = com.eliteforge.ability.legendary.AbilitySupreme
                            .getEffectiveLevelForEntity(entity, activeAbility, activeLevel);
                    try {
                        activeAbility.onTick(entity, effectiveLevel);
                    } catch (Exception e) {
                        EliteForge.LOGGER.error("Error ticking mutated ability: {}", e.getMessage());
                    }
                }
            }
        }

        // Handle cooldown
        int cooldown = data.getInt(NBTKeys.MUTATION_COOLDOWN);
        cooldown -= 10;

        if (cooldown <= 0 && !mutationActive) {
            applyMutation(entity, level);
            int newCooldown = Math.max(100, 500 - level * 80);
            data.putInt(NBTKeys.MUTATION_COOLDOWN, newCooldown);
        } else {
            data.putInt(NBTKeys.MUTATION_COOLDOWN, cooldown);
        }

        // Green swirl + enchant glyph particles when mutation is active
        if (mutationActive && entity.tickCount % 15 == 0 && entity.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                    entity.getX(), entity.getY() + entity.getBbHeight() * 0.5, entity.getZ(),
                    Math.min(2 + level, 8),
                    entity.getBbWidth() * 0.3, entity.getBbHeight() * 0.3, entity.getBbWidth() * 0.3,
                    0.02);
            serverLevel.sendParticles(ParticleTypes.ENCHANT,
                    entity.getX(), entity.getY() + entity.getBbHeight() * 0.5, entity.getZ(),
                    1 + level / 2,
                    entity.getBbWidth() * 0.3, entity.getBbHeight() * 0.3, entity.getBbWidth() * 0.3,
                    0.3);
        }
    }

    /**
     * Applies a random mutation ability.
     */
    private void applyMutation(LivingEntity entity, int level) {
        if (!(entity.level() instanceof ServerLevel serverLevel)) return;

        // Get non-legendary, non-creator abilities for mutation pool.
        // Creator-tier abilities (Reincarnation/Annihilate/Nexus/Evolution/...) permanently
        // mark the entity as a creator in onApply and grant creator-tier power — they must
        // NOT be granted to a regular elite via Mutation.
        List<Ability> mutationPool = AbilityRegistry.getAllAbilities().stream()
                .filter(a -> a.getCategory() != AbilityCategory.LEGENDARY
                        && a.getCategory() != AbilityCategory.CREATOR)
                .filter(Ability::isEnabled)
                .collect(Collectors.toList());

        if (mutationPool.isEmpty()) return;

        // Pick a random ability
        Ability chosen = mutationPool.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(mutationPool.size()));

        // Determine level: 1 + floor(level/2)
        int mutationLevel = 1 + level / 2;
        mutationLevel = Math.min(mutationLevel, chosen.getMaxLevel());

        // Apply the ability
        try {
            chosen.onApply(entity, mutationLevel);
        } catch (Exception e) {
            EliteForge.LOGGER.error("Error applying mutation: {}", e.getMessage());
        }

        // Store mutation data
        CompoundTag data = entity.getPersistentData();
        data.putBoolean(NBTKeys.MUTATION_ACTIVE, true);
        data.putString(NBTKeys.MUTATION_ABILITY, chosen.getIdString());
        data.putInt(NBTKeys.MUTATION_LEVEL, mutationLevel);
        data.putInt(NBTKeys.MUTATION_TIMER, MUTATION_DURATION);

        // Green swirl + enchant glyph burst
        serverLevel.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                entity.getX(), entity.getY() + entity.getBbHeight() * 0.5, entity.getZ(),
                15 + level * 3,
                0.5, entity.getBbHeight() * 0.4, 0.5,
                0.05);
        serverLevel.sendParticles(ParticleTypes.ENCHANT,
                entity.getX(), entity.getY() + entity.getBbHeight() * 0.5, entity.getZ(),
                10 + level * 2,
                0.5, entity.getBbHeight() * 0.4, 0.5,
                0.5);
    }

    /**
     * Removes the mutated ability effects.
     * <p>
     * H10 fix: previously a no-op. Now calls the mutated ability's onRemove()
     * to properly clean up attribute modifiers, NBT state, and other side effects.
     * Without this, abilities like Supreme, Armor, or any ability that adds
     * attribute modifiers in onApply() would leak those modifiers permanently
     * when the mutation expires.
     *
     * @param entity    the entity
     * @param abilityId the ability ID string of the mutated ability to remove
     */
    private void removeMutatedAbility(LivingEntity entity, String abilityId) {
        if (abilityId == null || abilityId.isEmpty()) return;

        // Look up the ability and call its onRemove
        Ability mutatedAbility = AbilityRegistry.getAbility(abilityId);
        if (mutatedAbility != null) {
            // Retrieve the mutation level from NBT to pass to onRemove
            CompoundTag data = entity.getPersistentData();
            int mutationLevel = data.contains(NBTKeys.MUTATION_LEVEL)
                    ? data.getInt(NBTKeys.MUTATION_LEVEL) : 1;
            try {
                mutatedAbility.onRemove(entity, mutationLevel);
            } catch (Exception e) {
                EliteForge.LOGGER.error("Error removing mutated ability {}: {}",
                        abilityId, e.getMessage());
            }
        }

        // Clear mutation NBT state
        CompoundTag data = entity.getPersistentData();
        data.remove(NBTKeys.MUTATION_ACTIVE);
        data.remove(NBTKeys.MUTATION_ABILITY);
        data.remove(NBTKeys.MUTATION_LEVEL);
        data.remove(NBTKeys.MUTATION_TIMER);
    }
}
