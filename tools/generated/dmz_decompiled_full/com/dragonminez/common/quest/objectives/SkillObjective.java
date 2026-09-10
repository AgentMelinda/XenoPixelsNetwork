package com.dragonminez.common.quest.objectives;

import com.dragonminez.common.quest.QuestObjective;
import lombok.Generated;

public class SkillObjective extends QuestObjective {
   private final String skill;
   private final int level;

   public SkillObjective(String skill, int level) {
      super(QuestObjective.ObjectiveType.SKILL, Math.max(1, level));
      this.skill = skill;
      this.level = Math.max(1, level);
   }

   @Override
   public boolean checkProgress(Object... params) {
      if (params.length > 0 && params[0] instanceof Number currentLevel) {
         this.setProgress(Math.max(0, currentLevel.intValue()));
         return this.isCompleted();
      } else {
         return false;
      }
   }

   @Generated
   public String getSkill() {
      return this.skill;
   }

   @Generated
   public int getLevel() {
      return this.level;
   }
}
