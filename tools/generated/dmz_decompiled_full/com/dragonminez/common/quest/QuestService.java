package com.dragonminez.common.quest;

import com.dragonminez.Env;
import com.dragonminez.LogUtil;
import com.dragonminez.common.events.DMZEvent;
import com.dragonminez.common.network.NetworkHandler;
import com.dragonminez.common.network.S2C.ProgressionSyncS2C;
import com.dragonminez.common.network.S2C.StoryToastS2C;
import com.dragonminez.common.quest.objectives.KillObjective;
import com.dragonminez.common.quest.objectives.TalkToObjective;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.NeoForge;
import org.jetbrains.annotations.Nullable;

public final class QuestService {
   public static final String QUEST_KEY_TAG = "dmz_quest_key";
   public static final String QUEST_OBJECTIVE_INDEX_TAG = "dmz_quest_objective_index";
   public static final String QUEST_OWNER_TAG = "dmz_quest_owner";
   public static final String SAGA_ID_TAG = "dmz_saga_id";
   public static final String QUEST_TEAM_TAG = "dmz_quest_team";
   private static final long RESUMMON_MIN_INTERVAL_MS = 1500L;
   private static final Map<UUID, Long> resummonAntiSpam = new ConcurrentHashMap<>();
   private static final double QUEST_SPAWN_DISTANCE = 10.0;
   private static final int QUEST_SPAWN_ANGLE_ATTEMPTS = 16;

   private QuestService() {
   }

   @Nullable
   public static QuestService.ResolvedQuest resolveQuest(String questKey) {
      if (questKey != null && !questKey.isBlank()) {
         Quest quest = QuestRegistry.getQuest(questKey);
         if (quest == null) {
            return null;
         } else {
            Saga saga = null;
            if (quest.isSagaQuest()) {
               int separator = questKey.lastIndexOf(58);
               if (separator <= 0) {
                  return null;
               }

               saga = QuestRegistry.getSaga(questKey.substring(0, separator));
               if (saga == null) {
                  return null;
               }
            }

            return new QuestService.ResolvedQuest(questKey, quest, saga);
         }
      } else {
         return null;
      }
   }

   @Nullable
   public static Component startQuest(ServerPlayer requester, String questKey) {
      QuestService.ResolvedQuest resolved = resolveQuest(questKey);
      if (resolved == null) {
         return Component.translatable("message.dragonminez.quest.start.unavailable");
      } else {
         ServerPlayer controller = PartyManager.resolveQuestController(requester);
         if (controller == null) {
            return Component.translatable("message.dragonminez.quest.start.unavailable");
         } else {
            StatsData data = StatsProvider.get(StatsCapability.INSTANCE, controller).resolve().orElse(null);
            return (Component)(data == null
               ? Component.translatable("message.dragonminez.quest.start.unavailable")
               : startQuest(requester, controller, resolved, data, data.getPlayerQuestData().getDifficulty()));
         }
      }
   }

   @Nullable
   public static Component resummonQuest(ServerPlayer requester, String questKey) {
      QuestService.ResolvedQuest resolved = resolveQuest(questKey);
      if (resolved == null) {
         return Component.translatable("message.dragonminez.quest.start.unavailable");
      } else {
         ServerPlayer controller = PartyManager.resolveQuestController(requester);
         if (controller == null) {
            return Component.translatable("message.dragonminez.quest.start.unavailable");
         } else {
            StatsData data = StatsProvider.get(StatsCapability.INSTANCE, controller).resolve().orElse(null);
            if (data == null) {
               return Component.translatable("message.dragonminez.quest.start.unavailable");
            } else {
               PlayerQuestData pqd = data.getPlayerQuestData();
               if (pqd.getQuestStatus(questKey) != PlayerQuestData.QuestStatus.ACCEPTED) {
                  return Component.translatable("message.dragonminez.quest.start.unavailable");
               } else {
                  long now = System.currentTimeMillis();
                  Long last = resummonAntiSpam.get(requester.getUUID());
                  if (last != null && now - last < 1500L) {
                     return null;
                  } else {
                     resummonAntiSpam.put(requester.getUUID(), now);
                     int partySize = PartyManager.getAllPartyMembers(controller).size();

                     try {
                        spawnKillObjectives(requester, resolved, pqd, partySize, pqd.getQuestDifficulty(questKey));
                     } catch (Exception var11) {
                        LogUtil.error(
                           Env.SERVER,
                           "Failed to re-summon kill objectives for quest '" + questKey + "' requested by " + requester.getGameProfile().getName(),
                           var11
                        );
                     }

                     return null;
                  }
               }
            }
         }
      }
   }

