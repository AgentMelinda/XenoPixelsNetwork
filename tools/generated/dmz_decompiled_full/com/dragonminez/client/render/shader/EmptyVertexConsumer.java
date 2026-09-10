package com.dragonminez.client.render.shader;

import com.mojang.blaze3d.vertex.VertexConsumer;

public final class EmptyVertexConsumer implements VertexConsumer {
   public static final EmptyVertexConsumer INSTANCE = new EmptyVertexConsumer();

   private EmptyVertexConsumer() {
   }

   public VertexConsumer addVertex(float x, float y, float z) {
      return this;
   }

   public VertexConsumer setColor(int r, int g, int b, int a) {
      return this;
   }

   public VertexConsumer setUv(float u, float v) {
      return this;
   }

   public VertexConsumer setUv1(int u, int v) {
      return this;
   }

   public VertexConsumer setUv2(int u, int v) {
      return this;
   }

   public VertexConsumer setNormal(float x, float y, float z) {
      return this;
   }
}
