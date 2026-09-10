package com.dragonminez.server.commands;

import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.events.DMZEvent;
import com.dragonminez.common.init.MainEntities;
import com.dragonminez.common.init.entities.questnpc.QuestNPCEntity;
import com.dragonminez.common.quest.PartyManager;
import com.dragonminez.common.quest.PlayerQuestData;
import com.dragonminez.common.quest.Quest;
import com.dragonminez.common.quest.QuestObjective;
import com.dragonminez.common.quest.QuestRegistry;
import com.dragonminez.common.quest.QuestService;
import com.dragonminez.common.quest.QuestTextFormatter;
import com.dragonminez.common.quest.Saga;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsProvider;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.NeoForge;

public class StoryCommand {
   private static final int NPC_SEARCH_RADIUS = 16;
   private static final SuggestionProvider<CommandSourceStack> QUEST_SUGGESTIONS = (context, builder) -> SharedSuggestionProvider.suggest(
         suggestionIds(QuestRegistry.getAllQuests().keySet()), builder
      );
   private static final SuggestionProvider<CommandSourceStack> QUEST_OR_ALL_SUGGESTIONS = (context, builder) -> {
      List<String> suggestions = suggestionIds(QuestRegistry.getAllQuests().keySet());
      suggestions.add("all");
      return SharedSuggestionProvider.suggest(suggestions, builder);
   };
   private static final SuggestionProvider<CommandSourceStack> QUEST_OR_NONE_SUGGESTIONS = (context, builder) -> {
      List<String> suggestions = suggestionIds(QuestRegistry.getAllQuests().keySet());
      suggestions.add("none");
      return SharedSuggestionProvider.suggest(suggestions, builder);
   };
   private static final SuggestionProvider<CommandSourceStack> SAGA_OR_ALL_SUGGESTIONS = (context, builder) -> {
      List<String> suggestions = suggestionIds(QuestRegistry.getAllSagas().keySet());
      suggestions.add("all");
      return SharedSuggestionProvider.suggest(suggestions, builder);
   };

   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      dispatcher.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal(
                                                "dmzquest"
                                             )
                                             .requires(source -> DMZPermissions.check(source, DMZPermissions.QUEST_LIST_SELF, DMZPermissions.QUEST_LIST_OTHERS)))
                                          .then(
                                             ((LiteralArgumentBuilder)Commands.literal("list").executes(context -> listQuests(context, null)))
                                                .then(
                                                   ((RequiredArgumentBuilder)Commands.argument("player", EntityArgument.player())
                                                         .requires(source -> DMZPermissions.hasPermission(source, DMZPermissions.QUEST_LIST_OTHERS)))
                                                      .executes(context -> listQuests(context, EntityArgument.getPlayer(context, "player")))
                                                )
                                          ))
                                       .then(
                                          ((LiteralArgumentBuilder)Commands.literal("info")
                                                .requires(source -> DMZPermissions.hasPermission(source, DMZPermissions.QUEST_INFO)))
                                             .then(
                                                Commands.argument("quest", StringArgumentType.string())
                                                   .suggests(QUEST_SUGGESTIONS)
                                                   .executes(context -> questInfo(context))
                                             )
                                       ))
                                    .then(
                                       ((LiteralArgumentBuilder)Commands.literal("start")
                                             .requires(
                                                source -> DMZPermissions.check(source, DMZPermissions.QUEST_START_SELF, DMZPermissions.QUEST_START_OTHERS)
                                             ))
                                          .then(
                                             ((RequiredArgumentBuilder)Commands.argument("quest", StringArgumentType.string())
                                                   .suggests(QUEST_OR_ALL_SUGGESTIONS)
                                                   .executes(context -> startQuest(context, null)))
                                                .then(
                                                   ((RequiredArgumentBuilder)Commands.argument("player", EntityArgument.player())
                                                         .requires(source -> DMZPermissions.hasPermission(source, DMZPermissions.QUEST_START_OTHERS)))
                                                      .executes(context -> startQuest(context, EntityArgument.getPlayer(context, "player")))
                                                )
                                          )
                                    ))
                                 .then(
                                    ((LiteralArgumentBuilder)Commands.literal("finish")
                                          .requires(
                                             source -> DMZPermissions.check(source, DMZPermissions.QUEST_FINISH_SELF, DMZPermissions.QUEST_FINISH_OTHERS)
                                          ))
                                       .then(
                                          ((RequiredArgumentBuilder)Commands.argument("quest", StringArgumentType.string())
                                                .suggests(QUEST_OR_ALL_SUGGESTIONS)
                                                .executes(context -> finishQuest(context, null)))
                                             .then(
                                                ((RequiredArgumentBuilder)Commands.argument("player", EntityArgument.player())
                                                      .requires(source -> DMZPermissions.hasPermission(source, DMZPermissions.QUEST_FINISH_OTHERS)))
                                                   .executes(context -> finishQuest(context, EntityArgument.getPlayer(context, "player")))
                                             )
                                       )
                                 ))
                              .then(
                                 ((LiteralArgumentBuilder)Commands.literal("fail")
                                       .requires(source -> DMZPermissions.check(source, DMZPermissions.QUEST_FAIL_SELF, DMZPermissions.QUEST_FAIL_OTHERS)))
                                    .then(
                                       ((RequiredArgumentBuilder)Commands.argument("quest", StringArgumentType.string())
                                             .suggests(QUEST_OR_ALL_SUGGESTIONS)
                                             .executes(context -> failQuest(context, null)))
                                          .then(
                                             ((RequiredArgumentBuilder)Commands.argument("player", EntityArgument.player())
                                                   .requires(source -> DMZPermissions.hasPermission(source, DMZPermissions.QUEST_FAIL_OTHERS)))
                                                .executes(context -> failQuest(context, EntityArgument.getPlayer(context, "player")))
                                          )
                                    )
                              ))
                           .then(
                              ((LiteralArgumentBuilder)Commands.literal("reset")
                                    .requires(source -> DMZPermissions.check(source, DMZPermissions.QUEST_RESET_SELF, DMZPermissions.QUEST_RESET_OTHERS)))
                                 .then(
                                    ((RequiredArgumentBuilder)Commands.argument("quest", StringArgumentType.string())
                                          .suggests(QUEST_OR_ALL_SUGGESTIONS)
                                          .executes(context -> resetQuest(context, null)))
                                       .then(
                                          ((RequiredArgumentBuilder)Commands.argument("player", EntityArgument.player())
                                                .requires(source -> DMZPermissions.hasPermission(source, DMZPermissions.QUEST_RESET_OTHERS)))
                                             .executes(context -> resetQuest(context, EntityArgument.getPlayer(context, "player")))
                                       )
                                 )
                           ))
                        .then(
                           ((LiteralArgumentBuilder)Commands.literal("track")
                                 .requires(source -> DMZPermissions.check(source, DMZPermissions.QUEST_TRACK_SELF, DMZPermissions.QUEST_TRACK_OTHERS)))
                              .then(
                                 ((RequiredArgumentBuilder)Commands.argument("quest", StringArgumentType.string())
                                       .suggests(QUEST_OR_NONE_SUGGESTIONS)
                                       .executes(context -> trackQuest(context, null)))
                                    .then(
                                       ((RequiredArgumentBuilder)Commands.argument("player", EntityArgument.player())
                                             .requires(source -> DMZPermissions.hasPermission(source, DMZPermissions.QUEST_TRACK_OTHERS)))
                                          .executes(context -> trackQuest(context, EntityArgument.getPlayer(context, "player")))
                                    )
                              )
                        ))
                     .then(
                        ((LiteralArgumentBuilder)Commands.literal("startsaga")
                              .requires(source -> DMZPermissions.check(source, DMZPermissions.QUEST_STARTSAGA_SELF, DMZPermissions.QUEST_STARTSAGA_OTHERS)))
                           .then(
                              ((RequiredArgumentBuilder)Commands.argument("saga", StringArgumentType.string())
                                    .suggests(SAGA_OR_ALL_SUGGESTIONS)
                                    .executes(context -> startSaga(context, null)))
                                 .then(
                                    ((RequiredArgumentBuilder)Commands.argument("player", EntityArgument.player())
                                          .requires(source -> DMZPermissions.hasPermission(source, DMZPermissions.QUEST_STARTSAGA_OTHERS)))
                                       .executes(context -> startSaga(context, EntityArgument.getPlayer(context, "player")))
                                 )
                           )
                     ))
                  .then(
                     ((LiteralArgumentBuilder)Commands.literal("finishsaga")
                           .requires(source -> DMZPermissions.check(source, DMZPermissions.QUEST_FINISHSAGA_SELF, DMZPermissions.QUEST_FINISHSAGA_OTHERS)))
                        .then(
                           ((RequiredArgumentBuilder)Commands.argument("saga", StringArgumentType.string())
                                 .suggests(SAGA_OR_ALL_SUGGESTIONS)
                                 .executes(context -> finishSaga(context, null)))
                              .then(
                                 ((RequiredArgumentBuilder)Commands.argument("player", EntityArgument.player())
                                       .requires(source -> DMZPermissions.hasPermission(source, DMZPermissions.QUEST_FINISHSAGA_OTHERS)))
                                    .executes(context -> finishSaga(context, EntityArgument.getPlayer(context, "player")))
                              )
                        )
                  ))
               .then(
                  ((LiteralArgumentBuilder)Commands.literal("resetsaga")
                        .requires(source -> DMZPermissions.check(source, DMZPermissions.QUEST_RESETSAGA_SELF, DMZPermissions.QUEST_RESETSAGA_OTHERS)))
                     .then(
                        ((RequiredArgumentBuilder)Commands.argument("saga", StringArgumentType.string())
                              .suggests(SAGA_OR_ALL_SUGGESTIONS)
                              .executes(context -> resetSaga(context, null)))
                           .then(
                              ((RequiredArgumentBuilder)Commands.argument("player", EntityArgument.player())
                                    .requires(source -> DMZPermissions.hasPermission(source, DMZPermissions.QUEST_RESETSAGA_OTHERS)))
                                 .executes(context -> resetSaga(context, EntityArgument.getPlayer(context, "player")))
                           )
                     )
               ))
            .then(
               ((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("questnpc")
                           .requires(source -> source.hasPermission(2)))
                        .then(
                           Commands.literal("spawn")
                              .then(
                                 ((RequiredArgumentBuilder)Commands.argument("npcId", StringArgumentType.string())
                                       .executes(context -> spawnQuestNPC(context, null, null)))
                                    .then(
                                       ((RequiredArgumentBuilder)Commands.argument("model", StringArgumentType.string())
                                             .executes(context -> spawnQuestNPC(context, StringArgumentType.getString(context, "model"), null)))
                                          .then(
                                             Commands.argument("texture", StringArgumentType.string())
                                                .executes(
                                                   context -> spawnQuestNPC(
                                                         context,
                                                         StringArgumentType.getString(context, "model"),
                                                         StringArgumentType.getString(context, "texture")
                                                      )
                                                )
                                          )
                                    )
                              )
                        ))
                     .then(Commands.literal("list").executes(StoryCommand::listNearbyQuestNPCs)))
                  .then(Commands.literal("remove").executes(StoryCommand::removeNearestQuestNPC))
            )
      );
   }

   private static String normalizeId(String input) {
      return input == null ? null : input.replace('.', ':');
   }

   private static List<String> suggestionIds(Set<String> realIds) {
      List<String> suggestions = new ArrayList<>();

      for (String id : realIds) {
         suggestions.add(id.replace(':', '.'));
      }

      return suggestions;
   }

   private static int listQuests(CommandContext<CommandSourceStack> context, ServerPlayer targetPlayer) {
      try {
         ServerPlayer player = targetPlayer != null ? targetPlayer : ((CommandSourceStack)context.getSource()).getPlayerOrException();
         StatsProvider.get(StatsCapability.INSTANCE, player)
            .ifPresent(
               data -> {
                  PlayerQuestData pqd = data.getPlayerQuestData();
                  List<Entry<String, Quest>> quests = new ArrayList<>(QuestRegistry.getAllQuests().entrySet());
                  quests.sort(Entry.comparingByKey(Comparator.naturalOrder()));
                  if (quests.isEmpty()) {
                     ((CommandSourceStack)context.getSource()).sendFailure(Component.literal("No quests are loaded."));
                  } else {
                     ((CommandSourceStack)context.getSource())
                        .sendSystemMessage(Component.literal("Quest progress for " + player.getGameProfile().getName() + ":"));

                     for (Entry<String, Quest> entry : quests) {
                        String questKey = entry.getKey();
                        Quest quest = entry.getValue();
                        Component status = formatQuestStatus(pqd, questKey);
                        Component title = Component.translatable(quest.getTitle());
                        ((CommandSourceStack)context.getSource())
                           .sendSystemMessage(status.copy().append(Component.literal(" ")).append(title).append(Component.literal(" [" + questKey + "]")));
                        if (pqd.isQuestAccepted(questKey) && !pqd.isQuestCompleted(questKey)) {
                           for (int i = 0; i < quest.getObjectives().size(); i++) {
                              QuestObjective objective = quest.getObjectives().get(i);
                              int progress = pqd.getObjectiveProgress(questKey, i);
                              int required = quest.getObjectiveRequired(pqd, questKey, i);
                              String progressText = progress >= required ? "[DONE]" : "[" + progress + "/" + required + "]";
                              ((CommandSourceStack)context.getSource())
                                 .sendSystemMessage(Component.literal("  " + progressText + " ").append(QuestTextFormatter.describeObjective(objective)));
                           }
                        }
                     }
                  }
               }
            );
         return 1;
      } catch (Exception var3) {
         ((CommandSourceStack)context.getSource()).sendFailure(Component.literal("Failed to list quests: " + var3.getMessage()));
         return 0;
      }
   }

   private static int questInfo(CommandContext<CommandSourceStack> context) {
      try {
         String questKey = normalizeId(StringArgumentType.getString(context, "quest"));
         Quest quest = QuestRegistry.getQuest(questKey);
         if (quest == null) {
            ((CommandSourceStack)context.getSource())
               .sendFailure(Component.literal("Quest not found: " + questKey + "  (tip: use '.' instead of ':' — e.g. saiyan_saga.1)"));
            return 0;
         } else {
            QuestService.ResolvedQuest resolved = QuestService.resolveQuest(questKey);
            String sagaLabel = resolved != null && resolved.saga() != null ? resolved.saga().getId() : "none";
            ((CommandSourceStack)context.getSource()).sendSystemMessage(Component.literal("=== Quest: " + questKey + " ==="));
            ((CommandSourceStack)context.getSource()).sendSystemMessage(Component.literal("Title : ").append(Component.translatable(quest.getTitle())));
            ((CommandSourceStack)context.getSource()).sendSystemMessage(Component.literal("Type  : " + quest.getType().name()));
            ((CommandSourceStack)context.getSource()).sendSystemMessage(Component.literal("Saga  : " + sagaLabel));
            if (quest.getQuestGiver() != null && !quest.getQuestGiver().isBlank()) {
               ((CommandSourceStack)context.getSource()).sendSystemMessage(Component.literal("Giver : " + quest.getQuestGiver()));
            }

            if (quest.getTurnIn() != null && !quest.getTurnIn().isBlank()) {
               ((CommandSourceStack)context.getSource()).sendSystemMessage(Component.literal("TurnIn: " + quest.getTurnIn()));
            }

            ((CommandSourceStack)context.getSource()).sendSystemMessage(Component.literal("Objectives (" + quest.getObjectives().size() + "):"));

            for (int i = 0; i < quest.getObjectives().size(); i++) {
               ((CommandSourceStack)context.getSource())
                  .sendSystemMessage(Component.literal("  [" + i + "] ").append(QuestTextFormatter.describeObjective(quest.getObjectives().get(i))));
            }

            ((CommandSourceStack)context.getSource()).sendSystemMessage(Component.literal("Rewards: " + quest.getRewards().size()));
            ((CommandSourceStack)context.getSource()).sendSystemMessage(Component.literal("Secret : " + quest.isSecret()));
            ((CommandSourceStack)context.getSource()).sendSystemMessage(Component.literal("Claim  : " + quest.getClaimMode().name()));
            if (quest.hasPrerequisites()) {
               ((CommandSourceStack)context.getSource()).sendSystemMessage(Component.literal("Has prerequisites: yes"));
            }

            if (quest.hasStartRequirements()) {
               ((CommandSourceStack)context.getSource()).sendSystemMessage(Component.literal("Has start requirements: yes"));
            }

            return 1;
         }
      } catch (Exception var6) {
         ((CommandSourceStack)context.getSource()).sendFailure(Component.literal("Failed to get quest info: " + var6.getMessage()));
         return 0;
      }
   }

   private static int startQuest(CommandContext<CommandSourceStack> context, ServerPlayer targetPlayer) {
      boolean log = ConfigManager.getServerConfig().getGameplay().getCommandOutputOnConsole();

      try {
         String questKey = normalizeId(StringArgumentType.getString(context, "quest"));
         List<ServerPlayer> targets = getTargetPlayers(context, targetPlayer);
         if (targets.isEmpty()) {
            ((CommandSourceStack)context.getSource()).sendFailure(Component.literal("No valid players found."));
            return 0;
         } else {
            List<Entry<String, Quest>> questsToStart = new ArrayList<>();
            if ("all".equalsIgnoreCase(questKey)) {
               questsToStart.addAll(QuestRegistry.getAllQuests().entrySet());
            } else {
               Quest quest = QuestRegistry.getQuest(questKey);
               if (quest == null) {
                  ((CommandSourceStack)context.getSource())
                     .sendFailure(Component.literal("Quest not found: " + questKey + "  (tip: use '.' instead of ':' — e.g. saiyan_saga.1)"));
                  return 0;
               }

               questsToStart.add(Map.entry(questKey, quest));
            }

            int successCount = 0;

            for (ServerPlayer player : targets) {
               StatsProvider.get(StatsCapability.INSTANCE, player).ifPresent(stats -> {
                  PlayerQuestData pqd = stats.getPlayerQuestData();
                  int partySize = PartyManager.getAllPartyMembers(player).size();

                  for (Entry<String, Quest> entry : questsToStart) {
                     String key = entry.getKey();
                     Quest quest = entry.getValue();
                     if (!pqd.isQuestAccepted(key) && !pqd.isQuestCompleted(key)) {
                        pqd.acceptQuest(key);
                        quest.initializeObjectiveRequirements(pqd, key, partySize);
                     }
                  }

                  QuestService.syncQuestState(player);
               });
               successCount++;
            }

            int affected = successCount;
            ((CommandSourceStack)context.getSource())
               .sendSuccess(
                  () -> Component.literal(
                        ("all".equalsIgnoreCase(questKey) ? "Started all quests" : "Started quest " + questKey) + " for " + affected + " player(s)."
                     ),
                  log
               );
            return successCount;
         }
      } catch (Exception var9) {
         ((CommandSourceStack)context.getSource()).sendFailure(Component.literal("Failed to start quest: " + var9.getMessage()));
         return 0;
      }
   }

   private static int finishQuest(CommandContext<CommandSourceStack> context, ServerPlayer targetPlayer) {
      boolean log = ConfigManager.getServerConfig().getGameplay().getCommandOutputOnConsole();

      try {
         String questKey = normalizeId(StringArgumentType.getString(context, "quest"));
         List<ServerPlayer> targets = getTargetPlayers(context, targetPlayer);
         if (targets.isEmpty()) {
            ((CommandSourceStack)context.getSource()).sendFailure(Component.literal("No valid players found."));
            return 0;
         } else {
            List<Entry<String, Quest>> questsToFinish = new ArrayList<>();
            if ("all".equalsIgnoreCase(questKey)) {
               questsToFinish.addAll(QuestRegistry.getAllQuests().entrySet());
            } else {
               Quest quest = QuestRegistry.getQuest(questKey);
               if (quest == null) {
                  ((CommandSourceStack)context.getSource())
                     .sendFailure(Component.literal("Quest not found: " + questKey + "  (tip: use '.' instead of ':' — e.g. saiyan_saga.1)"));
                  return 0;
               }

               questsToFinish.add(Map.entry(questKey, quest));
            }

            int successCount = 0;

            for (ServerPlayer player : targets) {
               StatsProvider.get(StatsCapability.INSTANCE, player).ifPresent(stats -> {
                  PlayerQuestData pqd = stats.getPlayerQuestData();

                  for (Entry<String, Quest> entry : questsToFinish) {
                     completeQuest(pqd, entry.getKey(), entry.getValue(), player);
                  }

                  QuestService.syncQuestState(player);
               });
               successCount++;
            }

            int affected = successCount;
            ((CommandSourceStack)context.getSource())
               .sendSuccess(
                  () -> Component.literal(
                        ("all".equalsIgnoreCase(questKey) ? "Finished all quests" : "Finished quest " + questKey) + " for " + affected + " player(s)."
                     ),
                  log
               );
            return successCount;
         }
      } catch (Exception var9) {
         ((CommandSourceStack)context.getSource()).sendFailure(Component.literal("Failed to finish quest: " + var9.getMessage()));
         return 0;
      }
   }

   private static int failQuest(CommandContext<CommandSourceStack> context, ServerPlayer targetPlayer) {
      boolean log = ConfigManager.getServerConfig().getGameplay().getCommandOutputOnConsole();

      try {
         String questKey = normalizeId(StringArgumentType.getString(context, "quest"));
         List<ServerPlayer> targets = getTargetPlayers(context, targetPlayer);
         if (targets.isEmpty()) {
            ((CommandSourceStack)context.getSource()).sendFailure(Component.literal("No valid players found."));
            return 0;
         } else if (!"all".equalsIgnoreCase(questKey) && QuestRegistry.getQuest(questKey) == null) {
            ((CommandSourceStack)context.getSource())
               .sendFailure(Component.literal("Quest not found: " + questKey + "  (tip: use '.' instead of ':' — e.g. saiyan_saga.1)"));
            return 0;
         } else {
            int successCount = 0;

            for (ServerPlayer player : targets) {
               StatsProvider.get(StatsCapability.INSTANCE, player).ifPresent(stats -> {
                  PlayerQuestData pqd = stats.getPlayerQuestData();
                  if ("all".equalsIgnoreCase(questKey)) {
                     for (String key : new ArrayList<>(pqd.getAcceptedQuestIds())) {
                        Quest quest = QuestRegistry.getQuest(key);
                        if (quest != null && postForcedFailEvent(player, pqd, key, quest)) {
                           pqd.failQuest(key);
                           if (key.equals(pqd.getTrackedQuestId())) {
                              pqd.setTrackedQuestId(null);
                           }
                        }
                     }
                  } else {
                     Quest quest = QuestRegistry.getQuest(questKey);
                     if (quest != null && pqd.isQuestAccepted(questKey) && postForcedFailEvent(player, pqd, questKey, quest)) {
                        pqd.failQuest(questKey);
                        if (questKey.equals(pqd.getTrackedQuestId())) {
                           pqd.setTrackedQuestId(null);
                        }
                     }
                  }

                  QuestService.syncQuestState(player);
               });
               successCount++;
            }

            int affected = successCount;
            ((CommandSourceStack)context.getSource())
               .sendSuccess(
                  () -> Component.literal(
                        ("all".equalsIgnoreCase(questKey) ? "Failed all active quests" : "Failed quest " + questKey) + " for " + affected + " player(s)."
                     ),
                  log
               );
            return successCount;
         }
      } catch (Exception var8) {
         ((CommandSourceStack)context.getSource()).sendFailure(Component.literal("Failed to fail quest: " + var8.getMessage()));
         return 0;
      }
   }

   private static int resetQuest(CommandContext<CommandSourceStack> context, ServerPlayer targetPlayer) {
      boolean log = ConfigManager.getServerConfig().getGameplay().getCommandOutputOnConsole();

      try {
         String questKey = normalizeId(StringArgumentType.getString(context, "quest"));
         List<ServerPlayer> targets = getTargetPlayers(context, targetPlayer);
         if (targets.isEmpty()) {
            ((CommandSourceStack)context.getSource()).sendFailure(Component.literal("No valid players found."));
            return 0;
         } else if (!"all".equalsIgnoreCase(questKey) && QuestRegistry.getQuest(questKey) == null) {
            ((CommandSourceStack)context.getSource())
               .sendFailure(Component.literal("Quest not found: " + questKey + "  (tip: use '.' instead of ':' — e.g. saiyan_saga.1)"));
            return 0;
         } else {
            int successCount = 0;

            for (ServerPlayer player : targets) {
               StatsProvider.get(StatsCapability.INSTANCE, player).ifPresent(stats -> {
                  PlayerQuestData pqd = stats.getPlayerQuestData();
                  if ("all".equalsIgnoreCase(questKey)) {
                     for (String acceptedQuestKey : pqd.getAcceptedQuestIds()) {
                        Quest acceptedQuest = QuestRegistry.getQuest(acceptedQuestKey);
                        if (acceptedQuest != null) {
                           postForcedResetFailEvent(player, pqd, acceptedQuestKey, acceptedQuest);
                        }
                     }

                     pqd.resetAll();
                  } else {
                     Quest quest = QuestRegistry.getQuest(questKey);
                     if (quest != null && postForcedResetFailEvent(player, pqd, questKey, quest)) {
                        resetQuestEntry(pqd, questKey);
                     }
                  }

                  QuestService.syncQuestState(player);
               });
               successCount++;
            }

            int affected = successCount;
            ((CommandSourceStack)context.getSource())
               .sendSuccess(
                  () -> Component.literal(
                        ("all".equalsIgnoreCase(questKey) ? "Reset all quests" : "Reset quest " + questKey) + " for " + affected + " player(s)."
                     ),
                  log
               );
            return successCount;
         }
      } catch (Exception var8) {
         ((CommandSourceStack)context.getSource()).sendFailure(Component.literal("Failed to reset quest: " + var8.getMessage()));
         return 0;
      }
   }

   private static int trackQuest(CommandContext<CommandSourceStack> context, ServerPlayer targetPlayer) {
      boolean log = ConfigManager.getServerConfig().getGameplay().getCommandOutputOnConsole();

      try {
         String raw = StringArgumentType.getString(context, "quest");
         String questKey = "none".equalsIgnoreCase(raw) ? null : normalizeId(raw);
         List<ServerPlayer> targets = getTargetPlayers(context, targetPlayer);
         if (targets.isEmpty()) {
            ((CommandSourceStack)context.getSource()).sendFailure(Component.literal("No valid players found."));
            return 0;
         } else if (questKey != null && QuestRegistry.getQuest(questKey) == null) {
            ((CommandSourceStack)context.getSource())
               .sendFailure(Component.literal("Quest not found: " + questKey + "  (tip: use '.' instead of ':' — e.g. saiyan_saga.1)"));
            return 0;
         } else {
            int successCount = 0;

            for (ServerPlayer player : targets) {
               StatsProvider.get(StatsCapability.INSTANCE, player).ifPresent(stats -> {
                  stats.getPlayerQuestData().setTrackedQuestId(questKey);
                  QuestService.syncQuestState(player);
               });
               successCount++;
            }

            String label = questKey == null ? "none" : questKey;
            int affected = successCount;
            ((CommandSourceStack)context.getSource())
               .sendSuccess(() -> Component.literal("Set tracked quest to '" + label + "' for " + affected + " player(s)."), log);
            return successCount;
         }
      } catch (Exception var9) {
         ((CommandSourceStack)context.getSource()).sendFailure(Component.literal("Failed to set tracked quest: " + var9.getMessage()));
         return 0;
      }
   }

   private static int startSaga(CommandContext<CommandSourceStack> context, ServerPlayer targetPlayer) {
      boolean log = ConfigManager.getServerConfig().getGameplay().getCommandOutputOnConsole();

      try {
         String sagaId = normalizeId(StringArgumentType.getString(context, "saga"));
         List<ServerPlayer> targets = getTargetPlayers(context, targetPlayer);
         if (targets.isEmpty()) {
            ((CommandSourceStack)context.getSource()).sendFailure(Component.literal("No valid players found."));
            return 0;
         } else if (!"all".equalsIgnoreCase(sagaId) && QuestRegistry.getSaga(sagaId) == null) {
            ((CommandSourceStack)context.getSource()).sendFailure(Component.literal("Saga not found: " + sagaId));
            return 0;
         } else {
            int successCount = 0;

            for (ServerPlayer player : targets) {
               StatsProvider.get(StatsCapability.INSTANCE, player).ifPresent(stats -> {
                  PlayerQuestData pqd = stats.getPlayerQuestData();
                  int partySize = PartyManager.getAllPartyMembers(player).size();
                  String prefix = "all".equalsIgnoreCase(sagaId) ? null : sagaId + ":";

                  for (Entry<String, Quest> entry : QuestRegistry.getAllQuests().entrySet()) {
                     String key = entry.getKey();
                     Quest quest = entry.getValue();
                     if (quest.isSagaQuest() && (prefix == null || key.startsWith(prefix)) && !pqd.isQuestAccepted(key) && !pqd.isQuestCompleted(key)) {
                        pqd.acceptQuest(key);
                        quest.initializeObjectiveRequirements(pqd, key, partySize);
                     }
                  }

                  QuestService.syncQuestState(player);
               });
               successCount++;
            }

            int affected = successCount;
            ((CommandSourceStack)context.getSource())
               .sendSuccess(
                  () -> Component.literal(
                        ("all".equalsIgnoreCase(sagaId) ? "Started all sagas" : "Started saga " + sagaId) + " for " + affected + " player(s)."
                     ),
                  log
               );
            return successCount;
         }
      } catch (Exception var8) {
         ((CommandSourceStack)context.getSource()).sendFailure(Component.literal("Failed to start saga: " + var8.getMessage()));
         return 0;
      }
   }

   private static int finishSaga(CommandContext<CommandSourceStack> context, ServerPlayer targetPlayer) {
      boolean log = ConfigManager.getServerConfig().getGameplay().getCommandOutputOnConsole();

      try {
         String sagaId = normalizeId(StringArgumentType.getString(context, "saga"));
         List<ServerPlayer> targets = getTargetPlayers(context, targetPlayer);
         if (targets.isEmpty()) {
            ((CommandSourceStack)context.getSource()).sendFailure(Component.literal("No valid players found."));
            return 0;
         } else if (!"all".equalsIgnoreCase(sagaId) && QuestRegistry.getSaga(sagaId) == null) {
            ((CommandSourceStack)context.getSource()).sendFailure(Component.literal("Saga not found: " + sagaId));
            return 0;
         } else {
            int successCount = 0;

            for (ServerPlayer player : targets) {
               StatsProvider.get(StatsCapability.INSTANCE, player).ifPresent(stats -> {
                  PlayerQuestData pqd = stats.getPlayerQuestData();
                  String prefix = "all".equalsIgnoreCase(sagaId) ? null : sagaId + ":";

                  for (Entry<String, Quest> entry : QuestRegistry.getAllQuests().entrySet()) {
                     String key = entry.getKey();
                     Quest quest = entry.getValue();
                     if (quest.isSagaQuest() && (prefix == null || key.startsWith(prefix))) {
                        completeQuest(pqd, key, quest, player);
                     }
                  }

                  QuestService.syncQuestState(player);
               });
               successCount++;
            }

            int affected = successCount;
            ((CommandSourceStack)context.getSource())
               .sendSuccess(
                  () -> Component.literal(
                        ("all".equalsIgnoreCase(sagaId) ? "Finished all sagas" : "Finished saga " + sagaId) + " for " + affected + " player(s)."
                     ),
                  log
               );
            return successCount;
         }
      } catch (Exception var8) {
         ((CommandSourceStack)context.getSource()).sendFailure(Component.literal("Failed to finish saga: " + var8.getMessage()));
         return 0;
      }
   }

   private static int resetSaga(CommandContext<CommandSourceStack> context, ServerPlayer targetPlayer) {
      boolean log = ConfigManager.getServerConfig().getGameplay().getCommandOutputOnConsole();

      try {
         String sagaId = normalizeId(StringArgumentType.getString(context, "saga"));
         List<ServerPlayer> targets = getTargetPlayers(context, targetPlayer);
         if (targets.isEmpty()) {
            ((CommandSourceStack)context.getSource()).sendFailure(Component.literal("No valid players found."));
            return 0;
         } else if (!"all".equalsIgnoreCase(sagaId) && QuestRegistry.getSaga(sagaId) == null) {
            ((CommandSourceStack)context.getSource()).sendFailure(Component.literal("Saga not found: " + sagaId));
            return 0;
         } else {
            int successCount = 0;

            for (ServerPlayer player : targets) {
               StatsProvider.get(StatsCapability.INSTANCE, player).ifPresent(stats -> {
                  PlayerQuestData pqd = stats.getPlayerQuestData();
                  if ("all".equalsIgnoreCase(sagaId)) {
                     for (String acceptedQuestKey : pqd.getAcceptedQuestIds()) {
                        Quest acceptedQuest = QuestRegistry.getQuest(acceptedQuestKey);
                        if (acceptedQuest != null) {
                           postForcedResetFailEvent(player, pqd, acceptedQuestKey, acceptedQuest);
                        }
                     }

                     pqd.resetAll();
                  } else {
                     String prefix = sagaId + ":";

                     for (String questKey : collectKnownQuestKeys(pqd)) {
                        if (questKey.startsWith(prefix)) {
                           Quest quest = QuestRegistry.getQuest(questKey);
                           if (quest == null || postForcedResetFailEvent(player, pqd, questKey, quest)) {
                              resetQuestEntry(pqd, questKey);
                           }
                        }
                     }
                  }

                  QuestService.syncQuestState(player);
               });
               successCount++;
            }

            int affected = successCount;
            ((CommandSourceStack)context.getSource())
               .sendSuccess(
                  () -> Component.literal(("all".equalsIgnoreCase(sagaId) ? "Reset all sagas" : "Reset saga " + sagaId) + " for " + affected + " player(s)."),
                  log
               );
            return successCount;
         }
      } catch (Exception var8) {
         ((CommandSourceStack)context.getSource()).sendFailure(Component.literal("Failed to reset saga: " + var8.getMessage()));
         return 0;
      }
   }

   private static int spawnQuestNPC(CommandContext<CommandSourceStack> context, String modelOverride, String textureOverride) {
      try {
         String npcId = StringArgumentType.getString(context, "npcId").toLowerCase();
         ServerPlayer player = ((CommandSourceStack)context.getSource()).getPlayerOrException();
         QuestNPCEntity npc = (QuestNPCEntity)((EntityType)MainEntities.QUEST_NPC.get()).create(player.level());
         if (npc == null) {
            ((CommandSourceStack)context.getSource()).sendFailure(Component.literal("Failed to create QuestNPCEntity."));
            return 0;
         } else {
            npc.setNpcId(npcId.toLowerCase());
            if (modelOverride != null && !modelOverride.isEmpty()) {
               npc.setNpcModel(modelOverride.toLowerCase());
            }

            if (textureOverride != null && !textureOverride.isEmpty()) {
               npc.setNpcTexture(textureOverride.toLowerCase());
            }

            npc.setPersistenceRequired();
            npc.setInvulnerable(true);
            npc.setHealth(npc.getMaxHealth());
            npc.setPos(player.getX(), player.getY(), player.getZ());
            npc.setHomePosition(player.getX(), player.getZ());
            player.serverLevel().addFreshEntity(npc);
            ((CommandSourceStack)context.getSource()).sendSuccess(() -> Component.literal("Spawned quest NPC '" + npcId + "'."), true);
            return 1;
         }
      } catch (Exception var6) {
         ((CommandSourceStack)context.getSource()).sendFailure(Component.literal("Failed to spawn quest NPC: " + var6.getMessage()));
         return 0;
      }
   }

   private static int listNearbyQuestNPCs(CommandContext<CommandSourceStack> context) {
      try {
         ServerPlayer player = ((CommandSourceStack)context.getSource()).getPlayerOrException();
         AABB searchBox = player.getBoundingBox().inflate(16.0);
         List<QuestNPCEntity> npcs = player.serverLevel().getEntitiesOfClass(QuestNPCEntity.class, searchBox);
         if (npcs.isEmpty()) {
            ((CommandSourceStack)context.getSource()).sendSystemMessage(Component.literal("No quest NPCs within 16 blocks."));
            return 0;
         } else {
            ((CommandSourceStack)context.getSource()).sendSystemMessage(Component.literal("Quest NPCs within 16 blocks (" + npcs.size() + "):"));
            npcs.sort(Comparator.comparingDouble(npcx -> npcx.distanceToSqr(player)));

            for (QuestNPCEntity npc : npcs) {
               int dist = (int)npc.distanceTo(player);
               ((CommandSourceStack)context.getSource())
                  .sendSystemMessage(
                     Component.literal(
                        "  [" + npc.getNpcId() + "] @ (" + (int)npc.getX() + ", " + (int)npc.getY() + ", " + (int)npc.getZ() + ") — " + dist + "m away"
                     )
                  );
            }

            return npcs.size();
         }
      } catch (Exception var7) {
         ((CommandSourceStack)context.getSource()).sendFailure(Component.literal("Failed to list quest NPCs: " + var7.getMessage()));
         return 0;
      }
   }

   private static int removeNearestQuestNPC(CommandContext<CommandSourceStack> context) {
      try {
         ServerPlayer player = ((CommandSourceStack)context.getSource()).getPlayerOrException();
         AABB searchBox = player.getBoundingBox().inflate(16.0);
         List<QuestNPCEntity> npcs = player.serverLevel().getEntitiesOfClass(QuestNPCEntity.class, searchBox);
         if (npcs.isEmpty()) {
            ((CommandSourceStack)context.getSource()).sendFailure(Component.literal("No quest NPCs within 16 blocks."));
            return 0;
         } else {
            QuestNPCEntity nearest = npcs.stream().min(Comparator.comparingDouble(npc -> npc.distanceToSqr(player))).orElse(null);
            if (nearest == null) {
               return 0;
            } else {
               String npcId = nearest.getNpcId();
               nearest.discard();
               ((CommandSourceStack)context.getSource()).sendSuccess(() -> Component.literal("Removed quest NPC '" + npcId + "'."), true);
               return 1;
            }
         }
      } catch (Exception var6) {
         ((CommandSourceStack)context.getSource()).sendFailure(Component.literal("Failed to remove quest NPC: " + var6.getMessage()));
         return 0;
      }
   }

   private static Component formatQuestStatus(PlayerQuestData pqd, String questKey) {
      if (pqd.isQuestCompleted(questKey)) {
         return Component.literal("[SUCCESS]");
      } else if (pqd.isQuestAccepted(questKey)) {
         return Component.literal("[ACTIVE]");
      } else {
         return pqd.getQuestStatus(questKey) == PlayerQuestData.QuestStatus.FAILED ? Component.literal("[FAILED]") : Component.literal("[NOT STARTED]");
      }
   }

   private static void completeQuest(PlayerQuestData pqd, String questKey, Quest quest, ServerPlayer player) {
      if (!pqd.isQuestAccepted(questKey)) {
         pqd.acceptQuest(questKey);
      }

      quest.initializeObjectiveRequirements(pqd, questKey, PartyManager.getAllPartyMembers(player).size());

      for (int i = 0; i < quest.getObjectives().size(); i++) {
         pqd.setObjectiveProgress(questKey, i, quest.getObjectiveRequired(pqd, questKey, i));
      }

      QuestService.ResolvedQuest resolved = QuestService.resolveQuest(questKey);
      Saga saga = resolved != null ? resolved.saga() : null;
      DMZEvent.QuestCompletedEvent completeEvent = new DMZEvent.QuestCompletedEvent(player, questKey, saga, quest, PartyManager.getAllPartyMembers(player));
      if (!((DMZEvent.QuestCompletedEvent)NeoForge.EVENT_BUS.post(completeEvent)).isCanceled()) {
         pqd.completeQuest(questKey);
         if (questKey.equals(pqd.getTrackedQuestId())) {
            pqd.setTrackedQuestId(null);
         }
      }
   }

   private static boolean postForcedFailEvent(ServerPlayer player, PlayerQuestData pqd, String questKey, Quest quest) {
      if (!pqd.isQuestAccepted(questKey)) {
         return false;
      } else {
         QuestService.ResolvedQuest resolved = QuestService.resolveQuest(questKey);
         DMZEvent.QuestFailEvent failEvent = new DMZEvent.QuestFailEvent(
            player,
            questKey,
            resolved != null ? resolved.saga() : null,
            quest,
            PartyManager.getAllPartyMembers(player),
            DMZEvent.QuestFailEvent.FailureReason.FORCED_RESET
         );
         return !((DMZEvent.QuestFailEvent)NeoForge.EVENT_BUS.post(failEvent)).isCanceled();
      }
   }

   private static boolean postForcedResetFailEvent(ServerPlayer player, PlayerQuestData pqd, String questKey, Quest quest) {
      if (pqd.isQuestAccepted(questKey) && !pqd.isQuestCompleted(questKey)) {
         QuestService.ResolvedQuest resolved = QuestService.resolveQuest(questKey);
         DMZEvent.QuestFailEvent failEvent = new DMZEvent.QuestFailEvent(
            player,
            questKey,
            resolved != null ? resolved.saga() : null,
            quest,
            PartyManager.getAllPartyMembers(player),
            DMZEvent.QuestFailEvent.FailureReason.FORCED_RESET
         );
         return !((DMZEvent.QuestFailEvent)NeoForge.EVENT_BUS.post(failEvent)).isCanceled();
      } else {
         return true;
      }
   }

   private static Set<String> collectKnownQuestKeys(PlayerQuestData pqd) {
      Set<String> questKeys = new LinkedHashSet<>();
      questKeys.addAll(pqd.getAcceptedQuestIds());
      questKeys.addAll(pqd.getCompletedQuestIds());
      questKeys.addAll(pqd.getFailedQuestIds());
      return questKeys;
   }

   private static void resetQuestEntry(PlayerQuestData pqd, String questKey) {
      pqd.resetQuest(questKey);
      if (questKey.equals(pqd.getTrackedQuestId())) {
         pqd.setTrackedQuestId(null);
      }
   }

   private static List<ServerPlayer> getTargetPlayers(CommandContext<CommandSourceStack> context, ServerPlayer targetPlayer) {
      List<ServerPlayer> players = new ArrayList<>();
      if (targetPlayer != null) {
         players.addAll(PartyManager.getAllPartyMembers(targetPlayer));
         return players;
      } else {
         try {
            ServerPlayer executor = ((CommandSourceStack)context.getSource()).getPlayerOrException();
            players.addAll(PartyManager.getAllPartyMembers(executor));
         } catch (Exception var4) {
         }

         return players;
      }
   }
}
