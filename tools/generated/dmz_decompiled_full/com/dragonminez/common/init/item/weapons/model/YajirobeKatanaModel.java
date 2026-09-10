package com.dragonminez.common.init.item.weapons.model;

import com.dragonminez.common.init.item.weapons.YajirobeKatanaItem;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class YajirobeKatanaModel extends GeoModel<YajirobeKatanaItem> {
   public ResourceLocation getModelResource(YajirobeKatanaItem animatable) {
      return ResourceLocation.fromNamespaceAndPath("dragonminez", "geo/weapons/yajirobe_katana.geo.json");
   }

   public ResourceLocation getTextureResource(YajirobeKatanaItem animatable) {
      return ResourceLocation.fromNamespaceAndPath("dragonminez", "textures/item/weapons/yajirobe_katana.png");
   }

   public ResourceLocation getAnimationResource(YajirobeKatanaItem animatable) {
      return null;
   }
}
