package com.eliteforge.item;

import com.eliteforge.ability.Ability;
import com.eliteforge.EliteForge;
import com.eliteforge.capability.EliteCapability;
import com.eliteforge.capability.EliteCapabilitySync;
import com.eliteforge.capability.EliteData;
import com.eliteforge.config.DifficultyMode;
import com.eliteforge.quality.QualityTier;
import com.eliteforge.spawn.AbilityGenerator;
import com.mojang.datafixers.util.Pair;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * BestowalSigil (赐能铭印) - Drop from C5 Bestowal creator.
 * Right-click on a non-elite hostile mob: Transforms it into a GOOD quality elite
 * with 1 random I-level ability.
 * Single use, consumed on success. Has 30% chance of failure (item still consumed).
 * Visual: golden flash on target.
 * Cannot be used on players, bosses, or already-elite mobs.
 */
public class BestowalSigil extends Item {

    private static final double FAILURE_CHANCE = 0.30;

    public BestowalSigil() {
        super(new Item.Properties().stacksTo(4));
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (player.level().isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        // Cannot be used on players
        if (target instanceof Player) {
            player.displayClientMessage(
                Component.translatable("message.eliteforge.bestowal_sigil.no_players")
                    .withStyle(ChatFormatting.RED),
                true
            );
            return InteractionResult.FAIL;
        }

        // Cannot be used on bosses (Ender Dragon, Wither)
        if (target instanceof EnderDragon || target instanceof WitherBoss) {
            player.displayClientMessage(
                Component.translatable("message.eliteforge.bestowal_sigil.no_bosses")
                    .withStyle(ChatFormatting.RED),
                true
            );
            return InteractionResult.FAIL;
        }

        // Must be a hostile mob (Monster type)
        if (!(target instanceof Monster monster)) {
            player.displayClientMessage(
                Component.translatable("message.eliteforge.bestowal_sigil.hostile_only")
                    .withStyle(ChatFormatting.RED),
                true
            );
            return InteractionResult.FAIL;
        }

        Mob mob = (Mob) monster;

        // Cannot be used on already-elite mobs
        boolean isAlreadyElite = target.getCapability(EliteCapability.CAPABILITY)
            .map(EliteCapability::isElite)
            .orElse(false);

        if (isAlreadyElite) {
            player.displayClientMessage(
                Component.translatable("message.eliteforge.bestowal_sigil.already_elite")
                    .withStyle(ChatFormatting.RED),
                true
            );
            return InteractionResult.FAIL;
        }

        Level level = player.level();
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.PASS;
        }

        // Consume the item regardless of success/failure
        if (!player.isCreative()) {
            stack.shrink(1);
        }

        // Check for failure (30% chance)
        if (ThreadLocalRandom.current().nextDouble() < FAILURE_CHANCE) {
            // Failure - still consume the item
            player.displayClientMessage(
                Component.translatable("message.eliteforge.bestowal_sigil.failed")
                    .withStyle(ChatFormatting.RED),
                true
            );

            // Dark particles on failure
            serverLevel.sendParticles(
                ParticleTypes.SMOKE,
                target.getX(), target.getY() + target.getBbHeight() / 2.0, target.getZ(),
                15, 0.3, 0.3, 0.3, 0.05
            );

            serverLevel.playSound(null, target.getX(), target.getY(), target.getZ(),
                SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.5f, 0.5f);

            return InteractionResult.CONSUME;
        }

        // Success - transform the mob into a GOOD quality elite
        transformToElite(serverLevel, mob, player);

        return InteractionResult.CONSUME;
    }

