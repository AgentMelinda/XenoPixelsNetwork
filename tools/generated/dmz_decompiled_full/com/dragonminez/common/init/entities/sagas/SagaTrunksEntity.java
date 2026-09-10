package com.dragonminez.common.init.entities.sagas;

import com.dragonminez.common.init.MainEntities;
import com.dragonminez.common.init.MainItems;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;

public class SagaTrunksEntity {
   public static class SagaFutureTrunksBaseEntity extends DBSagasEntity {
      public SagaFutureTrunksBaseEntity(EntityType<? extends Monster> pEntityType, Level pLevel) {
         super(pEntityType, pLevel);
         this.setCanFly(true);
         this.setAuraColor(16777215);
         this.setKiBlastSpeed(1.4F);
         this.setDBZStyle(0);
         this.setEvade(true, 60);
         this.setWildSense(true, 100);
         this.setAllowedCombos(120, new DBSagasEntity.ComboType[]{DBSagasEntity.ComboType.AIR});
         this.addKiSkill(DBSagasEntity.KiSkillType.KI_VOLLEY, 200, 1.2F, 49407, 49407);
         this.addKiSkill(DBSagasEntity.KiSkillType.GALICK_GUN, 400, 1.2F);
         this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack((ItemLike)MainItems.BRAVE_SWORD.get()));
      }

      @Override
      public String getGeckolibModelName() {
         return "saga_trunks";
      }

      @Override
      protected boolean hasTransformation() {
         return true;
      }

      @Override
      public EntityType<? extends DBSagasEntity> getNextTransform() {
         return (EntityType<? extends DBSagasEntity>)MainEntities.SAGA_FUTURE_TRUNKS_SSJ.get();
      }

