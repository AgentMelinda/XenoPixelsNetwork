package com.dragonminez.client.render.layer;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceLocation;

public class BodyLayerFadeTracker {
   public static final float FADE_SPEED = 0.1F;
   private static final Map<Integer, Map<String, BodyLayerFadeTracker.Entry>> LAYERS = new ConcurrentHashMap<>();
   private static final Map<Integer, Long> LAST_UPDATE = new ConcurrentHashMap<>();
   private static final Map<Integer, Long> LAST_SEEN = new ConcurrentHashMap<>();

   private BodyLayerFadeTracker() {
   }

   public static List<BodyLayerFadeTracker.RenderEntry> update(int entityId, long gameTime, List<BodyLayerFadeTracker.FadingLayer> activeLayers) {
      Map<String, BodyLayerFadeTracker.Entry> layers = LAYERS.computeIfAbsent(entityId, k -> new LinkedHashMap<>());
      Long lastSeen = LAST_SEEN.get(entityId);
      boolean snap = lastSeen == null || gameTime - lastSeen > 2L;
      LAST_SEEN.put(entityId, gameTime);

      for (BodyLayerFadeTracker.Entry e : layers.values()) {
         e.active = false;
         e.target = 0.0F;
      }

      for (BodyLayerFadeTracker.FadingLayer l : activeLayers) {
         BodyLayerFadeTracker.Entry e = layers.get(l.id());
         if (e == null) {
            e = new BodyLayerFadeTracker.Entry();
            e.progress = snap ? l.target() : 0.0F;
            layers.put(l.id(), e);
         }

         e.texture = l.texture();
         e.color = l.color();
         e.target = l.target();
         e.active = true;
      }

      Long lastUpdate = LAST_UPDATE.get(entityId);
      boolean advance = lastUpdate == null || lastUpdate != gameTime;
      if (advance) {
         LAST_UPDATE.put(entityId, gameTime);
      }

      List<BodyLayerFadeTracker.RenderEntry> result = new ArrayList<>();
      Iterator<Map.Entry<String, BodyLayerFadeTracker.Entry>> it = layers.entrySet().iterator();

      while (it.hasNext()) {
         BodyLayerFadeTracker.Entry e = it.next().getValue();
         float target = e.target;
         if (snap) {
            e.progress = target;
         } else if (advance) {
            if (e.progress < target) {
               e.progress = Math.min(target, e.progress + 0.1F);
            } else {
               e.progress = Math.max(target, e.progress - 0.1F);
            }
         }

         if (e.progress <= 0.001F && !e.active) {
            it.remove();
         } else if (e.progress > 0.001F && e.texture != null) {
            result.add(new BodyLayerFadeTracker.RenderEntry(e.texture, e.color, e.progress));
         }
      }

      return result;
   }

   public static float getProgress(int entityId, String layerId) {
      Map<String, BodyLayerFadeTracker.Entry> layers = LAYERS.get(entityId);
      if (layers == null) {
         return 0.0F;
      } else {
         BodyLayerFadeTracker.Entry e = layers.get(layerId);
         return e != null ? e.progress : 0.0F;
      }
   }

   public static float[] getColor(int entityId, String layerId) {
      Map<String, BodyLayerFadeTracker.Entry> layers = LAYERS.get(entityId);
      if (layers == null) {
         return null;
      } else {
         BodyLayerFadeTracker.Entry e = layers.get(layerId);
         return e != null ? e.color : null;
      }
   }

   private static final class Entry {
      float progress;
      float target;
      ResourceLocation texture;
      float[] color;
      boolean active;
   }

   public static record FadingLayer(String id, ResourceLocation texture, float[] color, float target) {
      public FadingLayer(String id, ResourceLocation texture, float[] color) {
         this(id, texture, color, 1.0F);
      }
   }

   public static record RenderEntry(ResourceLocation texture, float[] color, float alpha) {
   }
}
