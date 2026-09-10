package com.dragonminez.client.init.entities.renderer.sagas;

import com.dragonminez.client.init.entities.model.FlyingNimbusModel;
import com.dragonminez.common.init.entities.FlyingNimbusEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class FlyingNimbusRenderer extends GeoEntityRenderer<FlyingNimbusEntity> {
   public FlyingNimbusRenderer(Context renderManager) {
      super(renderManager, new FlyingNimbusModel());
   }

   public RenderType getRenderType(FlyingNimbusEntity animatable, ResourceLocation texture, @Nullable MultiBufferSource bufferSource, float partialTick) {
      return RenderType.entityTranslucent(texture);
   }
}
