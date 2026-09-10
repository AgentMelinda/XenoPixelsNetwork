package com.dragonminez.client.systems.taiyoken;

import com.dragonminez.Env;
import com.dragonminez.LogUtil;
import com.dragonminez.client.render.util.IrisCompat;
import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.mixin.client.PostChainAccessor;
import com.google.gson.JsonSyntaxException;
import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import java.io.IOException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EffectInstance;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.EventBusSubscriber.Bus;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent.Pre;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent.Stage;

@EventBusSubscriber(
   modid = "dragonminez",
   value = {Dist.CLIENT},
   bus = Bus.GAME
)
public class TaiyokenFlashHandler {
   private static final ResourceLocation EFFECT = ResourceLocation.fromNamespaceAndPath("dragonminez", "shaders/post/taiyoken_flash.json");
   private static PostChain shader = null;
   private static int lastWidth = 0;
   private static int lastHeight = 0;

   @SubscribeEvent
   public static void onClientTick(Pre event) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null) {
         TaiyokenBlindState.clear();
      } else if (!mc.isPaused()) {
         TaiyokenBlindState.tick();
      }
   }

   @SubscribeEvent
   public static void onRenderLevelStage(RenderLevelStageEvent event) {
      if (event.getStage() == Stage.AFTER_LEVEL) {
         if (TaiyokenBlindState.isActive()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null) {
               if (shader == null) {
                  try {
                     shader = new PostChain(mc.getTextureManager(), mc.getResourceManager(), mc.getMainRenderTarget(), EFFECT);
                     shader.resize(mc.getWindow().getWidth(), mc.getWindow().getHeight());
                     lastWidth = mc.getWindow().getWidth();
                     lastHeight = mc.getWindow().getHeight();
                  } catch (JsonSyntaxException | IOException var6) {
                     LogUtil.error(Env.CLIENT, "Failed to load taiyoken flash shader", var6);
                     TaiyokenBlindState.clear();
                     return;
                  }
               }

               if (mc.getWindow().getWidth() != lastWidth || mc.getWindow().getHeight() != lastHeight) {
                  shader.resize(mc.getWindow().getWidth(), mc.getWindow().getHeight());
                  lastWidth = mc.getWindow().getWidth();
                  lastHeight = mc.getWindow().getHeight();
               }

               float progress = TaiyokenBlindState.getProgress();
               float intensity = progress < 0.25F ? progress * 4.0F : 1.0F;
               boolean invert = Boolean.TRUE.equals(ConfigManager.getUserConfig().getTaiyokenInvertPalette());
               setUniform(shader, "intensity", intensity);
               setUniform(shader, "invert", invert ? 1.0F : 0.0F);
               boolean iris = IrisCompat.isShaderPackInUse(mc.level.getGameTime());
               RenderSystem.disableBlend();
               RenderSystem.disableDepthTest();
               RenderSystem.disableCull();
               if (iris) {
                  mc.getMainRenderTarget().bindWrite(false);
               }

               shader.process(event.getPartialTick().getGameTimeDeltaPartialTick(false));
               mc.getMainRenderTarget().bindWrite(false);
               RenderSystem.enableCull();
               RenderSystem.enableDepthTest();
            }
         }
      }
   }

   private static void setUniform(PostChain chain, String uniformName, float value) {
      for (PostPass pass : ((PostChainAccessor)chain).dragonminez$getPasses()) {
         EffectInstance effect = pass.getEffect();
         Uniform uniform = effect.getUniform(uniformName);
         if (uniform != null) {
            uniform.set(value);
         }
      }
   }
}
