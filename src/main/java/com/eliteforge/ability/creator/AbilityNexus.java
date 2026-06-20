package com.eliteforge.ability.creator;

import com.eliteforge.capability.EliteCapability;
import com.eliteforge.capability.EliteData;
import com.eliteforge.capability.EliteCapabilitySync;
import com.eliteforge.spawn.EliteEcosystem;
import com.eliteforge.util.NBTKeys;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.UUID;

/**
 * AbilityNexus (源核·滋养) - C1 Creator Ability
 * <p>
 * Passive aura that nurtures nearby elite mobs.
 * Level I:  range 16, every 100 ticks (5s), nurture up to 3 elites:
 *           target elite level+1, random ability level+1. Self: +20% health, +10% damage
 * Level II: range 24, every 60 ticks (3s), nurture up to 5 elites:
 *           I effects + 30% chance grant 1 new I-level ability. Self: +40% health, +20% damage
 * Level III:range 32, every 40 ticks (2s), nurture up to 8 elites:
 *           II effects + equipment quality upgrade. Self: +60% health, +30% damage, Regeneration I
 * On death: all nurtured elites get 10s "Rage" effect (Speed +50%, Damage +30%, Defense -20%)
 * Particles: Golden pulse aura on self, golden particle stream to nurtured targets
 * <p>
 * NBT keys:
 * <ul>
 *   <li>{@code EliteForgeNexusCooldown} - cooldown timer in ticks</li>
 *   <li>{@code EliteForgeNexusNurtured} - ListTag of nurtured elite UUIDs</li>
 *   <li>{@code EliteForgeNexusBonusesApplied} - level of self bonuses currently applied</li>
 *   <li>{@code EliteForgeNexusActive} - true while the nexus ability is active</li>
 * </ul>
 */
public class AbilityNexus extends CreatorAbility {

    private static final UUID HEALTH_MODIFIER_UUID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567801");
    private static final UUID DAMAGE_MODIFIER_UUID = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678012");
    private static final UUID RAGE_DAMAGE_MODIFIER_UUID = UUID.fromString("f1a2b3c4-d5e6-7890-abcd-ef1234567890");
    private static final UUID RAGE_ARMOR_MODIFIER_UUID = UUID.fromString("a2b3c4d5-e6f7-8901-bcde-f12345678901");

    // NBT keys (reference centralized constants)
    private static final String NEXUS_COOLDOWN_KEY = NBTKeys.NEXUS_COOLDOWN;
    private static final String NEXUS_NURTURED_KEY = NBTKeys.NEXUS_NURTURED; // ListTag of UUIDs
    private static final String NEXUS_BONUSES_APPLIED_KEY = NBTKeys.NEXUS_BONUSES_APPLIED; // Stores level of bonuses applied
    private static final String NEXUS_ACTIVE_KEY = NBTKeys.NEXUS_ACTIVE;

    public AbilityNexus() {
        super(new ResourceLocation("eliteforge", "creator_nexus"), 6.0f);
    }

    @Override
    public void onApply(LivingEntity entity, int level) {
        if (entity.level().isClientSide) return;
        CompoundTag data = entity.getPersistentData();
        data.putInt(NEXUS_COOLDOWN_KEY, 0);
        data.putBoolean(NEXUS_ACTIVE_KEY, true);

        // Apply self stat bonuses
        applySelfBonuses(entity, level);

        // Store applied bonus level so we don't re-apply every tick
        entity.getPersistentData().putInt(NEXUS_BONUSES_APPLIED_KEY, level);

        // Mark as creator entity in capability (also broadcasts the update).
        // NOTE: We intentionally do NOT set nexusSourceUUID here. That field
        // means "which Nexus is nurturing THIS entity" and is set only on
        // non-creator elites that are being nurtured by this Nexus (see
        // EliteEcosystem.nurtureNearbyElites). Setting it on the Nexus itself
        // would cause the Nexus to be incorrectly matched by its own UUID in
        // onDeath's rage-effect loop, and pollutes the data semantics.
        setupCreatorData(entity, level);
    }

