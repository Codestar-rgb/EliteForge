package com.eliteforge.ability.creator;

import com.eliteforge.capability.EliteCapability;
import com.eliteforge.capability.EliteData;
import com.eliteforge.capability.EliteCapabilitySync;
import com.eliteforge.util.NBTKeys;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.List;
import java.util.UUID;

/**
 * AbilityDominion (域界·支配) - C2 Creator Ability
 * <p>
 * Creates a territory zone around the entity.
 * Level I:  radius 20, 30s duration, 120s cooldown: friendly regen I, player dig speed -30%
 * Level II: radius 30, 45s duration, 90s cooldown: I + friendly damage +25%, player speed -15%
 * Level III:radius 40, 60s duration, 60s cooldown: II + friendly knockback immune, player can't place blocks
 * Dark red circle on ground as boundary, vignette effect for players inside.
 * Players entering get warning message.
 * 20% of mobs spawning inside become elite.
 * <p>
 * NBT keys:
 * <ul>
 *   <li>{@code EliteForgeDominionActive} - true while dominion zone is active</li>
 *   <li>{@code EliteForgeDominionTimer} - ticks remaining in active dominion phase</li>
 *   <li>{@code EliteForgeDominionCooldown} - ticks remaining in cooldown phase</li>
 *   <li>{@code EliteForgeDominionNoPlace} - flag on players inside Level III dominion (prevents block placement)</li>
 * </ul>
 */
public class AbilityDominion extends CreatorAbility {

    // UUIDs for dominion attribute modifiers
    private static final UUID DOMINION_DAMAGE_UUID = UUID.fromString("d4e5f6a7-b8c9-0123-4567-89abcdef0123");
    private static final UUID DOMINION_KNOCKBACK_UUID = UUID.fromString("e5f6a7b8-c9d0-1234-5678-9abcdef01234");

    // NBT keys (reference centralized constants)
    private static final String DOMINION_ACTIVE_KEY = NBTKeys.DOMINION_ACTIVE;
    private static final String DOMINION_TIMER_KEY = NBTKeys.DOMINION_TIMER;
    private static final String DOMINION_COOLDOWN_KEY = NBTKeys.DOMINION_COOLDOWN;
    private static final String DOMINION_NO_PLACE_KEY = NBTKeys.DOMINION_NO_PLACE;

    public AbilityDominion() {
        super(new ResourceLocation("eliteforge", "creator_dominion"), 5.5f);
    }

    @Override
    public void onApply(LivingEntity entity, int level) {
        if (entity.level().isClientSide) return;
        CompoundTag data = entity.getPersistentData();
        data.putBoolean(DOMINION_ACTIVE_KEY, false);
        data.putInt(DOMINION_TIMER_KEY, 0);
        data.putInt(DOMINION_COOLDOWN_KEY, 0);

        // Mark as creator entity in capability
        setupCreatorData(entity, level);
    }

    @Override
    public void onRemove(LivingEntity entity, int level) {
        if (entity.level().isClientSide) return;

        // Clean up NBT data
        CompoundTag data = entity.getPersistentData();
        data.remove(DOMINION_ACTIVE_KEY);
        data.remove(DOMINION_TIMER_KEY);
        data.remove(DOMINION_COOLDOWN_KEY);

        // Clear "no place" flag from nearby players
        if (entity.level() instanceof ServerLevel serverLevel) {
            double radius = 45.0; // Slightly larger than max dominion radius
            double minY = Math.max(serverLevel.getMinBuildHeight(), entity.getY() - radius);
            AABB area = new AABB(
                    entity.getX() - radius, minY, entity.getZ() - radius,
                    entity.getX() + radius, entity.getY() + radius, entity.getZ() + radius
            );
            for (net.minecraft.world.entity.player.Player player : serverLevel.getEntitiesOfClass(net.minecraft.world.entity.player.Player.class, area)) {
                player.getPersistentData().remove(DOMINION_NO_PLACE_KEY);
            }

            // Clear dominion attribute modifiers from nearby elites
            clearDominionModifiers(entity, serverLevel, radius);
        }
    }

