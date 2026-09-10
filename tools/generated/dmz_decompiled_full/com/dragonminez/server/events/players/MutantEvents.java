package com.dragonminez.server.events.players;

import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.config.GeneralServerConfig;
import com.dragonminez.server.util.MutantManager;
import com.dragonminez.server.world.data.MutantSavedData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.EventBusSubscriber.Bus;
import net.neoforged.neoforge.event.tick.ServerTickEvent.Post;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

@EventBusSubscriber(
   modid = "dragonminez",
   bus = Bus.GAME
)
public class MutantEvents {
   private static final int CHECK_INTERVAL_TICKS = 100;

   @SubscribeEvent
   public static void onServerTick(Post event) {
      MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
      if (server != null) {
         if (server.getTickCount() % 100 == 0) {
            GeneralServerConfig.MutantConfig cfg = ConfigManager.getServerConfig() != null ? ConfigManager.getServerConfig().getMutant() : null;
            if (cfg != null && cfg.getEnabled()) {
               ServerLevel overworld = server.getLevel(Level.OVERWORLD);
               if (overworld != null) {
                  MutantSavedData saved = MutantSavedData.get(server);
                  long now = overworld.getGameTime();
                  long intervalTicks = (long)cfg.getRollIntervalMinutes().intValue() * 60L * 20L;
                  if (saved.getNextRollTick() < 0L) {
                     saved.setNextRollTick(now + intervalTicks);
                  } else {
                     if (now >= saved.getNextRollTick()) {
                        MutantManager.runLottery(server);
                        saved.setNextRollTick(now + intervalTicks);
                     }
                  }
               }
            }
         }
      }
   }
}
