package com.dragonminez.common.quest;

import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.quest.objectives.ItemObjective;
import com.dragonminez.common.quest.objectives.KillObjective;
import java.util.ArrayList;
import java.util.List;
import lombok.Generated;

public class Quest {
   private final int id;
   private final String stringId;
   private final Quest.QuestType type;
   private final String title;
   private final String description;
   private final List<QuestObjective> objectives;
   private final List<QuestReward> rewards;
   private boolean completed;
   private int currentObjectiveIndex;
   private final String category;
   private final boolean parallelObjectives;
   private final boolean partyScaling;
   private final String questGiver;
   private final String turnIn;
   private final QuestPrerequisites prerequisites;
   private final QuestPrerequisites startRequirements;
   private final boolean secret;
   private final Quest.ClaimMode claimMode;

   public Quest(
      int id,
      String stringId,
      Quest.QuestType type,
      String title,
      String description,
      String category,
      boolean parallelObjectives,
      boolean partyScaling,
      List<QuestObjective> objectives,
      List<QuestReward> rewards,
      QuestPrerequisites prerequisites,
      QuestPrerequisites startRequirements,
      String questGiver,
      String turnIn
   ) {
      this(
         id,
         stringId,
         type,
         title,
         description,
         category,
         parallelObjectives,
         partyScaling,
         objectives,
         rewards,
         prerequisites,
         startRequirements,
         questGiver,
         turnIn,
         false,
         Quest.ClaimMode.TREE_OR_NPC
      );
   }

   public Quest(
      int id,
      String stringId,
      Quest.QuestType type,
      String title,
      String description,
      String category,
      boolean parallelObjectives,
      boolean partyScaling,
      List<QuestObjective> objectives,
      List<QuestReward> rewards,
      QuestPrerequisites prerequisites,
      QuestPrerequisites startRequirements,
      String questGiver,
      String turnIn,
      boolean secret,
      Quest.ClaimMode claimMode
   ) {
      this.id = id;
      this.stringId = stringId;
      this.type = type != null ? type : Quest.QuestType.SAGA;
      this.title = title;
      this.description = description;
      this.objectives = (List<QuestObjective>)(objectives != null ? objectives : new ArrayList<>());
      this.rewards = (List<QuestReward>)(rewards != null ? rewards : new ArrayList<>());
      this.completed = false;
      this.currentObjectiveIndex = 0;
      this.category = category != null ? category : "general";
      this.parallelObjectives = parallelObjectives;
      this.partyScaling = partyScaling;
      this.questGiver = questGiver;
      this.turnIn = turnIn;
      this.prerequisites = prerequisites;
      this.startRequirements = startRequirements;
      this.secret = secret;
      this.claimMode = claimMode != null ? claimMode : Quest.ClaimMode.TREE_OR_NPC;
   }

   public boolean hasPrerequisites() {
      return this.prerequisites != null && !this.prerequisites.conditions().isEmpty();
   }

   public boolean hasStartRequirements() {
      return this.startRequirements != null && !this.startRequirements.conditions().isEmpty();
   }

   public boolean isSideQuest() {
      return this.type == Quest.QuestType.SIDEQUEST;
   }

   public boolean isSagaQuest() {
      return this.type == Quest.QuestType.SAGA;
   }

   public String getEffectiveId() {
      return this.stringId != null ? this.stringId : String.valueOf(this.id);
   }

   public int getObjectiveRequired(PlayerQuestData pqd, String questId, int objectiveIndex) {
      if (objectiveIndex >= 0 && objectiveIndex < this.objectives.size()) {
         QuestObjective objective = this.objectives.get(objectiveIndex);
         return pqd == null ? objective.getRequired() : pqd.getObjectiveRequired(questId, objectiveIndex, objective.getRequired());
      } else {
         return 0;
      }
   }

   public void initializeObjectiveRequirements(PlayerQuestData pqd, String questId, int partySize) {
      if (pqd != null && questId != null && !questId.isBlank()) {
         int safePartySize = Math.max(1, partySize);

         for (int i = 0; i < this.objectives.size(); i++) {
            pqd.setObjectiveRequired(questId, i, this.getScaledObjectiveRequired(this.objectives.get(i), safePartySize));
         }
      }
   }

   public double getScaledKillHealth(KillObjective objective, int partySize) {
      return objective.getHealth() * this.enemyPartyMultiplier(partySize, ConfigManager.getServerConfig().getGameplay().getEnemyHealthPerPartyPlayer());
   }

   public double getScaledKillMeleeDamage(KillObjective objective, int partySize) {
      return objective.getMeleeDamage() * this.enemyPartyMultiplier(partySize, ConfigManager.getServerConfig().getGameplay().getEnemyDamagePerPartyPlayer());
   }