    @Override
    public void onRemove(LivingEntity entity, int level) {
        if (entity.level().isClientSide) return;

        // Q4: Use centralized safe modifier helpers instead of inline try/catch
        safeRemoveModifier(entity, Attributes.MAX_HEALTH, HEALTH_MODIFIER_UUID);
        safeRemoveModifier(entity, Attributes.ATTACK_DAMAGE, DAMAGE_MODIFIER_UUID);

        // Clean up NBT data
        CompoundTag data = entity.getPersistentData();
        data.remove(NEXUS_COOLDOWN_KEY);
        data.remove(NEXUS_NURTURED_KEY);
        data.remove(NEXUS_BONUSES_APPLIED_KEY);
        data.remove(NEXUS_ACTIVE_KEY);

        // Clear nexus source UUID from nearby nurtured elites
        if (entity.level() instanceof ServerLevel serverLevel) {
            double range = switch (level) {
                case 1 -> 16.0;
                case 2 -> 24.0;
                default -> 32.0;
            };
            AABB area = new AABB(
                    entity.getX() - range, entity.getY() - range, entity.getZ() - range,
                    entity.getX() + range, entity.getY() + range, entity.getZ() + range
            );
            for (LivingEntity nearby : serverLevel.getEntitiesOfClass(LivingEntity.class, area,
                    e -> e.isAlive() && e != entity && e.getCapability(EliteCapability.CAPABILITY).isPresent())) {
                nearby.getCapability(EliteCapability.CAPABILITY).ifPresent(cap -> {
                    EliteData nearbyData = cap.getEliteData();
                    if (entity.getUUID().equals(nearbyData.getNexusSourceUUID())) {
                        nearbyData.setNexusSourceUUID(null);
                        cap.setEliteData(nearbyData);
                        EliteCapabilitySync.broadcastEliteDataUpdate(nearby, nearbyData);
                    }
                });
            }
        }
    }

