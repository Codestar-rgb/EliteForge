package com.eliteforge.item;

import com.eliteforge.capability.EliteCapability;
import com.eliteforge.capability.EliteCapabilitySync;
import com.eliteforge.capability.EliteData;
import com.eliteforge.quality.QualityTier;
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

import javax.annotation.Nullable;
import java.util.List;

/**
 * EliteNameTag (精英名牌) - Right-click on elite mob to prevent despawning and name it.
 * Named elites have slightly increased difficulty (+1 level modifier, max V).
 * Named elites have guaranteed quality >= GOOD.
 * Consumed on use.
 */
public class EliteNameTag extends Item {

    private static final int MAX_LEVEL_MODIFIER = 5;
    private static final String LEVEL_MOD_TAG = "eliteforge:level_modifier";

    public EliteNameTag() {
        super(new Item.Properties().stacksTo(16));
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (player.level().isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        if (!(target instanceof Mob mob)) {
            player.displayClientMessage(
                Component.translatable("message.eliteforge.elite_name_tag.mob_only")
                    .withStyle(ChatFormatting.RED),
                true
            );
            return InteractionResult.FAIL;
        }

        // Check elite status via the EliteCapability (the canonical elite system). The old code
        // read from a "forge" sub-compound that no system ever writes to, so detection always failed.
        EliteCapability cap = target.getCapability(EliteCapability.CAPABILITY).orElse(null);
        if (cap == null || !cap.isElite()) {
            player.displayClientMessage(
                Component.translatable("message.eliteforge.elite_name_tag.elite_only")
                    .withStyle(ChatFormatting.RED),
                true
            );
            return InteractionResult.FAIL;
        }

        // Get the custom name from the item. If the player renamed the tag in an anvil,
        // use that name; otherwise fall back to the localized default "Named Elite".
        // Previously this compared against hardcoded English/Chinese item-name strings,
        // which broke for any other locale.
        Component customName;
        if (stack.hasCustomHoverName()) {
            customName = stack.getHoverName();
        } else {
            customName = Component.translatable("name.eliteforge.elite_default_name").withStyle(ChatFormatting.GOLD);
        }

        // Apply the custom name to the elite
        mob.setCustomName(customName);
        mob.setCustomNameVisible(true);

        // Prevent despawning
        mob.setPersistenceRequired();

        // Increase level modifier (max V = 5). Stored directly on the entity's persistent data
        // (no "forge" sub-compound) so other systems can read it.
        CompoundTag targetData = target.getPersistentData();
        int currentLevelMod = targetData.contains(LEVEL_MOD_TAG) ? targetData.getInt(LEVEL_MOD_TAG) : 0;
        int newLevelMod = Math.min(currentLevelMod + 1, MAX_LEVEL_MODIFIER);
        targetData.putInt(LEVEL_MOD_TAG, newLevelMod);

        // Guarantee quality >= GOOD via the EliteCapability, then sync to clients so the nameplate
        // color and tooltip reflect the new tier.
        EliteData data = cap.getEliteData();
        boolean qualityChanged = false;
        if (data.getQualityTier().ordinal() < QualityTier.GOOD.ordinal()) {
            data.setQualityTier(QualityTier.GOOD);
            qualityChanged = true;
        }
        if (qualityChanged) {
            cap.setEliteData(data);
            EliteCapabilitySync.broadcastEliteDataUpdate(target, data);
        }

        // Consume the item
        stack.shrink(1);

        player.displayClientMessage(
            Component.translatable("message.eliteforge.elite_name_tag.success", newLevelMod)
                .withStyle(ChatFormatting.GOLD),
            true
        );

        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.eliteforge.elite_name_tag")
            .withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("tooltip.eliteforge.elite_name_tag.effect")
            .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.eliteforge.elite_name_tag.rename_hint")
            .withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }
}
