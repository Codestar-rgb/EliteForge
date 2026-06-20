package com.eliteforge.ability.creator;

import com.eliteforge.ability.Ability;
import com.eliteforge.ability.AbilityRegistry;
import com.eliteforge.capability.EliteCapability;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.eliteforge.capability.EliteData;
import com.eliteforge.capability.EliteCapabilitySync;
import com.eliteforge.util.NBTKeys;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;

import java.util.*;

/**
 * AbilityReincarnation (轮回·不灭) - C7 Creator Ability
 * <p>
 * Reborn stronger after death.
 * Level I:  1 rebirth, +30% health +15% damage, 3s delay, 50% max health on rebirth
 * Level II: 2 rebirths, +50% health +25% damage + 1 random legendary I ability, 2s delay, 60% max health
 * Level III:3 rebirths, +80% health +40% damage + 1 random legendary ability (level increases each rebirth),
 *           1s delay, 75% max health
 * Soul particles scatter + reassemble animation.
 * Announces to nearby players on each rebirth.
 * Drops "Reincarnation Crystal" on final death.
 * Each rebirth gives full kill loot.
 * Second way to break exclusivity rule (gains legendary abilities on rebirth).
 * <p>
 * NBT keys:
 * <ul>
 *   <li>{@code EliteForgeReincarnationRemaining} - remaining rebirth count</li>
 *   <li>{@code EliteForgeReincarnationCount} - number of completed rebirths</li>
 *   <li>{@code EliteForgeReincarnationReviving} - true while revival animation is playing</li>
 *   <li>{@code EliteForgeReincarnationReviveTimer} - ticks remaining in revival delay</li>
 *   <li>{@code EliteForgeReincarnationStoredLevel} - stored ability level for revival</li>
 *   <li>{@code EliteForgeReincarnationInvuln} - invulnerability timer (managed by EliteEventHandler)</li>
 * </ul>
 */
public class AbilityReincarnation extends CreatorAbility {

    private static final Logger LOGGER = LogManager.getLogger();

    // Attribute modifier UUIDs
    private static final UUID HEALTH_MODIFIER_UUID = UUID.fromString("d7e8f9a0-b1c2-3456-7890-cdef45678901");
    private static final UUID DAMAGE_MODIFIER_UUID = UUID.fromString("e8f9a0b1-c2d3-4567-8901-defa56789012");

    // NBT keys (reference centralized constants)
    private static final String REINCARNATION_REMAINING_KEY = NBTKeys.REINCARNATION_REMAINING;
    private static final String REINCARNATION_COUNT_KEY = NBTKeys.REINCARNATION_COUNT;
    private static final String REINCARNATION_REVIVING_KEY = NBTKeys.REINCARNATION_REVIVING;
    private static final String REINCARNATION_REVIVE_TIMER_KEY = NBTKeys.REINCARNATION_REVIVE_TIMER;
    private static final String REINCARNATION_STORED_LEVEL_KEY = NBTKeys.REINCARNATION_STORED_LEVEL;

    public AbilityReincarnation() {
        super(new ResourceLocation("eliteforge", "creator_reincarnation"), 6.5f);
    }

    @Override
    public void onApply(LivingEntity entity, int level) {
        if (entity.level().isClientSide) return;
        CompoundTag data = entity.getPersistentData();

        int maxRebirths = switch (level) {
            case 1 -> 1;
            case 2 -> 2;
            default -> 3;
        };
        data.putInt(REINCARNATION_REMAINING_KEY, maxRebirths);
        data.putInt(REINCARNATION_COUNT_KEY, 0);
        data.putBoolean(REINCARNATION_REVIVING_KEY, false);
        data.putInt(REINCARNATION_REVIVE_TIMER_KEY, 0);
        data.putInt(REINCARNATION_STORED_LEVEL_KEY, level);

        // Mark as creator entity in capability
        setupCreatorData(entity, level);

        // Set reincarnation-specific data
        entity.getCapability(EliteCapability.CAPABILITY).ifPresent(cap -> {
            EliteData eliteData = cap.getEliteData();
            eliteData.setReincarnationRemaining(maxRebirths);
            cap.setEliteData(eliteData);
            EliteCapabilitySync.broadcastEliteDataUpdate(entity, eliteData);
        });
    }

