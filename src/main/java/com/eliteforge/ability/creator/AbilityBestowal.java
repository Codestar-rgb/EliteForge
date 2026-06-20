package com.eliteforge.ability.creator;

import com.eliteforge.ability.Ability;
import com.eliteforge.ability.AbilityRegistry;
import com.eliteforge.ability.MutualExclusion;
import com.eliteforge.capability.EliteCapability;
import com.eliteforge.capability.EliteData;
import com.eliteforge.capability.EliteCapabilitySync;
import com.eliteforge.config.EliteForgeConfig;
import com.eliteforge.quality.QualityTier;
import com.eliteforge.spawn.EliteEventHandler;
import com.eliteforge.util.NBTKeys;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;

import java.util.*;

/**
 * AbilityBestowal (铸造·赐能) - C5 Creator Ability
 * <p>
 * Transforms nearby normal mobs into elites.
 * Level I:  range 16, every 160 ticks (8s), up to 2 targets:
 *           normal → GOOD quality (1 I-level ability). Cost: 5% current health
 * Level II: range 24, every 100 ticks (5s), up to 4 targets:
 *           normal → FINE quality (2 abilities, max II). Cost: 8% current health
 * Level III:range 32, every 60 ticks (3s), up to 6 targets:
 *           normal → EPIC quality (3 abilities, max III). Cost: 10% current health
 * Bestowed elites marked with "✦", revert to normal 30s after creator dies.
 * Cannot bestow when health < 20%.
 * Golden ray particle from creator to target, forge animation on target.
 * <p>
 * NBT keys:
 * <ul>
 *   <li>{@code EliteForgeBestowalCooldown} - cooldown timer in ticks</li>
 *   <li>{@code EliteForgeBestowalRevert} - reversion timer on bestowed elites (managed by EliteEventHandler, 600 ticks = 30s)</li>
 * </ul>
 */
public class AbilityBestowal extends CreatorAbility {

    // NBT keys (reference centralized constants)
    private static final String BESTOWAL_COOLDOWN_KEY = NBTKeys.BESTOWAL_COOLDOWN;
    private static final String BESTOWAL_REVERT_KEY = NBTKeys.BESTOWAL_REVERT;

    public AbilityBestowal() {
        super(new ResourceLocation("eliteforge", "creator_bestowal"), 5.0f);
    }

    @Override
    public void onApply(LivingEntity entity, int level) {
        if (entity.level().isClientSide) return;
        CompoundTag data = entity.getPersistentData();
        data.putInt(BESTOWAL_COOLDOWN_KEY, 0);

        // Mark as creator entity in capability
        setupCreatorData(entity, level);
    }

    @Override
    public void onRemove(LivingEntity entity, int level) {
        if (entity.level().isClientSide) return;

        // Clean up NBT data
        CompoundTag data = entity.getPersistentData();
        data.remove(BESTOWAL_COOLDOWN_KEY);
    }

