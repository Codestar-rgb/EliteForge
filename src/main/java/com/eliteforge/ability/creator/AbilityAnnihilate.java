package com.eliteforge.ability.creator;

import com.eliteforge.capability.EliteCapability;
import com.eliteforge.capability.EliteData;
import com.eliteforge.capability.EliteCapabilitySync;
import com.eliteforge.config.EliteForgeConfig;
import com.eliteforge.util.NBTKeys;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * AbilityAnnihilate (湮灭·终焉) - C6 Creator Ability
 * <p>
 * Devastating death explosion.
 * Level I:  8-block radius, 30 damage (15 hearts), 3s warning
 * Level II: 12-block radius, 50 damage (25 hearts), chain: nearby elites also explode at 50% damage, 4s warning
 * Level III:16-block radius, 80 damage (40 hearts), II + "Scorched Earth" zone (10s, 5 fire damage/sec), 5s warning
 * Warning phase: entity shakes + red-black vortex + countdown sound
 * Explosion doesn't destroy blocks, only entity damage
 * Player who killed the creator gets 50% damage reduction from the explosion
 * Dropped items teleported to safe position
 * <p>
 * NBT keys:
 * <ul>
 *   <li>{@code EliteForgeAnnihilateWarning} - true while in warning phase</li>
 *   <li>{@code EliteForgeAnnihilateWarningTicks} - ticks remaining in warning phase</li>
 *   <li>{@code EliteForgeAnnihilateTriggered} - true after annihilation has triggered</li>
 *   <li>{@code EliteForgeAnnihilateKillerUUID} - UUID of last attacking player (for damage reduction)</li>
 *   <li>{@code EliteForgeAnnihilateChainExplosion} - flag marking entities damaged by chain explosion (prevents recursive triggers)</li>
 * </ul>
 */
public class AbilityAnnihilate extends CreatorAbility {

    // NBT keys (reference centralized constants)
    private static final String ANNIHILATE_WARNING_KEY = NBTKeys.ANNIHILATE_WARNING;
    private static final String ANNIHILATE_WARNING_TICKS_KEY = NBTKeys.ANNIHILATE_WARNING_TICKS;
    private static final String ANNIHILATE_TRIGGERED_KEY = NBTKeys.ANNIHILATE_TRIGGERED;
    private static final String ANNIHILATE_KILLER_UUID_KEY = NBTKeys.ANNIHILATE_KILLER_UUID;
    private static final String CHAIN_EXPLOSION_KEY = NBTKeys.ANNIHILATE_CHAIN_EXPLOSION;
    private static final String CHAIN_EXPLOSION_EXPIRY_KEY = NBTKeys.ANNIHILATE_CHAIN_EXPLOSION_EXPIRY;

    /**
     * In-memory storage for Scorched Earth zones, keyed by dimension resource
     * location. Replaces the legacy {@code serverLevel.getPersistentData()}
     * access pattern that was removed in Forge 1.20.1. Each CompoundTag maps
     * zone UUIDs to zone data; per-tick countdowns handle expiration.
     */
    private static final java.util.concurrent.ConcurrentHashMap<
            net.minecraft.resources.ResourceLocation, CompoundTag> SCORCHED_ZONES =
            new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Public accessor for the Scorched Earth zone data of a specific dimension.
     * Used by {@code EliteEventHandler.tickScorchedEarthZones} which previously
     * read from {@code level.getPersistentData()}.
     */
    public static CompoundTag getScorchedZones(net.minecraft.resources.ResourceLocation dimensionKey) {
        return SCORCHED_ZONES.get(dimensionKey);
    }

    public AbilityAnnihilate() {
        super(new ResourceLocation("eliteforge", "creator_annihilate"), 6.0f);
    }

