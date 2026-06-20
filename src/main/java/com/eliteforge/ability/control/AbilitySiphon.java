package com.eliteforge.ability.control;

import com.eliteforge.ability.Ability;
import com.eliteforge.ability.AbilityCategory;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * AbilitySiphon (吸取) - Drains hunger and experience from nearby players.
 * 
 * Drains (level * 0.5) hunger from players within (3 + level) blocks every 2 seconds.
 * Also drains (level * 0.3) experience.
 * Green/red swirl particles.
 */
public class AbilitySiphon extends Ability {

    public AbilitySiphon() {
        super(
            new ResourceLocation("eliteforge", "siphon"),
            AbilityCategory.CONTROL,
            2.0f
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

        float hungerDrain = level * 0.5f;
        // H9 fix: (int) Math.max(1, level * 0.3f) was always 1 for levels 1-5
        // (level*0.3 <= 1.5, max(1, 1.5)=1.5, (int)1.5=1). Now scales linearly:
        // level 1 = 2 XP, level 2 = 3 XP, ..., level 5 = 6 XP.
        int expDrain = level + 1;

        for (Player player : nearbyPlayers) {
            // Drain hunger
            int currentFood = player.getFoodData().getFoodLevel();
            int newFood = Math.max(0, currentFood - (int) hungerDrain);
            player.getFoodData().setFoodLevel(newFood);

            // Drain experience
            if (player.experienceLevel > 0 || player.experienceProgress > 0) {
                player.giveExperiencePoints(-expDrain);
            }

            // Siphon particles (green/red swirl between player and entity)
            if (entity.level() instanceof ServerLevel serverLevel) {
                // Green swirl at player (being drained)
                serverLevel.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                        player.getX(), player.getY() + player.getBbHeight() * 0.5, player.getZ(),
                        3 + level,
                        0.2, 0.2, 0.2,
                        0.02);
                // Red swirl at entity (receiving)
                serverLevel.sendParticles(ParticleTypes.DAMAGE_INDICATOR,
                        entity.getX(), entity.getY() + entity.getBbHeight() * 0.5, entity.getZ(),
                        2 + level,
                        0.2, 0.2, 0.2,
                        0.02);
            }
        }
    }
}
