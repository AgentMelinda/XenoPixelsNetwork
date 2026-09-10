package com.dragonminez.client.init.entities.model;

import com.dragonminez.common.init.entities.MajinSkillEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class MajinSkillModel<T extends MajinSkillEntity> extends GeoModel<T> {
   public ResourceLocation getModelResource(T animatable) {
      return ResourceLocation.fromNamespaceAndPath("dragonminez", "geo/entity/skills/majinskill.geo.json");
   }

   public ResourceLocation getTextureResource(T animatable) {
      return ResourceLocation.fromNamespaceAndPath("dragonminez", "textures/entity/ki/ki_laser.png");
   }

   public ResourceLocation getAnimationResource(T animatable) {
      return ResourceLocation.fromNamespaceAndPath("dragonminez", "animations/entity/skills/majinskill.animation.json");
   }
}