   @Nullable
   public static Component turnInQuest(ServerPlayer requester, String questKey, @Nullable String npcId) {
      QuestService.ResolvedQuest resolved = resolveQuest(questKey);
      if (resolved != null && npcId != null && !npcId.isBlank()) {
         ServerPlayer controller = PartyManager.resolveQuestController(requester);
         if (controller == null) {
            return Component.translatable("message.dragonminez.quest.start.unavailable");
         } else {
            StatsData data = StatsProvider.get(StatsCapability.INSTANCE, controller).resolve().orElse(null);
            return (Component)(data == null
               ? Component.translatable("message.dragonminez.quest.start.unavailable")
               : turnInQuest(requester, controller, resolved, data, npcId));
         }
      } else {
         return Component.translatable("message.dragonminez.quest.start.unavailable");
      }
   }

   public static void claimRewards(ServerPlayer requester, String questKey) {
      QuestService.ResolvedQuest resolved = resolveQuest(questKey);
      if (resolved != null) {
         if (resolved.quest().getClaimMode() == Quest.ClaimMode.NPC_ONLY) {
            requester.sendSystemMessage(Component.translatable("quest.dmz.reward.npc_only").withStyle(ChatFormatting.RED));
         } else {
            StatsProvider.get(StatsCapability.INSTANCE, requester).ifPresent(data -> {
               PlayerQuestData pqd = data.getPlayerQuestData();
               if (pqd.isQuestCompleted(questKey)) {
                  if (claimAvailableRewards(requester, resolved.quest(), questKey, pqd)) {
                     syncQuestState(requester);
                  }
               }
            });
         }
      }
   }

   public static void claimAllRewards(ServerPlayer requester) {
      StatsProvider.get(StatsCapability.INSTANCE, requester).ifPresent(data -> {
         PlayerQuestData pqd = data.getPlayerQuestData();
         boolean anyClaimed = false;

         for (String questKey : new ArrayList<>(pqd.getCompletedQuestIds())) {
            QuestService.ResolvedQuest resolved = resolveQuest(questKey);
            if (resolved != null && resolved.quest().getClaimMode() != Quest.ClaimMode.NPC_ONLY) {
               anyClaimed |= claimAvailableRewards(requester, resolved.quest(), questKey, pqd);
            }
         }

         if (anyClaimed) {
            syncQuestState(requester);
         }
      });
   }

   public static boolean isTurnInReady(PlayerQuestData pqd, String questKey, Quest quest) {
      if (pqd != null && quest != null && questKey != null && !questKey.isBlank()) {
         for (int i = 0; i < quest.getObjectives().size(); i++) {
            QuestObjective objective = quest.getObjectives().get(i);
            if (objective.getType() != QuestObjective.ObjectiveType.TALK_TO) {
               int progress = pqd.getObjectiveProgress(questKey, i);
               if (progress < quest.getObjectiveRequired(pqd, questKey, i)) {
                  return false;
               }
            }
         }

         return true;
      } else {
         return false;
      }
   }

   public static boolean requiresTurnInAction(Quest quest) {
      return quest != null && quest.getTurnIn() != null && !quest.getTurnIn().isBlank();
   }

