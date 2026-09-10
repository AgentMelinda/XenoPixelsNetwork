package com.dragonminez.client.render.hair;

import com.dragonminez.common.hair.CustomHair;
import com.dragonminez.common.hair.HairManager;
import com.dragonminez.common.hair.HairStrand;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.character.Character;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import com.mojang.math.Axis;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Vector3f;

public class HairRenderer {
   public static boolean PHYSICS_ENABLED = true;
   public static int EDITING_STRAND_ID = -1;
   private static final float UNIT_SCALE = 0.0625F;
   private static final float SIZE_DECAY = 0.85F;
   private static final float INVISIBLE_SCALE = 1.0E-4F;
   private static final float MICRO_SWAY = 4.0F;
   private static final float SEGMENT_PHASE = 0.6F;
   private static final float INERTIA_GAIN = 260.0F;
   private static final float INERTIA_MAX = 75.0F;
   private static final float KI_CHARGE_LIFT = 95.0F;
   private static final ResourceLocation HAIR_TEXTURE = ResourceLocation.fromNamespaceAndPath("dragonminez", "textures/entity/races/hair.png");
   private static final CustomHair.HairFace[] FACES = CustomHair.HairFace.values();

   public static void render(
      PoseStack poseStack,
      MultiBufferSource bufferSource,
      CustomHair hairFrom,
      CustomHair hairTo,
      float transitionFactor,
      Character character,
      StatsData stats,
      AbstractClientPlayer player,
      float[] rgbFrom,
      float[] rgbTo,
      boolean forceColorFrom,
      boolean forceColorTo,
      float partialTick,
      int packedLight,
      int packedOverlay,
      float baseAlpha,
      float physicsLodMultiplier,
      float chargeProgress
   ) {
      if (hairFrom == null) {
         hairFrom = new CustomHair();
      }

      if (hairTo == null) {
         hairTo = hairFrom;
      }

      if (character == null || HairManager.canUseHair(character)) {
         float movementIntensity = 0.0F;
         boolean isCharging = false;
         float time = 0.0F;
         float inertiaForward = 0.0F;
         float inertiaSide = 0.0F;
         float inertiaLift = 0.0F;
         boolean useFromOnly = transitionFactor <= 1.0E-4F;
         boolean useToOnly = transitionFactor >= 0.9999F;
         if (stats != null && player != null && PHYSICS_ENABLED && physicsLodMultiplier > 0.01F) {
            time = (float)player.tickCount + partialTick;
            double velX = player.getX() - player.xo;
            double velY = player.getY() - player.yo;
            double velZ = player.getZ() - player.zo;
            double velocity = velX * velX + velY * velY + velZ * velZ;
            boolean isMoving = velocity > 4.0E-4 || player.isSprinting() || player.isSwimming();
            movementIntensity = (isMoving ? 1.0F : 0.2F) * physicsLodMultiplier;
            if (!stats.getStatus().isChargingKi() && !stats.getStatus().isActionCharging()) {
               boolean var102 = false;
            } else {
               boolean var10000 = true;
            }

            float headYaw = Mth.lerp(partialTick, player.yHeadRotO, player.yHeadRot);
            double hy = Math.toRadians((double)headYaw);
            float sinY = (float)Math.sin(hy);
            float cosY = (float)Math.cos(hy);
            float fwd = (float)((double)(-sinY) * velX + (double)cosY * velZ);
            float side = (float)((double)cosY * velX + (double)sinY * velZ);
            float vert = (float)velY;
            float gain = 260.0F * physicsLodMultiplier;
            inertiaForward = Mth.clamp(fwd * gain, -75.0F, 75.0F);
            inertiaSide = Mth.clamp(-side * gain, -75.0F, 75.0F);
            inertiaLift = Mth.clamp(vert * gain, -75.0F, 75.0F);
            if (chargeProgress > 0.0F) {
               inertiaLift -= 95.0F * physicsLodMultiplier * chargeProgress;
            }
         }

         if (!useFromOnly && !useToOnly) {
            transitionFactor = transitionFactor * transitionFactor * (3.0F - 2.0F * transitionFactor);
         }

         float[] tempRgb = new float[3];
         RenderType opaqueType = RenderType.entityCutoutNoCull(HAIR_TEXTURE);
         RenderType translucentType = RenderType.entityTranslucent(HAIR_TEXTURE);

         for (CustomHair.HairFace face : FACES) {
            HairStrand[] strandsFrom = hairFrom.getStrands(face);
            HairStrand[] strandsTo = hairTo.getStrands(face);
            int maxStrands = Math.max(strandsFrom != null ? strandsFrom.length : 0, strandsTo != null ? strandsTo.length : 0);

            for (int i = 0; i < maxStrands; i++) {
               HairStrand s1 = strandsFrom != null && i < strandsFrom.length ? strandsFrom[i] : null;
               HairStrand s2 = strandsTo != null && i < strandsTo.length ? strandsTo[i] : null;
               boolean v1 = s1 != null && s1.isVisible();
               boolean v2 = s2 != null && s2.isVisible();
               if ((v1 || v2) && (!useFromOnly || v1) && (!useToOnly || v2)) {
                  float currentAlpha = baseAlpha;
                  float lerpRotZ;
                  float lerpScaleX;
                  float lerpScaleY;
                  float lerpScaleZ;
                  float lerpStretch;
                  float lerpCurveX;
                  float lerpCurveY;
                  float lerpCurveZ;
                  float lerpW;
                  float lerpH;
                  float lerpD;
                  int length;
                  int strandId;
                  float lerpRotX;
                  float lerpRotY;
                  if (useFromOnly) {
                     float[] fromRgb = !forceColorFrom && s1.hasCustomColor() ? s1.getRgbColor() : rgbFrom;
                     tempRgb[0] = fromRgb[0];
                     tempRgb[1] = fromRgb[1];
                     tempRgb[2] = fromRgb[2];
                     lerpRotX = s1.getRotationX();
                     lerpRotY = s1.getRotationY();
                     lerpRotZ = s1.getRotationZ();
                     lerpScaleX = s1.getScaleX();
                     lerpScaleY = s1.getScaleY();
                     lerpScaleZ = s1.getScaleZ();
                     lerpStretch = s1.getLengthScale();
                     lerpCurveX = s1.getCurveX();
                     lerpCurveY = s1.getCurveY();
                     lerpCurveZ = s1.getCurveZ();
                     lerpW = s1.getCubeWidth();
                     lerpH = s1.getCubeHeight();
                     lerpD = s1.getCubeDepth();
                     length = s1.getLength();
                     strandId = s1.getId();
                  } else if (useToOnly) {
                     float[] toRgb = !forceColorTo && s2.hasCustomColor() ? s2.getRgbColor() : rgbTo;
                     tempRgb[0] = toRgb[0];
                     tempRgb[1] = toRgb[1];
                     tempRgb[2] = toRgb[2];
                     lerpRotX = s2.getRotationX();
                     lerpRotY = s2.getRotationY();
                     lerpRotZ = s2.getRotationZ();
                     lerpScaleX = s2.getScaleX();
                     lerpScaleY = s2.getScaleY();
                     lerpScaleZ = s2.getScaleZ();
                     lerpStretch = s2.getLengthScale();
                     lerpCurveX = s2.getCurveX();
                     lerpCurveY = s2.getCurveY();
                     lerpCurveZ = s2.getCurveZ();
                     lerpW = s2.getCubeWidth();
                     lerpH = s2.getCubeHeight();
                     lerpD = s2.getCubeDepth();
                     length = s2.getLength();
                     strandId = s2.getId();
                  } else {
                     HairStrand colorFrom = v1 ? s1 : s2;
                     HairStrand colorTo = v2 ? s2 : s1;
                     fillInterpolatedRgb(colorFrom, colorTo, transitionFactor, rgbFrom, rgbTo, forceColorFrom, forceColorTo, tempRgb);
                     float fromRotX = v1 ? s1.getRotationX() : (s2 != null ? s2.getRotationX() : 0.0F);
                     float fromRotY = v1 ? s1.getRotationY() : (s2 != null ? s2.getRotationY() : 0.0F);
                     float fromRotZ = v1 ? s1.getRotationZ() : (s2 != null ? s2.getRotationZ() : 0.0F);
                     float toRotX = v2 ? s2.getRotationX() : (s1 != null ? s1.getRotationX() : 0.0F);
                     float toRotY = v2 ? s2.getRotationY() : (s1 != null ? s1.getRotationY() : 0.0F);
                     float toRotZ = v2 ? s2.getRotationZ() : (s1 != null ? s1.getRotationZ() : 0.0F);
                     lerpRotX = Mth.lerp(transitionFactor, fromRotX, toRotX);
                     lerpRotY = Mth.lerp(transitionFactor, fromRotY, toRotY);
                     lerpRotZ = Mth.lerp(transitionFactor, fromRotZ, toRotZ);
                     float fromScaleX = v1 ? s1.getScaleX() : 1.0E-4F;
                     float fromScaleY = v1 ? s1.getScaleY() : 1.0E-4F;
                     float fromScaleZ = v1 ? s1.getScaleZ() : 1.0E-4F;
                     float toScaleX = v2 ? s2.getScaleX() : 1.0E-4F;
                     float toScaleY = v2 ? s2.getScaleY() : 1.0E-4F;
                     float toScaleZ = v2 ? s2.getScaleZ() : 1.0E-4F;
                     lerpScaleX = Mth.lerp(transitionFactor, fromScaleX, toScaleX);
                     lerpScaleY = Mth.lerp(transitionFactor, fromScaleY, toScaleY);
                     lerpScaleZ = Mth.lerp(transitionFactor, fromScaleZ, toScaleZ);
                     float fromStretch = v1 ? s1.getLengthScale() : (s2 != null ? s2.getLengthScale() : 1.0F);
                     float toStretch = v2 ? s2.getLengthScale() : (s1 != null ? s1.getLengthScale() : 1.0F);
                     lerpStretch = Mth.lerp(transitionFactor, fromStretch, toStretch);
                     float fromCurveX = v1 ? s1.getCurveX() : (s2 != null ? s2.getCurveX() : 0.0F);
                     float fromCurveY = v1 ? s1.getCurveY() : (s2 != null ? s2.getCurveY() : 0.0F);
                     float fromCurveZ = v1 ? s1.getCurveZ() : (s2 != null ? s2.getCurveZ() : 0.0F);
                     float toCurveX = v2 ? s2.getCurveX() : (s1 != null ? s1.getCurveX() : 0.0F);
                     float toCurveY = v2 ? s2.getCurveY() : (s1 != null ? s1.getCurveY() : 0.0F);
                     float toCurveZ = v2 ? s2.getCurveZ() : (s1 != null ? s1.getCurveZ() : 0.0F);
                     lerpCurveX = Mth.lerp(transitionFactor, fromCurveX, toCurveX);
                     lerpCurveY = Mth.lerp(transitionFactor, fromCurveY, toCurveY);
                     lerpCurveZ = Mth.lerp(transitionFactor, fromCurveZ, toCurveZ);
                     float fromW = v1 ? s1.getCubeWidth() : (s2 != null ? s2.getCubeWidth() : 2.0F);
                     float fromH = v1 ? s1.getCubeHeight() : (s2 != null ? s2.getCubeHeight() : 2.0F);
                     float fromD = v1 ? s1.getCubeDepth() : (s2 != null ? s2.getCubeDepth() : 2.0F);
                     float toW = v2 ? s2.getCubeWidth() : (s1 != null ? s1.getCubeWidth() : 2.0F);
                     float toH = v2 ? s2.getCubeHeight() : (s1 != null ? s1.getCubeHeight() : 2.0F);
                     float toD = v2 ? s2.getCubeDepth() : (s1 != null ? s1.getCubeDepth() : 2.0F);
                     lerpW = Mth.lerp(transitionFactor, fromW, toW);
                     lerpH = Mth.lerp(transitionFactor, fromH, toH);
                     lerpD = Mth.lerp(transitionFactor, fromD, toD);
                     length = Math.max(v1 && s1 != null ? s1.getLength() : 0, v2 && s2 != null ? s2.getLength() : 0);
                     strandId = s1 != null ? s1.getId() : (s2 != null ? s2.getId() : -1);
                  }

                  if (length > 0) {
                     if (EDITING_STRAND_ID != -1 && strandId != EDITING_STRAND_ID) {
                        currentAlpha = baseAlpha * 0.35F;
                     }

                     Vector3f staticPos = CustomHair.getStrandBasePosition(face, i);
                     VertexConsumer strandBuffer = bufferSource.getBuffer(currentAlpha < 1.0F ? translucentType : opaqueType);
                     renderStrandInterpolated(
                        poseStack,
                        strandBuffer,
                        staticPos,
                        tempRgb[0],
                        tempRgb[1],
                        tempRgb[2],
                        packedLight,
                        packedOverlay,
                        time,
                        movementIntensity,
                        chargeProgress,
                        inertiaForward,
                        inertiaSide,
                        inertiaLift,
                        lerpRotX,
                        lerpRotY,
                        lerpRotZ,
                        lerpScaleX,
                        lerpScaleY,
                        lerpScaleZ,
                        lerpStretch,
                        lerpCurveX,
                        lerpCurveY,
                        lerpCurveZ,
                        lerpW,
                        lerpH,
                        lerpD,
                        length,
                        strandId,
                        face,
                        currentAlpha,
                        physicsLodMultiplier
                     );
                  }
               }
            }
         }
      }
   }

