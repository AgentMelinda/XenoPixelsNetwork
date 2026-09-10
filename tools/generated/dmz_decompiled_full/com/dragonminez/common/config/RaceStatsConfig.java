package com.dragonminez.common.config;

import com.google.gson.annotations.SerializedName;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import lombok.Generated;

public class RaceStatsConfig {
   public static final String CURRENT_VERSION = "2.1.3";
   private String configVersion;
   private final Map<String, RaceStatsConfig.ClassStats> classes = new HashMap<>();
   private static final int MAX_TRACKED_CLASSES = 64;

   public RaceStatsConfig.ClassStats getClassStats(String characterClass) {
      RaceStatsConfig.ClassStats existing = this.classes.get(characterClass);
      if (existing != null) {
         return existing;
      } else if (characterClass != null && this.classes.size() < 64) {
         RaceStatsConfig.ClassStats created = new RaceStatsConfig.ClassStats();
         this.classes.put(characterClass, created);
         return created;
      } else {
         return new RaceStatsConfig.ClassStats();
      }
   }

   public Collection<String> getAllClasses() {
      return this.classes.keySet();
   }

   @Generated
   public String getConfigVersion() {
      return this.configVersion;
   }

   @Generated
   public Map<String, RaceStatsConfig.ClassStats> getClasses() {
      return this.classes;
   }

   @Generated
   public void setConfigVersion(String configVersion) {
      this.configVersion = configVersion;
   }

   public static class BaseStats {
      @SerializedName("STR")
      private Integer strength = 5;
      @SerializedName("SKP")
      private Integer strikePower = 5;
      @SerializedName("RES")
      private Integer resistance = 5;
      @SerializedName("VIT")
      private Integer vitality = 5;
      @SerializedName("PWR")
      private Integer kiPower = 5;
      @SerializedName("ENE")
      private Integer energy = 5;

      @Generated
      public void setStrength(Integer strength) {
         this.strength = strength;
      }

      @Generated
      public void setStrikePower(Integer strikePower) {
         this.strikePower = strikePower;
      }

      @Generated
      public void setResistance(Integer resistance) {
         this.resistance = resistance;
      }

      @Generated
      public void setVitality(Integer vitality) {
         this.vitality = vitality;
      }

      @Generated
      public void setKiPower(Integer kiPower) {
         this.kiPower = kiPower;
      }

      @Generated
      public void setEnergy(Integer energy) {
         this.energy = energy;
      }

      @Generated
      public Integer getStrength() {
         return this.strength;
      }

      @Generated
      public Integer getStrikePower() {
         return this.strikePower;
      }

      @Generated
      public Integer getResistance() {
         return this.resistance;
      }

      @Generated
      public Integer getVitality() {
         return this.vitality;
      }

      @Generated
      public Integer getKiPower() {
         return this.kiPower;
      }

      @Generated
      public Integer getEnergy() {
         return this.energy;
      }
   }

   public static class ClassStats {
      private RaceStatsConfig.BaseStats baseStats = new RaceStatsConfig.BaseStats();
      private RaceStatsConfig.StatScaling statScaling = new RaceStatsConfig.StatScaling();
      private Double baseHp5 = 1.25;
      private Double hp5VitScaling = 0.0375;
      private Double baseEp5 = 10.0;
      private Double ep5EneScaling = 0.2;
      private Double baseSp5 = 10.0;
      private Double sp5StmScaling = 0.1;
      private Double tpCostMultiplier = 1.0;
      private Double tpGainMultiplier = 1.0;
      private RaceStatsConfig.Passive passive = new RaceStatsConfig.Passive();

      @Generated
      public void setBaseStats(RaceStatsConfig.BaseStats baseStats) {
         this.baseStats = baseStats;
      }

      @Generated
      public void setStatScaling(RaceStatsConfig.StatScaling statScaling) {
         this.statScaling = statScaling;
      }

      @Generated
      public void setBaseHp5(Double baseHp5) {
         this.baseHp5 = baseHp5;
      }

      @Generated
      public void setHp5VitScaling(Double hp5VitScaling) {
         this.hp5VitScaling = hp5VitScaling;
      }

      @Generated
      public void setBaseEp5(Double baseEp5) {
         this.baseEp5 = baseEp5;
      }

      @Generated
      public void setEp5EneScaling(Double ep5EneScaling) {
         this.ep5EneScaling = ep5EneScaling;
      }

