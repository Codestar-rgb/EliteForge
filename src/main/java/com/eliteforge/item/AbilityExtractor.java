package com.eliteforge.item;

import com.eliteforge.ability.Ability;
import com.eliteforge.ability.AbilityCategory;
import com.eliteforge.ability.AbilityRegistry;
import com.eliteforge.capability.EliteCapability;
import com.eliteforge.capability.EliteCapabilitySync;
import com.eliteforge.capability.EliteData;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * AbilityExtractor (能力提取器) - Removes a random ability from an elite and stores it.
 * Cannot extract legendary abilities.
 * Has 8 durability uses.
 */
public class AbilityExtractor extends Item {

    private static final Logger LOGGER = LogManager.getLogger();
    private static final int MAX_DURABILITY = 8;
    private static final String STORED_ABILITY_TAG = "eliteforge:stored_ability";
    private static final String STORED_ABILITY_LEVEL_TAG = "eliteforge:stored_ability_level";
    private static final String ABILITY_RARITY_TAG = "eliteforge:ability_rarity";

    public AbilityExtractor() {
        super(new Item.Properties().durability(MAX_DURABILITY));
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (player.level().isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        // Check if already contains an ability
        if (hasStoredAbility(stack)) {
            player.displayClientMessage(
                Component.translatable("message.eliteforge.ability_extractor.already_stored")
                    .withStyle(ChatFormatting.RED),
                true
            );
            return InteractionResult.FAIL;
        }

        // Check if target is an elite mob
        if (!(target instanceof Mob)) {
            player.displayClientMessage(
                Component.translatable("message.eliteforge.ability_extractor.elite_only")
                    .withStyle(ChatFormatting.RED),
                true
            );
            return InteractionResult.FAIL;
        }

        // Check elite status via the EliteCapability (the canonical elite system). The old code
        // read from a "forge" sub-compound that no system ever writes to, so detection always failed
        // and the abilities list was always empty.
        EliteCapability cap = target.getCapability(EliteCapability.CAPABILITY).orElse(null);
        if (cap == null || !cap.isElite()) {
            player.displayClientMessage(
                Component.translatable("message.eliteforge.ability_extractor.elite_only")
                    .withStyle(ChatFormatting.RED),
                true
            );
            return InteractionResult.FAIL;
        }

        EliteData data = cap.getEliteData();
        Map<String, Integer> abilities = data.getAbilities();

        if (abilities.isEmpty()) {
            player.displayClientMessage(
                Component.translatable("message.eliteforge.ability_extractor.no_abilities")
                    .withStyle(ChatFormatting.RED),
                true
            );
            return InteractionResult.FAIL;
        }

        // Build a list of extractable ability IDs (exclude legendary and creator abilities,
        // mirroring the old "cannot extract legendary" rule). We resolve each ability via the
        // AbilityRegistry to inspect its category.
        List<String> extractableIds = new ArrayList<>();
        for (String id : abilities.keySet()) {
            Ability ability = AbilityRegistry.getAbility(id);
            if (ability == null) {
                // Unknown ability ID — skip it (cannot be looked up for infuse later anyway).
                continue;
            }
            AbilityCategory category = ability.getCategory();
            if (category == AbilityCategory.LEGENDARY || category == AbilityCategory.CREATOR) {
                continue;
            }
            extractableIds.add(id);
        }

        if (extractableIds.isEmpty()) {
            player.displayClientMessage(
                Component.translatable("message.eliteforge.ability_extractor.all_legendary")
                    .withStyle(ChatFormatting.RED),
                true
            );
            return InteractionResult.FAIL;
        }

        // Select a random extractable ability
        ThreadLocalRandom random = ThreadLocalRandom.current();
        String selectedId = extractableIds.get(random.nextInt(extractableIds.size()));
        int abilityLevel = abilities.get(selectedId);

        // Derive a rarity string for the tooltip from the ability's category. The old code stored
        // a free-form rarity string; the new ability system only has categories, so we map them.
        Ability selectedAbility = AbilityRegistry.getAbility(selectedId);
        String abilityRarity = rarityFromCategory(selectedAbility.getCategory());

        // Store the ability on the extractor item
        CompoundTag itemTag = stack.getOrCreateTag();
        itemTag.putString(STORED_ABILITY_TAG, selectedId);
        itemTag.putInt(STORED_ABILITY_LEVEL_TAG, abilityLevel);
        itemTag.putString(ABILITY_RARITY_TAG, abilityRarity);

        // Remove the ability from the elite via the capability, calling onRemove for cleanup of
        // any attribute modifiers or persistent effects the ability applied.
        try {
            selectedAbility.onRemove(target, abilityLevel);
        } catch (Exception e) {
            LOGGER.error("Error in onRemove for {} during extraction: {}", selectedId, e.getMessage());
        }
        data.removeAbility(selectedId);
        cap.setEliteData(data);
        EliteCapabilitySync.broadcastEliteDataUpdate(target, data);

        // Damage the extractor
        stack.hurtAndBreak(1, player, (p) -> p.broadcastBreakEvent(hand));

        player.displayClientMessage(
            Component.translatable("message.eliteforge.ability_extractor.extracted", selectedId, abilityLevel)
                .withStyle(ChatFormatting.LIGHT_PURPLE),
            true
        );

        return InteractionResult.CONSUME;
    }

    /**
     * Map an {@link AbilityCategory} to the legacy rarity string used by this item's tooltip.
     * Legendary/Creator abilities are never stored here (filtered out before extraction),
     * so this mapping is mostly cosmetic for the remaining categories.
     */
    private static String rarityFromCategory(AbilityCategory category) {
        return switch (category) {
            case LEGENDARY, CREATOR -> "LEGENDARY";
            default -> "COMMON";
        };
    }

    public static boolean hasStoredAbility(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.contains(STORED_ABILITY_TAG);
    }

    public static String getStoredAbilityName(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(STORED_ABILITY_TAG)) return "";
        return tag.getString(STORED_ABILITY_TAG);
    }

