package com.eliteforge.command;

import com.eliteforge.EliteForge;
import com.eliteforge.ability.Ability;
import net.minecraft.ChatFormatting;
import com.eliteforge.ability.AbilityCategory;
import com.eliteforge.ability.AbilityRegistry;

import com.eliteforge.capability.EliteCapability;
import com.eliteforge.capability.EliteData;
import com.eliteforge.capability.EliteCapabilitySync;
import com.eliteforge.config.EliteForgeConfig;
import com.eliteforge.config.DifficultyMode;
import com.eliteforge.difficulty.DifficultyManager;
import com.eliteforge.difficulty.PlayerExperienceManager;
import com.eliteforge.difficulty.ChunkHeatManager;
import com.eliteforge.spawn.AbilityGenerator;
import com.eliteforge.spawn.EliteEcosystem;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.coordinates.ColumnPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import com.mojang.datafixers.util.Pair;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.ArrayList;

@Mod.EventBusSubscriber(modid = EliteForge.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class EliteForgeCommand {

    private static final String PERMISSION_PREFIX = "commands." + EliteForge.MODID;

    // Suggestion providers
    private static final SuggestionProvider<CommandSourceStack> ABILITY_SUGGESTIONS =
            (context, builder) -> SharedSuggestionProvider.suggest(
                    AbilityRegistry.getAllAbilities().stream().map(a -> a.getId().toString()),
                    builder
            );

    private static final SuggestionProvider<CommandSourceStack> MODE_SUGGESTIONS =
            (context, builder) -> SharedSuggestionProvider.suggest(
                    java.util.Arrays.stream(DifficultyMode.values()).map(DifficultyMode::name),
                    builder
            );

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        CommandBuildContext buildContext = event.getBuildContext();

        registerCommands(dispatcher, buildContext);
    }

    public static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext buildContext) {
        dispatcher.register(
                Commands.literal("eliteforge")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("spawn")
                                .then(Commands.argument("entity", ResourceLocationArgument.id())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                                net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.keySet().stream().map(ResourceLocation::toString),
                                                builder
                                        ))
                                        .executes(context -> spawnElite(context, 1, null))
                                        .then(Commands.argument("level", IntegerArgumentType.integer(1, 9999))
                                                .executes(context -> spawnElite(context, IntegerArgumentType.getInteger(context, "level"), null))
                                                .then(Commands.argument("mode", StringArgumentType.word())
                                                        .suggests(MODE_SUGGESTIONS)
                                                        .executes(context -> spawnElite(context,
                                                                IntegerArgumentType.getInteger(context, "level"),
                                                                StringArgumentType.getString(context, "mode")))
                                                )
                                        )
                                )
                        )
                        .then(Commands.literal("reroll")
                                .then(Commands.argument("target", EntityArgument.entity())
                                        .executes(EliteForgeCommand::rerollAbilities)
                                )
                        )
                        .then(Commands.literal("setlevel")
                                .then(Commands.argument("target", EntityArgument.entity())
                                        .then(Commands.argument("level", IntegerArgumentType.integer(1, 9999))
                                                .executes(EliteForgeCommand::setLevel)
                                        )
                                )
                        )
                        .then(Commands.literal("addability")
                                .then(Commands.argument("target", EntityArgument.entity())
                                        .then(Commands.argument("ability", StringArgumentType.string())
                                                .suggests(ABILITY_SUGGESTIONS)
                                                .executes(context -> addAbility(context, 1))
                                                .then(Commands.argument("level", IntegerArgumentType.integer(1, 5))
                                                        .executes(context -> addAbility(context, IntegerArgumentType.getInteger(context, "level")))
                                                )
                                        )
                                )
                        )
                        .then(Commands.literal("removeability")
                                .then(Commands.argument("target", EntityArgument.entity())
                                        .then(Commands.argument("ability", StringArgumentType.string())
                                                .suggests(ABILITY_SUGGESTIONS)
                                                .executes(EliteForgeCommand::removeAbility)
                                        )
                                )
                        )
                        .then(Commands.literal("heat")
                                .then(Commands.literal("get")
                                        .executes(EliteForgeCommand::getHeatCurrentChunk)
                                        .then(Commands.argument("chunk", ColumnPosArgument.columnPos())
                                                .executes(EliteForgeCommand::getHeatAtChunk)
                                        )
                                )
                                .then(Commands.literal("set")
                                        .then(Commands.argument("chunk", ColumnPosArgument.columnPos())
                                                .then(Commands.argument("value", IntegerArgumentType.integer(0, 100))
                                                        .executes(EliteForgeCommand::setHeat)
                                                )
                                        )
                                )
                                .then(Commands.literal("reset")
                                        .executes(EliteForgeCommand::resetHeatCurrentChunk)
                                        .then(Commands.argument("chunk", ColumnPosArgument.columnPos())
                                                .executes(EliteForgeCommand::resetHeatAtChunk)
                                        )
                                )
                        )
                        .then(Commands.literal("experience")
                                .then(Commands.literal("get")
                                        .executes(context -> getExperience(context, null))
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .executes(context -> getExperience(context, EntityArgument.getPlayer(context, "player")))
                                        )
                                )
                                .then(Commands.literal("set")
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .then(Commands.argument("value", IntegerArgumentType.integer(0))
                                                        .executes(EliteForgeCommand::setExperience)
                                                )
                                        )
                                )
                                .then(Commands.literal("reset")
                                        .executes(context -> resetExperience(context, null))
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .executes(context -> resetExperience(context, EntityArgument.getPlayer(context, "player")))
                                        )
                                )
                        )
                        .then(Commands.literal("creator")
                                .then(Commands.literal("spawn")
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .executes(EliteForgeCommand::creatorSpawn)
                                        )
                                )
                                .then(Commands.literal("awaken")
                                        .then(Commands.argument("entity", EntityArgument.entity())
                                                .executes(EliteForgeCommand::creatorAwaken)
                                        )
                                )
                                .then(Commands.literal("list")
                                        .executes(EliteForgeCommand::creatorList)
                                )
                                .then(Commands.literal("killall")
                                        .executes(EliteForgeCommand::creatorKillAll)
                                )
                        )
                        .then(Commands.literal("revenge")
                                .then(Commands.literal("trigger")
                                        .then(Commands.argument("chunkX", IntegerArgumentType.integer())
                                                .then(Commands.argument("chunkZ", IntegerArgumentType.integer())
                                                        .executes(EliteForgeCommand::revengeTrigger)
                                                )
                                        )
                                )
                                .then(Commands.literal("status")
                                        .executes(EliteForgeCommand::revengeStatus)
                                )
                        )
                        .then(Commands.literal("nearby")
                                .executes(EliteForgeCommand::nearbyElites)
                        )
                        .then(Commands.literal("config")
                                .then(Commands.literal("mode")
                                        .then(Commands.argument("mode", StringArgumentType.word())
                                                .suggests(MODE_SUGGESTIONS)
                                                .executes(EliteForgeCommand::setDifficultyMode)
                                        )
                                )
                                .then(Commands.literal("preset")
                                        .then(Commands.argument("preset", StringArgumentType.word())
                                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                                        java.util.Arrays.stream(com.eliteforge.config.ConfigPreset.values())
                                                                .map(com.eliteforge.config.ConfigPreset::getId),
                                                        builder))
                                                .executes(EliteForgeCommand::applyPreset)
                                        )
                                )
                                .then(Commands.literal("reload")
                                        .executes(EliteForgeCommand::reloadConfig)
                                )
                                .then(Commands.literal("info")
                                        .executes(EliteForgeCommand::showConfigInfo)
                                )
                        )
        );

        // Register alias /ef
        dispatcher.register(
                Commands.literal("ef")
                        .requires(source -> source.hasPermission(2))
                        .redirect(dispatcher.getRoot().getChild("eliteforge"))
        );
    }

    // ============ SPAWN COMMAND ============

    private static int spawnElite(CommandContext<CommandSourceStack> context, int level, String modeStr) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ResourceLocation entityId = ResourceLocationArgument.getId(context, "entity");

        // Resolve entity type
        var entityType = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getOptional(entityId);
        if (entityType.isEmpty()) {
            source.sendFailure(Component.translatable(PERMISSION_PREFIX + ".spawn.invalid_entity", entityId));
            return 0;
        }

        // Parse difficulty mode
        DifficultyMode mode = null;
        if (modeStr != null) {
            try {
                mode = DifficultyMode.valueOf(modeStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                source.sendFailure(Component.translatable(PERMISSION_PREFIX + ".spawn.invalid_mode", modeStr));
                return 0;
            }
        }

        ServerLevel level_ = source.getLevel();
        Vec3 pos = source.getPosition();
        Vec2 rot = source.getRotation();

        // Create entity
        Entity entity = entityType.get().create(level_);
        if (entity == null) {
            source.sendFailure(Component.translatable(PERMISSION_PREFIX + ".spawn.failed", entityId));
            return 0;
        }

        entity.moveTo(pos.x, pos.y, pos.z, rot.y, rot.x);

        if (entity instanceof LivingEntity living) {
            // Apply elite capability
            EliteCapability cap = living.getCapability(EliteCapability.CAPABILITY).orElse(null);
            if (cap != null) {
                cap.setElite(true);
                EliteData data = cap.getEliteData();
                data.setLevel(level);
                DifficultyMode effectiveMode = mode != null ? mode : EliteForgeConfig.COMMON.difficultyMode.get();
                data.setSpawnMode(effectiveMode);
                // Generate abilities based on level and mode
                List<Pair<Ability, Integer>> generatedAbilities = AbilityGenerator.generateAbilities(level, effectiveMode, entityType.get());
                for (Pair<Ability, Integer> pair : generatedAbilities) {
                    data.addAbility(pair.getFirst().getId().toString(), pair.getSecond());
                    // Call onApply for each new ability so passive effects and attribute modifiers are applied
                    try {
                        pair.getFirst().onApply(living, pair.getSecond());
                    } catch (Exception e) {
                        EliteForge.LOGGER.error("Error in onApply for {}: {}", pair.getFirst().getIdString(), e.getMessage());
                    }
                }
                cap.setEliteData(data);

                // Apply stat scaling (health/damage/speed multipliers) so the elite isn't just a normal mob with a flag
                DifficultyManager.INSTANCE.applyEliteModifiers(living, level, generatedAbilities);

                // Sync to clients so nameplate, particles, and ability icons render correctly
                EliteCapabilitySync.broadcastEliteDataUpdate(living, data);
            }
        }

        level_.addFreshEntity(entity);

        // Register the command-spawned elite with the elite tracker so its abilities
        // tick, it participates in dynamic strengthening/awakening/revenge, and the
        // summon leash (if applicable) processes it.
        if (entity instanceof LivingEntity livingEntity) {
            com.eliteforge.spawn.EliteEventHandler.trackElite(livingEntity);
        }

        String modeText = mode != null ? mode.name() : EliteForgeConfig.COMMON.difficultyMode.get().name();
        source.sendSuccess(() -> Component.translatable(PERMISSION_PREFIX + ".spawn.success",
                entityId, level, modeText), true);
        return 1;
    }

    // ============ REROLL COMMAND ============

    private static int rerollAbilities(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        Entity target = EntityArgument.getEntity(context, "target");

        if (!(target instanceof LivingEntity living)) {
            source.sendFailure(Component.translatable(PERMISSION_PREFIX + ".target_not_living"));
            return 0;
        }

        EliteCapability cap = living.getCapability(EliteCapability.CAPABILITY).orElse(null);
        if (cap == null || !cap.isElite()) {
            source.sendFailure(Component.translatable(PERMISSION_PREFIX + ".target_not_elite"));
            return 0;
        }

        // Reroll abilities
        EliteData data = cap.getEliteData();
        // Call onRemove for each existing ability so attribute modifiers and persistent effects are cleaned up
        for (Map.Entry<String, Integer> entry : new HashSet<>(data.getAbilities().entrySet())) {
            Ability oldAbility = AbilityRegistry.getAbility(entry.getKey());
            if (oldAbility != null) {
                try {
                    oldAbility.onRemove(living, entry.getValue());
                } catch (Exception e) {
                    EliteForge.LOGGER.error("Error in onRemove for {}: {}", entry.getKey(), e.getMessage());
                }
            }
            data.removeAbility(entry.getKey());
        }
        // Generate new abilities
        List<Pair<Ability, Integer>> generatedAbilities = AbilityGenerator.generateAbilities(
                data.getLevel(), data.getSpawnMode(), living.getType());
        for (Pair<Ability, Integer> pair : generatedAbilities) {
            data.addAbility(pair.getFirst().getId().toString(), pair.getSecond());
            // Call onApply for each new ability so passive effects and attribute modifiers are applied
            try {
                pair.getFirst().onApply(living, pair.getSecond());
            } catch (Exception e) {
                EliteForge.LOGGER.error("Error in onApply for {}: {}", pair.getFirst().getIdString(), e.getMessage());
            }
        }
        cap.setEliteData(data);
        EliteCapabilitySync.broadcastEliteDataUpdate(living, data);

        source.sendSuccess(() -> Component.translatable(PERMISSION_PREFIX + ".reroll.success",
                living.getName().getString(), data.getAbilities().size()), true);
        return 1;
    }

    // ============ SET LEVEL COMMAND ============

    private static int setLevel(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        Entity target = EntityArgument.getEntity(context, "target");
        int level = IntegerArgumentType.getInteger(context, "level");

        if (!(target instanceof LivingEntity living)) {
            source.sendFailure(Component.translatable(PERMISSION_PREFIX + ".target_not_living"));
            return 0;
        }

        EliteCapability cap = living.getCapability(EliteCapability.CAPABILITY).orElse(null);
        if (cap == null || !cap.isElite()) {
            source.sendFailure(Component.translatable(PERMISSION_PREFIX + ".target_not_elite"));
            return 0;
        }

        EliteData data = cap.getEliteData();
        int oldLevel = data.getLevel();
        // Clamp to the configured max so /ef setlevel 9999 doesn't exceed maxEliteLevel.
        int maxLvl = EliteForgeConfig.COMMON.maxEliteLevel.get();
        level = Math.max(1, Math.min(maxLvl, level));
        data.setLevel(level);

        // Call onRemove for each existing ability so attribute modifiers and persistent effects are cleaned up
        for (Map.Entry<String, Integer> entry : new HashSet<>(data.getAbilities().entrySet())) {
            Ability oldAbility = AbilityRegistry.getAbility(entry.getKey());
            if (oldAbility != null) {
                try {
                    oldAbility.onRemove(living, entry.getValue());
                } catch (Exception e) {
                    EliteForge.LOGGER.error("Error in onRemove for {}: {}", entry.getKey(), e.getMessage());
                }
            }
            data.removeAbility(entry.getKey());
        }
        // Regenerate abilities for new level
        List<Pair<Ability, Integer>> generatedAbilities = AbilityGenerator.generateAbilities(
                level, data.getSpawnMode(), living.getType());
        for (Pair<Ability, Integer> pair : generatedAbilities) {
            data.addAbility(pair.getFirst().getId().toString(), pair.getSecond());
            // Call onApply for each new ability so passive effects and attribute modifiers are applied
            try {
                pair.getFirst().onApply(living, pair.getSecond());
            } catch (Exception e) {
                EliteForge.LOGGER.error("Error in onApply for {}: {}", pair.getFirst().getIdString(), e.getMessage());
            }
        }
        cap.setEliteData(data);
        EliteCapabilitySync.broadcastEliteDataUpdate(living, data);

        final int finalLevel = level;
        source.sendSuccess(() -> Component.translatable(PERMISSION_PREFIX + ".setlevel.success",
                living.getName().getString(), oldLevel, finalLevel), true);
        return 1;
    }

    // ============ ADD ABILITY COMMAND ============

    private static int addAbility(CommandContext<CommandSourceStack> context, int level) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        Entity target = EntityArgument.getEntity(context, "target");
        String abilityIdStr = StringArgumentType.getString(context, "ability");

        if (!(target instanceof LivingEntity living)) {
            source.sendFailure(Component.translatable(PERMISSION_PREFIX + ".target_not_living"));
            return 0;
        }

        EliteCapability cap = living.getCapability(EliteCapability.CAPABILITY).orElse(null);
        if (cap == null || !cap.isElite()) {
            source.sendFailure(Component.translatable(PERMISSION_PREFIX + ".target_not_elite"));
            return 0;
        }

        // Parse ability ID
        ResourceLocation abilityId = ResourceLocation.tryParse(abilityIdStr);
        if (abilityId == null) {
            source.sendFailure(Component.translatable(PERMISSION_PREFIX + ".addability.invalid_id", abilityIdStr));
            return 0;
        }

        Ability ability = AbilityRegistry.getAbility(abilityId);
        if (ability == null) {
            source.sendFailure(Component.translatable(PERMISSION_PREFIX + ".addability.not_found", abilityId));
            return 0;
        }

        // Add ability — check return value in case the maxAbilities limit rejects the addition
        boolean added = cap.getEliteData().addAbility(abilityId.toString(), level);
        if (!added) {
            source.sendFailure(Component.translatable(PERMISSION_PREFIX + ".addability.limit_reached",
                    living.getName().getString(), abilityId, cap.getEliteData().getMaxAbilities()));
            return 0;
        }
        ability.onApply(living, level);

        // Sync the updated ability list to tracking clients so icons/nameplate update.
        EliteCapabilitySync.broadcastEliteDataUpdate(living, cap.getEliteData());

        source.sendSuccess(() -> Component.translatable(PERMISSION_PREFIX + ".addability.success",
                living.getName().getString(), abilityId, level), true);
        return 1;
    }

    // ============ REMOVE ABILITY COMMAND ============

    private static int removeAbility(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        Entity target = EntityArgument.getEntity(context, "target");
        String abilityIdStr = StringArgumentType.getString(context, "ability");

        if (!(target instanceof LivingEntity living)) {
            source.sendFailure(Component.translatable(PERMISSION_PREFIX + ".target_not_living"));
            return 0;
        }

        EliteCapability cap = living.getCapability(EliteCapability.CAPABILITY).orElse(null);
        if (cap == null || !cap.isElite()) {
            source.sendFailure(Component.translatable(PERMISSION_PREFIX + ".target_not_elite"));
            return 0;
        }

        ResourceLocation abilityId = ResourceLocation.tryParse(abilityIdStr);
        if (abilityId == null) {
            source.sendFailure(Component.translatable(PERMISSION_PREFIX + ".removeability.invalid_id", abilityIdStr));
            return 0;
        }

        Ability ability = AbilityRegistry.getAbility(abilityId);
        if (ability == null) {
            source.sendFailure(Component.translatable(PERMISSION_PREFIX + ".removeability.not_found", abilityId));
            return 0;
        }

        // Check if entity has this ability
        EliteData data = cap.getEliteData();
        if (!data.hasAbility(abilityId.toString())) {
            source.sendFailure(Component.translatable(PERMISSION_PREFIX + ".removeability.not_present",
                    living.getName().getString(), abilityId));
            return 0;
        }

        int oldLevel = data.getAbilityLevel(abilityId.toString());
        data.removeAbility(abilityId.toString());
        ability.onRemove(living, oldLevel);

        // Sync the updated ability list to tracking clients so icons/nameplate update.
        EliteCapabilitySync.broadcastEliteDataUpdate(living, data);

        source.sendSuccess(() -> Component.translatable(PERMISSION_PREFIX + ".removeability.success",
                living.getName().getString(), abilityId), true);
        return 1;
    }

    // ============ HEAT COMMANDS ============

    private static int getHeatCurrentChunk(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        BlockPos pos = BlockPos.containing(source.getPosition());
        ChunkPos chunkPos = new ChunkPos(pos);

        ChunkHeatManager heatManager = ChunkHeatManager.get(level);
        int heat = (int) heatManager.getHeat(level, chunkPos);

        source.sendSuccess(() -> Component.translatable(PERMISSION_PREFIX + ".heat.get",
                chunkPos.x, chunkPos.z, heat), false);
        return heat;
    }

    private static int getHeatAtChunk(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        var columnPos = ColumnPosArgument.getColumnPos(context, "chunk");
        ChunkPos chunkPos = new ChunkPos(columnPos.x() >> 4, columnPos.z() >> 4);

        ChunkHeatManager heatManager = ChunkHeatManager.get(level);
        int heat = (int) heatManager.getHeat(level, chunkPos);

        source.sendSuccess(() -> Component.translatable(PERMISSION_PREFIX + ".heat.get",
                chunkPos.x, chunkPos.z, heat), false);
        return heat;
    }

    private static int setHeat(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        var columnPos = ColumnPosArgument.getColumnPos(context, "chunk");
        int value = IntegerArgumentType.getInteger(context, "value");
        ChunkPos chunkPos = new ChunkPos(columnPos.x() >> 4, columnPos.z() >> 4);

        // Set heat to a specific value: reduce current heat to 0, then add the target amount
        ChunkHeatManager heatManager = ChunkHeatManager.get(level);
        float currentHeat = heatManager.getHeat(level, chunkPos);
        heatManager.reduceHeat(level, chunkPos, currentHeat);
        heatManager.addHeat(level, chunkPos, (float) value);

        source.sendSuccess(() -> Component.translatable(PERMISSION_PREFIX + ".heat.set",
                chunkPos.x, chunkPos.z, value), true);
        return value;
    }

    private static int resetHeatCurrentChunk(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        BlockPos pos = BlockPos.containing(source.getPosition());
        ChunkPos chunkPos = new ChunkPos(pos);

        // Reset heat to 0 by reducing all current heat
        ChunkHeatManager heatManager = ChunkHeatManager.get(level);
        float currentHeat = heatManager.getHeat(level, chunkPos);
        heatManager.reduceHeat(level, chunkPos, currentHeat);

        source.sendSuccess(() -> Component.translatable(PERMISSION_PREFIX + ".heat.reset",
                chunkPos.x, chunkPos.z), true);
        return 0;
    }

    private static int resetHeatAtChunk(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        var columnPos = ColumnPosArgument.getColumnPos(context, "chunk");
        ChunkPos chunkPos = new ChunkPos(columnPos.x() >> 4, columnPos.z() >> 4);

        // Reset heat to 0 by reducing all current heat
        ChunkHeatManager heatManager = ChunkHeatManager.get(level);
        float currentHeat = heatManager.getHeat(level, chunkPos);
        heatManager.reduceHeat(level, chunkPos, currentHeat);

        source.sendSuccess(() -> Component.translatable(PERMISSION_PREFIX + ".heat.reset",
                chunkPos.x, chunkPos.z), true);
        return 0;
    }

    // ============ EXPERIENCE COMMANDS ============

    private static int getExperience(CommandContext<CommandSourceStack> context, ServerPlayer targetPlayer) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        if (targetPlayer == null) {
            targetPlayer = source.getPlayerOrException();
        }
        final ServerPlayer finalTargetPlayer = targetPlayer;

        ServerLevel level = source.getLevel();
        PlayerExperienceManager expManager = PlayerExperienceManager.get(level);
        final int exp = (int) expManager.getPlayerExperience(finalTargetPlayer);

        source.sendSuccess(() -> Component.translatable(PERMISSION_PREFIX + ".experience.get",
                finalTargetPlayer.getName().getString(), exp), false);
        return exp;
    }

    private static int setExperience(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer target = EntityArgument.getPlayer(context, "player");
        int value = IntegerArgumentType.getInteger(context, "value");

        // Set experience: reset to 0, then add the target amount
        ServerLevel level = source.getLevel();
        PlayerExperienceManager expManager = PlayerExperienceManager.get(level);
        expManager.resetPlayerExperience(target.getUUID());
        expManager.addExperience(target, (float) value);

        source.sendSuccess(() -> Component.translatable(PERMISSION_PREFIX + ".experience.set",
                target.getName().getString(), value), true);
        return value;
    }

    private static int resetExperience(CommandContext<CommandSourceStack> context, ServerPlayer targetPlayer) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        if (targetPlayer == null) {
            targetPlayer = source.getPlayerOrException();
        }
        final ServerPlayer finalTargetPlayer = targetPlayer;

        ServerLevel level = source.getLevel();
        PlayerExperienceManager expManager = PlayerExperienceManager.get(level);
        expManager.resetPlayerExperience(finalTargetPlayer.getUUID());

        source.sendSuccess(() -> Component.translatable(PERMISSION_PREFIX + ".experience.reset",
                finalTargetPlayer.getName().getString()), true);
        return 0;
    }

    // ============ CREATOR COMMANDS ============

    private static int creatorSpawn(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer targetPlayer = EntityArgument.getPlayer(context, "player");

        if (!EliteForgeConfig.SERVER.enableCreatorTier.get()) {
            source.sendFailure(Component.translatable(PERMISSION_PREFIX + ".creator.disabled"));
            return 0;
        }

        ServerLevel level = source.getLevel();
        Vec3 pos = targetPlayer.position();

        // Pick a random hostile mob type near the player (exclude passive mobs and armor stands)
        var entityTypes = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.entrySet().stream()
                .filter(e -> LivingEntity.class.isAssignableFrom(e.getValue().getBaseClass()))
                .filter(e -> net.minecraft.world.entity.monster.Monster.class.isAssignableFrom(e.getValue().getBaseClass()))
                .toList();

        if (entityTypes.isEmpty()) {
            source.sendFailure(Component.translatable(PERMISSION_PREFIX + ".spawn.failed", "no valid entity"));
            return 0;
        }

        var entry = entityTypes.get(ThreadLocalRandom.current().nextInt(entityTypes.size()));
        Entity entity = entry.getValue().create(level);
        if (entity == null) {
            source.sendFailure(Component.translatable(PERMISSION_PREFIX + ".spawn.failed", entry.getKey().toString()));
            return 0;
        }

        // Spawn near the target player with slight offset
        double offsetX = (ThreadLocalRandom.current().nextDouble() - 0.5) * 8;
        double offsetZ = (ThreadLocalRandom.current().nextDouble() - 0.5) * 8;
        entity.moveTo(pos.x + offsetX, pos.y, pos.z + offsetZ, level.random.nextFloat() * 360F, 0);

        if (entity instanceof LivingEntity living) {
            EliteCapability cap = living.getCapability(EliteCapability.CAPABILITY).orElse(null);
            if (cap != null) {
                cap.setElite(true);
                EliteData data = cap.getEliteData();
                data.setLevel(5); // Creator-tier is always max level
                data.setQualityTier(com.eliteforge.quality.QualityTier.MYTHIC);

                // Assign a random creator-category ability at level I.
                // AbilityRegistry.getAbilitiesByCategory(...) returns Collection<Ability>,
                // so we materialize it into a List for indexed random selection.
                java.util.List<Ability> creatorAbilities = new java.util.ArrayList<>(
                        AbilityRegistry.getAbilitiesByCategory(AbilityCategory.CREATOR));
                if (!creatorAbilities.isEmpty()) {
                    Ability chosen = creatorAbilities.get(ThreadLocalRandom.current().nextInt(creatorAbilities.size()));
                    data.addAbility(chosen.getId().toString(), 1);
                    // Set all creator-tier state fields so the entity is properly tracked
                    data.setCreatorEntity(true);
                    data.setCreatorAbilityId(chosen.getIdString());
                    data.setCreatorAbilityLevel(1);
                    chosen.onApply(living, 1);
                    // Register in ecosystem for creator tracking, nurturing, and synergy checks
                    EliteEcosystem.registerCreator(living, chosen.getIdString(), 1);
                }

                // Generate additional abilities
                List<Pair<Ability, Integer>> generatedAbilities = AbilityGenerator.generateAbilities(
                        5, EliteForgeConfig.COMMON.difficultyMode.get(), living.getType());
                for (Pair<Ability, Integer> genPair : generatedAbilities) {
                    data.addAbility(genPair.getFirst().getId().toString(), genPair.getSecond());
                    try {
                        genPair.getFirst().onApply(living, genPair.getSecond());
                    } catch (Exception e) {
                        EliteForge.LOGGER.error("Error in onApply for {}: {}",
                                genPair.getFirst().getIdString(), e.getMessage());
                    }
                }
                cap.setEliteData(data);
                EliteCapabilitySync.broadcastEliteDataUpdate(living, data);
            }
        }

        level.addFreshEntity(entity);

        // Spawn effects: particles and sound
        if (entity instanceof LivingEntity living) {
            level.sendParticles(
                    net.minecraft.core.particles.ParticleTypes.TOTEM_OF_UNDYING,
                    living.getX(), living.getY() + 1, living.getZ(),
                    60, 0.5, 1.0, 0.5, 0.5
            );
            level.sendParticles(
                    net.minecraft.core.particles.ParticleTypes.DRAGON_BREATH,
                    living.getX(), living.getY() + 0.5, living.getZ(),
                    30, 0.3, 0.5, 0.3, 0.1
            );
        }

        // Announce to all players
        var entityId = entry.getKey();
        source.getServer().getPlayerList().broadcastSystemMessage(
                Component.translatable(PERMISSION_PREFIX + ".creator.spawn_announce", entityId.toString(), targetPlayer.getName()),
                false
        );

        source.sendSuccess(() -> Component.translatable(PERMISSION_PREFIX + ".creator.spawn_success",
                entityId.toString(), targetPlayer.getName().getString()), true);
        return 1;
    }

    private static int creatorAwaken(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        Entity target = EntityArgument.getEntity(context, "entity");

        if (!EliteForgeConfig.SERVER.enableCreatorTier.get()) {
            source.sendFailure(Component.translatable(PERMISSION_PREFIX + ".creator.disabled"));
            return 0;
        }

        if (!(target instanceof LivingEntity living)) {
            source.sendFailure(Component.translatable(PERMISSION_PREFIX + ".target_not_living"));
            return 0;
        }

        EliteCapability cap = living.getCapability(EliteCapability.CAPABILITY).orElse(null);
        if (cap == null || !cap.isElite()) {
            source.sendFailure(Component.translatable(PERMISSION_PREFIX + ".target_not_elite"));
            return 0;
        }

        EliteData data = cap.getEliteData();
        if (data.getQualityTier() != com.eliteforge.quality.QualityTier.LEGENDARY) {
            source.sendFailure(Component.translatable(PERMISSION_PREFIX + ".creator.not_legendary",
                    living.getName().getString()));
            return 0;
        }

        // Awaken: upgrade to MYTHIC quality and add a creator ability
        data.setQualityTier(com.eliteforge.quality.QualityTier.MYTHIC);
        data.setLevel(5);

        // AbilityRegistry.getAbilitiesByCategory(...) returns Collection<Ability>;
        // materialize into a List for indexed random selection.
        java.util.List<Ability> creatorAbilities = new java.util.ArrayList<>(
                AbilityRegistry.getAbilitiesByCategory(AbilityCategory.CREATOR));
        if (!creatorAbilities.isEmpty()) {
            Ability chosen = creatorAbilities.get(ThreadLocalRandom.current().nextInt(creatorAbilities.size()));
            data.addAbility(chosen.getId().toString(), 1);
            // Set all creator-tier state fields so the entity is properly tracked
            data.setCreatorEntity(true);
            data.setCreatorAbilityId(chosen.getIdString());
            data.setCreatorAbilityLevel(1);
            chosen.onApply(living, 1);
            // Register in ecosystem for creator tracking, nurturing, and synergy checks
            EliteEcosystem.registerCreator(living, chosen.getIdString(), 1);
        }
        cap.setEliteData(data);
        EliteCapabilitySync.broadcastEliteDataUpdate(living, data);

        // Awakening effects
        ServerLevel level = source.getLevel();
        level.sendParticles(
                net.minecraft.core.particles.ParticleTypes.SONIC_BOOM,
                living.getX(), living.getY() + 1, living.getZ(),
                5, 0.5, 0.5, 0.5, 0
        );
        level.sendParticles(
                net.minecraft.core.particles.ParticleTypes.TOTEM_OF_UNDYING,
                living.getX(), living.getY() + 0.5, living.getZ(),
                50, 0.5, 1.0, 0.5, 0.3
        );

        // Announce awakening
        source.getServer().getPlayerList().broadcastSystemMessage(
                Component.translatable(PERMISSION_PREFIX + ".creator.awaken_announce", living.getName()),
                false
        );

        source.sendSuccess(() -> Component.translatable(PERMISSION_PREFIX + ".creator.awaken_success",
                living.getName().getString()), true);
        return 1;
    }

    private static int creatorList(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();

        List<LivingEntity> creators = new ArrayList<>();
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof LivingEntity living) {
                EliteCapability cap = living.getCapability(EliteCapability.CAPABILITY).orElse(null);
                if (cap != null && cap.isElite() && cap.getEliteData().getQualityTier() == com.eliteforge.quality.QualityTier.MYTHIC) {
                    creators.add(living);
                }
            }
        }

        if (creators.isEmpty()) {
            source.sendSuccess(() -> Component.translatable(PERMISSION_PREFIX + ".creator.list_empty"), false);
            return 0;
        }

        MutableComponent header = Component.translatable("commands.eliteforge.creator.list_header")
                .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFF4444)));
        for (LivingEntity creator : creators) {
            EliteCapability cap = creator.getCapability(EliteCapability.CAPABILITY).orElse(null);
            if (cap == null) continue;

            EliteData data = cap.getEliteData();
            String abilities = data.getAbilities().entrySet().stream()
                    .map(e -> {
                        ResourceLocation rl = ResourceLocation.tryParse(e.getKey());
                        Ability ab = rl != null ? AbilityRegistry.getAbility(rl) : null;
                        String name = ab != null ? Component.translatable(ab.getNameKey()).getString() : e.getKey();
                        return name + " Lv." + e.getValue();
                    })
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("None");

            header.append(Component.translatable("commands.eliteforge.creator.list_entry",
                    creator.getName(),
                    data.getQualityTier().name(),
                    data.getLevel(),
                    abilities,
                    (float) creator.getX(),
                    (float) creator.getY(),
                    (float) creator.getZ()
            ).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFCC00))));
        }

        source.sendSuccess(() -> header, false);
        return creators.size();
    }

    private static int creatorKillAll(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();

        List<LivingEntity> creators = new ArrayList<>();
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof LivingEntity living) {
                EliteCapability cap = living.getCapability(EliteCapability.CAPABILITY).orElse(null);
                if (cap != null && cap.isElite() && cap.getEliteData().getQualityTier() == com.eliteforge.quality.QualityTier.MYTHIC) {
                    creators.add(living);
                }
            }
        }

        if (creators.isEmpty()) {
            source.sendSuccess(() -> Component.translatable(PERMISSION_PREFIX + ".creator.list_empty"), false);
            return 0;
        }

        int killed = 0;
        for (LivingEntity creator : creators) {
            // C6 Annihilate explosion effect at creator position
            level.explode(null, creator.getX(), creator.getY(), creator.getZ(),
                    4.0F, net.minecraft.world.level.Level.ExplosionInteraction.NONE);
            creator.kill();
            killed++;
        }

        final int count = killed;
        source.sendSuccess(() -> Component.translatable(PERMISSION_PREFIX + ".creator.killall_success", count), true);
        return killed;
    }

    // ============ REVENGE COMMANDS ============

    private static int revengeTrigger(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        int chunkX = IntegerArgumentType.getInteger(context, "chunkX");
        int chunkZ = IntegerArgumentType.getInteger(context, "chunkZ");
        ServerLevel level = source.getLevel();
        ChunkPos chunkPos = new ChunkPos(chunkX, chunkZ);

        // Set chunk heat to max to simulate revenge conditions
        ChunkHeatManager heatManager = ChunkHeatManager.get(level);
        float currentHeat = heatManager.getHeat(level, chunkPos);
        heatManager.reduceHeat(level, chunkPos, currentHeat);
        heatManager.addHeat(level, chunkPos, EliteForgeConfig.SERVER.chunkHeatMax.get().floatValue());

        source.sendSuccess(() -> Component.translatable(PERMISSION_PREFIX + ".revenge.trigger_success",
                chunkX, chunkZ), true);
        return 1;
    }

    private static int revengeStatus(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        BlockPos pos = BlockPos.containing(source.getPosition());
        ChunkPos chunkPos = new ChunkPos(pos);

        ChunkHeatManager heatManager = ChunkHeatManager.get(level);
        int heat = (int) heatManager.getHeat(level, chunkPos);
        int threshold = EliteForgeConfig.SERVER.revengeKillThreshold.get();
        boolean revengeEnabled = EliteForgeConfig.SERVER.enableRevengeSystem.get();

        MutableComponent status = Component.empty();
        status.append(Component.translatable("commands.eliteforge.revenge.status_header").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFF6600))));
        status.append(Component.translatable(revengeEnabled
                        ? "commands.eliteforge.revenge.system_enabled"
                        : "commands.eliteforge.revenge.system_disabled")
                .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))));
        status.append(Component.translatable("commands.eliteforge.revenge.chunk_heat", chunkPos.x, chunkPos.z, heat)
                .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))));
        status.append(Component.translatable("commands.eliteforge.revenge.kill_threshold", threshold)
                .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))));
        status.append(Component.translatable("commands.eliteforge.revenge.heat_max", heat, EliteForgeConfig.SERVER.chunkHeatMax.get().intValue())
                .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))));

        source.sendSuccess(() -> status, false);
        return 1;
    }

    // ============ NEARBY COMMAND ============

    private static int nearbyElites(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        net.minecraft.server.level.ServerPlayer player = source.getPlayerOrException();
        net.minecraft.server.level.ServerLevel level = player.serverLevel();

        // Scan a 64-block radius for elites.
        net.minecraft.world.phys.AABB area = player.getBoundingBox().inflate(64.0);
        java.util.List<net.minecraft.world.entity.LivingEntity> nearby = level.getEntitiesOfClass(
                net.minecraft.world.entity.LivingEntity.class, area,
                e -> e.isAlive() && e != player && e.getCapability(com.eliteforge.capability.EliteCapability.CAPABILITY)
                        .map(com.eliteforge.capability.EliteCapability::isElite)
                        .orElse(false));

        if (nearby.isEmpty()) {
            source.sendSuccess(() -> Component.translatable(PERMISSION_PREFIX + ".nearby.none")
                    .withStyle(ChatFormatting.GRAY), false);
            return 0;
        }

        // Header
        source.sendSuccess(() -> Component.translatable(PERMISSION_PREFIX + ".nearby.header", nearby.size())
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);

        // List each elite (limit to 20 to avoid chat spam).
        int shown = 0;
        for (net.minecraft.world.entity.LivingEntity e : nearby) {
            if (shown >= 20) break;
            shown++;
            com.eliteforge.capability.EliteData data = e.getCapability(com.eliteforge.capability.EliteCapability.CAPABILITY)
                    .map(com.eliteforge.capability.EliteCapability::getEliteData)
                    .orElse(null);
            if (data == null) continue;
            int dist = (int) Math.round(e.distanceTo(player));
            int abilityCount = data.getAbilities().size();
            boolean creator = data.isCreatorEntity();
            ChatFormatting color = data.getQualityTier().getChatColor();
            Component line = Component.literal("• ")
                    .append(e.getName().copy().withStyle(color))
                    .append(Component.literal(" Lv." + data.getLevel()).withStyle(color))
                    .append(Component.literal(" [" + data.getQualityTier().name() + "]").withStyle(color))
                    .append(creator ? Component.literal(" ★CREATOR").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD) : Component.empty())
                    .append(Component.literal(" " + abilityCount + " abilities").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(" (" + dist + "m)").withStyle(ChatFormatting.DARK_GRAY));
            source.sendSuccess(() -> line, false);
        }
        if (nearby.size() > 20) {
            int remaining = nearby.size() - 20;
            source.sendSuccess(() -> Component.translatable(PERMISSION_PREFIX + ".nearby.more", remaining)
                    .withStyle(ChatFormatting.DARK_GRAY), false);
        }
        return nearby.size();
    }

    // ============ CONFIG COMMANDS ============

    private static int setDifficultyMode(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        String modeStr = StringArgumentType.getString(context, "mode");

        DifficultyMode mode;
        try {
            mode = DifficultyMode.valueOf(modeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            source.sendFailure(Component.translatable(PERMISSION_PREFIX + ".config.invalid_mode", modeStr));
            return 0;
        }

        DifficultyMode oldMode = EliteForgeConfig.COMMON.difficultyMode.get();
        EliteForgeConfig.COMMON.difficultyMode.set(mode);
        EliteForgeConfig.COMMON_SPEC.save();

        source.sendSuccess(() -> Component.translatable(PERMISSION_PREFIX + ".config.mode_changed",
                oldMode.name(), mode.name()), true);
        return 1;
    }

    private static int reloadConfig(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        // NOTE: This command is named "reload" but actually SAVES in-memory config to disk
        // (Forge 1.20.1's ConfigTracker.loadConfig API is private and would require an
        // access transformer to reload FROM disk). The save behavior is kept because it
        // IS useful — operators editing values via /eliteforge setlevel etc. want those
        // changes persisted. The success message below is accurate about what happens.
        try {
            EliteForgeConfig.COMMON_SPEC.save();
            EliteForgeConfig.SERVER_SPEC.save();
            source.sendSuccess(() -> Component.translatable(PERMISSION_PREFIX + ".config.saved"), true);
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.translatable(PERMISSION_PREFIX + ".config.reload_failed", e.getMessage()));
            return 0;
        }
    }

    private static int applyPreset(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String presetName = StringArgumentType.getString(context, "preset");
        com.eliteforge.config.ConfigPreset preset = com.eliteforge.config.ConfigPreset.byId(presetName);
        if (preset == null) {
            source.sendFailure(Component.translatable(PERMISSION_PREFIX + ".config.preset_unknown", presetName));
            return 0;
        }
        try {
            preset.apply();
            EliteForgeConfig.COMMON_SPEC.save();
            EliteForgeConfig.SERVER_SPEC.save();
            source.sendSuccess(() -> Component.translatable(PERMISSION_PREFIX + ".config.preset_applied",
                    preset.getDisplayName()), true);
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.translatable(PERMISSION_PREFIX + ".config.preset_failed", e.getMessage()));
            return 0;
        }
    }

    private static int showConfigInfo(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        source.sendSuccess(() -> {
            MutableComponent info = Component.empty();
            info.append(Component.translatable("commands.eliteforge.config.info_header").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFAA00))));
            info.append(Component.translatable("commands.eliteforge.config.difficulty_mode", EliteForgeConfig.COMMON.difficultyMode.get().name()).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))));
            info.append(Component.translatable("commands.eliteforge.config.enable_elite_mobs", EliteForgeConfig.COMMON.enableEliteMobs.get()).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))));
            info.append(Component.translatable("commands.eliteforge.config.global_spawn_chance", EliteForgeConfig.COMMON.globalSpawnChance.get()).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))));
            info.append(Component.translatable("commands.eliteforge.config.max_abilities", EliteForgeConfig.COMMON.maxAbilitiesPerElite.get()).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))));
            info.append(Component.translatable("commands.eliteforge.config.heat_decay_rate", EliteForgeConfig.SERVER.chunkHeatDecayRate.get()).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))));
            info.append(Component.translatable("commands.eliteforge.config.heat_max", EliteForgeConfig.SERVER.chunkHeatMax.get()).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))));
            info.append(Component.translatable("commands.eliteforge.config.enable_creator_tier", EliteForgeConfig.SERVER.enableCreatorTier.get()).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))));
            info.append(Component.translatable("commands.eliteforge.config.revenge_kill_threshold", EliteForgeConfig.SERVER.revengeKillThreshold.get()).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))));
            info.append(Component.translatable("commands.eliteforge.config.enable_revenge", EliteForgeConfig.SERVER.enableRevengeSystem.get()).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))));
            info.append(Component.translatable("commands.eliteforge.config.enable_mutual_exclusion", EliteForgeConfig.SERVER.enableMutualExclusion.get()).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))));
            info.append(Component.translatable("commands.eliteforge.config.show_icons", EliteForgeConfig.CLIENT.showAbilityIcons.get()).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))));
            info.append(Component.translatable("commands.eliteforge.config.show_nameplate", EliteForgeConfig.CLIENT.showEliteNamePlate.get()).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))));
            info.append(Component.translatable("commands.eliteforge.config.icon_distance", EliteForgeConfig.CLIENT.iconRenderDistance.get()).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))));
            return info;
        }, false);

        return 1;
    }
}
