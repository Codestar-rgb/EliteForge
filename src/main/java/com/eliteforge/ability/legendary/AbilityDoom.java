package com.eliteforge.ability.legendary;

import com.eliteforge.ability.Ability;
import com.eliteforge.ability.AbilityCategory;
import com.eliteforge.util.NBTKeys;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * AbilityDoom (末日) - Massive damage to nearby players after a countdown.
 * 
 * After entity is engaged in combat for (30 - level * 5) seconds,
 * all nearby players take massive damage.
 * Damage: level * 3 hearts, ignores armor.
 * Warning: chat message + boss bar countdown.
 * One-time activation per fight.
 * Dark red particles intensifying over time.
 */
public class AbilityDoom extends Ability {

    public AbilityDoom() {
        super(
            new ResourceLocation("eliteforge", "doom"),
            AbilityCategory.LEGENDARY,
            5.0f
        );
    }

    @Override
    public void onApply(LivingEntity entity, int level) {
        if (entity.level().isClientSide) return;
        CompoundTag data = entity.getPersistentData();
        data.putBoolean(NBTKeys.DOOM_ACTIVATED, false);
        data.putInt(NBTKeys.DOOM_TIMER, 0);
        data.putBoolean(NBTKeys.DOOM_ENGAGED, false);
    }

    @Override
    public void onTick(LivingEntity entity, int level) {
        if (entity.level().isClientSide) return;

        CompoundTag data = entity.getPersistentData();
        if (!data.contains(NBTKeys.DOOM_ACTIVATED)) {
            onApply(entity, level);
            return;
        }

        boolean activated = data.getBoolean(NBTKeys.DOOM_ACTIVATED);
        if (activated) return; // Already activated this fight

        // Check if entity is in combat
        boolean inCombat = entity.getLastHurtByMob() != null || (entity instanceof net.minecraft.world.entity.Mob mob && mob.getTarget() != null);
        if (!inCombat) {
            // Reset if not in combat
            data.putBoolean(NBTKeys.DOOM_ENGAGED, false);
            data.putInt(NBTKeys.DOOM_TIMER, 0);
            return;
        }

        boolean engaged = data.getBoolean(NBTKeys.DOOM_ENGAGED);
        if (!engaged) {
            // Start doom countdown
            data.putBoolean(NBTKeys.DOOM_ENGAGED, true);
            int countdownTicks = (30 - level * 5) * 20; // Convert seconds to ticks
            data.putInt(NBTKeys.DOOM_TIMER, countdownTicks);

            // Send warning to nearby players
            warnNearbyPlayers(entity, level);
            return;
        }

        int timer = data.getInt(NBTKeys.DOOM_TIMER);
        timer--;

        // Update doom particles (intensify as timer decreases)
        if (entity.level() instanceof ServerLevel serverLevel) {
            float progress = 1.0f - ((float) timer / ((30 - level * 5) * 20));
            int particleCount = (int) (2 + progress * 8 + level * 2);
            serverLevel.sendParticles(ParticleTypes.DAMAGE_INDICATOR,
                    entity.getX(), entity.getY() + entity.getBbHeight() * 0.5, entity.getZ(),
                    particleCount,
                    entity.getBbWidth() * 0.5 * progress, entity.getBbHeight() * 0.5, entity.getBbWidth() * 0.5 * progress,
                    0.02);
        }

        if (timer <= 0) {
            // Activate DOOM!
            activateDoom(entity, level);
            data.putBoolean(NBTKeys.DOOM_ACTIVATED, true);
        } else {
            data.putInt(NBTKeys.DOOM_TIMER, timer);
        }
    }

    @Override
    public void onHurt(LivingEntity entity, float damage, int level) {
        // Combat engagement is detected via lastHurtByMob
    }

    @Override
    public void onDeath(LivingEntity entity, int level) {
        // Reset doom state on death
        CompoundTag data = entity.getPersistentData();
        data.putBoolean(NBTKeys.DOOM_ACTIVATED, false);
        data.putBoolean(NBTKeys.DOOM_ENGAGED, false);
        data.putInt(NBTKeys.DOOM_TIMER, 0);
    }

    /**
     * Sends a warning message to nearby players.
     */
    private void warnNearbyPlayers(LivingEntity entity, int level) {
        if (!(entity.level() instanceof ServerLevel serverLevel)) return;

        double range = 32.0;
        AABB area = new AABB(
                entity.getX() - range, entity.getY() - range, entity.getZ() - range,
                entity.getX() + range, entity.getY() + range, entity.getZ() + range
        );

        int countdownSeconds = 30 - level * 5;
        Component warning = Component.literal("⚠ DOOM INCOMING ⚠ " + countdownSeconds + "s")
                .withStyle(net.minecraft.ChatFormatting.DARK_RED, net.minecraft.ChatFormatting.BOLD);

        for (Player player : serverLevel.getEntitiesOfClass(Player.class, area)) {
            player.sendSystemMessage(warning);
        }
    }

    /**
     * Activates the doom effect - massive damage to all nearby players.
     */
    private void activateDoom(LivingEntity entity, int level) {
        if (!(entity.level() instanceof ServerLevel serverLevel)) return;

        double range = 12.0 + level * 2.0;
        AABB area = new AABB(
                entity.getX() - range, entity.getY() - range, entity.getZ() - range,
                entity.getX() + range, entity.getY() + range, entity.getZ() + range
        );

        // Damage: level * 3 hearts, ignores armor (magic damage)
        float doomDamage = level * 6.0f; // 3 hearts = 6 HP per level
        DamageSource doomSource = entity.damageSources().magic();

        List<Player> players = serverLevel.getEntitiesOfClass(Player.class, area,
                p -> p.isAlive() && !p.isSpectator() && !p.isCreative());

        for (Player player : players) {
            player.hurt(doomSource, doomDamage);
        }

        // Massive dark red particle explosion
        serverLevel.sendParticles(ParticleTypes.EXPLOSION,
                entity.getX(), entity.getY() + entity.getBbHeight() * 0.5, entity.getZ(),
                5 + level * 2,
                range * 0.3, entity.getBbHeight() * 0.5, range * 0.3,
                0.1);
        serverLevel.sendParticles(ParticleTypes.DAMAGE_INDICATOR,
                entity.getX(), entity.getY() + entity.getBbHeight() * 0.5, entity.getZ(),
                50 + level * 10,
                range, entity.getBbHeight() * 0.5, range,
                0.3);

        // Notify players
        Component doomMessage = Component.literal("☠ DOOM HAS ARRIVED ☠")
                .withStyle(net.minecraft.ChatFormatting.RED, net.minecraft.ChatFormatting.BOLD);
        for (Player player : players) {
            player.sendSystemMessage(doomMessage);
        }
    }
}
