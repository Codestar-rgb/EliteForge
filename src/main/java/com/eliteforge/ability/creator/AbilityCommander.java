package com.eliteforge.ability.creator;

import com.eliteforge.capability.EliteCapability;
import com.eliteforge.capability.EliteData;
import com.eliteforge.capability.EliteCapabilitySync;
import com.eliteforge.spawn.EliteEcosystem;
import com.eliteforge.util.NBTKeys;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * AbilityCommander (纷争·号令) - C8 Creator Ability
 * <p>
 * Commands nearby elites with tactical behaviors.
 * Level I:  range 20, squad of 4, focus fire tactic, every 100 ticks (5s)
 * Level II: range 30, squad of 6, I + surround tactic + protect commander, every 60 ticks (3s)
 * Level III:range 40, squad of 10, II + ranged/melee分工 + Speed I + Strength I buffs, every 40 ticks (2s)
 * Commander gets "crown" particles, commanded elites get arrow pointing to commander.
 * Tactical AI: set targets, protect commander, surround, divide by role.
 * On commander death: squad goes "chaotic" for 5 seconds (random attack/flee).
 * Red pulse wave on each command issued.
 * <p>
 * NBT keys:
 * <ul>
 *   <li>{@code EliteForgeCommanderCooldown} - cooldown timer in ticks</li>
 *   <li>{@code EliteForgeCommanderSquad} - ListTag of squad member UUIDs</li>
 * </ul>
 */
public class AbilityCommander extends CreatorAbility {

    // NBT keys (reference centralized constants)
    private static final String COMMANDER_COOLDOWN_KEY = NBTKeys.COMMANDER_COOLDOWN;
    private static final String COMMANDER_SQUAD_KEY = NBTKeys.COMMANDER_SQUAD; // ListTag of UUIDs

    public AbilityCommander() {
        super(new ResourceLocation("eliteforge", "creator_commander"), 5.5f);
    }

    @Override
    public void onApply(LivingEntity entity, int level) {
        if (entity.level().isClientSide) return;
        CompoundTag data = entity.getPersistentData();
        data.putInt(COMMANDER_COOLDOWN_KEY, 0);

        // Mark as creator entity in capability
        setupCreatorData(entity, level);
    }