   private static void fillInterpolatedRgb(
      HairStrand s1, HairStrand s2, float factor, float[] globalRgbFrom, float[] globalRgbTo, boolean forceColorFrom, boolean forceColorTo, float[] out
   ) {
      float[] effectiveFrom = globalRgbFrom;
      if (!forceColorFrom && s1 != null && s1.hasCustomColor()) {
         effectiveFrom = s1.getRgbColor();
      }

      float[] effectiveTo = globalRgbTo;
      if (!forceColorTo && s2 != null && s2.hasCustomColor()) {
         effectiveTo = s2.getRgbColor();
      }

      if (factor <= 0.0F) {
         out[0] = effectiveFrom[0];
         out[1] = effectiveFrom[1];
         out[2] = effectiveFrom[2];
      } else if (factor >= 1.0F) {
         out[0] = effectiveTo[0];
         out[1] = effectiveTo[1];
         out[2] = effectiveTo[2];
      } else {
         out[0] = Mth.lerp(factor, effectiveFrom[0], effectiveTo[0]);
         out[1] = Mth.lerp(factor, effectiveFrom[1], effectiveTo[1]);
         out[2] = Mth.lerp(factor, effectiveFrom[2], effectiveTo[2]);
      }
   }

   private static void renderStrandInterpolated(
      PoseStack poseStack,
      VertexConsumer strandBuffer,
      Vector3f pos,
      float r,
      float g,
      float b,
      int packedLight,
      int packedOverlay,
      float time,
      float moveIntensity,
      float chargeProgress,
      float inertiaForward,
      float inertiaSide,
      float inertiaLift,
      float rotX,
      float rotY,
      float rotZ,
      float scaleX,
      float scaleY,
      float scaleZ,
      float stretchFactor,
      float curveX,
      float curveY,
      float curveZ,
      float width,
      float height,
      float depth,
      int length,
      int id,
      CustomHair.HairFace face,
      float alpha,
      float physicsLodMultiplier
   ) {
      poseStack.pushPose();
      poseStack.translate(pos.x * 0.0625F, pos.y * 0.0625F, pos.z * 0.0625F);
      float offset = (float)id * 13.0F;
      float baseSwaySpeed = moveIntensity > 0.5F ? 0.4F : 0.05F;
      float baseSwayAmount = (moveIntensity > 0.5F ? 5.0F : 0.6F) * physicsLodMultiplier;
      float targetSwaySpeed = 0.8F;
      float targetSwayAmount = 3.0F * physicsLodMultiplier;
      float currentSwaySpeed = Mth.lerp(chargeProgress, baseSwaySpeed, targetSwaySpeed);
      float currentSwayAmount = Mth.lerp(chargeProgress, baseSwayAmount, targetSwayAmount);
      float phase = (time + offset) * currentSwaySpeed;
      float animRotX = time == 0.0F ? 0.0F : Mth.sin(phase) * currentSwayAmount;
      float animRotZ = time == 0.0F ? 0.0F : Mth.cos(phase * 0.7F) * currentSwayAmount * 0.5F;
      if (chargeProgress > 0.0F && time != 0.0F) {
         float chargeLift = Mth.abs(Mth.sin(time * 0.5F)) * 5.0F * physicsLodMultiplier * chargeProgress;
         curveX += chargeLift;
      }

      float finalRotX = rotX + animRotX;
      float finalRotZ = rotZ + animRotZ;
      switch (face) {
         case FRONT:
            finalRotX = animRotX > 0.0F ? Math.min(finalRotX, rotX) : finalRotX;
            break;
         case BACK:
            finalRotX = animRotX < 0.0F ? Math.max(finalRotX, rotX) : finalRotX;
            break;
         case LEFT:
            finalRotZ = animRotZ < 0.0F ? Math.max(finalRotZ, rotZ) : finalRotZ;
            break;
         case RIGHT:
            finalRotZ = animRotZ > 0.0F ? Math.min(finalRotZ, rotZ) : finalRotZ;
      }

      applyRotation(poseStack, finalRotX, rotY, finalRotZ);
      poseStack.scale(scaleX, scaleY, scaleZ);
      float baseW = width * 0.0625F;
      float baseH = height * 0.0625F;
      float baseD = depth * 0.0625F;
      float accumulatedHeight = 0.0F;
      float sizeFactor = 1.0F;
      float localInertiaX = 0.0F;
      float localInertiaZ = 0.0F;
      if (time != 0.0F && (inertiaForward != 0.0F || inertiaSide != 0.0F || inertiaLift != 0.0F)) {
         Vector3f inertiaLocal = new Vector3f(-inertiaSide, -inertiaLift, inertiaForward);
         inertiaLocal.rotateX((float)Math.toRadians((double)(-rotX)));
         inertiaLocal.rotateY((float)Math.toRadians((double)(-rotY)));
         inertiaLocal.rotateZ((float)Math.toRadians((double)(-rotZ)));
         localInertiaX = inertiaLocal.z;
         localInertiaZ = -inertiaLocal.x;
      }

      for (int i = 0; i < length; i++) {
         float cubeW = baseW * sizeFactor;
         float cubeH = baseH * sizeFactor * stretchFactor;
         float cubeD = baseD * sizeFactor;
         float overlap = 0.0F;
         if (i > 0) {
            poseStack.translate(0.0F, accumulatedHeight, 0.0F);
            float segCurveX = curveX;
            float segCurveZ = curveZ;
            if (time != 0.0F) {
               float tip = (float)i / (float)length;
               float seg = tip * physicsLodMultiplier;
               float altCurrentSwaySpeed = Mth.lerp(chargeProgress, baseSwaySpeed, targetSwaySpeed);
               float microPhase = time * altCurrentSwaySpeed + offset + (float)i * 0.6F;
               float waveX = Mth.sin(microPhase);
               float waveZ = Mth.cos(microPhase * 0.7F);
               float altCurrentSwayAmount = Mth.lerp(chargeProgress, baseSwayAmount, targetSwayAmount);
               segCurveX = curveX + waveX * 4.0F * altCurrentSwayAmount * seg + localInertiaX * seg;
               segCurveZ = curveZ + waveZ * 4.0F * 0.5F * altCurrentSwayAmount * seg + localInertiaZ * seg;
            }

            applyRotation(poseStack, segCurveX, curveY, segCurveZ);
            float radX = Math.abs(segCurveX) * (float) (Math.PI / 180.0);
            float radZ = Math.abs(segCurveZ) * (float) (Math.PI / 180.0);
            overlap = cubeW / 2.0F * Mth.sin(radZ) + cubeD / 2.0F * Mth.sin(radX) + 0.015F;
         }

         renderCube(poseStack, strandBuffer, cubeW, cubeH, cubeD, overlap, r, g, b, packedLight, packedOverlay, alpha);
         accumulatedHeight = cubeH;
         sizeFactor *= 0.85F;
      }

      poseStack.popPose();
   }

