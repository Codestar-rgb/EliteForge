package com.eliteforge.item;

import com.eliteforge.ability.Ability;
import com.eliteforge.ability.AbilityCategory;
import com.eliteforge.ability.AbilityRegistry;
import com.eliteforge.capability.EliteCapability;
import com.eliteforge.capability.EliteCapabilitySync;
import com.eliteforge.capability.EliteData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * RerollScroll (重铸卷轴) - Rerolls all abilities of an elite mob.
 * Consumed on use. New abilities are drawn from the live {@link AbilityRegistry}
 * (excluding legendary and creator abilities), preserving the original gameplay intent
 * while fixing the legacy hardcoded ABILITY_POOL that referenced non-existent ability IDs.
 * Elite briefly becomes immune during reroll (2 seconds) — the invulnerability is now
 * removed via a scheduled {@link TickTask} instead of relying on a non-existent tick handler.
 */
public class RerollScroll extends Item {

    private static final Logger LOGGER = LogManager.getLogger();
    private static final int IMMUNE_DURATION_TICKS = 40; // 2 seconds

    public RerollScroll() {
        super(new Item.Properties().stacksTo(16));
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (player.level().isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        Level level = player.level();

        if (!(target instanceof Mob mob)) {
            player.displayClientMessage(
                Component.translatable("message.eliteforge.reroll_scroll.elite_only")
                    .withStyle(ChatFormatting.RED),
                true
            );
            return InteractionResult.FAIL;
        }

        // Check elite status via the EliteCapability (the canonical elite system). The old code
        // read from a "forge" sub-compound that no system ever writes to, so detection always
        // failed and the scroll was unusable.
        EliteCapability cap = target.getCapability(EliteCapability.CAPABILITY).orElse(null);
        if (cap == null || !cap.isElite()) {
            player.displayClientMessage(
                Component.translatable("message.eliteforge.reroll_scroll.elite_only")
                    .withStyle(ChatFormatting.RED),
                true
            );
            return InteractionResult.FAIL;
        }

        EliteData data = cap.getEliteData();
        int eliteLevel = data.getLevel();

        // Remove existing abilities (call onRemove for cleanup of attribute modifiers / effects).
        // Iterate over a copy of the entry set because removeAbility mutates the underlying map.
        for (Map.Entry<String, Integer> entry : new HashSet<>(data.getAbilities().entrySet())) {
            Ability oldAbility = AbilityRegistry.getAbility(entry.getKey());
            if (oldAbility != null) {
                try {
                    oldAbility.onRemove(target, entry.getValue());
                } catch (Exception e) {
                    LOGGER.error("Error in onRemove for {} during reroll: {}",
                            entry.getKey(), e.getMessage());
                }
            }
            data.removeAbility(entry.getKey());
        }

        // Generate new abilities from the live AbilityRegistry, filtered to exclude legendary
        // and creator abilities (mirrors the old "no legendary reroll" intent).
        List<Ability> pool = getRerollPool();
        if (pool.isEmpty()) {
            LOGGER.error("Reroll pool is empty — no abilities available in the registry");
            // Still sync the cleared state and continue (the elite now has no abilities).
            cap.setEliteData(data);
            EliteCapabilitySync.broadcastEliteDataUpdate(target, data);
        } else {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            int numAbilities = Math.min(1 + (eliteLevel / 2), 5);
            for (int i = 0; i < numAbilities; i++) {
                Ability ability = pool.get(random.nextInt(pool.size()));
                // Preserve the original level-scaling formula: abilityLevel is eliteLevel or eliteLevel-1,
                // capped by the ability's maxLevel and floored at 1.
                int abilityLevel = Math.max(1, Math.min(ability.getMaxLevel(),
                        eliteLevel - random.nextInt(2)));
                data.addAbility(ability.getIdString(), abilityLevel);
                try {
                    ability.onApply(target, abilityLevel);
                } catch (Exception e) {
                    LOGGER.error("Error in onApply for {} during reroll: {}",
                            ability.getIdString(), e.getMessage());
                }
            }
            cap.setEliteData(data);
            EliteCapabilitySync.broadcastEliteDataUpdate(target, data);
        }

        // Brief invulnerability during reroll. The old code set the entity invulnerable forever
        // and wrote an "eliteforge:immune_until" tag that no tick handler ever reads, so the
        // invulnerability was permanent. We now schedule an explicit removal via TickTask.
        mob.setInvulnerable(true);
        if (level.getServer() != null) {
            level.getServer().tell(new TickTask(
                level.getServer().getTickCount() + IMMUNE_DURATION_TICKS,
                () -> {
                    if (mob.isAlive()) {
                        mob.setInvulnerable(false);
                    }
                }
            ));
        }

        // Consume the item
        stack.shrink(1);

        // Play enchanting sound
        level.playSound(null, target.getX(), target.getY(), target.getZ(),
            SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 1.0f, 1.0f);

        // Spawn enchanting particles
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(
                net.minecraft.core.particles.ParticleTypes.ENCHANT,
                target.getX(), target.getY() + target.getBbHeight() / 2.0, target.getZ(),
                30, 0.5, 0.5, 0.5, 1.0
            );
        }

        player.displayClientMessage(
            Component.translatable("message.eliteforge.reroll_scroll.success")
                .withStyle(ChatFormatting.LIGHT_PURPLE),
            true
        );

        return InteractionResult.CONSUME;
    }

    /**
     * Build the reroll pool: all registered abilities except legendary and creator abilities.
     * Replaces the legacy hardcoded ABILITY_POOL that referenced non-existent ability IDs
     * (e.g. "speed", "strength", "regeneration").
     */
    private static List<Ability> getRerollPool() {
        List<Ability> pool = new ArrayList<>();
        for (Ability ability : AbilityRegistry.getAllAbilities()) {
            AbilityCategory category = ability.getCategory();
            if (category == AbilityCategory.LEGENDARY || category == AbilityCategory.CREATOR) {
                continue;
            }
            if (!ability.isEnabled()) {
                continue;
            }
            pool.add(ability);
        }
        return pool;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.eliteforge.reroll_scroll.use")
            .withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.translatable("tooltip.eliteforge.reroll_scroll.immune")
            .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.eliteforge.reroll_scroll.rules")
            .withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }
}
