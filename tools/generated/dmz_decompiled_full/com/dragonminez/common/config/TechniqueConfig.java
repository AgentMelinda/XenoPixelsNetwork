package com.dragonminez.common.config;

import com.dragonminez.common.stats.techniques.KiAttackData;
import com.dragonminez.common.stats.techniques.PredefinedTechniques;
import com.google.gson.annotations.SerializedName;
import java.util.HashMap;
import java.util.Map;
import lombok.Generated;

public class TechniqueConfig {
   public static final String CURRENT_VERSION = "2.1.3";
   private String configVersion;
   @SerializedName("KiAttacks")
   private final Map<String, TechniqueConfig.TechniqueTypeConfig> kiAttacks = new HashMap<>();
   @SerializedName("StrikeAttacks")
   private final Map<String, TechniqueConfig.StrikeAttackConfig> strikeAttacks = new HashMap<>();

   public TechniqueConfig() {
      this.createDefaults();
   }

   private void createDefaults() {
      for (KiAttackData.KiType type : KiAttackData.KiType.values()) {
         TechniqueConfig.TechniqueTypeConfig cfg = TechniqueConfig.TechniqueTypeConfig.defaults();
         cfg.setCastTimeTicks(defaultCastTimeTicks(type));
         this.kiAttacks.put(type.name().toLowerCase(), cfg);
      }

      for (String strikeId : PredefinedTechniques.STRIKE_IDS) {
         TechniqueConfig.StrikeAttackConfig cfg = TechniqueConfig.StrikeAttackConfig.defaults();
         cfg.setCooldownTicks(defaultStrikeCooldownTicks(strikeId));
         this.strikeAttacks.put(strikeId, cfg);
      }
   }

   private static int defaultStrikeCooldownTicks(String strikeId) {
      return switch (strikeId) {
         case "dragon_fist" -> 320;
         case "oozaru_fist" -> 280;
         case "super_god_fist", "kaioken_attack" -> 240;
         case "deadly_dance_vegetto" -> 200;
         case "wolf_fang" -> 140;
         default -> 160;
      };
   }

   private static int defaultCastTimeTicks(KiAttackData.KiType type) {
      return switch (type) {
         case SMALL_BALL, LASER -> 0;
         case MEDIUM_BALL, DISK -> 30;
         case BARRAGE, SHIELD, AREA -> 40;
         case WAVE, BEAM -> 50;
         case GIANT_BALL, EXPLOSION -> 60;
      };
   }

   public TechniqueConfig.TechniqueTypeConfig getKiTypeConfig(KiAttackData.KiType type) {
      if (type == null) {
         return TechniqueConfig.TechniqueTypeConfig.defaults();
      } else {
         TechniqueConfig.TechniqueTypeConfig config = this.kiAttacks.get(type.name().toLowerCase());
         return config != null ? config : TechniqueConfig.TechniqueTypeConfig.defaults();
      }
   }

   public TechniqueConfig.StrikeAttackConfig getStrikeConfig(String strikeId) {
      if (strikeId != null && !strikeId.isEmpty()) {
         TechniqueConfig.StrikeAttackConfig config = this.strikeAttacks.get(strikeId.toLowerCase());
         return config != null ? config : TechniqueConfig.StrikeAttackConfig.defaults();
      } else {
         return TechniqueConfig.StrikeAttackConfig.defaults();
      }
   }

   @Generated
   public String getConfigVersion() {
      return this.configVersion;
   }

   @Generated
   public Map<String, TechniqueConfig.TechniqueTypeConfig> getKiAttacks() {
      return this.kiAttacks;
   }

   @Generated
   public Map<String, TechniqueConfig.StrikeAttackConfig> getStrikeAttacks() {
      return this.strikeAttacks;
   }

   @Generated
   public void setConfigVersion(String configVersion) {
      this.configVersion = configVersion;
   }

   public static class StrikeAttackConfig {
      private int minXPCost = 100;
      private int maxXPCost = -1;
      private double xpCostMultiplier = 1.0;
      private double xpGainMultiplier = 1.0;
      private int xpGainPerHit = 1;
      private int xpGainPerKill = 3;
      private double kiCostMultiplier = 1.0;
      private double damageMultiplier = 1.0;
      private int castTimeTicks = 0;
      private int cooldownTicks = 80;

      public static TechniqueConfig.StrikeAttackConfig defaults() {
         return new TechniqueConfig.StrikeAttackConfig();
      }

      @Generated
      public int getMinXPCost() {
         return this.minXPCost;
      }

      @Generated
      public int getMaxXPCost() {
         return this.maxXPCost;
      }

      @Generated
      public double getXpCostMultiplier() {
         return this.xpCostMultiplier;
      }

      @Generated
      public double getXpGainMultiplier() {
         return this.xpGainMultiplier;
      }

