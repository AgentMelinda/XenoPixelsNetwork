package com.dragonminez.common.init.entities.sagas;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;

public class SagaBuuEntity {
   public static class BuuFatEntity extends DBSagasEntity {
      public BuuFatEntity(EntityType<? extends Monster> pEntityType, Level pLevel) {
         super(pEntityType, pLevel);
         this.setCanFly(true);
         this.setDBZStyle(0);
         this.setAuraColor(16745203);
         this.setKiBlastSpeed(2.0F);
         this.setAllowedCombos(
            150, new DBSagasEntity.ComboType[]{DBSagasEntity.ComboType.GUM_PUNCH, DBSagasEntity.ComboType.GUM_EXPAND, DBSagasEntity.ComboType.AIR}
         );
         this.addKiSkill(DBSagasEntity.KiSkillType.KI_SMALL, 40, 1.5F, 16745203, 16718572);
         this.addKiSkill(DBSagasEntity.KiSkillType.KI_VOLLEY, 120, 1.0F, 16745203, 16718572);
         this.addKiSkill(DBSagasEntity.KiSkillType.MAJIN_CANDY, 200, 1.0F, 16745203, 16718572);
         this.setEvade(true, 60);
         this.setWildSense(true, 100);
         this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.35);
         this.setDefaultMovementSpeed(0.35);
         this.getAttribute(Attributes.ATTACK_SPEED).setBaseValue(4.5);
         this.setDefaultAttackSpeed(4.5);
         this.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0.45);
      }
   }

   public static class EvilBuuEntity extends DBSagasEntity {
      public EvilBuuEntity(EntityType<? extends Monster> pEntityType, Level pLevel) {
         super(pEntityType, pLevel);
         this.setCanFly(true);
         this.setDBZStyle(0);
         this.setAuraColor(16745203);
         this.setKiBlastSpeed(2.0F);
         this.setScaleVal(1.2F);
         this.setAllowedCombos(
            150, new DBSagasEntity.ComboType[]{DBSagasEntity.ComboType.GUM_PUNCH, DBSagasEntity.ComboType.AIR, DBSagasEntity.ComboType.BASIC}
         );
         this.addKiSkill(DBSagasEntity.KiSkillType.KI_SMALL, 40, 1.5F, 16745203, 16718572);
         this.addKiSkill(DBSagasEntity.KiSkillType.KI_VOLLEY, 120, 1.0F, 16745203, 16718572);
         this.addKiSkill(DBSagasEntity.KiSkillType.MAJIN_CANDY, 200, 1.0F, 16745203, 16718572);
         this.setEvade(true, 60);
         this.setWildSense(true, 100);
         this.setZanzoken(true, 250);
         this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.3);
         this.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0.3);
      }
   }

   public static class KidBuuEntity extends DBSagasEntity {
      public KidBuuEntity(EntityType<? extends Monster> pEntityType, Level pLevel) {
         super(pEntityType, pLevel);
         this.setCanFly(true);
         this.setDBZStyle(0);
         this.setAuraColor(16745203);
         this.setKiBlastSpeed(2.2F);
         this.setAllowedCombos(
            60,
            new DBSagasEntity.ComboType[]{
               DBSagasEntity.ComboType.GUM_PUNCH,
               DBSagasEntity.ComboType.AIR,
               DBSagasEntity.ComboType.BASIC,
               DBSagasEntity.ComboType.GUM_EXPAND,
               DBSagasEntity.ComboType.RAPID_KICKS,
               DBSagasEntity.ComboType.SLEEP_RECOVERY
            }
         );
         this.addKiSkill(DBSagasEntity.KiSkillType.KI_SMALL, 40, 1.5F, 16745203, 16718572);
         this.addKiSkill(DBSagasEntity.KiSkillType.GENERIC_KI_WAVE, 400, 2.0F, 16745203, 16718572, 14552013);
         this.addKiSkill(DBSagasEntity.KiSkillType.OOZARU_ROAR, 250, 1.5F);
         this.addKiSkill(DBSagasEntity.KiSkillType.BIG_BANG, 850, 5.0F, 10363782, 16732898, 16711889);
         this.setWildSense(true, 100);
         this.setZanzoken(true, 200);
         this.setisKid(true);
         this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.45);
         this.setDefaultMovementSpeed(0.45);
         this.getAttribute(Attributes.ATTACK_SPEED).setBaseValue(8.0);
         this.setDefaultAttackSpeed(8.0);
         this.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0.35);
      }
   }

   public static class MiniBuuEntity extends DBSagasEntity {
      public MiniBuuEntity(EntityType<? extends Monster> pEntityType, Level pLevel) {
         super(pEntityType, pLevel);
         this.setCanFly(true);
         this.setDBZStyle(0);
         this.setAuraColor(16745203);
         this.setKiBlastSpeed(1.6F);
         this.setScaleVal(0.45F);
         this.addKiSkill(DBSagasEntity.KiSkillType.KI_SMALL, 80, 1.0F, 16745203, 16718572);
         this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.28);
         this.setDefaultMovementSpeed(0.28);
         this.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0.2);
      }

      @Override
      public String getGeckolibModelName() {
         return "saga_buufat";
      }
   }

   public static class SuperBuuEntity extends DBSagasEntity {
      public SuperBuuEntity(EntityType<? extends Monster> pEntityType, Level pLevel) {
         super(pEntityType, pLevel);
         this.setCanFly(true);
         this.setDBZStyle(0);
         this.setAuraColor(16745203);
         this.setKiBlastSpeed(2.0F);
         this.setScaleVal(1.2F);
         this.setAllowedCombos(
            150,
            new DBSagasEntity.ComboType[]{
               DBSagasEntity.ComboType.GUM_PUNCH, DBSagasEntity.ComboType.AIR, DBSagasEntity.ComboType.BASIC, DBSagasEntity.ComboType.GUM_EXPAND
            }
         );
         this.addKiSkill(DBSagasEntity.KiSkillType.KI_SMALL, 40, 1.5F, 16745203, 16718572);
         this.addKiSkill(DBSagasEntity.KiSkillType.GENERIC_KI_WAVE, 120, 2.0F, 16745203, 16718572, 14552013);
         this.addKiSkill(DBSagasEntity.KiSkillType.KI_AIR_VOLLEY, 300, 1.5F, 16745203, 16718572);
         this.setEvade(true, 60);
         this.setWildSense(true, 100);
         this.setZanzoken(true, 250);
         this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.35);
         this.setDefaultMovementSpeed(0.35);
         this.getAttribute(Attributes.ATTACK_SPEED).setBaseValue(4.5);
         this.setDefaultAttackSpeed(4.5);
         this.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0.3);
      }

      @Override
      public String getGeckolibModelName() {
         return "saga_superbuu";
      }
   }

   public static class SuperBuuGohanEntity extends DBSagasEntity {
      public SuperBuuGohanEntity(EntityType<? extends Monster> pEntityType, Level pLevel) {
         super(pEntityType, pLevel);
         this.setCanFly(true);
         this.setDBZStyle(0);
         this.setAuraColor(16745203);
         this.setKiBlastSpeed(2.2F);
         this.setLightning(true);
         this.setScaleVal(1.2F);
         this.setLightningColor(14551819);
         this.setAllowedCombos(
            120,
            new DBSagasEntity.ComboType[]{
               DBSagasEntity.ComboType.GUM_PUNCH,
               DBSagasEntity.ComboType.AIR,
               DBSagasEntity.ComboType.BASIC,
               DBSagasEntity.ComboType.GUM_EXPAND,
               DBSagasEntity.ComboType.METEOR_COMBINATION,
               DBSagasEntity.ComboType.KI_CHARGE_ATTACK
            }
         );
         this.addKiSkill(DBSagasEntity.KiSkillType.KI_SMALL, 40, 1.5F, 16745203, 16718572);
         this.addKiSkill(DBSagasEntity.KiSkillType.GENERIC_KI_WAVE, 120, 2.0F, 16745203, 16718572, 14552013);
         this.addKiSkill(DBSagasEntity.KiSkillType.KAMEHAMEHA, 450, 2.0F);
         this.addKiSkill(DBSagasEntity.KiSkillType.MASENKO, 250, 2.0F);
         this.addKiSkill(DBSagasEntity.KiSkillType.KI_AIR_VOLLEY, 300, 0.5F, 16745203, 16718572);
         this.setEvade(true, 60);
         this.setWildSense(true, 100);
         this.setZanzoken(true, 200);
         this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.35);
         this.setDefaultMovementSpeed(0.35);
         this.getAttribute(Attributes.ATTACK_SPEED).setBaseValue(4.5);
         this.setDefaultAttackSpeed(4.5);
         this.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0.35);
      }

      @Override
      public String getGeckolibModelName() {
         return "saga_superbuu";
      }
   }

   public static class SuperBuuGotenksEntity extends DBSagasEntity {
      public SuperBuuGotenksEntity(EntityType<? extends Monster> pEntityType, Level pLevel) {
         super(pEntityType, pLevel);
         this.setCanFly(true);
         this.setDBZStyle(0);
         this.setAuraColor(16745203);
         this.setKiBlastSpeed(2.2F);
         this.setScaleVal(1.2F);
         this.setAllowedCombos(
            150,
            new DBSagasEntity.ComboType[]{
               DBSagasEntity.ComboType.GUM_PUNCH,
               DBSagasEntity.ComboType.AIR,
               DBSagasEntity.ComboType.BASIC,
               DBSagasEntity.ComboType.GUM_EXPAND,
               DBSagasEntity.ComboType.METEOR_COMBINATION,
               DBSagasEntity.ComboType.RAPID_KICKS
            }
         );
         this.addKiSkill(DBSagasEntity.KiSkillType.KI_SMALL, 40, 1.5F, 16745203, 16718572);
         this.addKiSkill(DBSagasEntity.KiSkillType.GENERIC_KI_WAVE, 120, 2.0F, 16745203, 16718572, 14552013);
         this.addKiSkill(DBSagasEntity.KiSkillType.KAMEHAMEHA, 450, 2.0F);
         this.addKiSkill(DBSagasEntity.KiSkillType.MAKANKOSAPPO, 250, 2.0F);
         this.setEvade(true, 60);
         this.setWildSense(true, 100);
         this.setZanzoken(true, 250);
         this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.35);
         this.setDefaultMovementSpeed(0.35);
         this.getAttribute(Attributes.ATTACK_SPEED).setBaseValue(4.5);
         this.setDefaultAttackSpeed(4.5);
         this.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0.3);
      }

      @Override
      public String getGeckolibModelName() {
         return "saga_superbuu";
      }
   }

   public static class SuperBuuPiccoloEntity extends DBSagasEntity {
      public SuperBuuPiccoloEntity(EntityType<? extends Monster> pEntityType, Level pLevel) {
         super(pEntityType, pLevel);
         this.setCanFly(true);
         this.setDBZStyle(0);
         this.setAuraColor(16745203);
         this.setKiBlastSpeed(2.2F);
         this.setScaleVal(1.2F);
         this.setAllowedCombos(
            150,
            new DBSagasEntity.ComboType[]{
               DBSagasEntity.ComboType.GUM_PUNCH, DBSagasEntity.ComboType.AIR, DBSagasEntity.ComboType.BASIC, DBSagasEntity.ComboType.GUM_EXPAND
            }
         );
         this.addKiSkill(DBSagasEntity.KiSkillType.KI_SMALL, 40, 1.5F, 16745203, 16718572);
         this.addKiSkill(DBSagasEntity.KiSkillType.GENERIC_KI_WAVE, 120, 2.0F, 16745203, 16718572, 14552013);
         this.addKiSkill(DBSagasEntity.KiSkillType.MAKANKOSAPPO, 400, 1.5F);
         this.addKiSkill(DBSagasEntity.KiSkillType.DEATH_BALL, 200, 1.2F);
         this.setEvade(true, 60);
         this.setWildSense(true, 100);
         this.setZanzoken(true, 250);
         this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.35);
         this.setDefaultMovementSpeed(0.35);
         this.getAttribute(Attributes.ATTACK_SPEED).setBaseValue(4.5);
         this.setDefaultAttackSpeed(4.5);
         this.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0.3);
      }

      @Override
      public String getGeckolibModelName() {
         return "saga_superbuu";
      }
   }
}
