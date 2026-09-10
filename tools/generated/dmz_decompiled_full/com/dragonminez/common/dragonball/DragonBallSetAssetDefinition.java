package com.dragonminez.common.dragonball;

import com.google.gson.JsonObject;
import java.util.Optional;

public class DragonBallSetAssetDefinition {
   private final String id;
   private final String flatTexturePrefixPath;
   private final String inventoryTextureFormat;
   private final String geoModelPath;
   private final String animationPath;
   private final String geoTexturePrefixPath;
   private final String blockModelTemplatePath;
   private final String itemModelTemplatePath;

   public DragonBallSetAssetDefinition(
      String id,
      String flatTexturePrefixPath,
      String inventoryTextureFormat,
      String geoModelPath,
      String animationPath,
      String geoTexturePrefixPath,
      String blockModelTemplatePath,
      String itemModelTemplatePath
   ) {
      this.id = id;
      this.flatTexturePrefixPath = blankToNull(flatTexturePrefixPath);
      this.inventoryTextureFormat = blankToNull(inventoryTextureFormat);
      this.geoModelPath = blankToNull(geoModelPath);
      this.animationPath = blankToNull(animationPath);
      this.geoTexturePrefixPath = blankToNull(geoTexturePrefixPath);
      this.blockModelTemplatePath = blankToNull(blockModelTemplatePath);
      this.itemModelTemplatePath = blankToNull(itemModelTemplatePath);
   }

   public String getId() {
      return this.id;
   }

   public Optional<String> getFlatTexturePrefixPath() {
      return Optional.ofNullable(this.flatTexturePrefixPath);
   }

   public Optional<String> getInventoryTextureFormat() {
      return Optional.ofNullable(this.inventoryTextureFormat);
   }

   public Optional<String> getGeoModelPath() {
      return Optional.ofNullable(this.geoModelPath);
   }

   public Optional<String> getAnimationPath() {
      return Optional.ofNullable(this.animationPath);
   }

   public Optional<String> getGeoTexturePrefixPath() {
      return Optional.ofNullable(this.geoTexturePrefixPath);
   }

   public Optional<String> getBlockModelTemplatePath() {
      return Optional.ofNullable(this.blockModelTemplatePath);
   }

   public Optional<String> getItemModelTemplatePath() {
      return Optional.ofNullable(this.itemModelTemplatePath);
   }

   public Optional<String> getFlatTexturePathForStar(int star) {
      return this.flatTexturePrefixPath == null ? Optional.empty() : Optional.of(this.flatTexturePrefixPath + star);
   }

   public Optional<String> getInventoryTexturePathForStar(int star) {
      return this.inventoryTextureFormat == null ? Optional.empty() : Optional.of(String.format(this.inventoryTextureFormat, star));
   }

   public Optional<String> getGeoTexturePathForStar(int star) {
      return this.geoTexturePrefixPath == null ? Optional.empty() : Optional.of(this.geoTexturePrefixPath + star + ".png");
   }

   public JsonObject toJson() {
      JsonObject root = new JsonObject();
      root.addProperty("id", this.id);
      if (this.flatTexturePrefixPath != null) {
         root.addProperty("flat_texture_prefix", this.flatTexturePrefixPath);
      }

      if (this.inventoryTextureFormat != null) {
         root.addProperty("inventory_texture_format", this.inventoryTextureFormat);
      }

      if (this.geoModelPath != null) {
         root.addProperty("geo_model", this.geoModelPath);
      }

      if (this.animationPath != null) {
         root.addProperty("animation", this.animationPath);
      }

      if (this.geoTexturePrefixPath != null) {
         root.addProperty("geo_texture_prefix", this.geoTexturePrefixPath);
      }

      if (this.blockModelTemplatePath != null) {
         root.addProperty("block_model_template", this.blockModelTemplatePath);
      }

      if (this.itemModelTemplatePath != null) {
         root.addProperty("item_model_template", this.itemModelTemplatePath);
      }

      return root;
   }

   public static DragonBallSetAssetDefinition fromJson(JsonObject root) {
      String inventoryFormat = root.has("inventory_texture_format") ? root.get("inventory_texture_format").getAsString() : null;
      return new DragonBallSetAssetDefinition(
         root.get("id").getAsString(),
         root.has("flat_texture_prefix") ? root.get("flat_texture_prefix").getAsString() : null,
         inventoryFormat,
         root.has("geo_model") ? root.get("geo_model").getAsString() : null,
         root.has("animation") ? root.get("animation").getAsString() : null,
         root.has("geo_texture_prefix") ? root.get("geo_texture_prefix").getAsString() : null,
         root.has("block_model_template") ? root.get("block_model_template").getAsString() : null,
         root.has("item_model_template") ? root.get("item_model_template").getAsString() : null
      );
   }

   private static String blankToNull(String value) {
      return value != null && !value.isBlank() ? value : null;
   }
}
