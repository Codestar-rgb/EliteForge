package com.eliteforge.ability.legendary;

import com.eliteforge.ability.Ability;
import com.eliteforge.ability.AbilityCategory;
import com.eliteforge.util.NBTKeys;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * AbilityChaos (混沌) - Random effects periodically.
 * 
 * Random effect every (60 - level * 10) ticks:
 * - Random buff on self
 * - Random debuff on nearby players
 * - Random environmental effect (lightning, explosion, etc.)
 * At level V: can trigger multiple effects at once.
 * Random colored particles.
 */
public class AbilityChaos extends Ability {

    // Available buff effects for self
    private static final net.minecraft.world.effect.MobEffect[] BUFF_EFFECTS = {
            MobEffects.MOVEMENT_SPEED,
            MobEffects.DAMAGE_BOOST,
            MobEffects.DAMAGE_RESISTANCE,
            MobEffects.REGENERATION,
            MobEffects.JUMP,
            MobEffects.FIRE_RESISTANCE,
            MobEffects.INVISIBILITY,
            MobEffects.ABSORPTION
    };

    // Available debuff effects for players
    private static final net.minecraft.world.effect.MobEffect[] DEBUFF_EFFECTS = {
            MobEffects.MOVEMENT_SLOWDOWN,
            MobEffects.WEAKNESS,
            MobEffects.POISON,
            MobEffects.WITHER,
            MobEffects.BLINDNESS,
            MobEffects.CONFUSION,
            MobEffects.HUNGER,
            MobEffects.LEVITATION
    };

    public AbilityChaos() {
        super(
            new ResourceLocation("eliteforge", "chaos"),
            AbilityCategory.LEGENDARY,
            4.5f
        );
    }

    @Override
    public void onApply(LivingEntity entity, int level) {
        if (entity.level().isClientSide) return;
        entity.getPersistentData().putInt(NBTKeys.CHAOS_COOLDOWN, 0);
    }

    @Override
    public void onTick(LivingEntity entity, int level) {
        if (entity.level().isClientSide) return;
        if (entity.tickCount % 10 != 0) return;

        CompoundTag data = entity.getPersistentData();
        if (!data.contains(NBTKeys.CHAOS_COOLDOWN)) {
            onApply(entity, level);
            return;
        }

        int cooldown = data.getInt(NBTKeys.CHAOS_COOLDOWN);
        cooldown -= 10;

        if (cooldown <= 0) {
            int effectCount = (level >= 5) ? ThreadLocalRandom.current().nextInt(3) + 1 : 1; // Level V: 1-3 effects at once
            for (int i = 0; i < effectCount; i++) {
                triggerChaosEffect(entity, level);
            }
            int newCooldown = Math.max(10, 60 - level * 10);
            data.putInt(NBTKeys.CHAOS_COOLDOWN, newCooldown);
        } else {
            data.putInt(NBTKeys.CHAOS_COOLDOWN, cooldown);
        }

        // Ambient random colored particles
        if (entity.tickCount % 5 == 0 && entity.level() instanceof ServerLevel serverLevel) {
            // Random particle types for visual chaos
            var particleType = switch (ThreadLocalRandom.current().nextInt(4)) {
                case 0 -> ParticleTypes.ENTITY_EFFECT;
                case 1 -> ParticleTypes.ENCHANT;
                case 2 -> ParticleTypes.WITCH;
                default -> ParticleTypes.END_ROD;
            };
            serverLevel.sendParticles(particleType,
                    entity.getX() + (ThreadLocalRandom.current().nextDouble() - 0.5) * entity.getBbWidth(),
                    entity.getY() + ThreadLocalRandom.current().nextDouble() * entity.getBbHeight(),
                    entity.getZ() + (ThreadLocalRandom.current().nextDouble() - 0.5) * entity.getBbWidth(),
                    1, 0.1, 0.1, 0.1, 0.02);
        }
    }

    /**
     * Triggers a random chaos effect.
     */
    private void triggerChaosEffect(LivingEntity entity, int level) {
        if (!(entity.level() instanceof ServerLevel serverLevel)) return;

        int effectType = ThreadLocalRandom.current().nextInt(3); // 0 = buff, 1 = debuff, 2 = environmental

        switch (effectType) {
            case 0 -> applyRandomBuff(entity, level, serverLevel);
            case 1 -> applyRandomDebuff(entity, level, serverLevel);
            case 2 -> applyEnvironmentalEffect(entity, level, serverLevel);
        }
    }

