package com.eliteforge.ability.control;

import com.eliteforge.ability.Ability;
import com.eliteforge.ability.AbilityCategory;
import com.eliteforge.util.NBTKeys;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * AbilityVoid (虚空) - Teleports nearby players to random positions.
 * 
 * Teleports nearby players (3 + level blocks) to a random position 5-10 blocks away.
 * Cooldown: (200 - level * 30) ticks.
 * Ender particles + void.
 */
public class AbilityVoid extends Ability {

    public AbilityVoid() {
        super(
            new ResourceLocation("eliteforge", "void"),
            AbilityCategory.CONTROL,
            3.0f
        );
    }

    @Override
    public void onApply(LivingEntity entity, int level) {
        if (entity.level().isClientSide) return;
        entity.getPersistentData().putInt(NBTKeys.VOID_COOLDOWN, 0);
    }

    @Override
    public void onTick(LivingEntity entity, int level) {
        if (entity.level().isClientSide) return;
        if (entity.tickCount % 10 != 0) return; // Check every 0.5 seconds

        CompoundTag data = entity.getPersistentData();
        int cooldown = data.getInt(NBTKeys.VOID_COOLDOWN);

        if (cooldown > 0) {
            data.putInt(NBTKeys.VOID_COOLDOWN, cooldown - 10);
            return;
        }

        double range = 3.0 + level;
        AABB area = new AABB(
                entity.getX() - range, entity.getY() - range, entity.getZ() - range,
                entity.getX() + range, entity.getY() + range, entity.getZ() + range
        );

        List<Player> nearbyPlayers = entity.level().getEntitiesOfClass(Player.class, area,
                p -> p.isAlive() && !p.isSpectator() && !p.isCreative());

        if (nearbyPlayers.isEmpty()) return;

        for (Player player : nearbyPlayers) {
            // Teleport to random position 5-10 blocks away — validate the destination
            // is safe (loaded chunk, non-fluid, non-void) before teleporting. Without
            // this check the ability could strand players in walls, lava, or the void.
            double teleportDistance = 5.0 + ThreadLocalRandom.current().nextDouble() * 5.0;
            double angle = ThreadLocalRandom.current().nextDouble() * Math.PI * 2;

            double newX = entity.getX() + Math.cos(angle) * teleportDistance;
            double newZ = entity.getZ() + Math.sin(angle) * teleportDistance;
            double newY = entity.getY(); // Keep same Y level

            // Find a safe Y at the target X/Z: scan downward for a solid floor with 2 air above.
            net.minecraft.core.BlockPos.MutableBlockPos probe = new net.minecraft.core.BlockPos.MutableBlockPos();
            boolean safe = false;
            for (int dy = 0; dy >= -8; dy--) {
                probe.set(newX, newY + dy + 1, newZ);
                net.minecraft.world.level.block.state.BlockState feet = entity.level().getBlockState(probe);
                probe.set(newX, newY + dy, newZ);
                net.minecraft.world.level.block.state.BlockState floor = entity.level().getBlockState(probe);
                if (floor.blocksMotion() && !floor.liquid()
                        && feet.getFluidState().isEmpty()
                        && !entity.level().getBlockState(probe.above()).liquid()) {
                    newY = newY + dy;
                    safe = true;
                    break;
                }
            }
            // Skip this player if no safe landing was found — don't teleport into a hazard.
            if (!safe) continue;

            // Ender particles at old position
            if (player.level() instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ParticleTypes.PORTAL,
                        player.getX(), player.getY() + player.getBbHeight() * 0.5, player.getZ(),
                        15 + level * 3,
                        0.5, 0.5, 0.5,
                        0.5);
            }

            // Teleport the player
            player.teleportTo(newX, newY, newZ);

            // Void particles at new position
            if (player.level() instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ParticleTypes.PORTAL,
                        newX, newY + player.getBbHeight() * 0.5, newZ,
                        15 + level * 3,
                        0.5, 0.5, 0.5,
                        0.5);
            }
        }

        // Set cooldown
        int cooldownTicks = Math.max(50, 200 - level * 30);
        data.putInt(NBTKeys.VOID_COOLDOWN, cooldownTicks);

        // Ender particles + void at entity
        if (entity.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.REVERSE_PORTAL,
                    entity.getX(), entity.getY() + entity.getBbHeight() * 0.5, entity.getZ(),
                    10 + level * 2,
                    0.4, 0.4, 0.4,
                    0.1);
        }
    }
}
