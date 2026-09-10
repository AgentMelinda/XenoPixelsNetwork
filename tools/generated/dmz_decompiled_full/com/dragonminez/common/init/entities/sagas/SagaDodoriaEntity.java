package com.dragonminez.common.init.entities.sagas;

import com.dragonminez.common.init.entities.IBattlePower;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;

public class SagaDodoriaEntity extends DBSagasEntity {
   public SagaDodoriaEntity(EntityType<? extends Monster> pEntityType, Level pLevel) {
      super(pEntityType, pLevel);
      if (this instanceof IBattlePower bp) {
         bp.setBattlePower(22000);
      }

      this.setCanFly(true);
      this.setKiBlastSpeed(1.3F);
      this.addKiSkill(DBSagasEntity.KiSkillType.OOZARU_BEAM, 200, 1.1F, 16776168, 16776168);
      this.setAuraColor(16732898);
      this.setDBZStyle(2);
   }
}