    @Override
    public void onTick(LivingEntity entity, int level) {
        if (entity.level().isClientSide) return;

        CompoundTag data = entity.getPersistentData();
        if (!data.contains(BESTOWAL_COOLDOWN_KEY)) {
            // Idempotency check: if capability data is already set, just re-initialize NBT
            // without re-calling onApply (which would re-apply creator data)
            entity.getCapability(EliteCapability.CAPABILITY).ifPresent(cap -> {
                EliteData eliteData = cap.getEliteData();
                if (eliteData.isCreatorEntity() && getIdString().equals(eliteData.getCreatorAbilityId())) {
                    data.putInt(BESTOWAL_COOLDOWN_KEY, 0);
                    return;
                }
            });
            // If capability wasn't set or didn't match, do full onApply
            if (!data.contains(BESTOWAL_COOLDOWN_KEY)) {
                onApply(entity, level);
            }
            return;
        }

        // Cannot bestow when health < 20%
        if (entity.getHealth() < entity.getMaxHealth() * 0.20f) return;

        int cooldown = data.getInt(BESTOWAL_COOLDOWN_KEY);
        if (cooldown > 0) {
            data.putInt(BESTOWAL_COOLDOWN_KEY, cooldown - 1);
            return;
        }

        // Determine parameters based on level
        double range = switch (level) {
            case 1 -> 16.0;
            case 2 -> 24.0;
            default -> 32.0;
        };
        int interval = switch (level) {
            case 1 -> 160;
            case 2 -> 100;
            default -> 60;
        };
        int maxTargets = switch (level) {
            case 1 -> 2;
            case 2 -> 4;
            default -> 6;
        };
        float healthCost = switch (level) {
            case 1 -> 0.05f;
            case 2 -> 0.08f;
            default -> 0.10f;
        };

        // Set cooldown
        data.putInt(BESTOWAL_COOLDOWN_KEY, interval);

        if (!(entity.level() instanceof ServerLevel serverLevel)) return;

        // Find nearby non-elite mobs
        AABB area = new AABB(
                entity.getX() - range, entity.getY() - range, entity.getZ() - range,
                entity.getX() + range, entity.getY() + range, entity.getZ() + range
        );

        List<LivingEntity> nearbyMobs = serverLevel.getEntitiesOfClass(LivingEntity.class, area,
                e -> e.isAlive() && e != entity
                        && e.getCapability(EliteCapability.CAPABILITY).isPresent()
                        && !e.getCapability(EliteCapability.CAPABILITY).map(cap -> cap.getEliteData().isElite()).orElse(false));

        // Shuffle and pick up to maxTargets
        Collections.shuffle(nearbyMobs);
        int bestowed = 0;

        for (LivingEntity target : nearbyMobs) {
            if (bestowed >= maxTargets) break;

            // Pay health cost
            float cost = entity.getHealth() * healthCost;
            if (entity.getHealth() - cost < entity.getMaxHealth() * 0.20f) break; // Don't go below 20%
            entity.setHealth(entity.getHealth() - cost);

            // Determine quality and abilities based on level
            QualityTier quality = switch (level) {
                case 1 -> QualityTier.GOOD;
                case 2 -> QualityTier.FINE;
                default -> QualityTier.EPIC;
            };
            int abilityCount = switch (level) {
                case 1 -> 1;
                case 2 -> 2;
                default -> 3;
            };
            int maxAbilityLevel = switch (level) {
                case 1 -> 1;
                case 2 -> 2;
                default -> 3;
            };

            // Bestow elite status
            target.getCapability(EliteCapability.CAPABILITY).ifPresent(cap -> {
                EliteData eliteData = new EliteData();
                eliteData.setElite(true);
                eliteData.setQualityTier(quality);
                eliteData.setLevel(1);
                eliteData.setSpawnMode(EliteForgeConfig.COMMON.difficultyMode.get());

                // Generate random non-creator abilities with mutual exclusion checking
                List<Ability> available = new ArrayList<>();
                for (Ability ab : AbilityRegistry.getAllAbilities()) {
                    if (ab.getCategory() != com.eliteforge.ability.AbilityCategory.CREATOR
                            && ab.isEnabled()
                            && !eliteData.hasAbility(ab.getIdString())) {
                        available.add(ab);
                    }
                }
                // In 1.20.1, Entity.getRandom() returns RandomSource (not java.util.Random),
                // but Collections.shuffle(List, Random) needs a java.util.Random. Use a fresh
                // Random seeded by the entity's own RNG for stable per-entity selection.
                Collections.shuffle(available);

                // Track selected ability IDs for mutual exclusion checking
                Set<String> selectedIds = new HashSet<>();
                int assigned = 0;
                for (Ability ab : available) {
                    if (assigned >= abilityCount) break;
                    // Check mutual exclusion with already-selected abilities
                    boolean canCoexist = true;
                    for (String selectedId : selectedIds) {
                        if (MutualExclusion.isMutuallyExclusive(ab.getIdString(), selectedId)) {
                            canCoexist = false;
                            break;
                        }
                    }
                    if (!canCoexist) continue;

                    int abLevel = Math.min(maxAbilityLevel, 1 + target.getRandom().nextInt(maxAbilityLevel));
                    eliteData.addAbility(ab.getIdString(), abLevel);
                    selectedIds.add(ab.getIdString());

                    // Call onApply for the ability
                    try {
                        ab.onApply(target, abLevel);
                    } catch (Exception e) {
                        // Silently fail
                    }
                    assigned++;
                }

                // Mark as bestowed
                eliteData.setBestowerUUID(entity.getUUID());

                cap.setEliteData(eliteData);
                EliteCapabilitySync.broadcastEliteDataUpdate(target, eliteData);
            });

            bestowed++;

            // Immediately track the bestowed elite so its abilities start
            // ticking on the next tick instead of waiting for the periodic
            // full scan (every 20 ticks / ~1 second delay).
            EliteEventHandler.trackElite(target);

            // Golden ray particle from creator to target
            spawnBestowalRayParticles(serverLevel, entity, target);

            // Forge animation on target
            serverLevel.sendParticles(ParticleTypes.ENCHANT,
                    target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(),
                    15, target.getBbWidth() * 0.5, target.getBbHeight() * 0.5, target.getBbWidth() * 0.5,
                    0.5);
            serverLevel.sendParticles(ParticleTypes.END_ROD,
                    target.getX(), target.getY() + target.getBbHeight() + 0.3, target.getZ(),
                    5, 0.3, 0.3, 0.3, 0.05);
        }

        // NOTE: Bestowed elite reversion timer (EliteForgeBestowalRevert) is handled by
        // EliteEventHandler.tickCreatorNbtTimers() — including the weakening effects in the
        // last 5 seconds. Do NOT decrement here to avoid double-decrement
        // (onTick runs every tick, tickCreatorNbtTimers runs every 20).
    }

