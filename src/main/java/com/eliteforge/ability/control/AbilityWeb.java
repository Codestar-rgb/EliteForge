package com.eliteforge.ability.control;

import com.eliteforge.ability.Ability;
import com.eliteforge.ability.AbilityCategory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;

/**
 * AbilityWeb (蛛网) - Places cobweb at target's feet on attack.
 * 
 * On attack: places cobweb at target's feet for (3 + level * 2) seconds.
 * String particle effect.
 */
public class AbilityWeb extends Ability {

    public AbilityWeb() {
        super(
            new ResourceLocation("eliteforge", "web"),
            AbilityCategory.CONTROL,
            1.5f
        );
    }

    @Override
    public void onTick(LivingEntity entity, int level) {
        if (entity.level().isClientSide) return;
        if (entity.tickCount % 30 != 0) return;

        if (entity.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.CLOUD,
                    entity.getX(), entity.getY() + entity.getBbHeight() * 0.5, entity.getZ(),
                    1 + level / 2,
                    entity.getBbWidth() * 0.3, entity.getBbHeight() * 0.3, entity.getBbWidth() * 0.3,
                    0.01);
        }
    }

    @Override
    public void onAttack(LivingEntity attacker, LivingEntity target, float damage, int level) {
        if (attacker.level().isClientSide) return;
        if (target.level().isClientSide) return;

        BlockPos targetPos = target.blockPosition();
        int durationSeconds = 3 + level * 2;

        // Place cobweb at target's feet
        if (target.level().getBlockState(targetPos).isAir()) {
            target.level().setBlock(targetPos, Blocks.COBWEB.defaultBlockState(), 3);

            // Schedule cobweb removal
            if (target.level() instanceof ServerLevel serverLevel) {
                serverLevel.getServer().tell(new net.minecraft.server.TickTask(
                        serverLevel.getServer().getTickCount() + durationSeconds * 20,
                        () -> {
                            if (serverLevel.getBlockState(targetPos).is(Blocks.COBWEB)) {
                                serverLevel.setBlock(targetPos, Blocks.AIR.defaultBlockState(), 3);
                            }
                        }
                ));
            }
        }

        // String particle effect
        if (target.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.CLOUD,
                    target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(),
                    8 + level * 3,
                    0.3, 0.3, 0.3,
                    0.02);
        }
    }
}