      @Generated
      public int getXpGainPerHit() {
         return this.xpGainPerHit;
      }

      @Generated
      public int getXpGainPerKill() {
         return this.xpGainPerKill;
      }

      @Generated
      public double getKiCostMultiplier() {
         return this.kiCostMultiplier;
      }

      @Generated
      public double getDamageMultiplier() {
         return this.damageMultiplier;
      }

      @Generated
      public int getCastTimeTicks() {
         return this.castTimeTicks;
      }

      @Generated
      public int getCooldownTicks() {
         return this.cooldownTicks;
      }

      @Generated
      public void setMinXPCost(int minXPCost) {
         this.minXPCost = minXPCost;
      }

      @Generated
      public void setMaxXPCost(int maxXPCost) {
         this.maxXPCost = maxXPCost;
      }

      @Generated
      public void setXpCostMultiplier(double xpCostMultiplier) {
         this.xpCostMultiplier = xpCostMultiplier;
      }

      @Generated
      public void setXpGainMultiplier(double xpGainMultiplier) {
         this.xpGainMultiplier = xpGainMultiplier;
      }

      @Generated
      public void setXpGainPerHit(int xpGainPerHit) {
         this.xpGainPerHit = xpGainPerHit;
      }

      @Generated
      public void setXpGainPerKill(int xpGainPerKill) {
         this.xpGainPerKill = xpGainPerKill;
      }

      @Generated
      public void setKiCostMultiplier(double kiCostMultiplier) {
         this.kiCostMultiplier = kiCostMultiplier;
      }

      @Generated
      public void setDamageMultiplier(double damageMultiplier) {
         this.damageMultiplier = damageMultiplier;
      }

      @Generated
      public void setCastTimeTicks(int castTimeTicks) {
         this.castTimeTicks = castTimeTicks;
      }

      @Generated
      public void setCooldownTicks(int cooldownTicks) {
         this.cooldownTicks = cooldownTicks;
      }
   }

   public static class TechniqueTypeConfig {
      private int minXPCost = 100;
      private int maxXPCost = -1;
      private double xpCostMultiplier = 1.0;
      private double xpGainMultiplier = 1.0;
      private int xpGainPerHit = 1;
      private int xpGainPerKill = 3;
      private double kiCostMultiplier = 1.0;
      private double damageMultiplier = 1.0;
      private double destructionMultiplier = 1.0;
      private int castTimeTicks = 30;

      public static TechniqueConfig.TechniqueTypeConfig defaults() {
         return new TechniqueConfig.TechniqueTypeConfig();
      }

      @Generated
      public int getMinXPCost() {
         return this.minXPCost;
      }

      @Generated
      public int getMaxXPCost() {
         return this.maxXPCost;
      }

      @Generated
      public double getXpCostMultiplier() {
         return this.xpCostMultiplier;
      }

      @Generated
      public double getXpGainMultiplier() {
         return this.xpGainMultiplier;
      }

      @Generated
      public int getXpGainPerHit() {
         return this.xpGainPerHit;
      }

      @Generated
      public int getXpGainPerKill() {
         return this.xpGainPerKill;
      }

      @Generated
      public double getKiCostMultiplier() {
         return this.kiCostMultiplier;
      }

      @Generated
      public double getDamageMultiplier() {
         return this.damageMultiplier;
      }

      @Generated
      public double getDestructionMultiplier() {
         return this.destructionMultiplier;
      }

      @Generated
      public int getCastTimeTicks() {
         return this.castTimeTicks;
      }

      @Generated
      public void setMinXPCost(int minXPCost) {
         this.minXPCost = minXPCost;
      }

      @Generated
      public void setMaxXPCost(int maxXPCost) {
         this.maxXPCost = maxXPCost;
      }

      @Generated
      public void setXpCostMultiplier(double xpCostMultiplier) {
         this.xpCostMultiplier = xpCostMultiplier;
      }

      @Generated
      public void setXpGainMultiplier(double xpGainMultiplier) {
         this.xpGainMultiplier = xpGainMultiplier;
      }

      @Generated
      public void setXpGainPerHit(int xpGainPerHit) {
         this.xpGainPerHit = xpGainPerHit;
      }

      @Generated
      public void setXpGainPerKill(int xpGainPerKill) {
         this.xpGainPerKill = xpGainPerKill;
      }

      @Generated
      public void setKiCostMultiplier(double kiCostMultiplier) {
         this.kiCostMultiplier = kiCostMultiplier;
      }

      @Generated
      public void setDamageMultiplier(double damageMultiplier) {
         this.damageMultiplier = damageMultiplier;
      }

      @Generated
      public void setDestructionMultiplier(double destructionMultiplier) {
         this.destructionMultiplier = destructionMultiplier;
      }

      @Generated
      public void setCastTimeTicks(int castTimeTicks) {
         this.castTimeTicks = castTimeTicks;
      }
   }
}
