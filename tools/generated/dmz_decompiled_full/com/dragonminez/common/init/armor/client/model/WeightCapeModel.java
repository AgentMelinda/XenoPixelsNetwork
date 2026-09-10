package com.dragonminez.common.init.armor.client.model;

import com.dragonminez.common.init.item.WeightItem;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class WeightCapeModel extends GeoModel<WeightItem> {
   public ResourceLocation getModelResource(WeightItem animatable) {
      return ResourceLocation.fromNamespaceAndPath("dragonminez", "geo/armor/weighted_cape.geo.json");
   }

   public ResourceLocation getTextureResource(WeightItem animatable) {
      return ResourceLocation.fromNamespaceAndPath("dragonminez", "textures/entity/races/weighted_items.png");
   }

   public ResourceLocation getAnimationResource(WeightItem animatable) {
      return ResourceLocation.fromNamespaceAndPath("dragonminez", "animations/armorcape.animation.json");
   }
}
