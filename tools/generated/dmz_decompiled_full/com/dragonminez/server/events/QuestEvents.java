package com.dragonminez.server.events;

import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.events.DMZEvent;
import com.dragonminez.common.init.entities.MastersEntity;
import com.dragonminez.common.init.entities.questnpc.QuestNPCEntity;
import com.dragonminez.common.network.NetworkHandler;
import com.dragonminez.common.network.S2C.ProgressionSyncS2C;
import com.dragonminez.common.network.S2C.StoryToastS2C;
import com.dragonminez.common.quest.PartyManager;
import com.dragonminez.common.quest.PlayerQuestData;
import com.dragonminez.common.quest.Quest;
import com.dragonminez.common.quest.QuestAvailabilityChecker;
import com.dragonminez.common.quest.QuestLocationHelper;
import com.dragonminez.common.quest.QuestObjective;
import com.dragonminez.common.quest.QuestRegistry;
import com.dragonminez.common.quest.QuestService;
import com.dragonminez.common.quest.objectives.DragonSummonObjective;
import com.dragonminez.common.quest.objectives.InteractObjective;
import com.dragonminez.common.quest.objectives.ItemObjective;
import com.dragonminez.common.quest.objectives.KillObjective;
import com.dragonminez.common.quest.objectives.SkillObjective;
import com.dragonminez.common.quest.objectives.TalkToObjective;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.Map.Entry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.EntityInteract;
import net.neoforged.neoforge.event.tick.PlayerTickEvent.Post;

@EventBusSubscriber(
   modid = "dragonminez"
)
public class QuestEvents {
   @SubscribeEvent
   public static void onPlayerTick(Post event) {
      if (!event.getEntity().level().isClientSide) {
         if (event.getEntity().tickCount % 20 == 0) {
            if (event.getEntity() instanceof ServerPlayer player) {
               StatsProvider.get(StatsCapability.INSTANCE, player).ifPresent(data -> {
                  boolean timingChanged = primeStartRequirementTimers(player, data);
                  processTickObjectives(player, data);
                  if (timingChanged) {
                     NetworkHandler.sendToTrackingEntityAndSelf(new ProgressionSyncS2C(player), player);
                  }
               });
            }
         }
      }
   }

   @SubscribeEvent
   public static void onEntityKill(LivingDeathEvent event) {
      if (event.getEntity() instanceof ServerPlayer deadPlayer) {
         handlePlayerQuestFailure(deadPlayer);
      }

      if (event.getSource().getEntity() instanceof ServerPlayer killer) {
         creditQuestKill(killer, event.getEntity());
      }
   }

   public static void creditQuestKill(ServerPlayer killer, LivingEntity killedEntity) {
      List<ServerPlayer> partyMembers = PartyManager.getAllPartyMembers(killer);

      for (ServerPlayer member : partyMembers) {
         StatsProvider.get(StatsCapability.INSTANCE, member)
            .ifPresent(
               data -> processAcceptedQuests(
                     member, data, (questKey, quest, pqd) -> processKillObjectives(member, pqd, questKey, quest, killedEntity, partyMembers)
                  )
            );
      }
   }

   private static void handlePlayerQuestFailure(ServerPlayer deadPlayer) {
      ServerPlayer controller = PartyManager.resolveQuestController(deadPlayer);
      if (controller != null) {
         if (isPartyWiped(controller, deadPlayer)) {
            StatsProvider.get(StatsCapability.INSTANCE, controller)
               .ifPresent(
                  data -> {
                     PlayerQuestData pqd = data.getPlayerQuestData();
                     Set<String> acceptedQuestIds = new LinkedHashSet<>(pqd.getAcceptedQuestIds());
                     if (!acceptedQuestIds.isEmpty()) {
                        Set<String> failedQuestIds = new LinkedHashSet<>();
                        List<ServerPlayer> partyMembers = PartyManager.getAllPartyMembers(controller);

                        for (String questKey : acceptedQuestIds) {
                           Quest quest = QuestRegistry.getQuest(questKey);
                           if (quest != null && !pqd.isQuestCompleted(questKey) && hasKillObjectives(quest)) {
                              QuestService.ResolvedQuest resolved = QuestService.resolveQuest(questKey);
                              DMZEvent.QuestFailEvent failEvent = new DMZEvent.QuestFailEvent(
                                 controller,
                                 questKey,
                                 resolved != null ? resolved.saga() : null,
                                 quest,
                                 partyMembers,
                                 DMZEvent.QuestFailEvent.FailureReason.PLAYER_DEATH
                              );
                              if (!((DMZEvent.QuestFailEvent)NeoForge.EVENT_BUS.post(failEvent)).isCanceled()) {
                                 pqd.failQuest(questKey);
                                 failedQuestIds.add(questKey);
                              }
                           }
                        }

                        if (!failedQuestIds.isEmpty()) {
                           notifyQuestFailure(controller, failedQuestIds);
                           QuestService.syncQuestState(controller);
                        }
                     }
                  }
               );
         }
      }
   }

