package com.dragonminez.common.quest.objectives;

import com.dragonminez.common.quest.QuestObjective;
import lombok.Generated;

public class TalkToObjective extends QuestObjective {
   private final String npcId;

   public TalkToObjective(String npcId) {
      super(QuestObjective.ObjectiveType.TALK_TO, 1);
      this.npcId = npcId;
   }

   @Override
   public boolean checkProgress(Object... params) {
      if (params.length > 0 && params[0] instanceof String interactedNpcId && this.npcId != null && this.npcId.equals(interactedNpcId)) {
         this.setProgress(1);
         return true;
      } else {
         return false;
      }
   }

   @Generated
   public String getNpcId() {
      return this.npcId;
   }
}
