package com.eliteforge.block;

import com.eliteforge.blockentity.EliteSpawnerBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;
import java.util.List;

/**
 * EliteSpawnerBlock (精英刷怪笼) - Spawns elite mobs with configurable difficulty.
 * Right-click: opens GUI to configure target difficulty, spawn mode, spawn interval, entity type.
 * Emits redstone signal when spawning (pulse).
 * Particle effects when active (flame + soul).
 * Light level: 7, Hardness: 5.0, Resistance: 8.0, Requires pickaxe.
 */
public class EliteSpawnerBlock extends BaseEntityBlock {

    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");
    public static final IntegerProperty DIFFICULTY = IntegerProperty.create("difficulty", 1, 5);

    public EliteSpawnerBlock() {
        super(BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_BLACK)
            .strength(5.0f, 8.0f)
            .requiresCorrectToolForDrops()
            .lightLevel((state) -> 7)
            .sound(net.minecraft.world.level.block.SoundType.METAL)
        );
        this.registerDefaultState(this.stateDefinition.any()
            .setValue(ACTIVE, false)
            .setValue(DIFFICULTY, 1));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ACTIVE, DIFFICULTY);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new EliteSpawnerBlockEntity(com.eliteforge.init.ModBlockEntities.ELITE_SPAWNER_BE.get(), pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (!level.isClientSide() && type == com.eliteforge.init.ModBlockEntities.ELITE_SPAWNER_BE.get()) {
            return (lvl, pos, st, be) -> {
                if (be instanceof EliteSpawnerBlockEntity spawner) {
                    spawner.tick();
                }
            };
        }
        return null;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof EliteSpawnerBlockEntity spawner) {
            sendConfigMenu(player, spawner);
        }

        return InteractionResult.CONSUME;
    }

    private void sendConfigMenu(Player player, EliteSpawnerBlockEntity spawner) {
        player.displayClientMessage(
            Component.translatable("message.eliteforge.elite_spawner.menu_title")
                .withStyle(ChatFormatting.GOLD),
            false
        );
        player.displayClientMessage(
            Component.translatable("message.eliteforge.elite_spawner.difficulty", spawner.getDifficulty())
                .withStyle(ChatFormatting.WHITE),
            false
        );
        player.displayClientMessage(
            Component.translatable("message.eliteforge.elite_spawner.mode", spawner.getSpawnMode())
                .withStyle(ChatFormatting.WHITE),
            false
        );
        player.displayClientMessage(
            Component.translatable("message.eliteforge.elite_spawner.interval", spawner.getSpawnInterval())
                .withStyle(ChatFormatting.WHITE),
            false
        );
        player.displayClientMessage(
            Component.translatable("message.eliteforge.elite_spawner.entity", spawner.getEntityType())
                .withStyle(ChatFormatting.WHITE),
            false
        );
        player.displayClientMessage(
            Component.translatable("message.eliteforge.elite_spawner.configure_hint")
                .withStyle(ChatFormatting.AQUA),
            false
        );
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(ACTIVE)) {
            double x = pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.5;
            double y = pos.getY() + 1.0;
            double z = pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.5;
            level.addParticle(ParticleTypes.FLAME, x, y, z, 0.0, 0.05, 0.0);

            if (random.nextInt(3) == 0) {
                level.addParticle(ParticleTypes.SOUL, x, y + 0.5, z,
                    (random.nextDouble() - 0.5) * 0.1, 0.1, (random.nextDouble() - 0.5) * 0.1);
            }
        }
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable BlockGetter level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.eliteforge.elite_spawner.description")
            .withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("tooltip.eliteforge.elite_spawner.right_click")
            .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.eliteforge.elite_spawner.redstone")
            .withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    public int getSignal(BlockState state, BlockGetter level, BlockPos pos, net.minecraft.core.Direction direction) {
        return state.getValue(ACTIVE) ? 15 : 0;
    }

    @Override
    public boolean isSignalSource(BlockState state) {
        return true;
    }
}
