package com.dragonminez.common.dragonball;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Map.Entry;
import java.util.function.IntSupplier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.registries.DeferredHolder;

public class DragonBallSetDefinition {
   private final String id;
   private final Set<ResourceLocation> validDimensions;
   private final IntSupplier copiesSupplier;
   private final IntSupplier spawnRangeSupplier;
   private final int summonRadius;
   private final Map<Integer, String> blockRegistryNamesByStar;
   private final String assetDefinitionId;
   private final String displayName;
   private final Map<Integer, DeferredHolder<Block, ? extends Block>> registeredBlocksByStar = new LinkedHashMap<>();

   public DragonBallSetDefinition(
      String id,
      Set<ResourceLocation> validDimensions,
      IntSupplier copiesSupplier,
      IntSupplier spawnRangeSupplier,
      int summonRadius,
      Map<Integer, String> blockRegistryNamesByStar
   ) {
      this(id, validDimensions, copiesSupplier, spawnRangeSupplier, summonRadius, blockRegistryNamesByStar, null, null);
   }

   public DragonBallSetDefinition(
      String id,
      Set<ResourceLocation> validDimensions,
      IntSupplier copiesSupplier,
      IntSupplier spawnRangeSupplier,
      int summonRadius,
      Map<Integer, String> blockRegistryNamesByStar,
      String assetDefinitionId,
      String displayName
   ) {
      this.id = id;
      this.validDimensions = Set.copyOf(validDimensions);
      this.copiesSupplier = copiesSupplier;
      this.spawnRangeSupplier = spawnRangeSupplier;
      this.summonRadius = summonRadius;
      this.blockRegistryNamesByStar = Map.copyOf(blockRegistryNamesByStar);
      this.assetDefinitionId = assetDefinitionId != null && !assetDefinitionId.isBlank() ? assetDefinitionId : null;
      this.displayName = displayName != null && !displayName.isBlank() ? displayName : null;
   }

   public String getId() {
      return this.id;
   }

   public Set<ResourceLocation> getValidDimensions() {
      return this.validDimensions;
   }

   public boolean supportsDimension(ResourceKey<Level> dimension) {
      return this.validDimensions.contains(dimension.location());
   }

   public int getCopies() {
      return Math.max(1, this.copiesSupplier.getAsInt());
   }

   public int getSpawnRange() {
      return Math.max(1, this.spawnRangeSupplier.getAsInt());
   }

   public int getSummonRadius() {
      return this.summonRadius;
   }

   public Map<Integer, String> getBlockRegistryNamesByStar() {
      return Collections.unmodifiableMap(this.blockRegistryNamesByStar);
   }

   public Set<Integer> getStars() {
      return Collections.unmodifiableSet(new LinkedHashSet<>(this.blockRegistryNamesByStar.keySet()));
   }

   public String getBlockRegistryNameForStar(int star) {
      return this.blockRegistryNamesByStar.get(star);
   }

   public Optional<String> getAssetDefinitionId() {
      return Optional.ofNullable(this.assetDefinitionId);
   }

   public Optional<String> getDisplayName() {
      return Optional.ofNullable(this.displayName);
   }

   public DragonBallSetAssetDefinition resolveAssetDefinition() {
      return this.assetDefinitionId == null ? null : DragonBallDefinitions.getBallSetAsset(this.assetDefinitionId);
   }

   public void setRegisteredBlock(int star, DeferredHolder<Block, ? extends Block> block) {
      this.registeredBlocksByStar.put(star, block);
   }

   public DeferredHolder<Block, ? extends Block> getRegisteredBlockObjectForStar(int star) {
      return this.registeredBlocksByStar.get(star);
   }

   public Block getBlockForStar(int star) {
      DeferredHolder<Block, ? extends Block> registryObject = this.registeredBlocksByStar.get(star);
      return registryObject == null ? null : (Block)registryObject.get();
   }

   public Integer getStarForBlock(Block block) {
      for (Entry<Integer, DeferredHolder<Block, ? extends Block>> entry : this.registeredBlocksByStar.entrySet()) {
         if (entry.getValue().get() == block) {
            return entry.getKey();
         }
      }

      return null;
   }

   public JsonObject toJson() {
      JsonObject root = new JsonObject();
      root.addProperty("id", this.id);
      JsonArray dimensions = new JsonArray();

      for (ResourceLocation dimension : this.validDimensions) {
         dimensions.add(dimension.toString());
      }

      root.add("dimensions", dimensions);
      root.addProperty("copies", this.getCopies());
      root.addProperty("spawn_range", this.getSpawnRange());
      root.addProperty("summon_radius", this.summonRadius);
      if (this.assetDefinitionId != null) {
         root.addProperty("asset_definition", this.assetDefinitionId);
      }

      if (this.displayName != null) {
         root.addProperty("display_name", this.displayName);
      }

      JsonObject blocks = new JsonObject();
      this.blockRegistryNamesByStar
         .entrySet()
         .stream()
         .sorted(Entry.comparingByKey())
         .forEach(entry -> blocks.addProperty(String.valueOf(entry.getKey()), entry.getValue()));
      root.add("blocks", blocks);
      return root;
   }

   public static DragonBallSetDefinition fromJson(JsonObject root) {
      String id = root.get("id").getAsString();
      Set<ResourceLocation> dimensions = new LinkedHashSet<>();

      for (JsonElement element : root.getAsJsonArray("dimensions")) {
         dimensions.add(ResourceLocation.parse(element.getAsString()));
      }

      int copies = root.has("copies") ? root.get("copies").getAsInt() : 5;
      int spawnRange = root.get("spawn_range").getAsInt();
      int summonRadius = root.get("summon_radius").getAsInt();
      Map<Integer, String> blockRegistryNamesByStar = new LinkedHashMap<>();
      JsonObject blocks = root.getAsJsonObject("blocks");

      for (String key : blocks.keySet()) {
         blockRegistryNamesByStar.put(Integer.parseInt(key), blocks.get(key).getAsString());
      }

      String assetDefinitionId = root.has("asset_definition") ? root.get("asset_definition").getAsString() : null;
      String displayName = root.has("display_name") ? root.get("display_name").getAsString() : null;
      return new DragonBallSetDefinition(id, dimensions, () -> copies, () -> spawnRange, summonRadius, blockRegistryNamesByStar, assetDefinitionId, displayName);
   }
}