   private static void applyRotation(PoseStack poseStack, float rotX, float rotY, float rotZ) {
      if (rotX != 0.0F) {
         poseStack.mulPose(Axis.XP.rotationDegrees(rotX));
      }

      if (rotY != 0.0F) {
         poseStack.mulPose(Axis.YP.rotationDegrees(rotY));
      }

      if (rotZ != 0.0F) {
         poseStack.mulPose(Axis.ZP.rotationDegrees(rotZ));
      }
   }

   private static void renderCube(
      PoseStack poseStack,
      VertexConsumer buffer,
      float width,
      float height,
      float depth,
      float overlap,
      float r,
      float g,
      float b,
      int packedLight,
      int packedOverlay,
      float alpha
   ) {
      Pose pose = poseStack.last();
      float hw = width / 2.0F;
      float hd = depth / 2.0F;
      float bottom = -overlap;
      addQuad(
         buffer,
         pose,
         -hw,
         bottom,
         -hd,
         hw,
         bottom,
         -hd,
         hw,
         bottom,
         hd,
         -hw,
         bottom,
         hd,
         0.0F,
         -1.0F,
         0.0F,
         r,
         g,
         b,
         0.0F,
         0.0F,
         1.0F,
         1.0F,
         packedLight,
         packedOverlay,
         alpha
      );
      addQuad(
         buffer,
         pose,
         -hw,
         height,
         hd,
         hw,
         height,
         hd,
         hw,
         height,
         -hd,
         -hw,
         height,
         -hd,
         0.0F,
         1.0F,
         0.0F,
         r,
         g,
         b,
         0.0F,
         0.0F,
         1.0F,
         1.0F,
         packedLight,
         packedOverlay,
         alpha
      );
      addQuad(
         buffer,
         pose,
         -hw,
         bottom,
         -hd,
         -hw,
         height,
         -hd,
         hw,
         height,
         -hd,
         hw,
         bottom,
         -hd,
         0.0F,
         0.0F,
         -1.0F,
         r,
         g,
         b,
         0.0F,
         0.0F,
         1.0F,
         1.0F,
         packedLight,
         packedOverlay,
         alpha
      );
      addQuad(
         buffer,
         pose,
         hw,
         bottom,
         hd,
         hw,
         height,
         hd,
         -hw,
         height,
         hd,
         -hw,
         bottom,
         hd,
         0.0F,
         0.0F,
         1.0F,
         r,
         g,
         b,
         0.0F,
         0.0F,
         1.0F,
         1.0F,
         packedLight,
         packedOverlay,
         alpha
      );
      addQuad(
         buffer,
         pose,
         hw,
         bottom,
         -hd,
         hw,
         height,
         -hd,
         hw,
         height,
         hd,
         hw,
         bottom,
         hd,
         1.0F,
         0.0F,
         0.0F,
         r,
         g,
         b,
         0.0F,
         0.0F,
         1.0F,
         1.0F,
         packedLight,
         packedOverlay,
         alpha
      );
      addQuad(
         buffer,
         pose,
         -hw,
         bottom,
         hd,
         -hw,
         height,
         hd,
         -hw,
         height,
         -hd,
         -hw,
         bottom,
         -hd,
         -1.0F,
         0.0F,
         0.0F,
         r,
         g,
         b,
         0.0F,
         0.0F,
         1.0F,
         1.0F,
         packedLight,
         packedOverlay,
         alpha
      );
   }

