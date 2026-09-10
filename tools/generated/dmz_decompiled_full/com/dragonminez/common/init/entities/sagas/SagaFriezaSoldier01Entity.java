package com.dragonminez.common.init.entities.sagas;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier.Builder;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;

public class SagaFriezaSoldier01Entity extends DBSagasEntity {
   public SagaFriezaSoldier01Entity(EntityType<? extends Monster> pEntityType, Level pLevel) {
      super(pEntityType, pLevel);
      this.setCanFly(true);
      this.addKiSkill(DBSagasEntity.KiSkillType.KI_SMALL, 100, 1.0F, 11141375, 7078051);
   }

   public static Builder createAttributes() {
      return Monster.createMonsterAttributes()
         .add(Attributes.MAX_HEALTH, 100.0)
         .add(Attributes.MOVEMENT_SPEED, 0.2)
         .add(Attributes.ATTACK_DAMAGE, 15.0)
         .add(Attributes.FOLLOW_RANGE, 12.0)
         .add(Attributes.KNOCKBACK_RESISTANCE, 0.6);
   }
}
