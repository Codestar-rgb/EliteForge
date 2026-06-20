package com.eliteforge.item;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

public class StormCaller extends Item {
    public StormCaller() { super(new Item.Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.EPIC)); }
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) return InteractionResultHolder.success(stack);
        if (player.getCooldowns().isOnCooldown(this)) return InteractionResultHolder.pass(stack);
        player.getCooldowns().addCooldown(this, 100);
        if (level instanceof ServerLevel sl) {
            net.minecraft.world.entity.LightningBolt bolt = net.minecraft.world.entity.EntityType.LIGHTNING_BOLT.create(sl);
            if (bolt != null) {
                bolt.moveTo(player.getX() + player.getLookAngle().x * 8, player.getY(), player.getZ() + player.getLookAngle().z * 8);
                bolt.setCause(null);
                sl.addFreshEntity(bolt);
                sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, bolt.getX(), bolt.getY(), bolt.getZ(), 20, 1, 1, 1, 0.1);
                sl.playSound(null, bolt.getX(), bolt.getY(), bolt.getZ(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, 1.0f, 1.0f);
            }
        }
        return InteractionResultHolder.consume(stack);
    }
    @Override
    public boolean isFoil(ItemStack stack) { return true; }
}
