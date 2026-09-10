package com.dragonminez.common.quest.objectives;

import com.dragonminez.common.quest.QuestObjective;
import lombok.Generated;
import net.minecraft.world.level.Level;

public class DimensionObjective extends QuestObjective {
   private final String dimensionId;

   public DimensionObjective(String dimensionId) {
      super(QuestObjective.ObjectiveType.DIMENSION, 1);
      this.dimensionId = dimensionId;
   }

   @Override
   public boolean checkProgress(Object... params) {
      if (params.length > 0 && params[0] instanceof Level level && level.dimension().location().toString().equals(this.dimensionId)) {
         this.setProgress(1);
         return true;
      } else {
         return false;
      }
   }

   @Generated
   public String getDimensionId() {
      return this.dimensionId;
   }
}
