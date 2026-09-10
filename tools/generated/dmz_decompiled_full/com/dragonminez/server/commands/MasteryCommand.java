package com.dragonminez.server.commands;

import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.config.FormConfig;
import com.dragonminez.common.network.NetworkHandler;
import com.dragonminez.common.network.S2C.AppearanceSyncS2C;
import com.dragonminez.common.network.S2C.StatsSyncS2C;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.common.stats.character.Character;
import com.dragonminez.common.stats.extras.FormMasteries;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.server.permission.nodes.PermissionNode;

public class MasteryCommand {
   private static final SuggestionProvider<CommandSourceStack> SUGGEST_GROUPS = (context, builder) -> {
      try {
         ServerPlayer player = getTarget(context);
         String race = StatsProvider.get(StatsCapability.INSTANCE, player).map(data -> data.getCharacter().getRaceName()).orElse("human");
         List<String> groups = new ArrayList<>(ConfigManager.getAllFormsForRace(race).keySet());
         groups.addAll(ConfigManager.getAllStackForms().keySet());
         return SharedSuggestionProvider.suggest(groups, builder);
      } catch (Exception var5) {
         return SharedSuggestionProvider.suggest(new ArrayList(), builder);
      }
   };
   private static final SuggestionProvider<CommandSourceStack> SUGGEST_FORMS = (context, builder) -> {
      try {
         String groupName = StringArgumentType.getString(context, "group");
         if (groupName != null) {
            FormConfig groupConfig = ConfigManager.getStackFormGroup(groupName);
            if (groupConfig == null) {
               ServerPlayer player = getTarget(context);
               String race = StatsProvider.get(StatsCapability.INSTANCE, player).map(data -> data.getCharacter().getRaceName()).orElse("human");
               groupConfig = ConfigManager.getFormGroup(race, groupName);
            }

            if (groupConfig != null) {
               return SharedSuggestionProvider.suggest(groupConfig.getForms().keySet(), builder);
            }
         }
      } catch (Exception var6) {
      }

      return SharedSuggestionProvider.suggest(new ArrayList(), builder);
   };

   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      dispatcher.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("dmzmastery").then(modeBranch("set", false))).then(modeBranch("add", true))
      );
   }

   private static LiteralArgumentBuilder<CommandSourceStack> modeBranch(String mode, boolean add) {
      PermissionNode<Boolean> permission = add ? DMZPermissions.MASTERY_ADD : DMZPermissions.MASTERY_SET;
      return (LiteralArgumentBuilder<CommandSourceStack>)((LiteralArgumentBuilder)Commands.literal(mode)
            .requires(source -> DMZPermissions.hasPermission(source, permission)))
         .then(
            ((RequiredArgumentBuilder)((RequiredArgumentBuilder)Commands.argument("target", EntityArgument.player())
                     .then(
                        Commands.argument("group", StringArgumentType.word())
                           .suggests(SUGGEST_GROUPS)
                           .then(
                              Commands.argument("form", StringArgumentType.word())
                                 .suggests(SUGGEST_FORMS)
                                 .then(Commands.argument("value", DoubleArgumentType.doubleArg()).executes(ctx -> setMastery(ctx, add)))
                           )
                     ))
                  .then(
                     ((LiteralArgumentBuilder)Commands.literal("current")
                           .then(
                              Commands.literal("form")
                                 .then(Commands.argument("value", DoubleArgumentType.doubleArg()).executes(ctx -> setCurrentMastery(ctx, add, false)))
                           ))
                        .then(
                           Commands.literal("stack")
                              .then(Commands.argument("value", DoubleArgumentType.doubleArg()).executes(ctx -> setCurrentMastery(ctx, add, true)))
                        )
                  ))
               .then(
                  ((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("ALL")
                           .then(
                              Commands.literal("form")
                                 .then(Commands.argument("value", DoubleArgumentType.doubleArg()).executes(ctx -> setAllMastery(ctx, add, true, false)))
                           ))
                        .then(
                           Commands.literal("stack")
                              .then(Commands.argument("value", DoubleArgumentType.doubleArg()).executes(ctx -> setAllMastery(ctx, add, false, true)))
                        ))
                     .then(
                        Commands.literal("all")
                           .then(Commands.argument("value", DoubleArgumentType.doubleArg()).executes(ctx -> setAllMastery(ctx, add, true, true)))
                     )
               )
         );
   }

   private static ServerPlayer getTarget(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
      EntitySelector selector = (EntitySelector)context.getArgument("target", EntitySelector.class);
      return selector.findSinglePlayer((CommandSourceStack)context.getSource());
   }

   private static int setMastery(CommandContext<CommandSourceStack> ctx, boolean add) throws CommandSyntaxException {
      ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
      String group = StringArgumentType.getString(ctx, "group");
      String form = StringArgumentType.getString(ctx, "form");
      double value = DoubleArgumentType.getDouble(ctx, "value");
      boolean stack = ConfigManager.getStackFormGroup(group) != null;
      return apply((CommandSourceStack)ctx.getSource(), target, group, form, value, add, stack);
   }

   private static int setCurrentMastery(CommandContext<CommandSourceStack> ctx, boolean add, boolean stack) throws CommandSyntaxException {
      ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
      double value = DoubleArgumentType.getDouble(ctx, "value");
      Character character = StatsProvider.get(StatsCapability.INSTANCE, target).map(data -> data.getCharacter()).orElse(null);
      if (character == null) {
         return 0;
      } else {
         boolean hasActive = stack ? character.hasActiveStackForm() : character.hasActiveForm();
         if (!hasActive) {
            ((CommandSourceStack)ctx.getSource())
               .sendFailure(
                  Component.translatable(
                     stack ? "command.dragonminez.mastery.no_active_stack" : "command.dragonminez.mastery.no_active_form",
                     new Object[]{target.getName().getString()}
                  )
               );
            return 0;
         } else {
            String group = stack ? character.getActiveStackFormGroup() : character.getActiveFormGroup();
            String form = stack ? character.getActiveStackForm() : character.getActiveForm();
            return apply((CommandSourceStack)ctx.getSource(), target, group, form, value, add, stack);
         }
      }
   }

   private static int setAllMastery(CommandContext<CommandSourceStack> ctx, boolean add, boolean doForms, boolean doStacks) throws CommandSyntaxException {
      ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
      double value = DoubleArgumentType.getDouble(ctx, "value");
      boolean log = ConfigManager.getServerConfig().getGameplay().getCommandOutputOnConsole();
      StatsProvider.get(StatsCapability.INSTANCE, target).ifPresent(data -> {
         if (doForms) {
            FormMasteries masteries = data.getCharacter().getFormMasteries();

            for (Entry<String, FormConfig> groupEntry : ConfigManager.getAllFormsForRace(data.getCharacter().getRaceName()).entrySet()) {
               applyGroup(masteries, groupEntry.getKey(), groupEntry.getValue(), value, add);
            }
         }

         if (doStacks) {
            FormMasteries masteries = data.getCharacter().getStackFormMasteries();

            for (Entry<String, FormConfig> groupEntry : ConfigManager.getAllStackForms().entrySet()) {
               applyGroup(masteries, groupEntry.getKey(), groupEntry.getValue(), value, add);
            }
         }

         if (doForms) {
            NetworkHandler.sendToTrackingEntityAndSelf(new StatsSyncS2C(target), target);
         }

         if (doStacks) {
            NetworkHandler.sendToTrackingEntityAndSelf(new AppearanceSyncS2C(target), target);
         }
      });
      String scope = doForms && doStacks ? "all" : (doStacks ? "stack" : "form");
      String modeKey = add ? "add" : "set";
      ((CommandSourceStack)ctx.getSource())
         .sendSuccess(
            () -> Component.translatable("command.dragonminez.mastery." + modeKey + ".all", new Object[]{value, scope, target.getName().getString()}), log
         );
      return 1;
   }

   private static void applyGroup(FormMasteries masteries, String group, FormConfig groupConfig, double value, boolean add) {
      for (Entry<String, FormConfig.FormData> formEntry : groupConfig.getForms().entrySet()) {
         double maxMastery = formEntry.getValue().getMaxMastery();
         if (add) {
            masteries.addMastery(group, formEntry.getKey(), value, maxMastery);
         } else {
            masteries.setMastery(group, formEntry.getKey(), value, maxMastery);
         }
      }
   }

   private static int apply(CommandSourceStack source, ServerPlayer target, String group, String form, double value, boolean add, boolean stack) {
      boolean log = ConfigManager.getServerConfig().getGameplay().getCommandOutputOnConsole();
      StatsProvider.get(StatsCapability.INSTANCE, target).ifPresent(data -> {
         FormMasteries masteries = stack ? data.getCharacter().getStackFormMasteries() : data.getCharacter().getFormMasteries();
         double maxMastery = 100.0;
         FormConfig.FormData formData = stack ? ConfigManager.getStackForm(group, form) : ConfigManager.getForm(data.getCharacter().getRaceName(), group, form);
         if (formData != null) {
            maxMastery = formData.getMaxMastery();
         }

         if (add) {
            masteries.addMastery(group, form, value, maxMastery);
         } else {
            masteries.setMastery(group, form, value, maxMastery);
         }

         if (stack) {
            NetworkHandler.sendToTrackingEntityAndSelf(new AppearanceSyncS2C(target), target);
         } else {
            NetworkHandler.sendToTrackingEntityAndSelf(new StatsSyncS2C(target), target);
         }
      });
      String modeKey = add ? "add" : "set";
      source.sendSuccess(
         () -> Component.translatable("command.dragonminez.mastery." + modeKey + ".success", new Object[]{value, group, form, target.getName().getString()}),
         log
      );
      return 1;
   }
}