    public static int getStoredAbilityLevel(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(STORED_ABILITY_LEVEL_TAG)) return 0;
        return tag.getInt(STORED_ABILITY_LEVEL_TAG);
    }

    public static String getStoredAbilityRarity(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(ABILITY_RARITY_TAG)) return "COMMON";
        return tag.getString(ABILITY_RARITY_TAG);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        if (hasStoredAbility(stack)) {
            String abilityName = getStoredAbilityName(stack);
            int abilityLevel = getStoredAbilityLevel(stack);
            String abilityRarity = getStoredAbilityRarity(stack);
            ChatFormatting rarityColor = getRarityColor(abilityRarity);

            tooltip.add(Component.translatable("tooltip.eliteforge.ability_extractor.stored_ability", abilityName)
                .withStyle(rarityColor));
            tooltip.add(Component.translatable("tooltip.eliteforge.ability_extractor.level", abilityLevel)
                .withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("tooltip.eliteforge.ability_extractor.rarity", abilityRarity)
                .withStyle(rarityColor));
        } else {
            tooltip.add(Component.translatable("tooltip.eliteforge.ability_extractor.use")
                .withStyle(ChatFormatting.LIGHT_PURPLE));
            tooltip.add(Component.translatable("tooltip.eliteforge.ability_extractor.no_legendary")
                .withStyle(ChatFormatting.DARK_GRAY));
        }
        tooltip.add(Component.translatable("tooltip.eliteforge.ability_extractor.uses", MAX_DURABILITY - stack.getDamageValue())
            .withStyle(ChatFormatting.GRAY));
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
        return hasStoredAbility(stack);
    }

    @Override
    public boolean isEnchantable(ItemStack stack) {
        return false;
    }
}
