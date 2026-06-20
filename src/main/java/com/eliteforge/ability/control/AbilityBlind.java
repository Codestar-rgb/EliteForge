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
 * AbilityBlind (致盲) - Applies Blindness to nearby players.
 * 
 * Applies Blindness to players within (3 + level) blocks.
 * Duration: 3s + level * 1s.
 * Applies every 40 ticks.
 * Dark smoke particles.
 */
public class AbilityBlind extends Ability {

    public AbilityBlind() {
        super(
            new ResourceLocation("eliteforge", "blind"),
            AbilityCategory.CONTROL,
            1.5f
        );
    }

    @Override
    public void onTick(LivingEntity entity, int level) {
        if (entity.level().isClientSide) return;
        if (entity.tickCount % 40 != 0) return; // Every 2 seconds

        double range = 3.0 + level;
        AABB area = new AABB(
                entity.getX() - range, entity.getY() - range, entity.getZ() - range,
                entity.getX() + range, entity.getY() + range, entity.getZ() + range
        );

        List<Player> nearbyPlayers = entity.level().getEntitiesOfClass(Player.class, area,
                p -> p.isAlive() && !p.isSpectator() && !p.isCreative());

        int durationTicks = 60 + level * 20; // 3s + level * 1s

        for (Player player : nearbyPlayers) {
            player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, durationTicks, 0, false, true));
        }

        // Dark smoke particles
        if (!nearbyPlayers.isEmpty() && entity.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE,
                    entity.getX(), entity.getY() + entity.getBbHeight() * 0.5, entity.getZ(),
                    5 + level * 2,
                    range * 0.3, entity.getBbHeight() * 0.3, range * 0.3,
                    0.02);
        }
    }
}
