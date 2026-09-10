package com.dragonminez.client.init.blocks.renderer;

import com.dragonminez.client.init.blocks.model.FuelGeneratorBlockModel;
import com.dragonminez.common.init.block.entity.FuelGeneratorBlockEntity;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider.Context;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

public class FuelGeneratorBlockRenderer extends GeoBlockRenderer<FuelGeneratorBlockEntity> {
   public FuelGeneratorBlockRenderer(Context context) {
      super(new FuelGeneratorBlockModel());
   }
}
