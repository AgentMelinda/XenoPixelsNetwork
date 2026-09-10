package com.dragonminez.common.init.entities.sagas;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier.Builder;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;

public class SagaFriezaSoldier02Entity extends DBSagasEntity {
   public SagaFriezaSoldier02Entity(EntityType<? extends Monster> pEntityType, Level pLevel) {
      super(pEntityType, pLevel);
      this.setCanFly(true);
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
