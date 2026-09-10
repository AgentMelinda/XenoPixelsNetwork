package com.dragonminez.client.clash;

import com.dragonminez.client.render.firstperson.dto.DMZCameraBuffer;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class BeamClashCinematicCamera {
   private static final double SIDE_DISTANCE_BASE = 5.0;
   private static final double SIDE_DISTANCE_SPAN = 0.55;
   private static final double SIDE_DISTANCE_MAX = 16.0;
   private static final double CAMERA_HEIGHT = 1.6;
   private static final double WALL_MARGIN = 0.5;
   private static final float FOV_TARGET = 0.84F;
   private static final float FOV_EASE = 0.18F;
   private static final double SHAKE_POS = 0.16;
   private static final float SHAKE_ANGLE = 0.9F;
   private static volatile boolean active = false;
   private static CameraType previousType = null;
   private static float fovFactor = 1.0F;

   private BeamClashCinematicCamera() {
   }

   public static boolean isActive() {
      return active;
   }

   public static void activate() {
      if (!active) {
         Minecraft mc = Minecraft.getInstance();
         previousType = mc.options.getCameraType();
         mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
         active = true;
      }
   }

   public static void deactivate() {
      if (active) {
         Minecraft mc = Minecraft.getInstance();
         if (previousType != null) {
            mc.options.setCameraType(previousType);
         }

         previousType = null;
         active = false;
         DMZCameraBuffer.reset();
      }
   }

   public static void tickFov() {
      float target = active ? 0.84F : 1.0F;
      fovFactor = fovFactor + (target - fovFactor) * 0.18F;
      if (Math.abs(fovFactor - target) < 0.001F) {
         fovFactor = target;
      }
   }

   public static double applyFov(double baseFov) {
      return baseFov * (double)fovFactor;
   }

   public static BeamClashCinematicCamera.Shot computeShot(BlockGetter level, LocalPlayer player, float partialTick) {
      if (!active) {
         return null;
      } else {
         Vec3 selfPos = player.getEyePosition(partialTick);
         Vec3 oppPos = null;
         int oppId = ClientBeamClashState.opponentId();
         if (oppId >= 0 && player.level() != null) {
            Entity opp = player.level().getEntity(oppId);
            if (opp != null) {
               oppPos = opp.getEyePosition(partialTick);
            }
         }

         if (oppPos == null) {
            oppPos = selfPos.add(player.getViewVector(partialTick).scale(8.0));
         }

         Vec3 mid = selfPos.add(oppPos).scale(0.5);
         Vec3 axis = oppPos.subtract(selfPos);
         Vec3 axisHoriz = new Vec3(axis.x, 0.0, axis.z);
         double separation = axisHoriz.length();
         if (separation < 0.1) {
            Vec3 look = player.getViewVector(partialTick);
            axisHoriz = new Vec3(look.x, 0.0, look.z);
            separation = Math.max(axisHoriz.length(), 0.1);
         }

         axisHoriz = axisHoriz.normalize();
         Vec3 side = new Vec3(0.0, 1.0, 0.0).cross(axisHoriz).normalize();
         double dist = Math.min(16.0, 5.0 + separation * 0.55);
         float decisiveness = Math.abs(ClientBeamClashState.advantage() - 0.5F) * 2.0F;
         double shake = 0.16 * (0.5 + (double)decisiveness);
         float angleShake = 0.9F * (0.5F + decisiveness);
         float t = (float)player.tickCount + partialTick;
         Vec3 camPos = mid.add(side.scale(dist))
            .add(0.0, 1.6, 0.0)
            .add(side.scale(Math.sin((double)t * 1.7) * shake))
            .add(0.0, Math.cos((double)t * 2.3) * shake * 0.6, 0.0);
         camPos = clampToLineOfSight(level, player, mid, camPos);
         Vec3 dir = mid.subtract(camPos);
         double horiz = Math.sqrt(dir.x * dir.x + dir.z * dir.z);
         float pitch = (float)(-(Mth.atan2(dir.y, horiz) * (180.0 / Math.PI)));
         float yaw = (float)(Mth.atan2(dir.z, dir.x) * (180.0 / Math.PI) - 90.0);
         yaw += (float)(Math.sin((double)t * 3.1) * (double)angleShake);
         pitch += (float)(Math.cos((double)t * 2.7) * (double)angleShake * 0.7);
         return new BeamClashCinematicCamera.Shot(camPos, yaw, pitch);
      }
   }

   private static Vec3 clampToLineOfSight(BlockGetter level, Entity viewer, Vec3 from, Vec3 to) {
      Vec3 delta = to.subtract(from);
      double len = delta.length();
      if (len < 1.0E-4) {
         return to;
      } else {
         Vec3 dir = delta.scale(1.0 / len);
         Vec3 rayEnd = from.add(dir.scale(len + 0.5));
         BlockHitResult hit = level.clip(new ClipContext(from, rayEnd, Block.COLLIDER, Fluid.NONE, viewer));
         if (hit.getType() != Type.MISS) {
            double allowed = Math.max(0.0, hit.getLocation().distanceTo(from) - 0.5);
            if (allowed < len) {
               return from.add(dir.scale(allowed));
            }
         }

         return to;
      }
   }

   public static record Shot(Vec3 pos, float yaw, float pitch) {
   }
}
