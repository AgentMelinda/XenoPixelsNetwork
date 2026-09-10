package com.dragonminez.common.init.item.weapons.model;

import com.dragonminez.common.init.item.weapons.PowerPoleItem;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class PowerPoleModel extends GeoModel<PowerPoleItem> {
   public ResourceLocation getModelResource(PowerPoleItem animatable) {
      return ResourceLocation.fromNamespaceAndPath("dragonminez", "geo/weapons/power_pole.geo.json");
   }

   public ResourceLocation getTextureResource(PowerPoleItem animatable) {
      return ResourceLocation.fromNamespaceAndPath("dragonminez", "textures/item/weapons/power_pole.png");
   }

   public ResourceLocation getAnimationResource(PowerPoleItem animatable) {
      return null;
   }
}
