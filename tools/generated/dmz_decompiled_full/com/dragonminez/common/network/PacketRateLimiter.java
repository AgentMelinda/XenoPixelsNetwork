package com.dragonminez.common.network;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PacketRateLimiter {
   private static final Map<String, Long> LAST_ACCEPTED = new ConcurrentHashMap<>();

   private PacketRateLimiter() {
   }

   public static boolean allow(UUID uuid, String key, long now, long minIntervalTicks) {
      String mapKey = uuid + " " + key;
      Long last = LAST_ACCEPTED.get(mapKey);
      if (last != null && now - last < minIntervalTicks) {
         return false;
      } else {
         LAST_ACCEPTED.put(mapKey, now);
         return true;
      }
   }

   public static void clear(UUID uuid) {
      String prefix = uuid + " ";
      LAST_ACCEPTED.keySet().removeIf(k -> k.startsWith(prefix));
   }
}
