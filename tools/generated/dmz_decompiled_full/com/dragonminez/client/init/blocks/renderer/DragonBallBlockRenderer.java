package com.dragonminez.client.init.blocks.renderer;

import com.dragonminez.client.init.blocks.model.DragonBallBlockModel;
import com.dragonminez.common.init.block.entity.DragonBallBlockEntity;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider.Context;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

public class DragonBallBlockRenderer extends GeoBlockRenderer<DragonBallBlockEntity> {
   public DragonBallBlockRenderer(Context context) {
      super(new DragonBallBlockModel());
   }
}
