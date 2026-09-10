package com.dragonminez.common.quest;

import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import net.minecraft.world.entity.player.Player;

public final class QuestUnlocks {
   public static final String SCOUTER_CALIBRATION = "bulma_scouter_calibration";
   public static final String SCOUTER_BIOSCAN = "bulma_scouter_bioscan";
   public static final String SCOUTER_THREAT_DB = "bulma_scouter_threat_db";
   public static final String GRAVITY_MK2 = "bulma_gravity_mk2";
   public static final String GRAVITY_MK3 = "bulma_gravity_mk3";

   private QuestUnlocks() {
   }

   public static boolean isCompleted(Player player, String questId) {
      if (player == null) {
         return false;
      } else {
         StatsData data = StatsProvider.get(StatsCapability.INSTANCE, player).orElse(null);
         return data != null && data.getPlayerQuestData().isQuestCompleted(questId);
      }
   }
}
