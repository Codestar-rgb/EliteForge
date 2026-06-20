package com.eliteforge.item;

import com.eliteforge.ability.Ability;
import com.eliteforge.ability.AbilityCategory;
import com.eliteforge.ability.AbilityRegistry;
import com.eliteforge.capability.EliteCapability;
import com.eliteforge.capability.EliteCapabilitySync;
import com.eliteforge.capability.EliteData;
import com.eliteforge.quality.QualityTier;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
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
import java.util.List;

/**
 * AbilityInfuser (能力灌注器) - Converts a non-elite mob to elite with a stored ability.
 * Must contain an ability from AbilityExtractor.
 * Consumed on use.
 * Created elite has level based on ability's level.
 * Cannot infuse legendary abilities.
 */
public class AbilityInfuser extends Item {

    private static final Logger LOGGER = LogManager.getLogger();

    public AbilityInfuser() {
        super(new Item.Properties().stacksTo(1));
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (player.level().isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        // Check if the infuser contains an ability
        if (!AbilityExtractor.hasStoredAbility(stack)) {
            player.displayClientMessage(
                Component.translatable("message.eliteforge.ability_infuser.empty")
                    .withStyle(ChatFormatting.RED),
                true
            );
            return InteractionResult.FAIL;
        }

        String abilityRarity = AbilityExtractor.getStoredAbilityRarity(stack);
        if ("LEGENDARY".equals(abilityRarity)) {
            player.displayClientMessage(
                Component.translatable("message.eliteforge.ability_infuser.no_legendary")
                    .withStyle(ChatFormatting.RED),
                true
            );
            return InteractionResult.FAIL;
        }

        // Look up the stored ability via the registry. The stored name is the ability's ID string
        // (e.g. "eliteforge:fire") written by AbilityExtractor.
        String abilityId = AbilityExtractor.getStoredAbilityName(stack);
        int abilityLevel = AbilityExtractor.getStoredAbilityLevel(stack);
        Ability ability = AbilityRegistry.getAbility(abilityId);
        if (ability == null) {
            player.displayClientMessage(
                Component.translatable("message.eliteforge.ability_infuser.unknown", abilityId)
                    .withStyle(ChatFormatting.RED),
                true
            );
            return InteractionResult.FAIL;
        }
        // Defensive: refuse to infuse legendary/creator abilities even if one somehow ended up stored.
        AbilityCategory category = ability.getCategory();
        if (category == AbilityCategory.LEGENDARY || category == AbilityCategory.CREATOR) {
            player.displayClientMessage(
                Component.translatable("message.eliteforge.ability_infuser.no_legendary")
                    .withStyle(ChatFormatting.RED),
                true
            );
            return InteractionResult.FAIL;
        }

        // Only works on non-elite mobs
        if (!(target instanceof Mob mob)) {
            player.displayClientMessage(
                Component.translatable("message.eliteforge.ability_infuser.mob_only")
                    .withStyle(ChatFormatting.RED),
                true
            );
            return InteractionResult.FAIL;
        }

        // Check elite status via the EliteCapability (the canonical elite system). The old code
        // read from a "forge" sub-compound that no system ever writes to, so detection always
        // returned false and the infuser would happily re-convert already-elite mobs.
        EliteCapability cap = target.getCapability(EliteCapability.CAPABILITY).orElse(null);
        if (cap != null && cap.isElite()) {
            player.displayClientMessage(
                Component.translatable("message.eliteforge.ability_infuser.already_elite")
                    .withStyle(ChatFormatting.RED),
                true
            );
            return InteractionResult.FAIL;
        }
        if (cap == null) {
            // Capability missing — cannot convert. This should never happen for a LivingEntity.
            player.displayClientMessage(
                Component.translatable("message.eliteforge.ability_infuser.cannot_convert")
                    .withStyle(ChatFormatting.RED),
                true
            );
            return InteractionResult.FAIL;
        }

        // Convert the mob to elite via the capability.
        EliteData data = cap.getEliteData();
        data.setElite(true);
        data.setLevel(Math.max(1, abilityLevel));
        data.setQualityTier(qualityTierFromRarity(abilityRarity));
        data.addAbility(ability.getIdString(), Math.max(1, abilityLevel));
        cap.setEliteData(data);
        EliteCapabilitySync.broadcastEliteDataUpdate(target, data);

        // Call onApply so the ability's passive effects / attribute modifiers are applied.
        try {
            ability.onApply(target, Math.max(1, abilityLevel));
        } catch (Exception e) {
            LOGGER.error("Error in onApply for {} during infusion: {}", abilityId, e.getMessage());
        }

        // Visual: give the mob a glowing name
        mob.setCustomName(
            Component.translatable("name.eliteforge.elite_prefix")
                .append(" ")
                .append(mob.getName())
                .withStyle(ChatFormatting.GOLD)
        );
        mob.setCustomNameVisible(true);

        // Scale health based on ability level. setHealth clamps to [0, getMaxHealth()],
        // so raise the MAX_HEALTH attribute base FIRST — otherwise the infused HP bonus
        // is silently capped at the vanilla max.
        float baseHealth = mob.getMaxHealth();
        float newHealth = baseHealth * (1.0f + (abilityLevel * 0.25f));
        net.minecraft.world.entity.ai.attributes.AttributeInstance maxHealthAttr =
                mob.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
        if (maxHealthAttr != null) {
            maxHealthAttr.setBaseValue(newHealth);
        }
        mob.setHealth(newHealth);

        // Consume the item
        stack.shrink(1);

        player.displayClientMessage(
            Component.translatable("message.eliteforge.ability_infuser.infused", abilityId, mob.getName())
                .withStyle(ChatFormatting.LIGHT_PURPLE),
            true
        );

        return InteractionResult.CONSUME;
    }

    /**
     * Map the legacy rarity string stored on the extractor item to a {@link QualityTier}.
     */
    private static QualityTier qualityTierFromRarity(String rarity) {
        return switch (rarity) {
            case "LEGENDARY" -> QualityTier.LEGENDARY;
            case "EPIC" -> QualityTier.EPIC;
            case "RARE" -> QualityTier.FINE;
            case "UNCOMMON" -> QualityTier.GOOD;
            default -> QualityTier.NORMAL;
        };
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        if (AbilityExtractor.hasStoredAbility(stack)) {
            String abilityName = AbilityExtractor.getStoredAbilityName(stack);
            int abilityLevel = AbilityExtractor.getStoredAbilityLevel(stack);
            String abilityRarity = AbilityExtractor.getStoredAbilityRarity(stack);
            ChatFormatting rarityColor = getRarityColor(abilityRarity);

            tooltip.add(Component.translatable("tooltip.eliteforge.ability_infuser.stored_ability", abilityName)
                .withStyle(rarityColor));
            tooltip.add(Component.translatable("tooltip.eliteforge.ability_infuser.level", abilityLevel)
                .withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("tooltip.eliteforge.ability_infuser.rarity", abilityRarity)
                .withStyle(rarityColor));
            tooltip.add(Component.translatable("tooltip.eliteforge.ability_infuser.use")
                .withStyle(ChatFormatting.GOLD));
        } else {
            tooltip.add(Component.translatable("tooltip.eliteforge.ability_infuser.empty_tooltip")
                .withStyle(ChatFormatting.DARK_GRAY));
            tooltip.add(Component.translatable("tooltip.eliteforge.ability_infuser.empty_hint")
                .withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    private ChatFormatting getRarityColor(String rarity) {
        return switch (rarity) {
            case "LEGENDARY" -> ChatFormatting.GOLD;
            case "EPIC" -> ChatFormatting.LIGHT_PURPLE;
            case "RARE" -> ChatFormatting.BLUE;
            case "UNCOMMON" -> ChatFormatting.GREEN;
            default -> ChatFormatting.WHITE;
        };
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return AbilityExtractor.hasStoredAbility(stack);
    }
}