      @Override
      protected boolean spawnsNewFormFullHealth() {
         return true;
      }
   }

   public static class SagaFutureTrunksKidBaseEntity extends DBSagasEntity {
      public SagaFutureTrunksKidBaseEntity(EntityType<? extends Monster> pEntityType, Level pLevel) {
         super(pEntityType, pLevel);
         this.setCanFly(true);
         this.setAuraColor(16777215);
         this.setKiBlastSpeed(1.4F);
         this.setDBZStyle(0);
         this.setEvade(true, 60);
         this.setisKid(true);
         this.addKiSkill(DBSagasEntity.KiSkillType.KI_VOLLEY, 200, 1.2F, 6094847, 6094847);
         this.addKiSkill(DBSagasEntity.KiSkillType.KI_SMALL, 400, 1.2F, 6094847, 6094847);
      }

      @Override
      public String getGeckolibModelName() {
         return "saga_trunks";
      }

      @Override
      protected boolean hasTransformation() {
         return true;
      }

      @Override
      public EntityType<? extends DBSagasEntity> getNextTransform() {
         return (EntityType<? extends DBSagasEntity>)MainEntities.SAGA_FUTURE_TRUNKS_KID_SSJ.get();
      }

      @Override
      protected boolean spawnsNewFormFullHealth() {
         return true;
      }
   }

   public static class SagaFutureTrunksKidSSJEntity extends DBSagasEntity {
      public SagaFutureTrunksKidSSJEntity(EntityType<? extends Monster> pEntityType, Level pLevel) {
         super(pEntityType, pLevel);
         this.setCanFly(true);
         this.setAuraColor(16770647);
         this.setKiBlastSpeed(1.4F);
         this.setDBZStyle(0);
         this.setEvade(true, 60);
         this.setisKid(true);
         this.addKiSkill(DBSagasEntity.KiSkillType.KI_VOLLEY, 200, 1.2F, 16770647, 16770647);
         this.addKiSkill(DBSagasEntity.KiSkillType.KI_SMALL, 400, 1.2F, 16770647, 16770647);
      }

      @Override
      public String getGeckolibModelName() {
         return "saga_trunks_ssj";
      }
   }

   public static class SagaFutureTrunksSSG3Entity extends DBSagasEntity {
      public SagaFutureTrunksSSG3Entity(EntityType<? extends Monster> pEntityType, Level pLevel) {
         super(pEntityType, pLevel);
         this.setCanFly(true);
         this.setAuraColor(16770647);
         this.setKiBlastSpeed(1.4F);
         this.setDBZStyle(0);
         this.setEvade(true, 60);
         this.setWildSense(true, 100);
         this.setKiCharge(true);
         this.setScaleVal(1.3F);
         this.addKiSkill(DBSagasEntity.KiSkillType.BIG_BANG, 200, 1.5F, 49407, 49407);
         this.addKiSkill(DBSagasEntity.KiSkillType.MASENKO, 100, 1.2F, 16770647, 16770647);
         this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.14);
         this.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0.2);
      }

      @Override
      public String getGeckolibModelName() {
         return "saga_trunks_ssg3";
      }
   }

   public static class SagaFutureTrunksSSJEntity extends DBSagasEntity {
      public SagaFutureTrunksSSJEntity(EntityType<? extends Monster> pEntityType, Level pLevel) {
         super(pEntityType, pLevel);
         this.setCanFly(true);
         this.setAuraColor(16770647);
         this.setKiBlastSpeed(1.4F);
         this.setDBZStyle(0);
         this.setEvade(true, 60);
         this.setWildSense(true, 100);
         this.setAllowedCombos(120, new DBSagasEntity.ComboType[]{DBSagasEntity.ComboType.AIR, DBSagasEntity.ComboType.KI_CHARGE_ATTACK});
         this.addKiSkill(DBSagasEntity.KiSkillType.KI_VOLLEY, 200, 1.2F, 49407, 49407);
         this.addKiSkill(DBSagasEntity.KiSkillType.MASENKO, 100, 1.2F, 16770647, 16770647);
         this.addKiSkill(DBSagasEntity.KiSkillType.GALICK_GUN, 400, 1.2F);
         this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack((ItemLike)MainItems.BRAVE_SWORD.get()));
      }

      @Override
      public String getGeckolibModelName() {
         return "saga_trunks_ssj";
      }
   }

   public static class SagaKidTrunksBaseEntity extends DBSagasEntity {
      public SagaKidTrunksBaseEntity(EntityType<? extends Monster> pEntityType, Level pLevel) {
         super(pEntityType, pLevel);
         this.setCanFly(true);
         this.setAuraColor(16777215);
         this.setKiBlastSpeed(1.4F);
         this.setDBZStyle(0);
         this.setEvade(true, 60);
         this.setisKid(true);
         this.addKiSkill(DBSagasEntity.KiSkillType.KI_VOLLEY, 200, 0.8F, 6094847, 6094847);
         this.addKiSkill(DBSagasEntity.KiSkillType.KI_SMALL, 400, 1.2F, 6094847, 6094847);
      }

      @Override
      public String getGeckolibModelName() {
         return "saga_kid_trunks";
      }

      @Override
      protected boolean hasTransformation() {
         return true;
      }

      @Override
      public EntityType<? extends DBSagasEntity> getNextTransform() {
         return (EntityType<? extends DBSagasEntity>)MainEntities.SAGA_KID_TRUNKS_SSJ.get();
      }

      @Override
      protected boolean spawnsNewFormFullHealth() {
         return true;
      }
   }

   public static class SagaKidTrunksSSJEntity extends DBSagasEntity {
      public SagaKidTrunksSSJEntity(EntityType<? extends Monster> pEntityType, Level pLevel) {
         super(pEntityType, pLevel);
         this.setCanFly(true);
         this.setAuraColor(16770647);
         this.setKiBlastSpeed(1.6F);
         this.setDBZStyle(0);
         this.setEvade(true, 60);
         this.setisKid(true);
         this.addKiSkill(DBSagasEntity.KiSkillType.KI_VOLLEY, 200, 1.2F, 16770647, 16770647);
         this.addKiSkill(DBSagasEntity.KiSkillType.KI_SMALL, 400, 1.2F, 16770647, 16770647);
      }

      @Override
      public String getGeckolibModelName() {
         return "saga_kid_trunks_ssj";
      }
   }
}