    @Override
    public void onRemove(LivingEntity entity, int level) {
        if (entity.level().isClientSide) return;

        // Clean up NBT data
        CompoundTag data = entity.getPersistentData();
        data.remove(COMMANDER_COOLDOWN_KEY);
        data.remove(COMMANDER_SQUAD_KEY);

        // Clear commander UUID from squad members
        if (entity.level() instanceof ServerLevel serverLevel) {
            double range = 45.0; // Slightly larger than max commander range
            AABB area = new AABB(
                    entity.getX() - range, entity.getY() - range, entity.getZ() - range,
                    entity.getX() + range, entity.getY() + range, entity.getZ() + range
            );
            for (LivingEntity nearby : serverLevel.getEntitiesOfClass(LivingEntity.class, area,
                    e -> e.isAlive() && e != entity && e.getCapability(EliteCapability.CAPABILITY).isPresent())) {
                nearby.getCapability(EliteCapability.CAPABILITY).ifPresent(cap -> {
                    EliteData nearbyData = cap.getEliteData();
                    if (entity.getUUID().equals(nearbyData.getCommanderUUID())) {
                        nearbyData.setCommanderUUID(null);
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
        if (!data.contains(COMMANDER_COOLDOWN_KEY)) {
            // Idempotency check: if capability data is already set, just re-initialize NBT
            // without re-calling onApply
            entity.getCapability(EliteCapability.CAPABILITY).ifPresent(cap -> {
                EliteData eliteData = cap.getEliteData();
                if (eliteData.isCreatorEntity() && getIdString().equals(eliteData.getCreatorAbilityId())) {
                    data.putInt(COMMANDER_COOLDOWN_KEY, 0);
                    return;
                }
            });
            // If capability wasn't set or didn't match, do full onApply
            if (!data.contains(COMMANDER_COOLDOWN_KEY)) {
                onApply(entity, level);
            }
            return;
        }

        // Crown particles on commander
        if (entity.level() instanceof ServerLevel serverLevel && entity.tickCount % 8 == 0) {
            spawnCrownParticles(serverLevel, entity);
        }

        int cooldown = data.getInt(COMMANDER_COOLDOWN_KEY);
        if (cooldown > 0) {
            data.putInt(COMMANDER_COOLDOWN_KEY, cooldown - 1);
            return;
        }

        // Determine parameters based on level
        double range = switch (level) {
            case 1 -> 20.0;
            case 2 -> 30.0;
            default -> 40.0;
        };
        int interval = switch (level) {
            case 1 -> 100;
            case 2 -> 60;
            default -> 40;
        };
        int maxSquadSize = switch (level) {
            case 1 -> 4;
            case 2 -> 6;
            default -> 10;
        };

        // Set cooldown
        data.putInt(COMMANDER_COOLDOWN_KEY, interval);

        if (!(entity.level() instanceof ServerLevel serverLevel)) return;

        // Delegate command logic to EliteEcosystem (single source of truth)
        // EliteEcosystem handles: squad building, target setting, surround tactic,
        // protect commander, role division, and Speed/Strength buffs
        List<LivingEntity> squad = EliteEcosystem.commandSquad(entity, maxSquadSize, range, level);

        // Spawn directional particles from squad members to commander
        for (LivingEntity member : squad) {
            spawnDirectionalParticle(serverLevel, member, entity);
        }

        // Red pulse wave on command issued
        if (!squad.isEmpty()) {
            spawnCommandPulse(serverLevel, entity, range);
        }
    }

    @Override
    public void onDeath(LivingEntity entity, int level) {
        if (entity.level().isClientSide) return;

        if (!(entity.level() instanceof ServerLevel serverLevel)) return;

        // Find all squad members and send them into chaos
        double range = switch (level) {
            case 1 -> 20.0;
            case 2 -> 30.0;
            default -> 40.0;
        };

        AABB area = new AABB(
                entity.getX() - range, entity.getY() - range, entity.getZ() - range,
                entity.getX() + range, entity.getY() + range, entity.getZ() + range
        );

        List<LivingEntity> nearbyElites = serverLevel.getEntitiesOfClass(LivingEntity.class, area,
                e -> e.isAlive() && e.getCapability(EliteCapability.CAPABILITY).isPresent());

        for (LivingEntity elite : nearbyElites) {
            elite.getCapability(EliteCapability.CAPABILITY).ifPresent(cap -> {
                EliteData eliteData = cap.getEliteData();
                if (entity.getUUID().equals(eliteData.getCommanderUUID())) {
                    // Clear commander reference
                    eliteData.setCommanderUUID(null);
                    cap.setEliteData(eliteData);
                    EliteCapabilitySync.broadcastEliteDataUpdate(elite, eliteData);

                    // Apply chaos effect for 5 seconds (100 ticks)
                    // Random attack/flee behavior via confusion effect
                    elite.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 100, 0, false, true));
                    elite.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 100, 1, false, true));

                    // Clear their target so they attack randomly
                    if (elite instanceof Mob mob) {
                        mob.setTarget(null);
                    }
                }
            });
        }

        // Death particles
        serverLevel.sendParticles(ParticleTypes.EXPLOSION,
                entity.getX(), entity.getY() + entity.getBbHeight() * 0.5, entity.getZ(),
                3, 1.0, entity.getBbHeight() * 0.5, 1.0, 0.1);
    }

    /**
     * Spawns crown particles above the commander's head.
     */
    private void spawnCrownParticles(ServerLevel serverLevel, LivingEntity entity) {
        double headY = entity.getY() + entity.getBbHeight() + 0.3;
        // Crown ring
        int points = 5;
        for (int i = 0; i < points; i++) {
            double angle = (Math.PI * 2 * i) / points + entity.tickCount * 0.03;
            double radius = 0.3;
            double px = entity.getX() + Math.cos(angle) * radius;
            double pz = entity.getZ() + Math.sin(angle) * radius;
            serverLevel.sendParticles(ParticleTypes.END_ROD, px, headY, pz, 1, 0, 0.02, 0, 0);
        }
    }

    /**
     * Spawns a directional particle from squad member toward the commander.
     */
    private void spawnDirectionalParticle(ServerLevel serverLevel, LivingEntity from, LivingEntity to) {
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (dist < 1.0) return;

        // Normalize and spawn a few particles in the direction of the commander
        double nx = dx / dist;
        double nz = dz / dist;
        double px = from.getX() + nx * 1.5;
        double pz = from.getZ() + nz * 1.5;
        serverLevel.sendParticles(ParticleTypes.ENCHANT,
                px, from.getY() + from.getBbHeight() * 0.5, pz, 2,
                0.1, 0.2, 0.1, 0.1);
    }

    /**
     * Spawns a red pulse wave when a command is issued.
     */
    private void spawnCommandPulse(ServerLevel serverLevel, LivingEntity entity, double range) {
        // Red pulse ring expanding outward
        int points = 16;
        for (int i = 0; i < points; i++) {
            double angle = (Math.PI * 2 * i) / points;
            double px = entity.getX() + Math.cos(angle) * range * 0.5;
            double pz = entity.getZ() + Math.sin(angle) * range * 0.5;
            serverLevel.sendParticles(ParticleTypes.DRAGON_BREATH, px, entity.getY() + 0.5, pz, 2,
                    0.5, 0.2, 0.5, 0.01);
        }
    }
}
