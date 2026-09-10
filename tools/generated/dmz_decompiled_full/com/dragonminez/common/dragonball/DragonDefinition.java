package com.dragonminez.common.dragonball;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

public class DragonDefinition {
   private final String id;
   private final String entityRegistryName;
   private final float entityWidth;
   private final float entityHeight;
   private final Set<ResourceLocation> validDimensions;
   private final String ballSetId;
   private final String wishScreenId;
   private final int wishCount;
   private final String assetDefinitionId;

   public DragonDefinition(
      String id,
      String entityRegistryName,
      float entityWidth,
      float entityHeight,
      Set<ResourceLocation> validDimensions,
      String ballSetId,
      String wishScreenId,
      int wishCount
   ) {
      this(id, entityRegistryName, entityWidth, entityHeight, validDimensions, ballSetId, wishScreenId, wishCount, null);
   }

   public DragonDefinition(
      String id,
      String entityRegistryName,
      float entityWidth,
      float entityHeight,
      Set<ResourceLocation> validDimensions,
      String ballSetId,
      String wishScreenId,
      int wishCount,
      String assetDefinitionId
   ) {
      this.id = id;
      this.entityRegistryName = entityRegistryName;
      this.entityWidth = entityWidth;
      this.entityHeight = entityHeight;
      this.validDimensions = Set.copyOf(validDimensions);
      this.ballSetId = ballSetId != null && !ballSetId.isBlank() ? ballSetId : null;
      this.wishScreenId = wishScreenId;
      this.wishCount = wishCount;
      this.assetDefinitionId = assetDefinitionId != null && !assetDefinitionId.isBlank() ? assetDefinitionId : null;
   }

   public String getId() {
      return this.id;
   }

   public String getEntityRegistryName() {
      return this.entityRegistryName;
   }

   public float getEntityWidth() {
      return this.entityWidth;
   }

   public float getEntityHeight() {
      return this.entityHeight;
   }

   public boolean supportsDimension(ResourceKey<Level> dimension) {
      return this.validDimensions.contains(dimension.location());
   }

   public boolean supportsBallSet(String setId) {
      return this.ballSetId != null && this.ballSetId.equals(setId);
   }

   public String getBallSetId() {
      return this.ballSetId;
   }

   public Set<String> getValidBallSetIds() {
      return this.ballSetId == null ? Set.of() : Set.of(this.ballSetId);
   }

   public String getWishScreenId() {
      return this.wishScreenId;
   }

   public int getWishCount() {
      return this.wishCount;
   }

   public Optional<String> getAssetDefinitionId() {
      return Optional.ofNullable(this.assetDefinitionId);
   }

   public DragonAssetDefinition resolveAssetDefinition() {
      return this.assetDefinitionId == null ? null : DragonBallDefinitions.getDragonAsset(this.assetDefinitionId);
   }

   public JsonObject toJson() {
      JsonObject root = new JsonObject();
      root.addProperty("id", this.id);
      root.addProperty("entity_registry_name", this.entityRegistryName);
      root.addProperty("entity_width", this.entityWidth);
      root.addProperty("entity_height", this.entityHeight);
      JsonArray dimensions = new JsonArray();

      for (ResourceLocation dimension : this.validDimensions) {
         dimensions.add(dimension.toString());
      }

      root.add("dimensions", dimensions);
      root.addProperty("ball_set", this.ballSetId);
      root.addProperty("wish_screen_id", this.wishScreenId);
      root.addProperty("wish_count", this.wishCount);
      if (this.assetDefinitionId != null) {
         root.addProperty("asset_definition", this.assetDefinitionId);
      }

      return root;
   }

   public static DragonDefinition fromJson(JsonObject root) {
      String id = root.get("id").getAsString();
      String entityRegistryName = root.get("entity_registry_name").getAsString();
      float entityWidth = root.get("entity_width").getAsFloat();
      float entityHeight = root.get("entity_height").getAsFloat();
      Set<ResourceLocation> dimensions = new LinkedHashSet<>();

      for (JsonElement element : root.getAsJsonArray("dimensions")) {
         dimensions.add(ResourceLocation.parse(element.getAsString()));
      }

      String ballSetId = root.get("ball_set").getAsString();
      String wishScreenId = root.get("wish_screen_id").getAsString();
      int wishCount = root.get("wish_count").getAsInt();
      String assetDefinitionId = root.has("asset_definition") ? root.get("asset_definition").getAsString() : null;
      return new DragonDefinition(id, entityRegistryName, entityWidth, entityHeight, dimensions, ballSetId, wishScreenId, wishCount, assetDefinitionId);
   }
}
