package com.eliteforge.ability.legendary;

import com.eliteforge.ability.Ability;
import com.eliteforge.ability.AbilityCategory;
import com.eliteforge.capability.EliteCapability;
import com.eliteforge.capability.EliteCapabilitySync;
import com.eliteforge.capability.EliteData;
import com.eliteforge.spawn.EliteEventHandler;
import com.eliteforge.util.NBTKeys;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.concurrent.ThreadLocalRandom;

/**
 * AbilityNecromancy (亡灵术) - Raises undead allies when in combat.
 * 
 * Raises (1 + floor(level/2)) zombie/skeleton allies when in combat.
 * Allies have level * 2 health and basic equipment.
 * Cooldown: (600 - level * 80) ticks.
 * Soul particles.
 */
public class AbilityNecromancy extends Ability {

    public AbilityNecromancy() {
        super(
            new ResourceLocation("eliteforge", "necromancy"),
            AbilityCategory.LEGENDARY,
            4.0f
        );
    }

    @Override
    public void onApply(LivingEntity entity, int level) {
        if (entity.level().isClientSide) return;
        entity.getPersistentData().putInt(NBTKeys.NECROMANCY_COOLDOWN, 0);
    }

    @Override
    public void onTick(LivingEntity entity, int level) {
        if (entity.level().isClientSide) return;
        if (entity.tickCount % 10 != 0) return;

        CompoundTag data = entity.getPersistentData();
        if (!data.contains(NBTKeys.NECROMANCY_COOLDOWN)) {
            onApply(entity, level);
            return;
        }

        int cooldown = data.getInt(NBTKeys.NECROMANCY_COOLDOWN);
        cooldown -= 10;

        if (cooldown <= 0) {
            // Only raise undead if in combat (has a target or was recently hurt)
            if (entity.getLastHurtByMob() != null || (entity instanceof net.minecraft.world.entity.Mob mob && mob.getTarget() != null)) {
                raiseUndead(entity, level);
                int newCooldown = Math.max(120, 600 - level * 80);
                data.putInt(NBTKeys.NECROMANCY_COOLDOWN, newCooldown);
            }
        } else {
            data.putInt(NBTKeys.NECROMANCY_COOLDOWN, cooldown);
        }

        // Ambient soul particles
        if (entity.tickCount % 20 == 0 && entity.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.SOUL,
                    entity.getX(), entity.getY() + entity.getBbHeight() * 0.5, entity.getZ(),
                    1 + level / 2,
                    entity.getBbWidth() * 0.3, entity.getBbHeight() * 0.3, entity.getBbWidth() * 0.3,
                    0.01);
        }
    }

    /**
     * Raises undead allies around the entity.
     */
    private void raiseUndead(LivingEntity entity, int level) {
        if (!(entity.level() instanceof ServerLevel serverLevel)) return;

        int allyCount = 1 + level / 2;
        float allyHealth = level * 2.0f;

        for (int i = 0; i < allyCount; i++) {
            double offsetX = (ThreadLocalRandom.current().nextDouble() - 0.5) * 4;
            double offsetZ = (ThreadLocalRandom.current().nextDouble() - 0.5) * 4;
            double spawnX = entity.getX() + offsetX;
            double spawnZ = entity.getZ() + offsetZ;

            LivingEntity ally;
            if (ThreadLocalRandom.current().nextBoolean()) {
                // Spawn Zombie.
                // In 1.20.1 mob (Level) constructors were deprecated; use EntityType.ZOMBIE.create.
                Zombie zombie = net.minecraft.world.entity.EntityType.ZOMBIE.create(serverLevel);
                if (zombie == null) continue;
                zombie.moveTo(spawnX, entity.getY(), spawnZ,
                        entity.getYRot() + (ThreadLocalRandom.current().nextFloat() - 0.5f) * 60, 0);
                zombie.setHealth(Math.min(allyHealth, zombie.getMaxHealth()));

                // Basic equipment
                zombie.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
                if (level >= 3) {
                    zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
                }
                if (level >= 5) {
                    zombie.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
                }

                ally = zombie;
            } else {
                // Spawn Skeleton.
                // In 1.20.1 the Skeleton(Level) public constructor was removed in favor
                // of EntityType.SKELETON.create(Level); use that and skip if creation fails.
                Skeleton skeleton = net.minecraft.world.entity.EntityType.SKELETON.create(serverLevel);
                if (skeleton == null) continue;
                skeleton.moveTo(spawnX, entity.getY(), spawnZ,
                        entity.getYRot() + (ThreadLocalRandom.current().nextFloat() - 0.5f) * 60, 0);
                skeleton.setHealth(Math.min(allyHealth, skeleton.getMaxHealth()));

                // Basic equipment
                skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
                if (level >= 3) {
                    skeleton.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
                }

                ally = skeleton;
            }

            // Set the ally's target to the entity's target
            if (entity instanceof net.minecraft.world.entity.Mob mob && mob.getTarget() != null) {
                if (entity instanceof net.minecraft.world.entity.Mob sourceMob) ally.setLastHurtByMob(sourceMob.getTarget());
                if (ally instanceof net.minecraft.world.entity.Mob mobAlly && entity instanceof net.minecraft.world.entity.Mob sourceMob2) mobAlly.setTarget(sourceMob2.getTarget());
            }

            serverLevel.addFreshEntity(ally);

            // Link the summon to its caster: enables the purple chain visual
            // (client-side, via synced EliteData) and the leash pull-back that
            // keeps the undead near its master (server-side, via persistent NBT).
            linkSummon(ally, entity);
        }

        // Soul particles burst
        serverLevel.sendParticles(ParticleTypes.SOUL,
                entity.getX(), entity.getY() + entity.getBbHeight() * 0.5, entity.getZ(),
                Math.min(Math.min(20 + level * 5, 50), 60),
                1.0, entity.getBbHeight() * 0.5, 1.0,
                0.1);
        serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                entity.getX(), entity.getY() + entity.getBbHeight() * 0.5, entity.getZ(),
                10 + level * 3,
                0.8, entity.getBbHeight() * 0.4, 0.8,
                0.05);
    }

    /**
     * Mark {@code summon} as a minion of {@code caster}.
     * <ul>
     *   <li>Writes {@link NBTKeys#SUMMONER_UUID} to the summon's persistent NBT
     *       for fast server-side leash checks (no capability lookup needed).</li>
     *   <li>Sets {@code summonerUUID} on the EliteData capability and broadcasts
     *       it to tracking clients so {@link com.eliteforge.render.ChainLinkRenderer}
     *       can render the purple chain.</li>
     *   <li>Prevents natural despawn so the undead persists for the fight
     *       (the leash system clears the link and re-enables despawn once the
     *       caster is gone).</li>
     *   <li>Registers the summon with {@link EliteEventHandler#trackSummon}
     *       so the leash tick processes it.</li>
     * </ul>
     * Marked {@code static} so {@link AbilityClone} can reuse the exact same
     * linking semantics for clones.
     *
     * @param summon the newly-spawned minion
     * @param caster the entity that summoned it
     */
    static void linkSummon(LivingEntity summon, LivingEntity caster) {
        if (summon == null || caster == null) return;
        java.util.UUID casterUUID = caster.getUUID();
        // Persistent NBT — fast server-side lookup for leash logic
        summon.getPersistentData().putUUID(NBTKeys.SUMMONER_UUID, casterUUID);
        // EliteData capability — synced to clients for purple chain rendering
        summon.getCapability(EliteCapability.CAPABILITY).ifPresent(cap -> {
            EliteData data = cap.getEliteData();
            data.setSummonerUUID(casterUUID);
            cap.setEliteData(data);
            EliteCapabilitySync.broadcastEliteDataUpdate(summon, data);
        });
        // Prevent natural despawn while the caster is alive
        if (summon instanceof net.minecraft.world.entity.Mob mob) {
            mob.setPersistenceRequired();
        }
        EliteEventHandler.trackSummon(summon);
    }
}