    @Override
    public void onRemove(LivingEntity entity, int level) {
        if (entity.level().isClientSide) return;

        // Remove health and damage attribute modifiers from rebirth bonuses
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
        data.remove(REINCARNATION_REMAINING_KEY);
        data.remove(REINCARNATION_COUNT_KEY);
        data.remove(REINCARNATION_REVIVING_KEY);
        data.remove(REINCARNATION_REVIVE_TIMER_KEY);
        data.remove(REINCARNATION_STORED_LEVEL_KEY);
    }

    @Override
    public void onTick(LivingEntity entity, int level) {
        if (entity.level().isClientSide) return;

        CompoundTag data = entity.getPersistentData();
        if (!data.contains(REINCARNATION_REMAINING_KEY)) {
            // Idempotency check: if capability data is already set, just re-initialize NBT
            // without re-calling onApply (which would re-apply attribute modifiers)
            entity.getCapability(EliteCapability.CAPABILITY).ifPresent(cap -> {
                EliteData eliteData = cap.getEliteData();
                if (eliteData.isCreatorEntity() && getIdString().equals(eliteData.getCreatorAbilityId())) {
                    int maxRebirths = switch (level) {
                        case 1 -> 1;
                        case 2 -> 2;
                        default -> 3;
                    };
                    data.putInt(REINCARNATION_REMAINING_KEY, maxRebirths);
                    data.putInt(REINCARNATION_COUNT_KEY, 0);
                    data.putBoolean(REINCARNATION_REVIVING_KEY, false);
                    data.putInt(REINCARNATION_REVIVE_TIMER_KEY, 0);
                    data.putInt(REINCARNATION_STORED_LEVEL_KEY, level);
                    return;
                }
            });
            // If capability wasn't set or didn't match, do full onApply
            if (!data.contains(REINCARNATION_REMAINING_KEY)) {
                onApply(entity, level);
            }
            return;
        }

        boolean reviving = data.getBoolean(REINCARNATION_REVIVING_KEY);
        if (!reviving) return;

        // Defensive: only decrement timer if entity is still alive.
        // If the entity somehow dies again during revival (shouldn't happen due to
        // invulnerability, but defensive coding), we don't want the timer to continue.
        if (!entity.isAlive()) {
            data.putBoolean(REINCARNATION_REVIVING_KEY, false);
            return;
        }

        // Null-check for level cast — ensures we're on a valid server level
        if (!(entity.level() instanceof ServerLevel serverLevel)) return;

        int timer = data.getInt(REINCARNATION_REVIVE_TIMER_KEY);
        timer--;
        data.putInt(REINCARNATION_REVIVE_TIMER_KEY, timer);

        // Soul particles scatter animation during revival
        if (timer > 0) {
            // Scatter soul particles outward
            int scatterCount = 3 + (level * 2);
            for (int i = 0; i < scatterCount; i++) {
                double angle = entity.getRandom().nextDouble() * Math.PI * 2;
                double radius = 1.0 + entity.getRandom().nextDouble() * 2.0;
                double px = entity.getX() + Math.cos(angle) * radius;
                double pz = entity.getZ() + Math.sin(angle) * radius;
                serverLevel.sendParticles(ParticleTypes.SOUL,
                        px, entity.getY() + entity.getRandom().nextDouble() * entity.getBbHeight(), pz,
                        1, 0.1, 0.2, 0.1, 0.02);
            }
        }

        if (timer <= 0) {
            // Complete the revival
            data.putBoolean(REINCARNATION_REVIVING_KEY, false);
            performRebirth(entity, level, data, serverLevel);
        }
    }

