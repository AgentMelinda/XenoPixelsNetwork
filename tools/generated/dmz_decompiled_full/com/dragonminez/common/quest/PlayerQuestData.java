package com.dragonminez.common.quest;

import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.quest.rewards.TPSReward;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import lombok.Generated;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;

public class PlayerQuestData {
   private final Map<String, PlayerQuestData.QuestProgress> quests = new LinkedHashMap<>();
   private final Map<String, Boolean> sagaUnlockState = new HashMap<>();
   private final Map<String, PlayerQuestData.QuestStartRequirementTiming> startRequirementTimings = new LinkedHashMap<>();
   private final Set<String> hostileNpcKeys = new LinkedHashSet<>();
   private String trackedQuestId = null;
   private Difficulty difficulty = Difficulty.NORMAL;
   private boolean difficultyChosen = false;
   private int storyResetCount = 0;
   private UUID activePartyId = null;
   private UUID partyLeaderId = null;
   private final List<UUID> partyMemberIds = new ArrayList<>();
   private boolean partyPvpEnabled = false;
   private PlayerQuestData.PartyInviteData pendingPartyInvite = null;

   public void acceptQuest(String questId) {
      this.getOrCreateProgress(questId).setStatus(PlayerQuestData.QuestStatus.ACCEPTED);
      this.clearStartRequirementTiming(questId);
   }

   public void failQuest(String questId) {
      PlayerQuestData.QuestProgress progress = this.getOrCreateProgress(questId);
      progress.markFailed();
      progress.resetForRestart();
      progress.setStatus(PlayerQuestData.QuestStatus.FAILED);
      this.clearStartRequirementTiming(questId);
   }

   public void restartFailedQuest(String questId) {
      PlayerQuestData.QuestProgress progress = this.getOrCreateProgress(questId);
      progress.resetForRestart();
      progress.setStatus(PlayerQuestData.QuestStatus.ACCEPTED);
      this.clearStartRequirementTiming(questId);
   }

   public void completeQuest(String questId) {
      this.getOrCreateProgress(questId).setStatus(PlayerQuestData.QuestStatus.SUCCESS);
      this.clearStartRequirementTiming(questId);
   }

   public boolean isQuestAccepted(String questId) {
      PlayerQuestData.QuestProgress progress = this.quests.get(questId);
      return progress != null && progress.getStatus() == PlayerQuestData.QuestStatus.ACCEPTED;
   }

   public boolean isQuestCompleted(String questId) {
      PlayerQuestData.QuestProgress progress = this.quests.get(questId);
      return progress != null && progress.getStatus() == PlayerQuestData.QuestStatus.SUCCESS;
   }

   public PlayerQuestData.QuestStatus getQuestStatus(String questId) {
      PlayerQuestData.QuestProgress progress = this.quests.get(questId);
      return progress != null ? progress.getStatus() : PlayerQuestData.QuestStatus.NOT_STARTED;
   }

   public Set<String> getFailedQuestIds() {
      Set<String> failed = new LinkedHashSet<>();

      for (Entry<String, PlayerQuestData.QuestProgress> entry : this.quests.entrySet()) {
         if (entry.getValue().getStatus() == PlayerQuestData.QuestStatus.FAILED) {
            failed.add(entry.getKey());
         }
      }

      return failed;
   }

   public void resetQuest(String questId) {
      this.quests.remove(questId);
      this.clearStartRequirementTiming(questId);
   }

   public void setDifficulty(Difficulty newDifficulty) {
      if (newDifficulty != null && newDifficulty != this.difficulty) {
         this.difficulty = newDifficulty;
      }
   }

   public void requestDifficultyReselect() {
      this.difficultyChosen = false;
   }

   public void resetAll() {
      this.clearActiveQuestState();
   }

   public void markStoryReset() {
      this.storyResetCount++;
   }

   public void clearStoryResets() {
      this.storyResetCount = 0;
   }

   public double tpRewardMultiplier() {
      if (this.storyResetCount <= 0) {
         return 1.0;
      } else {
         double perReset = ConfigManager.getServerConfig().getGameplay().getStoryResetTPMultiplier();
         return Math.pow(perReset, (double)this.storyResetCount);
      }
   }

   public double rewardMultiplierFor(QuestReward reward) {
      double multiplier = this.difficulty.questRewardMultiplier();
      return reward instanceof TPSReward ? multiplier * this.tpRewardMultiplier() : multiplier;
   }

