package com.dragonminez.client.init.entities.model;

import com.dragonminez.common.init.entities.animal.DinoGlobalEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.model.data.EntityModelData;

public class DinoGlobalModel<T extends DinoGlobalEntity> extends GeoModel<T> {
   public ResourceLocation getModelResource(T animatable) {
      String name = BuiltInRegistries.ENTITY_TYPE.getKey(animatable.getType()).getPath();
      return ResourceLocation.fromNamespaceAndPath("dragonminez", "geo/entity/animal/" + name + ".geo.json");
   }

   public ResourceLocation getTextureResource(T animatable) {
      String name = BuiltInRegistries.ENTITY_TYPE.getKey(animatable.getType()).getPath();
      return ResourceLocation.fromNamespaceAndPath("dragonminez", "textures/entity/animal/" + name + ".png");
   }

   public ResourceLocation getAnimationResource(T animatable) {
      String name = BuiltInRegistries.ENTITY_TYPE.getKey(animatable.getType()).getPath();
      return ResourceLocation.fromNamespaceAndPath("dragonminez", "animations/entity/animal/" + name + ".animation.json");
   }

   public void setCustomAnimations(T animatable, long instanceId, AnimationState<T> animationState) {
      GeoBone head = this.getAnimationProcessor().getBone("head");
      if (head != null) {
         EntityModelData entityData = (EntityModelData)animationState.getData(DataTickets.ENTITY_MODEL_DATA);
         head.setRotX(entityData.headPitch() * (float) (Math.PI / 180.0));
         head.setRotY(entityData.netHeadYaw() * (float) (Math.PI / 180.0));
      }
   }
}
