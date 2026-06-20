package com.eliteforge.ability.defense;

import com.eliteforge.ability.Ability;
import com.eliteforge.ability.AbilityCategory;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;

import java.util.concurrent.ThreadLocalRandom;

/**
 * AbilityReflect (反弹) - Chance to reflect projectiles back at the shooter.
 * 
 * On being hit by projectile: chance (15% + level * 10%) to reflect it back.
 * At level V: 65% reflect chance.
 * Reverse projectile trail particles.
 */
public class AbilityReflect extends Ability {

    public AbilityReflect() {
        super(
            new ResourceLocation("eliteforge", "reflect"),
            AbilityCategory.DEFENSE,
            2.5f
        );
    }

    @Override
    public void onTick(LivingEntity entity, int level) {
        // Passive - activated on projectile hit
    }

    @Override
    public void onHurt(LivingEntity entity, float damage, int level) {
        if (entity.level().isClientSide) return;

        // Check if the damage source was a projectile
        var lastSource = entity.getLastDamageSource();
        if (lastSource == null) return;

        var directEntity = lastSource.getDirectEntity();
        if (!(directEntity instanceof Projectile projectile)) return;

        float reflectChance = 0.15f + level * 0.10f;
        if (ThreadLocalRandom.current().nextFloat() > reflectChance) return;

        // Get the projectile owner/shooter
        var owner = projectile.getOwner();
        if (owner == null || owner == entity) return;

        // Reflect the projectile back
        Vec3 direction = owner.position().subtract(entity.position()).normalize();
        Projectile reflectedProjectile = (Projectile) projectile.getType().create(entity.level());
        if (reflectedProjectile == null) return;

        reflectedProjectile.setPos(entity.getX(), entity.getY() + entity.getBbHeight() * 0.5, entity.getZ());
        reflectedProjectile.setDeltaMovement(direction.scale(projectile.getDeltaMovement().length()));
        reflectedProjectile.setOwner(entity);
        entity.level().addFreshEntity(reflectedProjectile);

        // Remove original projectile
        projectile.discard();

        // Reverse projectile trail particles
        if (entity.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.CRIT,
                    entity.getX(), entity.getY() + entity.getBbHeight() * 0.5, entity.getZ(),
                    8 + level * 2,
                    0.3, 0.3, 0.3,
                    0.1);
        }
    }
}