   private void clearActiveQuestState() {
      this.quests.clear();
      this.sagaUnlockState.clear();
      this.startRequirementTimings.clear();
      this.hostileNpcKeys.clear();
      this.trackedQuestId = null;
   }

   public void resetSaga(String sagaId) {
      String prefix = sagaId + ":";
      this.quests.keySet().removeIf(key -> key.startsWith(prefix));
      this.startRequirementTimings.keySet().removeIf(key -> key.startsWith(prefix));
      if (this.trackedQuestId != null && this.trackedQuestId.startsWith(prefix)) {
         this.trackedQuestId = null;
      }
   }

   public void setTrackedQuestId(String trackedQuestId) {
      if (trackedQuestId != null && !trackedQuestId.isBlank()) {
         this.trackedQuestId = trackedQuestId;
      } else {
         this.trackedQuestId = null;
      }
   }

   public PlayerQuestData.QuestStartRequirementTiming getStartRequirementTiming(String questId) {
      return questId == null ? null : this.startRequirementTimings.get(questId);
   }

   public boolean ensureStartRequirementTiming(String questId, long gameTimeStarted, long realTimeStartedMs) {
      if (questId == null || questId.isBlank()) {
         return false;
      } else if (this.startRequirementTimings.containsKey(questId)) {
         return false;
      } else {
         this.startRequirementTimings.put(questId, new PlayerQuestData.QuestStartRequirementTiming(gameTimeStarted, realTimeStartedMs));
         return true;
      }
   }

   public void clearStartRequirementTiming(String questId) {
      if (questId != null && !questId.isBlank()) {
         this.startRequirementTimings.remove(questId);
      }
   }

   public void markNpcHostile(String npcKey) {
      if (npcKey != null && !npcKey.isBlank()) {
         this.hostileNpcKeys.add(npcKey);
      }
   }

   public boolean isNpcHostile(String npcKey) {
      return npcKey != null && this.hostileNpcKeys.contains(npcKey);
   }

   public void clearNpcHostility(String npcKey) {
      if (npcKey != null && !npcKey.isBlank()) {
         this.hostileNpcKeys.remove(npcKey);
      }
   }

   public Set<String> getAcceptedQuestIds() {
      Set<String> accepted = new LinkedHashSet<>();

      for (Entry<String, PlayerQuestData.QuestProgress> entry : this.quests.entrySet()) {
         if (entry.getValue().getStatus() == PlayerQuestData.QuestStatus.ACCEPTED) {
            accepted.add(entry.getKey());
         }
      }

      return accepted;
   }

   public Set<String> getCompletedQuestIds() {
      Set<String> completed = new LinkedHashSet<>();

      for (Entry<String, PlayerQuestData.QuestProgress> entry : this.quests.entrySet()) {
         if (entry.getValue().getStatus() == PlayerQuestData.QuestStatus.SUCCESS) {
            completed.add(entry.getKey());
         }
      }

      return completed;
   }

   public void setObjectiveProgress(String questId, int objectiveIndex, int progress) {
      this.getOrCreateProgress(questId).setObjectiveProgress(objectiveIndex, progress);
   }

   public void setObjectiveRequired(String questId, int objectiveIndex, int required) {
      this.getOrCreateProgress(questId).setObjectiveRequired(objectiveIndex, required);
   }

   public int getObjectiveRequired(String questId, int objectiveIndex, int fallbackRequired) {
      PlayerQuestData.QuestProgress progress = this.quests.get(questId);
      return progress != null ? progress.getObjectiveRequired(objectiveIndex, fallbackRequired) : fallbackRequired;
   }

   public void setQuestDifficulty(String questId, Difficulty difficulty) {
      this.getOrCreateProgress(questId).setDifficulty(difficulty != null ? difficulty : Difficulty.NORMAL);
   }

   public Difficulty getQuestDifficulty(String questId) {
      PlayerQuestData.QuestProgress progress = this.quests.get(questId);
      return progress != null ? progress.getDifficulty() : Difficulty.NORMAL;
   }

   public int getQuestFailureCount(String questId) {
      PlayerQuestData.QuestProgress progress = this.quests.get(questId);
      return progress != null ? progress.getFailureCount() : 0;
   }

   public int getObjectiveProgress(String questId, int objectiveIndex) {
      PlayerQuestData.QuestProgress progress = this.quests.get(questId);
      return progress != null ? progress.getObjectiveProgress(objectiveIndex) : 0;
   }