   private static boolean isPartyWiped(ServerPlayer controller, ServerPlayer deadPlayer) {
      for (ServerPlayer member : PartyManager.getAllPartyMembers(controller)) {
         if (!member.getUUID().equals(deadPlayer.getUUID()) && !member.isDeadOrDying()) {
            return false;
         }
      }

      return true;
   }

   @SubscribeEvent
   public static void onEntityInteract(EntityInteract event) {
      if (event.getEntity() instanceof ServerPlayer interactor) {
         if (event.getHand() == InteractionHand.MAIN_HAND) {
            String interactedNpcId = null;
            if (event.getTarget() instanceof QuestNPCEntity questNpc) {
               interactedNpcId = questNpc.getNpcId();
            } else if (event.getTarget() instanceof MastersEntity master) {
               interactedNpcId = master.getMasterName();
            }

            String finalNpcId = interactedNpcId;

            for (ServerPlayer member : PartyManager.getAllPartyMembers(interactor)) {
               StatsProvider.get(StatsCapability.INSTANCE, member)
                  .ifPresent(
                     data -> processAcceptedQuests(
                           member, data, (questKey, quest, pqd) -> processInteractObjectives(member, pqd, questKey, quest, event, finalNpcId)
                        )
                  );
            }
         }
      }
   }

   @SubscribeEvent
   public static void onDragonSummoned(DMZEvent.DragonSummonedEvent event) {
      if (event.getPlayer() instanceof ServerPlayer summoner) {
         for (ServerPlayer member : PartyManager.getAllPartyMembers(summoner)) {
            StatsProvider.get(StatsCapability.INSTANCE, member)
               .ifPresent(
                  data -> processAcceptedQuests(member, data, (questKey, quest, pqd) -> processDragonSummonObjectives(member, pqd, questKey, quest, event))
               );
         }
      }
   }

   private static boolean primeStartRequirementTimers(ServerPlayer player, StatsData data) {
      PlayerQuestData pqd = data.getPlayerQuestData();
      boolean timingChanged = false;

      for (Entry<String, Quest> entry : QuestRegistry.getAllQuests().entrySet()) {
         String questKey = entry.getKey();
         Quest quest = entry.getValue();
         if (isQuestTypeEnabled(quest)
            && !pqd.isQuestAccepted(questKey)
            && !pqd.isQuestCompleted(questKey)
            && pqd.getQuestStatus(questKey) != PlayerQuestData.QuestStatus.FAILED
            && isQuestAvailableForTracking(questKey, quest, data)) {
            timingChanged |= QuestAvailabilityChecker.primeStartRequirementTiming(quest, questKey, player, data);
         }
      }

      return timingChanged;
   }

   private static void processTickObjectives(ServerPlayer player, StatsData data) {
      processAcceptedQuests(player, data, (questKey, quest, pqd) -> {
         for (int i = 0; i < quest.getObjectives().size(); i++) {
            QuestObjective objective = quest.getObjectives().get(i);
            int currentProgress = pqd.getObjectiveProgress(questKey, i);
            if (currentProgress < quest.getObjectiveRequired(pqd, questKey, i)) {
               if (!quest.isParallelObjectives() && !isFirstUncompleted(pqd, questKey, quest, i)) {
                  break;
               }

               if (!QuestLocationHelper.isLocationObjective(objective)) {
                  if (objective instanceof ItemObjective) {
                     ItemObjective itemObjective = (ItemObjective)objective;
                     int itemCount = countItems(player, itemObjective.getItemId());
                     if (itemCount != currentProgress) {
                        int progressToSet = Math.min(itemCount, quest.getObjectiveRequired(pqd, questKey, i));
                        updateProgress(player, pqd, questKey, quest, i, progressToSet);
                     }
                  } else if (objective instanceof SkillObjective) {
                     SkillObjective skillObjective = (SkillObjective)objective;
                     int skillLevel = data.getSkills().getSkillLevel(skillObjective.getSkill());
                     if (skillLevel != currentProgress) {
                        int progressToSet = Math.min(skillLevel, quest.getObjectiveRequired(pqd, questKey, i));
                        updateProgress(player, pqd, questKey, quest, i, progressToSet);
                     }
                  }
               } else {
                  List<ServerPlayer> partyMembers = PartyManager.getAllPartyMembers(player);
                  boolean anyMemberInZone = false;

                  for (ServerPlayer member : partyMembers) {
                     if (QuestLocationHelper.isLocationConditionMet(member, objective)) {
                        anyMemberInZone = true;
                        break;
                     }
                  }

                  int targetProgress = anyMemberInZone ? 1 : 0;
                  updatePartyProgress(partyMembers, questKey, quest, i, targetProgress);
               }

               if (!quest.isParallelObjectives()) {
                  break;
               }
            }
         }

         checkAndComplete(player, pqd, questKey, quest);
      });
   }