    @Override
    public void onApply(LivingEntity entity, int level) {
        if (entity.level().isClientSide) return;
        CompoundTag data = entity.getPersistentData();
        data.putBoolean(ANNIHILATE_WARNING_KEY, false);
        data.putInt(ANNIHILATE_WARNING_TICKS_KEY, 0);
        data.putBoolean(ANNIHILATE_TRIGGERED_KEY, false);

        // Mark as creator entity in capability
        setupCreatorData(entity, level);
    }

    @Override
    public void onRemove(LivingEntity entity, int level) {
        if (entity.level().isClientSide) return;

        // Clean up NBT data
        CompoundTag data = entity.getPersistentData();
        data.remove(ANNIHILATE_WARNING_KEY);
        data.remove(ANNIHILATE_WARNING_TICKS_KEY);
        data.remove(ANNIHILATE_TRIGGERED_KEY);
        data.remove(ANNIHILATE_KILLER_UUID_KEY);
        data.remove(CHAIN_EXPLOSION_KEY); // Safety cleanup in case entity was chain-damaged
        data.remove(CHAIN_EXPLOSION_EXPIRY_KEY);
    }

    @Override
    public void onTick(LivingEntity entity, int level) {
        if (entity.level().isClientSide) return;

        CompoundTag data = entity.getPersistentData();
        if (!data.contains(ANNIHILATE_WARNING_KEY)) {
            // Idempotency check: if capability data is already set, just re-initialize NBT
            // without re-calling onApply
            entity.getCapability(EliteCapability.CAPABILITY).ifPresent(cap -> {
                EliteData eliteData = cap.getEliteData();
                if (eliteData.isCreatorEntity() && getIdString().equals(eliteData.getCreatorAbilityId())) {
                    data.putBoolean(ANNIHILATE_WARNING_KEY, false);
                    data.putInt(ANNIHILATE_WARNING_TICKS_KEY, 0);
                    data.putBoolean(ANNIHILATE_TRIGGERED_KEY, false);
                    return;
                }
            });
            // If capability wasn't set or didn't match, do full onApply
            if (!data.contains(ANNIHILATE_WARNING_KEY)) {
                onApply(entity, level);
            }
            return;
        }

        boolean warning = data.getBoolean(ANNIHILATE_WARNING_KEY);
        boolean triggered = data.getBoolean(ANNIHILATE_TRIGGERED_KEY);

        if (triggered) return;

        // Check if we should start the warning phase (when health drops below 10%).
        // Guard: don't start the warning if this entity is a chain explosion target,
        // which prevents chain-damaged Annihilate entities from triggering their own
        // annihilation and causing unbounded chain reactions.
        //
        // The chain flag is paired with an expiry tick (set when the flag is written)
        // so a server restart inside the 5-tick cleanup window no longer leaves the
        // flag permanently set, permanently disabling Annihilate. We first purge any
        // expired flag directly from NBT (the scheduled TickTask is the primary cleanup
        // path, but this onTick fallback covers the server-restart edge case).
        if (data.getBoolean(CHAIN_EXPLOSION_KEY) && data.contains(CHAIN_EXPLOSION_EXPIRY_KEY)
                && entity.level().getGameTime() >= data.getLong(CHAIN_EXPLOSION_EXPIRY_KEY)) {
            data.remove(CHAIN_EXPLOSION_KEY);
            data.remove(CHAIN_EXPLOSION_EXPIRY_KEY);
        }
        boolean chainFlagActive = data.getBoolean(CHAIN_EXPLOSION_KEY)
                && (!data.contains(CHAIN_EXPLOSION_EXPIRY_KEY)
                    || entity.level().getGameTime() < data.getLong(CHAIN_EXPLOSION_EXPIRY_KEY));
        if (!warning && entity.getHealth() < entity.getMaxHealth() * 0.10f
                && !chainFlagActive) {
            int warningDuration = switch (level) {
                case 1 -> 60;   // 3 seconds
                case 2 -> 80;   // 4 seconds
                default -> 100; // 5 seconds
            };

            data.putBoolean(ANNIHILATE_WARNING_KEY, true);
            data.putInt(ANNIHILATE_WARNING_TICKS_KEY, warningDuration);

            // Warn nearby players
            // C1: Now uses translatable key "message.eliteforge.annihilate.warning"
            // with (countdownSeconds, entityName) as %d/%s parameters.
            if (entity.level() instanceof ServerLevel serverLevel) {
                double warnRange = 48.0;
                AABB area = new AABB(
                        entity.getX() - warnRange, entity.getY() - warnRange, entity.getZ() - warnRange,
                        entity.getX() + warnRange, entity.getY() + warnRange, entity.getZ() + warnRange
                );
                int countdownSeconds = warningDuration / 20;
                Component warningMsg = Component.translatable("message.eliteforge.annihilate.warning",
                                countdownSeconds, entity.getName().getString())
                        .withStyle(net.minecraft.ChatFormatting.DARK_RED, net.minecraft.ChatFormatting.BOLD);
                for (Player player : serverLevel.getEntitiesOfClass(Player.class, area)) {
                    player.sendSystemMessage(warningMsg);
                }
            }
        }

        // Warning phase - shake + particles
        if (warning) {
            int warningTicks = data.getInt(ANNIHILATE_WARNING_TICKS_KEY);
            warningTicks--;
            data.putInt(ANNIHILATE_WARNING_TICKS_KEY, warningTicks);

            if (entity.level() instanceof ServerLevel serverLevel) {
                // Entity shake effect (random position jitter)
                double shakeIntensity = 0.05 + (1.0 - (double) warningTicks / 100) * 0.1;
                entity.setDeltaMovement(entity.getDeltaMovement().add(
                        (entity.getRandom().nextDouble() - 0.5) * shakeIntensity,
                        0,
                        (entity.getRandom().nextDouble() - 0.5) * shakeIntensity
                ));

                // Red-black vortex particles
                int particleCount = 3 + (100 - warningTicks) / 20;
                serverLevel.sendParticles(ParticleTypes.DRAGON_BREATH,
                        entity.getX(), entity.getY() + entity.getBbHeight() * 0.5, entity.getZ(),
                        particleCount,
                        0.5, entity.getBbHeight() * 0.5, 0.5, 0.05);
                serverLevel.sendParticles(ParticleTypes.SMOKE,
                        entity.getX(), entity.getY() + entity.getBbHeight() * 0.5, entity.getZ(),
                        particleCount,
                        0.3, entity.getBbHeight() * 0.3, 0.3, 0.02);

                // Countdown sound substitute: more particles at each second mark
                if (warningTicks % 20 == 0 && warningTicks > 0) {
                    int remainingSeconds = warningTicks / 20;
                    serverLevel.sendParticles(ParticleTypes.EXPLOSION,
                            entity.getX(), entity.getY() + entity.getBbHeight() * 0.5, entity.getZ(),
                            2, 0.3, 0.3, 0.3, 0.01);
                }
            }

            if (warningTicks <= 0) {
                // Trigger annihilation
                data.putBoolean(ANNIHILATE_TRIGGERED_KEY, true);
                data.putBoolean(ANNIHILATE_WARNING_KEY, false); // B6: clean up warning flag
                triggerAnnihilation(entity, level);
                // B2: Design intent is "devastating DEATH explosion". After the
                // warning phase auto-triggers (entity below 10% HP), the entity
                // must die from its own annihilation. Without this, the entity
                // survives with 10% HP and `triggered=true` forever, which is
                // inconsistent with the onDeath path and the design doc.
                // H4 fix: use entity.kill() instead of setHealth(0)+MAX_VALUE hurt.
                // kill() is the vanilla-recommended way to force entity death — it
                // properly fires LivingDeathEvent and handles cleanup. The old
                // approach (setHealth(0) + Float.MAX_VALUE damage) was redundant
                // and could cause issues with mods that cap damage.
                entity.kill();
            }
        }

        // Level III: Scorched Earth zone maintenance
        if (triggered || (warning && level >= 3)) {
            // Scorched Earth is handled in the trigger
        }
    }

