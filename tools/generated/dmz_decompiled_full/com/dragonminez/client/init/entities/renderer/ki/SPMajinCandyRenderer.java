package com.dragonminez.client.init.entities.renderer.ki;

import com.dragonminez.client.render.effects.AuraRenderer;
import com.dragonminez.client.render.shader.DMZShaders;
import com.dragonminez.client.render.util.ModRenderTypes;
import com.dragonminez.common.init.entities.ki.SPMajinCandyEntity;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.math.Axis;
import java.util.Random;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

public class SPMajinCandyRenderer extends EntityRenderer<SPMajinCandyEntity> {
   private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("dragonminez", "textures/entity/races/null.png");

   public SPMajinCandyRenderer(Context pContext) {
      super(pContext);
   }

   public void render(SPMajinCandyEntity entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
      super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
      float ageInTicks = (float)entity.tickCount + partialTick;
      boolean isFiring = entity.isFiring();
      float[] color = new float[]{1.0F, 0.4F, 0.8F};
      float alpha = 1.0F;
      if (!isFiring) {
         this.renderCastingLightning(poseStack, entity, buffer, color, alpha, ageInTicks);
      } else {
         LivingEntity target = entity.getTargetEntity();
         if (target != null && target.isAlive()) {
            this.renderBeamLightning(poseStack, entity, target, buffer, color, alpha, ageInTicks, partialTick);
         }
      }
   }

   private void renderCastingLightning(PoseStack poseStack, SPMajinCandyEntity entity, MultiBufferSource buffer, float[] color, float alpha, float ageInTicks) {
      ShaderInstance shader = DMZShaders.lightningShader;
      VertexBuffer mesh = AuraRenderer.getLightningMesh();
      if (shader != null && mesh != null) {
         this.setupShader(shader, color, alpha, ageInTicks, false);
         RenderType lightningType = ModRenderTypes.getCustomLightning(TEXTURE);
         lightningType.setupRenderState();
         shader.apply();
         mesh.bind();
         Random seededRand = new Random((long)(entity.getId() + (int)ageInTicks * 5));
         float dynamicWidth = 0.6F;

         for (int i = 0; i < 4; i++) {
            poseStack.pushPose();
            poseStack.translate(0.0, -0.2, 0.0);
            poseStack.mulPose(Axis.XP.rotationDegrees(seededRand.nextFloat() * 360.0F));
            poseStack.mulPose(Axis.YP.rotationDegrees(seededRand.nextFloat() * 360.0F));
            poseStack.mulPose(Axis.ZP.rotationDegrees(seededRand.nextFloat() * 360.0F));
            float individualScale = dynamicWidth * (0.8F + seededRand.nextFloat() * 0.4F);
            poseStack.scale(individualScale, individualScale, individualScale);
            shader.safeGetUniform("modelMatrix").set(poseStack.last().pose());
            shader.safeGetUniform("normalMatrix").set(new Matrix4f(new Matrix3f(poseStack.last().normal())));
            shader.apply();
            mesh.drawWithShader(poseStack.last().pose(), RenderSystem.getProjectionMatrix(), shader);
            poseStack.popPose();
         }

         VertexBuffer.unbind();
         shader.clear();
         lightningType.clearRenderState();
      }
   }

   private void renderBeamLightning(
      PoseStack poseStack,
      SPMajinCandyEntity entity,
      LivingEntity target,
      MultiBufferSource buffer,
      float[] color,
      float alpha,
      float ageInTicks,
      float partialTick
   ) {
      ShaderInstance shader = DMZShaders.lightningShader;
      VertexBuffer mesh = AuraRenderer.getLightningMesh();
      if (shader != null && mesh != null) {
         Vec3 startPos = entity.getPosition(partialTick);
         Vec3 targetPos = target.getEyePosition(partialTick);
         double dx = targetPos.x - startPos.x;
         double dy = targetPos.y - startPos.y;
         double dz = targetPos.z - startPos.z;
         float distance = (float)Math.sqrt(dx * dx + dy * dy + dz * dz);
         float horizontalDistance = (float)Math.sqrt(dx * dx + dz * dz);
         float yaw = (float)(Mth.atan2(dz, dx) * 180.0F / (float)Math.PI) - 90.0F;
         float pitch = (float)(-(Mth.atan2(dy, (double)horizontalDistance) * 180.0F / (float)Math.PI));
         this.setupShader(shader, color, alpha, ageInTicks, true);
         RenderType lightningType = ModRenderTypes.getCustomLightning(TEXTURE);
         lightningType.setupRenderState();
         shader.apply();
         mesh.bind();
         Random seededRand = new Random((long)(entity.getId() + (int)ageInTicks * 5));
         float thickness = 0.8F;

         for (int i = 0; i < 6; i++) {
            poseStack.pushPose();
            poseStack.mulPose(Axis.YP.rotationDegrees(yaw));
            poseStack.mulPose(Axis.XP.rotationDegrees(pitch));
            poseStack.mulPose(Axis.ZP.rotationDegrees(seededRand.nextFloat() * 360.0F));
            float scaleX = thickness * (0.8F + seededRand.nextFloat() * 0.4F);
            float scaleY = thickness * (0.8F + seededRand.nextFloat() * 0.4F);
            poseStack.scale(scaleX, scaleY, distance);
            shader.safeGetUniform("modelMatrix").set(poseStack.last().pose());
            shader.safeGetUniform("normalMatrix").set(new Matrix4f(new Matrix3f(poseStack.last().normal())));
            shader.apply();
            mesh.drawWithShader(poseStack.last().pose(), RenderSystem.getProjectionMatrix(), shader);
            poseStack.popPose();
         }

         VertexBuffer.unbind();
         shader.clear();
         lightningType.clearRenderState();
      }
   }

   private void setupShader(ShaderInstance shader, float[] color, float alpha, float ageInTicks, boolean isFiring) {
      shader.safeGetUniform("time").set(ageInTicks / 20.0F);
      shader.safeGetUniform("speedModifier").set(isFiring ? 2.5F : 1.5F);
      shader.safeGetUniform("color1").set(1.0F, 1.0F, 1.0F);
      shader.safeGetUniform("color2").set(color[0], color[1], color[2]);
      shader.safeGetUniform("alp1").set(alpha);
      shader.safeGetUniform("alp2").set(0.1F * alpha);
      shader.safeGetUniform("projectionMatrix").set(RenderSystem.getProjectionMatrix());
   }

   public ResourceLocation getTextureLocation(SPMajinCandyEntity entity) {
      return TEXTURE;
   }
}
