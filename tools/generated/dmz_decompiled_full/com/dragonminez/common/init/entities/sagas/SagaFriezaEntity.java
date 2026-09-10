package com.dragonminez.common.init.entities.sagas;

import com.dragonminez.common.init.MainEntities;
import com.dragonminez.common.init.entities.IBattlePower;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;

public class SagaFriezaEntity {
   public static class SagaFriezaFPForm extends DBSagasEntity {
      public SagaFriezaFPForm(EntityType<? extends Monster> pEntityType, Level pLevel) {
         super(pEntityType, pLevel);
         if (this instanceof IBattlePower bp) {
            bp.setBattlePower(120000000);
         }

         this.setCanFly(true);
         this.setDBZStyle(2);
         this.setAuraColor(16711935);
         this.setKiBlastSpeed(1.4F);
         this.setAllowedCombos(100, new DBSagasEntity.ComboType[]{DBSagasEntity.ComboType.AIR});
         this.addKiSkill(DBSagasEntity.KiSkillType.DEATH_BALL, 250, 1.1F, 10888158, 10888158);
         this.addKiSkill(DBSagasEntity.KiSkillType.KIENZAN, 100, 1.6F, 14558169, 14558169);
         this.setWildSense(true, 100);
      }
   }

   public static class SagaFriezaFinalForm extends DBSagasEntity {
      public SagaFriezaFinalForm(EntityType<? extends Monster> pEntityType, Level pLevel) {
         super(pEntityType, pLevel);
         if (this instanceof IBattlePower bp) {
            bp.setBattlePower(120000000);
         }

         this.setCanFly(true);
         this.setDBZStyle(0);
         this.setAuraColor(11141375);
         this.setKiBlastSpeed(1.4F);
         this.addKiSkill(DBSagasEntity.KiSkillType.DEATH_BALL, 250, 1.1F, 11141375, 7078051);
         this.addKiSkill(DBSagasEntity.KiSkillType.KI_LASER, 60, 1.0F, 11141375, 7078051);
         this.setAllowedCombos(100, new DBSagasEntity.ComboType[]{DBSagasEntity.ComboType.AIR, DBSagasEntity.ComboType.BASIC});
         this.setWildSense(true, 100);
      }
   }

   public static class SagaFriezaFirstForm extends DBSagasEntity {
      public SagaFriezaFirstForm(EntityType<? extends Monster> pEntityType, Level pLevel) {
         super(pEntityType, pLevel);
         if (this instanceof IBattlePower bp) {
            bp.setBattlePower(530000);
         }

         this.setCanFly(true);
         this.setDBZStyle(0);
         this.setAuraColor(9903848);
         this.setKiBlastSpeed(1.4F);
         this.setScaleVal(0.8F);
         this.addKiSkill(DBSagasEntity.KiSkillType.KI_SMALL, 200, 1.0F, 11141375, 7078051);
         this.addKiSkill(DBSagasEntity.KiSkillType.KI_LASER, 60, 1.0F, 11141375, 7078051);
         this.setWildSense(true, 200);
      }
   }

   public static class SagaFriezaSecondForm extends DBSagasEntity {
      public SagaFriezaSecondForm(EntityType<? extends Monster> pEntityType, Level pLevel) {
         super(pEntityType, pLevel);
         if (this instanceof IBattlePower bp) {
            bp.setBattlePower(1000000);
         }

         this.setKiBlastSpeed(1.4F);
         this.setCanFly(true);
         this.setDBZStyle(2);
         this.setAuraColor(9903848);
         this.setScaleVal(1.2F);
         this.addKiSkill(DBSagasEntity.KiSkillType.KI_LASER, 60, 1.0F, 11141375, 7078051);
         this.addKiSkill(DBSagasEntity.KiSkillType.GENERIC_KI_WAVE, 200, 1.1F, 12919774, 12919774);
         this.setAllowedCombos(150, new DBSagasEntity.ComboType[]{DBSagasEntity.ComboType.AIR});
         this.setWildSense(true, 180);
      }

      @Override
      protected boolean hasTransformation() {
         return true;
      }

      @Override
      public EntityType<? extends DBSagasEntity> getNextTransform() {
         return (EntityType<? extends DBSagasEntity>)MainEntities.SAGA_FREEZER_THIRD.get();
      }

      @Override
      protected boolean spawnsNewFormFullHealth() {
         return false;
      }
   }

   public static class SagaFriezaThirdForm extends DBSagasEntity {
      public SagaFriezaThirdForm(EntityType<? extends Monster> pEntityType, Level pLevel) {
         super(pEntityType, pLevel);
         if (this instanceof IBattlePower bp) {
            bp.setBattlePower(2100000);
         }

         this.setCanFly(true);
         this.setDBZStyle(0);
         this.setAuraColor(9903848);
         this.setKiBlastSpeed(1.4F);
         this.setScaleVal(1.3F);
         this.addKiSkill(DBSagasEntity.KiSkillType.TRIPLE_LASER, 200, 1.1F, 12919774, 12919774);
         this.addKiSkill(DBSagasEntity.KiSkillType.KI_VOLLEY, 200, 1.1F, 12919774, 12919774);
         this.setWildSense(true, 160);
      }
   }

   public static class SagaKingCold extends DBSagasEntity {
      public SagaKingCold(EntityType<? extends Monster> pEntityType, Level pLevel) {
         super(pEntityType, pLevel);
         if (this instanceof IBattlePower bp) {
            bp.setBattlePower(80000000);
         }

         this.setCanFly(true);
         this.setDBZStyle(0);
         this.setAuraColor(12521231);
         this.setKiBlastSpeed(1.4F);
         this.setScaleVal(1.2F);
         this.addKiSkill(DBSagasEntity.KiSkillType.TRIPLE_LASER, 200, 1.0F, 14558015, 14558015);
         this.addKiSkill(DBSagasEntity.KiSkillType.KI_LASER, 60, 1.0F, 10888158, 10888158);
         this.setAllowedCombos(100, new DBSagasEntity.ComboType[]{DBSagasEntity.ComboType.AIR});
         this.setWildSense(true, 100);
      }
   }

   public static class SagaMechaFrieza extends DBSagasEntity {
      public SagaMechaFrieza(EntityType<? extends Monster> pEntityType, Level pLevel) {
         super(pEntityType, pLevel);
         if (this instanceof IBattlePower bp) {
            bp.setBattlePower(120000000);
         }

         this.setCanFly(true);
         this.setDBZStyle(0);
         this.setAuraColor(12521231);
         this.setKiBlastSpeed(1.4F);
         this.addKiSkill(DBSagasEntity.KiSkillType.DEATH_BALL, 250, 1.1F, 15408670, 10360856);
         this.addKiSkill(DBSagasEntity.KiSkillType.KI_LASER, 60, 1.0F, 15408670, 10360856);
         this.setAllowedCombos(100, new DBSagasEntity.ComboType[]{DBSagasEntity.ComboType.AIR});
         this.setWildSense(true, 100);
      }

      @Override
      public String getGeckolibModelName() {
         return "saga_frieza_base";
      }
   }
}
