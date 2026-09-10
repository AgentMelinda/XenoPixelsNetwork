package com.dragonminez.client.init.entities.renderer;

import com.dragonminez.client.init.entities.model.MasterGlobalModel;
import com.dragonminez.common.init.entities.MastersEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class MasterEntityRenderer<T extends MastersEntity> extends GeoEntityRenderer<T> {
   public MasterEntityRenderer(Context renderManager) {
      super(renderManager, new MasterGlobalModel());
      this.shadowRadius = 0.4F;
   }

   public void render(T entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
      float sc = entity.getScale();
      this.shadowRadius = 0.4F * sc;
      poseStack.pushPose();
      poseStack.scale(sc, sc, sc);
      super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
      poseStack.popPose();
   }

   public RenderType getRenderType(T animatable, ResourceLocation texture, @Nullable MultiBufferSource bufferSource, float partialTick) {
      return RenderType.entityCutout(texture);
   }
}
