package com.dragonminez.client.render.firstperson.dto;

import com.dragonminez.client.render.DMZPlayerRenderer;
import com.dragonminez.client.render.DMZRendererCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource.BufferSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.EventBusSubscriber.Bus;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent.Stage;

@EventBusSubscriber(
   modid = "dragonminez",
   bus = Bus.GAME,
   value = {Dist.CLIENT}
)
public class FirstPersonListener {
   @SubscribeEvent
   public static void onWorldRender(RenderLevelStageEvent event) {
      LocalPlayer player = Minecraft.getInstance().player;
      if (player != null) {
         if (event.getStage() == Stage.AFTER_ENTITIES) {
            boolean isFirstPerson = FirstPersonManager.shouldRenderFirstPerson(player);
            if (isFirstPerson) {
               DMZPlayerRenderer renderer = DMZRendererCache.getRenderer(player);
               if (renderer != null) {
                  if (renderer.shouldRender(player, event.getFrustum(), player.getX(), player.getY(), player.getZ())) {
                     BufferSource source = Minecraft.getInstance().renderBuffers().bufferSource();
                     renderer.render(
                        player,
                        player.getYRot(),
                        event.getPartialTick().getGameTimeDeltaPartialTick(false),
                        event.getPoseStack(),
                        source,
                        renderer.getPackedLightCoords(player, event.getPartialTick().getGameTimeDeltaPartialTick(false))
                     );
                     source.endBatch();
                  }
               }
            }
         }
      }
   }
}