    @Override
    public void onTick(LivingEntity entity, int level) {
        if (entity.level().isClientSide) return;

        CompoundTag data = entity.getPersistentData();
        if (!data.contains(NEXUS_COOLDOWN_KEY)) {
            // Idempotency check: if capability data is already set, just re-initialize NBT
            // without re-calling onApply (which would re-apply attribute modifiers)
            entity.getCapability(EliteCapability.CAPABILITY).ifPresent(cap -> {
                EliteData eliteData = cap.getEliteData();
                if (eliteData.isCreatorEntity() && getIdString().equals(eliteData.getCreatorAbilityId())) {
                    data.putInt(NEXUS_COOLDOWN_KEY, 0);
                    data.putBoolean(NEXUS_ACTIVE_KEY, true);
                    data.putInt(NEXUS_BONUSES_APPLIED_KEY, level);
                    return;
                }
            });
            // If capability wasn't set or didn't match, do full onApply
            if (!data.contains(NEXUS_COOLDOWN_KEY)) {
                onApply(entity, level);
            }
            return;
        }

        // Only re-apply self bonuses if the stored level differs from the current level
        // This avoids removing and re-adding attribute modifiers every tick
        int appliedLevel = data.getInt(NEXUS_BONUSES_APPLIED_KEY);
        if (appliedLevel != level) {
            applySelfBonuses(entity, level);
            data.putInt(NEXUS_BONUSES_APPLIED_KEY, level);
        }

        // Level III: Regeneration I
        if (level >= 3) {
            if (!entity.hasEffect(MobEffects.REGENERATION) || entity.getEffect(MobEffects.REGENERATION).getDuration() < 40) {
                entity.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 60, 0, false, true));
            }
        }

        // Cooldown logic
        int cooldown = data.getInt(NEXUS_COOLDOWN_KEY);
        if (cooldown > 0) {
            data.putInt(NEXUS_COOLDOWN_KEY, cooldown - 1);
            // Still show aura particles while on cooldown
            if (entity.level() instanceof ServerLevel serverLevel && entity.tickCount % 20 == 0) {
                spawnAuraParticles(serverLevel, entity, level);
            }
            return;
        }

        // Determine interval, range, and max targets based on level
        int interval = switch (level) {
            case 1 -> 100;
            case 2 -> 60;
            default -> 40; // Level 3
        };
        double range = switch (level) {
            case 1 -> 16.0;
            case 2 -> 24.0;
            default -> 32.0; // Level 3
        };
        int maxTargets = switch (level) {
            case 1 -> 3;
            case 2 -> 5;
            default -> 8; // Level 3
        };

        // Set cooldown
        data.putInt(NEXUS_COOLDOWN_KEY, interval);

        if (!(entity.level() instanceof ServerLevel serverLevel)) return;

        // Delegate nurturing logic to EliteEcosystem (single source of truth)
        // EliteEcosystem handles: level+1, ability level+1, new ability grant (with mutual exclusion), quality upgrade (capped at EPIC)
        List<LivingEntity> nurturedTargets = EliteEcosystem.nurtureNearbyElites(entity, level, range, maxTargets);

        // Spawn golden particle streams to nurtured targets
        for (LivingEntity target : nurturedTargets) {
            spawnNurtureParticleStream(serverLevel, entity, target);
        }

        // Aura particles on self
        spawnAuraParticles(serverLevel, entity, level);
    }

    @Override
    public void onDeath(LivingEntity entity, int level) {
        if (entity.level().isClientSide) return;

        if (!(entity.level() instanceof ServerLevel serverLevel)) return;

        double range = switch (level) {
            case 1 -> 16.0;
            case 2 -> 24.0;
            default -> 32.0;
        };

        // Find all nurtured elites and give them "Rage" effect
        AABB area = new AABB(
                entity.getX() - range, entity.getY() - range, entity.getZ() - range,
                entity.getX() + range, entity.getY() + range, entity.getZ() + range
        );

        List<LivingEntity> nearbyElites = serverLevel.getEntitiesOfClass(LivingEntity.class, area,
                e -> e.isAlive() && e.getCapability(EliteCapability.CAPABILITY).isPresent());

        for (LivingEntity target : nearbyElites) {
            target.getCapability(EliteCapability.CAPABILITY).ifPresent(cap -> {
                EliteData targetData = cap.getEliteData();
                if (targetData.isElite() && entity.getUUID().equals(targetData.getNexusSourceUUID())) {
                    // Apply "Rage" effect for 10 seconds (200 ticks)
                    // Speed I (+40% speed ≈ +50% design intent), Damage +30%, Defense -20%
                    target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 200, 1, false, true));

                    // Damage +30%: AttributeModifier on ATTACK_DAMAGE with MULTIPLY_BASE
                    try {
                        var damageAttr = target.getAttribute(Attributes.ATTACK_DAMAGE);
                        if (damageAttr != null) {
                            damageAttr.removeModifier(RAGE_DAMAGE_MODIFIER_UUID);
                            damageAttr.addTransientModifier(new AttributeModifier(
                                    RAGE_DAMAGE_MODIFIER_UUID,
                                    "EliteForge Nexus Rage Damage",
                                    0.3,
                                    AttributeModifier.Operation.MULTIPLY_BASE
                            ));
                        }
                    } catch (Exception e) {
                        // Attribute may not be available for all entities
                    }

                    // Defense -20%: AttributeModifier on ARMOR with ADDITION (flat -4 armor reduction as proxy)
                    try {
                        var armorAttr = target.getAttribute(Attributes.ARMOR);
                        if (armorAttr != null) {
                            armorAttr.removeModifier(RAGE_ARMOR_MODIFIER_UUID);
                            armorAttr.addTransientModifier(new AttributeModifier(
                                    RAGE_ARMOR_MODIFIER_UUID,
                                    "EliteForge Nexus Rage Armor Reduction",
                                    -4.0,
                                    AttributeModifier.Operation.ADDITION
                            ));
                        }
                    } catch (Exception e) {
                        // Attribute may not be available for all entities
                    }

                    // Schedule removal of rage attribute modifiers after 200 ticks (10 seconds)
                    final var server = serverLevel.getServer();
                    server.tell(new TickTask(server.getTickCount() + 200, () -> {
                        if (target.isAlive()) {
                            try {
                                var dmgAttr = target.getAttribute(Attributes.ATTACK_DAMAGE);
                                if (dmgAttr != null) {
                                    dmgAttr.removeModifier(RAGE_DAMAGE_MODIFIER_UUID);
                                }
                            } catch (Exception ignored) {}
                            try {
                                var armorAttr = target.getAttribute(Attributes.ARMOR);
                                if (armorAttr != null) {
                                    armorAttr.removeModifier(RAGE_ARMOR_MODIFIER_UUID);
                                }
                            } catch (Exception ignored) {}
                        }
                    }));

                    // Clear nexus source
                    targetData.setNexusSourceUUID(null);
                    cap.setEliteData(targetData);
                    EliteCapabilitySync.broadcastEliteDataUpdate(target, targetData);
                }
            });
        }

        // Death explosion particles
        serverLevel.sendParticles(ParticleTypes.EXPLOSION,
                entity.getX(), entity.getY() + entity.getBbHeight() * 0.5, entity.getZ(),
                5, 1.0, entity.getBbHeight() * 0.5, 1.0, 0.1);
    }

    /**
     * Applies the self stat bonuses based on level.
     * Level I:   +20% health, +10% damage
     * Level II:  +40% health, +20% damage
     * Level III: +60% health, +30% damage
     */
    private void applySelfBonuses(LivingEntity entity, int level) {
        try {
            double healthMult = switch (level) {
                case 1 -> 0.20;
                case 2 -> 0.40;
                default -> 0.60;
            };
            double damageMult = switch (level) {
                case 1 -> 0.10;
                case 2 -> 0.20;
                default -> 0.30;
            };

            var healthAttr = entity.getAttribute(Attributes.MAX_HEALTH);
            if (healthAttr != null) {
                healthAttr.removeModifier(HEALTH_MODIFIER_UUID);
                double baseHealth = healthAttr.getBaseValue();
                healthAttr.addTransientModifier(new AttributeModifier(
                        HEALTH_MODIFIER_UUID,
                        "EliteForge Nexus Health",
                        baseHealth * healthMult,
                        AttributeModifier.Operation.ADDITION
                ));
            }

            var damageAttr = entity.getAttribute(Attributes.ATTACK_DAMAGE);
            if (damageAttr != null) {
                damageAttr.removeModifier(DAMAGE_MODIFIER_UUID);
                damageAttr.addTransientModifier(new AttributeModifier(
                        DAMAGE_MODIFIER_UUID,
                        "EliteForge Nexus Damage",
                        damageMult,
                        AttributeModifier.Operation.MULTIPLY_BASE
                ));
            }
        } catch (Exception e) {
            // Attribute may not be available for all entities
        }
    }

    /**
     * Spawns golden pulse aura particles around the nexus entity.
     */
    private void spawnAuraParticles(ServerLevel serverLevel, LivingEntity entity, int level) {
        int particleCount = 3 + level;
        for (int i = 0; i < particleCount; i++) {
            double angle = (entity.tickCount * 0.1) + (Math.PI * 2 * i) / particleCount;
            double radius = 1.2 + level * 0.3;
            double px = entity.getX() + Math.cos(angle) * radius;
            double pz = entity.getZ() + Math.sin(angle) * radius;
            serverLevel.sendParticles(ParticleTypes.END_ROD,
                    px, entity.getY() + 0.3, pz, 1,
                    0, 0.05, 0, 0);
        }
        // Central golden sparkle
        serverLevel.sendParticles(ParticleTypes.ENCHANT,
                entity.getX(), entity.getY() + entity.getBbHeight() * 0.5, entity.getZ(),
                2 + level,
                entity.getBbWidth() * 0.3, entity.getBbHeight() * 0.3, entity.getBbWidth() * 0.3,
                0.2);
    }

    /**
     * Spawns golden particle stream from nexus to nurtured target.
     */
    private void spawnNurtureParticleStream(ServerLevel serverLevel, LivingEntity source, LivingEntity target) {
        double dx = target.getX() - source.getX();
        double dy = (target.getY() + target.getBbHeight() * 0.5) - (source.getY() + source.getBbHeight() * 0.5);
        double dz = target.getZ() - source.getZ();
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);

        int steps = Math.max(3, (int) (dist / 1.5));
        for (int i = 0; i < steps; i++) {
            double t = (double) i / steps;
            double px = source.getX() + dx * t + (source.getRandom().nextDouble() - 0.5) * 0.3;
            double py = source.getY() + source.getBbHeight() * 0.5 + dy * t + (source.getRandom().nextDouble() - 0.5) * 0.3;
            double pz = source.getZ() + dz * t + (source.getRandom().nextDouble() - 0.5) * 0.3;
            serverLevel.sendParticles(ParticleTypes.END_ROD, px, py, pz, 1, 0, 0.02, 0, 0);
        }
    }
}
