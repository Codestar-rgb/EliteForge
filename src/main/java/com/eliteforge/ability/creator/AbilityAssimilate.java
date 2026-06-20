package com.eliteforge.ability.creator;

import com.eliteforge.ability.Ability;
import com.eliteforge.ability.AbilityCategory;
import com.eliteforge.ability.AbilityRegistry;
import com.eliteforge.capability.EliteCapability;
import com.eliteforge.capability.EliteData;
import com.eliteforge.capability.EliteCapabilitySync;
import com.eliteforge.config.EliteForgeConfig;
import com.eliteforge.util.NBTKeys;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;

import java.util.*;

/**
 * AbilityAssimilate (渊源·同化) - C4 Creator Ability
 * <p>
 * Absorbs abilities and stats from dying elites nearby.
 * Level I:  range 12, absorb 1 random ability (level halved, min I), max 2 abilities, heal 15% per absorb
 * Level II: range 20, absorb 1 random ability (level-1, min I) + 10% stats, max 3 abilities, heal 25% per absorb
 * Level III:range 30, absorb ALL abilities (level-1, min I) + 15% stats, max 5 abilities, heal 35% + 3s invulnerability per absorb
 * Only way creator entities can gain non-creator abilities (breaks the exclusivity rule).
 * Purple soul particles from corpse to assimilator.
 * Track assimilated abilities in EliteData.assimilatedAbilities
 * <p>
 * NBT keys:
 * <ul>
 *   <li>{@code EliteForgeAssimilateCooldown} - cooldown timer in ticks</li>
 *   <li>{@code EliteForgeAssimilateAppliedCount} - number of assimilation stat bonuses currently applied as attribute modifiers</li>
 *   <li>{@code EliteForgeAssimilateInvuln} - invulnerability timer (Level III, managed by EliteEventHandler)</li>
 * </ul>
 */
public class AbilityAssimilate extends CreatorAbility {

    // Attribute modifier UUIDs for stat absorption
    private static final UUID HEALTH_MODIFIER_UUID = UUID.fromString("a7b8c9d0-e1f2-3456-7890-abcd12345601");
    private static final UUID DAMAGE_MODIFIER_UUID = UUID.fromString("b8c9d0e1-f2a3-4567-8901-bcde23456702");

    // NBT keys (reference centralized constants)
    private static final String ASSIMILATE_COOLDOWN_KEY = NBTKeys.ASSIMILATE_COOLDOWN;
    private static final String ASSIMILATE_APPLIED_COUNT_KEY = NBTKeys.ASSIMILATE_APPLIED_COUNT;
    private static final String ASSIMILATE_INVULN_KEY = NBTKeys.ASSIMILATE_INVULN;

    public AbilityAssimilate() {
        super(new ResourceLocation("eliteforge", "creator_assimilate"), 5.5f);
    }

    @Override
    public void onApply(LivingEntity entity, int level) {
        if (entity.level().isClientSide) return;

        // Mark as creator entity in capability
        setupCreatorData(entity, level);
    }

    @Override
    public void onRemove(LivingEntity entity, int level) {
        if (entity.level().isClientSide) return;

        // Remove health and damage attribute modifiers from stat absorption
        try {
            var healthAttr = entity.getAttribute(Attributes.MAX_HEALTH);
            if (healthAttr != null) {
                healthAttr.removeModifier(HEALTH_MODIFIER_UUID);
            }
            var damageAttr = entity.getAttribute(Attributes.ATTACK_DAMAGE);
            if (damageAttr != null) {
                damageAttr.removeModifier(DAMAGE_MODIFIER_UUID);
            }
        } catch (Exception e) {
            // Attribute may not be available
        }

        // Clean up NBT data
        CompoundTag data = entity.getPersistentData();
        data.remove(ASSIMILATE_COOLDOWN_KEY);
        data.remove(ASSIMILATE_APPLIED_COUNT_KEY);

        // Also remove invulnerability status before removing the NBT key
        if (data.contains(ASSIMILATE_INVULN_KEY)) {
            entity.setInvulnerable(false);
        }
        data.remove(ASSIMILATE_INVULN_KEY);
    }