    @Override
    public void onDeath(LivingEntity entity, int level) {
        if (entity.level().isClientSide) return;

        CompoundTag data = entity.getPersistentData();
        if (!data.contains(REINCARNATION_REMAINING_KEY)) return;

        int remaining = data.getInt(REINCARNATION_REMAINING_KEY);

        if (remaining > 0) {
            // Death prevention is now handled directly by EliteEventHandler,
            // which checks the remaining rebirth count from NBT before dispatching
            // onDeath to other abilities. This onDeath method is called by the
            // event handler AFTER it has already canceled the death event.
            // The revival timer setup is also handled by the event handler.
            // Here we only handle the soul scatter particle effect.
            if (entity.level() instanceof ServerLevel serverLevel) {
                // Soul particles scatter animation
                for (int i = 0; i < 20 + level * 5; i++) {
                    double angle = entity.getRandom().nextDouble() * Math.PI * 2;
                    double radius = 1.0 + entity.getRandom().nextDouble() * 2.5;
                    double px = entity.getX() + Math.cos(angle) * radius;
                    double pz = entity.getZ() + Math.sin(angle) * radius;
                    serverLevel.sendParticles(ParticleTypes.SOUL,
                            px, entity.getY() + entity.getRandom().nextDouble() * entity.getBbHeight(), pz,
                            1, 0.1, 0.2, 0.1, 0.02);
                }
            }
        } else {
            // Final death - drop Reincarnation Crystal
            if (entity.level() instanceof ServerLevel serverLevel) {
                dropReincarnationCrystal(entity, serverLevel, level);
            }
        }
    }

    /**
     * Performs the actual rebirth: restore health, apply stat boosts, grant abilities.
     * This is called after the revival delay expires.
     * Safety check: ensures the entity is still alive before applying rebirth effects.
     */
    private void performRebirth(LivingEntity entity, int level, CompoundTag data, ServerLevel serverLevel) {
        // Safety: ensure entity is still alive before rebirth
        if (!entity.isAlive()) {
            LOGGER.warn("Reincarnation rebirth skipped for {} - entity is no longer alive", entity.getName().getString());
            return;
        }
        LOGGER.debug("Performing reincarnation rebirth for {} (rebirth #{})", entity.getName().getString(), data.getInt(REINCARNATION_COUNT_KEY));

        // Clear invulnerability that was set during the revival period
        // (set by EliteEventHandler.onLivingDeath when preventing death)
        entity.setInvulnerable(false);
        entity.getPersistentData().remove(NBTKeys.REINCARNATION_INVULN);

        int rebirthCount = data.getInt(REINCARNATION_COUNT_KEY) + 1;
        data.putInt(REINCARNATION_COUNT_KEY, rebirthCount);

        // Calculate rebirth health percentage
        float healthPercent = switch (level) {
            case 1 -> 0.50f;
            case 2 -> 0.60f;
            default -> 0.75f;
        };

        // Apply stat bonuses based on level and rebirth count
        double healthBonus = switch (level) {
            case 1 -> 0.30;
            case 2 -> 0.50;
            default -> 0.80;
        };

        double damageBonus = switch (level) {
            case 1 -> 0.15;
            case 2 -> 0.25;
            default -> 0.40;
        };

        // Apply attribute modifiers
        try {
            var healthAttr = entity.getAttribute(Attributes.MAX_HEALTH);
            if (healthAttr != null) {
                healthAttr.removeModifier(HEALTH_MODIFIER_UUID);
                double baseHealth = healthAttr.getBaseValue();
                healthAttr.addTransientModifier(new AttributeModifier(
                        HEALTH_MODIFIER_UUID,
                        "EliteForge Reincarnation Health",
                        baseHealth * healthBonus,
                        AttributeModifier.Operation.ADDITION
                ));
                entity.setHealth(entity.getMaxHealth() * healthPercent);
            }

            var damageAttr = entity.getAttribute(Attributes.ATTACK_DAMAGE);
            if (damageAttr != null) {
                damageAttr.removeModifier(DAMAGE_MODIFIER_UUID);
                damageAttr.addTransientModifier(new AttributeModifier(
                        DAMAGE_MODIFIER_UUID,
                        "EliteForge Reincarnation Damage",
                        damageBonus,
                        AttributeModifier.Operation.MULTIPLY_BASE
                ));
            }
        } catch (Exception e) {
            // Attribute may not be available
        }

        // Level II+: Grant 1 random legendary ability at level I
        // Level III: Level increases each rebirth
        if (level >= 2) {
            entity.getCapability(EliteCapability.CAPABILITY).ifPresent(cap -> {
                EliteData eliteData = cap.getEliteData();

                var legendaryAbilities = AbilityRegistry.getAbilitiesByCategory(
                        com.eliteforge.ability.AbilityCategory.LEGENDARY
                ).stream()
                        .filter(Ability::isEnabled)
                        .filter(a -> !eliteData.hasAbility(a.getIdString()))
                        .toList();

                if (!legendaryAbilities.isEmpty()) {
                    Ability chosen = legendaryAbilities.get(entity.getRandom().nextInt(legendaryAbilities.size()));
                    int abilityLevel = level >= 3 ? Math.min(5, rebirthCount) : 1;
                    boolean added = eliteData.addAbility(chosen.getIdString(), abilityLevel);
                    if (added) {
                        eliteData.addAssimilatedAbility(chosen.getIdString()); // Track as special-ability (breaks exclusivity, similar to assimilation)
                    }

                    try {
                        if (added) {
                            chosen.onApply(entity, abilityLevel);
                        }
                    } catch (Exception e) {
                        // Silently fail
                    }
                }

                cap.setEliteData(eliteData);
                EliteCapabilitySync.broadcastEliteDataUpdate(entity, eliteData);
            });
        }

        // Reassemble animation - soul particles converge
        for (int i = 0; i < 30 + level * 10; i++) {
            double angle = entity.getRandom().nextDouble() * Math.PI * 2;
            double radius = 2.0 + entity.getRandom().nextDouble() * 2.0;
            double px = entity.getX() + Math.cos(angle) * radius;
            double pz = entity.getZ() + Math.sin(angle) * radius;
            serverLevel.sendParticles(ParticleTypes.SOUL,
                    px, entity.getY() + entity.getBbHeight() * 0.5, pz,
                    1, -Math.cos(angle) * 0.2, 0.1, -Math.sin(angle) * 0.2, 0.01);
        }

        // Central burst
        serverLevel.sendParticles(ParticleTypes.TOTEM_OF_UNDYING,
                entity.getX(), entity.getY() + entity.getBbHeight() * 0.5, entity.getZ(),
                20 + level * 5, 0.6, entity.getBbHeight() * 0.5, 0.6, 0.2);
        serverLevel.sendParticles(ParticleTypes.END_ROD,
                entity.getX(), entity.getY() + entity.getBbHeight() * 0.5, entity.getZ(),
                15 + level * 3, 0.5, entity.getBbHeight() * 0.4, 0.5, 0.1);

        // Announce to nearby players
        // C1: Now uses translatable key "message.eliteforge.reincarnation.rebirth"
        // with (entityName, rebirthCount) as %s/%d parameters.
        AABB area = new AABB(
                entity.getX() - 48, entity.getY() - 48, entity.getZ() - 48,
                entity.getX() + 48, entity.getY() + 48, entity.getZ() + 48
        );
        Component rebirthMsg = Component.translatable("message.eliteforge.reincarnation.rebirth",
                entity.getName().getString(), rebirthCount)
                .withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE, net.minecraft.ChatFormatting.BOLD);
        for (Player player : serverLevel.getEntitiesOfClass(Player.class, area)) {
            player.sendSystemMessage(rebirthMsg);
        }