    @Override
    public void onHurt(LivingEntity entity, float damage, int level) {
        if (entity.level().isClientSide) return;

        // Track the last attacker for damage reduction
        if (entity.getLastHurtByMob() instanceof Player player) {
            entity.getPersistentData().putUUID(ANNIHILATE_KILLER_UUID_KEY, player.getUUID());
        }
    }

    @Override
    public void onDeath(LivingEntity entity, int level) {
        if (entity.level().isClientSide) return;

        // If not already triggered by health threshold, trigger on death
        CompoundTag data = entity.getPersistentData();
        boolean triggered = data.getBoolean(ANNIHILATE_TRIGGERED_KEY);

        if (!triggered) {
            triggerAnnihilation(entity, level);
        }
    }

    /**
     * Triggers the devastating annihilation explosion.
     */
    private void triggerAnnihilation(LivingEntity entity, int level) {
        if (!(entity.level() instanceof ServerLevel serverLevel)) return;

        // Prevent chain explosion recursion
        if (entity.getPersistentData().getBoolean(CHAIN_EXPLOSION_KEY)) {
            return;
        }

        double radius = switch (level) {
            case 1 -> 8.0;
            case 2 -> 12.0;
            default -> 16.0;
        };
        float baseDamage = switch (level) {
            case 1 -> 30.0f;
            case 2 -> 50.0f;
            default -> 80.0f;
        };

        // Get the killer UUID for damage reduction (final so it can be captured by lambdas below)
        CompoundTag data = entity.getPersistentData();
        final java.util.UUID killerUUID = data.hasUUID(ANNIHILATE_KILLER_UUID_KEY)
                ? data.getUUID(ANNIHILATE_KILLER_UUID_KEY)
                : null;

        AABB area = new AABB(
                entity.getX() - radius, entity.getY() - radius, entity.getZ() - radius,
                entity.getX() + radius, entity.getY() + radius, entity.getZ() + radius
        );

        // Damage all entities in range
        List<LivingEntity> targets = serverLevel.getEntitiesOfClass(LivingEntity.class, area,
                e -> e.isAlive() && e != entity);

        for (LivingEntity target : targets) {
            float damage = baseDamage;

            // Player who killed the creator gets 50% damage reduction
            if (target instanceof Player player && killerUUID != null && player.getUUID().equals(killerUUID)) {
                damage *= 0.5f;
            }

            // Distance-based falloff
            double distance = target.distanceTo(entity);
            double falloff = 1.0 - (distance / radius) * 0.5; // 50% damage at edge
            damage *= falloff;

            target.hurt(entity.damageSources().magic(), damage);
        }

        // Level II: Chain explosion - nearby elites also explode at 50% damage
        if (level >= 2) {
            List<LivingEntity> nearbyElites = serverLevel.getEntitiesOfClass(LivingEntity.class, area,
                    e -> e.isAlive() && e != entity && e.getCapability(EliteCapability.CAPABILITY).isPresent());

            for (LivingEntity elite : nearbyElites) {
                elite.getCapability(EliteCapability.CAPABILITY).ifPresent(cap -> {
                    EliteData eliteData = cap.getEliteData();
                    if (eliteData.isElite()) {
                        // Mark this elite with the chain explosion flag so that if it also
                        // has the Annihilate ability and drops below 10% health from the
                        // chain damage, it won't start its own warning phase and cause
                        // unbounded chain reactions.
                        elite.getPersistentData().putBoolean(CHAIN_EXPLOSION_KEY, true);
                        // Pair the flag with an expiry tick so a server restart inside
                        // the 5-tick cleanup window no longer leaves it permanently set.
                        elite.getPersistentData().putLong(CHAIN_EXPLOSION_EXPIRY_KEY,
                                elite.level().getGameTime() + 5);
                        // Schedule cleanup of the chain flag after 5 ticks (0.25s)
                        // Guard: check entity validity before accessing NBT, as the
                        // scheduled task may execute after the entity is removed under heavy load.
                        if (elite.level() instanceof ServerLevel eliteLevel) {
                            eliteLevel.getServer().tell(new net.minecraft.server.TickTask(
                                    eliteLevel.getServer().getTickCount() + 5, () -> {
                                        if (elite.isAlive() && !elite.isRemoved()) {
                                            elite.getPersistentData().remove(CHAIN_EXPLOSION_KEY);
                                            elite.getPersistentData().remove(CHAIN_EXPLOSION_EXPIRY_KEY);
                                        }
                                    }));
                        }

                        // Chain explosion at 50% damage in smaller radius
                        double chainRadius = radius * 0.5;
                        float chainDamage = baseDamage * 0.5f;

                        AABB chainArea = new AABB(
                                elite.getX() - chainRadius, elite.getY() - chainRadius, elite.getZ() - chainRadius,
                                elite.getX() + chainRadius, elite.getY() + chainRadius, elite.getZ() + chainRadius
                        );

                        List<LivingEntity> chainTargets = serverLevel.getEntitiesOfClass(LivingEntity.class, chainArea,
                                e -> e.isAlive() && e != elite && e != entity);

                        for (LivingEntity chainTarget : chainTargets) {
                            // Mark target as chain-damaged to prevent recursive explosions.
                            // Pair the flag with an expiry tick so a server restart inside
                            // the 1-tick cleanup window no longer leaves it permanently set.
                            chainTarget.getPersistentData().putBoolean(CHAIN_EXPLOSION_KEY, true);
                            chainTarget.getPersistentData().putLong(CHAIN_EXPLOSION_EXPIRY_KEY,
                                    chainTarget.level().getGameTime() + 1);

                            float targetDamage = chainDamage;

                            // Player who killed the creator gets 50% damage reduction
                            if (chainTarget instanceof Player player && killerUUID != null && player.getUUID().equals(killerUUID)) {
                                targetDamage *= 0.5f;
                            }

                            // Distance-based falloff
                            double chainDistance = chainTarget.distanceTo(elite);
                            double chainFalloff = 1.0 - (chainDistance / chainRadius) * 0.5;
                            targetDamage *= chainFalloff;

                            chainTarget.hurt(entity.damageSources().magic(), targetDamage);

                            // Clear the chain explosion flag 1 tick later
                            // Guard: check entity validity before accessing NBT, as the
                            // scheduled task may execute after the entity is removed under heavy load.
                            if (chainTarget.level() instanceof ServerLevel targetLevel) {
                                final LivingEntity finalChainTarget = chainTarget;
                                targetLevel.getServer().tell(new net.minecraft.server.TickTask(targetLevel.getServer().getTickCount() + 1, () -> {
                                    if (finalChainTarget.isAlive() && !finalChainTarget.isRemoved()) {
                                        finalChainTarget.getPersistentData().remove(CHAIN_EXPLOSION_KEY);
                                        finalChainTarget.getPersistentData().remove(CHAIN_EXPLOSION_EXPIRY_KEY);
                                    }
                                }));
                            }
                        }

                        // Chain explosion particles
                        serverLevel.sendParticles(ParticleTypes.EXPLOSION,
                                elite.getX(), elite.getY() + elite.getBbHeight() * 0.5, elite.getZ(),
                                3, chainRadius * 0.3, elite.getBbHeight() * 0.3, chainRadius * 0.3, 0.1);
                    }
                });
            }
        }

        // Level III: Scorched Earth zone - mark area for fire damage
        if (level >= 3) {
            // NOTE: The Scorched Earth zone data written below is processed by
            // EliteEventHandler.tickScorchedEarthZones(), which handles the per-tick
            // fire damage and zone expiration each tick.
            //
            // In 1.20.1, ServerLevel no longer exposes getPersistentData() the way
            // older Forge versions did; we store the zone map in a static in-memory
            // ConcurrentHashMap instead. Zones expire naturally via the per-tick
            // countdown, so persistence across server restarts is not required.

            CompoundTag levelData = SCORCHED_ZONES.computeIfAbsent(
                    serverLevel.dimension().location(), k -> new CompoundTag());

            String zoneKey = entity.getUUID().toString();
            CompoundTag zoneData = new CompoundTag();
            zoneData.putDouble("x", entity.getX());
            zoneData.putDouble("y", entity.getY());
            zoneData.putDouble("z", entity.getZ());
            zoneData.putDouble("radius", radius);
            zoneData.putString("dimension", serverLevel.dimension().location().toString());
            int maxTicks = EliteForgeConfig.SERVER.scorchedEarthMaxTicks.get();
            zoneData.putInt("ticksRemaining", maxTicks); // Configurable zone duration
            zoneData.putFloat("damagePerSecond", 5.0f);
            zoneData.putUUID("creatorUUID", entity.getUUID()); // Store creator UUID for allied-elite exclusion
            levelData.put(zoneKey, zoneData);
        }

        // Teleport dropped items to safe position (away from explosion center)
        List<ItemEntity> droppedItems = serverLevel.getEntitiesOfClass(ItemEntity.class, area);
        for (ItemEntity item : droppedItems) {
            Vec3 safePos = findSafePosition(serverLevel, entity.blockPosition(), (int) radius + 5);
            item.setPos(safePos.x, safePos.y, safePos.z);
        }

        // Massive explosion particles (no block destruction)
        serverLevel.sendParticles(ParticleTypes.EXPLOSION_EMITTER,
                entity.getX(), entity.getY() + entity.getBbHeight() * 0.5, entity.getZ(),
                3 + level, radius * 0.3, entity.getBbHeight() * 0.3, radius * 0.3, 0.1);
        serverLevel.sendParticles(ParticleTypes.FLAME,
                entity.getX(), entity.getY() + entity.getBbHeight() * 0.5, entity.getZ(),
                50 + level * 20, radius, entity.getBbHeight() * 0.5, radius, 0.3);
        serverLevel.sendParticles(ParticleTypes.LAVA,
                entity.getX(), entity.getY() + entity.getBbHeight() * 0.5, entity.getZ(),
                30 + level * 10, radius * 0.5, entity.getBbHeight() * 0.3, radius * 0.5, 0.1);
        serverLevel.sendParticles(ParticleTypes.SMOKE,
                entity.getX(), entity.getY() + entity.getBbHeight() * 0.5, entity.getZ(),
                40 + level * 15, radius * 0.8, entity.getBbHeight() * 0.5, radius * 0.8, 0.05);

        // Notify players
        // C1: Now uses translatable key "message.eliteforge.annihilate.trigger"
        AABB notifyArea = new AABB(
                entity.getX() - 48, entity.getY() - 48, entity.getZ() - 48,
                entity.getX() + 48, entity.getY() + 48, entity.getZ() + 48
        );
        Component annihilateMsg = Component.translatable("message.eliteforge.annihilate.trigger")
                .withStyle(net.minecraft.ChatFormatting.DARK_RED, net.minecraft.ChatFormatting.BOLD);
        for (Player player : serverLevel.getEntitiesOfClass(Player.class, notifyArea)) {
            player.sendSystemMessage(annihilateMsg);
        }
    }

    /**
     * Finds a safe position away from the explosion center.
     * Simplified: just picks a position at the edge of the radius.
     */
    private Vec3 findSafePosition(ServerLevel serverLevel, net.minecraft.core.BlockPos center, int minDistance) {
        double angle = serverLevel.random.nextDouble() * Math.PI * 2;
        double dist = minDistance + serverLevel.random.nextDouble() * 5;
        double x = center.getX() + Math.cos(angle) * dist;
        double z = center.getZ() + Math.sin(angle) * dist;
        double y = center.getY(); // Simplified: use same Y
        return new Vec3(x, y, z);
    }
}
