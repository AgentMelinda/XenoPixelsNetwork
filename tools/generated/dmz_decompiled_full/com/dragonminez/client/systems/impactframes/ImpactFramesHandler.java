package com.dragonminez.client.systems.impactframes;

import com.dragonminez.Env;
import com.dragonminez.LogUtil;
import com.dragonminez.client.render.util.IrisCompat;
import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.mixin.client.PostChainAccessor;
import com.google.gson.JsonSyntaxException;
import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Queue;
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
public class ImpactFramesHandler {
   private static final Queue<ImpactFrame> IMPACT_FRAMES = new ArrayDeque<>();
   private static ImpactFrame currentFrame = null;
   private static int currentTick = 0;
   private static PostChain impactFrameShader = null;
   private static int lastWidth = 0;
   private static int lastHeight = 0;

   public static void addImpactFrame(ImpactFrame frame) {
      if (ConfigManager.getUserConfig().isImpactFramesEnabled()) {
         IMPACT_FRAMES.offer(frame);
      }
   }

   @SubscribeEvent
   public static void onClientTick(Pre event) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level != null && !mc.isPaused()) {
         if (currentFrame == null) {
            if (!IMPACT_FRAMES.isEmpty()) {
               currentFrame = IMPACT_FRAMES.poll();
               currentTick = 0;
            }
         } else {
            currentTick++;
            if (currentTick >= currentFrame.getDuration()) {
               currentFrame = null;
               currentTick = 0;
               if (!IMPACT_FRAMES.isEmpty()) {
                  currentFrame = IMPACT_FRAMES.poll();
               }
            }
         }
      }
   }

   @SubscribeEvent
   public static void onRenderLevelStage(RenderLevelStageEvent event) {
      if (event.getStage() == Stage.AFTER_LEVEL) {
         if (currentFrame != null) {
            Minecraft mc = Minecraft.getInstance();
            if (impactFrameShader == null) {
               try {
                  impactFrameShader = new PostChain(
                     mc.getTextureManager(),
                     mc.getResourceManager(),
                     mc.getMainRenderTarget(),
                     ResourceLocation.fromNamespaceAndPath("dragonminez", "shaders/post/impact_frame.json")
                  );
                  impactFrameShader.resize(mc.getWindow().getWidth(), mc.getWindow().getHeight());
                  lastWidth = mc.getWindow().getWidth();
                  lastHeight = mc.getWindow().getHeight();
               } catch (JsonSyntaxException | IOException var3) {
                  LogUtil.error(Env.CLIENT, "Failed to load impact frame shader", var3);
                  currentFrame = null;
                  return;
               }
            }

            if (mc.getWindow().getWidth() != lastWidth || mc.getWindow().getHeight() != lastHeight) {
               impactFrameShader.resize(mc.getWindow().getWidth(), mc.getWindow().getHeight());
               lastWidth = mc.getWindow().getWidth();
               lastHeight = mc.getWindow().getHeight();
            }

            setUniform(impactFrameShader, "treshhold", currentFrame.getThreshold());
            setUniform(impactFrameShader, "treshholdLerp", currentFrame.getThresholdLerp());
            setUniform(impactFrameShader, "invert", currentFrame.isInverted() ? 1.0F : 0.0F);
            boolean iris = mc.level != null && IrisCompat.isShaderPackInUse(mc.level.getGameTime());
            RenderSystem.disableBlend();
            RenderSystem.disableDepthTest();
            RenderSystem.disableCull();
            if (iris) {
               mc.getMainRenderTarget().bindWrite(false);
            }

            impactFrameShader.process(event.getPartialTick().getGameTimeDeltaPartialTick(false));
            mc.getMainRenderTarget().bindWrite(false);
            RenderSystem.enableCull();
            RenderSystem.enableDepthTest();
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