   private static void processKillObjectives(
      ServerPlayer player, PlayerQuestData pqd, String questKey, Quest quest, LivingEntity killedEntity, List<ServerPlayer> partyMembers
   ) {
      for (int i = 0; i < quest.getObjectives().size(); i++) {
         QuestObjective objective = quest.getObjectives().get(i);
         int currentProgress = pqd.getObjectiveProgress(questKey, i);
         if (currentProgress < quest.getObjectiveRequired(pqd, questKey, i) && objective instanceof KillObjective) {
            KillObjective killObjective = (KillObjective)objective;
            if (isKillObjectiveUnlocked(pqd, questKey, quest, i) && matchesKillObjective(killedEntity, questKey, i, killObjective, partyMembers)) {
               updateProgress(player, pqd, questKey, quest, i, currentProgress + 1);
            }
         }
      }

      checkAndComplete(player, pqd, questKey, quest);
   }

   private static void processInteractObjectives(
      ServerPlayer player, PlayerQuestData pqd, String questKey, Quest quest, EntityInteract event, String interactedNpcId
   ) {
      for (int i = 0; i < quest.getObjectives().size(); i++) {
         QuestObjective objective = quest.getObjectives().get(i);
         int currentProgress = pqd.getObjectiveProgress(questKey, i);
         if (currentProgress < quest.getObjectiveRequired(pqd, questKey, i) && (quest.isParallelObjectives() || isFirstUncompleted(pqd, questKey, quest, i))) {
            if (objective instanceof InteractObjective) {
               InteractObjective interactObjective = (InteractObjective)objective;
               String targetStr = interactObjective.getEntityTypeId();
               EntityType<?> requiredType = targetStr != null ? (EntityType)BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse(targetStr)) : null;
               if (requiredType == null || event.getTarget().getType().equals(requiredType)) {
                  updateProgress(player, pqd, questKey, quest, i, currentProgress + 1);
               }
            } else if (objective instanceof TalkToObjective) {
               TalkToObjective talkToObjective = (TalkToObjective)objective;
               if (interactedNpcId != null && !QuestService.requiresTurnInAction(quest) && interactedNpcId.equals(talkToObjective.getNpcId())) {
                  updateProgress(player, pqd, questKey, quest, i, currentProgress + 1);
               }
            }
         }
      }

