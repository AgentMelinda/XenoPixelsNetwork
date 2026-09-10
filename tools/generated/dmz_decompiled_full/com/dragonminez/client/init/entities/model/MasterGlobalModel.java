package com.dragonminez.client.init.entities.model;

import com.dragonminez.common.init.entities.MastersEntity;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.model.data.EntityModelData;

public class MasterGlobalModel<T extends MastersEntity> extends GeoModel<T> {
   private static final Map<String, Boolean> MODEL_CACHE = new HashMap<>();
   private static final Map<String, Boolean> TEXTURE_CACHE = new HashMap<>();
   private static final Map<String, Boolean> ANIM_CACHE = new HashMap<>();

   public ResourceLocation getModelResource(T animatable) {
      String name = BuiltInRegistries.ENTITY_TYPE.getKey(animatable.getType()).getPath();
      ResourceLocation original = ResourceLocation.fromNamespaceAndPath("dragonminez", "geo/entity/master/" + name + ".geo.json");
      boolean exists = MODEL_CACHE.computeIfAbsent(name, k -> this.resourceExists(original));
      return exists ? original : ResourceLocation.fromNamespaceAndPath("dragonminez", "geo/entity/enemies/robotxv.geo.json");
   }

   public ResourceLocation getTextureResource(T animatable) {
      String name = BuiltInRegistries.ENTITY_TYPE.getKey(animatable.getType()).getPath();
      ResourceLocation original = ResourceLocation.fromNamespaceAndPath("dragonminez", "textures/entity/master/" + name + ".png");
      boolean exists = TEXTURE_CACHE.computeIfAbsent(name, k -> this.resourceExists(original));
      return exists ? original : ResourceLocation.fromNamespaceAndPath("dragonminez", "textures/entity/enemies/robotxv.png");
   }

   public ResourceLocation getAnimationResource(T animatable) {
      String name = BuiltInRegistries.ENTITY_TYPE.getKey(animatable.getType()).getPath();
      ResourceLocation original = ResourceLocation.fromNamespaceAndPath("dragonminez", "animations/entity/master/" + name + ".animation.json");
      boolean exists = ANIM_CACHE.computeIfAbsent(name, k -> this.resourceExists(original));
      return exists ? original : ResourceLocation.fromNamespaceAndPath("dragonminez", "animations/entity/master/master_goku.animation.json");
   }

   public void setCustomAnimations(T animatable, long instanceId, AnimationState<T> animationState) {
      GeoBone head = this.getAnimationProcessor().getBone("head");
      if (head != null) {
         EntityModelData entityData = (EntityModelData)animationState.getData(DataTickets.ENTITY_MODEL_DATA);
         head.setRotX(entityData.headPitch() * (float) (Math.PI / 180.0));
         head.setRotY(entityData.netHeadYaw() * (float) (Math.PI / 180.0));
      }
   }

   private boolean resourceExists(ResourceLocation location) {
      return Minecraft.getInstance().getResourceManager().getResource(location).isPresent();
   }
}