   public static QuestService.NPCQuestOptions collectNpcQuestOptions(String npcId, StatsData data) {
      List<String> offerableQuestIds = new ArrayList<>();
      List<String> turnInQuestIds = new ArrayList<>();
      List<String> inProgressQuestIds = new ArrayList<>();
      if (npcId != null && !npcId.isBlank() && data != null) {
         PlayerQuestData pqd = data.getPlayerQuestData();
         Map<String, Quest> allQuests = QuestRegistry.getAllQuests();

         for (String questId : QuestRegistry.getQuestIdsByGiver(npcId)) {
            Quest quest = allQuests.get(questId);
            if (quest != null && !pqd.isQuestCompleted(questId)) {
               if (pqd.isQuestAccepted(questId)) {
                  inProgressQuestIds.add(questId);
               } else if (isOfferableNpcQuest(questId, quest, data)) {
                  offerableQuestIds.add(questId);
               }
            }
         }

         for (String questIdx : QuestRegistry.getQuestIdsByTurnIn(npcId)) {
            Quest quest = allQuests.get(questIdx);
            if (quest != null) {
               if (pqd.isQuestCompleted(questIdx)) {
                  if (hasUnclaimedRewards(pqd, questIdx, quest) && !turnInQuestIds.contains(questIdx)) {
                     turnInQuestIds.add(questIdx);
                  }
               } else if (pqd.isQuestAccepted(questIdx) && isTurnInReady(pqd, questIdx, quest) && !turnInQuestIds.contains(questIdx)) {
                  turnInQuestIds.add(questIdx);
               }
            }
         }

         return new QuestService.NPCQuestOptions(offerableQuestIds, turnInQuestIds, inProgressQuestIds);
      } else {
         return new QuestService.NPCQuestOptions(offerableQuestIds, turnInQuestIds, inProgressQuestIds);
      }
   }

   public static void spawnKillObjectivesForQuest(ServerPlayer requester, String questKey, int partySize, Difficulty difficulty) {
      QuestService.ResolvedQuest resolved = resolveQuest(questKey);
      if (resolved != null) {
         StatsProvider.get(StatsCapability.INSTANCE, requester)
            .ifPresent(data -> spawnKillObjectives(requester, resolved, data.getPlayerQuestData(), partySize, difficulty));
      }
   }

   private static Component startQuest(
      ServerPlayer requester, ServerPlayer controller, QuestService.ResolvedQuest resolved, StatsData data, Difficulty difficulty
   ) {
      PlayerQuestData pqd = data.getPlayerQuestData();
      String questKey = resolved.questKey();
      Quest quest = resolved.quest();
      List<ServerPlayer> partyMembers = PartyManager.getAllPartyMembers(controller);
      PlayerQuestData.QuestStatus questStatus = pqd.getQuestStatus(questKey);
      boolean restartingFailed = questStatus == PlayerQuestData.QuestStatus.FAILED;
      if (pqd.isQuestCompleted(questKey) || questStatus == PlayerQuestData.QuestStatus.ACCEPTED) {
         return Component.translatable("message.dragonminez.quest.start.already_active");
      } else if (!restartingFailed && !isQuestAvailableToStart(resolved, data)) {
         Component reason = QuestAvailabilityChecker.describeAvailabilityFailure(quest, data);
         return (Component)(reason != null ? reason : Component.translatable("message.dragonminez.quest.start.locked"));
      } else {
         Component controllerBlocker = restartingFailed
            ? QuestAvailabilityChecker.describeStartRequirementFailure(quest, questKey, controller, data)
            : QuestAvailabilityChecker.describeQuestStartBlocker(quest, questKey, controller, data);
         if (controllerBlocker != null) {
            return (Component)(requester.getUUID().equals(controller.getUUID())
               ? controllerBlocker
               : Component.translatable(
                  "message.dragonminez.quest.start.party_member_requirement", new Object[]{controller.getGameProfile().getName(), controllerBlocker}
               ));
         } else {
            Component partyBlocker = getPartyRequirementFailure(requester, controller, quest, questKey, restartingFailed);
            if (partyBlocker != null) {
               return partyBlocker;
            } else {
               DMZEvent.QuestStartEvent startEvent = new DMZEvent.QuestStartEvent(controller, questKey, resolved.saga(), quest, partyMembers, difficulty);
               if (((DMZEvent.QuestStartEvent)NeoForge.EVENT_BUS.post(startEvent)).isCanceled()) {
                  return Component.translatable("message.dragonminez.quest.start.unavailable");
               } else {
                  int partySize = partyMembers.size();
                  pqd.acceptQuest(questKey);
                  quest.initializeObjectiveRequirements(pqd, questKey, partySize);
                  pqd.setQuestDifficulty(questKey, startEvent.getDifficulty());
                  pqd.setTrackedQuestId(questKey);

                  try {
                     spawnKillObjectives(requester, resolved, pqd, partySize, startEvent.getDifficulty());
                  } catch (Exception var17) {
                     LogUtil.error(
                        Env.SERVER, "Failed to spawn kill objectives for quest '" + questKey + "' started by " + requester.getGameProfile().getName(), var17
                     );
                  }

                  NetworkHandler.sendToPlayer(StoryToastS2C.questStarted(questKey), controller);
                  if (PartyManager.isInParty(controller)) {
                     PartyManager.syncPartyQuestState(controller);

                     for (ServerPlayer member : partyMembers) {
                        if (!member.getUUID().equals(controller.getUUID())) {
                           NetworkHandler.sendToPlayer(StoryToastS2C.questStarted(questKey), member);
                        }
                     }
                  } else {
                     NetworkHandler.sendToTrackingEntityAndSelf(new ProgressionSyncS2C(controller), controller);
                  }

                  return null;
               }
            }
         }
      }
   }

