package com.dragonminez.common.compat;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class CameraAimHelper {
   private static final String AIM_X = "dmz_camera_aim_x";
   private static final String AIM_Y = "dmz_camera_aim_y";
   private static final String AIM_Z = "dmz_camera_aim_z";
   private static final String AIM_TIME = "dmz_camera_aim_time";
   private static final Set<Integer> LOCAL_SOKIDANS = new HashSet<>();
   private static final long AIM_FRESH_TICKS = 3L;
   private static final double VERTICAL_EPSILON = 1.0E-6;

   private CameraAimHelper() {
   }

   public static void store(LivingEntity entity, Vec3 aim) {
      if (isValid(aim)) {
         Vec3 normalized = aim.normalize();
         CompoundTag tag = entity.getPersistentData();
         tag.putDouble("dmz_camera_aim_x", normalized.x);
         tag.putDouble("dmz_camera_aim_y", normalized.y);
         tag.putDouble("dmz_camera_aim_z", normalized.z);
         tag.putLong("dmz_camera_aim_time", entity.level().getGameTime());
      }
   }

   public static Vec3 resolve(LivingEntity entity) {
      CompoundTag tag = entity.getPersistentData();
      if (entity.level().getGameTime() - tag.getLong("dmz_camera_aim_time") <= 3L) {
         Vec3 aim = new Vec3(tag.getDouble("dmz_camera_aim_x"), tag.getDouble("dmz_camera_aim_y"), tag.getDouble("dmz_camera_aim_z"));
         if (isValid(aim)) {
            return aim.normalize();
         }
      }

      return entity.getLookAngle().normalize();
   }

   public static void trackLocalSokidan(int entityId, boolean controlled) {
      if (controlled) {
         LOCAL_SOKIDANS.add(entityId);
      } else {
         LOCAL_SOKIDANS.remove(entityId);
      }
   }

   public static boolean hasLocalSokidan(Level level) {
      LOCAL_SOKIDANS.removeIf(id -> {
         Entity entity = level.getEntity(id);
         return entity == null || entity.isRemoved();
      });
      return !LOCAL_SOKIDANS.isEmpty();
   }

   public static void clearLocalSokidans() {
      LOCAL_SOKIDANS.clear();
   }

   public static float yaw(Vec3 direction) {
      return (float)(Mth.atan2(direction.z, direction.x) * (180.0 / Math.PI) - 90.0);
   }

   public static float yaw(LivingEntity entity, Vec3 direction) {
      double horizontalSqr = direction.x * direction.x + direction.z * direction.z;
      return horizontalSqr < 1.0E-6 ? entity.getYRot() : yaw(direction);
   }

   public static float pitch(Vec3 direction) {
      double horizontal = Math.sqrt(direction.x * direction.x + direction.z * direction.z);
      return (float)(-(Mth.atan2(direction.y, horizontal) * (180.0 / Math.PI)));
   }

   private static boolean isValid(Vec3 aim) {
      return aim != null && Double.isFinite(aim.x) && Double.isFinite(aim.y) && Double.isFinite(aim.z) && aim.lengthSqr() > 1.0E-6;
   }
}
