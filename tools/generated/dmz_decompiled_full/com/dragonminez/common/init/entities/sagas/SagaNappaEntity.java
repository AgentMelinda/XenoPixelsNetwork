package com.dragonminez.common.init.entities.sagas;

import com.dragonminez.common.init.entities.IBattlePower;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;

public class SagaNappaEntity extends DBSagasEntity {
   public SagaNappaEntity(EntityType<? extends Monster> pEntityType, Level pLevel) {
      super(pEntityType, pLevel);
      if (this instanceof IBattlePower bp) {
         bp.setBattlePower(4000);
      }

      this.setCanFly(true);
      this.setWildSense(true, 200);
      this.setKiBlastSpeed(1.3F);
      this.addKiSkill(DBSagasEntity.KiSkillType.OOZARU_BEAM, 200, 1.5F, 12919774, 11477982);
      this.setAuraColor(16066477);
      this.setDBZStyle(1);
      this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.2);
      this.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0.2);
   }
}
