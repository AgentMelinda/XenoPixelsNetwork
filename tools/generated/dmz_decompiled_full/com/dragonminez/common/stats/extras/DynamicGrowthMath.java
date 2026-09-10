package com.dragonminez.common.stats.extras;

import com.dragonminez.common.config.ConfigManager;

public final class DynamicGrowthMath {
   private DynamicGrowthMath() {
   }

   public static int requiredXp(int currentStat) {
      return !ConfigManager.getServerConfig().getDynamicGrowth().isPracticeCurveEnabled()
         ? 100
         : 70 + currentStat * 2 + (int)Math.floor(Math.pow((double)currentStat, 1.35));
   }
}
