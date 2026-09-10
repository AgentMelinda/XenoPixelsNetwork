package com.dragonminez.common.init.entities.sagas;

import com.dragonminez.common.init.MainEntities;
import com.dragonminez.common.init.entities.IBattlePower;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;

public class SagaVegetaEntity {
   public static class SagaMajinVegetaEntity extends DBSagasEntity {
      private boolean hasUsedFinalExplosion = false;

      public SagaMajinVegetaEntity(EntityType<? extends Monster> pEntityType, Level pLevel) {
         super(pEntityType, pLevel);
         this.setCanFly(true);
         this.setAuraColor(16770647);
         this.setKiBlastSpeed(1.6F);
         this.setDBZStyle(0);
         this.setWildSense(true, 600);
         this.setZanzoken(true, 200);
         this.setLightning(true);
         this.setAllowedCombos(120, new DBSagasEntity.ComboType[]{DBSagasEntity.ComboType.AIR, DBSagasEntity.ComboType.KI_CHARGE_ATTACK});
         this.addKiSkill(DBSagasEntity.KiSkillType.KI_VOLLEY, 260, 1.2F, 16770647, 16770647);
         this.addKiSkill(DBSagasEntity.KiSkillType.BIG_BANG, 340, 1.7F, 14942207, 14942207);
         this.addKiSkill(DBSagasEntity.KiSkillType.FINAL_FLASH, 400, 2.2F);
      }

      @Override
      public boolean hurt(DamageSource pSource, float pAmount) {
         if (this.isCasting() && this.getSkillType() == DBSagasEntity.KiSkillType.KI_EXPLOSION.getId()) {
            return false;
         } else {
            boolean actuallyHurt = super.hurt(pSource, pAmount);
            if (actuallyHurt && !this.level().isClientSide) {
               float umbralVida = this.getMaxHealth() * 0.15F;
               if (this.getHealth() <= umbralVida && !this.hasUsedFinalExplosion) {
                  this.hasUsedFinalExplosion = true;
                  if (this.isComboing()) {
                     this.setComboing(false);
                  }

                  this.setSkillColors(16770647, 16770647, 16777215);
                  this.startCasting(DBSagasEntity.KiSkillType.KI_EXPLOSION.getId());
                  this.setHealth(10.0F);
               }
            }

            return actuallyHurt;
         }
      }

      @Override
      public String getGeckolibModelName() {
         return "saga_vegeta_ssg2";
      }
   }

   public static class SagaVegetaEndBaseEntity extends DBSagasEntity {
      public SagaVegetaEndBaseEntity(EntityType<? extends Monster> pEntityType, Level pLevel) {
         super(pEntityType, pLevel);
         this.setCanFly(true);
         this.setAuraColor(16777215);
         this.setKiBlastSpeed(1.6F);
         this.setDBZStyle(0);
         this.setEvade(true, 60);
         this.setWildSense(true, 100);
         this.setAllowedCombos(120, new DBSagasEntity.ComboType[]{DBSagasEntity.ComboType.AIR});
         this.addKiSkill(DBSagasEntity.KiSkillType.KI_VOLLEY, 200, 1.2F, 49407, 49407);
         this.addKiSkill(DBSagasEntity.KiSkillType.GALICK_GUN, 400, 1.2F);
      }

      @Override
      public String getGeckolibModelName() {
         return "saga_vegeta";
      }

      @Override
      protected boolean hasTransformation() {
         return true;
      }

      @Override
      public EntityType<? extends DBSagasEntity> getNextTransform() {
         return (EntityType<? extends DBSagasEntity>)MainEntities.SAGA_VEGETA_END_SSJ.get();
      }

      @Override
      protected boolean spawnsNewFormFullHealth() {
         return true;
      }
   }

   public static class SagaVegetaEndSSJ2Entity extends DBSagasEntity {
      public SagaVegetaEndSSJ2Entity(EntityType<? extends Monster> pEntityType, Level pLevel) {
         super(pEntityType, pLevel);
         this.setCanFly(true);
         this.setAuraColor(16770647);
         this.setKiBlastSpeed(1.6F);
         this.setDBZStyle(0);
         this.setWildSense(true, 100);
         this.setZanzoken(true, 200);
         this.setLightning(true);
         this.setAllowedCombos(120, new DBSagasEntity.ComboType[]{DBSagasEntity.ComboType.AIR, DBSagasEntity.ComboType.KI_CHARGE_ATTACK});
         this.addKiSkill(DBSagasEntity.KiSkillType.KI_VOLLEY, 260, 1.2F, 16770647, 16770647);
         this.addKiSkill(DBSagasEntity.KiSkillType.GALICK_GUN, 100, 1.2F);
         this.addKiSkill(DBSagasEntity.KiSkillType.BIG_BANG, 340, 1.7F, 14942207, 14942207);
         this.addKiSkill(DBSagasEntity.KiSkillType.FINAL_FLASH, 400, 2.2F);
      }