    @Override
    public void onTick(LivingEntity entity, int level) {
        // Assimilate triggers on death of nearby elites, not on tick
        // But we maintain the self stat bonuses here
        // NOTE: Invulnerability timer (EliteForgeAssimilateInvuln) is handled by
        // EliteEventHandler.tickCreatorNbtTimers() — do NOT decrement here to avoid
        // double-decrement (onTick runs every tick, tickCreatorNbtTimers runs every 20).
        if (entity.level().isClientSide) return;

        // Safety: ensure invulnerability is cleared if timer has expired or is missing
        CompoundTag nbt = entity.getPersistentData();
        if (!nbt.contains(ASSIMILATE_INVULN_KEY) || nbt.getInt(ASSIMILATE_INVULN_KEY) <= 0) {
            if (entity.isInvulnerable()) {
                entity.setInvulnerable(false);
            }
        }

        entity.getCapability(EliteCapability.CAPABILITY).ifPresent(cap -> {
            EliteData eliteData = cap.getEliteData();
            int currentCount = eliteData.getAssimilatedAbilities().size();
            int appliedCount = entity.getPersistentData().getInt(ASSIMILATE_APPLIED_COUNT_KEY);

            // Only re-apply stat bonuses if the assimilated count changed
            // This avoids removing and re-adding attribute modifiers every tick
            if (currentCount != appliedCount) {
                if (currentCount > 0) {
                    applyStatBonuses(entity, level, currentCount);
                }
                entity.getPersistentData().putInt(ASSIMILATE_APPLIED_COUNT_KEY, currentCount);
            }
        });

        // Ambient purple aura particles
        if (entity.level() instanceof ServerLevel serverLevel && entity.tickCount % 15 == 0) {
            serverLevel.sendParticles(ParticleTypes.PORTAL,
                    entity.getX(), entity.getY() + entity.getBbHeight() * 0.5, entity.getZ(),
                    1 + level,
                    entity.getBbWidth() * 0.4, entity.getBbHeight() * 0.3, entity.getBbWidth() * 0.4,
                    0.02);
        }
    }

    @Override
    public void onDeath(LivingEntity entity, int level) {
        if (entity.level().isClientSide) return;
        // When the assimilator dies, nothing special for assimilate
    }

