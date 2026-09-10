package com.dragonminez.client.gui.tooltip;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

public class TooltipDecor {
   public static final ResourceLocation DEFAULT_BORDERS = ResourceLocation.fromNamespaceAndPath("dragonminez", "textures/gui/tooltip_borders.png");
   public static int currentBorderStart = 0;
   public static int currentBorderEnd = 0;
   public static int currentBackgroundStart = 0;
   public static int currentBackgroundEnd = 0;
   public static boolean hasSpecialBorder = false;
   public static boolean forceCustomBorder = false;
   public static int forcedColor = 16777215;
   public static boolean hasItemBox = false;
   public static int lastTooltipX = 0;
   public static int lastTooltipY = 0;
   public static int lastTooltipW = 0;
   public static int lastTooltipH = 0;
   private static float shineTimer = 2.5F;
   public static float rotationTimer = 0.0F;

   public static void updateTimer(float deltaTime) {
      if (shineTimer > 0.0F) {
         shineTimer -= deltaTime;
      }

      rotationTimer += deltaTime;
      if (rotationTimer > 100.0F) {
         rotationTimer -= 100.0F;
      }
   }

   public static void resetTimer() {
      shineTimer = 2.5F;
   }

   public static void drawShadow(PoseStack poseStack, int x, int y, int width, int height) {
      int shadowColor = 1140850688;
      poseStack.pushPose();
      Matrix4f matrix = poseStack.last().pose();
      drawGradientRect(matrix, 390, x - 1, y + height + 4, x + width + 4, y + height + 5, shadowColor, shadowColor);
      drawGradientRect(matrix, 390, x + width + 4, y - 1, x + width + 5, y + height + 5, shadowColor, shadowColor);
      drawGradientRect(matrix, 390, x + width + 3, y + height + 3, x + width + 4, y + height + 4, shadowColor, shadowColor);
      drawGradientRect(matrix, 390, x, y + height + 5, x + width + 5, y + height + 6, shadowColor, shadowColor);
      drawGradientRect(matrix, 390, x + width + 5, y, x + width + 6, y + height + 5, shadowColor, shadowColor);
      poseStack.popPose();
   }

   public static void drawSeparator(PoseStack poseStack, int x, int y, int width, int color) {
      poseStack.pushPose();
      Matrix4f matrix = poseStack.last().pose();
      drawGradientRectHorizontal(matrix, 402, x, y, x + width / 2, y + 1, color & 16777215, color);
      drawGradientRectHorizontal(matrix, 402, x + width / 2, y, x + width, y + 1, color, color & 16777215);
      poseStack.popPose();
   }

   public static void drawBorder(PoseStack poseStack, int x, int y, int width, int height) {
      if (hasSpecialBorder) {
         poseStack.pushPose();
         Matrix4f matrix = poseStack.last().pose();
         if (shineTimer >= 0.5F && shineTimer <= 2.0F) {
            float interval = Mth.clamp(shineTimer - 0.5F, 0.0F, 1.0F);
            int alpha = (int)(153.0F * interval) << 24;
            int hMin = x - 3;
            int hMax = x + width + 3;
            int hInterval = (int)Mth.lerp(interval * interval, (float)hMax, (float)hMin);
            drawGradientRectHorizontal(matrix, 402, Math.max(hInterval - 36, hMin), y - 3, Math.min(hInterval, hMax), y - 3 + 1, 16777215, 16777215 | alpha);
            drawGradientRectHorizontal(matrix, 402, Math.max(hInterval, hMin), y - 3, Math.min(hInterval + 36, hMax), y - 3 + 1, 16777215 | alpha, 16777215);
         }

         if (shineTimer <= 1.0F) {
            float interval = Mth.clamp(shineTimer, 0.0F, 1.0F);
            int alpha = (int)(85.0F * interval) << 24;
            int vMin = y - 3 + 1;
            int vMax = y + height + 3 - 1;
            int vInterval = (int)Mth.lerp(interval * interval, (float)vMax, (float)vMin);
            drawGradientRect(matrix, 402, x - 3, Math.max(vInterval - 12, vMin), x - 3 + 1, Math.min(vInterval, vMax), 16777215, 16777215 | alpha);
            drawGradientRect(matrix, 402, x - 3, Math.max(vInterval, vMin), x - 3 + 1, Math.min(vInterval + 12, vMax), 16777215 | alpha, 16777215);
         }

         poseStack.popPose();
         RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
         RenderSystem.setShaderTexture(0, DEFAULT_BORDERS);
         poseStack.pushPose();
         poseStack.translate(0.0, 0.0, 410.0);
         int texW = 128;
         int texH = 128;
         int u = 0;
         int v = 0;
         blit(poseStack, x - 6, y - 6, 8, 8, (float)u, (float)v, 8, 8, texW, texH);
         blit(poseStack, x + width - 2, y - 6, 8, 8, (float)(56 + u), (float)v, 8, 8, texW, texH);
         blit(poseStack, x - 6, y + height - 2, 8, 8, (float)u, (float)(v + 8), 8, 8, texW, texH);
         blit(poseStack, x + width - 2, y + height - 2, 8, 8, (float)(56 + u), (float)(v + 8), 8, 8, texW, texH);
         if (width >= 48) {
            blit(poseStack, x + width / 2 - 24, y - 9, 48, 8, (float)(8 + u), (float)v, 48, 8, texW, texH);
            blit(poseStack, x + width / 2 - 24, y + height + 1, 48, 8, (float)(8 + u), (float)(v + 8), 48, 8, texW, texH);
         }

         poseStack.popPose();
      }
   }

