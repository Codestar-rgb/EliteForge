package com.eliteforge.render;

import com.eliteforge.ability.Ability;
import com.eliteforge.ability.AbilityCategory;
import com.eliteforge.ability.AbilityRegistry;
import com.eliteforge.capability.EliteCapability;
import com.eliteforge.capability.EliteData;
import com.eliteforge.config.EliteForgeConfig;
import com.eliteforge.quality.QualityTier;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Particle renderer for elite mob visual effects.
 * <p>
 * Provides three categories of particle effects:
 * <ul>
 *   <li><b>Ambient particles</b> — spawned client-side around living elites,
 *       type determined by the highest ability category present.</li>
 *   <li><b>Death particles</b> — spawned server-side when an elite dies,
 *       type determined by quality tier.</li>
 *   <li><b>Spawn particles</b> — spawned server-side when an elite is created,
 *       a soul-rising + flame-burst effect.</li>
 * </ul>
 * <p>
 * <b>Round 1 fix (2025-06-17):</b> This class previously had multiple compilation
 * errors that prevented the entire mod from building:
 * <ul>
 *   <li>Referenced non-existent {@code EliteForgeConfig.CLIENT.renderDistance}
 *       field (now uses {@code iconRenderDistance}).</li>
 *   <li>Called {@code cap.getLevel()}, {@code cap.getQualityTier()},
 *       {@code cap.getAbilities()} on {@link EliteCapability} which only exposes
 *       {@code getEliteData()}. Now takes {@link EliteData} directly and calls
 *       {@code data.getLevel()} etc.</li>
 *   <li>Variable name collision: {@code Level level} and {@code int level} in
 *       the same scope. Renamed the local int to {@code eliteLevel}.</li>
 *   <li>{@code getDeathParticle} switch used ChatFormatting constant names
 *       (WHITE/GREEN/BLUE/PURPLE/GOLD) instead of QualityTier enum names
 *       (NORMAL/GOOD/FINE/EPIC/LEGENDARY). Now fixed.</li>
 *   <li>{@code getHighestCategory} iterated {@code Map.Entry<Ability, Integer>}
 *       but {@code EliteData.getAbilities()} returns {@code Map<String, Integer>}
 *       (ability ID strings, not Ability objects). Now looks up each ID via
 *       {@link AbilityRegistry#getAbility(String)}.</li>
 *   <li>Used {@code new Random()} instead of the project-standard
 *       {@link ThreadLocalRandom}.</li>
 * </ul>
 */
public class EliteParticleRenderer {

    private static final int AMBIENT_PARTICLE_RATE_BASE = 120; // ticks between particles at level 1
    private static final int AMBIENT_PARTICLE_RATE_MIN = 30;   // minimum ticks between particles
    private static final double AMBIENT_RADIUS = 1.5;
    private static final int DEATH_PARTICLE_COUNT = 12;
    private static final int SPAWN_PARTICLE_COUNT = 8;

    /**
     * Spawns ambient particles around an elite entity. Called on the client side
     * during render events.
     *
     * @param entity the elite entity
     * @param data   the elite's EliteData (level, abilities, quality)
     */
    public static void spawnAmbientParticles(LivingEntity entity, EliteData data) {
        if (!EliteForgeConfig.CLIENT.showAbilityParticles.get()) {
            return;
        }
        if (com.eliteforge.config.EliteForgeConfig.SERVER.maxAmbientParticles.get() <= 0) {
            return;
        }

        Level level = entity.level();
        if (!level.isClientSide) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }

        double distanceSq = mc.player.distanceToSqr(entity);
        // Round 1 fix: use iconRenderDistance (the actual config field) instead of
        // the non-existent renderDistance field.
        double renderDist = EliteForgeConfig.CLIENT.iconRenderDistance.get();
        if (distanceSq > renderDist * renderDist) {
            return;
        }

        // Particle rate increases with level
        // Round 1 fix: renamed to eliteLevel to avoid collision with `Level level`.
        int eliteLevel = data.getLevel();
        int rate = Math.max(AMBIENT_PARTICLE_RATE_MIN, AMBIENT_PARTICLE_RATE_BASE - (eliteLevel * 4));

        // Only spawn on appropriate ticks
        if (entity.tickCount % rate != 0) {
            return;
        }

        AbilityCategory highestCategory = getHighestCategory(data);
        ParticleOptions particleType = getAmbientParticle(highestCategory);

        if (particleType != null) {
            ThreadLocalRandom rng = ThreadLocalRandom.current();
            double x = entity.getX() + (rng.nextDouble() - 0.5) * AMBIENT_RADIUS * 2;
            double y = entity.getY() + rng.nextDouble() * entity.getBbHeight();
            double z = entity.getZ() + (rng.nextDouble() - 0.5) * AMBIENT_RADIUS * 2;

            level.addParticle(particleType, x, y, z,
                    (rng.nextDouble() - 0.5) * 0.05,
                    rng.nextDouble() * 0.05,
                    (rng.nextDouble() - 0.5) * 0.05);
        }
    }

    /**
     * Spawns death particles when an elite mob dies. Called on the server side.
     *
     * @param level  the server level
     * @param entity the elite entity that died
     * @param data   the elite's EliteData (for quality tier)
     */
    public static void spawnDeathParticles(ServerLevel level, LivingEntity entity, EliteData data) {
        if (level == null || entity == null || data == null) {
            return;
        }

        QualityTier tier = data.getQualityTier();
        ParticleOptions particleType = getDeathParticle(tier);

        if (particleType != null) {
            ThreadLocalRandom rng = ThreadLocalRandom.current();
            double x = entity.getX();
            double y = entity.getY() + entity.getBbHeight() / 2.0;
            double z = entity.getZ();

            // Explosion of colored particles
            for (int i = 0; i < DEATH_PARTICLE_COUNT; i++) {
                double offsetX = (rng.nextDouble() - 0.5) * 2.0;
                double offsetY = (rng.nextDouble() - 0.5) * 2.0;
                double offsetZ = (rng.nextDouble() - 0.5) * 2.0;

                double speedX = (rng.nextDouble() - 0.5) * 0.3;
                double speedY = rng.nextDouble() * 0.2 + 0.1;
                double speedZ = (rng.nextDouble() - 0.5) * 0.3;

                level.sendParticles(particleType,
                        x + offsetX, y + offsetY, z + offsetZ,
                        1, speedX, speedY, speedZ, 0.1);
            }

            // Additional flash effect
            level.sendParticles(ParticleTypes.FLASH,
                    x, y, z,
                    1, 0, 0, 0, 0);
        }
    }

    /**
     * Spawns particles when an elite mob spawns. Called on the server side.
     *
     * @param level  the server level
     * @param entity the elite entity that spawned
     */
    public static void spawnSpawnParticles(ServerLevel level, LivingEntity entity) {
        if (level == null || entity == null) {
            return;
        }

        ThreadLocalRandom rng = ThreadLocalRandom.current();
        double x = entity.getX();
        double y = entity.getY();
        double z = entity.getZ();

        // Soul particles rising from ground
        for (int i = 0; i < SPAWN_PARTICLE_COUNT; i++) {
            double offsetX = (rng.nextDouble() - 0.5) * 1.5;
            double offsetZ = (rng.nextDouble() - 0.5) * 1.5;

            level.sendParticles(ParticleTypes.SOUL,
                    x + offsetX, y, z + offsetZ,
                    1, 0, 0.15 + rng.nextDouble() * 0.1, 0, 0.02);
        }

        // Flame burst effect
        for (int i = 0; i < 15; i++) {
            double offsetX = (rng.nextDouble() - 0.5) * 1.0;
            double offsetZ = (rng.nextDouble() - 0.5) * 1.0;

            level.sendParticles(ParticleTypes.FLAME,
                    x + offsetX, y + 0.5, z + offsetZ,
                    1, (rng.nextDouble() - 0.5) * 0.1, 0.1, (rng.nextDouble() - 0.5) * 0.1, 0.02);
        }

        // Flash effect
        level.sendParticles(ParticleTypes.FLASH,
                x, y + entity.getBbHeight() / 2.0, z,
                1, 0, 0, 0, 0);
    }

    /**
     * Gets the ambient particle type based on the highest ability category.
     * Creator-tier elites get a special flame particle.
     *
     * @param category the highest ability category, or null
     * @return the particle type to spawn
     */
    private static ParticleOptions getAmbientParticle(AbilityCategory category) {
        if (category == null) {
            return ParticleTypes.FLAME;
        }
        // Round 1 fix: use modern switch expression (consistent with rest of codebase)
        return switch (category) {
            case ATTACK -> ParticleTypes.FLAME;
            case DEFENSE -> ParticleTypes.ENCHANT;
            case CONTROL -> ParticleTypes.DRAGON_BREATH;
            case LEGENDARY -> ParticleTypes.END_ROD;
            case CREATOR -> ParticleTypes.SOUL_FIRE_FLAME;
        };
    }

    /**
     * Gets the death particle type based on quality tier.
     * <p>
     * Round 1 fix: switch case labels were incorrectly using ChatFormatting
     * constant names (WHITE/GREEN/BLUE/PURPLE/GOLD) instead of the actual
     * QualityTier enum names (NORMAL/GOOD/FINE/EPIC/LEGENDARY/MYTHIC).
     *
     * @param tier the quality tier, or null
     * @return the particle type to spawn on death
     */
    private static ParticleOptions getDeathParticle(QualityTier tier) {
        if (tier == null) {
            return ParticleTypes.EXPLOSION;
        }
        return switch (tier) {
            case NORMAL -> ParticleTypes.CLOUD;
            case GOOD -> ParticleTypes.HAPPY_VILLAGER;
            case FINE -> ParticleTypes.ENCHANT;
            case EPIC -> ParticleTypes.DRAGON_BREATH;
            case LEGENDARY -> ParticleTypes.TOTEM_OF_UNDYING;
            case MYTHIC -> ParticleTypes.FLASH; // Creator-tier: dramatic flash
        };
    }

    /**
     * Determines the highest category from the elite's abilities.
     * <p>
     * Round 1 fix: previously iterated {@code Map.Entry<Ability, Integer>} but
     * {@link EliteData#getAbilities()} returns {@code Map<String, Integer>}
     * (ability ID strings). Now looks up each ID via
     * {@link AbilityRegistry#getAbility(String)} to resolve the Ability object
     * and its category.
     *
     * @param data the elite's data
     * @return the highest ability category, or ATTACK as default
     */
    private static AbilityCategory getHighestCategory(EliteData data) {
        AbilityCategory highest = null;
        int highestOrdinal = -1;

        Map<String, Integer> abilities = data.getAbilities();
        for (String abilityId : abilities.keySet()) {
            Ability ability = AbilityRegistry.getAbility(abilityId);
            if (ability == null) {
                continue;
            }
            AbilityCategory cat = ability.getCategory();
            if (cat != null && cat.ordinal() > highestOrdinal) {
                highestOrdinal = cat.ordinal();
                highest = cat;
            }
        }

        return highest != null ? highest : AbilityCategory.ATTACK;
    }
}
