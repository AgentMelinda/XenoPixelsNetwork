package com.dragonminez.server.world.dimension;

import com.dragonminez.client.render.DMZCloudsRenderer;
import com.dragonminez.client.util.ClientStateHelper;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.DimensionSpecialEffects.SkyType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RegisterDimensionSpecialEffectsEvent;
import org.joml.Matrix4f;

public class CustomSpecialEffects extends DimensionSpecialEffects {
   public static final ResourceLocation NAMEK_EFFECTS = ResourceLocation.fromNamespaceAndPath("dragonminez", "namek_effects");
   public static final ResourceLocation OTHERWORLD_EFFECTS = ResourceLocation.fromNamespaceAndPath("dragonminez", "otherworld_effects");
   public static final ResourceLocation HTC_EFFECT = ResourceLocation.fromNamespaceAndPath("dragonminez", "htc_effects");
   public static final ResourceLocation SACREDKAI_EFFECTS = ResourceLocation.fromNamespaceAndPath("dragonminez", "sacredkai_effects");
   final DMZCloudsRenderer cloudRenderer = new DMZCloudsRenderer();

   public CustomSpecialEffects(float cloudLevel, boolean hasGround, SkyType skyType, boolean forceBrightLightMap, boolean constantAmbientLight) {
      super(cloudLevel, hasGround, skyType, forceBrightLightMap, constantAmbientLight);
   }

   public Vec3 getBrightnessDependentFogColor(Vec3 biomeFogColor, float daylight) {
      return biomeFogColor.multiply((double)(daylight * 0.94F + 0.06F), (double)(daylight * 0.94F + 0.06F), (double)(daylight * 0.91F + 0.09F));
   }

   public boolean isFoggyAt(int x, int y) {
      return false;
   }

   public boolean renderClouds(
      ClientLevel level,
      int ticks,
      float partialTick,
      PoseStack poseStack,
      double camX,
      double camY,
      double camZ,
      Matrix4f modelViewMatrix,
      Matrix4f projectionMatrix
   ) {
      return false;
   }

   public boolean renderSky(
      ClientLevel level, int ticks, float partialTick, Matrix4f modelViewMatrix, Camera camera, Matrix4f projectionMatrix, boolean isFoggy, Runnable setupFog
   ) {
      return false;
   }

   public boolean renderSnowAndRain(ClientLevel level, int ticks, float partialTick, LightTexture lightTexture, double camX, double camY, double camZ) {
      return false;
   }

   public static void registerSpecialEffects(RegisterDimensionSpecialEffectsEvent event) {
      event.register(NAMEK_EFFECTS, new CustomSpecialEffects.NamekEffects());
      event.register(OTHERWORLD_EFFECTS, new CustomSpecialEffects.OtherWorldEffects());
      event.register(HTC_EFFECT, new CustomSpecialEffects.HTCEffects());
      event.register(SACREDKAI_EFFECTS, new CustomSpecialEffects.SacredKaiEffects());
   }

   public static class HTCEffects extends CustomSpecialEffects {
      public HTCEffects() {
         super(192.0F, true, SkyType.NORMAL, false, false);
      }

      @Override
      public Vec3 getBrightnessDependentFogColor(Vec3 biomeFogColor, float daylight) {
         return biomeFogColor.multiply((double)(daylight * 0.94F + 0.06F), (double)(daylight * 0.94F + 0.06F), (double)(daylight * 0.91F + 0.09F));
      }

      @Override
      public boolean isFoggyAt(int x, int y) {
         return Math.abs(x) > 64;
      }

      @Override
      public boolean renderClouds(
         ClientLevel level,
         int ticks,
         float partialTick,
         PoseStack poseStack,
         double camX,
         double camY,
         double camZ,
         Matrix4f modelViewMatrix,
         Matrix4f projectionMatrix
      ) {
         return true;
      }

      @Override
      public boolean renderSky(
         ClientLevel level,
         int ticks,
         float partialTick,
         Matrix4f modelViewMatrix,
         Camera camera,
         Matrix4f projectionMatrix,
         boolean isFoggy,
         Runnable setupFog
      ) {
         return true;
      }

      @Override
      public boolean renderSnowAndRain(ClientLevel level, int ticks, float partialTick, LightTexture lightTexture, double camX, double camY, double camZ) {
         return true;
      }
   }

   public static class NamekEffects extends CustomSpecialEffects {
      public NamekEffects() {
         super(192.0F, true, SkyType.NORMAL, false, false);
      }

      @Override
      public Vec3 getBrightnessDependentFogColor(Vec3 biomeFogColor, float daylight) {
         return ClientStateHelper.isPorungaActive
            ? new Vec3(0.02, 0.02, 0.02)
            : biomeFogColor.multiply((double)(daylight * 0.94F + 0.06F), (double)(daylight * 0.94F + 0.06F), (double)(daylight * 0.91F + 0.09F));
      }

