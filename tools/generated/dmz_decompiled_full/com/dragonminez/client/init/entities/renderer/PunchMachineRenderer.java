package com.dragonminez.client.init.entities.renderer;

import com.dragonminez.client.init.entities.model.PunchMachineModel;
import com.dragonminez.common.init.entities.PunchMachineEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class PunchMachineRenderer extends GeoEntityRenderer<PunchMachineEntity> {
   public PunchMachineRenderer(Context renderManager) {
      super(renderManager, new PunchMachineModel());
   }
}