   private static void addQuad(
      VertexConsumer buffer,
      Pose pose,
      float x1,
      float y1,
      float z1,
      float x2,
      float y2,
      float z2,
      float x3,
      float y3,
      float z3,
      float x4,
      float y4,
      float z4,
      float nx,
      float ny,
      float nz,
      float r,
      float g,
      float b,
      float u0,
      float v0,
      float u1,
      float v1,
      int packedLight,
      int packedOverlay,
      float alpha
   ) {
      buffer.addVertex(pose, x1, y1, z1).setColor(r, g, b, alpha).setUv(u0, v0).setOverlay(packedOverlay).setLight(packedLight).setNormal(pose, nx, ny, nz);
      buffer.addVertex(pose, x2, y2, z2).setColor(r, g, b, alpha).setUv(u0, v1).setOverlay(packedOverlay).setLight(packedLight).setNormal(pose, nx, ny, nz);
      buffer.addVertex(pose, x3, y3, z3).setColor(r, g, b, alpha).setUv(u1, v1).setOverlay(packedOverlay).setLight(packedLight).setNormal(pose, nx, ny, nz);
      buffer.addVertex(pose, x4, y4, z4).setColor(r, g, b, alpha).setUv(u1, v0).setOverlay(packedOverlay).setLight(packedLight).setNormal(pose, nx, ny, nz);
   }
}