   public void claimReward(String questId, int rewardIndex) {
      this.getOrCreateProgress(questId).claimReward(rewardIndex);
   }

   public boolean isRewardClaimed(String questId, int rewardIndex) {
      PlayerQuestData.QuestProgress progress = this.quests.get(questId);
      return progress != null && progress.isRewardClaimed(rewardIndex);
   }

   public void setSagaUnlocked(String sagaId, boolean unlocked) {
      this.sagaUnlockState.put(sagaId, unlocked);
   }

   public boolean isSagaLocked(String sagaId) {
      return this.sagaUnlockState.getOrDefault(sagaId, false);
   }

   public static String sagaQuestKey(String sagaId, int questId) {
      return sagaId + ":" + questId;
   }

   public List<UUID> getPartyMemberIds() {
      return Collections.unmodifiableList(this.partyMemberIds);
   }

   public boolean isInParty() {
      return this.activePartyId != null;
   }

   public boolean isPartyLeader(UUID playerId) {
      return playerId != null && playerId.equals(this.partyLeaderId);
   }

   public void setPartyState(UUID partyId, UUID leaderId, Collection<UUID> members, boolean pvpEnabled) {
      this.activePartyId = partyId;
      this.partyLeaderId = leaderId;
      this.partyPvpEnabled = pvpEnabled;
      this.partyMemberIds.clear();
      if (leaderId != null) {
         this.partyMemberIds.add(leaderId);
      }

      if (members != null) {
         for (UUID memberId : members) {
            if (memberId != null && !this.partyMemberIds.contains(memberId)) {
               this.partyMemberIds.add(memberId);
            }
         }
      }
   }

   public void clearPartyState() {
      this.activePartyId = null;
      this.partyLeaderId = null;
      this.partyPvpEnabled = false;
      this.partyMemberIds.clear();
   }

   public PlayerQuestData.PartyInviteData getPendingPartyInviteData() {
      return this.pendingPartyInvite;
   }

   public boolean hasPendingPartyInvite() {
      return this.pendingPartyInvite != null;
   }

   public void clearPendingPartyInvite() {
      this.pendingPartyInvite = null;
   }

   public void mergeQuestStateFrom(PlayerQuestData other) {
      if (other != null) {
         this.difficulty = other.difficulty;

         for (PlayerQuestData.QuestProgress otherProgress : other.quests.values()) {
            PlayerQuestData.QuestProgress own = this.quests.get(otherProgress.getQuestId());
            if (own == null) {
               own = new PlayerQuestData.QuestProgress(otherProgress.getQuestId());
               this.quests.put(own.getQuestId(), own);
            }

            own.mergeForwardFrom(otherProgress);
         }

         for (Entry<String, Boolean> entry : other.sagaUnlockState.entrySet()) {
            if (entry.getValue()) {
               this.sagaUnlockState.put(entry.getKey(), true);
            } else {
               this.sagaUnlockState.putIfAbsent(entry.getKey(), false);
            }
         }

         if (this.trackedQuestId == null && other.trackedQuestId != null) {
            this.trackedQuestId = other.trackedQuestId;
         }
      }
   }

   private PlayerQuestData.QuestProgress getOrCreateProgress(String questId) {
      return this.quests.computeIfAbsent(questId, PlayerQuestData.QuestProgress::new);
   }

   private CompoundTag serializeCoreQuestState() {
      CompoundTag tag = new CompoundTag();
      ListTag questList = new ListTag();

      for (PlayerQuestData.QuestProgress progress : this.quests.values()) {
         questList.add(progress.serializeNBT());
      }

      tag.put("quests", questList);
      CompoundTag sagaUnlocks = new CompoundTag();

      for (Entry<String, Boolean> entry : this.sagaUnlockState.entrySet()) {
         sagaUnlocks.putBoolean(entry.getKey(), entry.getValue());
      }

      tag.put("sagaUnlocks", sagaUnlocks);
      if (!this.startRequirementTimings.isEmpty()) {
         CompoundTag timingTag = new CompoundTag();

         for (Entry<String, PlayerQuestData.QuestStartRequirementTiming> entry : this.startRequirementTimings.entrySet()) {
            timingTag.put(entry.getKey(), entry.getValue().serializeNBT());
         }

         tag.put("startRequirementTimings", timingTag);
      }

      if (this.trackedQuestId != null && !this.trackedQuestId.isBlank()) {
         tag.putString("trackedQuestId", this.trackedQuestId);
      }

      if (!this.hostileNpcKeys.isEmpty()) {
         ListTag hostileNpcs = new ListTag();

         for (String npcKey : this.hostileNpcKeys) {
            hostileNpcs.add(StringTag.valueOf(npcKey));
         }

         tag.put("hostileNpcKeys", hostileNpcs);
      }

      return tag;
   }

