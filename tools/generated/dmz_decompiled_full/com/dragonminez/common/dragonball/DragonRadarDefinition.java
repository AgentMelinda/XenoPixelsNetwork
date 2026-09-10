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

public class DragonRadarDefinition {
   private final String id;
   private final String itemRegistryName;
   private final Set<ResourceLocation> validDimensions;
   private final String ballSetId;
   private final String tooltipKey;
   private final int[] ranges;
   private final String recipeDefinitionId;
   private final String assetDefinitionId;
   private final String chipItemId;
   private final String cpuItemId;
   private final String chipModelItemId;
   private final String cpuModelItemId;
   private final String displayName;

   public DragonRadarDefinition(String id, String itemRegistryName, Set<ResourceLocation> validDimensions, String ballSetId, String tooltipKey, int[] ranges) {
      this(id, itemRegistryName, validDimensions, ballSetId, tooltipKey, ranges, null, null, null, null, null, null, null);
   }

   public DragonRadarDefinition(
      String id,
      String itemRegistryName,
      Set<ResourceLocation> validDimensions,
      String ballSetId,
      String tooltipKey,
      int[] ranges,
      String recipeDefinitionId,
      String assetDefinitionId
   ) {
      this(id, itemRegistryName, validDimensions, ballSetId, tooltipKey, ranges, recipeDefinitionId, assetDefinitionId, null, null, null, null, null);
   }

   public DragonRadarDefinition(
      String id,
      String itemRegistryName,
      Set<ResourceLocation> validDimensions,
      String ballSetId,
      String tooltipKey,
      int[] ranges,
      String recipeDefinitionId,
      String assetDefinitionId,
      String displayName
   ) {
      this(id, itemRegistryName, validDimensions, ballSetId, tooltipKey, ranges, recipeDefinitionId, assetDefinitionId, null, null, null, null, displayName);
   }

   public DragonRadarDefinition(
      String id,
      String itemRegistryName,
      Set<ResourceLocation> validDimensions,
      String ballSetId,
      String tooltipKey,
      int[] ranges,
      String recipeDefinitionId,
      String assetDefinitionId,
      String chipItemId,
      String cpuItemId,
      String displayName
   ) {
      this(
         id,
         itemRegistryName,
         validDimensions,
         ballSetId,
         tooltipKey,
         ranges,
         recipeDefinitionId,
         assetDefinitionId,
         chipItemId,
         cpuItemId,
         null,
         null,
         displayName
      );
   }

   public DragonRadarDefinition(
      String id,
      String itemRegistryName,
      Set<ResourceLocation> validDimensions,
      String ballSetId,
      String tooltipKey,
      int[] ranges,
      String recipeDefinitionId,
      String assetDefinitionId,
      String chipItemId,
      String cpuItemId,
      String chipModelItemId,
      String cpuModelItemId,
      String displayName
   ) {
      this.id = id;
      this.itemRegistryName = itemRegistryName;
      this.validDimensions = Set.copyOf(validDimensions);
      this.ballSetId = ballSetId != null && !ballSetId.isBlank() ? ballSetId : null;
      this.tooltipKey = tooltipKey;
      this.ranges = ranges == null ? new int[0] : (int[])ranges.clone();
      this.recipeDefinitionId = recipeDefinitionId != null && !recipeDefinitionId.isBlank() ? recipeDefinitionId : null;
      this.assetDefinitionId = assetDefinitionId != null && !assetDefinitionId.isBlank() ? assetDefinitionId : null;
      this.chipItemId = chipItemId != null && !chipItemId.isBlank() ? chipItemId : null;
      this.cpuItemId = cpuItemId != null && !cpuItemId.isBlank() ? cpuItemId : null;
      this.chipModelItemId = chipModelItemId != null && !chipModelItemId.isBlank() ? chipModelItemId : null;
      this.cpuModelItemId = cpuModelItemId != null && !cpuModelItemId.isBlank() ? cpuModelItemId : null;
      this.displayName = displayName != null && !displayName.isBlank() ? displayName : null;
   }

   public String getId() {
      return this.id;
   }

   public String getItemRegistryName() {
      return this.itemRegistryName;
   }