   public double getScaledKillKiDamage(KillObjective objective, int partySize) {
      return objective.getKiDamage() * this.enemyPartyMultiplier(partySize, ConfigManager.getServerConfig().getGameplay().getEnemyDamagePerPartyPlayer());
   }

   public Double getScaledTransformHealth(KillObjective objective, int partySize) {
      Double base = objective.getTransformHealth();
      return base == null ? null : base * this.enemyPartyMultiplier(partySize, ConfigManager.getServerConfig().getGameplay().getEnemyHealthPerPartyPlayer());
   }

   public Double getScaledTransformMeleeDamage(KillObjective objective, int partySize) {
      Double base = objective.getTransformMeleeDamage();
      return base == null ? null : base * this.enemyPartyMultiplier(partySize, ConfigManager.getServerConfig().getGameplay().getEnemyDamagePerPartyPlayer());
   }

   public Double getScaledTransformKiDamage(KillObjective objective, int partySize) {
      Double base = objective.getTransformKiDamage();
      return base == null ? null : base * this.enemyPartyMultiplier(partySize, ConfigManager.getServerConfig().getGameplay().getEnemyDamagePerPartyPlayer());
   }

   private int getScaledObjectiveRequired(QuestObjective objective, int partySize) {
      int baseRequired = objective.getRequired();
      if (this.partyScaling && partySize > 1 && baseRequired > 0) {
         int extraMembers = partySize - 1;
         double configuredMultiplier = ConfigManager.getServerConfig().getGameplay().getDefaultQuestPartyMultiplier();
         if (objective instanceof ItemObjective) {
            double scaled = (double)baseRequired * Math.pow(configuredMultiplier, (double)extraMembers);
            return roundUpCount(Math.max((double)baseRequired, scaled), resolveItemCountStep(baseRequired));
         } else if (!(objective instanceof KillObjective)) {
            return baseRequired;
         } else if (baseRequired <= 1) {
            return baseRequired;
         } else {
            double scaled = (double)baseRequired * Math.pow(configuredMultiplier, (double)extraMembers * 0.75);
            return Math.max(baseRequired, (int)Math.ceil(scaled));
         }
      } else {
         return baseRequired;
      }
   }

   private double enemyPartyMultiplier(int partySize, double perPlayerMultiplier) {
      if (!this.partyScaling) {
         return 1.0;
      } else {
         int extraMembers = Math.max(1, partySize) - 1;
         return 1.0 + (double)extraMembers * (perPlayerMultiplier - 1.0);
      }
   }

   private static int resolveItemCountStep(int baseRequired) {
      if (baseRequired >= 96) {
         return 10;
      } else if (baseRequired >= 24) {
         return 5;
      } else {
         return baseRequired >= 8 ? 2 : 1;
      }
   }

   private static int roundUpCount(double value, int step) {
      return step <= 1 ? (int)Math.ceil(value) : (int)(Math.ceil(value / (double)step) * (double)step);
   }

   @Generated
   public int getId() {
      return this.id;
   }

   @Generated
   public String getStringId() {
      return this.stringId;
   }

   @Generated
   public Quest.QuestType getType() {
      return this.type;
   }

   @Generated
   public String getTitle() {
      return this.title;
   }

   @Generated
   public String getDescription() {
      return this.description;
   }

   @Generated
   public List<QuestObjective> getObjectives() {
      return this.objectives;
   }

   @Generated
   public List<QuestReward> getRewards() {
      return this.rewards;
   }

   @Generated
   public boolean isCompleted() {
      return this.completed;
   }

   @Generated
   public int getCurrentObjectiveIndex() {
      return this.currentObjectiveIndex;
   }

   @Generated
   public String getCategory() {
      return this.category;
   }

   @Generated
   public boolean isParallelObjectives() {
      return this.parallelObjectives;
   }

   @Generated
   public boolean isPartyScaling() {
      return this.partyScaling;
   }

   @Generated
   public String getQuestGiver() {
      return this.questGiver;
   }

   @Generated
   public String getTurnIn() {
      return this.turnIn;
   }

   @Generated
   public QuestPrerequisites getPrerequisites() {
      return this.prerequisites;
   }

   @Generated
   public QuestPrerequisites getStartRequirements() {
      return this.startRequirements;
   }

   @Generated
   public boolean isSecret() {
      return this.secret;
   }

   @Generated
   public Quest.ClaimMode getClaimMode() {
      return this.claimMode;
   }

   @Generated
   public void setCompleted(boolean completed) {
      this.completed = completed;
   }

   @Generated
   public void setCurrentObjectiveIndex(int currentObjectiveIndex) {
      this.currentObjectiveIndex = currentObjectiveIndex;
   }

   public static enum ClaimMode {
      TREE_OR_NPC,
      NPC_ONLY;
   }

   public static enum QuestType {
      SAGA,
      SIDEQUEST,
      DAILY,
      EVENT;
   }
}
