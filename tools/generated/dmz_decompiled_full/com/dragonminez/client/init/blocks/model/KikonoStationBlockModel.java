package com.dragonminez.client.init.blocks.model;

import com.dragonminez.common.init.block.entity.KikonoStationBlockEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class KikonoStationBlockModel extends GeoModel<KikonoStationBlockEntity> {
   public ResourceLocation getModelResource(KikonoStationBlockEntity animatable) {
      return ResourceLocation.fromNamespaceAndPath("dragonminez", "geo/block/kikono_station.geo.json");
   }

   public ResourceLocation getTextureResource(KikonoStationBlockEntity animatable) {
      return ResourceLocation.fromNamespaceAndPath("dragonminez", "textures/block/custom/kikono_station.png");
   }

   public ResourceLocation getAnimationResource(KikonoStationBlockEntity animatable) {
      return ResourceLocation.fromNamespaceAndPath("dragonminez", "animations/block/kikono_station.animation.json");
   }
}
