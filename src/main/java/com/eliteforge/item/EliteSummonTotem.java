package com.eliteforge.item;

import com.eliteforge.capability.EliteCapability;
import com.eliteforge.capability.EliteCapabilitySync;
import com.eliteforge.capability.EliteData;
import com.eliteforge.config.DifficultyMode;
import com.eliteforge.quality.QualityTier;
import com.eliteforge.spawn.AbilityGenerator;
import com.eliteforge.spawn.EliteEventHandler;
import com.mojang.datafixers.util.Pair;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.core.particles.ParticleTypes;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * EliteSummonTotem (精英召唤图腾) — a consumable that spawns a testing elite mob
 * on right-click. Designed for server operators and players who want to test
 * combat, abilities, and loot drops on demand.
 * <p>
 * The totem reads optional NBT overrides from the stack:
 * <ul>
 *   <li>{@code eliteforge:entity} — entity ID string (default: random hostile mob)</li>
 *   <li>{@code eliteforge:quality} — QualityTier name (default: weighted random)</li>
 *   <li>{@code eliteforge:level} — int level (default: based on chunk heat, 1-50 range)</li>
 * </ul>
 * If no overrides are set, the totem spawns a random vanilla hostile mob at a random
 * quality (weighted toward GOOD/FINE) and a moderate level (10-30).
 * <p>
 * The spawned elite is tracked, sync'd to clients, and given proper abilities via
 * AbilityGenerator — identical to a naturally-spawned elite.
 */
public class EliteSummonTotem extends Item {

    private static final String KEY_ENTITY = "eliteforge:entity";
    private static final String KEY_QUALITY = "eliteforge:quality";
    private static final String KEY_LEVEL = "eliteforge:level";

    /** Vanilla hostile mobs the totem can pick from when no entity override is set. */
    private static final String[] RANDOM_MOBS = {
            "minecraft:zombie", "minecraft:skeleton", "minecraft:creeper",
            "minecraft:spider", "minecraft:husk", "minecraft:stray",
            "minecraft:drowned", "minecraft:pillager", "minecraft:vindicator"
    };

    public EliteSummonTotem() {
        super(new Item.Properties().stacksTo(16));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            return InteractionResultHolder.success(stack);
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResultHolder.pass(stack);
        }
        // 10-tick (0.5s) cooldown to prevent spam-clicking from flooding the world
        // with elite entities.
        if (player.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.pass(stack);
        }
        player.getCooldowns().addCooldown(this, 10);

        // Resolve the entity type to spawn.
        String entityIdStr = stack.getOrCreateTag().getString(KEY_ENTITY);
        if (entityIdStr.isEmpty()) {
            entityIdStr = RANDOM_MOBS[ThreadLocalRandom.current().nextInt(RANDOM_MOBS.length)];
        }
        net.minecraft.resources.ResourceLocation entityId =
                net.minecraft.resources.ResourceLocation.tryParse(entityIdStr);
        if (entityId == null) {
            player.displayClientMessage(Component.translatable("message.eliteforge.totem.invalid_entity", entityIdStr)
                    .withStyle(ChatFormatting.RED), true);
            return InteractionResultHolder.fail(stack);
        }
        EntityType<?> entityType = net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getValue(entityId);
        if (entityType == null) {
            player.displayClientMessage(Component.translatable("message.eliteforge.totem.invalid_entity", entityIdStr)
                    .withStyle(ChatFormatting.RED), true);
            return InteractionResultHolder.fail(stack);
        }

