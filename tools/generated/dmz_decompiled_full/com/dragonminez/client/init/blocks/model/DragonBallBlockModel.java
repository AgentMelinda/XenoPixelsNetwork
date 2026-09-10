package com.dragonminez.client.init.blocks.model;

import com.dragonminez.common.dragonball.DragonBallDefinitions;
import com.dragonminez.common.dragonball.DragonBallSetAssetDefinition;
import com.dragonminez.common.init.block.entity.DragonBallBlockEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class DragonBallBlockModel extends GeoModel<DragonBallBlockEntity> {
   public ResourceLocation getModelResource(DragonBallBlockEntity blockEntity) {
      DragonBallSetAssetDefinition assets = DragonBallDefinitions.getBallSet(blockEntity.getBallSetId()) != null
         ? DragonBallDefinitions.getBallSet(blockEntity.getBallSetId()).resolveAssetDefinition()
         : null;
      if (assets != null && assets.getGeoModelPath().isPresent()) {
         return ResourceLocation.parse(assets.getGeoModelPath().get());
      } else {
         String modelName = blockEntity.isNamekian() ? "dballnamek" : "dball";
         return ResourceLocation.fromNamespaceAndPath("dragonminez", "geo/block/" + modelName + ".geo.json");
      }
   }

   public ResourceLocation getTextureResource(DragonBallBlockEntity blockEntity) {
      DragonBallSetAssetDefinition assets = DragonBallDefinitions.getBallSet(blockEntity.getBallSetId()) != null
         ? DragonBallDefinitions.getBallSet(blockEntity.getBallSetId()).resolveAssetDefinition()
         : null;
      if (assets != null && assets.getGeoTexturePathForStar(blockEntity.getBallType().getStars()).isPresent()) {
         return ResourceLocation.parse(assets.getGeoTexturePathForStar(blockEntity.getBallType().getStars()).get());
      } else {
         String prefix = blockEntity.isNamekian() ? "dballnamekblock" : "dballblock";
         int starNumber = blockEntity.getBallType().getStars();
         return ResourceLocation.fromNamespaceAndPath("dragonminez", "textures/block/custom/" + prefix + starNumber + ".png");
      }
   }

   public ResourceLocation getAnimationResource(DragonBallBlockEntity blockEntity) {
      DragonBallSetAssetDefinition assets = DragonBallDefinitions.getBallSet(blockEntity.getBallSetId()) != null
         ? DragonBallDefinitions.getBallSet(blockEntity.getBallSetId()).resolveAssetDefinition()
         : null;
      return assets != null && assets.getAnimationPath().isPresent()
         ? ResourceLocation.parse(assets.getAnimationPath().get())
         : ResourceLocation.fromNamespaceAndPath("dragonminez", "animations/block/dball.animation.json");
   }
}
