package com.dragonminez.server.util;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

public final class GravityDeviceManager {
   private static final Map<String, GravityDeviceManager.Zone> ZONES = new ConcurrentHashMap<>();

   private GravityDeviceManager() {
   }

   private static String key(Level level, BlockPos pos) {
      return level.dimension().location() + "@" + pos.asLong();
   }

   public static void register(Level level, BlockPos pos, AABB bounds, double gravity) {
      ZONES.put(key(level, pos), new GravityDeviceManager.Zone(level.dimension().location().toString(), bounds, gravity));
   }

   public static void unregister(Level level, BlockPos pos) {
      ZONES.remove(key(level, pos));
   }

   public static double getGravityFor(Player player) {
      if (ZONES.isEmpty()) {
         return 0.0;
      } else {
         String dim = player.level().dimension().location().toString();
         double x = player.getX();
         double y = player.getY();
         double z = player.getZ();
         double max = 0.0;

         for (GravityDeviceManager.Zone zone : ZONES.values()) {
            if (zone.dimension.equals(dim) && zone.bounds.contains(x, y, z) && zone.gravity > max) {
               max = zone.gravity;
            }
         }

         return max;
      }
   }

   private static record Zone(String dimension, AABB bounds, double gravity) {
   }
}
