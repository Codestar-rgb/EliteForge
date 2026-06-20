package com.eliteforge.ability.creator;

import com.eliteforge.capability.EliteCapability;
import com.eliteforge.capability.EliteData;
import com.eliteforge.capability.EliteCapabilitySync;
// TemperedMaterial removed in v0.2.0
import com.eliteforge.util.NBTKeys;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

import java.util.UUID;

/**
 * AbilityEvolution (熔炉·进化) - C3 Creator Ability
 * <p>
 * Evolves permanently when taking enough damage.
 * Level I:  every 100 damage → +5% health, +3% damage, max 5 evolutions, size +2%/evolution
 * Level II: every 80 damage → +8% health, +5% damage, +1 armor, max 8 evolutions, size +3% + lava texture
 * Level III:every 60 damage → +10% health, +8% damage, +2 armor, +5% speed, max 12 evolutions,
 *           size +4% + lava + fire crown
 * Track evolution count in NBT.
 * Use AttributeModifier per evolution.
 * At max evolution: "Ultimate Form" - burning effect, attacks set targets on fire
 * Extra tempered material drops based on evolution count
 * <p>
 * NBT keys:
 * <ul>
 *   <li>{@code EliteForgeEvolutionCount} - current evolution count</li>
 *   <li>{@code EliteForgeEvolutionDamageAccum} - accumulated damage toward next evolution</li>
 *   <li>{@code EliteForgeEvolutionApplied} - number of evolutions already applied as attribute modifiers</li>
 * </ul>
 */
public class AbilityEvolution extends CreatorAbility {

    // UUIDs for attribute modifiers - use base + evolution offset pattern
    private static final UUID HEALTH_MODIFIER_UUID = UUID.fromString("c3d4e5f6-a7b8-9012-cdef-345678901201");
    private static final UUID DAMAGE_MODIFIER_UUID = UUID.fromString("d4e5f6a7-b8c9-0123-defa-456789012302");
    private static final UUID ARMOR_MODIFIER_UUID = UUID.fromString("e5f6a7b8-c9d0-1234-efab-567890123403");
    private static final UUID SPEED_MODIFIER_UUID = UUID.fromString("f6a7b8c9-d0e1-2345-fabc-678901234504");

    // NBT keys (reference centralized constants)
    private static final String EVOLUTION_COUNT_KEY = NBTKeys.EVOLUTION_COUNT;
    private static final String EVOLUTION_DAMAGE_ACCUM_KEY = NBTKeys.EVOLUTION_DAMAGE_ACCUM;
    private static final String EVOLUTION_APPLIED_KEY = NBTKeys.EVOLUTION_APPLIED;

    public AbilityEvolution() {
        super(new ResourceLocation("eliteforge", "creator_evolution"), 5.0f);
    }

    @Override
    public void onApply(LivingEntity entity, int level) {
        if (entity.level().isClientSide) return;
        CompoundTag data = entity.getPersistentData();
        data.putInt(EVOLUTION_COUNT_KEY, 0);
        data.putFloat(EVOLUTION_DAMAGE_ACCUM_KEY, 0.0f);
        data.putInt(EVOLUTION_APPLIED_KEY, 0); // Number of evolutions already applied as modifiers

        // Mark as creator entity in capability
        setupCreatorData(entity, level);
    }

    @Override
    public void onRemove(LivingEntity entity, int level) {
        if (entity.level().isClientSide) return;

        // Remove all evolution attribute modifiers
        try {
            var healthAttr = entity.getAttribute(Attributes.MAX_HEALTH);
            if (healthAttr != null) {
                healthAttr.removeModifier(HEALTH_MODIFIER_UUID);
            }
            var damageAttr = entity.getAttribute(Attributes.ATTACK_DAMAGE);
            if (damageAttr != null) {
                damageAttr.removeModifier(DAMAGE_MODIFIER_UUID);
            }
            var armorAttr = entity.getAttribute(Attributes.ARMOR);
            if (armorAttr != null) {
                armorAttr.removeModifier(ARMOR_MODIFIER_UUID);
            }
            var speedAttr = entity.getAttribute(Attributes.MOVEMENT_SPEED);
            if (speedAttr != null) {
                speedAttr.removeModifier(SPEED_MODIFIER_UUID);
            }
        } catch (Exception e) {
            // Attribute may not be available
        }

        // Clean up NBT data
        CompoundTag data = entity.getPersistentData();
        data.remove(EVOLUTION_COUNT_KEY);
        data.remove(EVOLUTION_DAMAGE_ACCUM_KEY);
        data.remove(EVOLUTION_APPLIED_KEY);
    }

