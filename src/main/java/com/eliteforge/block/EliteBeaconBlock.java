package com.eliteforge.block;

import com.eliteforge.blockentity.EliteBeaconBlockEntity;
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
 * EliteBeaconBlock (精英信标) - Like vanilla beacon but for elite mobs.
 * Requires Heat Collector within 8 blocks to power.
 * When powered, provides one of:
 *   Level I: Suppress elite spawns in 16-block radius
 *   Level II: Reduce elite level by 1 in 32-block radius
 *   Level III: Prevent all elite spawns in 48-block radius
 * Right-click: open GUI to select suppression mode.
 * Beam color: red (when active).
 * Consumes 1 heat unit per 20 ticks from nearby Heat Collector.
 * Hardness: 3.0, Light level: 15 when active.
 */
public class EliteBeaconBlock extends BaseEntityBlock {

    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");
    public static final IntegerProperty SUPPRESSION_MODE = IntegerProperty.create("suppression_mode", 0, 2);

    public EliteBeaconBlock() {
        super(BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_RED)
            .strength(3.0f)
            .sound(net.minecraft.world.level.block.SoundType.GLASS)
            .lightLevel((state) -> state.getValue(ACTIVE) ? 15 : 0)
        );
        this.registerDefaultState(this.stateDefinition.any()
            .setValue(ACTIVE, false)
            .setValue(SUPPRESSION_MODE, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ACTIVE, SUPPRESSION_MODE);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new EliteBeaconBlockEntity(com.eliteforge.init.ModBlockEntities.ELITE_BEACON_BE.get(), pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (!level.isClientSide() && type == com.eliteforge.init.ModBlockEntities.ELITE_BEACON_BE.get()) {
            return (lvl, pos, st, be) -> {
                if (be instanceof EliteBeaconBlockEntity beacon) {
                    beacon.tick();
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

        // v0.2.0: Elite Beacon now functions as Accessory Upgrade Station
        ItemStack mainHand = player.getMainHandItem();
        ItemStack offHand = player.getOffhandItem();

        // v0.7.0: If both hands hold accessories, try fusion.
        if (mainHand.getItem() instanceof com.eliteforge.accessory.EliteAccessory
                && offHand.getItem() instanceof com.eliteforge.accessory.EliteAccessory) {
            boolean success = com.eliteforge.accessory.AccessoryUpgrader.tryFusion(mainHand, offHand, player);
            if (success) {
                player.displayClientMessage(
                    net.minecraft.network.chat.Component.translatable("message.eliteforge.fusion_success")
                        .withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE), true);
            } else {
                player.displayClientMessage(
                    net.minecraft.network.chat.Component.translatable("message.eliteforge.fusion_failed")
                        .withStyle(net.minecraft.ChatFormatting.RED), true);
            }
            return InteractionResult.CONSUME;
        }

        // Standard accessory upgrade (single accessory in main hand)
        if (mainHand.getItem() instanceof com.eliteforge.accessory.EliteAccessory) {
            boolean success = com.eliteforge.accessory.AccessoryUpgrader.tryUpgrade(mainHand, player);
            if (success) {
                player.displayClientMessage(
                    net.minecraft.network.chat.Component.translatable("message.eliteforge.upgrade_success")
                        .withStyle(net.minecraft.ChatFormatting.GOLD), true);
            } else {
                player.displayClientMessage(
                    net.minecraft.network.chat.Component.translatable("message.eliteforge.upgrade_failed")
                        .withStyle(net.minecraft.ChatFormatting.RED), true);
            }
            return InteractionResult.CONSUME;
        }

        // v0.7.5: Dismantle — Dismantle Kit in main hand + accessory in off-hand
        if (mainHand.getItem() == com.eliteforge.init.ModItems.DISMANTLE_KIT.get()
                && offHand.getItem() instanceof com.eliteforge.accessory.EliteAccessory) {
            boolean success = com.eliteforge.accessory.AccessoryUpgrader.tryDismantle(offHand, player);
            player.displayClientMessage(
                net.minecraft.network.chat.Component.translatable(
                    success ? "message.eliteforge.dismantle_success" : "message.eliteforge.dismantle_failed")
                    .withStyle(success ? net.minecraft.ChatFormatting.AQUA : net.minecraft.ChatFormatting.RED), true);
            return InteractionResult.CONSUME;
        }

        // v0.7.5: Weapon Enhancement — Weapon Enhancer in main hand + weapon in off-hand
        if (mainHand.getItem() == com.eliteforge.init.ModItems.WEAPON_ENHANCER.get()
                && !offHand.isEmpty() && offHand.getItem() instanceof net.minecraft.world.item.SwordItem) {
            boolean success = com.eliteforge.accessory.AccessoryUpgrader.tryEnhanceWeapon(offHand, player);
            player.displayClientMessage(
                net.minecraft.network.chat.Component.translatable(
                    success ? "message.eliteforge.enhance_success" : "message.eliteforge.enhance_failed")
                    .withStyle(success ? net.minecraft.ChatFormatting.GOLD : net.minecraft.ChatFormatting.RED), true);
            return InteractionResult.CONSUME;
        }

        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof EliteBeaconBlockEntity beacon) {
            openBeaconMenu(player, beacon);
        }

        return InteractionResult.CONSUME;
    }

    private void openBeaconMenu(Player player, EliteBeaconBlockEntity beacon) {
        player.displayClientMessage(
            Component.translatable("message.eliteforge.elite_beacon.menu_title")
                .withStyle(ChatFormatting.RED),
            false
        );

        boolean isActive = beacon.isActive();
        player.displayClientMessage(
            Component.translatable(isActive
                    ? "message.eliteforge.elite_beacon.status.active"
                    : "message.eliteforge.elite_beacon.status.inactive")
                .withStyle(isActive ? ChatFormatting.GREEN : ChatFormatting.RED),
            false
        );

        int mode = beacon.getSuppressionMode();
        String[] modeNameKeys = {
            "tooltip.eliteforge.elite_beacon.mode.0",
            "tooltip.eliteforge.elite_beacon.mode.1",
            "tooltip.eliteforge.elite_beacon.mode.2"
        };
        String[] modeDescKeys = {
            "tooltip.eliteforge.elite_beacon.mode.0.desc",
            "tooltip.eliteforge.elite_beacon.mode.1.desc",
            "tooltip.eliteforge.elite_beacon.mode.2.desc"
        };

        for (int i = 0; i < modeNameKeys.length; i++) {
            ChatFormatting color = (i == mode) ? ChatFormatting.GOLD : ChatFormatting.GRAY;
            String prefix = (i == mode) ? "► " : "  ";
            player.displayClientMessage(
                Component.literal(prefix)
                    .append(Component.translatable(modeNameKeys[i]))
                    .withStyle(color),
                false
            );
            player.displayClientMessage(
                Component.literal("    ")
                    .append(Component.translatable(modeDescKeys[i]))
                    .withStyle(ChatFormatting.DARK_GRAY),
                false
            );
        }

        player.displayClientMessage(
            Component.translatable("message.eliteforge.elite_beacon.change_mode_hint")
                .withStyle(ChatFormatting.AQUA),
            false
        );

        boolean hasHeatSource = true; // Always true in v0.2.0
        player.displayClientMessage(
            Component.translatable(hasHeatSource
                    ? "message.eliteforge.elite_beacon.heat_source.connected"
                    : "message.eliteforge.elite_beacon.heat_source.not_found")
                .withStyle(hasHeatSource ? ChatFormatting.GREEN : ChatFormatting.RED),
            false
        );
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(ACTIVE)) {
            double x = pos.getX() + 0.5;
            double y = pos.getY() + 1.0;
            double z = pos.getZ() + 0.5;

            for (int i = 0; i < 3; i++) {
                double offsetY = random.nextDouble() * 5.0;
                level.addParticle(ParticleTypes.END_ROD,
                    x + (random.nextDouble() - 0.5) * 0.2,
                    y + offsetY,
                    z + (random.nextDouble() - 0.5) * 0.2,
                    0.0, 0.1, 0.0);
            }
        }
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable BlockGetter level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.eliteforge.elite_beacon.description")
            .withStyle(ChatFormatting.RED));
        tooltip.add(Component.translatable("tooltip.eliteforge.elite_beacon.requires_heat_collector")
            .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.eliteforge.elite_beacon.level_1")
            .withStyle(ChatFormatting.WHITE));
        tooltip.add(Component.translatable("tooltip.eliteforge.elite_beacon.level_2")
            .withStyle(ChatFormatting.YELLOW));
        tooltip.add(Component.translatable("tooltip.eliteforge.elite_beacon.level_3")
            .withStyle(ChatFormatting.GOLD));
    }
}
