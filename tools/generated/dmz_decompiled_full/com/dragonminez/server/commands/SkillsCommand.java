package com.dragonminez.server.commands;

import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.config.SkillsConfig;
import com.dragonminez.common.network.NetworkHandler;
import com.dragonminez.common.network.S2C.ProgressionSyncS2C;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsProvider;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import java.util.Collection;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class SkillsCommand {
   private static final SuggestionProvider<CommandSourceStack> SKILL_SUGGESTIONS = (ctx, builder) -> {
      SkillsConfig config = ConfigManager.getSkillsConfig();
      List<String> validSkills = config.getSkills()
         .keySet()
         .stream()
         .filter(
            s -> !config.getKiSkills().contains(s)
                  && !config.getStackSkills().contains(s)
                  && !config.getFormSkills().contains(s)
                  && !config.getStrikeSkills().contains(s)
         )
         .toList();
      return SharedSuggestionProvider.suggest(validSkills, builder);
   };

   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      dispatcher.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("dmzskill")
                     .requires(source -> DMZPermissions.check(source, DMZPermissions.SKILLS_LIST_SELF, DMZPermissions.SKILLS_LIST_OTHERS)))
                  .then(
                     ((LiteralArgumentBuilder)Commands.literal("set")
                           .requires(source -> DMZPermissions.check(source, DMZPermissions.SKILLS_SET_SELF, DMZPermissions.SKILLS_SET_OTHERS)))
                        .then(
                           Commands.argument("skill", StringArgumentType.string())
                              .suggests(SKILL_SUGGESTIONS)
                              .then(
                                 ((RequiredArgumentBuilder)Commands.argument("level", IntegerArgumentType.integer(0))
                                       .executes(
                                          ctx -> setSkill(
                                                (CommandSourceStack)ctx.getSource(),
                                                List.of(((CommandSourceStack)ctx.getSource()).getPlayerOrException()),
                                                StringArgumentType.getString(ctx, "skill"),
                                                IntegerArgumentType.getInteger(ctx, "level")
                                             )
                                       ))
                                    .then(
                                       ((RequiredArgumentBuilder)Commands.argument("targets", EntityArgument.players())
                                             .requires(source -> DMZPermissions.hasPermission(source, DMZPermissions.SKILLS_SET_OTHERS)))
                                          .executes(
                                             ctx -> setSkill(
                                                   (CommandSourceStack)ctx.getSource(),
                                                   EntityArgument.getPlayers(ctx, "targets"),
                                                   StringArgumentType.getString(ctx, "skill"),
                                                   IntegerArgumentType.getInteger(ctx, "level")
                                                )
                                          )
                                    )
                              )
                        )
                  ))
               .then(
                  ((LiteralArgumentBuilder)Commands.literal("add")
                        .requires(source -> DMZPermissions.check(source, DMZPermissions.SKILLS_ADD_SELF, DMZPermissions.SKILLS_ADD_OTHERS)))
                     .then(
                        ((RequiredArgumentBuilder)Commands.argument("skill", StringArgumentType.string())
                              .suggests(SKILL_SUGGESTIONS)
                              .executes(
                                 ctx -> setSkill(
                                       (CommandSourceStack)ctx.getSource(),
                                       List.of(((CommandSourceStack)ctx.getSource()).getPlayerOrException()),
                                       StringArgumentType.getString(ctx, "skill"),
                                       1
                                    )
                              ))
                           .then(
                              ((RequiredArgumentBuilder)Commands.argument("targets", EntityArgument.players())
                                    .requires(source -> DMZPermissions.hasPermission(source, DMZPermissions.SKILLS_ADD_OTHERS)))
                                 .executes(
                                    ctx -> setSkill(
                                          (CommandSourceStack)ctx.getSource(),
                                          EntityArgument.getPlayers(ctx, "targets"),
                                          StringArgumentType.getString(ctx, "skill"),
                                          1
                                       )
                                 )
                           )
                     )
               ))
            .then(
               ((LiteralArgumentBuilder)Commands.literal("remove")
                     .requires(source -> DMZPermissions.check(source, DMZPermissions.SKILLS_REMOVE_SELF, DMZPermissions.SKILLS_REMOVE_OTHERS)))
                  .then(
                     ((RequiredArgumentBuilder)Commands.argument("skill", StringArgumentType.string())
                           .suggests(SKILL_SUGGESTIONS)
                           .executes(
                              ctx -> removeSkill(
                                    (CommandSourceStack)ctx.getSource(),
                                    List.of(((CommandSourceStack)ctx.getSource()).getPlayerOrException()),
                                    StringArgumentType.getString(ctx, "skill")
                                 )
                           ))
                        .then(
                           ((RequiredArgumentBuilder)Commands.argument("targets", EntityArgument.players())
                                 .requires(source -> DMZPermissions.hasPermission(source, DMZPermissions.SKILLS_REMOVE_OTHERS)))
                              .executes(
                                 ctx -> removeSkill(
                                       (CommandSourceStack)ctx.getSource(),
                                       EntityArgument.getPlayers(ctx, "targets"),
                                       StringArgumentType.getString(ctx, "skill")
                                    )
                              )
                        )
                  )
            )
      );
   }

   private static int setSkill(CommandSourceStack source, Collection<ServerPlayer> targets, String skillName, int level) {
      boolean log = ConfigManager.getServerConfig().getGameplay().getCommandOutputOnConsole();
      String lowerName = skillName.toLowerCase();
      SkillsConfig config = ConfigManager.getSkillsConfig();
      if (!config.getKiSkills().contains(lowerName)
         && !config.getStackSkills().contains(lowerName)
         && !config.getFormSkills().contains(lowerName)
         && config.getSkills().containsKey(lowerName)) {
         for (ServerPlayer player : targets) {
            StatsProvider.get(StatsCapability.INSTANCE, player).ifPresent(data -> {
               data.getSkills().setSkillLevel(lowerName, level);
               NetworkHandler.sendToTrackingEntityAndSelf(new ProgressionSyncS2C(player), player);
            });
         }

         if (targets.size() == 1) {
            source.sendSuccess(
               () -> Component.translatable(
                     "command.dragonminez.skills.set_success", new Object[]{skillName, level, targets.iterator().next().getName().getString()}
                  ),
               log
            );
         } else {
            source.sendSuccess(() -> Component.translatable("command.dragonminez.skills.set_multiple", new Object[]{skillName, level, targets.size()}), log);
         }

         return targets.size();
      } else {
         source.sendFailure(Component.translatable("command.dragonminez.skills.unknown_skill", new Object[]{skillName}));
         return 0;
      }
   }

   private static int removeSkill(CommandSourceStack source, Collection<ServerPlayer> targets, String skillName) {
      boolean log = ConfigManager.getServerConfig().getGameplay().getCommandOutputOnConsole();
      String lowerName = skillName.toLowerCase();
      SkillsConfig config = ConfigManager.getSkillsConfig();
      if (!config.getKiSkills().contains(lowerName)
         && !config.getStackSkills().contains(lowerName)
         && !config.getFormSkills().contains(lowerName)
         && config.getSkills().containsKey(lowerName)) {
         for (ServerPlayer player : targets) {
            StatsProvider.get(StatsCapability.INSTANCE, player).ifPresent(data -> {
               if (data.getSkills().hasSkill(lowerName)) {
                  data.getSkills().removeSkill(lowerName);
                  NetworkHandler.sendToTrackingEntityAndSelf(new ProgressionSyncS2C(player), player);
               }
            });
         }

         if (targets.size() == 1) {
            source.sendSuccess(
               () -> Component.translatable(
                     "command.dragonminez.skills.remove_success", new Object[]{skillName, targets.iterator().next().getName().getString()}
                  ),
               log
            );
         } else {
            source.sendSuccess(() -> Component.translatable("command.dragonminez.skills.remove_multiple", new Object[]{skillName, targets.size()}), log);
         }

         return targets.size();
      } else {
         source.sendFailure(Component.translatable("command.dragonminez.skills.unknown_skill", new Object[]{skillName}));
         return 0;
      }
   }
}
