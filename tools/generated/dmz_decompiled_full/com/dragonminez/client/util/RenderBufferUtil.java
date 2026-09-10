package com.dragonminez.client.util;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import net.minecraft.client.DeltaTracker;
import org.joml.Matrix4f;

public final class RenderBufferUtil {
   private RenderBufferUtil() {
   }

   public static void drawTexturedQuad(Matrix4f matrix, float size) {
      drawTexturedQuad(matrix, -size, size, size, -size, 0.0F, 0.0F, 1.0F, 1.0F);
   }

   public static void drawTexturedQuad(Matrix4f matrix, float x0, float y0, float x1, float y1, float u0, float v0, float u1, float v1) {
      Tesselator tesselator = Tesselator.getInstance();
      BufferBuilder builder = tesselator.begin(Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
      builder.addVertex(matrix, x0, y0, 0.0F).setUv(u0, v1);
      builder.addVertex(matrix, x1, y0, 0.0F).setUv(u1, v1);
      builder.addVertex(matrix, x1, y1, 0.0F).setUv(u1, v0);
      builder.addVertex(matrix, x0, y1, 0.0F).setUv(u0, v0);
      MeshData mesh = builder.buildOrThrow();
      BufferUploader.drawWithShader(mesh);
   }

   public static void drawColoredQuad(Matrix4f matrix, float x0, float y0, float x1, float y1, int argb) {
      Tesselator tesselator = Tesselator.getInstance();
      BufferBuilder builder = tesselator.begin(Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
      builder.addVertex(matrix, x0, y0, 0.0F).setColor(argb);
      builder.addVertex(matrix, x1, y0, 0.0F).setColor(argb);
      builder.addVertex(matrix, x1, y1, 0.0F).setColor(argb);
      builder.addVertex(matrix, x0, y1, 0.0F).setColor(argb);
      MeshData mesh = builder.buildOrThrow();
      BufferUploader.drawWithShader(mesh);
   }

   public static float partialTick(DeltaTracker tracker) {
      return tracker.getGameTimeDeltaPartialTick(false);
   }
}
