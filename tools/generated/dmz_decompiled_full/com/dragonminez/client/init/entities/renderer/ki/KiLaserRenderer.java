package com.dragonminez.client.init.entities.renderer.ki;

import com.dragonminez.client.render.shader.DMZShaders;
import com.dragonminez.client.render.util.KiMeshFactory;
import com.dragonminez.client.render.util.KiSpiralMesh;
import com.dragonminez.client.render.util.PlayerEffectQueue;
import com.dragonminez.common.init.entities.ki.KiLaserEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

public class KiLaserRenderer extends EntityRenderer<KiLaserEntity> {
   private static final ResourceLocation TEXTURE_KI = ResourceLocation.fromNamespaceAndPath("dragonminez", "textures/entity/ki/kiblast.png");

   public KiLaserRenderer(Context pContext) {
      super(pContext);
   }

   public void render(KiLaserEntity entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
      Matrix4f basePose = new Matrix4f(poseStack.last().pose());
      PlayerEffectQueue.addKiAttack((stack, proj) -> {
         stack.pushPose();
         stack.last().pose().mul(basePose);
         int renderType = entity.getKiRenderType();
         float alphaMultiplier = 1.0F;
         float ageInTicks = (float)entity.tickCount + partialTick;
         int maxLife = entity.getMaxLife();
         int fadeTicks = 10;
         if (entity.tickCount >= maxLife - fadeTicks) {
            alphaMultiplier = ((float)maxLife - ageInTicks) / (float)fadeTicks;
            alphaMultiplier = Math.max(0.0F, alphaMultiplier);
         }

         switch (renderType) {
            case 1:
            case 2:
               this.renderKiMakkankosanpo(entity, partialTick, stack, proj, alphaMultiplier);
               break;
            default:
               this.renderKiLaser(entity, partialTick, stack, proj, alphaMultiplier);
         }

         stack.popPose();
      });
   }

   public void renderKiLaser(KiLaserEntity entity, float partialTick, PoseStack poseStack, Matrix4f proj, float alphaMultiplier) {
      float[] coreColor = entity.getRgbColorMain();
      float[] borderColor = entity.getRgbColorBorder();
      float[] outlineColor = entity.getRgbColorOutline();
      float radius = entity.getSize() * 0.08F;
      boolean isFiring = entity.isFiring();
      float length = isFiring ? entity.getBeamLength() : radius;
      float ageInTicks = (float)entity.tickCount + partialTick;
      float yaw = entity.getFixedYaw();
      float pitch = entity.getFixedPitch();
      ShaderInstance shader = DMZShaders.ki3dShader;
      if (shader != null) {
         shader.safeGetUniform("colorCore").set(coreColor[0], coreColor[1], coreColor[2]);
         shader.safeGetUniform("colorBorder").set(borderColor[0], borderColor[1], borderColor[2]);
         shader.safeGetUniform("colorOutline").set(outlineColor[0], outlineColor[1], outlineColor[2]);
         shader.safeGetUniform("time").set(ageInTicks / 20.0F);
         shader.safeGetUniform("ProjMat").set(proj);
         VertexBuffer mesh = isFiring ? KiMeshFactory.getCylinderMesh() : KiMeshFactory.getSphereMesh();
         mesh.bind();
         poseStack.pushPose();
         poseStack.mulPose(Axis.YP.rotationDegrees(-yaw));
         poseStack.mulPose(Axis.XP.rotationDegrees(pitch));
         poseStack.pushPose();
         if (isFiring) {
            poseStack.scale(radius, radius, length);
         } else {
            float chargeScale = entity.getSize() * 0.16F;
            poseStack.scale(chargeScale, chargeScale, chargeScale);
         }

         shader.safeGetUniform("ModelViewMat").set(poseStack.last().pose());
         shader.safeGetUniform("alphaMult").set(1.0F * alphaMultiplier);
         shader.apply();
         mesh.drawWithShader(poseStack.last().pose(), proj, shader);
         poseStack.popPose();
         poseStack.popPose();
         VertexBuffer.unbind();
         shader.clear();
      }
   }

