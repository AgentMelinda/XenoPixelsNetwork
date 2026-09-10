package com.dragonminez.client.render.util;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexBuffer.Usage;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;

public class AuraMeshFactory {
   private static VertexBuffer billboardQuad;
   private static VertexBuffer groundQuad;
   private static final VertexBuffer[] billboardFrames = new VertexBuffer[4];
   private static final VertexBuffer[] groundFrames = new VertexBuffer[4];

   public static VertexBuffer getBillboardQuad() {
      if (billboardQuad == null) {
         billboardQuad = new VertexBuffer(Usage.STATIC);
         Tesselator tesselator = Tesselator.getInstance();
         BufferBuilder builder = tesselator.begin(Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
         builder.addVertex(-1.0F, -1.0F, 0.0F).setUv(0.0F, 1.0F);
         builder.addVertex(1.0F, -1.0F, 0.0F).setUv(1.0F, 1.0F);
         builder.addVertex(1.0F, 1.0F, 0.0F).setUv(1.0F, 0.0F);
         builder.addVertex(-1.0F, 1.0F, 0.0F).setUv(0.0F, 0.0F);
         billboardQuad.bind();
         billboardQuad.upload(builder.buildOrThrow());
         VertexBuffer.unbind();
      }

      return billboardQuad;
   }

   public static VertexBuffer getGroundQuad() {
      if (groundQuad == null) {
         groundQuad = new VertexBuffer(Usage.STATIC);
         Tesselator tesselator = Tesselator.getInstance();
         BufferBuilder builder = tesselator.begin(Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
         builder.addVertex(-1.0F, 0.0F, -1.0F).setUv(0.0F, 0.0F);
         builder.addVertex(-1.0F, 0.0F, 1.0F).setUv(0.0F, 1.0F);
         builder.addVertex(1.0F, 0.0F, 1.0F).setUv(1.0F, 1.0F);
         builder.addVertex(1.0F, 0.0F, -1.0F).setUv(1.0F, 0.0F);
         groundQuad.bind();
         groundQuad.upload(builder.buildOrThrow());
         VertexBuffer.unbind();
      }

      return groundQuad;
   }

   public static VertexBuffer getBillboardQuadFrame(int frame) {
      frame = (frame % 4 + 4) % 4;
      if (billboardFrames[frame] == null) {
         float u0 = (float)frame / 4.0F;
         float u1 = (float)(frame + 1) / 4.0F;
         VertexBuffer vb = new VertexBuffer(Usage.STATIC);
         BufferBuilder builder = Tesselator.getInstance().begin(Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
         builder.addVertex(-1.0F, -1.0F, 0.0F).setUv(u0, 1.0F);
         builder.addVertex(1.0F, -1.0F, 0.0F).setUv(u1, 1.0F);
         builder.addVertex(1.0F, 1.0F, 0.0F).setUv(u1, 0.0F);
         builder.addVertex(-1.0F, 1.0F, 0.0F).setUv(u0, 0.0F);
         vb.bind();
         vb.upload(builder.buildOrThrow());
         VertexBuffer.unbind();
         billboardFrames[frame] = vb;
      }

      return billboardFrames[frame];
   }

   public static VertexBuffer getGroundQuadFrame(int frame) {
      frame = (frame % 4 + 4) % 4;
      if (groundFrames[frame] == null) {
         float u0 = (float)frame / 4.0F;
         float u1 = (float)(frame + 1) / 4.0F;
         VertexBuffer vb = new VertexBuffer(Usage.STATIC);
         BufferBuilder builder = Tesselator.getInstance().begin(Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
         builder.addVertex(-1.0F, 0.0F, -1.0F).setUv(u0, 0.0F);
         builder.addVertex(-1.0F, 0.0F, 1.0F).setUv(u0, 1.0F);
         builder.addVertex(1.0F, 0.0F, 1.0F).setUv(u1, 1.0F);
         builder.addVertex(1.0F, 0.0F, -1.0F).setUv(u1, 0.0F);
         vb.bind();
         vb.upload(builder.buildOrThrow());
         VertexBuffer.unbind();
         groundFrames[frame] = vb;
      }

      return groundFrames[frame];
   }
}
