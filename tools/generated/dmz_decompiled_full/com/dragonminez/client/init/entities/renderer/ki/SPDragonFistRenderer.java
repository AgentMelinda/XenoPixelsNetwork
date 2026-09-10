package com.dragonminez.client.init.entities.renderer.ki;

import com.dragonminez.client.init.entities.model.ki.SPDragonFistModel;
import com.dragonminez.common.init.entities.ki.SPDragonFistEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.util.Color;

public class SPDragonFistRenderer<T extends SPDragonFistEntity> extends GeoEntityRenderer<T> {
   public SPDragonFistRenderer(Context renderManager) {
      super(renderManager, new SPDragonFistModel());
      this.shadowRadius = 0.8F;
   }

   public void render(T entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
      if (entity.isFiring()) {
         poseStack.pushPose();
         float activeTick = (float)entity.tickCount + partialTick;
         float shakeIntensity;
         if (activeTick < (float)entity.getMaxLife() / 2.0F) {
            shakeIntensity = 0.15F;
         } else {
            shakeIntensity = 0.04F;
         }

         float shakeX = (entity.level().random.nextFloat() - 0.5F) * shakeIntensity;
         float shakeY = (entity.level().random.nextFloat() - 0.5F) * shakeIntensity;
         float shakeZ = (entity.level().random.nextFloat() - 0.5F) * shakeIntensity;
         poseStack.translate(shakeX, shakeY, shakeZ);
         poseStack.scale(5.0F, 5.0F, 5.0F);
         super.render(entity, 0.0F, partialTick, poseStack, bufferSource, packedLight);
         poseStack.popPose();
      }
   }

   public RenderType getRenderType(T animatable, ResourceLocation texture, @Nullable MultiBufferSource bufferSource, float partialTick) {
      return RenderType.entityTranslucentEmissive(texture);
   }

   public Color getRenderColor(T animatable, float partialTick, int packedLight) {
      float fadeDuration = 10.0F;
      float activeTick = (float)animatable.tickCount + partialTick;
      float maxActiveLife = (float)animatable.getMaxLife();
      float remainingLife = maxActiveLife - activeTick;
      float alpha = 0.5F;
      if (activeTick < fadeDuration) {
         alpha = activeTick / fadeDuration * 0.8F;
      } else if (remainingLife < fadeDuration) {
         alpha = remainingLife / fadeDuration * 0.8F;
      } else {
         alpha = 0.8F;
      }

      alpha = Mth.clamp(alpha, 0.0F, 0.8F);
      return Color.ofRGBA(1.0F, 1.0F, 1.0F, alpha);
   }
}