      @Generated
      public void setBaseSp5(Double baseSp5) {
         this.baseSp5 = baseSp5;
      }

      @Generated
      public void setSp5StmScaling(Double sp5StmScaling) {
         this.sp5StmScaling = sp5StmScaling;
      }

      @Generated
      public void setTpCostMultiplier(Double tpCostMultiplier) {
         this.tpCostMultiplier = tpCostMultiplier;
      }

      @Generated
      public void setTpGainMultiplier(Double tpGainMultiplier) {
         this.tpGainMultiplier = tpGainMultiplier;
      }

      @Generated
      public void setPassive(RaceStatsConfig.Passive passive) {
         this.passive = passive;
      }

      @Generated
      public RaceStatsConfig.BaseStats getBaseStats() {
         return this.baseStats;
      }

      @Generated
      public RaceStatsConfig.StatScaling getStatScaling() {
         return this.statScaling;
      }

      @Generated
      public Double getBaseHp5() {
         return this.baseHp5;
      }

      @Generated
      public Double getHp5VitScaling() {
         return this.hp5VitScaling;
      }

      @Generated
      public Double getBaseEp5() {
         return this.baseEp5;
      }

      @Generated
      public Double getEp5EneScaling() {
         return this.ep5EneScaling;
      }

      @Generated
      public Double getBaseSp5() {
         return this.baseSp5;
      }

      @Generated
      public Double getSp5StmScaling() {
         return this.sp5StmScaling;
      }

      @Generated
      public Double getTpCostMultiplier() {
         return this.tpCostMultiplier;
      }

      @Generated
      public Double getTpGainMultiplier() {
         return this.tpGainMultiplier;
      }

      @Generated
      public RaceStatsConfig.Passive getPassive() {
         return this.passive;
      }
   }

   public static class Passive {
      private boolean enabled = true;
      private Map<String, Double> values = new HashMap<>();

      @Generated
      public void setEnabled(boolean enabled) {
         this.enabled = enabled;
      }

      @Generated
      public void setValues(Map<String, Double> values) {
         this.values = values;
      }

      @Generated
      public boolean isEnabled() {
         return this.enabled;
      }

      @Generated
      public Map<String, Double> getValues() {
         return this.values;
      }
   }

   public static class StatScaling {
      @SerializedName("STR_scaling")
      private Double strengthScaling = 1.0;
      @SerializedName("SKP_scaling")
      private Double strikePowerScaling = 1.0;
      @SerializedName("STM_scaling")
      private Double staminaScaling = 1.0;
      @SerializedName("DEF_scaling")
      private Double defenseScaling = 1.0;
      @SerializedName("VIT_scaling")
      private Double vitalityScaling = 1.0;
      @SerializedName("PWR_scaling")
      private Double kiPowerScaling = 1.0;
      @SerializedName("ENE_scaling")
      private Double energyScaling = 1.0;

      @Generated
      public void setStrengthScaling(Double strengthScaling) {
         this.strengthScaling = strengthScaling;
      }

      @Generated
      public void setStrikePowerScaling(Double strikePowerScaling) {
         this.strikePowerScaling = strikePowerScaling;
      }

      @Generated
      public void setStaminaScaling(Double staminaScaling) {
         this.staminaScaling = staminaScaling;
      }

      @Generated
      public void setDefenseScaling(Double defenseScaling) {
         this.defenseScaling = defenseScaling;
      }

      @Generated
      public void setVitalityScaling(Double vitalityScaling) {
         this.vitalityScaling = vitalityScaling;
      }

      @Generated
      public void setKiPowerScaling(Double kiPowerScaling) {
         this.kiPowerScaling = kiPowerScaling;
      }

      @Generated
      public void setEnergyScaling(Double energyScaling) {
         this.energyScaling = energyScaling;
      }

      @Generated
      public Double getStrengthScaling() {
         return this.strengthScaling;
      }

      @Generated
      public Double getStrikePowerScaling() {
         return this.strikePowerScaling;
      }

      @Generated
      public Double getStaminaScaling() {
         return this.staminaScaling;
      }

      @Generated
      public Double getDefenseScaling() {
         return this.defenseScaling;
      }

      @Generated
      public Double getVitalityScaling() {
         return this.vitalityScaling;
      }

      @Generated
      public Double getKiPowerScaling() {
         return this.kiPowerScaling;
      }

      @Generated
      public Double getEnergyScaling() {
         return this.energyScaling;
      }
   }
}
