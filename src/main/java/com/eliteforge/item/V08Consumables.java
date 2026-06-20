package com.eliteforge.item;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class V08Consumables {
    public static class CorruptedApple extends Item {
        public CorruptedApple() { super(new Item.Properties().stacksTo(16).rarity(net.minecraft.world.item.Rarity.UNCOMMON)
                .food(new net.minecraft.world.food.FoodProperties.Builder().nutrition(6).saturationMod(0.8f)
                        .effect(() -> new MobEffectInstance(MobEffects.DAMAGE_BOOST, 200, 0), 1.0f)
                        .effect(() -> new MobEffectInstance(MobEffects.WEAKNESS, 100, 0), 0.5f).build())); }
    }
    public static class ManaCrystal extends Item {
        public ManaCrystal() { super(new Item.Properties().stacksTo(16).rarity(net.minecraft.world.item.Rarity.UNCOMMON)); }
        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            ItemStack stack = player.getItemInHand(hand);
            if (level.isClientSide) return InteractionResultHolder.success(stack);
            player.addEffect(new MobEffectInstance(MobEffects.CONDUIT_POWER, 400, 0));
            player.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 400, 1));
            player.getFoodData().eat(2, 0.3f);
            if (!player.getAbilities().instabuild) stack.shrink(1);
            return InteractionResultHolder.consume(stack);
        }
    }
    public static class SwiftBrew extends Item {
        public SwiftBrew() { super(new Item.Properties().stacksTo(16)); }
        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            ItemStack stack = player.getItemInHand(hand);
            if (level.isClientSide) return InteractionResultHolder.success(stack);
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 600, 2));
            player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 600, 0));
            player.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 600, 1));
            if (!player.getAbilities().instabuild) stack.shrink(1);
            return InteractionResultHolder.consume(stack);
        }
    }
    public static class IronSkinPotion extends Item {
        public IronSkinPotion() { super(new Item.Properties().stacksTo(16)); }
        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            ItemStack stack = player.getItemInHand(hand);
            if (level.isClientSide) return InteractionResultHolder.success(stack);
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 600, 3));
            player.addEffect(new MobEffectInstance(MobEffects.SLOW, 200, 0));
            if (!player.getAbilities().instabuild) stack.shrink(1);
            return InteractionResultHolder.consume(stack);
        }
    }
    public static class ShadowStepElixir extends Item {
        public ShadowStepElixir() { super(new Item.Properties().stacksTo(8).rarity(net.minecraft.world.item.Rarity.RARE)); }
        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            ItemStack stack = player.getItemInHand(hand);
            if (level.isClientSide) return InteractionResultHolder.success(stack);
            player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 400, 0));
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 400, 1));
            if (!player.getAbilities().instabuild) stack.shrink(1);
            return InteractionResultHolder.consume(stack);
        }
    }
}
