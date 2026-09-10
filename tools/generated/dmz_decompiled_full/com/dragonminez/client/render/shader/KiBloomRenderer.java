package com.dragonminez.client.render.shader;

import com.dragonminez.client.render.util.PlayerEffectQueue;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

public final class KiBloomRenderer {
   private static final ResourceLocation KI_BLOOM_EFFECT = ResourceLocation.fromNamespaceAndPath("dragonminez", "shaders/post/ki_bloom.json");
   private static final String KI_SCENE_TARGET = "ki_scene";
   private static PostChain chain;
   private static int chainWidth = -1;
   private static int chainHeight = -1;

   private KiBloomRenderer() {
   }

   public static void render(List<PlayerEffectQueue.KiRenderTask> tasks, PoseStack poseStack, Matrix4f projectionMatrix, float partialTick) {
      if (!tasks.isEmpty()) {
         Minecraft mc = Minecraft.getInstance();
         if (mc.gameRenderer != null) {
            RenderTarget main = mc.getMainRenderTarget();
            PostChain bloomChain = ensureChain(mc, main);
            if (bloomChain != null) {
               RenderTarget kiTarget = bloomChain.getTempTarget("ki_scene");
               if (kiTarget != null) {
                  kiTarget.setClearColor(0.0F, 0.0F, 0.0F, 0.0F);
                  kiTarget.clear(Minecraft.ON_OSX);
                  kiTarget.copyDepthFrom(main);
                  kiTarget.bindWrite(true);
                  RenderSystem.depthMask(false);
                  RenderSystem.enableBlend();
                  RenderSystem.defaultBlendFunc();
                  RenderSystem.disableCull();
                  RenderSystem.enableDepthTest();
                  if (DMZShaders.ki3dShader != null) {
                     DMZShaders.ki3dShader.safeGetUniform("bloomMode").set(1.0F);
                  }

                  for (PlayerEffectQueue.KiRenderTask task : tasks) {
                     task.render(poseStack, projectionMatrix);
                  }

                  if (DMZShaders.ki3dShader != null) {
                     DMZShaders.ki3dShader.safeGetUniform("bloomMode").set(0.0F);
                  }

                  RenderSystem.enableCull();
                  RenderSystem.disableBlend();
                  main.bindWrite(false);
                  bloomChain.process(partialTick);
                  main.bindWrite(false);
                  RenderSystem.depthMask(true);
               }
            }
         }
      }
   }

   private static PostChain ensureChain(Minecraft mc, RenderTarget main) {
      if (chain != null && chainWidth == main.width && chainHeight == main.height) {
         return chain;
      } else {
         try {
            if (chain != null) {
               chain.close();
            }

            chain = new PostChain(mc.getTextureManager(), mc.getResourceManager(), main, KI_BLOOM_EFFECT);
            chain.resize(main.width, main.height);
            chainWidth = main.width;
            chainHeight = main.height;
         } catch (Exception var3) {
            chain = null;
            chainWidth = -1;
            chainHeight = -1;
         }

         return chain;
      }
   }

   public static void reset() {
      if (chain != null) {
         chain.close();
         chain = null;
         chainWidth = -1;
         chainHeight = -1;
      }
   }
}
