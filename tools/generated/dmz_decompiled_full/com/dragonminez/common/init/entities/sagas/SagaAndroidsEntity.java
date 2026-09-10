package com.dragonminez.common.init.entities.sagas;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;

public class SagaAndroidsEntity {
   public static class SagaA16Entity extends DBSagasEntity {
      public SagaA16Entity(EntityType<? extends Monster> pEntityType, Level pLevel) {
         super(pEntityType, pLevel);
         this.setCanFly(true);
         this.setAuraColor(16774282);
         this.setKiBlastSpeed(1.4F);
         this.setDBZStyle(0);
         this.setEvade(true, 60);
         this.setWildSense(true, 100);
         this.setScaleVal(1.2F);
         this.setAllowedCombos(120, new DBSagasEntity.ComboType[]{DBSagasEntity.ComboType.BASIC});
         this.addKiSkill(DBSagasEntity.KiSkillType.GENERIC_KI_WAVE, 400, 3.0F, 16775280, 16775280);
         this.addKiSkill(DBSagasEntity.KiSkillType.KI_VOLLEY, 120, 2.3F, 16775280, 16775280);
      }
   }

   public static class SagaA17Entity extends DBSagasEntity {
      public SagaA17Entity(EntityType<? extends Monster> pEntityType, Level pLevel) {
         super(pEntityType, pLevel);
         this.setCanFly(true);
         this.setAuraColor(16774282);
         this.setKiBlastSpeed(1.4F);
         this.setDBZStyle(0);
         this.setEvade(true, 60);
         this.setWildSense(true, 100);
         this.setAllowedCombos(120, new DBSagasEntity.ComboType[]{DBSagasEntity.ComboType.AIR});
         this.addKiSkill(DBSagasEntity.KiSkillType.KIENZAN, 400, 5.0F, 4904036, 4904036);
         this.addKiSkill(DBSagasEntity.KiSkillType.KI_BARRIER, 200, 2.3F, 4904036, 3322734);
      }
   }

   public static class SagaA18Entity extends DBSagasEntity {
      public SagaA18Entity(EntityType<? extends Monster> pEntityType, Level pLevel) {
         super(pEntityType, pLevel);
         this.setCanFly(true);
         this.setAuraColor(16774282);
         this.setKiBlastSpeed(1.4F);
         this.setDBZStyle(0);
         this.setEvade(true, 60);
         this.setWildSense(true, 100);
         this.setAllowedCombos(120, new DBSagasEntity.ComboType[]{DBSagasEntity.ComboType.BASIC});
         this.addKiSkill(DBSagasEntity.KiSkillType.KIENZAN, 400, 3.0F, 16740598, 16740598);
         this.addKiSkill(DBSagasEntity.KiSkillType.KI_VOLLEY, 200, 2.3F, 16740598, 16740598);
      }
   }

   public static class SagaA19Entity extends DBSagasEntity {
      public SagaA19Entity(EntityType<? extends Monster> pEntityType, Level pLevel) {
         super(pEntityType, pLevel);
         this.setCanFly(true);
         this.setAuraColor(16774282);
         this.setKiBlastSpeed(1.4F);
         this.setDBZStyle(0);
         this.setEvade(true, 60);
         this.setWildSense(true, 100);
         this.setAllowedCombos(120, new DBSagasEntity.ComboType[]{DBSagasEntity.ComboType.ANDROID_ABSORPTION});
         this.addKiSkill(DBSagasEntity.KiSkillType.KI_SMALL, 50, 1.2F, 16774282, 16774282);
         this.addKiSkill(DBSagasEntity.KiSkillType.KI_LASER, 50, 1.2F, 16066343, 16066343);
      }
   }

   public static class SagaDrGeroEntity extends DBSagasEntity {
      public SagaDrGeroEntity(EntityType<? extends Monster> pEntityType, Level pLevel) {
         super(pEntityType, pLevel);
         this.setCanFly(true);
         this.setAuraColor(16774282);
         this.setKiBlastSpeed(1.4F);
         this.setDBZStyle(0);
         this.setEvade(true, 60);
         this.setWildSense(true, 100);
         this.setAllowedCombos(120, new DBSagasEntity.ComboType[]{DBSagasEntity.ComboType.ANDROID_ABSORPTION});
         this.addKiSkill(DBSagasEntity.KiSkillType.KI_SMALL, 50, 1.2F, 16774282, 16774282);
         this.addKiSkill(DBSagasEntity.KiSkillType.KI_LASER, 50, 1.2F, 16066343, 16066343);
      }
   }
}