    @Override
    public void onTick(LivingEntity entity, int level) {
        if (entity.level().isClientSide) return;

        CompoundTag data = entity.getPersistentData();
        if (!data.contains(EVOLUTION_COUNT_KEY)) {
            // Idempotency check: if capability data is already set, just re-initialize NBT
            // without re-calling onApply (which would re-apply attribute modifiers)
            entity.getCapability(EliteCapability.CAPABILITY).ifPresent(cap -> {
                EliteData eliteData = cap.getEliteData();
                if (eliteData.isCreatorEntity() && getIdString().equals(eliteData.getCreatorAbilityId())) {
                    data.putInt(EVOLUTION_COUNT_KEY, 0);
                    data.putFloat(EVOLUTION_DAMAGE_ACCUM_KEY, 0.0f);
                    data.putInt(EVOLUTION_APPLIED_KEY, 0);
                    return;
                }
            });
            // If capability wasn't set or didn't match, do full onApply
            if (!data.contains(EVOLUTION_COUNT_KEY)) {
                onApply(entity, level);
            }
            return;
        }

        int evolutionCount = data.getInt(EVOLUTION_COUNT_KEY);
        int maxEvolutions = switch (level) {
            case 1 -> 5;
            case 2 -> 8;
            default -> 12;
        };

        // Re-apply attribute modifiers if evolution count changed
        int appliedCount = data.getInt(EVOLUTION_APPLIED_KEY);
        if (appliedCount < evolutionCount) {
            applyEvolutionModifiers(entity, level, evolutionCount);
            data.putInt(EVOLUTION_APPLIED_KEY, evolutionCount);
        }

        // At max evolution: "Ultimate Form"
        if (evolutionCount >= maxEvolutions) {
            // Visual-only burning effect: Fire Resistance prevents damage,
            // and we rely solely on particles instead of actual fire rendering
            // (setSecondsOnFire was removed to avoid fighting against Fire Resistance)
            if (entity.tickCount % 20 == 0) {
                entity.setRemainingFireTicks(0);
                entity.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 40, 0, false, false));
            }