   private void renderKiLaserAro(KiLaserEntity entity, float partialTick, PoseStack poseStack, Matrix4f proj, float alphaMultiplier) {
      boolean isFiring = entity.isFiring();
      float width = entity.getSize() * 0.2F;
      float length = isFiring ? entity.getBeamLength() : entity.getSize() * 0.08F;
      float yaw = entity.getFixedYaw();
      float pitch = entity.getFixedPitch();
      float ageInTicks = (float)entity.tickCount + partialTick;
      float[] coreColor = entity.getRgbColorMain();
      float[] borderColor = entity.getRgbColorBorder();
      float[] outlineColor = entity.getRgbColorOutline();
      ShaderInstance shader = DMZShaders.ki3dShader;
      if (shader != null) {
         shader.safeGetUniform("time").set(ageInTicks / 20.0F);
         shader.safeGetUniform("ProjMat").set(proj);
         shader.safeGetUniform("colorCore").set(coreColor[0], coreColor[1], coreColor[2]);
         shader.safeGetUniform("colorBorder").set(borderColor[0], borderColor[1], borderColor[2]);
         shader.safeGetUniform("colorOutline").set(outlineColor[0], outlineColor[1], outlineColor[2]);
         poseStack.pushPose();
         poseStack.mulPose(Axis.YP.rotationDegrees(-yaw));
         poseStack.mulPose(Axis.XP.rotationDegrees(pitch));
         if (!isFiring) {
            VertexBuffer sphereMesh = KiMeshFactory.getSphereMesh();
            sphereMesh.bind();
            poseStack.pushPose();
            float chargeScale = entity.getSize() * 0.16F;
            poseStack.scale(chargeScale, chargeScale, chargeScale);
            shader.safeGetUniform("ModelViewMat").set(poseStack.last().pose());
            shader.safeGetUniform("alphaMult").set(1.0F * alphaMultiplier);
            shader.apply();
            sphereMesh.drawWithShader(poseStack.last().pose(), proj, shader);
            poseStack.popPose();
            VertexBuffer.unbind();
            shader.clear();
            poseStack.popPose();
         } else {
            Matrix4f beamBase = new Matrix4f(poseStack.last().pose());
            VertexBuffer cylinderMesh = KiMeshFactory.getCylinderMesh();
            cylinderMesh.bind();
            poseStack.pushPose();
            poseStack.scale(width, width, length);
            shader.safeGetUniform("ModelViewMat").set(poseStack.last().pose());
            shader.safeGetUniform("alphaMult").set(1.0F * alphaMultiplier);
            shader.apply();
            cylinderMesh.drawWithShader(poseStack.last().pose(), proj, shader);
            poseStack.popPose();
            VertexBuffer.unbind();
            float twistRad = (float)Math.toRadians((double)(-(ageInTicks * 30.0F)));
            Matrix4f spiralMV = new Matrix4f(beamBase).rotateZ(twistRad);
            VertexBuffer spiralMesh = KiSpiralMesh.get(2, entity.getSize(), length, width * 1.5F, width * 0.4F, 0.0F, 0.6F, false);
            spiralMesh.bind();
            shader.safeGetUniform("localPosMode").set(1.0F);
            shader.safeGetUniform("alphaMult").set(1.0F * alphaMultiplier);
            spiralMesh.drawWithShader(spiralMV, proj, shader);
            shader.safeGetUniform("localPosMode").set(0.0F);
            VertexBuffer.unbind();
            VertexBuffer sphereMesh = KiMeshFactory.getSphereMesh();
            sphereMesh.bind();
            poseStack.pushPose();
            poseStack.translate(0.0, 0.0, (double)length);
            float endBallScale = width * 2.5F;
            poseStack.scale(endBallScale, endBallScale, endBallScale);
            shader.safeGetUniform("ModelViewMat").set(poseStack.last().pose());
            shader.safeGetUniform("alphaMult").set(1.0F * alphaMultiplier);
            shader.apply();
            sphereMesh.drawWithShader(poseStack.last().pose(), proj, shader);
            poseStack.popPose();
            VertexBuffer.unbind();
            shader.clear();
            poseStack.popPose();
         }
      }
   }