    /**
     * Called when a nearby elite dies. This should be invoked from the event handler
     * by checking for nearby entities with the assimilate ability.
     * <p>
     * This is a static method so it can be called from EliteEventHandler.
     *
     * @param deadElite  the dying elite entity
     * @param serverLevel the server level
     */
    public static void onNearbyEliteDeath(LivingEntity deadElite, ServerLevel serverLevel) {
        if (serverLevel.isClientSide) return;

        // Find all entities with the Assimilate ability nearby
        double maxRange = 30.0; // Maximum possible range (Level III)
        AABB searchArea = new AABB(
                deadElite.getX() - maxRange, deadElite.getY() - maxRange, deadElite.getZ() - maxRange,
                deadElite.getX() + maxRange, deadElite.getY() + maxRange, deadElite.getZ() + maxRange
        );

        List<LivingEntity> nearbyEntities = serverLevel.getEntitiesOfClass(LivingEntity.class, searchArea,
                e -> e.isAlive() && e != deadElite && e.getCapability(EliteCapability.CAPABILITY).isPresent());

        for (LivingEntity potentialAssimilator : nearbyEntities) {
            potentialAssimilator.getCapability(EliteCapability.CAPABILITY).ifPresent(cap -> {
                EliteData data = cap.getEliteData();
                if (!data.isCreatorEntity()) return;
                if (!"eliteforge:creator_assimilate".equals(data.getCreatorAbilityId())) return;

                int level = data.getCreatorAbilityLevel();
                double range = switch (level) {
                    case 1 -> 12.0;
                    case 2 -> 20.0;
                    default -> 30.0;
                };

                // Check distance
                if (potentialAssimilator.distanceTo(deadElite) > range) return;

                int maxAssimilated = switch (level) {
                    case 1 -> 2;
                    case 2 -> 3;
                    default -> 5;
                };

                if (data.getAssimilatedAbilities().size() >= maxAssimilated) return;

                // Get the dead elite's abilities
                deadElite.getCapability(EliteCapability.CAPABILITY).ifPresent(deadCap -> {
                    EliteData deadData = deadCap.getEliteData();
                    // Defensive copy of dead entity's ability map to avoid any potential
                    // ConcurrentModificationException if the dead entity's data is accessed
                    // from another source during iteration
                    Map<String, Integer> deadAbilities = new LinkedHashMap<>(deadData.getAbilities());

                    if (deadAbilities.isEmpty()) return;

                    // Phase 1: Determine which abilities to absorb (read-only decisions)
                    // Build a list of (abilityId, newLevel) pairs without modifying the
                    // assimilator's data, to avoid ConcurrentModificationException if
                    // the assimilator's data collections are iterated elsewhere on the same tick.
                    List<Map.Entry<String, Integer>> toAbsorb = new ArrayList<>();
                    if (level >= 3) {
                        // Level III: absorb ALL abilities (excluding creator-tier)
                        for (String abilityId : deadAbilities.keySet()) {
                            Ability ability = AbilityRegistry.getAbility(abilityId);
                            if (ability != null && ability.getCategory() == AbilityCategory.CREATOR) {
                                continue; // Skip creator abilities - they are exclusive
                            }
                            int originalLevel = deadAbilities.get(abilityId);
                            int newLevel = Math.max(1, originalLevel - 1);
                            toAbsorb.add(Map.entry(abilityId, newLevel));
                        }
                    } else {
                        // Level I-II: absorb 1 random ability
                        List<String> abilityKeys = new ArrayList<>(deadAbilities.keySet());
                        String chosen = abilityKeys.get(potentialAssimilator.getRandom().nextInt(abilityKeys.size()));
                        int originalLevel = deadAbilities.get(chosen);
                        int newLevel = switch (level) {
                            case 1 -> Math.max(1, originalLevel / 2); // Halved, min I
                            case 2 -> Math.max(1, originalLevel - 1); // -1, min I
                            default -> Math.max(1, originalLevel - 1);
                        };
                        toAbsorb.add(Map.entry(chosen, newLevel));
                    }

                    // Phase 2: Apply absorbed abilities (mutations to assimilator's data)
                    // Now modify the assimilator's data in a separate loop, with
                    // defensive limit checking against the max assimilation count.
                    // Check addAbility() return value to avoid applying onApply side effects
                    // when the maxAbilities limit rejects the addition.
                    for (Map.Entry<String, Integer> entry : toAbsorb) {
                        if (data.getAssimilatedAbilities().size() >= maxAssimilated) break;

                        String abilityId = entry.getKey();
                        int newLevel = entry.getValue();

                        boolean added = data.addAbility(abilityId, newLevel);
                        if (!added) {
                            // maxAbilities limit reached — skip this ability entirely
                            continue;
                        }

                        data.addAssimilatedAbility(abilityId);

                        // Call onApply for the absorbed ability
                        Ability ability = AbilityRegistry.getAbility(abilityId);
                        if (ability != null) {
                            try {
                                ability.onApply(potentialAssimilator, newLevel);
                            } catch (Exception e) {
                                // Silently fail
                            }
                        }
                    }

                    cap.setEliteData(data);
                    EliteCapabilitySync.broadcastEliteDataUpdate(potentialAssimilator, data);
                });

                // Heal based on level
                float healPercent = switch (level) {
                    case 1 -> 0.15f;
                    case 2 -> 0.25f;
                    default -> 0.35f;
                };
                float healAmount = potentialAssimilator.getMaxHealth() * healPercent;
                potentialAssimilator.heal(healAmount);

                // Level III: 3 seconds invulnerability
                if (level >= 3) {
                    potentialAssimilator.setInvulnerable(true);
                    // We set a tag to track the invulnerability timer
                    int invulnTicks = EliteForgeConfig.SERVER.assimilateInvulnTicks.get();
                    potentialAssimilator.getPersistentData().putInt(ASSIMILATE_INVULN_KEY, invulnTicks);
                }

                // Purple soul particles from corpse to assimilator
                spawnAssimilationParticles(serverLevel, deadElite, potentialAssimilator);
            });
        }
    }

