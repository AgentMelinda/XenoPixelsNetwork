package com.dragonminez.common.quest;

import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.config.GeneralServerConfig;

public enum Difficulty {
   EASY,
   NORMAL,
   HARD;

   public static Difficulty fromName(String name) {
      if (name != null && !name.isBlank()) {
         try {
            return valueOf(name.toUpperCase());
         } catch (IllegalArgumentException var2) {
            return NORMAL;
         }
      } else {
         return NORMAL;
      }
   }

   public static Difficulty fromOrdinal(int ordinal) {
      Difficulty[] values = values();
      return ordinal >= 0 && ordinal < values.length ? values[ordinal] : NORMAL;
   }

   private static GeneralServerConfig.GameplayConfig gameplay() {
      return ConfigManager.getServerConfig().getGameplay();
   }

   public double hpMultiplier() {
      return switch (this) {
         case EASY -> gameplay().getEasyModeHPMultiplier();
         case NORMAL -> 1.0;
         case HARD -> gameplay().getHardModeHPMultiplier();
      };
   }

   public double damageMultiplier() {
      return switch (this) {
         case EASY -> gameplay().getEasyModeDamageMultiplier();
         case NORMAL -> 1.0;
         case HARD -> gameplay().getHardModeDamageMultiplier();
      };
   }

   public double tpMultiplier() {
      return switch (this) {
         case EASY -> gameplay().getEasyModeTPMultiplier();
         case NORMAL -> 1.0;
         case HARD -> gameplay().getHardModeTPMultiplier();
      };
   }

   public double questRewardMultiplier() {
      return switch (this) {
         case EASY -> gameplay().getEasyModeQuestRewardMultiplier();
         case NORMAL -> 1.0;
         case HARD -> gameplay().getHardModeQuestRewardMultiplier();
      };
   }

   public int aiTierId() {
      return switch (this) {
         case EASY -> 1;
         case NORMAL -> 2;
         case HARD -> 3;
      };
   }
}