   public Set<ResourceLocation> getValidDimensions() {
      return this.validDimensions;
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

   public String getTooltipKey() {
      return this.tooltipKey;
   }

   public int[] getRanges() {
      return (int[])this.ranges.clone();
   }

   public Optional<String> getRecipeDefinitionId() {
      return Optional.ofNullable(this.recipeDefinitionId);
   }

   public Optional<String> getChipItemId() {
      return Optional.ofNullable(this.chipItemId);
   }

   public Optional<String> getCpuItemId() {
      return Optional.ofNullable(this.cpuItemId);
   }

   public Optional<String> getChipModelItemId() {
      return Optional.ofNullable(this.chipModelItemId);
   }

   public Optional<String> getCpuModelItemId() {
      return Optional.ofNullable(this.cpuModelItemId);
   }

   public Optional<String> getChipRegistryName() {
      return this.itemIdToRegistryName(this.chipItemId);
   }

   public Optional<String> getCpuRegistryName() {
      return this.itemIdToRegistryName(this.cpuItemId);
   }

   public DragonRadarRecipeDefinition resolveRecipeDefinition() {
      if (this.recipeDefinitionId != null) {
         return DragonBallDefinitions.getRadarRecipe(this.recipeDefinitionId);
      } else {
         return this.chipItemId != null && this.cpuItemId != null
            ? new ShapedDragonRadarRecipeDefinition(this.id + "_auto_recipe", this.chipItemId, this.cpuItemId)
            : null;
      }
   }

   public Optional<String> getAssetDefinitionId() {
      return Optional.ofNullable(this.assetDefinitionId);
   }

   public Optional<String> getDisplayName() {
      return Optional.ofNullable(this.displayName);
   }

   public DragonRadarAssetDefinition resolveAssetDefinition() {
      return this.assetDefinitionId == null ? null : DragonBallDefinitions.getRadarAsset(this.assetDefinitionId);
   }

   public JsonObject toJson() {
      JsonObject root = new JsonObject();
      root.addProperty("id", this.id);
      root.addProperty("item_registry_name", this.itemRegistryName);
      JsonArray dimensions = new JsonArray();

      for (ResourceLocation dimension : this.validDimensions) {
         dimensions.add(dimension.toString());
      }

      root.add("dimensions", dimensions);
      if (this.ballSetId != null) {
         root.addProperty("ball_set", this.ballSetId);
      }

      root.addProperty("tooltip_key", this.tooltipKey);
      JsonArray rangesArray = new JsonArray();

      for (int range : this.ranges) {
         rangesArray.add(range);
      }

      root.add("ranges", rangesArray);
      if (this.recipeDefinitionId != null) {
         root.addProperty("recipe_definition", this.recipeDefinitionId);
      }

      if (this.assetDefinitionId != null) {
         root.addProperty("asset_definition", this.assetDefinitionId);
      }

      if (this.chipItemId != null) {
         root.addProperty("chip_item", this.chipItemId);
      }

      if (this.cpuItemId != null) {
         root.addProperty("cpu_item", this.cpuItemId);
      }

      if (this.chipModelItemId != null) {
         root.addProperty("chip_model_item", this.chipModelItemId);
      }

      if (this.cpuModelItemId != null) {
         root.addProperty("cpu_model_item", this.cpuModelItemId);
      }

      if (this.displayName != null) {
         root.addProperty("display_name", this.displayName);
      }

      return root;
   }

   private Optional<String> itemIdToRegistryName(String itemId) {
      if (itemId != null && !itemId.isBlank()) {
         ResourceLocation rl = ResourceLocation.tryParse(itemId);
         if (rl == null) {
            return Optional.empty();
         } else {
            return !"dragonminez".equals(rl.getNamespace()) ? Optional.empty() : Optional.of(rl.getPath());
         }
      } else {
         return Optional.empty();
      }
   }

   public static DragonRadarDefinition fromJson(JsonObject root) {
      String id = root.get("id").getAsString();
      String itemRegistryName = root.get("item_registry_name").getAsString();
      Set<ResourceLocation> dimensions = new LinkedHashSet<>();

      for (JsonElement element : root.getAsJsonArray("dimensions")) {
         dimensions.add(ResourceLocation.parse(element.getAsString()));
      }

      String ballSetId = root.get("ball_set").getAsString();
      String tooltipKey = root.get("tooltip_key").getAsString();
      JsonArray rangesArray = root.getAsJsonArray("ranges");
      int[] ranges = new int[rangesArray.size()];

      for (int i = 0; i < rangesArray.size(); i++) {
         ranges[i] = rangesArray.get(i).getAsInt();
      }

      String recipeDefinitionId = root.has("recipe_definition") ? root.get("recipe_definition").getAsString() : null;
      String assetDefinitionId = root.has("asset_definition") ? root.get("asset_definition").getAsString() : null;
      String chipItemId = root.has("chip_item") ? root.get("chip_item").getAsString() : null;
      String cpuItemId = root.has("cpu_item") ? root.get("cpu_item").getAsString() : null;
      String chipModelItemId = root.has("chip_model_item") ? root.get("chip_model_item").getAsString() : null;
      String cpuModelItemId = root.has("cpu_model_item") ? root.get("cpu_model_item").getAsString() : null;
      String displayName = root.has("display_name") ? root.get("display_name").getAsString() : null;
      return new DragonRadarDefinition(
         id,
         itemRegistryName,
         dimensions,
         ballSetId,
         tooltipKey,
         ranges,
         recipeDefinitionId,
         assetDefinitionId,
         chipItemId,
         cpuItemId,
         chipModelItemId,
         cpuModelItemId,
         displayName
      );
   }
}