    @Override
    public void onTick(LivingEntity entity, int level) {
        if (entity.level().isClientSide) return;

        CompoundTag data = entity.getPersistentData();
        if (!data.contains(DOMINION_ACTIVE_KEY)) {
            // Idempotency check: if capability data is already set, just re-initialize NBT
            // without re-calling onApply
            entity.getCapability(EliteCapability.CAPABILITY).ifPresent(cap -> {
                EliteData eliteData = cap.getEliteData();
                if (eliteData.isCreatorEntity() && getIdString().equals(eliteData.getCreatorAbilityId())) {
                    data.putBoolean(DOMINION_ACTIVE_KEY, false);
                    data.putInt(DOMINION_TIMER_KEY, 0);
                    data.putInt(DOMINION_COOLDOWN_KEY, 0);
                    return;
                }
            });
            // If capability wasn't set or didn't match, do full onApply
            if (!data.contains(DOMINION_ACTIVE_KEY)) {
                onApply(entity, level);
            }
            return;
        }

        boolean active = data.getBoolean(DOMINION_ACTIVE_KEY);
        int timer = data.getInt(DOMINION_TIMER_KEY);
        int cooldown = data.getInt(DOMINION_COOLDOWN_KEY);

        double radius = switch (level) {
            case 1 -> 20.0;
            case 2 -> 30.0;
            default -> 40.0;
        };
        int durationTicks = switch (level) {
            case 1 -> 600;  // 30s
            case 2 -> 900;  // 45s
            default -> 1200; // 60s
        };
        int cooldownTicks = switch (level) {
            case 1 -> 2400; // 120s
            case 2 -> 1800; // 90s
            default -> 1200; // 60s
        };

        if (active) {
            timer--;
            data.putInt(DOMINION_TIMER_KEY, timer);

            if (!(entity.level() instanceof ServerLevel serverLevel)) return;

            // Apply dominion effects while active
            applyDominionEffects(entity, serverLevel, level, radius);

            // Boundary particles
            if (entity.tickCount % 10 == 0) {
                spawnBoundaryParticles(serverLevel, entity, radius);
            }

            if (timer <= 0) {
                // Dominion ends
                data.putBoolean(DOMINION_ACTIVE_KEY, false);
                data.putInt(DOMINION_COOLDOWN_KEY, cooldownTicks);

                // Clear NoPlace flag for nearby players since this dominion is ending
                clearNoPlaceFlags(entity, serverLevel, radius);

                // Clear dominion attribute modifiers from nearby elites
                clearDominionModifiers(entity, serverLevel, radius);

                // Notify nearby players
                // C1: Now uses translatable key "message.eliteforge.dominion.expire"
                notifyNearbyPlayers(entity, serverLevel, radius,
                        Component.translatable("message.eliteforge.dominion.expire")
                                .withStyle(net.minecraft.ChatFormatting.GRAY));
            }
        } else {
            // Not active (cooldown phase), count down cooldown
            if (cooldown > 0) {
                cooldown--;
                data.putInt(DOMINION_COOLDOWN_KEY, cooldown);
            }

            // Clean up NoPlace flags from players who still have it while dominion is inactive.
            // This ensures flags are cleared promptly even if the global tickCreatorNbtTimers
            // check hasn't run yet.
            if (entity.level() instanceof ServerLevel serverLevel && entity.tickCount % 40 == 0) {
                clearNoPlaceFlags(entity, serverLevel, radius + 5);
            }

            // Activate if cooldown is done and entity is in combat
            if (cooldown <= 0 && (entity.getLastHurtByMob() != null || (entity instanceof net.minecraft.world.entity.Mob mob && mob.getTarget() != null))) {
                data.putBoolean(DOMINION_ACTIVE_KEY, true);
                data.putInt(DOMINION_TIMER_KEY, durationTicks);

                if (entity.level() instanceof ServerLevel serverLevel) {
                    // Notify players of dominion activation
                    // C1: Now uses translatable key "message.eliteforge.dominion.activate"
                    Component warning = Component.translatable("message.eliteforge.dominion.activate")
                            .withStyle(net.minecraft.ChatFormatting.DARK_RED, net.minecraft.ChatFormatting.BOLD);
                    notifyNearbyPlayers(entity, serverLevel, radius + 10, warning);
                }
            }
        }
    }

    @Override
    public void onDeath(LivingEntity entity, int level) {
        if (entity.level().isClientSide) return;
        CompoundTag data = entity.getPersistentData();
        data.putBoolean(DOMINION_ACTIVE_KEY, false);
        data.putInt(DOMINION_TIMER_KEY, 0);

        // Clear NoPlace flags for all nearby players since this dominion is gone
        if (entity.level() instanceof ServerLevel serverLevel) {
            double radius = switch (level) {
                case 1 -> 20.0;
                case 2 -> 30.0;
                default -> 40.0;
            };
            clearNoPlaceFlags(entity, serverLevel, radius + 10);
            clearDominionModifiers(entity, serverLevel, radius + 10);
        }
    }

