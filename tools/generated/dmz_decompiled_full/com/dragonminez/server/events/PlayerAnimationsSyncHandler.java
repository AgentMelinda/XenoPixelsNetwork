package com.dragonminez.server.events;

import com.dragonminez.common.network.NetworkHandler;
import com.dragonminez.common.network.S2C.PlayerAnimationsSync;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent.Post;

@EventBusSubscriber(
   modid = "dragonminez"
)
public class PlayerAnimationsSyncHandler {
   private static final Map<UUID, Boolean> lastFlyingState = new HashMap<>();

   @SubscribeEvent
   public static void onPlayerTick(Post event) {
      if (event.getEntity() instanceof ServerPlayer) {
         if (event.getEntity().level().isClientSide()) {
            Player player = event.getEntity();
            UUID uuid = player.getUUID();
            boolean isCurrentlyFlying = player.getAbilities().flying || player.isFallFlying();
            Boolean lastState = lastFlyingState.get(uuid);
            if (lastState == null || lastState != isCurrentlyFlying) {
               lastFlyingState.put(uuid, isCurrentlyFlying);
               NetworkHandler.sendToTrackingEntityAndSelf(new PlayerAnimationsSync(uuid, isCurrentlyFlying), player);
            }
         }
      }
   }
}
