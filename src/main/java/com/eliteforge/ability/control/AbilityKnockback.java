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
 * AbilityKnockback (击退) - Increased knockback on attack and periodic push.
 *
 * On attack: knockback multiplier = (1 + level * 0.5).
 * Also pushes nearby players every (60 - level * 10) ticks.
 * Explosion particles.
 */
public class AbilityKnockback extends Ability {

    public AbilityKnockback() {
        super(
            new ResourceLocation("eliteforge", "knockback"),
            AbilityCategory.CONTROL,
            1.5f
        );
    }

    @Override
    public void onTick(LivingEntity entity, int level) {
        if (entity.level().isClientSide) return;

        int cooldown = Math.max(20, 60 - level * 10);
        // Use entity.tickCount (Minecraft's built-in tick counter) instead of the
        // deprecated AbilityManager NBT-based counter (which always returned 0).
        if (entity.tickCount > 20 && entity.tickCount % cooldown == 0) {
            pushNearbyPlayers(entity, level);
        }
    }

    @Override
    public void onAttack(LivingEntity attacker, LivingEntity target, float damage, int level) {
        if (attacker.level().isClientSide) return;

        // Apply extra knockback
        float knockbackMultiplier = 1.0f + level * 0.5f;
        // Guard against zero distance (attacker on top of target) — normalize() of the
        // zero vector yields NaN, which would propagate into the player's velocity.
        Vec3 delta = target.position().subtract(attacker.position());
        Vec3 knockbackDirection = delta.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 1) : delta.normalize();
        double knockbackStrength = 0.5 * knockbackMultiplier;
        target.setDeltaMovement(target.getDeltaMovement().add(
                knockbackDirection.x * knockbackStrength,
                0.3 * knockbackMultiplier,
                knockbackDirection.z * knockbackStrength
        ));
        target.hurtMarked = true;

        // Explosion particles at impact
        if (attacker.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.POOF,
                    target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(),
                    5 + level * 2,
                    0.2, 0.2, 0.2,
                    0.05);
        }
    }

    /**
     * Pushes all nearby players away from the entity.
     */
    private void pushNearbyPlayers(LivingEntity entity, int level) {
        double range = 4.0 + level;
        AABB area = new AABB(
                entity.getX() - range, entity.getY() - range, entity.getZ() - range,
                entity.getX() + range, entity.getY() + range, entity.getZ() + range
        );

        List<Player> nearbyPlayers = entity.level().getEntitiesOfClass(Player.class, area,
                p -> p.isAlive() && !p.isSpectator() && !p.isCreative());

        for (Player player : nearbyPlayers) {
            Vec3 pushDirection = player.position().subtract(entity.position()).normalize();
            double pushStrength = 0.5 + level * 0.25;
            player.setDeltaMovement(player.getDeltaMovement().add(
                    pushDirection.x * pushStrength,
                    0.3,
                    pushDirection.z * pushStrength
            ));
            player.hurtMarked = true;
        }

        // Explosion particles
        if (!nearbyPlayers.isEmpty() && entity.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.POOF,
                    entity.getX(), entity.getY() + entity.getBbHeight() * 0.5, entity.getZ(),
                    8 + level * 2,
                    0.4, 0.4, 0.4,
                    0.05);
        }
    }
}