    /**
     * Apply dominion effects: buffs to friendly elites, debuffs to players.
     */
    private void applyDominionEffects(LivingEntity entity, ServerLevel serverLevel, int level, double radius) {
        // Clamp lower Y bound to world min build height to avoid searching below the world
        double minY = Math.max(serverLevel.getMinBuildHeight(), entity.getY() - radius);
        AABB area = new AABB(
                entity.getX() - radius, minY, entity.getZ() - radius,
                entity.getX() + radius, entity.getY() + radius, entity.getZ() + radius
        );

        // Buff friendly elites
        List<LivingEntity> nearbyMobs = serverLevel.getEntitiesOfClass(LivingEntity.class, area,
                e -> e.isAlive() && e != entity && e.getCapability(EliteCapability.CAPABILITY).isPresent());

        for (LivingEntity mob : nearbyMobs) {
            mob.getCapability(EliteCapability.CAPABILITY).ifPresent(cap -> {
                EliteData eliteData = cap.getEliteData();
                if (eliteData.isElite()) {
                    // Level I+: Regeneration I
                    if (!mob.hasEffect(MobEffects.REGENERATION) || mob.getEffect(MobEffects.REGENERATION).getDuration() < 40) {
                        mob.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 60, 0, false, true));
                    }

                    // Level II+: Damage +25% (as attribute modifier, not flat effect)
                    if (level >= 2) {
                        var damageAttr = mob.getAttribute(Attributes.ATTACK_DAMAGE);
                        if (damageAttr != null) {
                            if (damageAttr.getModifier(DOMINION_DAMAGE_UUID) == null) {
                                damageAttr.addTransientModifier(new AttributeModifier(
                                        DOMINION_DAMAGE_UUID,
                                        "EliteForge Dominion Friendly Damage",
                                        0.25,
                                        AttributeModifier.Operation.MULTIPLY_BASE
                                ));
                            }
                        }
                    }

                    // Level III: Knockback immune (full knockback resistance)
                    if (level >= 3) {
                        var kbAttr = mob.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
                        if (kbAttr != null) {
                            if (kbAttr.getModifier(DOMINION_KNOCKBACK_UUID) == null) {
                                kbAttr.addTransientModifier(new AttributeModifier(
                                        DOMINION_KNOCKBACK_UUID,
                                        "EliteForge Dominion Knockback Immune",
                                        1.0,
                                        AttributeModifier.Operation.ADDITION
                                ));
                            }
                        }
                    }
                }
            });
        }

        // Debuff players
        List<Player> nearbyPlayers = serverLevel.getEntitiesOfClass(Player.class, area,
                p -> p.isAlive() && !p.isSpectator() && !p.isCreative());

        for (Player player : nearbyPlayers) {
            // Level I: Mining fatigue (dig speed -30%)
            if (level >= 1) {
                if (!player.hasEffect(MobEffects.DIG_SLOWDOWN) || player.getEffect(MobEffects.DIG_SLOWDOWN).getDuration() < 40) {
                    player.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 60, 0, false, true));
                }
            }

            // Level II: Slowness (speed -15%)
            if (level >= 2) {
                if (!player.hasEffect(MobEffects.MOVEMENT_SLOWDOWN) || player.getEffect(MobEffects.MOVEMENT_SLOWDOWN).getDuration() < 40) {
                    player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 0, false, true));
                }
            }

            // Level III: Can't place blocks (weakness + fatigue as proxy, actual block prevention
            // would require an event handler check - we mark with NBT for external checks)
            if (level >= 3) {
                if (!player.hasEffect(MobEffects.WEAKNESS) || player.getEffect(MobEffects.WEAKNESS).getDuration() < 40) {
                    player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 1, false, true));
                }
                player.getPersistentData().putBoolean(DOMINION_NO_PLACE_KEY, true);
            }
        }

        // Clear no-place flag for players outside the domain
        double outerMinY = Math.max(serverLevel.getMinBuildHeight(), entity.getY() - radius - 5);
        AABB outerArea = new AABB(
                entity.getX() - radius - 5, outerMinY, entity.getZ() - radius - 5,
                entity.getX() + radius + 5, entity.getY() + radius + 5, entity.getZ() + radius + 5
        );
        for (Player outsidePlayer : serverLevel.getEntitiesOfClass(Player.class, outerArea)) {
            if (!area.contains(outsidePlayer.getX(), outsidePlayer.getY(), outsidePlayer.getZ())) {
                outsidePlayer.getPersistentData().remove(DOMINION_NO_PLACE_KEY);
            }
        }
    }

    /**
     * Spawns dark red circle particles on the ground as domain boundary.
     */
    private void spawnBoundaryParticles(ServerLevel serverLevel, LivingEntity entity, double radius) {
        int points = 24;
        for (int i = 0; i < points; i++) {
            double angle = (Math.PI * 2 * i) / points;
            double px = entity.getX() + Math.cos(angle) * radius;
            double pz = entity.getZ() + Math.sin(angle) * radius;
            serverLevel.sendParticles(ParticleTypes.DRAGON_BREATH,
                    px, entity.getY() + 0.1, pz, 2,
                    0.1, 0.1, 0.1, 0.01);
        }
        // Also spawn some interior particles
        for (int i = 0; i < 3; i++) {
            double angle = entity.getRandom().nextDouble() * Math.PI * 2;
            double r = entity.getRandom().nextDouble() * radius * 0.8;
            double px = entity.getX() + Math.cos(angle) * r;
            double pz = entity.getZ() + Math.sin(angle) * r;
            serverLevel.sendParticles(ParticleTypes.SMOKE,
                    px, entity.getY() + 0.5, pz, 1,
                    0, 0.05, 0, 0);
        }
    }

    /**
     * Clear the EliteForgeDominionNoPlace flag from all nearby players.
     * Called when the dominion ends, during cooldown phase, or on creator death
     * to ensure the block-placement prevention flag is always cleaned up.
     *
     * @param entity the dominion creator entity
     * @param serverLevel the server level
     * @param range the range to search for players
     */
    private void clearNoPlaceFlags(LivingEntity entity, ServerLevel serverLevel, double range) {
        double minY = Math.max(serverLevel.getMinBuildHeight(), entity.getY() - range);
        AABB area = new AABB(
                entity.getX() - range, minY, entity.getZ() - range,
                entity.getX() + range, entity.getY() + range, entity.getZ() + range
        );
        for (Player player : serverLevel.getEntitiesOfClass(Player.class, area, Player::isAlive)) {
            if (player.getPersistentData().getBoolean(DOMINION_NO_PLACE_KEY)) {
                player.getPersistentData().remove(DOMINION_NO_PLACE_KEY);
            }
        }
    }

    /**
     * Clears dominion attribute modifiers (damage boost, knockback resistance) from nearby elites.
     * Called when the dominion ends, the ability is removed, or the entity dies.
     */
    private void clearDominionModifiers(LivingEntity entity, ServerLevel serverLevel, double range) {
        double minY = Math.max(serverLevel.getMinBuildHeight(), entity.getY() - range);
        AABB modifierArea = new AABB(
                entity.getX() - range, minY, entity.getZ() - range,
                entity.getX() + range, entity.getY() + range, entity.getZ() + range
        );
        List<LivingEntity> nearbyMobs = serverLevel.getEntitiesOfClass(LivingEntity.class, modifierArea,
                e -> e.isAlive() && e != entity && e.getCapability(EliteCapability.CAPABILITY).isPresent());
        for (LivingEntity mob : nearbyMobs) {
            var damageAttr = mob.getAttribute(Attributes.ATTACK_DAMAGE);
            if (damageAttr != null) {
                damageAttr.removeModifier(DOMINION_DAMAGE_UUID);
            }
            var kbAttr = mob.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
            if (kbAttr != null) {
                kbAttr.removeModifier(DOMINION_KNOCKBACK_UUID);
            }
        }
    }

    /**
     * Sends a chat message to all players within range.
     */
    private void notifyNearbyPlayers(LivingEntity entity, ServerLevel serverLevel, double range, Component message) {
        double minY = Math.max(serverLevel.getMinBuildHeight(), entity.getY() - range);
        AABB area = new AABB(
                entity.getX() - range, minY, entity.getZ() - range,
                entity.getX() + range, entity.getY() + range, entity.getZ() + range
        );
        for (Player player : serverLevel.getEntitiesOfClass(Player.class, area)) {
            player.sendSystemMessage(message);
        }
    }
}