      @Override
      public String getGeckolibModelName() {
         return "saga_vegeta_ssj2";
      }
   }

   public static class SagaVegetaEndSSJEntity extends DBSagasEntity {
      public SagaVegetaEndSSJEntity(EntityType<? extends Monster> pEntityType, Level pLevel) {
         super(pEntityType, pLevel);
         this.setCanFly(true);
         this.setAuraColor(16770647);
         this.setKiBlastSpeed(1.6F);
         this.setDBZStyle(0);
         this.setEvade(true, 60);
         this.setWildSense(true, 100);
         this.setAllowedCombos(120, new DBSagasEntity.ComboType[]{DBSagasEntity.ComboType.AIR, DBSagasEntity.ComboType.KI_CHARGE_ATTACK});
         this.addKiSkill(DBSagasEntity.KiSkillType.KI_VOLLEY, 200, 1.2F, 16770647, 16770647);
         this.addKiSkill(DBSagasEntity.KiSkillType.GALICK_GUN, 400, 1.2F);
         this.addKiSkill(DBSagasEntity.KiSkillType.BIG_BANG, 200, 1.7F, 14942207, 14942207);
      }

      @Override
      public String getGeckolibModelName() {
         return "saga_vegeta";
      }

      @Override
      protected boolean hasTransformation() {
         return true;
      }

      @Override
      public EntityType<? extends DBSagasEntity> getNextTransform() {
         return (EntityType<? extends DBSagasEntity>)MainEntities.SAGA_VEGETA_END_SSJ2.get();
      }

      @Override
      protected boolean spawnsNewFormFullHealth() {
         return true;
      }
   }

   public static class SagaVegetaExplorerEntity extends DBSagasEntity {
      public SagaVegetaExplorerEntity(EntityType<? extends Monster> pEntityType, Level pLevel) {
         super(pEntityType, pLevel);
         if (this instanceof IBattlePower bp) {
            bp.setBattlePower(18000);
         }

         this.setCanFly(true);
         this.setAuraColor(16774282);
         this.setKiBlastSpeed(1.2F);
         this.setDBZStyle(0);
         this.setEvade(true, 60);
         this.setWildSense(true, 100);
         this.setAllowedCombos(120, new DBSagasEntity.ComboType[]{DBSagasEntity.ComboType.AIR});
         this.addKiSkill(DBSagasEntity.KiSkillType.KI_SMALL, 50, 1.2F, 16774282, 16774282);
         this.addKiSkill(DBSagasEntity.KiSkillType.GALICK_GUN, 400, 1.2F);
      }

      @Override
      public String getGeckolibModelName() {
         return "saga_vegeta";
      }
   }

   public static class SagaVegetaMidBaseEntity extends DBSagasEntity {
      public SagaVegetaMidBaseEntity(EntityType<? extends Monster> pEntityType, Level pLevel) {
         super(pEntityType, pLevel);
         if (this instanceof IBattlePower bp) {
            bp.setBattlePower(13000000);
         }

         this.setCanFly(true);
         this.setAuraColor(16777215);
         this.setKiBlastSpeed(1.4F);
         this.setDBZStyle(0);
         this.setEvade(true, 60);
         this.setWildSense(true, 100);
         this.setAllowedCombos(120, new DBSagasEntity.ComboType[]{DBSagasEntity.ComboType.AIR});
         this.addKiSkill(DBSagasEntity.KiSkillType.KI_VOLLEY, 200, 1.2F, 49407, 49407);
         this.addKiSkill(DBSagasEntity.KiSkillType.GALICK_GUN, 400, 1.2F);
      }

      @Override
      public String getGeckolibModelName() {
         return "saga_vegeta";
      }

      @Override
      protected boolean hasTransformation() {
         return true;
      }

      @Override
      public EntityType<? extends DBSagasEntity> getNextTransform() {
         return (EntityType<? extends DBSagasEntity>)MainEntities.SAGA_VEGETA_MID_SSJ.get();
      }

      @Override
      protected boolean spawnsNewFormFullHealth() {
         return true;
      }
   }

   public static class SagaVegetaMidSSG2Entity extends DBSagasEntity {
      public SagaVegetaMidSSG2Entity(EntityType<? extends Monster> pEntityType, Level pLevel) {
         super(pEntityType, pLevel);
         if (this instanceof IBattlePower bp) {
            bp.setBattlePower(650000000);
         }

         this.setCanFly(true);
         this.setAuraColor(16770647);
         this.setKiBlastSpeed(1.4F);
         this.setDBZStyle(0);
         this.setEvade(true, 60);
         this.setWildSense(true, 100);
         this.setAllowedCombos(120, new DBSagasEntity.ComboType[]{DBSagasEntity.ComboType.AIR, DBSagasEntity.ComboType.KI_CHARGE_ATTACK});
         this.addKiSkill(DBSagasEntity.KiSkillType.KI_SMALL, 80, 1.2F, 16770647, 16770647);
         this.addKiSkill(DBSagasEntity.KiSkillType.BIG_BANG, 200, 1.5F, 14942207, 14942207);
         this.addKiSkill(DBSagasEntity.KiSkillType.FINAL_FLASH, 400, 2.0F);
      }