      @Override
      public boolean isFoggyAt(int x, int y) {
         return ClientStateHelper.isPorungaActive;
      }

      @Override
      public boolean renderSky(
         ClientLevel level,
         int ticks,
         float partialTick,
         Matrix4f modelViewMatrix,
         Camera camera,
         Matrix4f projectionMatrix,
         boolean isFoggy,
         Runnable setupFog
      ) {
         if (!ClientStateHelper.isPorungaActive) {
            return false;
         } else {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.depthMask(false);
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            Tesselator tesselator = Tesselator.getInstance();
            PoseStack poseStack = new PoseStack();
            poseStack.mulPose(modelViewMatrix);
            float r = 0.05F;
            float g = 0.05F;
            float b = 0.05F;
            float a = 1.0F;

            for (int i = 0; i < 6; i++) {
               poseStack.pushPose();
               if (i == 1) {
                  poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));
               }

               if (i == 2) {
                  poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F));
               }

               if (i == 3) {
                  poseStack.mulPose(Axis.XP.rotationDegrees(180.0F));
               }

               if (i == 4) {
                  poseStack.mulPose(Axis.ZP.rotationDegrees(90.0F));
               }

               if (i == 5) {
                  poseStack.mulPose(Axis.ZP.rotationDegrees(-90.0F));
               }

               Matrix4f matrix4f = poseStack.last().pose();
               BufferBuilder bufferbuilder = tesselator.begin(Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
               bufferbuilder.addVertex(matrix4f, -100.0F, -100.0F, -100.0F).setColor(r, g, b, a);
               bufferbuilder.addVertex(matrix4f, -100.0F, -100.0F, 100.0F).setColor(r, g, b, a);
               bufferbuilder.addVertex(matrix4f, 100.0F, -100.0F, 100.0F).setColor(r, g, b, a);
               bufferbuilder.addVertex(matrix4f, 100.0F, -100.0F, -100.0F).setColor(r, g, b, a);
               BufferUploader.drawWithShader(bufferbuilder.buildOrThrow());
               poseStack.popPose();
            }

            RenderSystem.depthMask(true);
            RenderSystem.disableBlend();
            return true;
         }
      }

      @Override
      public boolean renderClouds(
         ClientLevel level,
         int ticks,
         float partialTick,
         PoseStack poseStack,
         double camX,
         double camY,
         double camZ,
         Matrix4f modelViewMatrix,
         Matrix4f projectionMatrix
      ) {
         Vec3 namekGreen = new Vec3(0.659, 0.922, 0.443);
         this.cloudRenderer.render(poseStack, modelViewMatrix, projectionMatrix, partialTick, camX, camY, camZ, namekGreen);
         return true;
      }

      @Override
      public boolean renderSnowAndRain(ClientLevel level, int ticks, float partialTick, LightTexture lightTexture, double camX, double camY, double camZ) {
         return true;
      }
   }

   public static class OtherWorldEffects extends CustomSpecialEffects {
      public OtherWorldEffects() {
         super(192.0F, false, SkyType.NORMAL, false, false);
      }

      @Override
      public Vec3 getBrightnessDependentFogColor(Vec3 biomeFogColor, float daylight) {
         return biomeFogColor.multiply((double)(daylight * 0.94F + 0.06F), (double)(daylight * 0.94F + 0.06F), (double)(daylight * 0.91F + 0.09F));
      }

      @Override
      public boolean isFoggyAt(int x, int y) {
         return Math.abs(x) > 64;
      }

      @Override
      public boolean renderClouds(
         ClientLevel level,
         int ticks,
         float partialTick,
         PoseStack poseStack,
         double camX,
         double camY,
         double camZ,
         Matrix4f modelViewMatrix,
         Matrix4f projectionMatrix
      ) {
         Vec3 namekYellow = new Vec3(0.929, 0.929, 0.157);
         this.cloudRenderer.render(poseStack, modelViewMatrix, projectionMatrix, partialTick, camX, camY, camZ, namekYellow);
         return true;
      }

      @Override
      public boolean renderSky(
         ClientLevel level,
         int ticks,
         float partialTick,
         Matrix4f modelViewMatrix,
         Camera camera,
         Matrix4f projectionMatrix,
         boolean isFoggy,
         Runnable setupFog
      ) {
         return true;
      }

      @Override
      public boolean renderSnowAndRain(ClientLevel level, int ticks, float partialTick, LightTexture lightTexture, double camX, double camY, double camZ) {
         return true;
      }
   }

   public static class SacredKaiEffects extends CustomSpecialEffects {
      public SacredKaiEffects() {
         super(192.0F, true, SkyType.NORMAL, false, false);
      }
   }
}
