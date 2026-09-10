package com.dragonminez.server.events.players;

import com.dragonminez.common.stats.StatsData;
import net.minecraft.server.level.ServerPlayer;

public interface IStatusEffectHandler {
   void handleStatusEffects(ServerPlayer var1, StatsData var2);

   void onPlayerTick(ServerPlayer var1, StatsData var2);

   void onPlayerSecond(ServerPlayer var1, StatsData var2);
}
