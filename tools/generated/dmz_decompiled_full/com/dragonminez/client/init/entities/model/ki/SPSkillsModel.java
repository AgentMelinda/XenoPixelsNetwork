package com.dragonminez.client.init.entities.model.ki;

import com.dragonminez.common.init.entities.ki.SPBlueHurricaneEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class SPSkillsModel<T extends SPBlueHurricaneEntity> extends GeoModel<T> {
   public ResourceLocation getModelResource(T animatable) {
      String name = BuiltInRegistries.ENTITY_TYPE.getKey(animatable.getType()).getPath();
      return ResourceLocation.fromNamespaceAndPath("dragonminez", "geo/entity/skills/" + name + ".geo.json");
   }

   public ResourceLocation getTextureResource(T animatable) {
      String name = BuiltInRegistries.ENTITY_TYPE.getKey(animatable.getType()).getPath();
      return ResourceLocation.fromNamespaceAndPath("dragonminez", "textures/entity/skills/" + name + ".png");
   }

   public ResourceLocation getAnimationResource(T animatable) {
      String name = BuiltInRegistries.ENTITY_TYPE.getKey(animatable.getType()).getPath();
      return ResourceLocation.fromNamespaceAndPath("dragonminez", "animations/entity/skills/" + name + ".animation.json");
   }
}
