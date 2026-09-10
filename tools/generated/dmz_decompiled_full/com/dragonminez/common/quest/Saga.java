package com.dragonminez.common.quest;

import java.util.ArrayList;
import java.util.List;
import lombok.Generated;

public class Saga {
   private final String id;
   private final String name;
   private final List<Quest> quests;
   private final Saga.SagaRequirements requirements;
   private boolean unlocked;
   private int currentQuestIndex;

   public Saga(String id, String name, List<Quest> quests, Saga.SagaRequirements requirements) {
      this.id = id;
      this.name = name;
      this.quests = (List<Quest>)(quests != null ? quests : new ArrayList<>());
      this.requirements = requirements;
      this.unlocked = false;
      this.currentQuestIndex = 0;
   }

   public Quest getCurrentQuest() {
      return this.currentQuestIndex >= 0 && this.currentQuestIndex < this.quests.size() ? this.quests.get(this.currentQuestIndex) : null;
   }

   public boolean advanceQuest() {
      if (this.currentQuestIndex < this.quests.size() - 1) {
         this.currentQuestIndex++;
         return true;
      } else {
         return false;
      }
   }

   public boolean isCompleted() {
      return this.currentQuestIndex >= this.quests.size() - 1 && (this.getCurrentQuest() == null || this.getCurrentQuest().isCompleted());
   }

   public Quest getQuestById(int questId) {
      return this.quests.stream().filter(q -> q.getId() == questId).findFirst().orElse(null);
   }

   @Generated
   public void setUnlocked(boolean unlocked) {
      this.unlocked = unlocked;
   }

   @Generated
   public void setCurrentQuestIndex(int currentQuestIndex) {
      this.currentQuestIndex = currentQuestIndex;
   }

   @Generated
   public String getId() {
      return this.id;
   }

   @Generated
   public String getName() {
      return this.name;
   }

   @Generated
   public List<Quest> getQuests() {
      return this.quests;
   }

   @Generated
   public Saga.SagaRequirements getRequirements() {
      return this.requirements;
   }

   @Generated
   public boolean isUnlocked() {
      return this.unlocked;
   }

   @Generated
   public int getCurrentQuestIndex() {
      return this.currentQuestIndex;
   }

   public static record SagaRequirements(String previousSagaId) {
   }
}
