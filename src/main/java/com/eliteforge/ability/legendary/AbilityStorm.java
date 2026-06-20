package com.eliteforge.ability.legendary;

import com.eliteforge.ability.Ability;
import com.eliteforge.ability.AbilityCategory;
import com.eliteforge.util.NBTKeys;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.SmallFireball;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * AbilityStorm (风暴) - Summons a storm of projectiles around the entity.
 * 
 * Every (100 - level * 15) ticks, launches level projectiles at nearby players.
 * Particle: thunder + fire particles.
 */
public class AbilityStorm extends Ability {

    public AbilityStorm() {
        super(
            new ResourceLocation("eliteforge", "storm"),
            AbilityCategory.LEGENDARY,
            4.0f
        );
    }

    @Override
    public void onApply(LivingEntity entity, int level) {
        if (entity.level().isClientSide) return;
        entity.getPersistentData().putInt(NBTKeys.STORM_COOLDOWN, 0);
    }

    @Override
    public void onTick(LivingEntity entity, int level) {
        if (entity.level().isClientSide) return;
        if (entity.tickCount % 10 != 0) return;

        CompoundTag data = entity.getPersistentData();
        if (!data.contains(NBTKeys.STORM_COOLDOWN)) {
            onApply(entity, level);
            return;
        }

        int cooldown = data.getInt(NBTKeys.STORM_COOLDOWN);
        cooldown -= 10;

        if (cooldown <= 0) {
            launchStormProjectiles(entity, level);
            int newCooldown = Math.max(25, 100 - level * 15);
            data.putInt(NBTKeys.STORM_COOLDOWN, newCooldown);
        } else {
            data.putInt(NBTKeys.STORM_COOLDOWN, cooldown);
        }

        // Ambient thunder particles
        if (entity.tickCount % 20 == 0 && entity.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.END_ROD,
                    entity.getX(), entity.getY() + entity.getBbHeight() * 0.75, entity.getZ(),
                    2 + level,
                    entity.getBbWidth() * 0.5, entity.getBbHeight() * 0.3, entity.getBbWidth() * 0.5,
                    0.02);
        }
    }

    /**
     * Launches projectiles at nearby players.
     */
    private void launchStormProjectiles(LivingEntity entity, int level) {
        if (!(entity.level() instanceof ServerLevel serverLevel)) return;

        double searchRange = 20.0;
        AABB area = new AABB(
                entity.getX() - searchRange, entity.getY() - searchRange, entity.getZ() - searchRange,
                entity.getX() + searchRange, entity.getY() + searchRange, entity.getZ() + searchRange
        );

        List<Player> players = serverLevel.getEntitiesOfClass(Player.class, area,
                p -> p.isAlive() && !p.isSpectator() && !p.isCreative());

        if (players.isEmpty()) return;

        int projectileCount = level;

        for (int i = 0; i < projectileCount; i++) {
            // Pick a random target player
            Player target = players.get(ThreadLocalRandom.current().nextInt(players.size()));

            Vec3 direction = target.position().subtract(entity.position())
                    .add(0, target.getBbHeight() * 0.5, 0)
                    .normalize();

            // Alternate between arrows and fireballs
            if (ThreadLocalRandom.current().nextBoolean()) {
                // Fireball — in 1.20.1 use EntityType.SMALL_FIREBALL.create() + setter APIs
                // (avoids ambiguity with the (Level, LivingEntity, Vec3) constructor overload resolution)
                SmallFireball fireball = net.minecraft.world.entity.EntityType.SMALL_FIREBALL.create(serverLevel);
                if (fireball != null) {
                    fireball.setOwner(entity);
                    fireball.setPos(entity.getX(), entity.getY() + entity.getBbHeight() * 0.5, entity.getZ());
                    fireball.setDeltaMovement(direction.scale(0.5 + level * 0.1));
                    serverLevel.addFreshEntity(fireball);
                }
            } else {
                // Arrow
                Arrow arrow = new Arrow(serverLevel, entity);
                arrow.setPos(entity.getX(), entity.getY() + entity.getBbHeight() * 0.5, entity.getZ());
                arrow.shoot(direction.x, direction.y, direction.z, 1.5f + level * 0.2f, 2.0f);
                arrow.setBaseDamage(level * 1.5);
                serverLevel.addFreshEntity(arrow);
            }
        }

        // Thunder + fire particles
        serverLevel.sendParticles(ParticleTypes.END_ROD,
                entity.getX(), entity.getY() + entity.getBbHeight() * 0.75, entity.getZ(),
                Math.min(15 + level * 5, 40),
                0.5, 0.5, 0.5,
                0.1);
        serverLevel.sendParticles(ParticleTypes.FLAME,
                entity.getX(), entity.getY() + entity.getBbHeight() * 0.5, entity.getZ(),
                10 + level * 3,
                0.3, 0.3, 0.3,
                0.05);
    }
}
