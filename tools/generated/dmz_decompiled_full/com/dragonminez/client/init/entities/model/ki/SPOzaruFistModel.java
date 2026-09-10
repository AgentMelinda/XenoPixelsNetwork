package com.dragonminez.client.init.entities.model.ki;

import com.dragonminez.common.init.entities.ki.OzaruFistEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class SPOzaruFistModel<T extends OzaruFistEntity> extends GeoModel<T> {
   public ResourceLocation getModelResource(T animatable) {
      return ResourceLocation.fromNamespaceAndPath("dragonminez", "geo/entity/skills/sp_ozarufist.geo.json");
   }

   public ResourceLocation getTextureResource(T animatable) {
      return ResourceLocation.fromNamespaceAndPath("dragonminez", "textures/entity/skills/sp_ozarufist.png");
   }

   public ResourceLocation getAnimationResource(T animatable) {
      return ResourceLocation.fromNamespaceAndPath("dragonminez", "animations/entity/skills/sp_ozarufist.animation.json");
   }
}
