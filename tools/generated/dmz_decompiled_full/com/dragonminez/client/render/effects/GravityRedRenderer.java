package com.dragonminez.client.render.effects;

import com.dragonminez.client.render.shader.ClientGravityState;
import com.dragonminez.client.render.shader.GravityRedManager;
import com.dragonminez.client.render.util.IrisCompat;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent.Stage;

@EventBusSubscriber(
   modid = "dragonminez",
   value = {Dist.CLIENT}
)
public final class GravityRedRenderer {
   private GravityRedRenderer() {
   }

   @SubscribeEvent(
      priority = EventPriority.LOWEST
   )
   public static void onRenderLevelStage(RenderLevelStageEvent event) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level != null && mc.player != null) {
         boolean iris = IrisCompat.isShaderPackInUse(mc.level.getGameTime());
         Stage targetStage = iris ? Stage.AFTER_LEVEL : Stage.AFTER_WEATHER;
         if (event.getStage() == targetStage) {
            float intensity = ClientGravityState.getShaderIntensity();
            if (intensity <= 0.0F) {
               GravityRedManager.reset();
            } else {
               if (iris) {
                  mc.getMainRenderTarget().bindWrite(false);
                  GravityRedManager.process(event.getPartialTick().getGameTimeDeltaPartialTick(false), intensity, false);
               } else {
                  GravityRedManager.process(event.getPartialTick().getGameTimeDeltaPartialTick(false), intensity);
               }
            }
         }
      }
   }
}
