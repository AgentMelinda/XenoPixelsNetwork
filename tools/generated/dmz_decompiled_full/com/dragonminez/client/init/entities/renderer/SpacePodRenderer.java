package com.dragonminez.client.init.entities.renderer;

import com.dragonminez.client.init.entities.model.SpacePodModel;
import com.dragonminez.common.init.entities.SpacePodEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class SpacePodRenderer extends GeoEntityRenderer<SpacePodEntity> {
   public SpacePodRenderer(Context renderManager) {
      super(renderManager, new SpacePodModel());
   }

   public void render(SpacePodEntity entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
      poseStack.pushPose();
      poseStack.scale(1.1F, 1.1F, 1.1F);
      super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
      poseStack.popPose();
   }

   public RenderType getRenderType(SpacePodEntity animatable, ResourceLocation texture, @Nullable MultiBufferSource bufferSource, float partialTick) {
      return RenderType.entityTranslucent(texture);
   }
}