    /**
     * Transform the target mob into a GOOD quality elite with 1 random I-level ability.
     */
    private void transformToElite(ServerLevel serverLevel, Mob mob, Player player) {
        // Generate 1 random ability at level I using the AbilityGenerator
        List<Pair<Ability, Integer>> abilities = AbilityGenerator.generateAbilities(
            1, DifficultyMode.FORGE, mob.getType()
        );

        // Keep only 1 ability
        if (abilities.size() > 1) {
            abilities = abilities.subList(0, 1);
        }
        final List<Pair<Ability, Integer>> finalAbilities = abilities;

        // Set capability data
        mob.getCapability(EliteCapability.CAPABILITY).ifPresent(cap -> {
            EliteData data = new EliteData();
            data.setElite(true);
            data.setLevel(1);
            data.setQualityTier(QualityTier.GOOD);
            data.setSpawnMode(DifficultyMode.FORGE);

            // Store abilities AND fire their onApply so attribute modifiers / particle
            // loops / scheduled effects actually take effect. Previously onApply was
            // skipped, leaving the elite with a dormant ability in its capability map.
            for (Pair<Ability, Integer> abilityPair : finalAbilities) {
                data.addAbility(abilityPair.getFirst().getIdString(), abilityPair.getSecond());
            }

            cap.setEliteData(data);

            // Fire onApply for each granted ability (after setEliteData so onApply can
            // read back the capability if it needs to).
            for (Pair<Ability, Integer> abilityPair : finalAbilities) {
                try {
                    abilityPair.getFirst().onApply(mob, abilityPair.getSecond());
                } catch (Exception e) {
                    EliteForge.LOGGER.error("Error in onApply for {} during BestowalSigil transform: {}",
                            abilityPair.getFirst().getIdString(), e.getMessage());
                }
            }

            // Register with the elite tracker so the elite's abilities tick and it
            // participates in dynamic strengthening / awakening / revenge.
            com.eliteforge.spawn.EliteEventHandler.trackElite(mob);

            // Sync to clients
            EliteCapabilitySync.broadcastEliteDataUpdate(mob, data);
        });

        // Apply elite visual modifiers
        mob.setCustomName(
            Component.translatable("name.eliteforge.elite_prefix").append(" ").append(mob.getName())
                .withStyle(QualityTier.GOOD.getChatColor())
        );
        mob.setCustomNameVisible(true);

        // Scale health (GOOD tier bonus).
        // In 1.20.1, Mob.getAttribute(Attribute) returns AttributeInstance (not Optional),
        // so we use a null check instead of ifPresent(...).
        float baseHealth = mob.getMaxHealth();
        float newHealth = baseHealth * 1.5f;
        net.minecraft.world.entity.ai.attributes.AttributeInstance maxHealthAttr = mob.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealthAttr != null) {
            maxHealthAttr.setBaseValue(newHealth);
        }
        mob.setHealth(newHealth);

        // Golden flash visual effect
        serverLevel.sendParticles(
            ParticleTypes.ENCHANT,
            mob.getX(), mob.getY() + mob.getBbHeight() / 2.0, mob.getZ(),
            30, 0.5, 0.5, 0.5, 1.0
        );

        // Golden burst
        serverLevel.sendParticles(
            ParticleTypes.HAPPY_VILLAGER,
            mob.getX(), mob.getY() + mob.getBbHeight() / 2.0, mob.getZ(),
            20, 0.3, 0.3, 0.3, 0.1
        );

        // Play transformation sound
        serverLevel.playSound(null, mob.getX(), mob.getY(), mob.getZ(),
            SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.8f, 1.5f);

        // Notify player
        if (!abilities.isEmpty()) {
            String abilityName = abilities.get(0).getFirst().getIdString();
            player.displayClientMessage(
                Component.translatable("message.eliteforge.bestowal_sigil.transformed_with_ability", abilityName)
                    .withStyle(ChatFormatting.GOLD),
                true
            );
        } else {
            player.displayClientMessage(
                Component.translatable("message.eliteforge.bestowal_sigil.transformed")
                    .withStyle(ChatFormatting.GOLD),
                true
            );
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.eliteforge.bestowal_sigil.imbues")
            .withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("item.eliteforge.bestowal_sigil.tooltip")
            .withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("tooltip.eliteforge.bestowal_sigil.action")
            .withStyle(ChatFormatting.YELLOW));
        tooltip.add(Component.translatable("tooltip.eliteforge.bestowal_sigil.grants")
            .withStyle(ChatFormatting.YELLOW));
        tooltip.add(Component.translatable("tooltip.eliteforge.bestowal_sigil.failure_chance")
            .withStyle(ChatFormatting.RED));
        tooltip.add(Component.translatable("tooltip.eliteforge.bestowal_sigil.restrictions")
            .withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public boolean isEnchantable(ItemStack stack) {
        return false;
    }
}
