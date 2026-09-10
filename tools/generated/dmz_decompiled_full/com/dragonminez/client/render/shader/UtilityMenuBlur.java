package com.dragonminez.client.render.shader;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.ResourceLocation;

public final class UtilityMenuBlur {
   public static boolean ENABLED = true;
   private static final ResourceLocation EFFECT = ResourceLocation.fromNamespaceAndPath("dragonminez", "shaders/post/utility_blur.json");
   private static boolean loadedByUs = false;

   private UtilityMenuBlur() {
   }

   public static void start() {
      if (ENABLED) {
         Minecraft mc = Minecraft.getInstance();
         if (mc.gameRenderer != null) {
            PostChain current = mc.gameRenderer.currentEffect();
            if (current != null) {
               loadedByUs = EFFECT.toString().equals(current.getName());
            } else {
               try {
                  mc.gameRenderer.loadEffect(EFFECT);
                  PostChain now = mc.gameRenderer.currentEffect();
                  loadedByUs = now != null && EFFECT.toString().equals(now.getName());
               } catch (Exception var3) {
                  loadedByUs = false;
               }
            }
         }
      }
   }

   public static void stop() {
      Minecraft mc = Minecraft.getInstance();
      if (mc.gameRenderer == null) {
         loadedByUs = false;
      } else {
         PostChain current = mc.gameRenderer.currentEffect();
         if (current != null && EFFECT.toString().equals(current.getName())) {
            mc.gameRenderer.shutdownEffect();
         }

         loadedByUs = false;
      }
   }
}
