package com.dragonminez.client.render.camera;

import com.dragonminez.client.events.LockOnEvent;
import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.config.GeneralUserConfig;
import lombok.Generated;
import net.minecraft.client.Camera;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import org.joml.Vector3f;

public final class OverShoulderCamera {
   public static final int MODE_NONE = 0;
   public static final int MODE_LOCK_ON = 1;
   public static final int MODE_ALWAYS = 2;
   private static double curBack;
   private static double curUp;
   private static double curRight;
   private static boolean active;
   private static boolean previewOverride;

   private OverShoulderCamera() {
   }

   public static boolean isRunning() {
      return active;
   }

   public static double getCurrentSide() {
      return curRight;
   }

   public static boolean isActive(Entity entity, boolean thirdPersonReverse) {
      if (!thirdPersonReverse && entity instanceof Player) {
         if (entity instanceof LivingEntity living && living.isSleeping()) {
            return false;
         }

         if (previewOverride) {
            return true;
         } else {
            int mode = ConfigManager.getUserConfig().getOverShoulderMode();
            if (mode == 2) {
               return true;
            } else {
               return mode == 1 ? LockOnEvent.getLockedTarget() != null : false;
            }
         }
      } else {
         return false;
      }
   }

   public static Vec3 computeMove(
      Camera camera,
      BlockGetter level,
      Entity entity,
      boolean thirdPersonReverse,
      double vanillaForward,
      double vanillaUp,
      double vanillaLeft,
      float partialTick
   ) {
      if (!isActive(entity, thirdPersonReverse)) {
         active = false;
         return new Vec3(vanillaForward, vanillaUp, vanillaLeft);
      } else {
         if (!active) {
            curBack = -vanillaForward;
            curUp = 0.0;
            curRight = 0.0;
            active = true;
         }

         GeneralUserConfig config = ConfigManager.getUserConfig();
         double ease = (double)config.getOverShoulderSmoothing().floatValue();
         double targetBack = (double)config.getOverShoulderBack().floatValue();
         double targetUp = (double)config.getOverShoulderUp().floatValue();
         double targetRight = (double)config.getOverShoulderSide().floatValue() * (config.getOverShoulderLeft() ? -1.0 : 1.0);
         curBack = curBack + (targetBack - curBack) * ease;
         curUp = curUp + (targetUp - curUp) * ease;
         curRight = curRight + (targetRight - curRight) * ease;
         double moveForward = -curBack;
         double moveUp = curUp;
         double moveLeft = -curRight;
         double allowed = clampToObstruction(camera, level, entity, moveForward, moveUp, moveLeft, partialTick);
         double desired = Math.sqrt(moveForward * moveForward + moveUp * moveUp + moveLeft * moveLeft);
         if (desired > 1.0E-4 && allowed < desired) {
            double scale = allowed / desired;
            moveForward *= scale;
            moveUp *= scale;
            moveLeft *= scale;
         }

         return new Vec3(moveForward, moveUp, moveLeft);
      }
   }

   private static double clampToObstruction(
      Camera camera, BlockGetter level, Entity entity, double moveForward, double moveUp, double moveLeft, float partialTick
   ) {
      Vector3f look = camera.getLookVector();
      Vector3f up = camera.getUpVector();
      Vector3f left = camera.getLeftVector();
      Vec3 worldOffset = new Vec3(
         (double)look.x() * moveForward + (double)up.x() * moveUp + (double)left.x() * moveLeft,
         (double)look.y() * moveForward + (double)up.y() * moveUp + (double)left.y() * moveLeft,
         (double)look.z() * moveForward + (double)up.z() * moveUp + (double)left.z() * moveLeft
      );
      double distance = worldOffset.length();
      if (distance < 1.0E-4) {
         return distance;
      } else {
         Vec3 eyePosition = entity.getEyePosition(partialTick);

         for (int i = 0; i < 8; i++) {
            Vec3 corner = new Vec3((double)(i & 1), (double)(i >> 1 & 1), (double)(i >> 2 & 1)).scale(2.0).subtract(1.0, 1.0, 1.0);
            Vec3 fromOffset = corner.scale((double)Mth.clamp(entity.getBbWidth() / 2.0F / Mth.sqrt(2.0F), 0.0F, 0.15F))
               .xRot(-camera.getXRot() * (float) (Math.PI / 180.0))
               .yRot(-camera.getYRot() * (float) (Math.PI / 180.0));
            Vec3 from = eyePosition.add(fromOffset);
            Vec3 to = from.add(worldOffset);
            ClipContext context = new ClipContext(from, to, Block.VISUAL, Fluid.NONE, entity);
            HitResult hit = level.clip(context);
            if (hit.getType() != Type.MISS) {
               double hitDistance = hit.getLocation().distanceTo(from);
               if (hitDistance < distance) {
                  distance = hitDistance;
               }
            }
         }

         return distance;
      }
   }

   @Generated
   public static boolean isPreviewOverride() {
      return previewOverride;
   }

   @Generated
   public static void setPreviewOverride(boolean previewOverride) {
      OverShoulderCamera.previewOverride = previewOverride;
   }
}