   private static Component turnInQuest(ServerPlayer requester, ServerPlayer controller, QuestService.ResolvedQuest resolved, StatsData data, String npcId) {
      Quest quest = resolved.quest();
      String questKey = resolved.questKey();
      PlayerQuestData pqd = data.getPlayerQuestData();
      List<ServerPlayer> partyMembers = PartyManager.getAllPartyMembers(controller);
      if (!requiresTurnInAction(quest) || !npcId.equals(quest.getTurnIn())) {
         return Component.translatable("message.dragonminez.quest.start.unavailable");
      } else if (pqd.isQuestCompleted(questKey)) {
         return claimRewardsForRequester(requester, quest, questKey) ? null : Component.translatable("message.dragonminez.quest.start.unavailable");
      } else if (!pqd.isQuestAccepted(questKey)) {
         return Component.translatable("message.dragonminez.quest.start.unavailable");
      } else if (!isTurnInReady(pqd, questKey, quest)) {
         return Component.translatable("message.dragonminez.quest.start.locked");
      } else {
         DMZEvent.QuestTurnInEvent turnInEvent = new DMZEvent.QuestTurnInEvent(controller, questKey, resolved.saga(), quest, partyMembers, npcId);
         if (((DMZEvent.QuestTurnInEvent)NeoForge.EVENT_BUS.post(turnInEvent)).isCanceled()) {
            return Component.translatable("message.dragonminez.quest.start.unavailable");
         } else {
            boolean objectiveProgressChanged = false;

            for (int i = 0; i < quest.getObjectives().size(); i++) {
               QuestObjective objective = quest.getObjectives().get(i);
               if (objective instanceof TalkToObjective) {
                  TalkToObjective talkToObjective = (TalkToObjective)objective;
                  if (npcId.equals(talkToObjective.getNpcId())) {
                     int required = quest.getObjectiveRequired(pqd, questKey, i);
                     objectiveProgressChanged |= updateObjectiveProgressWithEvent(controller, pqd, resolved, quest, partyMembers, i, required);
                  }
               }
            }

            DMZEvent.QuestCompletedEvent completeEvent = new DMZEvent.QuestCompletedEvent(controller, questKey, resolved.saga(), quest, partyMembers);
            if (((DMZEvent.QuestCompletedEvent)NeoForge.EVENT_BUS.post(completeEvent)).isCanceled()) {
               if (objectiveProgressChanged) {
                  syncQuestState(controller);
               }

               return Component.translatable("message.dragonminez.quest.start.unavailable");
            } else {
               pqd.completeQuest(questKey);
               if (questKey.equals(pqd.getTrackedQuestId())) {
                  pqd.setTrackedQuestId(null);
               }

               requester.displayClientMessage(Component.translatable("command.dragonminez.story.sidequest.turned_in", new Object[]{questKey}), false);
               NetworkHandler.sendToPlayer(StoryToastS2C.questComplete(questKey), controller);
               if (PartyManager.isInParty(controller)) {
                  for (ServerPlayer member : partyMembers) {
                     if (!member.getUUID().equals(controller.getUUID())) {
                        NetworkHandler.sendToPlayer(StoryToastS2C.questComplete(questKey), member);
                     }
                  }
               }

               syncQuestState(controller);
               claimRewardsForRequester(requester, quest, questKey);
               return null;
            }
         }
      }
   }

