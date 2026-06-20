package com.eliteforge.item;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import java.util.List;

public class V09Items {
    public static class TeleportScroll extends Item {
        public TeleportScroll() { super(new Item.Properties().stacksTo(16).rarity(net.minecraft.world.item.Rarity.UNCOMMON)); }
        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            ItemStack stack = player.getItemInHand(hand);
            if (level.isClientSide) return InteractionResultHolder.success(stack);
            if (player.getCooldowns().isOnCooldown(this)) return InteractionResultHolder.pass(stack);
            player.getCooldowns().addCooldown(this, 100);
            // Teleport to spawn point
            if (level instanceof ServerLevel sl && player instanceof ServerPlayer sp) {
                sp.teleportTo(sp.getRespawnPosition() != null ? sp.getRespawnPosition().getX() : 0,
                    sp.getRespawnPosition() != null ? sp.getRespawnPosition().getY() : 64,
                    sp.getRespawnPosition() != null ? sp.getRespawnPosition().getZ() : 0);
                sl.sendParticles(ParticleTypes.PORTAL, player.getX(), player.getY()+1, player.getZ(), 30, 0.5, 1, 0.5, 0.5);
                sl.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0f, 1.0f);
            }
            if (!player.getAbilities().instabuild) stack.shrink(1);
            return InteractionResultHolder.consume(stack);
        }
        @Override
        public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
            tooltip.add(Component.translatable("tooltip.eliteforge.teleport_scroll").withStyle(ChatFormatting.AQUA));
        }
    }
    public static class LightningRod extends Item {
        public LightningRod() { super(new Item.Properties().stacksTo(1).durability(32).rarity(net.minecraft.world.item.Rarity.RARE)); }
        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            ItemStack stack = player.getItemInHand(hand);
            if (level.isClientSide) return InteractionResultHolder.success(stack);
            if (player.getCooldowns().isOnCooldown(this)) return InteractionResultHolder.pass(stack);
            player.getCooldowns().addCooldown(this, 80);
            if (level instanceof ServerLevel sl) {
                net.minecraft.world.entity.LightningBolt bolt = net.minecraft.world.entity.EntityType.LIGHTNING_BOLT.create(sl);
                if (bolt != null) {
                    bolt.moveTo(player.getX(), player.getY(), player.getZ());
                    bolt.setCause(null);
                    sl.addFreshEntity(bolt);
                    sl.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, 0.8f, 1.2f);
                }
                if (!player.getAbilities().instabuild) stack.hurt(1, level.getRandom(), null);
            }
            return InteractionResultHolder.consume(stack);
        }
    }
    public static class SmokeBomb extends Item {
        public SmokeBomb() { super(new Item.Properties().stacksTo(16)); }
        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            ItemStack stack = player.getItemInHand(hand);
            if (level.isClientSide) return InteractionResultHolder.success(stack);
            if (player.getCooldowns().isOnCooldown(this)) return InteractionResultHolder.pass(stack);
            player.getCooldowns().addCooldown(this, 60);
            player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 200, 0));
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 200, 0));
            if (level instanceof ServerLevel sl) {
                sl.sendParticles(ParticleTypes.LARGE_SMOKE, player.getX(), player.getY()+0.5, player.getZ(), 40, 1, 1, 1, 0.1);
                sl.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 1.0f, 0.8f);
            }
            if (!player.getAbilities().instabuild) stack.shrink(1);
            return InteractionResultHolder.consume(stack);
        }
    }
    public static class HealingTotem extends Item {
        public HealingTotem() { super(new Item.Properties().stacksTo(8).rarity(net.minecraft.world.item.Rarity.RARE)); }
        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            ItemStack stack = player.getItemInHand(hand);
            if (level.isClientSide) return InteractionResultHolder.success(stack);
            if (player.getCooldowns().isOnCooldown(this)) return InteractionResultHolder.pass(stack);
            player.getCooldowns().addCooldown(this, 200);
            player.setHealth(player.getMaxHealth());
            player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 2));
            player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 400, 2));
            if (level instanceof ServerLevel sl) {
                sl.sendParticles(ParticleTypes.HEART, player.getX(), player.getY()+1, player.getZ(), 20, 0.5, 1, 0.5, 0.1);
                sl.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0f, 1.5f);
            }
            if (!player.getAbilities().instabuild) stack.shrink(1);
            return InteractionResultHolder.consume(stack);
        }
    }
    public static class XPCrystal extends Item {
        public XPCrystal() { super(new Item.Properties().stacksTo(64).rarity(net.minecraft.world.item.Rarity.UNCOMMON)); }
        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            ItemStack stack = player.getItemInHand(hand);
            if (level.isClientSide) return InteractionResultHolder.success(stack);
            player.giveExperiencePoints(50);
            if (level instanceof ServerLevel sl) {
                sl.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY()+1, player.getZ(), 10, 0.3, 0.5, 0.3, 0.05);
            }
            if (!player.getAbilities().instabuild) stack.shrink(1);
            return InteractionResultHolder.consume(stack);
        }
    }
    public static class RevealCompass extends Item {
        public RevealCompass() { super(new Item.Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.UNCOMMON)); }
        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            ItemStack stack = player.getItemInHand(hand);
            if (level.isClientSide) return InteractionResultHolder.success(stack);
            if (player.getCooldowns().isOnCooldown(this)) return InteractionResultHolder.pass(stack);
            player.getCooldowns().addCooldown(this, 60);
            if (level instanceof ServerLevel sl) {
                sl.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, player.getBoundingBox().inflate(48),
                    e -> e.isAlive() && e != player && e.getCapability(com.eliteforge.capability.EliteCapability.CAPABILITY)
                        .map(com.eliteforge.capability.EliteCapability::isElite).orElse(false))
                    .forEach(e -> sl.sendParticles(ParticleTypes.WITCH, e.getX(), e.getY()+1, e.getZ(), 8, 0.3, 0.5, 0.3, 0.02));
            }
            return InteractionResultHolder.success(stack);
        }
    }
    public static class ReforgeHammer extends Item {
        public ReforgeHammer() { super(new Item.Properties().stacksTo(1).durability(32).rarity(net.minecraft.world.item.Rarity.RARE)); }
        @Override
        public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
            tooltip.add(Component.translatable("tooltip.eliteforge.reforge_hammer.1").withStyle(ChatFormatting.GOLD));
            tooltip.add(Component.translatable("tooltip.eliteforge.reforge_hammer.2").withStyle(ChatFormatting.GRAY));
        }
    }
    public static class TransmuteStone extends Item {
        public TransmuteStone() { super(new Item.Properties().stacksTo(16).rarity(net.minecraft.world.item.Rarity.UNCOMMON)); }
        @Override
        public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
            tooltip.add(Component.translatable("tooltip.eliteforge.transmute_stone.1").withStyle(ChatFormatting.AQUA));
            tooltip.add(Component.translatable("tooltip.eliteforge.transmute_stone.2").withStyle(ChatFormatting.GRAY));
        }
    }
}
