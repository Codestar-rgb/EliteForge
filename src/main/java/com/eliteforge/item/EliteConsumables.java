package com.eliteforge.item;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

public class EliteConsumables {
    public static class EliteApple extends Item {
        public EliteApple() { super(new Item.Properties().stacksTo(16).rarity(net.minecraft.world.item.Rarity.UNCOMMON)
                .food(new net.minecraft.world.food.FoodProperties.Builder().nutrition(8).saturationMod(1.2f)
                        .effect(() -> new MobEffectInstance(MobEffects.REGENERATION, 100, 1), 1.0f)
                        .effect(() -> new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 200, 0), 1.0f).build())); }
    }
    public static class EnergyBrew extends Item {
        public EnergyBrew() { super(new Item.Properties().stacksTo(16)); }
        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            ItemStack stack = player.getItemInHand(hand);
            if (level.isClientSide) return InteractionResultHolder.success(stack);
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 600, 1));
            player.addEffect(new MobEffectInstance(MobEffects.JUMP, 600, 0));
            player.getFoodData().eat(4, 0.6f);
            if (!player.getAbilities().instabuild) stack.shrink(1);
            return InteractionResultHolder.consume(stack);
        }
    }
    public static class ShieldPotion extends Item {
        public ShieldPotion() { super(new Item.Properties().stacksTo(16)); }
        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            ItemStack stack = player.getItemInHand(hand);
            if (level.isClientSide) return InteractionResultHolder.success(stack);
            player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 1200, 3));
            if (!player.getAbilities().instabuild) stack.shrink(1);
            return InteractionResultHolder.consume(stack);
        }
    }
    public static class RageElixir extends Item {
        public RageElixir() { super(new Item.Properties().stacksTo(16)); }
        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            ItemStack stack = player.getItemInHand(hand);
            if (level.isClientSide) return InteractionResultHolder.success(stack);
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 400, 2));
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 400, 0));
            if (!player.getAbilities().instabuild) stack.shrink(1);
            return InteractionResultHolder.consume(stack);
        }
    }
    public static class PhoenixBrew extends Item {
        public PhoenixBrew() { super(new Item.Properties().stacksTo(4).rarity(net.minecraft.world.item.Rarity.EPIC)); }
        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            ItemStack stack = player.getItemInHand(hand);
            if (level.isClientSide) return InteractionResultHolder.success(stack);
            // Grants a powerful regen + fire resistance — "phoenix" protection
            player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 400, 2));
            player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 1200, 0));
            player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 600, 2));
            player.setHealth(player.getMaxHealth());
            if (!player.getAbilities().instabuild) stack.shrink(1);
            return InteractionResultHolder.consume(stack);
        }
    }
}
