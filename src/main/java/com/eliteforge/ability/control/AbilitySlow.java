package com.eliteforge.ability.control;

import com.eliteforge.ability.Ability;
import com.eliteforge.ability.AbilityCategory;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * AbilitySlow (迟缓) - Applies Slowness to nearby players.
 * 
 * Applies Slowness to players within (3 + level) blocks.
 * Slowness level: min(level, 4), duration: 5s + level * 2s.
 * Applies every 30 ticks.
 * Snowflake particles.
 */
public class AbilitySlow extends Ability {

    public AbilitySlow() {
        super(
            new ResourceLocation("eliteforge", "slow"),
            AbilityCategory.CONTROL,
            1.5f
        );
    }

    @Override
    public void onTick(LivingEntity entity, int level) {
        if (entity.level().isClientSide) return;
        if (entity.tickCount % 30 != 0) return; // Every 1.5 seconds

        double range = 3.0 + level;
        AABB area = new AABB(
                entity.getX() - range, entity.getY() - range, entity.getZ() - range,
                entity.getX() + range, entity.getY() + range, entity.getZ() + range
        );

        List<Player> nearbyPlayers = entity.level().getEntitiesOfClass(Player.class, area,
                p -> p.isAlive() && !p.isSpectator() && !p.isCreative());

        int slownessLevel = Math.min(level, 4);
        int durationTicks = 100 + level * 40; // 5s + level * 2s

        for (Player player : nearbyPlayers) {
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, durationTicks, slownessLevel, false, true));
        }

        // Snowflake particles
        if (!nearbyPlayers.isEmpty() && entity.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.SNOWFLAKE,
                    entity.getX(), entity.getY() + entity.getBbHeight() * 0.5, entity.getZ(),
                    5 + level * 2,
                    range * 0.4, entity.getBbHeight() * 0.3, range * 0.4,
                    0.02);
        }
    }
}