        // Resolve quality override or pick a weighted random one (favor mid-tier).
        QualityTier quality;
        String qualityStr = stack.getOrCreateTag().getString(KEY_QUALITY);
        if (!qualityStr.isEmpty()) {
            try {
                quality = QualityTier.valueOf(qualityStr.toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException e) {
                quality = QualityTier.GOOD;
            }
        } else {
            quality = pickRandomQuality();
        }

        // Resolve level override or pick a moderate random one (10-30).
        int eliteLevel;
        if (stack.getOrCreateTag().contains(KEY_LEVEL)) {
            eliteLevel = Math.max(1, stack.getOrCreateTag().getInt(KEY_LEVEL));
        } else {
            eliteLevel = 5 + ThreadLocalRandom.current().nextInt(21); // 5-25 (v0.6.0: lowered for early testing)
        }

        // Spawn the entity 2 blocks in front of the player.
        // Capture final copies for the lambda below (quality/eliteLevel are reassigned above).
        final QualityTier finalQuality = quality;
        final int finalEliteLevel = eliteLevel;
        float yaw = player.getYRot();
        double dx = -Math.sin(Math.toRadians(yaw)) * 2.0;
        double dz = Math.cos(Math.toRadians(yaw)) * 2.0;
        double spawnX = player.getX() + dx;
        double spawnY = player.getY();
        double spawnZ = player.getZ() + dz;

        LivingEntity living;
        try {
            var entityRaw = entityType.create(serverLevel);
            if (!(entityRaw instanceof LivingEntity le)) {
                player.displayClientMessage(Component.translatable("message.eliteforge.totem.not_living", entityIdStr)
                        .withStyle(ChatFormatting.RED), true);
                return InteractionResultHolder.fail(stack);
            }
            living = le;
            living.moveTo(spawnX, spawnY, spawnZ, yaw + 180, 0);
        } catch (Exception e) {
            player.displayClientMessage(Component.translatable("message.eliteforge.totem.spawn_failed", e.getMessage())
                    .withStyle(ChatFormatting.RED), true);
            return InteractionResultHolder.fail(stack);
        }

        // Convert to elite via the capability system.
        living.getCapability(EliteCapability.CAPABILITY).ifPresent(cap -> {
            EliteData data = new EliteData();
            data.setElite(true);
            data.setLevel(finalEliteLevel);
            data.setQualityTier(finalQuality);
            data.setSpawnMode(DifficultyMode.FORGE);

            // Generate abilities for this level.
            List<Pair<com.eliteforge.ability.Ability, Integer>> abilities =
                    AbilityGenerator.generateAbilities(finalEliteLevel, DifficultyMode.FORGE, living.getType());
            for (Pair<com.eliteforge.ability.Ability, Integer> pair : abilities) {
                data.addAbility(pair.getFirst().getIdString(), pair.getSecond());
                try {
                    pair.getFirst().onApply(living, pair.getSecond());
                } catch (Exception e) {
                    com.eliteforge.EliteForge.LOGGER.error("Error applying totem ability {}: {}",
                            pair.getFirst().getIdString(), e.getMessage());
                }
            }

            cap.setEliteData(data);

            // Scale health/damage to the elite level (v0.6.0: progressive sqrt curve,
            // matching DifficultyManager so totem-spawned elites feel consistent).
            float healthMult = 1.0f + (float)Math.sqrt(finalEliteLevel / 10.0f) * 0.03f;
            float newMaxHealth = living.getMaxHealth() * healthMult;
            if (living instanceof Mob mob) {
                var maxHealthAttr = mob.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
                if (maxHealthAttr != null) maxHealthAttr.setBaseValue(newMaxHealth);
                var dmgAttr = mob.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
                if (dmgAttr != null) {
                    float dmgMult = 1.0f + (float)Math.sqrt(finalEliteLevel / 10.0f) * 0.05f;
                    dmgAttr.setBaseValue(dmgAttr.getBaseValue() * dmgMult);
                }
            }
            living.setHealth(newMaxHealth);

            // Custom name showing the quality + level.
            living.setCustomName(Component.translatable("name.eliteforge.elite_prefix")
                    .append(" ").append(living.getName())
                    .withStyle(finalQuality.getChatColor()));
            living.setCustomNameVisible(true);

            // Register with the tracker so abilities tick + loot drops work.
            EliteEventHandler.trackElite(living);
            EliteCapabilitySync.broadcastEliteDataUpdate(living, data);
        });

        // Add to world.
        serverLevel.addFreshEntity(living);

        // Visual + audio feedback.
        serverLevel.sendParticles(ParticleTypes.WITCH,
                spawnX, spawnY + 1, spawnZ, 20, 0.5, 1.0, 0.5, 0.1);
        serverLevel.sendParticles(ParticleTypes.END_ROD,
                spawnX, spawnY + 1, spawnZ, 10, 0.3, 0.6, 0.3, 0.05);
        serverLevel.playSound(null, spawnX, spawnY, spawnZ,
                SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 0.6f, 1.2f);

        // Consume one totem (unless creative).
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }

        player.displayClientMessage(Component.translatable("message.eliteforge.totem.summoned",
                living.getName(), quality.name(), eliteLevel).withStyle(ChatFormatting.GOLD), true);

        return InteractionResultHolder.consume(stack);
    }

    /** Pick a weighted-random quality favoring mid-tier (GOOD/FINE). */
    private static QualityTier pickRandomQuality() {
        // v0.6.5: adjusted distribution — favors FINE/EPIC for better testing variety.
        int roll = ThreadLocalRandom.current().nextInt(100);
        if (roll < 20) return QualityTier.GOOD;
        if (roll < 50) return QualityTier.FINE;
        if (roll < 75) return QualityTier.EPIC;
        if (roll < 92) return QualityTier.LEGENDARY;
        return QualityTier.MYTHIC;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.eliteforge.summon_totem.1")
                .withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("tooltip.eliteforge.summon_totem.2")
                .withStyle(ChatFormatting.GRAY));

        CompoundTag tag = stack.getOrCreateTag();
        if (tag.contains(KEY_ENTITY)) {
            tooltip.add(Component.translatable("tooltip.eliteforge.summon_totem.entity", tag.getString(KEY_ENTITY))
                    .withStyle(ChatFormatting.YELLOW));
        }
        if (tag.contains(KEY_QUALITY)) {
            tooltip.add(Component.translatable("tooltip.eliteforge.summon_totem.quality", tag.getString(KEY_QUALITY))
                    .withStyle(ChatFormatting.YELLOW));
        }
        if (tag.contains(KEY_LEVEL)) {
            tooltip.add(Component.translatable("tooltip.eliteforge.summon_totem.level", tag.getInt(KEY_LEVEL))
                    .withStyle(ChatFormatting.YELLOW));
        }
        if (!tag.contains(KEY_ENTITY) && !tag.contains(KEY_QUALITY) && !tag.contains(KEY_LEVEL)) {
            tooltip.add(Component.translatable("tooltip.eliteforge.summon_totem.random")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }
}
