package com.dragonminez.client.render.util;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.UseAnim;
import org.joml.Vector3d;
import software.bernie.geckolib.cache.object.GeoBone;

public class RenderUtil {
   public static void rotateHead(AbstractClientPlayer animatable, GeoBone bone, float partialTick) {
      float lerpBodyRot = Mth.lerp(partialTick, animatable.yBodyRotO, animatable.yBodyRot);
      float lerpHeadRot = Mth.lerp(partialTick, animatable.yHeadRotO, animatable.yHeadRot);
      float netHeadYaw = lerpHeadRot - lerpBodyRot;
      float netHeadPitch = Mth.lerp(partialTick, animatable.xRotO, animatable.getXRot());
      bone.setRotX(-netHeadPitch * (float) (Math.PI / 180.0));
      bone.setRotY(-netHeadYaw * (float) (Math.PI / 180.0));
   }

   public static void animateHand(AbstractClientPlayer animatable, GeoBone armBone, float partialTick, float ageInTicks) {
      UseAnim useAction = animatable.getUseItem().getUseAnimation();
      if (useAction == UseAnim.BOW) {
         animateBowHand(animatable, armBone, ageInTicks);
      } else if (animatable.getUseItem().getItem() instanceof CrossbowItem || animatable.getMainHandItem().getItem() instanceof CrossbowItem) {
         animateCrossbowHand(animatable, armBone, ageInTicks);
      }
   }

   private static void animateBowHand(AbstractClientPlayer player, GeoBone armBone, float ageInTicks) {
      boolean armIsLeft = armBone.getName().equals("left_arm");
      float pitch = player.getXRot() * (float) (Math.PI / 180.0);
      float yawDelta = (player.getYHeadRot() - player.yBodyRot) * (float) (Math.PI / 180.0);
      float minPitch = -0.1F;
      float maxPitch = 1.0F;
      pitch = Mth.clamp(pitch, minPitch, maxPitch);
      float yawScale = 0.3F;
      float minYaw = -0.5F;
      float maxYaw = 0.5F;
      float yaw = Mth.clamp(yawDelta * yawScale, minYaw, maxYaw);
      float baseRotX = 1.5F - pitch * 0.5F;
      armBone.setRotX(baseRotX);
      armBone.setRotY(armIsLeft ? -0.5F + yaw : 0.3F + yaw);
      armBone.setRotZ(-0.2F);
      armBone.setRotZ(armBone.getRotZ() + Mth.sin(ageInTicks * 2.0F) * 0.02F);
   }

   private static void animateCrossbowHand(AbstractClientPlayer player, GeoBone armBone, float ageInTicks) {
      boolean armIsLeft = armBone.getName().equals("left_arm");
      boolean hasMainHandCrossbow = player.getMainHandItem().getItem() instanceof CrossbowItem;
      boolean hasOffHandCrossbow = player.getOffhandItem().getItem() instanceof CrossbowItem;
      boolean isCharging = player.isUsingItem() && player.getUseItem().getItem() instanceof CrossbowItem;
      boolean isMainHandCharged = hasMainHandCrossbow && CrossbowItem.isCharged(player.getMainHandItem());
      boolean isOffHandCharged = hasOffHandCrossbow && CrossbowItem.isCharged(player.getOffhandItem());
      if (isCharging || isMainHandCharged || isOffHandCharged) {
         float pitch = player.getXRot() * (float) (Math.PI / 180.0);
         float yawDelta = (player.getYHeadRot() - player.yBodyRot) * (float) (Math.PI / 180.0);
         float minPitch = -0.2F;
         float maxPitch = 0.8F;
         pitch = Mth.clamp(pitch, minPitch, maxPitch);
         float yawScale = 0.25F;
         float minYaw = -0.4F;
         float maxYaw = 0.4F;
         float yaw = Mth.clamp(yawDelta * yawScale, minYaw, maxYaw);
         float baseRotX = 1.2F - pitch * 0.5F;
         armBone.setRotX(baseRotX);
         armBone.setRotY(armIsLeft ? -0.4F + yaw : 0.4F + yaw);
         armBone.setRotZ(armIsLeft ? 0.15F : -0.15F);
         if (!isCharging) {
            armBone.setRotZ(armBone.getRotZ() + Mth.sin(ageInTicks * 1.5F) * 0.01F);
         }
      }
   }

   public static void playProceduralAnimations(AbstractClientPlayer player, GeoBone bone, float partialTick, float ageInTicks) {
      if (bone.getName().equals("head")) {
         rotateHead(player, bone, partialTick);
      }

      if (bone.getName().equals("right_arm") || bone.getName().equals("left_arm")) {
         animateHand(player, bone, partialTick, ageInTicks);
      }
   }

   public static boolean isMoving(LivingEntity entity) {
      Vector3d currentPos = new Vector3d(entity.getX(), entity.getY(), entity.getZ());
      Vector3d lastPos = new Vector3d(entity.xo, entity.yo, entity.zo);
      Vector3d expectedVelocity = currentPos.sub(lastPos);
      float avgVelocity = (float)(Math.abs(expectedVelocity.x) + Math.abs(expectedVelocity.z) / 2.0);
      return (double)avgVelocity >= 0.015;
   }
}
