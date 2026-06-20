package com.eliteforge.item;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class V085Consumables {
    public static class MenaceApple extends Item {
        public MenaceApple() { super(new Item.Properties().stacksTo(16).rarity(net.minecraft.world.item.Rarity.UNCOMMON)
                .food(new net.minecraft.world.food.FoodProperties.Builder().nutrition(8).saturationMod(1.0f)
                        .effect(() -> new MobEffectInstance(MobEffects.DAMAGE_BOOST, 300, 1), 1.0f)
                        .effect(() -> new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 0), 0.5f).build())); }
    }
    public static class StaminaCrystal extends Item {
        public StaminaCrystal() { super(new Item.Properties().stacksTo(16).rarity(net.minecraft.world.item.Rarity.UNCOMMON)); }
        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            ItemStack stack = player.getItemInHand(hand);
            if (level.isClientSide) return InteractionResultHolder.success(stack);
            player.getFoodData().eat(8, 1.0f);
            player.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 400, 1));
            if (!player.getAbilities().instabuild) stack.shrink(1);
            return InteractionResultHolder.consume(stack);
        }
    }
    public static class HasteTonic extends Item {
        public HasteTonic() { super(new Item.Properties().stacksTo(16)); }
        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            ItemStack stack = player.getItemInHand(hand);
            if (level.isClientSide) return InteractionResultHolder.success(stack);
            player.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 600, 2));
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 600, 0));
            if (!player.getAbilities().instabuild) stack.shrink(1);
            return InteractionResultHolder.consume(stack);
        }
    }
    public static class BerserkBrew extends Item {
        public BerserkBrew() { super(new Item.Properties().stacksTo(8).rarity(net.minecraft.world.item.Rarity.RARE)); }
        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            ItemStack stack = player.getItemInHand(hand);
            if (level.isClientSide) return InteractionResultHolder.success(stack);
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 400, 2));
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 400, 1));
            player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 100, 0));
            if (!player.getAbilities().instabuild) stack.shrink(1);
            return InteractionResultHolder.consume(stack);
        }
    }
    public static class NullPotion extends Item {
        public NullPotion() { super(new Item.Properties().stacksTo(8).rarity(net.minecraft.world.item.Rarity.RARE)); }
        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            ItemStack stack = player.getItemInHand(hand);
            if (level.isClientSide) return InteractionResultHolder.success(stack);
            player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 800, 4));
            player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 400, 0));
            if (!player.getAbilities().instabuild) stack.shrink(1);
            return InteractionResultHolder.consume(stack);
        }
    }
}
