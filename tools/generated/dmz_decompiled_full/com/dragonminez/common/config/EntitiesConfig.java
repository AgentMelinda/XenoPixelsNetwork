package com.dragonminez.common.config;

import java.util.HashMap;
import java.util.Map;
import lombok.Generated;
import net.minecraft.util.Mth;

public class EntitiesConfig {
   public static final String CURRENT_VERSION = "2.1.3";
   private String configVersion;
   private Map<String, EntitiesConfig.EntityStats> defaultEntityStats = new HashMap<>();
   private EntitiesConfig.TransformSettings transformDefaults = new EntitiesConfig.TransformSettings();

   @Generated
   public String getConfigVersion() {
      return this.configVersion;
   }

   @Generated
   public Map<String, EntitiesConfig.EntityStats> getDefaultEntityStats() {
      return this.defaultEntityStats;
   }

   @Generated
   public EntitiesConfig.TransformSettings getTransformDefaults() {
      return this.transformDefaults;
   }

   @Generated
   public void setConfigVersion(String configVersion) {
      this.configVersion = configVersion;
   }

   public static class EntityStats {
      private Double health;
      private Double meleeDamage;
      private Double kiDamage;

      public Double getHealth() {
         return this.health != null ? Math.max(1.0, this.health) : null;
      }

      public Double getMeleeDamage() {
         return this.meleeDamage != null ? Math.max(1.0, this.meleeDamage) : null;
      }

      public Double getKiDamage() {
         return this.kiDamage != null ? Math.max(1.0, this.kiDamage) : null;
      }

      @Generated
      public void setHealth(Double health) {
         this.health = health;
      }

      @Generated
      public void setMeleeDamage(Double meleeDamage) {
         this.meleeDamage = meleeDamage;
      }

      @Generated
      public void setKiDamage(Double kiDamage) {
         this.kiDamage = kiDamage;
      }
   }

   public static class TransformSettings {
      private Double healthMultiplier;
      private Double meleeMultiplier;
      private Double kiMultiplier;
      private Double triggerHealthPercent;

      public double healthMultiplierOr(double fallback) {
         return this.healthMultiplier != null ? Math.max(0.1, this.healthMultiplier) : fallback;
      }

      public double meleeMultiplierOr(double fallback) {
         return this.meleeMultiplier != null ? Math.max(0.1, this.meleeMultiplier) : fallback;
      }

      public double kiMultiplierOr(double fallback) {
         return this.kiMultiplier != null ? Math.max(0.1, this.kiMultiplier) : fallback;
      }

      public double triggerHealthFractionOr(double fallback) {
         return this.triggerHealthPercent != null ? Mth.clamp(this.triggerHealthPercent, 0.0, 1.0) : fallback;
      }

      @Generated
      public void setHealthMultiplier(Double healthMultiplier) {
         this.healthMultiplier = healthMultiplier;
      }

      @Generated
      public void setMeleeMultiplier(Double meleeMultiplier) {
         this.meleeMultiplier = meleeMultiplier;
      }

      @Generated
      public void setKiMultiplier(Double kiMultiplier) {
         this.kiMultiplier = kiMultiplier;
      }

      @Generated
      public void setTriggerHealthPercent(Double triggerHealthPercent) {
         this.triggerHealthPercent = triggerHealthPercent;
      }
   }
}
