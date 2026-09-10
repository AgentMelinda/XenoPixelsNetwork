package com.dragonminez.client.init.entities.renderer;

import com.dragonminez.client.init.entities.model.DinoFlyModel;
import com.dragonminez.common.init.entities.animal.DinoFlyEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class DinoFlyRenderer extends GeoEntityRenderer<DinoFlyEntity> {
   public DinoFlyRenderer(Context renderManager) {
      super(renderManager, new DinoFlyModel());
      this.shadowRadius = 0.8F;
   }
}
