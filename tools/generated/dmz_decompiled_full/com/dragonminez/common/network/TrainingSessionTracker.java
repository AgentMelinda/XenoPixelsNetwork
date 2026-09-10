package com.dragonminez.common.network;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class TrainingSessionTracker {
   public static final long MIN_TICKS_PER_LEVEL = 8L;
   private static final Map<UUID, Long> SESSION_START = new ConcurrentHashMap<>();

   private TrainingSessionTracker() {
   }

   public static void begin(UUID uuid, long now) {
      SESSION_START.put(uuid, now);
   }

   public static void end(UUID uuid) {
      SESSION_START.remove(uuid);
   }

   public static int plausibleLevels(UUID uuid, long now) {
      Long start = SESSION_START.get(uuid);
      if (start == null) {
         return 0;
      } else {
         long elapsed = now - start;
         if (elapsed <= 0L) {
            return 0;
         } else {
            long levels = elapsed / 8L;
            return levels >= 2147483647L ? Integer.MAX_VALUE : (int)levels;
         }
      }
   }
}