            // Fire particles (fire crown effect)
            if (entity.level() instanceof ServerLevel serverLevel) {
                if (entity.tickCount % 5 == 0) {
                    serverLevel.sendParticles(ParticleTypes.FLAME,
                            entity.getX(), entity.getY() + entity.getBbHeight() + 0.2, entity.getZ(),
                            3, 0.2, 0.1, 0.2, 0.02);
                    // Lava drip particles
                    serverLevel.sendParticles(ParticleTypes.DRIPPING_LAVA,
                            entity.getX(), entity.getY() + entity.getBbHeight() * 0.8, entity.getZ(),
                            2, entity.getBbWidth() * 0.3, entity.getBbHeight() * 0.2, entity.getBbWidth() * 0.3,
                            0.01);
                }
            }
        } else if (evolutionCount > 0 && entity.level() instanceof ServerLevel serverLevel) {
            // Sub-max evolution: lava texture particles at level II+
            if (level >= 2 && entity.tickCount % 15 == 0) {
                serverLevel.sendParticles(ParticleTypes.DRIPPING_LAVA,
                        entity.getX(), entity.getY() + entity.getBbHeight() * 0.5, entity.getZ(),
                        1, entity.getBbWidth() * 0.3, entity.getBbHeight() * 0.3, entity.getBbWidth() * 0.3,
                        0.01);
            }
            // Fire crown at level III
            if (level >= 3 && entity.tickCount % 8 == 0) {
                serverLevel.sendParticles(ParticleTypes.FLAME,
                        entity.getX(), entity.getY() + entity.getBbHeight() + 0.1, entity.getZ(),
                        1, 0.15, 0.05, 0.15, 0.01);
            }
        }

        // Size scaling via attribute (use scale attribute if available, or just visual)
        // In 1.20.1 there's no direct scale attribute, so we skip actual size changes
        // and rely on visual effects instead
    }

    @Override
    public void onHurt(LivingEntity entity, float damage, int level) {
        if (entity.level().isClientSide) return;

        CompoundTag data = entity.getPersistentData();
        if (!data.contains(EVOLUTION_COUNT_KEY)) return;

        int currentCount = data.getInt(EVOLUTION_COUNT_KEY);
        int maxEvolutions = switch (level) {
            case 1 -> 5;
            case 2 -> 8;
            default -> 12;
        };

        if (currentCount >= maxEvolutions) return; // Already at max

        float damageThreshold = switch (level) {
            case 1 -> 100.0f;
            case 2 -> 80.0f;
            default -> 60.0f;
        };

        float accumulated = data.getFloat(EVOLUTION_DAMAGE_ACCUM_KEY);
        accumulated += damage;

        if (accumulated >= damageThreshold) {
            // Evolution!
            int newCount = currentCount + 1;
            data.putInt(EVOLUTION_COUNT_KEY, newCount);
            data.putFloat(EVOLUTION_DAMAGE_ACCUM_KEY, accumulated - damageThreshold);

            // Update EliteData
            entity.getCapability(EliteCapability.CAPABILITY).ifPresent(cap -> {
                EliteData eliteData = cap.getEliteData();
                eliteData.setEvolutionCount(newCount);
                cap.setEliteData(eliteData);
                EliteCapabilitySync.broadcastEliteDataUpdate(entity, eliteData);
            });

            // Evolution particles
            if (entity.level() instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ParticleTypes.EXPLOSION,
                        entity.getX(), entity.getY() + entity.getBbHeight() * 0.5, entity.getZ(),
                        3, 0.5, entity.getBbHeight() * 0.5, 0.5, 0.1);
                serverLevel.sendParticles(ParticleTypes.LAVA,
                        entity.getX(), entity.getY() + entity.getBbHeight() * 0.5, entity.getZ(),
                        10 + newCount * 3, 1.0, entity.getBbHeight() * 0.5, 1.0, 0.1);

                // Notify nearby players
                // C1: Now uses translatable key "message.eliteforge.evolution.ultimate"
                // with entityName as %s parameter.
                if (newCount == maxEvolutions) {
                    AABB area = new AABB(
                            entity.getX() - 32, entity.getY() - 32, entity.getZ() - 32,
                            entity.getX() + 32, entity.getY() + 32, entity.getZ() + 32
                    );
                    Component message = Component.translatable("message.eliteforge.evolution.ultimate",
                                    entity.getName().getString())
                            .withStyle(net.minecraft.ChatFormatting.DARK_RED, net.minecraft.ChatFormatting.BOLD);
                    for (Player player : serverLevel.getEntitiesOfClass(Player.class, area)) {
                        player.sendSystemMessage(message);
                    }
                }
            }
        } else {
            data.putFloat(EVOLUTION_DAMAGE_ACCUM_KEY, accumulated);
        }
    }

    @Override
    public void onAttack(LivingEntity attacker, LivingEntity target, float damage, int level) {
        if (attacker.level().isClientSide) return;

        CompoundTag data = attacker.getPersistentData();
        int evolutionCount = data.getInt(EVOLUTION_COUNT_KEY);
        int maxEvolutions = switch (level) {
            case 1 -> 5;
            case 2 -> 8;
            default -> 12;
        };

        // At max evolution: attacks set targets on fire
        if (evolutionCount >= maxEvolutions) {
            target.setSecondsOnFire(3 + level);
        }
    }

    @Override
    public void onDeath(LivingEntity entity, int level) {
        if (entity.level().isClientSide) return;

        CompoundTag data = entity.getPersistentData();
        int evolutionCount = data.getInt(EVOLUTION_COUNT_KEY);

        if (evolutionCount <= 0) return;

        // Drop extra Evolution Cores based on evolution count.
        // (Previously dropped TemperedMaterial, which was removed in v0.2.0 phase 1;
        // the Evolution Core is the thematic successor growth-material item.)
        if (entity.level() instanceof ServerLevel serverLevel) {
            int materialCount = Math.max(1, evolutionCount / 2);
            for (int i = 0; i < materialCount; i++) {
                ItemStack material = new ItemStack(com.eliteforge.init.ModItems.EVOLUTION_CORE.get());
                ItemEntity itemEntity = new ItemEntity(serverLevel,
                        entity.getX() + (serverLevel.random.nextDouble() - 0.5) * 2,
                        entity.getY() + 0.5,
                        entity.getZ() + (serverLevel.random.nextDouble() - 0.5) * 2,
                        material);
                serverLevel.addFreshEntity(itemEntity);
            }
        }
    }

    /**
     * Applies attribute modifiers based on current evolution count.
     * Removes old modifiers first, then applies cumulative bonuses.
     */
    private void applyEvolutionModifiers(LivingEntity entity, int level, int evolutionCount) {
        try {
            // Calculate bonuses per evolution based on level
            double healthPerEvo = switch (level) {
                case 1 -> 0.05;
                case 2 -> 0.08;
                default -> 0.10;
            };
            double damagePerEvo = switch (level) {
                case 1 -> 0.03;
                case 2 -> 0.05;
                default -> 0.08;
            };
            double armorPerEvo = switch (level) {
                case 1 -> 0;
                case 2 -> 1.0;
                default -> 2.0;
            };
            double speedPerEvo = level >= 3 ? 0.05 : 0;

            // Health modifier
            var healthAttr = entity.getAttribute(Attributes.MAX_HEALTH);
            if (healthAttr != null) {
                healthAttr.removeModifier(HEALTH_MODIFIER_UUID);
                double baseHealth = healthAttr.getBaseValue();
                double healthBonus = baseHealth * healthPerEvo * evolutionCount;
                healthAttr.addTransientModifier(new AttributeModifier(
                        HEALTH_MODIFIER_UUID,
                        "EliteForge Evolution Health",
                        healthBonus,
                        AttributeModifier.Operation.ADDITION
                ));
            }

            // Damage modifier
            var damageAttr = entity.getAttribute(Attributes.ATTACK_DAMAGE);
            if (damageAttr != null) {
                damageAttr.removeModifier(DAMAGE_MODIFIER_UUID);
                double damageBonus = damagePerEvo * evolutionCount;
                damageAttr.addTransientModifier(new AttributeModifier(
                        DAMAGE_MODIFIER_UUID,
                        "EliteForge Evolution Damage",
                        damageBonus,
                        AttributeModifier.Operation.MULTIPLY_BASE
                ));
            }

            // Armor modifier (Level II+)
            if (armorPerEvo > 0) {
                var armorAttr = entity.getAttribute(Attributes.ARMOR);
                if (armorAttr != null) {
                    armorAttr.removeModifier(ARMOR_MODIFIER_UUID);
                    double armorBonus = armorPerEvo * evolutionCount;
                    armorAttr.addTransientModifier(new AttributeModifier(
                            ARMOR_MODIFIER_UUID,
                            "EliteForge Evolution Armor",
                            armorBonus,
                            AttributeModifier.Operation.ADDITION
                    ));
                }
            }

            // Speed modifier (Level III)
            if (speedPerEvo > 0) {
                var speedAttr = entity.getAttribute(Attributes.MOVEMENT_SPEED);
                if (speedAttr != null) {
                    speedAttr.removeModifier(SPEED_MODIFIER_UUID);
                    double speedBonus = speedPerEvo * evolutionCount;
                    speedAttr.addTransientModifier(new AttributeModifier(
                            SPEED_MODIFIER_UUID,
                            "EliteForge Evolution Speed",
                            speedBonus,
                            AttributeModifier.Operation.MULTIPLY_BASE
                    ));
                }
            }
        } catch (Exception e) {
            // Attribute may not be available for all entities
        }
    }
}