   private void deserializeCoreQuestState(CompoundTag tag) {
      this.clearActiveQuestState();
      ListTag questList = tag.getList("quests", 10);

      for (int i = 0; i < questList.size(); i++) {
         CompoundTag questTag = questList.getCompound(i);
         PlayerQuestData.QuestProgress progress = PlayerQuestData.QuestProgress.deserialize(questTag);
         this.quests.put(progress.getQuestId(), progress);
      }

      if (tag.contains("sagaUnlocks")) {
         CompoundTag sagaUnlocks = tag.getCompound("sagaUnlocks");

         for (String key : sagaUnlocks.getAllKeys()) {
            this.sagaUnlockState.put(key, sagaUnlocks.getBoolean(key));
         }
      }

      if (tag.contains("startRequirementTimings", 10)) {
         CompoundTag timingTag = tag.getCompound("startRequirementTimings");

         for (String key : timingTag.getAllKeys()) {
            if (timingTag.contains(key, 10)) {
               this.startRequirementTimings.put(key, PlayerQuestData.QuestStartRequirementTiming.deserialize(timingTag.getCompound(key)));
            }
         }
      }

      if (tag.contains("trackedQuestId", 8)) {
         String tracked = tag.getString("trackedQuestId");
         if (!tracked.isBlank()) {
            this.trackedQuestId = tracked;
         }
      }

      if (tag.contains("hostileNpcKeys", 9)) {
         ListTag hostileNpcs = tag.getList("hostileNpcKeys", 8);

         for (int i = 0; i < hostileNpcs.size(); i++) {
            String npcKey = hostileNpcs.getString(i);
            if (!npcKey.isBlank()) {
               this.hostileNpcKeys.add(npcKey);
            }
         }
      }
   }

   private CompoundTag serializeFullQuestState() {
      CompoundTag tag = new CompoundTag();
      tag.putString("difficulty", this.difficulty.name());
      tag.put("questState", this.serializeCoreQuestState());
      return tag;
   }

   private void deserializeFullQuestState(CompoundTag tag) {
      this.clearActiveQuestState();
      if (tag.contains("questState", 10)) {
         this.difficulty = Difficulty.fromName(tag.getString("difficulty"));
         this.deserializeCoreQuestState(tag.getCompound("questState"));
      } else if (tag.contains("difficultyStates", 10)) {
         this.difficulty = Difficulty.fromName(tag.getString("difficulty"));
         CompoundTag states = tag.getCompound("difficultyStates");
         if (states.contains(this.difficulty.name(), 10)) {
            this.deserializeCoreQuestState(states.getCompound(this.difficulty.name()));
         }
      } else {
         this.difficulty = tag.getBoolean("hardModeEnabled") ? Difficulty.HARD : Difficulty.NORMAL;
         this.deserializeCoreQuestState(tag);
      }
   }

   private static UUID parseUuid(String raw) {
      if (raw != null && !raw.isBlank()) {
         try {
            return UUID.fromString(raw);
         } catch (IllegalArgumentException var2) {
            return null;
         }
      } else {
         return null;
      }
   }

   public CompoundTag serializeNBT() {
      CompoundTag tag = this.serializeFullQuestState();
      tag.putBoolean("difficultyChosen", this.difficultyChosen);
      tag.putInt("storyResetCount", this.storyResetCount);
      CompoundTag partyTag = new CompoundTag();
      if (this.activePartyId != null) {
         partyTag.putString("partyId", this.activePartyId.toString());
      }

      if (this.partyLeaderId != null) {
         partyTag.putString("leaderId", this.partyLeaderId.toString());
      }

      if (this.partyPvpEnabled) {
         partyTag.putBoolean("pvpEnabled", true);
      }

      if (!this.partyMemberIds.isEmpty()) {
         ListTag membersTag = new ListTag();

         for (UUID memberId : this.partyMemberIds) {
            membersTag.add(StringTag.valueOf(memberId.toString()));
         }

         partyTag.put("members", membersTag);
      }

      if (this.pendingPartyInvite != null) {
         partyTag.put("pendingInvite", this.pendingPartyInvite.serializeNBT());
      }

      if (!partyTag.isEmpty()) {
         tag.put("partyState", partyTag);
      }

      return tag;
   }

