package com.dragonminez.client.init.entities.renderer.sagas;

import com.dragonminez.client.init.entities.model.BlackNimbusModel;
import com.dragonminez.common.init.entities.BlackNimbusEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class BlackNimbusRenderer extends GeoEntityRenderer<BlackNimbusEntity> {
   public BlackNimbusRenderer(Context renderManager) {
      super(renderManager, new BlackNimbusModel());
   }

   public RenderType getRenderType(BlackNimbusEntity animatable, ResourceLocation texture, @Nullable MultiBufferSource bufferSource, float partialTick) {
      return RenderType.entityTranslucent(texture);
   }
}