   private void renderKiMakkankosanpo(KiLaserEntity entity, float partialTick, PoseStack poseStack, Matrix4f proj, float alphaMultiplier) {
      boolean isFiring = entity.isFiring();
      float width = entity.getSize() * 0.15F;
      float length = isFiring ? entity.getBeamLength() : entity.getSize() * 0.08F;
      float yaw = entity.getFixedYaw();
      float pitch = entity.getFixedPitch();
      float ageInTicks = (float)entity.tickCount + partialTick;
      float[] coreColor = entity.getRgbColorMain();
      float[] borderColor = entity.getRgbColorBorder();
      float[] outlineColor = entity.getRgbColorOutline();
      ShaderInstance shader = DMZShaders.ki3dShader;
      if (shader != null) {
         shader.safeGetUniform("time").set(ageInTicks / 20.0F);
         shader.safeGetUniform("ProjMat").set(proj);
         shader.safeGetUniform("colorCore").set(coreColor[0], coreColor[1], coreColor[2]);
         shader.safeGetUniform("colorBorder").set(borderColor[0], borderColor[1], borderColor[2]);
         shader.safeGetUniform("colorOutline").set(outlineColor[0], outlineColor[1], outlineColor[2]);
         poseStack.pushPose();
         poseStack.mulPose(Axis.YP.rotationDegrees(-yaw));
         poseStack.mulPose(Axis.XP.rotationDegrees(pitch));
         if (!isFiring) {
            VertexBuffer sphereMesh = KiMeshFactory.getSphereMesh();
            sphereMesh.bind();
            poseStack.pushPose();
            float chargeScale = entity.getSize() * 0.16F;
            poseStack.scale(chargeScale, chargeScale, chargeScale);
            shader.safeGetUniform("ModelViewMat").set(poseStack.last().pose());
            shader.safeGetUniform("alphaMult").set(1.0F * alphaMultiplier);
            shader.apply();
            sphereMesh.drawWithShader(poseStack.last().pose(), proj, shader);
            poseStack.popPose();
            VertexBuffer.unbind();
            shader.clear();
            poseStack.popPose();
         } else {
            Matrix4f beamBase = new Matrix4f(poseStack.last().pose());
            VertexBuffer cylinderMesh = KiMeshFactory.getCylinderMesh();
            cylinderMesh.bind();
            poseStack.pushPose();
            poseStack.scale(width, width, length);
            shader.safeGetUniform("ModelViewMat").set(poseStack.last().pose());
            shader.safeGetUniform("alphaMult").set(1.0F * alphaMultiplier);
            shader.apply();
            cylinderMesh.drawWithShader(poseStack.last().pose(), proj, shader);
            poseStack.popPose();
            VertexBuffer.unbind();
            float twistRad = (float)Math.toRadians((double)(-(ageInTicks * 30.0F)));
            Matrix4f spiralMV = new Matrix4f(beamBase).rotateZ(twistRad);
            float aroGrosor = width * 0.5F;
            VertexBuffer spiralMesh = KiSpiralMesh.get(1, entity.getSize(), length, width * 2.0F, aroGrosor * 1.3F, aroGrosor, 0.9F, true);
            spiralMesh.bind();
            shader.safeGetUniform("localPosMode").set(1.0F);
            shader.safeGetUniform("alphaMult").set(1.0F * alphaMultiplier);
            spiralMesh.drawWithShader(spiralMV, proj, shader);
            shader.safeGetUniform("localPosMode").set(0.0F);
            VertexBuffer.unbind();
            VertexBuffer sphereMesh = KiMeshFactory.getSphereMesh();
            sphereMesh.bind();
            poseStack.pushPose();
            poseStack.translate(0.0, 0.0, (double)length);
            float endBallScale = width * 1.5F;
            poseStack.scale(endBallScale, endBallScale, endBallScale);
            shader.safeGetUniform("ModelViewMat").set(poseStack.last().pose());
            shader.safeGetUniform("alphaMult").set(1.0F * alphaMultiplier);
            shader.apply();
            sphereMesh.drawWithShader(poseStack.last().pose(), proj, shader);
            poseStack.popPose();
            VertexBuffer.unbind();
            shader.clear();
            poseStack.popPose();
         }
      }
   }

   public ResourceLocation getTextureLocation(KiLaserEntity pEntity) {
      return TEXTURE_KI;
   }
}
