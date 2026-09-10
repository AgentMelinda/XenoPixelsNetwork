package com.dragonminez.common.init.entities.goals;

import com.dragonminez.common.init.entities.namek.NamekWarriorEntity;
import java.util.EnumSet;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal.Flag;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.AABB;

public class NamekDefendVillageGoal extends TargetGoal {
   private final NamekWarriorEntity warrior;
   private LivingEntity villageAggressor;
   private final TargetingConditions attackStrategy = TargetingConditions.forCombat().range(64.0);

   public NamekDefendVillageGoal(NamekWarriorEntity pWarrior) {
      super(pWarrior, false, true);
      this.warrior = pWarrior;
      this.setFlags(EnumSet.of(Flag.TARGET));
   }

   public boolean canUse() {
      AABB aabb = this.warrior.getBoundingBox().inflate(16.0, 8.0, 16.0);

      for (Villager villager : this.warrior.level().getEntitiesOfClass(Villager.class, aabb)) {
         LivingEntity lastHurtBy = villager.getLastHurtByMob();
         if (lastHurtBy != null && this.warrior.canAttack(lastHurtBy, this.attackStrategy)) {
            this.villageAggressor = lastHurtBy;
            return true;
         }
      }

      return false;
   }

   public void start() {
      this.warrior.setTarget(this.villageAggressor);
      super.start();
   }
}
