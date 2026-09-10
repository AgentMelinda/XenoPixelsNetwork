package com.dragonminez.common.quest;

import lombok.Generated;

public abstract class QuestObjective {
   private final QuestObjective.ObjectiveType type;
   private final int required;
   private int progress;
   private boolean completed;

   public QuestObjective(QuestObjective.ObjectiveType type, int required) {
      this.type = type;
      this.required = required;
      this.progress = 0;
      this.completed = false;
   }

   public void setProgress(int progress) {
      this.progress = Math.min(progress, this.required);
      this.checkCompletion();
   }

   public void addProgress(int amount) {
      this.progress = Math.min(this.progress + amount, this.required);
      this.checkCompletion();
   }

   private void checkCompletion() {
      if (this.progress >= this.required) {
         this.completed = true;
      }
   }

   public abstract boolean checkProgress(Object... var1);

   @Generated
   public QuestObjective.ObjectiveType getType() {
      return this.type;
   }

   @Generated
   public int getRequired() {
      return this.required;
   }

   @Generated
   public int getProgress() {
      return this.progress;
   }

   @Generated
   public boolean isCompleted() {
      return this.completed;
   }

   @Generated
   public void setCompleted(boolean completed) {
      this.completed = completed;
   }

   public static enum ObjectiveType {
      ITEM,
      KILL,
      INTERACT,
      STRUCTURE,
      BIOME,
      DIMENSION,
      COORDS,
      TALK_TO,
      DRAGON_SUMMON,
      SKILL;
   }
}