      checkAndComplete(player, pqd, questKey, quest);
   }

   private static void processDragonSummonObjectives(ServerPlayer player, PlayerQuestData pqd, String questKey, Quest quest, DMZEvent.DragonSummonedEvent event) {
      for (int i = 0; i < quest.getObjectives().size(); i++) {
         QuestObjective objective = quest.getObjectives().get(i);
         int currentProgress = pqd.getObjectiveProgress(questKey, i);
         if (currentProgress < quest.getObjectiveRequired(pqd, questKey, i)
            && (quest.isParallelObjectives() || isFirstUncompleted(pqd, questKey, quest, i))
            && objective instanceof DragonSummonObjective) {
            DragonSummonObjective summonObjective = (DragonSummonObjective)objective;
            if (summonObjective.matches(event.getDragonId(), event.getBallSetId())) {
               updateProgress(player, pqd, questKey, quest, i, currentProgress + 1);
            }
         }
      }

      checkAndComplete(player, pqd, questKey, quest);
   }

   private static void processAcceptedQuests(ServerPlayer player, StatsData data, QuestEvents.AcceptedQuestProcessor processor) {
      PlayerQuestData pqd = data.getPlayerQuestData();

      for (String questKey : pqd.getAcceptedQuestIds()) {
         Quest quest = QuestRegistry.getQuest(questKey);
         if (quest != null && !pqd.isQuestCompleted(questKey) && isQuestTypeEnabled(quest)) {
            processor.process(questKey, quest, pqd);
         }
      }
   }

   private static boolean isQuestAvailableForTracking(String questKey, Quest quest, StatsData data) {
      if (!quest.isSagaQuest()) {
         return QuestAvailabilityChecker.isAvailable(quest, data);
      } else {
         QuestService.ResolvedQuest resolved = QuestService.resolveQuest(questKey);
         if (resolved != null && resolved.saga() != null) {
            int questIndex = resolved.saga().getQuests().indexOf(quest);
            return questIndex >= 0 && QuestAvailabilityChecker.isSagaQuestAvailable(quest, resolved.saga(), questIndex, data);
         } else {
            return false;
         }
      }
   }

   private static boolean isQuestTypeEnabled(Quest quest) {
      if (quest == null) {
         return false;
      } else if (quest.isSagaQuest()) {
         return ConfigManager.getServerConfig().getGameplay().getStoryModeEnabled();
      } else {
         return quest.isSideQuest() ? ConfigManager.getServerConfig().getGameplay().getSideQuestsEnabled() : true;
      }
   }

   private static boolean hasKillObjectives(Quest quest) {
      for (QuestObjective objective : quest.getObjectives()) {
         if (objective instanceof KillObjective) {
            return true;
         }
      }

      return false;
   }

   private static void notifyQuestFailure(ServerPlayer controller, Set<String> failedQuestIds) {
      List<ServerPlayer> partyMembers = PartyManager.getAllPartyMembers(controller);

      for (String questKey : failedQuestIds) {
         for (ServerPlayer member : partyMembers) {
            NetworkHandler.sendToPlayer(StoryToastS2C.questFailed(questKey), member);
         }
      }
   }

   private static int countItems(ServerPlayer player, String itemId) {
      try {
         Item item = (Item)BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemId));
         int count = 0;

         for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.getItem() == item) {
               count += stack.getCount();
            }
         }

         return count;
      } catch (Exception var6) {
         return 0;
      }
   }

   private static boolean matchesKillObjective(
      LivingEntity killedEntity, String questKey, int objectiveIndex, KillObjective killObjective, List<ServerPlayer> partyMembers
   ) {
      try {
         boolean typeMatches = killObjective.matches(killedEntity.getType());
         boolean hasQuestTags = hasQuestSpawnTags(killedEntity);
         boolean questTagsMatch = hasQuestTags && matchesQuestSpawnTags(killedEntity, questKey, objectiveIndex, partyMembers);
         return acceptsKillMatch(typeMatches, questTagsMatch, killObjective.getCountMode(), hasQuestTags);
      } catch (Exception var8) {
         return false;
      }
   }

   static boolean acceptsKillMatch(boolean entityTypeMatches, boolean questSpawnTagsMatch, KillObjective.CountMode countMode, boolean hasQuestSpawnTags) {
      return questSpawnTagsMatch ? true : entityTypeMatches && countMode == KillObjective.CountMode.ANY_MATCHING && !hasQuestSpawnTags;
   }

   private static boolean hasQuestSpawnTags(LivingEntity killedEntity) {
      return killedEntity.getPersistentData().contains("dmz_quest_key")
         || killedEntity.getPersistentData().contains("dmz_quest_objective_index")
         || killedEntity.getPersistentData().contains("dmz_quest_owner");
   }

   private static boolean matchesQuestSpawnTags(LivingEntity killedEntity, String questKey, int objectiveIndex, List<ServerPlayer> partyMembers) {
      if (killedEntity.getPersistentData().contains("dmz_quest_key")
         && killedEntity.getPersistentData().contains("dmz_quest_objective_index")
         && killedEntity.getPersistentData().contains("dmz_quest_owner")) {
         if (questKey.equals(killedEntity.getPersistentData().getString("dmz_quest_key"))
            && objectiveIndex == killedEntity.getPersistentData().getInt("dmz_quest_objective_index")) {
            String ownerUuid = killedEntity.getPersistentData().getString("dmz_quest_owner");

            for (ServerPlayer member : partyMembers) {
               if (member.getStringUUID().equals(ownerUuid)) {
                  return true;
               }
            }

            return false;
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   static boolean isKillObjectiveUnlocked(PlayerQuestData pqd, String questKey, Quest quest, int objectiveIndex) {
      if (quest != null && objectiveIndex >= 0 && objectiveIndex < quest.getObjectives().size()) {
         if (!(quest.getObjectives().get(objectiveIndex) instanceof KillObjective)) {
            return false;
         } else if (quest.isParallelObjectives()) {
            return true;
         } else {
            int killBlockStart = objectiveIndex;

            while (killBlockStart > 0 && quest.getObjectives().get(killBlockStart - 1) instanceof KillObjective) {
               killBlockStart--;
            }

            return isFirstUncompleted(pqd, questKey, quest, killBlockStart);
         }
      } else {
         return false;
      }
   }

   private static boolean isFirstUncompleted(PlayerQuestData pqd, String questKey, Quest quest, int targetIndex) {
      for (int i = 0; i < targetIndex; i++) {
         int progress = pqd.getObjectiveProgress(questKey, i);
         if (progress < quest.getObjectiveRequired(pqd, questKey, i)) {
            return false;
         }
      }

      return true;
   }

   private static void updatePartyProgress(List<ServerPlayer> members, String questKey, Quest quest, int objectiveIndex, int newProgress) {
      for (ServerPlayer member : members) {
         StatsProvider.get(StatsCapability.INSTANCE, member).ifPresent(data -> {
            PlayerQuestData pqd = data.getPlayerQuestData();
            if (pqd.isQuestAccepted(questKey) && !pqd.isQuestCompleted(questKey)) {
               if (quest.isParallelObjectives() || isFirstUncompleted(pqd, questKey, quest, objectiveIndex)) {
                  updateProgress(member, pqd, questKey, quest, objectiveIndex, newProgress);
                  checkAndComplete(member, pqd, questKey, quest);
               }
            }
         });
      }
   }

   private static void updateProgress(ServerPlayer player, PlayerQuestData pqd, String questKey, Quest quest, int objectiveIndex, int newProgress) {
      int current = pqd.getObjectiveProgress(questKey, objectiveIndex);
      if (current != newProgress) {
         QuestService.ResolvedQuest resolved = QuestService.resolveQuest(questKey);
         int required = objectiveIndex >= 0 && objectiveIndex < quest.getObjectives().size() ? quest.getObjectiveRequired(pqd, questKey, objectiveIndex) : 0;
         DMZEvent.QuestObjectiveProgressEvent progressEvent = new DMZEvent.QuestObjectiveProgressEvent(
            player,
            questKey,
            resolved != null ? resolved.saga() : null,
            quest,
            PartyManager.getAllPartyMembers(player),
            objectiveIndex,
            current,
            newProgress,
            required
         );
         if (!((DMZEvent.QuestObjectiveProgressEvent)NeoForge.EVENT_BUS.post(progressEvent)).isCanceled()) {
            newProgress = progressEvent.getNewProgress();
            if (current != newProgress) {
               pqd.setObjectiveProgress(questKey, objectiveIndex, newProgress);
               if (objectiveIndex >= 0 && objectiveIndex < quest.getObjectives().size() && current < required && newProgress >= required) {
                  int clampedProgress = Math.min(newProgress, required);
                  NetworkHandler.sendToPlayer(StoryToastS2C.objectiveComplete(questKey, objectiveIndex, clampedProgress, required), player);
               }

               NetworkHandler.sendToTrackingEntityAndSelf(new ProgressionSyncS2C(player), player);
            }
         }
      }
   }

   private static void checkAndComplete(ServerPlayer player, PlayerQuestData pqd, String questKey, Quest quest) {
      if (!pqd.isQuestCompleted(questKey) && !QuestService.requiresTurnInAction(quest)) {
         for (int i = 0; i < quest.getObjectives().size(); i++) {
            int progress = pqd.getObjectiveProgress(questKey, i);
            if (progress < quest.getObjectiveRequired(pqd, questKey, i)) {
               return;
            }
         }

         QuestService.ResolvedQuest resolved = QuestService.resolveQuest(questKey);
         DMZEvent.QuestCompletedEvent completeEvent = new DMZEvent.QuestCompletedEvent(
            player, questKey, resolved != null ? resolved.saga() : null, quest, PartyManager.getAllPartyMembers(player)
         );
         if (!((DMZEvent.QuestCompletedEvent)NeoForge.EVENT_BUS.post(completeEvent)).isCanceled()) {
            pqd.completeQuest(questKey);
            if (questKey.equals(pqd.getTrackedQuestId())) {
               pqd.setTrackedQuestId(null);
            }

            NetworkHandler.sendToPlayer(StoryToastS2C.questComplete(questKey), player);
            NetworkHandler.sendToTrackingEntityAndSelf(new ProgressionSyncS2C(player), player);
         }
      }
   }

   @FunctionalInterface
   private interface AcceptedQuestProcessor {
      void process(String var1, Quest var2, PlayerQuestData var3);
   }
}