   public void deserializeNBT(CompoundTag tag) {
      this.deserializeFullQuestState(tag);
      this.difficultyChosen = tag.getBoolean("difficultyChosen");
      this.storyResetCount = Math.max(0, tag.getInt("storyResetCount"));
      this.activePartyId = null;
      this.partyLeaderId = null;
      this.partyPvpEnabled = false;
      this.partyMemberIds.clear();
      this.pendingPartyInvite = null;
      if (tag.contains("partyState", 10)) {
         CompoundTag partyTag = tag.getCompound("partyState");
         if (partyTag.contains("partyId", 8)) {
            this.activePartyId = parseUuid(partyTag.getString("partyId"));
         }

         if (partyTag.contains("leaderId", 8)) {
            this.partyLeaderId = parseUuid(partyTag.getString("leaderId"));
         }

         this.partyPvpEnabled = partyTag.getBoolean("pvpEnabled");
         if (partyTag.contains("members", 9)) {
            ListTag memberList = partyTag.getList("members", 8);

            for (int i = 0; i < memberList.size(); i++) {
               UUID memberId = parseUuid(memberList.getString(i));
               if (memberId != null && !this.partyMemberIds.contains(memberId)) {
                  this.partyMemberIds.add(memberId);
               }
            }
         }

         if (this.partyLeaderId != null && !this.partyMemberIds.contains(this.partyLeaderId)) {
            this.partyMemberIds.add(0, this.partyLeaderId);
         }

         if (partyTag.contains("pendingInvite", 10)) {
            this.pendingPartyInvite = PlayerQuestData.PartyInviteData.deserialize(partyTag.getCompound("pendingInvite"));
         }
      }
   }

   @Generated
   public String getTrackedQuestId() {
      return this.trackedQuestId;
   }

   @Generated
   public Difficulty getDifficulty() {
      return this.difficulty;
   }

   @Generated
   public boolean isDifficultyChosen() {
      return this.difficultyChosen;
   }

   @Generated
   public void setDifficultyChosen(boolean difficultyChosen) {
      this.difficultyChosen = difficultyChosen;
   }

   @Generated
   public int getStoryResetCount() {
      return this.storyResetCount;
   }

   @Generated
   public UUID getActivePartyId() {
      return this.activePartyId;
   }

   @Generated
   public UUID getPartyLeaderId() {
      return this.partyLeaderId;
   }

   @Generated
   public boolean isPartyPvpEnabled() {
      return this.partyPvpEnabled;
   }

   @Generated
   public void setPendingPartyInvite(PlayerQuestData.PartyInviteData pendingPartyInvite) {
      this.pendingPartyInvite = pendingPartyInvite;
   }

   public static class PartyInviteData {
      private final UUID inviterUUID;
      private final UUID partyId;
      private final UUID partyLeaderId;
      private final String inviterName;
      private final long expiresAtMs;
      private final Difficulty partyDifficulty;

      public PartyInviteData(UUID inviterUUID, UUID partyId, UUID partyLeaderId, String inviterName, long expiresAtMs, Difficulty partyDifficulty) {
         this.inviterUUID = inviterUUID;
         this.partyId = partyId;
         this.partyLeaderId = partyLeaderId;
         this.inviterName = inviterName == null ? "" : inviterName;
         this.expiresAtMs = expiresAtMs;
         this.partyDifficulty = partyDifficulty == null ? Difficulty.NORMAL : partyDifficulty;
      }

      public boolean isExpired() {
         return System.currentTimeMillis() > this.expiresAtMs;
      }

      public CompoundTag serializeNBT() {
         CompoundTag tag = new CompoundTag();
         if (this.inviterUUID != null) {
            tag.putString("inviterUUID", this.inviterUUID.toString());
         }

         if (this.partyId != null) {
            tag.putString("partyId", this.partyId.toString());
         }

         if (this.partyLeaderId != null) {
            tag.putString("partyLeaderId", this.partyLeaderId.toString());
         }

         if (!this.inviterName.isBlank()) {
            tag.putString("inviterName", this.inviterName);
         }

         tag.putLong("expiresAtMs", this.expiresAtMs);
         tag.putString("partyDifficulty", this.partyDifficulty.name());
         return tag;
      }

