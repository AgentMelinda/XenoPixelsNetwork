package com.dragonminez.common.datagen;

import com.dragonminez.common.dragonball.DragonAssetDefinition;
import com.dragonminez.common.dragonball.DragonBallDefinitions;
import com.dragonminez.common.dragonball.DragonBallSetAssetDefinition;
import com.dragonminez.common.dragonball.DragonBallSetDefinition;
import com.dragonminez.common.dragonball.DragonDefinition;
import com.dragonminez.common.dragonball.DragonRadarAssetDefinition;
import com.dragonminez.common.dragonball.DragonRadarDefinition;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.data.PackOutput.Target;

public class DMZDragonDefinitionProvider implements DataProvider {
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
   private final PackOutput output;

   public DMZDragonDefinitionProvider(PackOutput output) {
      this.output = output;
   }

   public CompletableFuture<?> run(CachedOutput cachedOutput) {
      CompletableFuture<?> future = CompletableFuture.completedFuture(null);
      Map<String, DragonBallSetDefinition> setsById = new LinkedHashMap<>();

      for (DragonBallSetDefinition definition : DragonBallDefinitions.getBootstrapBallSets()) {
         setsById.put(definition.getId(), definition);
         future = CompletableFuture.allOf(
            future, this.save(cachedOutput, definition.toJson(), "dragonballs/" + definition.getId() + "/definitions/ballset.json")
         );
      }

      for (DragonRadarDefinition definition : DragonBallDefinitions.getBootstrapRadars()) {
         if (definition.getBallSetId() != null
            && setsById.containsKey(definition.getBallSetId())
            && definition.getId().equals(definition.getBallSetId() + "_radar")) {
            future = CompletableFuture.allOf(
               future, this.save(cachedOutput, definition.toJson(), "dragonballs/" + definition.getBallSetId() + "/definitions/radar.json")
            );
         }
      }

      for (DragonDefinition definitionx : DragonBallDefinitions.getBootstrapDragons()) {
         if (definitionx.getBallSetId() != null && setsById.containsKey(definitionx.getBallSetId())) {
            future = CompletableFuture.allOf(
               future, this.save(cachedOutput, definitionx.toJson(), "dragonballs/" + definitionx.getBallSetId() + "/definitions/dragon.json")
            );
         }
      }

      for (DragonBallSetAssetDefinition definitionxx : DragonBallDefinitions.getBootstrapBallSetAssets()) {
         String setId = this.findSetForBallAsset(definitionxx.getId());
         if (setId != null) {
            future = CompletableFuture.allOf(future, this.save(cachedOutput, definitionxx.toJson(), "dragonballs/" + setId + "/assets/ballset.json"));
         }
      }

      for (DragonRadarAssetDefinition definitionxxx : DragonBallDefinitions.getBootstrapRadarAssets()) {
         String setId = this.findSetForRadarAsset(definitionxxx.getId());
         if (setId != null) {
            future = CompletableFuture.allOf(future, this.save(cachedOutput, definitionxxx.toJson(), "dragonballs/" + setId + "/assets/radar.json"));
         }
      }

      for (DragonAssetDefinition definitionxxxx : DragonBallDefinitions.getBootstrapDragonAssets()) {
         String setId = this.findSetForDragonAsset(definitionxxxx.getId());
         if (setId != null) {
            future = CompletableFuture.allOf(future, this.save(cachedOutput, definitionxxxx.toJson(), "dragonballs/" + setId + "/assets/dragon.json"));
         }
      }

      return future;
   }

   private String findSetForBallAsset(String assetId) {
      for (DragonBallSetDefinition set : DragonBallDefinitions.getBootstrapBallSets()) {
         if (set.getAssetDefinitionId().isPresent() && set.getAssetDefinitionId().get().equals(assetId)) {
            return set.getId();
         }
      }

      return null;
   }

   private String findSetForRadarAsset(String assetId) {
      for (DragonRadarDefinition radar : DragonBallDefinitions.getBootstrapRadars()) {
         if (radar.getAssetDefinitionId().isPresent() && radar.getAssetDefinitionId().get().equals(assetId)) {
            return radar.getBallSetId();
         }
      }

      return null;
   }

   private String findSetForDragonAsset(String assetId) {
      for (DragonDefinition dragon : DragonBallDefinitions.getBootstrapDragons()) {
         if (dragon.getAssetDefinitionId().isPresent() && dragon.getAssetDefinitionId().get().equals(assetId)) {
            return dragon.getBallSetId();
         }
      }

      return null;
   }

   private CompletableFuture<?> save(CachedOutput cachedOutput, JsonObject root, String relativePath) {
      Path path = this.output.getOutputFolder(Target.DATA_PACK).resolve("dragonminez").resolve(relativePath);
      return DataProvider.saveStable(cachedOutput, GSON.toJsonTree(root), path);
   }

   public String getName() {
      return "DragonMineZ dragonballs definition datapack provider";
   }
}
