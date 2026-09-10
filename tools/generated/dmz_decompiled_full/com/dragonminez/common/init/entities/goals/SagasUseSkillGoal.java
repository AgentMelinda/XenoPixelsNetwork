package com.dragonminez.common.init.entities.goals;

import com.dragonminez.common.init.entities.sagas.DBSagasEntity;
import java.util.EnumSet;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.Goal.Flag;

public class SagasUseSkillGoal extends Goal {
   private final DBSagasEntity entity;

   public SagasUseSkillGoal(DBSagasEntity entity) {
      this.entity = entity;
      this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
   }

   public boolean canUse() {
      if (this.entity.getAiTier() != DBSagasEntity.AiTier.SIMPLE) {
         return false;
      } else {
         return this.entity.getTarget() != null
               && !this.entity.isCasting()
               && !this.entity.isComboing()
               && !this.entity.isEvading()
               && !this.entity.isStunned()
            ? this.entity.hasSkillReady()
            : false;
      }
   }

   public boolean canContinueToUse() {
      return this.entity.isCasting();
   }

   public void start() {
      this.entity.startFirstAvailableSkill();
   }
}
