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
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * AbilityPhaseShift (相位跃迁) - Periodically teleports behind nearest player.
 * 
 * Every (300 - level * 40) ticks, teleport behind nearest player.
 * Also becomes invisible for 3s + level seconds after teleporting.
 * Ender particles.
 */
public class AbilityPhaseShift extends Ability {

    public AbilityPhaseShift() {
        super(
            new ResourceLocation("eliteforge", "phase_shift"),
            AbilityCategory.LEGENDARY,
            3.5f
        );
    }

    @Override
    public void onApply(LivingEntity entity, int level) {
        if (entity.level().isClientSide) return;
        CompoundTag data = entity.getPersistentData();
        data.putInt(NBTKeys.PHASE_SHIFT_COOLDOWN, 0);
        data.putBoolean(NBTKeys.PHASE_SHIFT_INVISIBLE, false);
        data.putInt(NBTKeys.PHASE_SHIFT_INVIS_TIMER, 0);
    }

    @Override
    public void onTick(LivingEntity entity, int level) {
        if (entity.level().isClientSide) return;
        if (entity.tickCount % 10 != 0) return;

        CompoundTag data = entity.getPersistentData();
        if (!data.contains(NBTKeys.PHASE_SHIFT_COOLDOWN)) {
            onApply(entity, level);
            return;
        }

        // Handle invisibility timer
        boolean isInvisible = data.getBoolean(NBTKeys.PHASE_SHIFT_INVISIBLE);
        int invisTimer = data.getInt(NBTKeys.PHASE_SHIFT_INVIS_TIMER);

        if (isInvisible) {
            invisTimer -= 10;
            if (invisTimer <= 0) {
                data.putBoolean(NBTKeys.PHASE_SHIFT_INVISIBLE, false);
                entity.setInvisible(false);
            } else {
                data.putInt(NBTKeys.PHASE_SHIFT_INVIS_TIMER, invisTimer);
                // Maintain invisibility
                entity.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 30, 0, false, false));
            }
        }

        // Handle teleport cooldown
        int cooldown = data.getInt(NBTKeys.PHASE_SHIFT_COOLDOWN);
        cooldown -= 10;

        if (cooldown <= 0) {
            // Try to teleport behind nearest player
            tryTeleportBehindPlayer(entity, level);
            int newCooldown = Math.max(60, 300 - level * 40);
            data.putInt(NBTKeys.PHASE_SHIFT_COOLDOWN, newCooldown);
        } else {
            data.putInt(NBTKeys.PHASE_SHIFT_COOLDOWN, cooldown);
        }
    }

    /**
     * Teleports the entity behind the nearest player.
     */
    private void tryTeleportBehindPlayer(LivingEntity entity, int level) {
        if (!(entity.level() instanceof ServerLevel serverLevel)) return;

        double searchRange = 16.0;
        AABB area = new AABB(
                entity.getX() - searchRange, entity.getY() - searchRange, entity.getZ() - searchRange,
                entity.getX() + searchRange, entity.getY() + searchRange, entity.getZ() + searchRange
        );

        List<Player> players = serverLevel.getEntitiesOfClass(Player.class, area,
                p -> p.isAlive() && !p.isSpectator() && !p.isCreative());

        if (players.isEmpty()) return;

        // Find nearest player
        Optional<Player> nearestOpt = players.stream()
                .min(Comparator.comparingDouble(p -> p.distanceTo(entity)));

        if (nearestOpt.isEmpty()) return;

        Player nearest = nearestOpt.get();

        // Calculate position behind the player (opposite their look direction)
        Vec3 lookAngle = nearest.getLookAngle();
        Vec3 behindPosition = nearest.position().subtract(lookAngle.scale(2.0));

        // Ender particles at old position
        serverLevel.sendParticles(ParticleTypes.PORTAL,
                entity.getX(), entity.getY() + entity.getBbHeight() * 0.5, entity.getZ(),
                20 + level * 3,
                0.5, entity.getBbHeight() * 0.5, 0.5,
                0.5);

        // Teleport
        entity.teleportTo(behindPosition.x, nearest.getY(), behindPosition.z);

        // Ender particles at new position
        serverLevel.sendParticles(ParticleTypes.PORTAL,
                behindPosition.x, nearest.getY() + entity.getBbHeight() * 0.5, behindPosition.z,
                20 + level * 3,
                0.5, entity.getBbHeight() * 0.5, 0.5,
                0.5);

        // Become invisible
        int invisDuration = (3 + level) * 20; // 3s + level seconds
        CompoundTag data = entity.getPersistentData();
        data.putBoolean(NBTKeys.PHASE_SHIFT_INVISIBLE, true);
        data.putInt(NBTKeys.PHASE_SHIFT_INVIS_TIMER, invisDuration);
        entity.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, invisDuration + 20, 0, false, false));
    }

    @Override
    public void onRemove(LivingEntity entity, int level) {
        // Clean up phase shift NBT state to prevent permanent invisibility
        if (entity.level().isClientSide) return;
        entity.setInvisible(false);
        CompoundTag data = entity.getPersistentData();
        data.putBoolean(NBTKeys.PHASE_SHIFT_INVISIBLE, false);
        data.putInt(NBTKeys.PHASE_SHIFT_INVIS_TIMER, 0);
    }
}
