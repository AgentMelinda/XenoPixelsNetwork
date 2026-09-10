package com.dragonminez.client.init.entities.renderer.sagas;

import com.dragonminez.client.init.entities.model.sagas.NamekianModel;
import com.dragonminez.common.init.entities.namek.NamekTraderEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class NamekianRenderer extends GeoEntityRenderer<NamekTraderEntity> {
   public NamekianRenderer(Context renderManager) {
      super(renderManager, new NamekianModel());
      this.shadowRadius = 0.4F;
   }

   public ResourceLocation getTextureLocation(NamekTraderEntity animatable) {
      return animatable.getCurrentTexture();
   }

   public RenderType getRenderType(NamekTraderEntity animatable, ResourceLocation texture, @Nullable MultiBufferSource bufferSource, float partialTick) {
      return RenderType.entityCutout(texture);
   }
}
