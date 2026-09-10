package com.dragonminez.common.init.entities.sagas;

import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.init.MainItems;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsProvider;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;

public class SagaBabidiSoldiersEntity {
   public static class BabidiEntity extends DBSagasEntity {
      public BabidiEntity(EntityType<? extends Monster> pEntityType, Level pLevel) {
         super(pEntityType, pLevel);
         this.setCanFly(true);
         this.setAuraColor(16774282);
         this.setKiBlastSpeed(1.4F);
         this.setDBZStyle(1);
         this.setWildSense(true, 100);
         this.setAllowedCombos(120, new DBSagasEntity.ComboType[]{DBSagasEntity.ComboType.AIR});
         this.addKiSkill(DBSagasEntity.KiSkillType.KI_BARRIER, 200, 2.3F, 4904036, 3322734);
      }
   }

   public static class DaburaEntity extends DBSagasEntity {
      public DaburaEntity(EntityType<? extends Monster> pEntityType, Level pLevel) {
         super(pEntityType, pLevel);
         this.setCanFly(true);
         this.setDBZStyle(0);
         this.setAuraColor(16066343);
         this.setTextureVariant(0);
         this.setKiBlastSpeed(2.0F);
         this.setAllowedCombos(
            150, new DBSagasEntity.ComboType[]{DBSagasEntity.ComboType.AIR, DBSagasEntity.ComboType.BASIC, DBSagasEntity.ComboType.KI_CHARGE_ATTACK}
         );
         this.addKiSkill(DBSagasEntity.KiSkillType.GENERIC_KI_WAVE, 450, 1.0F, 16066343, 12194836);
         this.setWildSense(true, 100);
         this.setZanzoken(true, 100);
         this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.3);
         this.setDefaultMovementSpeed(0.3);
         this.getAttribute(Attributes.ATTACK_SPEED).setBaseValue(6.0);
         this.setDefaultAttackSpeed(6.0);
         this.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0.2);
         this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack((ItemLike)MainItems.BRAVE_SWORD.get()));
      }
   }

   public static class SagaPuiPuiEntity extends DBSagasEntity {
      public SagaPuiPuiEntity(EntityType<? extends Monster> pEntityType, Level pLevel) {
         super(pEntityType, pLevel);
         this.setCanFly(true);
         this.setAuraColor(16777215);
         this.setKiBlastSpeed(1.4F);
         this.setDBZStyle(0);
         this.setEvade(true, 100);
         this.addKiSkill(DBSagasEntity.KiSkillType.KI_VOLLEY, 170, 1.0F, 10379775, 10379775);
         this.addKiSkill(DBSagasEntity.KiSkillType.KI_SMALL, 80, 1.0F, 10379775, 10379775);
         this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.4);
      }
   }

   public static class SagaSpopovitchEntity extends DBSagasEntity {
      public SagaSpopovitchEntity(EntityType<? extends Monster> pEntityType, Level pLevel) {
         super(pEntityType, pLevel);
         this.setCanFly(true);
         this.setAuraColor(16777215);
         this.setKiBlastSpeed(1.4F);
         this.setDBZStyle(2);
         this.addKiSkill(DBSagasEntity.KiSkillType.OOZARU_BEAM, 120, 1.0F, 12462069, 12462069);
      }
   }

   public static class SagaYakonEntity extends DBSagasEntity {
      public SagaYakonEntity(EntityType<? extends Monster> pEntityType, Level pLevel) {
         super(pEntityType, pLevel);
         this.setCanFly(true);
         this.setAuraColor(16777215);
         this.setKiBlastSpeed(1.4F);
         this.setDBZStyle(2);
         this.setScaleVal(1.5F);
      }

      @Override
      public boolean doHurtTarget(Entity target) {
         boolean hurt = super.doHurtTarget(target);
         if (hurt && target instanceof ServerPlayer serverPlayer) {
            StatsProvider.get(StatsCapability.INSTANCE, serverPlayer)
               .ifPresent(
                  data -> {
                     if (data.getStatus().isHasCreatedCharacter()) {
                        double currentKi = (double)data.getResources().getCurrentEnergy();
                        double maxKi = (double)data.getMaxEnergy();
                        double drainAmount = maxKi * 0.03;
                        double newKi = Math.max(0.0, currentKi - drainAmount);
                        data.getResources().setCurrentEnergy((float)newKi);
                        if (data.getCharacter().hasActiveForm() || data.getCharacter().hasActiveStackForm()) {
                           float dmg = (float)(
                              (double)ConfigManager.getCombatConfig().getBaselineFormDrain().intValue()
                                 * data.getTotalMultiplier(
                                    data.getMeleeDamage() > data.getKiDamage() ? "STR" : (data.getKiDamage() > data.getStrikeDamage() ? "PWR" : "SKP")
                                 )
                                 / 4.0
                           );
                           data.getCharacter().clearActiveForm(this);
                           data.getCharacter().clearActiveStackForm(this);
                           this.hurt(this.damageSources().magic(), dmg);
                        }
                     }
                  }
               );
         }

         return hurt;
      }
   }
}
