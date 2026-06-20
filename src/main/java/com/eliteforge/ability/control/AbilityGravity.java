package com.eliteforge.ability.control;

import com.eliteforge.ability.Ability;
import com.eliteforge.ability.AbilityCategory;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * AbilityGravity (引力) - Pulls nearby players toward the entity.
 * 
 * Pulls players within (4 + level * 2) blocks toward the entity.
 * Strength increases with proximity.
 * Portal particles pulling inward.
 */
public class AbilityGravity extends Ability {

    public AbilityGravity() {
        super(
            new ResourceLocation("eliteforge", "gravity"),
            AbilityCategory.CONTROL,
            2.0f
        );
    }

    @Override
    public void onTick(LivingEntity entity, int level) {
        if (entity.level().isClientSide) return;
        if (entity.tickCount % 5 != 0) return; // Check every 0.25 seconds

        double range = 4.0 + level * 2.0;
        AABB area = new AABB(
                entity.getX() - range, entity.getY() - range, entity.getZ() - range,
                entity.getX() + range, entity.getY() + range, entity.getZ() + range
        );

        List<Player> nearbyPlayers = entity.level().getEntitiesOfClass(Player.class, area,
                p -> p.isAlive() && !p.isSpectator() && !p.isCreative());

        for (Player player : nearbyPlayers) {
            double distance = player.distanceTo(entity);
            if (distance > range || distance < 0.5) continue;

            // Pull strength: stronger when closer
            double pullStrength = (1.0 - (distance / range)) * 0.15 * (1 + level * 0.3);
            Vec3 direction = entity.position().subtract(player.position()).normalize();
            Vec3 pullVector = direction.scale(pullStrength);

            player.setDeltaMovement(player.getDeltaMovement().add(pullVector));
            player.hurtMarked = true;
        }

        // Portal particles pulling inward
        if (!nearbyPlayers.isEmpty() && entity.level() instanceof ServerLevel serverLevel) {
            for (int i = 0; i < 2 + level; i++) {
                double angle = (entity.tickCount * 0.1) + (Math.PI * 2 * i) / (2 + level);
                double px = entity.getX() + Math.cos(angle) * range * 0.7;
                double py = entity.getY() + entity.getBbHeight() * 0.5;
                double pz = entity.getZ() + Math.sin(angle) * range * 0.7;
                serverLevel.sendParticles(ParticleTypes.PORTAL,
                        px, py, pz, 1,
                        0.1, 0.1, 0.1, 0.0);
            }
        }
    }

    @Override
    public void onAttack(LivingEntity attacker, LivingEntity target, float damage, int level) {
        // Gravity pull is handled passively via onTick
    }
}
