package com.dragonminez.client.render.layer;

import com.dragonminez.client.render.util.RenderBufferUtil;
import com.dragonminez.common.init.entities.redribbon.RedRibbonSoldierEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

public class RedRibbonOutfitLayer<T extends RedRibbonSoldierEntity> extends GeoRenderLayer<T> {
   private static final ResourceLocation OUTFIT_TEXTURE = ResourceLocation.fromNamespaceAndPath("dragonminez", "textures/entity/enemies/redribbon_outfit.png");

   public RedRibbonOutfitLayer(GeoRenderer<T> renderer) {
      super(renderer);
   }

   public void render(
      PoseStack poseStack,
      T animatable,
      BakedGeoModel bakedModel,
      RenderType renderType,
      MultiBufferSource bufferSource,
      VertexConsumer buffer,
      float partialTick,
      int packedLight,
      int packedOverlay
   ) {
      RenderType outfitType = RenderType.entityCutoutNoCull(OUTFIT_TEXTURE);
      this.getRenderer()
         .reRender(
            bakedModel,
            poseStack,
            bufferSource,
            animatable,
            outfitType,
            bufferSource.getBuffer(outfitType),
            partialTick,
            packedLight,
            packedOverlay,
            RenderBufferUtil.packColor(1.0F, 1.0F, 1.0F, 1.0F)
         );
   }
}
