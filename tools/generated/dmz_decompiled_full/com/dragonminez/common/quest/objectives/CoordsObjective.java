package com.dragonminez.common.quest.objectives;

import com.dragonminez.common.quest.QuestObjective;
import lombok.Generated;
import net.minecraft.core.BlockPos;

public class CoordsObjective extends QuestObjective {
   private final BlockPos targetPos;
   private final int radius;

   public CoordsObjective(BlockPos targetPos, int radius) {
      super(QuestObjective.ObjectiveType.COORDS, 1);
      this.targetPos = targetPos;
      this.radius = radius;
   }

   @Override
   public boolean checkProgress(Object... params) {
      if (params.length > 0 && params[0] instanceof BlockPos playerPos) {
         double distance = Math.sqrt(playerPos.distSqr(this.targetPos));
         if (distance <= (double)this.radius) {
            this.setProgress(1);
            return true;
         }
      }

      return false;
   }

   @Generated
   public BlockPos getTargetPos() {
      return this.targetPos;
   }

   @Generated
   public int getRadius() {
      return this.radius;
   }
}
