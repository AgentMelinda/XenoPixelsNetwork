package com.dragonminez.client.events;

import com.dragonminez.client.clash.BeamClashCinematicCamera;
import com.dragonminez.client.clash.ClientBeamClashState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.EventBusSubscriber.Bus;
import net.neoforged.neoforge.client.event.ClientTickEvent.Post;
import net.neoforged.neoforge.client.event.ViewportEvent.ComputeFov;

@EventBusSubscriber(
   modid = "dragonminez",
   bus = Bus.GAME,
   value = {Dist.CLIENT}
)
public class BeamClashClientEvents {
   @SubscribeEvent
   public static void onClientTick(Post event) {
      if (ClientBeamClashState.isActive() && !BeamClashCinematicCamera.isActive()) {
         BeamClashCinematicCamera.activate();
      } else if (!ClientBeamClashState.isActive() && BeamClashCinematicCamera.isActive()) {
         BeamClashCinematicCamera.deactivate();
      }

      BeamClashCinematicCamera.tickFov();
   }

   @SubscribeEvent
   public static void onComputeFov(ComputeFov event) {
      event.setFOV(BeamClashCinematicCamera.applyFov(event.getFOV()));
   }
}
