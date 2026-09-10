package com.dragonminez.server.events.players;

import com.dragonminez.common.stats.StatsData;
import net.minecraft.server.level.ServerPlayer;

public interface IActionModeHandler {
   int handleActionCharge(ServerPlayer var1, StatsData var2);

   boolean performAction(ServerPlayer var1, StatsData var2);

   boolean canCharge(ServerPlayer var1, StatsData var2);
}
