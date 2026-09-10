package com.dragonminez.common.init.entities.sagas;

import com.dragonminez.common.init.entities.IBattlePower;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;

public class SagaRaditzEntity extends DBSagasEntity {
   public SagaRaditzEntity(EntityType<? extends Monster> pEntityType, Level pLevel) {
      super(pEntityType, pLevel);
      if (this instanceof IBattlePower bp) {
         bp.setBattlePower(1200);
      }

      this.setKiBlastSpeed(1.2F);
      this.setFlySpeed(0.35);
      this.setAuraColor(16711935);
      this.setEvade(true, 60);
      this.setDBZStyle(0);
      this.setCanFly(true);
      this.addKiSkill(DBSagasEntity.KiSkillType.DOUBLE_SUNDAY, 200, 1.0F, 13774837, 9244584);
   }
}
