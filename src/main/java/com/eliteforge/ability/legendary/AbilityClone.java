package com.eliteforge.ability.legendary;

import com.eliteforge.ability.Ability;
import com.eliteforge.ability.AbilityCategory;
import com.eliteforge.ability.AbilityRegistry;
import com.eliteforge.EliteForge;
import com.eliteforge.capability.EliteCapability;
import com.eliteforge.capability.EliteData;
import com.eliteforge.capability.EliteCapabilitySync;
import com.eliteforge.util.NBTKeys;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;

import java.util.Map;

/**
 * AbilityClone (分身) - Spawns clones when health drops below 50%.
 * 
 * Spawns (1 + floor(level/2)) clones when health drops below 50%.
 * Clones have 25% of original health, same abilities at half level.
 * Only spawns once per fight.
 * Smoke burst particles.
 */
public class AbilityClone extends Ability {

    public AbilityClone() {
        super(
            new ResourceLocation("eliteforge", "clone"),
            AbilityCategory.LEGENDARY,
            4.0f
        );
    }

    @Override
    public void onApply(LivingEntity entity, int level) {
        if (entity.level().isClientSide) return;
        entity.getPersistentData().putBoolean(NBTKeys.CLONE_SPAWNED, false);
    }

    @Override
    public void onTick(LivingEntity entity, int level) {
        // Clone spawning is triggered by onHurt
    }

    @Override
    public void onHurt(LivingEntity entity, float damage, int level) {
        if (entity.level().isClientSide) return;

        // Check if already spawned clones this fight
        CompoundTag data = entity.getPersistentData();
        if (data.getBoolean(NBTKeys.CLONE_SPAWNED)) return;

        // Check if health dropped below 50%
        float healthAfterDamage = entity.getHealth() - damage;
        if (healthAfterDamage > entity.getMaxHealth() * 0.5f) return;

        // Mark as spawned
        data.putBoolean(NBTKeys.CLONE_SPAWNED, true);

        int cloneCount = 1 + level / 2;
        float cloneHealth = entity.getMaxHealth() * 0.25f;

        if (!(entity.level() instanceof ServerLevel serverLevel)) return;

        // Get the entity's abilities from the capability system
        entity.getCapability(EliteCapability.CAPABILITY).ifPresent(sourceCap -> {
            EliteData sourceData = sourceCap.getEliteData();

            for (int i = 0; i < cloneCount; i++) {
                // Spawn a clone of the same entity type
                try {
                    var cloneEntityRaw = entity.getType().create(serverLevel);
                    if (!(cloneEntityRaw instanceof LivingEntity cloneEntity)) continue;

                    // Position clone nearby
                    double offsetX = (serverLevel.random.nextDouble() - 0.5) * 4;
                    double offsetZ = (serverLevel.random.nextDouble() - 0.5) * 4;
                    cloneEntity.moveTo(entity.getX() + offsetX, entity.getY(), entity.getZ() + offsetZ,
                            entity.getYRot(), entity.getXRot());

                    // Set clone health to 25% of original.
                    // setHealth is defined on LivingEntity (not Entity), so the cast above is required.
                    cloneEntity.setHealth(cloneHealth);

                    // Apply abilities at half level via the capability system
                    cloneEntity.getCapability(EliteCapability.CAPABILITY).ifPresent(cloneCap -> {
                        EliteData cloneEliteData = new EliteData();
                        cloneEliteData.setElite(true);
                        cloneEliteData.setQualityTier(sourceData.getQualityTier());
                        cloneEliteData.setLevel(Math.max(1, sourceData.getLevel() - 1));
                        // Link this clone back to its source so the purple chain
                        // renders and the leash keeps the clone near the original.
                        cloneEliteData.setSummonerUUID(entity.getUUID());

                        // Copy abilities at half level, excluding:
                        //  - Clone itself (prevent infinite cloning)
                        //  - Creator-tier abilities (would mark the clone as a creator entity
                        //    and grant creator-tier power — design bug, see AUDIT-2 M3)
                        for (Map.Entry<String, Integer> entry : sourceData.getAbilities().entrySet()) {
                            if (entry.getKey().equals(this.getIdString())) continue;
                            Ability candidate = AbilityRegistry.getAbility(entry.getKey());
                            if (candidate != null && candidate.getCategory() == AbilityCategory.CREATOR) continue;
                            int halfLevel = Math.max(1, entry.getValue() / 2);
                            cloneEliteData.addAbility(entry.getKey(), halfLevel);

                            // Call onApply for each ability on the clone
                            if (candidate != null) {
                                try {
                                    candidate.onApply(cloneEntity, halfLevel);
                                } catch (Exception e) {
                                    EliteForge.LOGGER.error("Error applying clone ability {}: {}", entry.getKey(), e.getMessage());
                                }
                            }
                        }

                        cloneCap.setEliteData(cloneEliteData);
                    });

                    // Add clone to world
                    serverLevel.addFreshEntity(cloneEntity);

                    // Sync clone data to tracking clients
                    cloneEntity.getCapability(EliteCapability.CAPABILITY).ifPresent(cloneCap -> {
                        EliteCapabilitySync.broadcastEliteDataUpdate(cloneEntity, cloneCap.getEliteData());
                    });

                    // Mirror the summoner link into persistent NBT (for fast server-side
                    // leash checks) and register the clone with both the elite tracker
                    // (so its copied abilities tick) and the summon tracker (so the leash
                    // pull-back keeps it near the original).
                    cloneEntity.getPersistentData().putUUID(NBTKeys.SUMMONER_UUID, entity.getUUID());
                    if (cloneEntity instanceof net.minecraft.world.entity.Mob mobClone) {
                        mobClone.setPersistenceRequired();
                    }
                    com.eliteforge.spawn.EliteEventHandler.trackElite(cloneEntity);
                    com.eliteforge.spawn.EliteEventHandler.trackSummon(cloneEntity);

                } catch (Exception e) {
                    EliteForge.LOGGER.error("Error spawning clone: {}", e.getMessage());
                }
            }
        });

        // Smoke burst particles
        serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE,
                entity.getX(), entity.getY() + entity.getBbHeight() * 0.5, entity.getZ(),
                Math.min(30 + level * 5, 60),
                1.0, entity.getBbHeight() * 0.5, 1.0,
                0.1);
        serverLevel.sendParticles(ParticleTypes.POOF,
                entity.getX(), entity.getY() + entity.getBbHeight() * 0.5, entity.getZ(),
                20 + level * 3,
                0.8, entity.getBbHeight() * 0.4, 0.8,
                0.05);
    }

    @Override
    public void onDeath(LivingEntity entity, int level) {
        // Reset clone flag on death (for next fight, if revived)
        entity.getPersistentData().putBoolean(NBTKeys.CLONE_SPAWNED, false);
    }
}
