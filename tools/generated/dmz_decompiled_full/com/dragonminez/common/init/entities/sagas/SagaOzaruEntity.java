package com.dragonminez.common.init.entities.sagas;

import com.dragonminez.common.init.entities.IBattlePower;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier.Builder;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;

public class SagaOzaruEntity extends DBSagasEntity {
   public SagaOzaruEntity(EntityType<? extends Monster> pEntityType, Level pLevel) {
      super(pEntityType, pLevel);
      this.setCanFly(false);
      if (this instanceof IBattlePower bp) {
         bp.setBattlePower(180000);
      }

      this.setScaleVal(1.8F);
      this.setKiBlastSpeed(1.5F);
      this.setDBZStyle(4);
      this.addKiSkill(DBSagasEntity.KiSkillType.OOZARU_BEAM, 250, 1.5F, 12919774, 11477982);
      this.addKiSkill(DBSagasEntity.KiSkillType.OOZARU_ROAR, 500, 15.5F);
      this.getAttribute(Attributes.ATTACK_SPEED).setBaseValue(0.25);
   }

   public static Builder createAttributes() {
      return Monster.createMonsterAttributes()
         .add(Attributes.MAX_HEALTH, 300.0)
         .add(Attributes.MOVEMENT_SPEED, 0.18)
         .add(Attributes.ATTACK_DAMAGE, 15.0)
         .add(Attributes.ATTACK_SPEED, 4.5)
         .add(Attributes.KNOCKBACK_RESISTANCE, 0.6)
         .add(Attributes.FOLLOW_RANGE, 64.0);
   }

   @Override
   protected void registerGoals() {
      super.registerGoals();
      this.goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.8, false));
   }

   @Override
   public String getGeckolibModelName() {
      return "saga_ozaru";
   }

   @Override
   public boolean hasHitboxParts() {
      return true;
   }

   @Override
   protected EntityDimensions getCoreDimensions() {
      return EntityDimensions.scalable(3.0F, 4.5F);
   }

   @Override
   protected DBSagasPart[] createHitboxParts() {
      return new DBSagasPart[]{
         new DBSagasPart(this, "legs", 5.0F, 4.0F, 0.0F, 0.0F, 2.0F),
         new DBSagasPart(this, "torso", 6.0F, 4.0F, 0.0F, 0.0F, 6.0F),
         new DBSagasPart(this, "head", 4.5F, 3.5F, 0.5F, 0.0F, 9.0F)
      };
   }
}