   public static int combineARGB(int a, int r, int g, int b) {
      a = Mth.clamp(a, 0, 255);
      r = Mth.clamp(r, 0, 255);
      g = Mth.clamp(g, 0, 255);
      b = Mth.clamp(b, 0, 255);
      return a << 24 | r << 16 | g << 8 | b;
   }

   private static void blit(PoseStack poseStack, int x, int y, int width, int height, float uOffset, float vOffset, int uWidth, int vHeight, int texW, int texH) {
      Matrix4f matrix4f = poseStack.last().pose();
      float minU = uOffset / (float)texW;
      float maxU = (uOffset + (float)uWidth) / (float)texW;
      float minV = vOffset / (float)texH;
      float maxV = (vOffset + (float)vHeight) / (float)texH;
      RenderSystem.setShader(GameRenderer::getPositionTexShader);
      Tesselator tesselator = Tesselator.getInstance();
      BufferBuilder bufferbuilder = tesselator.begin(Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
      bufferbuilder.addVertex(matrix4f, (float)x, (float)y + (float)height, 0.0F).setUv(minU, maxV);
      bufferbuilder.addVertex(matrix4f, (float)x + (float)width, (float)y + (float)height, 0.0F).setUv(maxU, maxV);
      bufferbuilder.addVertex(matrix4f, (float)x + (float)width, (float)y, 0.0F).setUv(maxU, minV);
      bufferbuilder.addVertex(matrix4f, (float)x, (float)y, 0.0F).setUv(minU, minV);
      BufferUploader.drawWithShader(bufferbuilder.buildOrThrow());
   }

   public static void drawGradientRect(Matrix4f mat, int z, int left, int top, int right, int bottom, int startColor, int endColor) {
      float f = (float)(startColor >> 24 & 0xFF) / 255.0F;
      float f1 = (float)(startColor >> 16 & 0xFF) / 255.0F;
      float f2 = (float)(startColor >> 8 & 0xFF) / 255.0F;
      float f3 = (float)(startColor & 0xFF) / 255.0F;
      float f4 = (float)(endColor >> 24 & 0xFF) / 255.0F;
      float f5 = (float)(endColor >> 16 & 0xFF) / 255.0F;
      float f6 = (float)(endColor >> 8 & 0xFF) / 255.0F;
      float f7 = (float)(endColor & 0xFF) / 255.0F;
      RenderSystem.setShader(GameRenderer::getPositionColorShader);
      RenderSystem.enableBlend();
      RenderSystem.defaultBlendFunc();
      BufferBuilder buff = Tesselator.getInstance().begin(Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
      buff.addVertex(mat, (float)left, (float)top, (float)z).setColor(f1, f2, f3, f);
      buff.addVertex(mat, (float)left, (float)bottom, (float)z).setColor(f5, f6, f7, f4);
      buff.addVertex(mat, (float)right, (float)bottom, (float)z).setColor(f5, f6, f7, f4);
      buff.addVertex(mat, (float)right, (float)top, (float)z).setColor(f1, f2, f3, f);
      BufferUploader.drawWithShader(buff.buildOrThrow());
      RenderSystem.disableBlend();
   }

   public static void drawGradientRectHorizontal(Matrix4f mat, int z, int left, int top, int right, int bottom, int startColor, int endColor) {
      float f = (float)(startColor >> 24 & 0xFF) / 255.0F;
      float f1 = (float)(startColor >> 16 & 0xFF) / 255.0F;
      float f2 = (float)(startColor >> 8 & 0xFF) / 255.0F;
      float f3 = (float)(startColor & 0xFF) / 255.0F;
      float f4 = (float)(endColor >> 24 & 0xFF) / 255.0F;
      float f5 = (float)(endColor >> 16 & 0xFF) / 255.0F;
      float f6 = (float)(endColor >> 8 & 0xFF) / 255.0F;
      float f7 = (float)(endColor & 0xFF) / 255.0F;
      RenderSystem.setShader(GameRenderer::getPositionColorShader);
      RenderSystem.enableBlend();
      RenderSystem.defaultBlendFunc();
      BufferBuilder buff = Tesselator.getInstance().begin(Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
      buff.addVertex(mat, (float)right, (float)top, (float)z).setColor(f5, f6, f7, f4);
      buff.addVertex(mat, (float)left, (float)top, (float)z).setColor(f1, f2, f3, f);
      buff.addVertex(mat, (float)left, (float)bottom, (float)z).setColor(f1, f2, f3, f);
      buff.addVertex(mat, (float)right, (float)bottom, (float)z).setColor(f5, f6, f7, f4);
      BufferUploader.drawWithShader(buff.buildOrThrow());
      RenderSystem.disableBlend();
   }
}