    /**
     * Applies stat bonuses based on number of assimilated abilities.
     * Level II: +10% stats per assimilated ability
     * Level III: +15% stats per assimilated ability
     */
    private void applyStatBonuses(LivingEntity entity, int level, int assimilatedCount) {
        if (level < 2) return;

        try {
            double healthBonus = switch (level) {
                case 2 -> 0.10 * assimilatedCount;
                default -> 0.15 * assimilatedCount;
            };
            double damageBonus = switch (level) {
                case 2 -> 0.10 * assimilatedCount;
                default -> 0.15 * assimilatedCount;
            };

            var healthAttr = entity.getAttribute(Attributes.MAX_HEALTH);
            if (healthAttr != null) {
                healthAttr.removeModifier(HEALTH_MODIFIER_UUID);
                double baseHealth = healthAttr.getBaseValue();
                healthAttr.addTransientModifier(new AttributeModifier(
                        HEALTH_MODIFIER_UUID,
                        "EliteForge Assimilate Health",
                        baseHealth * healthBonus,
                        AttributeModifier.Operation.ADDITION
                ));
            }

            var damageAttr = entity.getAttribute(Attributes.ATTACK_DAMAGE);
            if (damageAttr != null) {
                damageAttr.removeModifier(DAMAGE_MODIFIER_UUID);
                damageAttr.addTransientModifier(new AttributeModifier(
                        DAMAGE_MODIFIER_UUID,
                        "EliteForge Assimilate Damage",
                        damageBonus,
                        AttributeModifier.Operation.MULTIPLY_BASE
                ));
            }
        } catch (Exception e) {
            // Attribute may not be available
        }
    }

    /**
     * Spawns purple soul particles traveling from the dead elite to the assimilator.
     */
    private static void spawnAssimilationParticles(ServerLevel serverLevel, LivingEntity source, LivingEntity target) {
        double dx = target.getX() - source.getX();
        double dy = (target.getY() + target.getBbHeight() * 0.5) - (source.getY() + source.getBbHeight() * 0.5);
        double dz = target.getZ() - source.getZ();
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);

        int steps = Math.max(5, (int) (dist / 0.8));
        for (int i = 0; i < steps; i++) {
            double t = (double) i / steps;
            double px = source.getX() + dx * t + (source.getRandom().nextDouble() - 0.5) * 0.3;
            double py = source.getY() + source.getBbHeight() * 0.5 + dy * t + (source.getRandom().nextDouble() - 0.5) * 0.3;
            double pz = source.getZ() + dz * t + (source.getRandom().nextDouble() - 0.5) * 0.3;
            serverLevel.sendParticles(ParticleTypes.PORTAL, px, py, pz, 2, 0.1, 0.1, 0.1, 0.05);
        }

        // Burst of particles at source
        serverLevel.sendParticles(ParticleTypes.SOUL,
                source.getX(), source.getY() + source.getBbHeight() * 0.5, source.getZ(),
                15, 0.5, source.getBbHeight() * 0.5, 0.5, 0.1);
    }
}