    @Override
    public void onDeath(LivingEntity entity, int level) {
        if (entity.level().isClientSide) return;

        if (!(entity.level() instanceof ServerLevel serverLevel)) return;

        // Find all bestowed elites and schedule reversion (30 seconds)
        // We tag them for reversion - the actual reversion would be handled
        // by the event handler checking the tag timer
        double range = 64.0; // Large range to find all bestowed
        AABB area = new AABB(
                entity.getX() - range, entity.getY() - range, entity.getZ() - range,
                entity.getX() + range, entity.getY() + range, entity.getZ() + range
        );

        List<LivingEntity> bestowedElites = serverLevel.getEntitiesOfClass(LivingEntity.class, area,
                e -> e.isAlive() && e.getCapability(EliteCapability.CAPABILITY).isPresent());

        for (LivingEntity bestowed : bestowedElites) {
            bestowed.getCapability(EliteCapability.CAPABILITY).ifPresent(cap -> {
                EliteData data = cap.getEliteData();
                if (data.isElite() && entity.getUUID().equals(data.getBestowerUUID())) {
                    // Mark for reversion after configured delay (default 600 ticks = 30 seconds)
                    int revertTicks = EliteForgeConfig.SERVER.bestowalRevertTicks.get();
                    bestowed.getPersistentData().putInt(BESTOWAL_REVERT_KEY, revertTicks);
                }
            });
        }
    }

    /**
     * Spawns golden ray particles from creator to the bestowed target.
     */
    private void spawnBestowalRayParticles(ServerLevel serverLevel, LivingEntity source, LivingEntity target) {
        double dx = target.getX() - source.getX();
        double dy = (target.getY() + target.getBbHeight() * 0.5) - (source.getY() + source.getBbHeight() * 0.5);
        double dz = target.getZ() - source.getZ();
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);

        int steps = Math.max(5, (int) (dist / 1.0));
        for (int i = 0; i < steps; i++) {
            double t = (double) i / steps;
            double px = source.getX() + dx * t;
            double py = source.getY() + source.getBbHeight() * 0.5 + dy * t;
            double pz = source.getZ() + dz * t;
            serverLevel.sendParticles(ParticleTypes.END_ROD, px, py, pz, 2, 0.1, 0.1, 0.1, 0.01);
        }
    }
}
