package com.dragonminez.common.init.entities.sagas;

import com.dragonminez.common.init.entities.IBattlePower;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;

public class SagaCellEntity {
   public static class SagaCellJREntity extends DBSagasEntity {
      public SagaCellJREntity(EntityType<? extends Monster> pEntityType, Level pLevel) {
         super(pEntityType, pLevel);
         if (this instanceof IBattlePower bp) {
            bp.setBattlePower(2100000000);
         }

         this.setCanFly(true);
         this.setDBZStyle(0);
         this.setAuraColor(16776258);
         this.setTextureVariant(0);
         this.setKiBlastSpeed(2.0F);
         this.setisKid(true);
         this.setAllowedCombos(150, new DBSagasEntity.ComboType[]{DBSagasEntity.ComboType.AIR, DBSagasEntity.ComboType.BASIC});
         this.addKiSkill(DBSagasEntity.KiSkillType.KAMEHAMEHA, 300, 1.5F);
         this.addKiSkill(DBSagasEntity.KiSkillType.KI_SMALL, 80, 1.0F, 16772992, 16772992);
         this.setEvade(true, 150);
         this.setWildSense(true, 250);
         this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.35);
         this.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0.2);
      }
   }

   public static class SagaImperfectCellEntity extends DBSagasEntity {
      public SagaImperfectCellEntity(EntityType<? extends Monster> pEntityType, Level pLevel) {
         super(pEntityType, pLevel);
         if (this instanceof IBattlePower bp) {
            bp.setBattlePower(750000000);
         }

         this.setCanFly(true);
         this.setDBZStyle(0);
         this.setAuraColor(16777215);
         this.setTextureVariant(0);
         this.setKiBlastSpeed(2.0F);
         this.setAllowedCombos(150, new DBSagasEntity.ComboType[]{DBSagasEntity.ComboType.AIR, DBSagasEntity.ComboType.ANDROID_ABSORPTION});
         this.addKiSkill(DBSagasEntity.KiSkillType.KAMEHAMEHA, 250, 1.0F);
         this.addKiSkill(DBSagasEntity.KiSkillType.KI_SMALL, 80, 1.0F, 16774484, 16774484);
         this.setEvade(true, 150);
         this.setWildSense(true, 250);
         this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.35);
      }
   }

   public static class SagaPerfectCellEntity extends DBSagasEntity {
      public SagaPerfectCellEntity(EntityType<? extends Monster> pEntityType, Level pLevel) {
         super(pEntityType, pLevel);
         if (this instanceof IBattlePower bp) {
            bp.setBattlePower(2100000000);
         }

         this.setCanFly(true);
         this.setDBZStyle(0);
         this.setAuraColor(16776258);
         this.setTextureVariant(0);
         this.setKiBlastSpeed(2.0F);
         this.setAllowedCombos(
            150, new DBSagasEntity.ComboType[]{DBSagasEntity.ComboType.AIR, DBSagasEntity.ComboType.BASIC, DBSagasEntity.ComboType.KI_CHARGE_ATTACK}
         );
         this.addKiSkill(DBSagasEntity.KiSkillType.KI_EXPLOSION, 450, 1.0F, 9515729, 5714641);
         this.addKiSkill(DBSagasEntity.KiSkillType.KAMEHAMEHA, 300, 1.5F);
         this.addKiSkill(DBSagasEntity.KiSkillType.MAKANKOSAPPO, 150, 1.0F);
         this.addKiSkill(DBSagasEntity.KiSkillType.KI_SMALL, 80, 1.0F, 16772992, 16772992);
         this.setEvade(true, 150);
         this.setWildSense(true, 250);
         this.setZanzoken(true, 100);
         this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.35);
         this.setDefaultMovementSpeed(0.35);
         this.getAttribute(Attributes.ATTACK_SPEED).setBaseValue(5.0);
         this.setDefaultAttackSpeed(5.0);
         this.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0.2);
      }

      @Override
      public String getGeckolibModelName() {
         return "saga_cell_perfect";
      }
   }

   public static class SagaSemiPerfectCellEntity extends DBSagasEntity {
      public SagaSemiPerfectCellEntity(EntityType<? extends Monster> pEntityType, Level pLevel) {
         super(pEntityType, pLevel);
         if (this instanceof IBattlePower bp) {
            bp.setBattlePower(1450000000);
         }

         this.setCanFly(true);
         this.setDBZStyle(0);
         this.setAuraColor(16777215);
         this.setTextureVariant(0);
         this.setKiBlastSpeed(2.0F);
         this.setScaleVal(1.2F);
         this.setAllowedCombos(150, new DBSagasEntity.ComboType[]{DBSagasEntity.ComboType.AIR, DBSagasEntity.ComboType.KI_CHARGE_ATTACK});
         this.addKiSkill(DBSagasEntity.KiSkillType.BIG_BANG, 250, 2.0F, 13046804, 9373447);
         this.addKiSkill(DBSagasEntity.KiSkillType.KI_SMALL, 80, 1.0F, 12067133, 9177634);
         this.setEvade(true, 150);
         this.setWildSense(true, 250);
         this.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0.2);
      }
   }

   public static class SagaSuperPerfectCellEntity extends DBSagasEntity {
      public SagaSuperPerfectCellEntity(EntityType<? extends Monster> pEntityType, Level pLevel) {
         super(pEntityType, pLevel);
         if (this instanceof IBattlePower bp) {
            bp.setBattlePower(2100000000);
         }

         this.setCanFly(true);
         this.setDBZStyle(0);
         this.setAuraColor(16776258);
         this.setLightning(true);
         this.setTextureVariant(0);
         this.setKiBlastSpeed(2.0F);
         this.setAllowedCombos(
            150,
            new DBSagasEntity.ComboType[]{
               DBSagasEntity.ComboType.AIR, DBSagasEntity.ComboType.BASIC, DBSagasEntity.ComboType.KI_CHARGE_ATTACK, DBSagasEntity.ComboType.METEOR_COMBINATION
            }
         );
         this.addKiSkill(DBSagasEntity.KiSkillType.KI_EXPLOSION, 450, 1.0F, 9515729, 5714641);
         this.addKiSkill(DBSagasEntity.KiSkillType.KAMEHAMEHA, 300, 1.8F);
         this.addKiSkill(DBSagasEntity.KiSkillType.MAKANKOSAPPO, 150, 1.0F);
         this.addKiSkill(DBSagasEntity.KiSkillType.KI_SMALL, 80, 1.0F, 16772992, 16772992);
         this.setEvade(true, 60);
         this.setWildSense(true, 100);
         this.setZanzoken(true, 100);
         this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.35);
         this.setDefaultMovementSpeed(0.35);
         this.getAttribute(Attributes.ATTACK_SPEED).setBaseValue(8.0);
         this.setDefaultAttackSpeed(8.0);
         this.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0.2);
      }

      @Override
      public String getGeckolibModelName() {
         return "saga_cell_perfect";
      }
   }
}