   private static boolean updateObjectiveProgressWithEvent(
      ServerPlayer controller,
      PlayerQuestData pqd,
      QuestService.ResolvedQuest resolved,
      Quest quest,
      List<ServerPlayer> partyMembers,
      int objectiveIndex,
      int newProgress
   ) {
      int current = pqd.getObjectiveProgress(resolved.questKey(), objectiveIndex);
      if (current == newProgress) {
         return false;
      } else {
         int required = objectiveIndex >= 0 && objectiveIndex < quest.getObjectives().size()
            ? quest.getObjectiveRequired(pqd, resolved.questKey(), objectiveIndex)
            : 0;
         DMZEvent.QuestObjectiveProgressEvent progressEvent = new DMZEvent.QuestObjectiveProgressEvent(
            controller, resolved.questKey(), resolved.saga(), quest, partyMembers, objectiveIndex, current, newProgress, required
         );
         if (((DMZEvent.QuestObjectiveProgressEvent)NeoForge.EVENT_BUS.post(progressEvent)).isCanceled()) {
            return false;
         } else {
            int updated = progressEvent.getNewProgress();
            if (updated == current) {
               return false;
            } else {
               pqd.setObjectiveProgress(resolved.questKey(), objectiveIndex, updated);
               return true;
            }
         }
      }
   }

   private static boolean isQuestAvailableToStart(QuestService.ResolvedQuest resolved, StatsData data) {
      if (!resolved.quest().isSagaQuest()) {
         return QuestAvailabilityChecker.isAvailable(resolved.quest(), data);
      } else {
         Saga saga = resolved.saga();
         if (saga == null) {
            return false;
         } else {
            int questIndex = saga.getQuests().indexOf(resolved.quest());
            return questIndex >= 0 && QuestAvailabilityChecker.isSagaQuestAvailable(resolved.quest(), saga, questIndex, data);
         }
      }
   }

   private static boolean hasUnclaimedRewards(PlayerQuestData pqd, String questKey, Quest quest) {
      List<QuestReward> rewards = quest.getRewards();

      for (int i = 0; i < rewards.size(); i++) {
         if (rewards.get(i).isUnlockedFor(pqd.getDifficulty()) && !pqd.isRewardClaimed(questKey, i)) {
            return true;
         }
      }

      return false;
   }

   private static boolean claimRewardsForRequester(ServerPlayer requester, Quest quest, String questKey) {
      StatsData data = StatsProvider.get(StatsCapability.INSTANCE, requester).resolve().orElse(null);
      if (data == null) {
         return false;
      } else {
         PlayerQuestData pqd = data.getPlayerQuestData();
         if (!pqd.isQuestCompleted(questKey)) {
            return false;
         } else if (claimAvailableRewards(requester, quest, questKey, pqd)) {
            syncQuestState(requester);
            return true;
         } else {
            return false;
         }
      }
   }

   private static boolean claimAvailableRewards(ServerPlayer rewardTarget, Quest quest, String questKey, PlayerQuestData pqd) {
      boolean anyClaimed = false;
      List<QuestReward> rewards = quest.getRewards();
      QuestService.ResolvedQuest resolved = resolveQuest(questKey);
      Saga saga = resolved != null ? resolved.saga() : null;
      List<ServerPlayer> partyMembers = PartyManager.getAllPartyMembers(rewardTarget);

      for (int i = 0; i < rewards.size(); i++) {
         if (!pqd.isRewardClaimed(questKey, i) && rewards.get(i).isUnlockedFor(pqd.getDifficulty())) {
            DMZEvent.QuestRewardClaimEvent rewardEvent = new DMZEvent.QuestRewardClaimEvent(rewardTarget, questKey, saga, quest, partyMembers, i);
            if (!((DMZEvent.QuestRewardClaimEvent)NeoForge.EVENT_BUS.post(rewardEvent)).isCanceled()) {
               rewards.get(i).giveReward(rewardTarget, pqd.rewardMultiplierFor(rewards.get(i)));
               pqd.claimReward(questKey, i);
               anyClaimed = true;
            }
         }
      }

      return anyClaimed;
   }

   private static boolean isOfferableNpcQuest(String questId, Quest quest, StatsData data) {
      if (!quest.isSagaQuest()) {
         return QuestAvailabilityChecker.isAvailable(quest, data);
      } else {
         QuestService.ResolvedQuest resolved = resolveQuest(questId);
         if (resolved != null && resolved.saga() != null) {
            int questIndex = resolved.saga().getQuests().indexOf(quest);
            return questIndex >= 0 && QuestAvailabilityChecker.isSagaQuestAvailable(quest, resolved.saga(), questIndex, data);
         } else {
            return false;
         }
      }
   }

