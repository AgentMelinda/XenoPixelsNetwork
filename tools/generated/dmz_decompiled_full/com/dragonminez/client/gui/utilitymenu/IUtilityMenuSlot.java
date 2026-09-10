package com.dragonminez.client.gui.utilitymenu;

import com.dragonminez.common.stats.StatsData;

public interface IUtilityMenuSlot {
   ButtonInfo render(StatsData var1);

   void handle(StatsData var1, boolean var2);

   default boolean hasRightClickAction(StatsData statsData) {
      return false;
   }
}
