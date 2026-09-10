package com.dragonminez.common.init.entities.sagas;

import com.dragonminez.common.init.entities.IBattlePower;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;

public class SagaGinyuForcesEntity {
   public static class SagaBurterEntity extends DBSagasEntity {
      public SagaBurterEntity(EntityType<? extends Monster> pEntityType, Level pLevel) {
         super(pEntityType, pLevel);
         if (this instanceof IBattlePower bp) {
            bp.setBattlePower(40000);
         }

         this.setCanFly(true);
         this.setDBZStyle(1);
         this.setAuraColor(4151548);
         this.setFlySpeed(0.55);
         this.setEvade(true, 120);
         this.setWildSense(true, 200);
         this.addKiSkill(DBSagasEntity.KiSkillType.BLUE_HURRICANE, 400, 5.0F, 16727018, 14826495);
      }
   }

   public static class SagaGinyuEntity extends DBSagasEntity {
      public SagaGinyuEntity(EntityType<? extends Monster> pEntityType, Level pLevel) {
         super(pEntityType, pLevel);
         if (this instanceof IBattlePower bp) {
            bp.setBattlePower(120000);
         }

         this.setCanFly(true);
         this.setDBZStyle(0);
         this.setAuraColor(11141375);
         this.setAllowedCombos(140, new DBSagasEntity.ComboType[]{DBSagasEntity.ComboType.KI_CHARGE_ATTACK});
         this.setEvade(true, 150);
         this.setWildSense(true, 200);
         this.addKiSkill(DBSagasEntity.KiSkillType.GENERIC_KI_WAVE, 180, 1.5F, 11141375, 6684842);
         this.addKiSkill(DBSagasEntity.KiSkillType.KI_SMALL, 80, 1.0F, 11141375, 6684842);
      }
   }

   public static class SagaGinyuGokuEntity extends DBSagasEntity {
      public SagaGinyuGokuEntity(EntityType<? extends Monster> pEntityType, Level pLevel) {
         super(pEntityType, pLevel);
         if (this instanceof IBattlePower bp) {
            bp.setBattlePower(120000);
         }

         this.setCanFly(true);
         this.setDBZStyle(0);
         this.setAuraColor(11141375);
         this.setAllowedCombos(140, new DBSagasEntity.ComboType[]{DBSagasEntity.ComboType.KI_CHARGE_ATTACK});
         this.setEvade(true, 150);
         this.setWildSense(true, 200);
         this.addKiSkill(DBSagasEntity.KiSkillType.GENERIC_KI_WAVE, 180, 1.5F, 11141375, 6684842);
         this.addKiSkill(DBSagasEntity.KiSkillType.KI_SMALL, 80, 1.0F, 11141375, 6684842);
      }
   }

   public static class SagaGuldoEntity extends DBSagasEntity {
      public SagaGuldoEntity(EntityType<? extends Monster> pEntityType, Level pLevel) {
         super(pEntityType, pLevel);
         if (this instanceof IBattlePower bp) {
            bp.setBattlePower(10000);
         }

         this.setDBZStyle(0);
         this.setAuraColor(5635925);
         this.setEvade(true, 100);
         this.addKiSkill(DBSagasEntity.KiSkillType.KI_SMALL, 60, 1.0F, 5635925, 5635925);
      }
   }

   public static class SagaJeiceEntity extends DBSagasEntity {
      public SagaJeiceEntity(EntityType<? extends Monster> pEntityType, Level pLevel) {
         super(pEntityType, pLevel);
         if (this instanceof IBattlePower bp) {
            bp.setBattlePower(40000);
         }

         this.setCanFly(true);
         this.setDBZStyle(1);
         this.setAuraColor(16724787);
         this.setKiBlastSpeed(2.0F);
         this.setEvade(true, 180);
         this.addKiSkill(DBSagasEntity.KiSkillType.KI_SMALL, 60, 1.0F, 16724787, 16724787);
         this.addKiSkill(DBSagasEntity.KiSkillType.KI_VOLLEY, 200, 2.0F, 16724787, 16724787);
      }
   }

   public static class SagaRecoomeEntity extends DBSagasEntity {
      public SagaRecoomeEntity(EntityType<? extends Monster> pEntityType, Level pLevel) {
         super(pEntityType, pLevel);
         if (this instanceof IBattlePower bp) {
            bp.setBattlePower(40000);
         }

         this.setCanFly(true);
         this.setDBZStyle(2);
         this.setAuraColor(16777215);
         this.setKiBlastSpeed(1.3F);
         this.setAllowedCombos(140, new DBSagasEntity.ComboType[]{DBSagasEntity.ComboType.AIR});
         this.addKiSkill(DBSagasEntity.KiSkillType.KI_EXPLOSION, 300, 5.0F, 16727018, 14826495);
         this.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0.5);
      }
   }
}
