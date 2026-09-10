package com.dragonminez.server.world.raid;

import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent.Post;

@EventBusSubscriber(
   modid = "dragonminez"
)
public class RaidEvents {
   @SubscribeEvent
   public static void onLevelTick(Post event) {
      if (!event.getLevel().isClientSide) {
         if (event.getLevel() instanceof ServerLevel level) {
            RaidManager.tick(level);
         }
      }
   }
}