      public static PlayerQuestData.PartyInviteData deserialize(CompoundTag tag) {
         return new PlayerQuestData.PartyInviteData(
            PlayerQuestData.parseUuid(tag.getString("inviterUUID")),
            PlayerQuestData.parseUuid(tag.getString("partyId")),
            PlayerQuestData.parseUuid(tag.getString("partyLeaderId")),
            tag.getString("inviterName"),
            tag.getLong("expiresAtMs"),
            Difficulty.fromName(tag.getString("partyDifficulty"))
         );
      }

      @Generated
      public UUID getInviterUUID() {
         return this.inviterUUID;
      }

      @Generated
      public UUID getPartyId() {
         return this.partyId;
      }

      @Generated
      public UUID getPartyLeaderId() {
         return this.partyLeaderId;
      }

      @Generated
      public String getInviterName() {
         return this.inviterName;
      }

      @Generated
      public long getExpiresAtMs() {
         return this.expiresAtMs;
      }

      @Generated
      public Difficulty getPartyDifficulty() {
         return this.partyDifficulty;
      }
   }

   public static class QuestProgress {
      private final String questId;
      private PlayerQuestData.QuestStatus status;
      private final Map<Integer, Integer> objectiveProgress = new HashMap<>();
      private final Map<Integer, Integer> objectiveRequired = new HashMap<>();
      private final Map<Integer, Boolean> rewardsClaimed = new HashMap<>();
      private int failureCount = 0;
      private Difficulty difficulty = Difficulty.NORMAL;

      public QuestProgress(String questId) {
         this.questId = questId;
         this.status = PlayerQuestData.QuestStatus.NOT_STARTED;
      }

      public void setObjectiveProgress(int index, int progress) {
         this.objectiveProgress.put(index, progress);
      }

      public int getObjectiveProgress(int index) {
         return this.objectiveProgress.getOrDefault(index, 0);
      }

      public void setObjectiveRequired(int index, int required) {
         this.objectiveRequired.put(index, required);
      }

      public int getObjectiveRequired(int index, int fallbackRequired) {
         return this.objectiveRequired.getOrDefault(index, fallbackRequired);
      }

      public void claimReward(int index) {
         this.rewardsClaimed.put(index, true);
      }

      public boolean isRewardClaimed(int index) {
         return this.rewardsClaimed.getOrDefault(index, false);
      }

      public Map<Integer, Boolean> copyRewardClaims() {
         return new HashMap<>(this.rewardsClaimed);
      }

      public void clearRewardClaims() {
         this.rewardsClaimed.clear();
      }

      public void restoreRewardClaims(Map<Integer, Boolean> claims) {
         if (claims != null) {
            this.rewardsClaimed.putAll(claims);
         }
      }

      public void mergeForwardFrom(PlayerQuestData.QuestProgress other) {
         if (other != null) {
            if (statusRank(other.status) > statusRank(this.status)) {
               this.status = other.status;
               this.difficulty = other.difficulty;
            }

            for (Entry<Integer, Integer> entry : other.objectiveProgress.entrySet()) {
               int current = this.objectiveProgress.getOrDefault(entry.getKey(), 0);
               if (entry.getValue() > current) {
                  this.objectiveProgress.put(entry.getKey(), entry.getValue());
               }
            }

            for (Entry<Integer, Integer> entryx : other.objectiveRequired.entrySet()) {
               this.objectiveRequired.putIfAbsent(entryx.getKey(), entryx.getValue());
            }
         }
      }

      private static int statusRank(PlayerQuestData.QuestStatus status) {
         return switch (status) {
            case NOT_STARTED -> 0;
            case ACCEPTED -> 2;
            case FAILED -> 1;
            case SUCCESS -> 3;
         };
      }

      public void markFailed() {
         this.failureCount++;
      }

      public void resetForRestart() {
         this.objectiveProgress.clear();
         this.rewardsClaimed.clear();
      }

