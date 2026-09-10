package com.dragonminez.client.render.layer;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AuraTintTracker {
   public static final float FADE_SPEED = 0.025F;
   public static final float DARK_TINT_THRESHOLD = 0.35F;
   public static final float DARK_TINT_FLOOR = 0.15F;
   private static final Map<Integer, Float> PROGRESS = new ConcurrentHashMap<>();
   private static final Map<Integer, Long> LAST_UPDATE = new ConcurrentHashMap<>();
   private static final Map<Integer, Long> LAST_SEEN = new ConcurrentHashMap<>();

   private AuraTintTracker() {
   }

   public static float update(int entityId, long gameTime, boolean shouldFadeIn) {
      float progress = PROGRESS.getOrDefault(entityId, 0.0F);
      Long lastSeen = LAST_SEEN.get(entityId);
      if (lastSeen == null || gameTime - lastSeen > 2L) {
         progress = 0.0F;
      }

      LAST_SEEN.put(entityId, gameTime);
      Long lastUpdate = LAST_UPDATE.get(entityId);
      if (lastUpdate == null || lastUpdate != gameTime) {
         if (shouldFadeIn) {
            progress = Math.min(1.0F, progress + 0.025F);
         } else {
            progress = Math.max(0.0F, progress - 0.025F);
         }

         PROGRESS.put(entityId, progress);
         LAST_UPDATE.put(entityId, gameTime);
      }

      return progress;
   }

   public static float get(int entityId) {
      return PROGRESS.getOrDefault(entityId, 0.0F);
   }

   public static float darkTintScale(float r, float g, float b) {
      float luminance = 0.2126F * r + 0.7152F * g + 0.0722F * b;
      float scale = luminance >= 0.35F ? 1.0F : luminance / 0.35F;
      return 0.15F + 0.85F * scale;
   }

   public static float darkTintScale(float[] rgb) {
      return rgb != null && rgb.length >= 3 ? darkTintScale(rgb[0], rgb[1], rgb[2]) : 1.0F;
   }
}
