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

/**
 * AbilityTimeWarp (时空扭曲) - Periodically slows nearby players and speeds up self.
 * 
 * Every (400 - level * 60) ticks, slow all nearby players (Slowness III + Mining Fatigue III).
 * Duration: 3s + level * 1s.
 * At level IV+: also speed up entity (Speed II).
 * Clock particles (portal).
 */
public class AbilityTimeWarp extends Ability {

    public AbilityTimeWarp() {
        super(
            new ResourceLocation("eliteforge", "time_warp"),
            AbilityCategory.LEGENDARY,
            4.0f
        );
    }

    @Override
    public void onApply(LivingEntity entity, int level) {
        if (entity.level().isClientSide) return;
        entity.getPersistentData().putInt(NBTKeys.TIME_WARP_COOLDOWN, 0);
    }

    @Override
    public void onTick(LivingEntity entity, int level) {
        if (entity.level().isClientSide) return;
        if (entity.tickCount % 10 != 0) return;

        CompoundTag data = entity.getPersistentData();
        if (!data.contains(NBTKeys.TIME_WARP_COOLDOWN)) {
            onApply(entity, level);
            return;
        }

        int cooldown = data.getInt(NBTKeys.TIME_WARP_COOLDOWN);
        cooldown -= 10;

        if (cooldown <= 0) {
            activateTimeWarp(entity, level);
            int newCooldown = Math.max(100, 400 - level * 60);
            data.putInt(NBTKeys.TIME_WARP_COOLDOWN, newCooldown);
        } else {
            data.putInt(NBTKeys.TIME_WARP_COOLDOWN, cooldown);
        }

        // Ambient portal particles (clock substitute)
        if (entity.tickCount % 20 == 0 && entity.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.PORTAL,
                    entity.getX(), entity.getY() + entity.getBbHeight() * 0.5, entity.getZ(),
                    1 + level / 2,
                    entity.getBbWidth() * 0.3, entity.getBbHeight() * 0.3, entity.getBbWidth() * 0.3,
                    0.01);
        }
    }

    /**
     * Activates the time warp effect.
     */
    private void activateTimeWarp(LivingEntity entity, int level) {
        if (!(entity.level() instanceof ServerLevel serverLevel)) return;

        double range = 8.0 + level * 2.0;
        AABB area = new AABB(
                entity.getX() - range, entity.getY() - range, entity.getZ() - range,
                entity.getX() + range, entity.getY() + range, entity.getZ() + range
        );

        List<Player> nearbyPlayers = serverLevel.getEntitiesOfClass(Player.class, area,
                p -> p.isAlive() && !p.isSpectator() && !p.isCreative());

        int durationTicks = 60 + level * 20; // 3s + level * 1s

        // Apply Slowness III + Mining Fatigue III to all nearby players
        for (Player player : nearbyPlayers) {
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, durationTicks, 2, false, true));
            player.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, durationTicks, 2, false, true));
        }

        // At level IV+: speed up self
        if (level >= 4) {
            entity.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, durationTicks, 1, false, true));
        }

        // Portal particles (clock substitute)
        for (int i = 0; i < 6 + level * 2; i++) {
            double angle = (Math.PI * 2 * i) / (6 + level * 2);
            double px = entity.getX() + Math.cos(angle) * range * 0.6;
            double pz = entity.getZ() + Math.sin(angle) * range * 0.6;
            serverLevel.sendParticles(ParticleTypes.PORTAL,
                    px, entity.getY() + entity.getBbHeight() * 0.5, pz,
                    3 + level,
                    0.3, entity.getBbHeight() * 0.3, 0.3,
                    0.1);
        }

        // Center burst
        serverLevel.sendParticles(ParticleTypes.REVERSE_PORTAL,
                entity.getX(), entity.getY() + entity.getBbHeight() * 0.5, entity.getZ(),
                15 + level * 3,
                0.5, entity.getBbHeight() * 0.5, 0.5,
                0.2);
    }
}