      public CompoundTag serializeNBT() {
         CompoundTag tag = new CompoundTag();
         tag.putString("questId", this.questId);
         tag.putString("status", this.status.name());
         CompoundTag objectivesTag = new CompoundTag();

         for (Entry<Integer, Integer> entry : this.objectiveProgress.entrySet()) {
            objectivesTag.putInt(String.valueOf(entry.getKey()), entry.getValue());
         }

         tag.put("objectives", objectivesTag);
         CompoundTag objectiveRequirementsTag = new CompoundTag();

         for (Entry<Integer, Integer> entry : this.objectiveRequired.entrySet()) {
            objectiveRequirementsTag.putInt(String.valueOf(entry.getKey()), entry.getValue());
         }

         tag.put("objectiveRequirements", objectiveRequirementsTag);
         tag.putInt("failureCount", this.failureCount);
         tag.putString("difficulty", this.difficulty.name());
         CompoundTag rewardsTag = new CompoundTag();

         for (Entry<Integer, Boolean> entry : this.rewardsClaimed.entrySet()) {
            rewardsTag.putBoolean(String.valueOf(entry.getKey()), entry.getValue());
         }

         tag.put("rewards", rewardsTag);
         return tag;
      }

      public static PlayerQuestData.QuestProgress deserialize(CompoundTag tag) {
         String questId = tag.getString("questId");
         PlayerQuestData.QuestProgress progress = new PlayerQuestData.QuestProgress(questId);

         try {
            progress.status = PlayerQuestData.QuestStatus.valueOf(tag.getString("status"));
         } catch (IllegalArgumentException var8) {
            progress.status = PlayerQuestData.QuestStatus.NOT_STARTED;
         }

         CompoundTag objectivesTag = tag.getCompound("objectives");

         for (String key : objectivesTag.getAllKeys()) {
            progress.objectiveProgress.put(Integer.parseInt(key), objectivesTag.getInt(key));
         }

         CompoundTag objectiveRequirementsTag = tag.getCompound("objectiveRequirements");

         for (String key : objectiveRequirementsTag.getAllKeys()) {
            progress.objectiveRequired.put(Integer.parseInt(key), objectiveRequirementsTag.getInt(key));
         }

         if (tag.contains("failureCount", 3)) {
            progress.failureCount = tag.getInt("failureCount");
         }

         if (tag.contains("difficulty", 8)) {
            progress.difficulty = Difficulty.fromName(tag.getString("difficulty"));
         } else if (tag.contains("hardMode", 1)) {
            progress.difficulty = tag.getBoolean("hardMode") ? Difficulty.HARD : Difficulty.NORMAL;
         }

         CompoundTag rewardsTag = tag.getCompound("rewards");

         for (String key : rewardsTag.getAllKeys()) {
            progress.rewardsClaimed.put(Integer.parseInt(key), rewardsTag.getBoolean(key));
         }

         return progress;
      }

      @Generated
      public String getQuestId() {
         return this.questId;
      }

      @Generated
      public void setStatus(PlayerQuestData.QuestStatus status) {
         this.status = status;
      }

      @Generated
      public PlayerQuestData.QuestStatus getStatus() {
         return this.status;
      }

      @Generated
      public int getFailureCount() {
         return this.failureCount;
      }

      @Generated
      public Difficulty getDifficulty() {
         return this.difficulty;
      }

      @Generated
      public void setDifficulty(Difficulty difficulty) {
         this.difficulty = difficulty;
      }
   }

   public static class QuestStartRequirementTiming {
      private final long gameTimeStarted;
      private final long realTimeStartedMs;

      public QuestStartRequirementTiming(long gameTimeStarted, long realTimeStartedMs) {
         this.gameTimeStarted = gameTimeStarted;
         this.realTimeStartedMs = realTimeStartedMs;
      }

      public CompoundTag serializeNBT() {
         CompoundTag tag = new CompoundTag();
         tag.putLong("gameTimeStarted", this.gameTimeStarted);
         tag.putLong("realTimeStartedMs", this.realTimeStartedMs);
         return tag;
      }

      public static PlayerQuestData.QuestStartRequirementTiming deserialize(CompoundTag tag) {
         return new PlayerQuestData.QuestStartRequirementTiming(tag.getLong("gameTimeStarted"), tag.getLong("realTimeStartedMs"));
      }

      @Generated
      public long getGameTimeStarted() {
         return this.gameTimeStarted;
      }

      @Generated
      public long getRealTimeStartedMs() {
         return this.realTimeStartedMs;
      }
   }

   public static enum QuestStatus {
      NOT_STARTED,
      ACCEPTED,
      FAILED,
      SUCCESS;
   }
}
