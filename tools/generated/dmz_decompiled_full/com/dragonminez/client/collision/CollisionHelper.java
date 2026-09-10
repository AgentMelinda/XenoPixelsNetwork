package com.dragonminez.client.collision;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class CollisionHelper {
   public static double angleBetween(Vec3 a, Vec3 b) {
      double cosineTheta = a.dot(b) / (a.length() * b.length());
      double angle = Math.acos(cosineTheta) * (180.0 / Math.PI);
      return Double.isNaN(angle) ? 0.0 : angle;
   }

   public static Vec3 distanceVector(Vec3 point, AABB box) {
      double dx = 0.0;
      if (box.minX > point.x) {
         dx = box.minX - point.x;
      } else if (box.maxX < point.x) {
         dx = box.maxX - point.x;
      }

      double dy = 0.0;
      if (box.minY > point.y) {
         dy = box.minY - point.y;
      } else if (box.maxY < point.y) {
         dy = box.maxY - point.y;
      }

      double dz = 0.0;
      if (box.minZ > point.z) {
         dz = box.minZ - point.z;
      } else if (box.maxZ < point.z) {
         dz = box.maxZ - point.z;
      }

      return new Vec3(dx, dy, dz);
   }

   public static double distance(Vec3 point, AABB box) {
      return distanceVector(point, box).length();
   }
}