   private static void spawnKillObjectives(
      ServerPlayer requester, QuestService.ResolvedQuest resolved, PlayerQuestData pqd, int partySize, Difficulty difficulty
   ) {
      Quest quest = resolved.quest();
      String questKey = resolved.questKey();
      int totalToSpawn = 0;

      for (int i = 0; i < quest.getObjectives().size(); i++) {
         QuestObjective objective = quest.getObjectives().get(i);
         if (objective instanceof KillObjective) {
            KillObjective killObjective = (KillObjective)objective;
            if (killObjective.getSpawnMode() == KillObjective.SpawnMode.QUEST) {
               int currentProgress = pqd.getObjectiveProgress(questKey, i);
               int required = quest.getObjectiveRequired(pqd, questKey, i);
               totalToSpawn += Math.max(0, required - currentProgress);
            }
         }
      }

      String questTeam = totalToSpawn > 1 ? questKey + "@" + requester.getStringUUID() + "@" + System.nanoTime() : null;

      for (int ix = 0; ix < quest.getObjectives().size(); ix++) {
         QuestObjective objective = quest.getObjectives().get(ix);
         if (objective instanceof KillObjective) {
            KillObjective killObjective = (KillObjective)objective;
            if (killObjective.getSpawnMode() == KillObjective.SpawnMode.QUEST) {
               int currentProgress = pqd.getObjectiveProgress(questKey, ix);
               int required = quest.getObjectiveRequired(pqd, questKey, ix);
               int remaining = Math.max(0, required - currentProgress);
               if (remaining > 0) {
                  for (int j = 0; j < remaining; j++) {
                     EntityType<?> entityType = killObjective.resolveEntityType();
                     if (entityType != null) {
                        Entity entity = entityType.create(requester.level());
                        if (entity != null) {
                           positionQuestEntity(requester, entity);
                           if (difficulty != null && difficulty != Difficulty.NORMAL) {
                              entity.getPersistentData().putString("dmz_difficulty", difficulty.name());
                           }

                           if (resolved.saga() != null) {
                              entity.getPersistentData().putString("dmz_saga_id", resolved.saga().getId());
                           }

                           entity.getPersistentData().putString("dmz_quest_key", questKey);
                           entity.getPersistentData().putInt("dmz_quest_objective_index", ix);
                           entity.getPersistentData().putString("dmz_quest_owner", requester.getStringUUID());
                           if (questTeam != null) {
                              entity.getPersistentData().putString("dmz_quest_team", questTeam);
                           }

                           entity.getPersistentData().putDouble("dmz_quest_hp", quest.getScaledKillHealth(killObjective, partySize));
                           entity.getPersistentData().putDouble("dmz_quest_melee", quest.getScaledKillMeleeDamage(killObjective, partySize));
                           entity.getPersistentData().putDouble("dmz_quest_ki", quest.getScaledKillKiDamage(killObjective, partySize));
                           if (killObjective.getTextureVariant() >= 0) {
                              entity.getPersistentData().putInt("dmz_quest_texture_variant", killObjective.getTextureVariant());
                           }

                           int aiTier = killObjective.getAiTier() > 0
                              ? killObjective.getAiTier()
                              : (difficulty != null ? difficulty.aiTierId() : Difficulty.NORMAL.aiTierId());
                           entity.getPersistentData().putInt("dmz_quest_ai_tier", aiTier);
                           if (!killObjective.isCanTransform()) {
                              entity.getPersistentData().putBoolean("dmz_quest_no_transform", true);
                           }

                           Double transformHealth = quest.getScaledTransformHealth(killObjective, partySize);
                           if (transformHealth != null) {
                              entity.getPersistentData().putDouble("dmz_quest_tf_hp_abs", transformHealth);
                           }

                           Double transformMelee = quest.getScaledTransformMeleeDamage(killObjective, partySize);
                           if (transformMelee != null) {
                              entity.getPersistentData().putDouble("dmz_quest_tf_melee_abs", transformMelee);
                           }

                           Double transformKi = quest.getScaledTransformKiDamage(killObjective, partySize);
                           if (transformKi != null) {
                              entity.getPersistentData().putDouble("dmz_quest_tf_ki_abs", transformKi);
                           }

                           if (killObjective.getTransformHealthMultiplier() != null) {
                              entity.getPersistentData().putDouble("dmz_quest_tf_hp_mult", killObjective.getTransformHealthMultiplier());
                           }

                           if (killObjective.getTransformMeleeMultiplier() != null) {
                              entity.getPersistentData().putDouble("dmz_quest_tf_melee_mult", killObjective.getTransformMeleeMultiplier());
                           }

                           if (killObjective.getTransformKiMultiplier() != null) {
                              entity.getPersistentData().putDouble("dmz_quest_tf_ki_mult", killObjective.getTransformKiMultiplier());
                           }

                           if (killObjective.getTransformTriggerPercent() != null) {
                              entity.getPersistentData().putDouble("dmz_quest_tf_trigger", killObjective.getTransformTriggerPercent());
                           }

                           if (entity instanceof Mob mob) {
                              mob.setTarget(requester);
                           }

                           requester.serverLevel().addFreshEntity(entity);
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private static void positionQuestEntity(ServerPlayer requester, Entity entity) {
      ServerLevel level = requester.serverLevel();
      RandomSource random = requester.getRandom();

      for (int attempt = 0; attempt < 16; attempt++) {
         double angle = random.nextDouble() * Math.PI * 2.0;
         double dist = 10.0 + (random.nextDouble() - 0.5) * 2.0;
         double targetX = requester.getX() + Math.cos(angle) * dist;
         double targetZ = requester.getZ() + Math.sin(angle) * dist;
         Double safeY = findSafeSpawnY(level, entity, targetX, targetZ, requester.getY());
         if (safeY != null) {
            entity.setPos(targetX, safeY, targetZ);
            return;
         }
      }

      entity.setPos(requester.getX(), requester.getY(), requester.getZ());
   }

   @Nullable
   private static Double findSafeSpawnY(ServerLevel level, Entity entity, double x, double z, double baseY) {
      int startY = Mth.floor(baseY) + 4;
      int minY = Mth.floor(baseY) - 8;

      for (int y = startY; y >= minY; y--) {
         entity.setPos(x, (double)y, z);
         if (level.noCollision(entity, entity.getBoundingBox())) {
            BlockPos ground = BlockPos.containing(x, (double)y, z).below();
            BlockState groundState = level.getBlockState(ground);
            if (!groundState.getCollisionShape(level, ground).isEmpty()) {
               return (double)y;
            }
         }
      }

      return null;
   }

   @Nullable
   private static Component getPartyRequirementFailure(ServerPlayer requester, ServerPlayer controller, Quest quest, String questKey, boolean restartingFailed) {
      if (!PartyManager.isInParty(controller)) {
         return null;
      } else {
         for (ServerPlayer member : PartyManager.getAllPartyMembers(controller)) {
            if (!member.getUUID().equals(controller.getUUID())) {
               StatsData memberData = StatsProvider.get(StatsCapability.INSTANCE, member).resolve().orElse(null);
               Component blocker = (Component)(memberData == null
                  ? Component.translatable("message.dragonminez.quest.start.unavailable")
                  : (
                     restartingFailed
                        ? QuestAvailabilityChecker.describeNonPositionalStartRequirementFailure(quest, questKey, member, memberData)
                        : QuestAvailabilityChecker.describeNonPositionalStartBlocker(quest, questKey, member, memberData)
                  ));
               if (blocker != null) {
                  if (member.getUUID().equals(requester.getUUID())) {
                     return blocker;
                  }

                  return Component.translatable(
                     "message.dragonminez.quest.start.party_member_requirement", new Object[]{member.getGameProfile().getName(), blocker}
                  );
               }
            }
         }

         return null;
      }
   }

   public static void syncQuestState(ServerPlayer controller) {
      if (PartyManager.isInParty(controller)) {
         PartyManager.syncPartyQuestState(controller);
      } else {
         NetworkHandler.sendToTrackingEntityAndSelf(new ProgressionSyncS2C(controller), controller);
      }
   }

   public static record NPCQuestOptions(List<String> offerableQuestIds, List<String> turnInQuestIds, List<String> inProgressQuestIds) {
   }

   public static record ResolvedQuest(String questKey, Quest quest, @Nullable Saga saga) {
   }
}
