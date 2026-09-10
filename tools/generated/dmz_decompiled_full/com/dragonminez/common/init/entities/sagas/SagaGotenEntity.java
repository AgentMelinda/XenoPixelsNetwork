package com.dragonminez.common.init.entities.sagas;

import com.dragonminez.common.init.MainEntities;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;

public class SagaGotenEntity {
   public static class SagaGotenKidEntity extends DBSagasEntity {
      public SagaGotenKidEntity(EntityType<? extends Monster> pEntityType, Level pLevel) {
         super(pEntityType, pLevel);
         this.setCanFly(true);
         this.setAuraColor(16777215);
         this.setKiBlastSpeed(1.6F);
         this.setDBZStyle(0);
         this.setEvade(true, 100);
         this.setAllowedCombos(200, new DBSagasEntity.ComboType[]{DBSagasEntity.ComboType.AIR});
         this.setisKid(true);
         this.addKiSkill(DBSagasEntity.KiSkillType.KAMEHAMEHA, 200, 0.8F);
      }

      @Override
      public String getGeckolibModelName() {
         return "saga_goten";
      }

      @Override
      protected boolean hasTransformation() {
         return true;
      }

      @Override
      public EntityType<? extends DBSagasEntity> getNextTransform() {
         return (EntityType<? extends DBSagasEntity>)MainEntities.SAGA_GOTEN_SSJ.get();
      }

      @Override
      protected boolean spawnsNewFormFullHealth() {
         return true;
      }
   }

   public static class SagaGotenKidSSJEntity extends DBSagasEntity {
      public SagaGotenKidSSJEntity(EntityType<? extends Monster> pEntityType, Level pLevel) {
         super(pEntityType, pLevel);
         this.setCanFly(true);
         this.setAuraColor(16770647);
         this.setKiBlastSpeed(1.4F);
         this.setDBZStyle(0);
         this.setEvade(true, 60);
         this.setWildSense(true, 100);
         this.setisKid(true);
         this.setAllowedCombos(120, new DBSagasEntity.ComboType[]{DBSagasEntity.ComboType.BASIC});
         this.addKiSkill(DBSagasEntity.KiSkillType.KAMEHAMEHA, 200, 0.8F);
         this.addKiSkill(DBSagasEntity.KiSkillType.KI_VOLLEY, 100, 0.8F, 16770647, 16770647);
      }

      @Override
      public String getGeckolibModelName() {
         return "saga_goten_ssj";
      }
   }

   public static class SagaGotenksBaseEntity extends DBSagasEntity {
      public SagaGotenksBaseEntity(EntityType<? extends Monster> pEntityType, Level pLevel) {
         super(pEntityType, pLevel);
         this.setCanFly(true);
         this.setAuraColor(16777215);
         this.setKiBlastSpeed(1.4F);
         this.setDBZStyle(0);
         this.setEvade(true, 40);
         this.setAllowedCombos(200, new DBSagasEntity.ComboType[]{DBSagasEntity.ComboType.KI_CHARGE_ATTACK, DBSagasEntity.ComboType.AIR});
         this.addKiSkill(DBSagasEntity.KiSkillType.KAMEHAMEHA, 200, 1.3F);
         this.addKiSkill(DBSagasEntity.KiSkillType.KI_SMALL, 50, 1.5F, 7733247, 7733247);
         this.addKiSkill(DBSagasEntity.KiSkillType.KI_VOLLEY, 50, 0.7F, 7733247, 7733247);
         this.setisKid(true);
         this.setWildSense(true, 150);
      }

      @Override
      protected boolean hasTransformation() {
         return true;
      }

      @Override
      public EntityType<? extends DBSagasEntity> getNextTransform() {
         return (EntityType<? extends DBSagasEntity>)MainEntities.SAGA_GOTENKS_SSJ.get();
      }

      @Override
      protected boolean spawnsNewFormFullHealth() {
         return true;
      }

      @Override
      public String getGeckolibModelName() {
         return "saga_gotenks";
      }
   }

   public static class SagaGotenksSSJ3Entity extends DBSagasEntity {
      public SagaGotenksSSJ3Entity(EntityType<? extends Monster> pEntityType, Level pLevel) {
         super(pEntityType, pLevel);
         this.setCanFly(true);
         this.setAuraColor(16770647);
         this.setLightning(true);
         this.setKiBlastSpeed(1.4F);
         this.setDBZStyle(0);
         this.setEvade(true, 40);
         this.setAllowedCombos(
            200,
            new DBSagasEntity.ComboType[]{
               DBSagasEntity.ComboType.KI_CHARGE_ATTACK, DBSagasEntity.ComboType.AIR, DBSagasEntity.ComboType.METEOR_COMBINATION, DBSagasEntity.ComboType.BASIC
            }
         );
         this.addKiSkill(DBSagasEntity.KiSkillType.KAMEHAMEHA, 200, 3.0F);
         this.addKiSkill(DBSagasEntity.KiSkillType.KI_SMALL, 60, 1.5F, 16770647, 16770647);
         this.addKiSkill(DBSagasEntity.KiSkillType.KI_VOLLEY, 100, 1.5F, 16770647, 16770647);
         this.setisKid(true);
         this.setWildSense(true, 70);
         this.setZanzoken(true, 200);
      }

      @Override
      public String getGeckolibModelName() {
         return "saga_gotenks_ssj3";
      }
   }

   public static class SagaGotenksSSJEntity extends DBSagasEntity {
      public SagaGotenksSSJEntity(EntityType<? extends Monster> pEntityType, Level pLevel) {
         super(pEntityType, pLevel);
         this.setCanFly(true);
         this.setAuraColor(16770647);
         this.setKiBlastSpeed(1.4F);
         this.setDBZStyle(0);
         this.setEvade(true, 40);
         this.setAllowedCombos(200, new DBSagasEntity.ComboType[]{DBSagasEntity.ComboType.KI_CHARGE_ATTACK, DBSagasEntity.ComboType.AIR});
         this.addKiSkill(DBSagasEntity.KiSkillType.KAMEHAMEHA, 200, 1.5F);
         this.addKiSkill(DBSagasEntity.KiSkillType.KI_SMALL, 60, 1.5F, 16770647, 16770647);
         this.addKiSkill(DBSagasEntity.KiSkillType.KI_VOLLEY, 100, 1.5F, 16770647, 16770647);
         this.setisKid(true);
         this.setWildSense(true, 150);
         this.setZanzoken(true, 300);
      }

      @Override
      protected boolean hasTransformation() {
         return true;
      }

      @Override
      public EntityType<? extends DBSagasEntity> getNextTransform() {
         return (EntityType<? extends DBSagasEntity>)MainEntities.SAGA_GOTENKS_SSJ3.get();
      }

      @Override
      protected boolean spawnsNewFormFullHealth() {
         return true;
      }

      @Override
      public String getGeckolibModelName() {
         return "saga_gotenks";
      }
   }
}
