package com.dragonminez.common.init.entities.sagas;

import com.dragonminez.common.init.MainEntities;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;

public class SagaMoviesEntity {
   public static class A13Entity extends DBSagasEntity {
      public A13Entity(EntityType<? extends Monster> pEntityType, Level pLevel) {
         super(pEntityType, pLevel);
         this.setDBZStyle(0);
         this.setAuraColor(2276756);
         this.setKiBlastSpeed(2.2F);
         this.setCanFly(true);
         this.setAllowedCombos(
            120, new DBSagasEntity.ComboType[]{DBSagasEntity.ComboType.AIR, DBSagasEntity.ComboType.BASIC, DBSagasEntity.ComboType.KI_CHARGE_ATTACK}
         );
         this.addKiSkill(DBSagasEntity.KiSkillType.KI_SMALL, 200, 1.0F, 11141375, 7078051);
         this.addKiSkill(DBSagasEntity.KiSkillType.DEATH_BALL, 450, 1.1F, 15408670, 10360856);
         this.setWildSense(true, 100);
         this.setZanzoken(true, 200);
         this.setEvade(true, 60);
         this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.3);
         this.setDefaultMovementSpeed(0.3);
         this.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0.2);
      }
   }

   public static class A14Entity extends DBSagasEntity {
      public A14Entity(EntityType<? extends Monster> pEntityType, Level pLevel) {
         super(pEntityType, pLevel);
         this.setDBZStyle(0);
         this.setAuraColor(2276756);
         this.setKiBlastSpeed(2.2F);
         this.setScaleVal(1.2F);
         this.setCanFly(true);
         this.setAllowedCombos(120, new DBSagasEntity.ComboType[]{DBSagasEntity.ComboType.AIR});
         this.addKiSkill(DBSagasEntity.KiSkillType.KI_SMALL, 200, 1.0F, 11141375, 7078051);
         this.setWildSense(true, 100);
         this.setEvade(true, 60);
         this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.3);
         this.setDefaultMovementSpeed(0.3);
         this.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0.2);
      }
   }

   public static class A15Entity extends DBSagasEntity {
      public A15Entity(EntityType<? extends Monster> pEntityType, Level pLevel) {
         super(pEntityType, pLevel);
         this.setDBZStyle(0);
         this.setAuraColor(2276756);
         this.setKiBlastSpeed(2.2F);
         this.setScaleVal(0.8F);
         this.setCanFly(true);
         this.setAllowedCombos(120, new DBSagasEntity.ComboType[]{DBSagasEntity.ComboType.AIR});
         this.setWildSense(true, 100);
         this.setEvade(true, 60);
         this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.3);
         this.setDefaultMovementSpeed(0.3);
         this.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0.2);
      }
   }

   public static class BidoEntity extends DBSagasEntity {
      public BidoEntity(EntityType<? extends Monster> pEntityType, Level pLevel) {
         super(pEntityType, pLevel);
         this.setCanFly(true);
         this.setDBZStyle(0);
         this.setAuraColor(7714047);
         this.setScaleVal(1.3F);
         this.setKiBlastSpeed(1.5F);
         this.setAllowedCombos(120, new DBSagasEntity.ComboType[]{DBSagasEntity.ComboType.AIR, DBSagasEntity.ComboType.BASIC});
         this.addKiSkill(DBSagasEntity.KiSkillType.KI_SMALL, 80, 1.2F, 7714047, 3178979);
         this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.28);
         this.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0.5);
      }
   }

   public static class BioBrolyEntity extends DBSagasEntity {
      public BioBrolyEntity(EntityType<? extends Monster> pEntityType, Level pLevel) {
         super(pEntityType, pLevel);
         this.setCanFly(true);
         this.setDBZStyle(0);
         this.setAuraColor(8227374);
         this.setKiBlastSpeed(1.5F);
         this.setScaleVal(1.4F);
         this.setAllowedCombos(120, new DBSagasEntity.ComboType[]{DBSagasEntity.ComboType.BASIC});
         this.addKiSkill(DBSagasEntity.KiSkillType.KI_SMALL, 100, 1.2F, 8227374, 5200154);
         this.setWildSense(false, 0);
         this.setZanzoken(false, 0);
         this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.25);
         this.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0.8);
      }

      @Override
      public String getGeckolibModelName() {
         return "saga_bio_broly";
      }
   }

   public static class BioBrolyGiganteEntity extends DBSagasEntity {
      public BioBrolyGiganteEntity(EntityType<? extends Monster> pEntityType, Level pLevel) {
         super(pEntityType, pLevel);
         this.setCanFly(false);
         this.setDBZStyle(2);
         this.setAuraColor(4060490);
         this.setKiBlastSpeed(2.0F);
         this.setScaleVal(6.0F);
         this.setAllowedCombos(150, new DBSagasEntity.ComboType[]{DBSagasEntity.ComboType.BASIC, DBSagasEntity.ComboType.KI_CHARGE_ATTACK});
         this.addKiSkill(DBSagasEntity.KiSkillType.KI_AIR_VOLLEY, 300, 1.0F, 4060490, 1031963);
         this.addKiSkill(DBSagasEntity.KiSkillType.OOZARU_BEAM, 500, 1.5F, 4060490, 1031963);
         this.setWildSense(true, 150);
         this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.22);
         this.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0);
      }

      @Override
      public String getGeckolibModelName() {
         return "saga_bio_broly";
      }

      @Override
      public boolean hasHitboxParts() {
         return true;
      }

      @Override
      protected EntityDimensions getCoreDimensions() {
         return EntityDimensions.scalable(2.5F, 5.0F);
      }

      @Override
      protected DBSagasPart[] createHitboxParts() {
         return new DBSagasPart[]{
            new DBSagasPart(this, "legs", 4.0F, 5.0F, 0.0F, 0.0F, 2.5F),
            new DBSagasPart(this, "torso", 4.5F, 5.0F, 0.0F, 0.0F, 7.0F),
            new DBSagasPart(this, "head", 4.0F, 4.0F, 0.5F, 0.0F, 11.0F)
         };
      }
   }

   public static class BojackEntity extends DBSagasEntity {
      public BojackEntity(EntityType<? extends Monster> pEntityType, Level pLevel) {
         super(pEntityType, pLevel);
         this.setCanFly(true);
         this.setDBZStyle(0);
         this.setAuraColor(6222414);
         this.setKiBlastSpeed(2.0F);
         this.setScaleVal(1.2F);
         this.setAllowedCombos(
            150, new DBSagasEntity.ComboType[]{DBSagasEntity.ComboType.AIR, DBSagasEntity.ComboType.BASIC, DBSagasEntity.ComboType.KI_CHARGE_ATTACK}
         );
         this.addKiSkill(DBSagasEntity.KiSkillType.GENERIC_KI_WAVE, 300, 1.2F, 6222414, 3005976);
         this.addKiSkill(DBSagasEntity.KiSkillType.KI_AIR_VOLLEY, 250, 0.8F, 6222414, 3005976);
         this.addKiSkill(DBSagasEntity.KiSkillType.KI_BARRIER, 200, 2.3F, 6222414, 3005976);
         this.setWildSense(true, 100);
         this.setZanzoken(true, 100);
         this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.32);
         this.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0.3);
      }
   }

   public static class BojackFullPowerEntity extends DBSagasEntity {
      public BojackFullPowerEntity(EntityType<? extends Monster> pEntityType, Level pLevel) {
         super(pEntityType, pLevel);
         this.setCanFly(true);
         this.setLightning(true);
         this.setDBZStyle(0);
         this.setAuraColor(3005976);
         this.setKiBlastSpeed(2.5F);
         this.setScaleVal(1.3F);
         this.setAllowedCombos(
            150, new DBSagasEntity.ComboType[]{DBSagasEntity.ComboType.AIR, DBSagasEntity.ComboType.BASIC, DBSagasEntity.ComboType.KI_CHARGE_ATTACK}
         );
         this.addKiSkill(DBSagasEntity.KiSkillType.GENERIC_KI_WAVE, 300, 1.5F, 6222414, 3005976);
         this.addKiSkill(DBSagasEntity.KiSkillType.DEATH_BALL, 450, 1.2F, 6222414, 3005976);
         this.addKiSkill(DBSagasEntity.KiSkillType.KI_BARRIER, 200, 2.3F, 6222414, 3005976);
         this.setWildSense(true, 100);
         this.setZanzoken(true, 100);
         this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.33);
         this.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0.5);
      }
   }

   public static class BrolyBaseEntity extends DBSagasEntity {
      public BrolyBaseEntity(EntityType<? extends Monster> pEntityType, Level pLevel) {
         super(pEntityType, pLevel);
         this.setCanFly(true);
         this.setDBZStyle(0);
         this.setAuraColor(16777215);
         this.setKiBlastSpeed(2.0F);
         this.setAllowedCombos(150, new DBSagasEntity.ComboType[]{DBSagasEntity.ComboType.AIR, DBSagasEntity.ComboType.BASIC});
         this.addKiSkill(DBSagasEntity.KiSkillType.KI_SMALL, 40, 1.5F, 4060490, 1031963);
         this.addKiSkill(DBSagasEntity.KiSkillType.KI_AIR_VOLLEY, 300, 0.5F, 4060490, 1031963);
         this.setWildSense(true, 100);
         this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.3);
         this.setDefaultMovementSpeed(0.3);
         this.getAttribute(Attributes.ATTACK_SPEED).setBaseValue(6.0);
         this.setDefaultAttackSpeed(6.0);
         this.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0.2);
      }

      @Override
      protected boolean hasTransformation() {
         return true;
      }

      @Override
      public EntityType<? extends DBSagasEntity> getNextTransform() {
         return (EntityType<? extends DBSagasEntity>)MainEntities.SAGA_BROLY_SSJ_RESTRICTED.get();
      }

      @Override
      protected boolean spawnsNewFormFullHealth() {
         return false;
      }

      @Override
      public String getGeckolibModelName() {
         return "saga_broly_base";
      }
   }

   public static class BrolySSJEntity extends DBSagasEntity {
      public BrolySSJEntity(EntityType<? extends Monster> pEntityType, Level pLevel) {
         super(pEntityType, pLevel);
         this.setCanFly(true);
         this.setDBZStyle(0);
         this.setAuraColor(16771671);
         this.setKiBlastSpeed(2.2F);
         this.setAllowedCombos(
            150, new DBSagasEntity.ComboType[]{DBSagasEntity.ComboType.AIR, DBSagasEntity.ComboType.BASIC, DBSagasEntity.ComboType.KI_CHARGE_ATTACK}
         );
         this.addKiSkill(DBSagasEntity.KiSkillType.KI_SMALL, 40, 1.5F, 4060490, 1031963);
         this.addKiSkill(DBSagasEntity.KiSkillType.KI_AIR_VOLLEY, 400, 0.5F, 4060490, 1031963);
         this.setWildSense(true, 100);
         this.setZanzoken(true, 100);
         this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.3);
         this.setDefaultMovementSpeed(0.3);
         this.getAttribute(Attributes.ATTACK_SPEED).setBaseValue(6.0);
         this.setDefaultAttackSpeed(6.0);
         this.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0.2);
      }
   }

   public static class BrolySSJLegendarioEntity extends DBSagasEntity {
      public BrolySSJLegendarioEntity(EntityType<? extends Monster> pEntityType, Level pLevel) {
         super(pEntityType, pLevel);
         this.setCanFly(true);
         this.setDBZStyle(0);
         this.setAuraColor(4060490);
         this.setKiCharge(true);
         this.setKiBlastSpeed(2.5F);
         this.setScaleVal(1.4F);
         this.setAllowedCombos(
            150, new DBSagasEntity.ComboType[]{DBSagasEntity.ComboType.AIR, DBSagasEntity.ComboType.BASIC, DBSagasEntity.ComboType.KI_CHARGE_ATTACK}
         );
         this.addKiSkill(DBSagasEntity.KiSkillType.KI_SMALL, 40, 1.5F, 4060490, 1031963);
         this.addKiSkill(DBSagasEntity.KiSkillType.KI_AIR_VOLLEY, 400, 0.5F, 4060490, 1031963);
         this.addKiSkill(DBSagasEntity.KiSkillType.OOZARU_BEAM, 600, 1.5F, 4060490, 1031963);
         this.addKiSkill(DBSagasEntity.KiSkillType.OOZARU_ROAR, 200, 15.5F);
         this.setWildSense(true, 100);
         this.setZanzoken(true, 100);
         this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.3);
         this.setDefaultMovementSpeed(0.3);
         this.getAttribute(Attributes.ATTACK_SPEED).setBaseValue(6.0);
         this.setDefaultAttackSpeed(6.0);
         this.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0.2);
      }
   }

   public static class BrolySSJRestringidoEntity extends DBSagasEntity {
      public BrolySSJRestringidoEntity(EntityType<? extends Monster> pEntityType, Level pLevel) {
         super(pEntityType, pLevel);
         this.setCanFly(true);
         this.setDBZStyle(0);
         this.setAuraColor(16771671);
         this.setKiCharge(true);
         this.setKiBlastSpeed(2.0F);
         this.setAllowedCombos(150, new DBSagasEntity.ComboType[]{DBSagasEntity.ComboType.AIR, DBSagasEntity.ComboType.BASIC});
         this.addKiSkill(DBSagasEntity.KiSkillType.KI_SMALL, 40, 1.5F, 4060490, 1031963);
         this.addKiSkill(DBSagasEntity.KiSkillType.KI_AIR_VOLLEY, 300, 0.5F, 4060490, 1031963);
         this.setWildSense(true, 100);
         this.setZanzoken(true, 100);
         this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.3);
         this.setDefaultMovementSpeed(0.3);
         this.getAttribute(Attributes.ATTACK_SPEED).setBaseValue(6.0);
         this.setDefaultAttackSpeed(6.0);
         this.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0.2);
      }

      @Override
      public String getGeckolibModelName() {
         return "saga_broly_base";
      }
   }

   public static class BujinEntity extends DBSagasEntity {
      public BujinEntity(EntityType<? extends Monster> pEntityType, Level pLevel) {
         super(pEntityType, pLevel);
         this.setCanFly(true);
         this.setDBZStyle(0);
         this.setAuraColor(7714047);
         this.setScaleVal(0.9F);
         this.setKiBlastSpeed(1.8F);
         this.setAllowedCombos(120, new DBSagasEntity.ComboType[]{DBSagasEntity.ComboType.AIR});
         this.addKiSkill(DBSagasEntity.KiSkillType.KI_BARRIER, 200, 1.0F, 7714047, 3178979);
         this.setWildSense(true, 80);
         this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.3);
      }
   }

   public static class Cooler5TAEntity extends DBSagasEntity {
      public Cooler5TAEntity(EntityType<? extends Monster> pEntityType, Level pLevel) {
         super(pEntityType, pLevel);
         this.setCanFly(true);
         this.setDBZStyle(0);
         this.setAuraColor(2276756);
         this.setLightning(true);
         this.setLightningColor(10031641);
         this.setKiBlastSpeed(2.2F);
         this.setScaleVal(1.4F);
         this.setAllowedCombos(
            150, new DBSagasEntity.ComboType[]{DBSagasEntity.ComboType.AIR, DBSagasEntity.ComboType.BASIC, DBSagasEntity.ComboType.KI_CHARGE_ATTACK}
         );
         this.addKiSkill(DBSagasEntity.KiSkillType.KI_LASER, 60, 1.0F, 12521240, 10031641);
         this.addKiSkill(DBSagasEntity.KiSkillType.KI_SMALL, 200, 1.0F, 12521240, 10031641);
         this.addKiSkill(DBSagasEntity.KiSkillType.DEATH_BALL, 450, 1.1F, 12521240, 10031641);
         this.setWildSense(true, 100);
         this.setZanzoken(true, 100);
         this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.3);
         this.setDefaultMovementSpeed(0.3);
         this.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0.2);
      }
   }

   public static class CoolerEntity extends DBSagasEntity {
      public CoolerEntity(EntityType<? extends Monster> pEntityType, Level pLevel) {
         super(pEntityType, pLevel);
         this.setCanFly(true);
         this.setDBZStyle(0);
         this.setAuraColor(2276756);
         this.setKiBlastSpeed(2.2F);
         this.setAllowedCombos(
            150, new DBSagasEntity.ComboType[]{DBSagasEntity.ComboType.AIR, DBSagasEntity.ComboType.BASIC, DBSagasEntity.ComboType.KI_CHARGE_ATTACK}
         );
         this.addKiSkill(DBSagasEntity.KiSkillType.KI_LASER, 60, 1.0F, 11141375, 7078051);
         this.addKiSkill(DBSagasEntity.KiSkillType.KI_SMALL, 200, 1.0F, 11141375, 7078051);
         this.addKiSkill(DBSagasEntity.KiSkillType.DEATH_BALL, 450, 1.1F, 15408670, 10360856);
         this.setWildSense(true, 100);
         this.setZanzoken(true, 100);
         this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.3);
         this.setDefaultMovementSpeed(0.3);
         this.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0.2);
      }
   }

   public static class CoolerSoldierEntity extends DBSagasEntity {
      public CoolerSoldierEntity(EntityType<? extends Monster> pEntityType, Level pLevel) {
         super(pEntityType, pLevel);
         this.setKiBlastSpeed(1.4F);
         this.setCanFly(true);
         this.setZanzoken(true, 100);
         this.addKiSkill(DBSagasEntity.KiSkillType.KI_SMALL, 40, 1.5F, 14517759, 9511613);
         this.addKiSkill(DBSagasEntity.KiSkillType.GENERIC_KI_WAVE, 250, 1.0F, 14517759, 9511613);
      }
   }

   public static class DrWheeloEntity extends DBSagasEntity {
      public DrWheeloEntity(EntityType<? extends Monster> pEntityType, Level pLevel) {
         super(pEntityType, pLevel);
         this.setAuraColor(16777215);
         this.setKiBlastSpeed(1.4F);
         this.setDBZStyle(0);
         this.setEvade(true, 100);
         this.setLightning(true);
         this.setScaleVal(1.5F);
         this.addKiSkill(DBSagasEntity.KiSkillType.KI_VOLLEY, 170, 1.0F, 13385430, 9113748);
         this.addKiSkill(DBSagasEntity.KiSkillType.KI_SMALL, 80, 1.0F, 13385430, 9113748);
         this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.4);
      }
   }

   public static class GarlickJrEntity extends DBSagasEntity {
      public GarlickJrEntity(EntityType<? extends Monster> pEntityType, Level pLevel) {
         super(pEntityType, pLevel);
         this.setAuraColor(16777215);
         this.setKiBlastSpeed(1.4F);
         this.setZanzoken(true, 100);
         this.addKiSkill(DBSagasEntity.KiSkillType.KI_BARRIER, 200, 2.3F, 13385430, 9113748);
         this.addKiSkill(DBSagasEntity.KiSkillType.KI_VOLLEY, 170, 1.0F, 13385430, 9113748);
      }
   }

   public static class GarlickJrTransformedEntity extends DBSagasEntity {
      public GarlickJrTransformedEntity(EntityType<? extends Monster> pEntityType, Level pLevel) {
         super(pEntityType, pLevel);
         this.setAuraColor(10031402);
         this.setKiBlastSpeed(1.4F);
         this.setZanzoken(true, 100);
         this.setScaleVal(1.2F);
         this.setAllowedCombos(150, new DBSagasEntity.ComboType[]{DBSagasEntity.ComboType.AIR});
         this.addKiSkill(DBSagasEntity.KiSkillType.KI_BARRIER, 200, 2.3F, 13385430, 9113748);
         this.addKiSkill(DBSagasEntity.KiSkillType.KI_VOLLEY, 170, 1.0F, 13385430, 9113748);
      }
   }

   public static class GeteRobotEntity extends DBSagasEntity {
      public GeteRobotEntity(EntityType<? extends Monster> pEntityType, Level pLevel) {
         super(pEntityType, pLevel);
         this.setZanzoken(true, 100);
         this.setDBZStyle(2);
      }
   }

   public static class GokuaEntity extends DBSagasEntity {
      public GokuaEntity(EntityType<? extends Monster> pEntityType, Level pLevel) {
         super(pEntityType, pLevel);
         this.setCanFly(true);
         this.setDBZStyle(0);
         this.setAuraColor(7714047);
         this.setKiBlastSpeed(1.6F);
         this.setScaleVal(1.2F);
         this.setAllowedCombos(150, new DBSagasEntity.ComboType[]{DBSagasEntity.ComboType.AIR, DBSagasEntity.ComboType.BASIC});
         this.addKiSkill(DBSagasEntity.KiSkillType.GENERIC_KI_WAVE, 200, 1.0F, 7714047, 3178979);
         this.setWildSense(true, 100);
         this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.32);
         this.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(10.0);
      }
   }

   public static class HirudegarnEntity extends DBSagasEntity {
      public HirudegarnEntity(EntityType<? extends Monster> pEntityType, Level pLevel) {
         super(pEntityType, pLevel);
         this.setCanFly(false);
         this.setDBZStyle(3);
         this.setAuraColor(3674189);
         this.setKiBlastSpeed(1.5F);
         this.setScaleVal(2.5F);
         this.addKiSkill(DBSagasEntity.KiSkillType.KI_SMALL, 150, 2.0F, 3674189, 1771302);
         this.addKiSkill(DBSagasEntity.KiSkillType.OOZARU_BEAM, 300, 1.5F, 3674189, 1771302);
         this.addKiSkill(DBSagasEntity.KiSkillType.OOZARU_ROAR, 200, 15.5F);
         this.setWildSense(true, 120);
         this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.28);
         this.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0);
      }

      @Override
      public String getGeckolibModelName() {
         return "saga_hirudegarn";
      }

      @Override
      public boolean hasHitboxParts() {
         return true;
      }

      @Override
      protected EntityDimensions getCoreDimensions() {
         return EntityDimensions.scalable(3.0F, 5.0F);
      }

      @Override
      protected DBSagasPart[] createHitboxParts() {
         return new DBSagasPart[]{
            new DBSagasPart(this, "legs", 5.0F, 5.0F, 0.0F, 0.0F, 2.5F),
            new DBSagasPart(this, "torso", 5.0F, 5.0F, 0.0F, 0.0F, 7.0F),
            new DBSagasPart(this, "head", 5.0F, 4.0F, 0.5F, 0.0F, 11.0F)
         };
      }
   }

   public static class JanembaGordoEntity extends DBSagasEntity {
      public JanembaGordoEntity(EntityType<? extends Monster> pEntityType, Level pLevel) {
         super(pEntityType, pLevel);
         this.setCanFly(true);
         this.setDBZStyle(0);
         this.setAuraColor(16766720);
         this.setKiBlastSpeed(1.8F);
         this.setScaleVal(8.0F);
         this.setAllowedCombos(100, new DBSagasEntity.ComboType[]{DBSagasEntity.ComboType.BASIC, DBSagasEntity.ComboType.GUM_PUNCH});
         this.addKiSkill(DBSagasEntity.KiSkillType.KI_VOLLEY, 150, 1.5F, 16766720, 13413376);
         this.addKiSkill(DBSagasEntity.KiSkillType.OOZARU_ROAR, 200, 15.5F);
         this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.24);
         this.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0.8);
      }

      @Override
      public boolean hasHitboxParts() {
         return true;
      }

      @Override
      protected EntityDimensions getCoreDimensions() {
         return EntityDimensions.scalable(3.5F, 5.0F);
      }

      @Override
      protected DBSagasPart[] createHitboxParts() {
         return new DBSagasPart[]{
            new DBSagasPart(this, "legs", 6.0F, 5.0F, 0.0F, 0.0F, 2.5F),
            new DBSagasPart(this, "torso", 7.0F, 5.0F, 0.0F, 0.0F, 7.0F),
            new DBSagasPart(this, "head", 5.0F, 4.0F, 0.5F, 0.0F, 11.0F)
         };
      }
   }

   public static class MetalCoolerCoreEntity extends DBSagasEntity {
      public MetalCoolerCoreEntity(EntityType<? extends Monster> pEntityType, Level pLevel) {
         super(pEntityType, pLevel);
         this.setDBZStyle(2);
         this.setAuraColor(2276756);
         this.setKiBlastSpeed(2.2F);
         this.setScaleVal(6.0F);
         this.setLightning(true);
         this.setAllowedCombos(
            150, new DBSagasEntity.ComboType[]{DBSagasEntity.ComboType.AIR, DBSagasEntity.ComboType.BASIC, DBSagasEntity.ComboType.KI_CHARGE_ATTACK}
         );
         this.addKiSkill(DBSagasEntity.KiSkillType.KI_LASER, 60, 1.0F, 11141375, 7078051);
         this.addKiSkill(DBSagasEntity.KiSkillType.KI_SMALL, 200, 1.0F, 11141375, 7078051);
         this.addKiSkill(DBSagasEntity.KiSkillType.DEATH_BALL, 450, 1.1F, 15408670, 10360856);
         this.addKiSkill(DBSagasEntity.KiSkillType.KI_AIR_VOLLEY, 300, 0.5F, 15408670, 10360856);
         this.setWildSense(true, 100);
         this.setZanzoken(true, 200);
         this.setEvade(true, 60);
         this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.3);
         this.setDefaultMovementSpeed(0.3);
         this.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0.2);
      }
   }

   public static class MetalCoolerEntity extends DBSagasEntity {
      public MetalCoolerEntity(EntityType<? extends Monster> pEntityType, Level pLevel) {
         super(pEntityType, pLevel);
         this.setCanFly(true);
         this.setDBZStyle(0);
         this.setAuraColor(2276756);
         this.setKiBlastSpeed(2.2F);
         this.setAllowedCombos(
            150, new DBSagasEntity.ComboType[]{DBSagasEntity.ComboType.AIR, DBSagasEntity.ComboType.BASIC, DBSagasEntity.ComboType.KI_CHARGE_ATTACK}
         );
         this.addKiSkill(DBSagasEntity.KiSkillType.KI_LASER, 60, 1.0F, 11141375, 7078051);
         this.addKiSkill(DBSagasEntity.KiSkillType.KI_SMALL, 200, 1.0F, 11141375, 7078051);
         this.addKiSkill(DBSagasEntity.KiSkillType.DEATH_BALL, 450, 1.1F, 15408670, 10360856);
         this.setWildSense(true, 100);
         this.setZanzoken(true, 200);
         this.setEvade(true, 60);
         this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.3);
         this.setDefaultMovementSpeed(0.3);
         this.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0.2);
      }

      @Override
      public String getGeckolibModelName() {
         return "saga_cooler";
      }
   }

   public static class PaikuhanEntity extends DBSagasEntity {
      public PaikuhanEntity(EntityType<? extends Monster> pEntityType, Level pLevel) {
         super(pEntityType, pLevel);
         this.setCanFly(true);
         this.setDBZStyle(0);
         this.setAuraColor(16735744);
         this.setKiBlastSpeed(2.2F);
         this.setAllowedCombos(
            150, new DBSagasEntity.ComboType[]{DBSagasEntity.ComboType.AIR, DBSagasEntity.ComboType.BASIC, DBSagasEntity.ComboType.KI_CHARGE_ATTACK}
         );
         this.addKiSkill(DBSagasEntity.KiSkillType.GENERIC_KI_WAVE, 200, 1.2F, 16735744, 13388544);
         this.addKiSkill(DBSagasEntity.KiSkillType.KI_BARRIER, 200, 2.3F, 16735744, 13388544);
         this.setWildSense(true, 80);
         this.setZanzoken(true, 80);
         this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.34);
         this.getAttribute(Attributes.ATTACK_SPEED).setBaseValue(7.0);
      }
   }

   public static class ParagusEntity extends DBSagasEntity {
      public ParagusEntity(EntityType<? extends Monster> pEntityType, Level pLevel) {
         super(pEntityType, pLevel);
         this.setCanFly(true);
         this.setDBZStyle(0);
         this.setKiBlastSpeed(2.0F);
         this.setAllowedCombos(150, new DBSagasEntity.ComboType[]{DBSagasEntity.ComboType.AIR});
         this.addKiSkill(DBSagasEntity.KiSkillType.KI_SMALL, 40, 1.5F, 4060490, 1031963);
         this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.3);
         this.setDefaultMovementSpeed(0.3);
         this.getAttribute(Attributes.ATTACK_SPEED).setBaseValue(6.0);
         this.setDefaultAttackSpeed(6.0);
         this.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0.2);
      }
   }

   public static class SlugEntity extends DBSagasEntity {
      public SlugEntity(EntityType<? extends Monster> pEntityType, Level pLevel) {
         super(pEntityType, pLevel);
         this.setCanFly(true);
         this.setDBZStyle(0);
         this.setAuraColor(2276756);
         this.setKiBlastSpeed(2.0F);
         this.setScaleVal(1.2F);
         this.setAllowedCombos(
            150,
            new DBSagasEntity.ComboType[]{
               DBSagasEntity.ComboType.AIR, DBSagasEntity.ComboType.BASIC, DBSagasEntity.ComboType.KI_CHARGE_ATTACK, DBSagasEntity.ComboType.GUM_PUNCH
            }
         );
         this.addKiSkill(DBSagasEntity.KiSkillType.GENERIC_KI_WAVE, 450, 1.0F, 11093731, 6754713);
         this.addKiSkill(DBSagasEntity.KiSkillType.MASENKO, 250, 2.0F);
         this.setWildSense(true, 100);
         this.setZanzoken(true, 100);
         this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.3);
         this.setDefaultMovementSpeed(0.3);
         this.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0.2);
      }

      @Override
      public String getGeckolibModelName() {
         return "saga_slug";
      }
   }

   public static class SlugGiantEntity extends DBSagasEntity {
      public SlugGiantEntity(EntityType<? extends Monster> pEntityType, Level pLevel) {
         super(pEntityType, pLevel);
         this.setCanFly(true);
         this.setDBZStyle(2);
         this.setAuraColor(2276756);
         this.setKiBlastSpeed(2.0F);
         this.setScaleVal(6.0F);
         this.setAllowedCombos(
            150,
            new DBSagasEntity.ComboType[]{
               DBSagasEntity.ComboType.AIR, DBSagasEntity.ComboType.BASIC, DBSagasEntity.ComboType.KI_CHARGE_ATTACK, DBSagasEntity.ComboType.GUM_PUNCH
            }
         );
         this.addKiSkill(DBSagasEntity.KiSkillType.GENERIC_KI_WAVE, 450, 1.0F, 11093731, 6754713);
         this.addKiSkill(DBSagasEntity.KiSkillType.MASENKO, 250, 2.0F);
         this.setWildSense(true, 100);
         this.setZanzoken(true, 100);
         this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.3);
         this.setDefaultMovementSpeed(0.3);
         this.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0.2);
      }

      @Override
      public String getGeckolibModelName() {
         return "saga_slug";
      }
   }

   public static class SlugSoldierEntity extends DBSagasEntity {
      public SlugSoldierEntity(EntityType<? extends Monster> pEntityType, Level pLevel) {
         super(pEntityType, pLevel);
         this.setKiBlastSpeed(1.4F);
         this.setCanFly(true);
         this.setZanzoken(true, 100);
         this.addKiSkill(DBSagasEntity.KiSkillType.KI_SMALL, 40, 1.5F, 13994751, 10178247);
      }
   }

   public static class SuperA13Entity extends DBSagasEntity {
      public SuperA13Entity(EntityType<? extends Monster> pEntityType, Level pLevel) {
         super(pEntityType, pLevel);
         this.setDBZStyle(0);
         this.setAuraColor(2276756);
         this.setKiBlastSpeed(2.2F);
         this.setScaleVal(1.4F);
         this.setCanFly(true);
         this.setLightning(true);
         this.setAllowedCombos(
            120, new DBSagasEntity.ComboType[]{DBSagasEntity.ComboType.AIR, DBSagasEntity.ComboType.BASIC, DBSagasEntity.ComboType.KI_CHARGE_ATTACK}
         );
         this.addKiSkill(DBSagasEntity.KiSkillType.KI_SMALL, 200, 1.0F, 11141375, 7078051);
         this.addKiSkill(DBSagasEntity.KiSkillType.DEATH_BALL, 450, 1.1F, 15408670, 10360856);
         this.addKiSkill(DBSagasEntity.KiSkillType.KI_AIR_VOLLEY, 300, 0.5F, 15408670, 10360856);
         this.setWildSense(true, 100);
         this.setZanzoken(true, 200);
         this.setEvade(true, 60);
         this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.3);
         this.setDefaultMovementSpeed(0.3);
         this.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0.2);
      }
   }

   public static class SuperHirudegarnEntity extends DBSagasEntity {
      public SuperHirudegarnEntity(EntityType<? extends Monster> pEntityType, Level pLevel) {
         super(pEntityType, pLevel);
         this.setCanFly(true);
         this.setDBZStyle(3);
         this.setAuraColor(3674189);
         this.setKiBlastSpeed(2.2F);
         this.setScaleVal(2.5F);
         this.addKiSkill(DBSagasEntity.KiSkillType.KI_SMALL, 150, 2.0F, 3674189, 1771302);
         this.addKiSkill(DBSagasEntity.KiSkillType.OOZARU_BEAM, 300, 1.5F, 3674189, 1771302);
         this.addKiSkill(DBSagasEntity.KiSkillType.OOZARU_ROAR, 200, 15.5F);
         this.setWildSense(true, 80);
         this.setZanzoken(true, 100);
         this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.35);
         this.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0);
      }

      @Override
      public String getGeckolibModelName() {
         return "saga_hirudegarn";
      }
   }

   public static class SuperJanembaEntity extends DBSagasEntity {
      public SuperJanembaEntity(EntityType<? extends Monster> pEntityType, Level pLevel) {
         super(pEntityType, pLevel);
         this.setCanFly(true);
         this.setDBZStyle(0);
         this.setAuraColor(12724552);
         this.setKiBlastSpeed(2.5F);
         this.setAllowedCombos(
            150,
            new DBSagasEntity.ComboType[]{
               DBSagasEntity.ComboType.AIR, DBSagasEntity.ComboType.BASIC, DBSagasEntity.ComboType.KI_CHARGE_ATTACK, DBSagasEntity.ComboType.GUM_PUNCH
            }
         );
         this.addKiSkill(DBSagasEntity.KiSkillType.KI_LASER, 150, 1.5F, 12724552, 9182002);
         this.addKiSkill(DBSagasEntity.KiSkillType.GENERIC_KI_WAVE, 250, 1.2F, 12724552, 9182002);
         this.addKiSkill(DBSagasEntity.KiSkillType.OOZARU_ROAR, 200, 15.5F);
         this.addKiSkill(DBSagasEntity.KiSkillType.KI_BARRIER, 200, 2.3F, 12724552, 9182002);
         this.setWildSense(true, 50);
         this.setZanzoken(true, 40);
         this.setEvade(true, 50);
         this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.42);
         this.getAttribute(Attributes.ATTACK_SPEED).setBaseValue(8.0);
      }
   }

   public static class TurlesEntity extends DBSagasEntity {
      public TurlesEntity(EntityType<? extends Monster> pEntityType, Level pLevel) {
         super(pEntityType, pLevel);
         this.setCanFly(true);
         this.setDBZStyle(0);
         this.setAuraColor(16066343);
         this.setKiBlastSpeed(2.0F);
         this.setAllowedCombos(
            150, new DBSagasEntity.ComboType[]{DBSagasEntity.ComboType.AIR, DBSagasEntity.ComboType.BASIC, DBSagasEntity.ComboType.KI_CHARGE_ATTACK}
         );
         this.addKiSkill(DBSagasEntity.KiSkillType.GENERIC_KI_WAVE, 450, 1.0F, 11093731, 6754713);
         this.addKiSkill(DBSagasEntity.KiSkillType.KI_AIR_VOLLEY, 300, 0.5F, 11093731, 6754713);
         this.setWildSense(true, 100);
         this.setZanzoken(true, 100);
         this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.3);
         this.setDefaultMovementSpeed(0.3);
         this.getAttribute(Attributes.ATTACK_SPEED).setBaseValue(6.0);
         this.setDefaultAttackSpeed(6.0);
         this.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0.2);
      }
   }

   public static class ZangyaEntity extends DBSagasEntity {
      public ZangyaEntity(EntityType<? extends Monster> pEntityType, Level pLevel) {
         super(pEntityType, pLevel);
         this.setCanFly(true);
         this.setDBZStyle(0);
         this.setAuraColor(7714047);
         this.setKiBlastSpeed(1.8F);
         this.setAllowedCombos(120, new DBSagasEntity.ComboType[]{DBSagasEntity.ComboType.AIR, DBSagasEntity.ComboType.BASIC});
         this.addKiSkill(DBSagasEntity.KiSkillType.KI_SMALL, 60, 1.0F, 7714047, 3178979);
         this.setWildSense(true, 100);
         this.setZanzoken(true, 100);
         this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.3);
      }
   }
}