    /**
     * Applies a random buff to the entity.
     */
    private void applyRandomBuff(LivingEntity entity, int level, ServerLevel serverLevel) {
        net.minecraft.world.effect.MobEffect buff = BUFF_EFFECTS[ThreadLocalRandom.current().nextInt(BUFF_EFFECTS.length)];
        int duration = 100 + level * 20;
        int amplifier = ThreadLocalRandom.current().nextInt(Math.min(level, 3));
        entity.addEffect(new MobEffectInstance(buff, duration, amplifier, false, true));

        serverLevel.sendParticles(ParticleTypes.ENCHANT,
                entity.getX(), entity.getY() + entity.getBbHeight() * 0.5, entity.getZ(),
                8 + level * 2,
                0.3, entity.getBbHeight() * 0.3, 0.3,
                0.2);
    }

    /**
     * Applies a random debuff to nearby players.
     */
    private void applyRandomDebuff(LivingEntity entity, int level, ServerLevel serverLevel) {
        double range = 6.0 + level * 2.0;
        AABB area = new AABB(
                entity.getX() - range, entity.getY() - range, entity.getZ() - range,
                entity.getX() + range, entity.getY() + range, entity.getZ() + range
        );

        List<Player> players = serverLevel.getEntitiesOfClass(Player.class, area,
                p -> p.isAlive() && !p.isSpectator() && !p.isCreative());

        if (players.isEmpty()) return;

        net.minecraft.world.effect.MobEffect debuff = DEBUFF_EFFECTS[ThreadLocalRandom.current().nextInt(DEBUFF_EFFECTS.length)];
        int duration = 60 + level * 20;
        int amplifier = ThreadLocalRandom.current().nextInt(Math.min(level, 3));

        for (Player player : players) {
            player.addEffect(new MobEffectInstance(debuff, duration, amplifier, false, true));
        }

        serverLevel.sendParticles(ParticleTypes.WITCH,
                entity.getX(), entity.getY() + entity.getBbHeight() * 0.5, entity.getZ(),
                8 + level * 2,
                0.4, entity.getBbHeight() * 0.3, 0.4,
                0.05);
    }

    /**
     * Applies a random environmental effect.
     */
    private void applyEnvironmentalEffect(LivingEntity entity, int level, ServerLevel serverLevel) {
        int envEffect = ThreadLocalRandom.current().nextInt(3);

        switch (envEffect) {
            case 0 -> {
                // Fake lightning strike nearby
                double ox = (ThreadLocalRandom.current().nextDouble() - 0.5) * 8;
                double oz = (ThreadLocalRandom.current().nextDouble() - 0.5) * 8;
                serverLevel.sendParticles(ParticleTypes.END_ROD,
                        entity.getX() + ox, entity.getY() + 5, entity.getZ() + oz,
                        10 + level * 3,
                        0.3, 5, 0.3,
                        0.1);
                // Deal some damage to nearby players
                var source = entity.damageSources().lightningBolt();
                AABB strikeArea = new AABB(
                        entity.getX() + ox - 2, entity.getY() - 2, entity.getZ() + oz - 2,
                        entity.getX() + ox + 2, entity.getY() + 5, entity.getZ() + oz + 2
                );
                for (Player player : serverLevel.getEntitiesOfClass(Player.class, strikeArea)) {
                    player.hurt(source, level * 1.5f);
                }
            }
            case 1 -> {
                // Small explosion (no block damage)
                serverLevel.explode(null,
                        entity.getX() + (ThreadLocalRandom.current().nextDouble() - 0.5) * 6,
                        entity.getY(),
                        entity.getZ() + (ThreadLocalRandom.current().nextDouble() - 0.5) * 6,
                        level * 0.3f,
                        false,
                        net.minecraft.world.level.Level.ExplosionInteraction.NONE);
            }
            case 2 -> {
                // Random fire in area
                double fx = entity.getX() + (ThreadLocalRandom.current().nextDouble() - 0.5) * 8;
                double fz = entity.getZ() + (ThreadLocalRandom.current().nextDouble() - 0.5) * 8;
                serverLevel.sendParticles(ParticleTypes.FLAME,
                        fx, entity.getY() + 1, fz,
                        15 + level * 3,
                        0.5, 0.5, 0.5,
                        0.05);
                // Set nearby entities on fire
                AABB fireArea = new AABB(fx - 2, entity.getY() - 1, fz - 2, fx + 2, entity.getY() + 3, fz + 2);
                for (Player player : serverLevel.getEntitiesOfClass(Player.class, fireArea)) {
                    player.setSecondsOnFire(2 + level);
                }
            }
        }
    }
}
