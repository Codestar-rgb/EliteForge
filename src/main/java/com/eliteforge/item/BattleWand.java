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

public class BattleWand extends Item {
    public BattleWand() { super(new Item.Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.RARE)); }
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) return InteractionResultHolder.success(stack);
        if (player.getCooldowns().isOnCooldown(this)) return InteractionResultHolder.pass(stack);
        player.getCooldowns().addCooldown(this, 40);
        if (level instanceof ServerLevel sl) {
            var snowball = net.minecraft.world.entity.projectile.Snowball(level, player);
            snowball.shootFromRotation(player, player.getXRot(), player.getYRot(), 0, 2.0f, 1.0f);
            snowball.setDeltaMovement(snowball.getDeltaMovement().scale(2.0));
            sl.addFreshEntity(snowball);
            sl.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SNOWBALL_THROW, SoundSource.PLAYERS, 1.0f, 0.8f);
        }
        return InteractionResultHolder.consume(stack);
    }
    @Override
    public boolean isFoil(ItemStack stack) { return true; }
}