        // Play totem activation visual
        entity.level().broadcastEntityEvent(entity, (byte) 35);
    }

    /**
     * Drops a "Reincarnation Crystal" item on final death.
     */
    private void dropReincarnationCrystal(LivingEntity entity, ServerLevel serverLevel, int level) {
        ItemStack crystal = new ItemStack(Items.NETHER_STAR);
        Component name = Component.literal("✦ Reincarnation Crystal ✦")
                .withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE);
        crystal.setHoverName(name);

        // Add NBT data
        CompoundTag crystalTag = crystal.getOrCreateTag();
        crystalTag.putBoolean(NBTKeys.ITEM_REINCARNATION_CRYSTAL, true);
        crystalTag.putInt("ReincarnationLevel", level);
        crystalTag.putBoolean("Enchanted", true);
        crystalTag.putString(NBTKeys.ITEM_QUALITY, "MYTHIC");

        ItemEntity itemEntity = new ItemEntity(serverLevel,
                entity.getX(), entity.getY() + 0.5, entity.getZ(),
                crystal);
        serverLevel.addFreshEntity(itemEntity);

        // Crystal drop particles
        serverLevel.sendParticles(ParticleTypes.SOUL,
                entity.getX(), entity.getY() + entity.getBbHeight() * 0.5, entity.getZ(),
                20, 0.5, entity.getBbHeight() * 0.5, 0.5, 0.05);
    }
}
