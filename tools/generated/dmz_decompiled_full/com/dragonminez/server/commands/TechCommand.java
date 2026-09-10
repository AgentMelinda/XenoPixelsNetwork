package com.dragonminez.server.commands;

import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.config.SkillsConfig;
import com.dragonminez.common.network.NetworkHandler;
import com.dragonminez.common.network.S2C.ProgressionSyncS2C;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.common.stats.techniques.KiAttackData;
import com.dragonminez.common.stats.techniques.PredefinedTechniques;
import com.dragonminez.common.stats.techniques.StrikeAttackData;
import com.dragonminez.common.stats.techniques.TechniqueData;
import com.dragonminez.common.stats.techniques.Techniques;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class TechCommand {
   private static final SuggestionProvider<CommandSourceStack> TECH_SUGGESTIONS = (ctx, builder) -> {
      SkillsConfig config = ConfigManager.getSkillsConfig();
      List<String> validTechs = config.getKiSkills().stream().filter(PredefinedTechniques.REGISTRY::containsKey).toList();
      List<String> validStrike = config.getStrikeSkills().stream().filter(PredefinedTechniques.STRIKE_REGISTRY::containsKey).toList();
      List<String> all = new ArrayList<>(validTechs);
      all.addAll(validStrike);
      return SharedSuggestionProvider.suggest(all, builder);
   };
   private static final SuggestionProvider<CommandSourceStack> UNLOCKED_TECH_SUGGESTIONS = (ctx, builder) -> {
      ServerPlayer player = ((CommandSourceStack)ctx.getSource()).getPlayer();
      if (player == null) {
         return builder.buildFuture();
      } else {
         List<String> ids = new ArrayList<>();
         StatsProvider.get(StatsCapability.INSTANCE, player).ifPresent(data -> ids.addAll(data.getTechniques().getUnlockedTechniques().keySet()));
         return SharedSuggestionProvider.suggest(ids, builder);
      }
   };

   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      dispatcher.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("dmztech")
                     .requires(source -> DMZPermissions.check(source, DMZPermissions.TECH_LIST_SELF, DMZPermissions.TECH_LIST_OTHERS)))
                  .then(
                     ((LiteralArgumentBuilder)Commands.literal("add")
                           .requires(source -> DMZPermissions.check(source, DMZPermissions.TECH_ADD_SELF, DMZPermissions.TECH_ADD_OTHERS)))
                        .then(
                           ((RequiredArgumentBuilder)Commands.argument("technique", StringArgumentType.string())
                                 .suggests(TECH_SUGGESTIONS)
                                 .executes(
                                    ctx -> addTechnique(
                                          (CommandSourceStack)ctx.getSource(),
                                          List.of(((CommandSourceStack)ctx.getSource()).getPlayerOrException()),
                                          StringArgumentType.getString(ctx, "technique")
                                       )
                                 ))
                              .then(
                                 ((RequiredArgumentBuilder)Commands.argument("targets", EntityArgument.players())
                                       .requires(source -> DMZPermissions.hasPermission(source, DMZPermissions.TECH_ADD_OTHERS)))
                                    .executes(
                                       ctx -> addTechnique(
                                             (CommandSourceStack)ctx.getSource(),
                                             EntityArgument.getPlayers(ctx, "targets"),
                                             StringArgumentType.getString(ctx, "technique")
                                          )
                                    )
                              )
                        )
                  ))
               .then(
                  ((LiteralArgumentBuilder)Commands.literal("remove")
                        .requires(source -> DMZPermissions.check(source, DMZPermissions.TECH_REMOVE_SELF, DMZPermissions.TECH_REMOVE_OTHERS)))
                     .then(
                        ((RequiredArgumentBuilder)Commands.argument("technique", StringArgumentType.string())
                              .suggests(TECH_SUGGESTIONS)
                              .executes(
                                 ctx -> removeTechnique(
                                       (CommandSourceStack)ctx.getSource(),
                                       List.of(((CommandSourceStack)ctx.getSource()).getPlayerOrException()),
                                       StringArgumentType.getString(ctx, "technique")
                                    )
                              ))
                           .then(
                              ((RequiredArgumentBuilder)Commands.argument("targets", EntityArgument.players())
                                    .requires(source -> DMZPermissions.hasPermission(source, DMZPermissions.TECH_REMOVE_OTHERS)))
                                 .executes(
                                    ctx -> removeTechnique(
                                          (CommandSourceStack)ctx.getSource(),
                                          EntityArgument.getPlayers(ctx, "targets"),
                                          StringArgumentType.getString(ctx, "technique")
                                       )
                                 )
                           )
                     )
               ))
            .then(
               ((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("experience")
                           .requires(source -> DMZPermissions.check(source, DMZPermissions.TECH_EXP_SELF, DMZPermissions.TECH_EXP_OTHERS)))
                        .then(experienceMode("add", TechCommand.ExperienceMode.ADD)))
                     .then(experienceMode("set", TechCommand.ExperienceMode.SET)))
                  .then(experienceMode("remove", TechCommand.ExperienceMode.REMOVE))
            )
      );
   }

   private static LiteralArgumentBuilder<CommandSourceStack> experienceMode(String literal, TechCommand.ExperienceMode mode) {
      return (LiteralArgumentBuilder<CommandSourceStack>)Commands.literal(literal)
         .then(
            Commands.argument("technique", StringArgumentType.string())
               .suggests(UNLOCKED_TECH_SUGGESTIONS)
               .then(
                  ((RequiredArgumentBuilder)Commands.argument("amount", IntegerArgumentType.integer(0))
                        .executes(
                           ctx -> experienceTechnique(
                                 (CommandSourceStack)ctx.getSource(),
                                 List.of(((CommandSourceStack)ctx.getSource()).getPlayerOrException()),
                                 StringArgumentType.getString(ctx, "technique"),
                                 IntegerArgumentType.getInteger(ctx, "amount"),
                                 mode
                              )
                        ))
                     .then(
                        ((RequiredArgumentBuilder)Commands.argument("targets", EntityArgument.players())
                              .requires(source -> DMZPermissions.hasPermission(source, DMZPermissions.TECH_EXP_OTHERS)))
                           .executes(
                              ctx -> experienceTechnique(
                                    (CommandSourceStack)ctx.getSource(),
                                    EntityArgument.getPlayers(ctx, "targets"),
                                    StringArgumentType.getString(ctx, "technique"),
                                    IntegerArgumentType.getInteger(ctx, "amount"),
                                    mode
                                 )
                           )
                     )
               )
         );
   }

   private static int addTechnique(CommandSourceStack source, Collection<ServerPlayer> targets, String techniqueId) {
      boolean log = ConfigManager.getServerConfig().getGameplay().getCommandOutputOnConsole();
      String id = techniqueId.toLowerCase();
      if (isUnknownTechnique(id)) {
         source.sendFailure(Component.translatable("command.dragonminez.tech.unknown_technique", new Object[]{techniqueId}));
         return 0;
      } else {
         for (ServerPlayer player : targets) {
            StatsProvider.get(StatsCapability.INSTANCE, player).ifPresent(data -> {
               if (PredefinedTechniques.REGISTRY.containsKey(id)) {
                  KiAttackData template = PredefinedTechniques.REGISTRY.get(id);
                  KiAttackData clone = new KiAttackData();
                  clone.load(template.save());
                  data.getTechniques().unlockTechnique(clone);
               } else if (PredefinedTechniques.STRIKE_REGISTRY.containsKey(id)) {
                  StrikeAttackData template = PredefinedTechniques.STRIKE_REGISTRY.get(id);
                  StrikeAttackData clone = new StrikeAttackData();
                  clone.load(template.save());
                  data.getTechniques().unlockTechnique(clone);
               }

               NetworkHandler.sendToTrackingEntityAndSelf(new ProgressionSyncS2C(player), player);
            });
         }

         if (targets.size() == 1) {
            source.sendSuccess(
               () -> Component.translatable("command.dragonminez.tech.add_success", new Object[]{techniqueId, targets.iterator().next().getName().getString()}),
               log
            );
         } else {
            source.sendSuccess(() -> Component.translatable("command.dragonminez.tech.add_multiple", new Object[]{techniqueId, targets.size()}), log);
         }

         return targets.size();
      }
   }

   private static int removeTechnique(CommandSourceStack source, Collection<ServerPlayer> targets, String techniqueId) {
      boolean log = ConfigManager.getServerConfig().getGameplay().getCommandOutputOnConsole();
      String id = techniqueId.toLowerCase();
      if (isUnknownTechnique(id)) {
         source.sendFailure(Component.translatable("command.dragonminez.tech.unknown_technique", new Object[]{techniqueId}));
         return 0;
      } else {
         for (ServerPlayer player : targets) {
            StatsProvider.get(StatsCapability.INSTANCE, player).ifPresent(data -> {
               Techniques techniques = data.getTechniques();
               techniques.getUnlockedTechniques().remove(id);
               String[] slots = techniques.getEquippedSlots();

               for (int i = 0; i < slots.length; i++) {
                  if (id.equals(slots[i])) {
                     slots[i] = "";
                  }
               }

               NetworkHandler.sendToTrackingEntityAndSelf(new ProgressionSyncS2C(player), player);
            });
         }

         if (targets.size() == 1) {
            source.sendSuccess(
               () -> Component.translatable(
                     "command.dragonminez.tech.remove_success", new Object[]{techniqueId, targets.iterator().next().getName().getString()}
                  ),
               log
            );
         } else {
            source.sendSuccess(() -> Component.translatable("command.dragonminez.tech.remove_multiple", new Object[]{techniqueId, targets.size()}), log);
         }

         return targets.size();
      }
   }

   private static int experienceTechnique(
      CommandSourceStack source, Collection<ServerPlayer> targets, String techniqueId, int amount, TechCommand.ExperienceMode mode
   ) {
      boolean log = ConfigManager.getServerConfig().getGameplay().getCommandOutputOnConsole();
      AtomicInteger applied = new AtomicInteger();

      for (ServerPlayer player : targets) {
         StatsProvider.get(StatsCapability.INSTANCE, player).ifPresent(data -> {
            TechniqueData tech = data.getTechniques().getUnlockedTechniques().get(techniqueId);
            if (tech != null) {
               switch (mode) {
                  case ADD:
                     tech.addExperience(tech.getExperience() + amount);
                     break;
                  case SET:
                     tech.setExperience(amount);
                     break;
                  case REMOVE:
                     tech.setExperience(Math.max(0, tech.getExperience() - amount));
               }

               applied.incrementAndGet();
               NetworkHandler.sendToTrackingEntityAndSelf(new ProgressionSyncS2C(player), player);
            }
         });
      }

      if (applied.get() == 0) {
         source.sendFailure(Component.translatable("command.dragonminez.tech.unknown_technique", new Object[]{techniqueId}));
         return 0;
      } else {
         if (applied.get() == 1) {
            source.sendSuccess(
               () -> Component.translatable(
                     "command.dragonminez.tech.experience_success", new Object[]{techniqueId, targets.iterator().next().getName().getString()}
                  ),
               log
            );
         } else {
            source.sendSuccess(() -> Component.translatable("command.dragonminez.tech.experience_multiple", new Object[]{techniqueId, applied.get()}), log);
         }

         return applied.get();
      }
   }

   private static boolean isUnknownTechnique(String id) {
      SkillsConfig config = ConfigManager.getSkillsConfig();
      boolean isKi = config.getKiSkills().contains(id) && PredefinedTechniques.REGISTRY.containsKey(id);
      boolean isStrike = config.getStrikeSkills().contains(id) && PredefinedTechniques.STRIKE_REGISTRY.containsKey(id);
      return !isKi && !isStrike;
   }

   private static enum ExperienceMode {
      ADD,
      SET,
      REMOVE;
   }
}