      @Override
      public String getGeckolibModelName() {
         return "saga_vegeta_ssg2";
      }
   }

   public static class SagaVegetaMidSSJEntity extends DBSagasEntity {
      public SagaVegetaMidSSJEntity(EntityType<? extends Monster> pEntityType, Level pLevel) {
         super(pEntityType, pLevel);
         if (this instanceof IBattlePower bp) {
            bp.setBattlePower(650000000);
         }

         this.setCanFly(true);
         this.setAuraColor(16770647);
         this.setKiBlastSpeed(1.4F);
         this.setDBZStyle(0);
         this.setEvade(true, 60);
         this.setWildSense(true, 100);
         this.setAllowedCombos(120, new DBSagasEntity.ComboType[]{DBSagasEntity.ComboType.AIR, DBSagasEntity.ComboType.KI_CHARGE_ATTACK});
         this.addKiSkill(DBSagasEntity.KiSkillType.KI_SMALL, 80, 1.2F, 16770647, 16770647);
         this.addKiSkill(DBSagasEntity.KiSkillType.GALICK_GUN, 200, 1.2F);
         this.addKiSkill(DBSagasEntity.KiSkillType.BIG_BANG, 400, 1.5F, 14942207, 14942207);
      }

      @Override
      public String getGeckolibModelName() {
         return "saga_vegeta";
      }

      @Override
      protected boolean hasTransformation() {
         return true;
      }

      @Override
      public EntityType<? extends DBSagasEntity> getNextTransform() {
         return (EntityType<? extends DBSagasEntity>)MainEntities.SAGA_VEGETA_MID_SSG2.get();
      }

      @Override
      protected boolean spawnsNewFormFullHealth() {
         return false;
      }
   }

   public static class SagaVegetaNamekEntity extends DBSagasEntity {
      public SagaVegetaNamekEntity(EntityType<? extends Monster> pEntityType, Level pLevel) {
         super(pEntityType, pLevel);
         if (this instanceof IBattlePower bp) {
            bp.setBattlePower(24000);
         }

         this.setCanFly(true);
         this.setAuraColor(16774282);
         this.setKiBlastSpeed(1.4F);
         this.setDBZStyle(0);
         this.setEvade(true, 60);
         this.setWildSense(true, 100);
         this.setAllowedCombos(120, new DBSagasEntity.ComboType[]{DBSagasEntity.ComboType.AIR});
         this.addKiSkill(DBSagasEntity.KiSkillType.KI_VOLLEY, 200, 1.2F, 49407, 49407);
         this.addKiSkill(DBSagasEntity.KiSkillType.GALICK_GUN, 400, 1.2F);
      }

      @Override
      public String getGeckolibModelName() {
         return "saga_vegeta";
      }
   }

   public static class SagaVegettoBaseEntity extends DBSagasEntity {
      public SagaVegettoBaseEntity(EntityType<? extends Monster> pEntityType, Level pLevel) {
         super(pEntityType, pLevel);
         this.setCanFly(true);
         this.setAuraColor(16777215);
         this.setKiBlastSpeed(1.6F);
         this.setDBZStyle(0);
         this.setEvade(true, 60);
         this.setWildSense(true, 100);
         this.setAllowedCombos(120, new DBSagasEntity.ComboType[]{DBSagasEntity.ComboType.AIR});
         this.addKiSkill(DBSagasEntity.KiSkillType.KI_VOLLEY, 200, 1.2F, 49407, 49407);
         this.addKiSkill(DBSagasEntity.KiSkillType.BIG_BANG, 340, 1.7F, 14942207, 14942207);
      }
   }

   public static class SagaVegettoSSJEntity extends DBSagasEntity {
      public SagaVegettoSSJEntity(EntityType<? extends Monster> pEntityType, Level pLevel) {
         super(pEntityType, pLevel);
         this.setCanFly(true);
         this.setAuraColor(16770647);
         this.setKiBlastSpeed(1.6F);
         this.setDBZStyle(0);
         this.setWildSense(true, 100);
         this.setZanzoken(true, 200);
         this.setAllowedCombos(120, new DBSagasEntity.ComboType[]{DBSagasEntity.ComboType.AIR, DBSagasEntity.ComboType.KI_CHARGE_ATTACK});
         this.addKiSkill(DBSagasEntity.KiSkillType.KI_VOLLEY, 260, 1.2F, 16770647, 16770647);
         this.addKiSkill(DBSagasEntity.KiSkillType.BIG_BANG, 340, 1.7F, 14942207, 14942207);
         this.addKiSkill(DBSagasEntity.KiSkillType.FINAL_FLASH, 400, 2.2F);
      }
   }
}
