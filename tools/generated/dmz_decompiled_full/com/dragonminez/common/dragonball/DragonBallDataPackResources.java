package com.dragonminez.common.dragonball;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PackResources.ResourceOutput;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;
import net.minecraft.server.packs.resources.IoSupplier;
import org.jetbrains.annotations.Nullable;

public class DragonBallDataPackResources implements PackResources {
   private final PackLocationInfo location;
   private final byte[] packMcmetaBytes;

   public DragonBallDataPackResources(PackLocationInfo location) {
      this.location = location;
      JsonObject packInfo = new JsonObject();
      packInfo.addProperty("description", "DMZ Dragonballs Runtime Data");
      packInfo.addProperty("pack_format", 48);
      JsonObject root = new JsonObject();
      root.add("pack", packInfo);
      this.packMcmetaBytes = root.toString().getBytes(StandardCharsets.UTF_8);
   }

   @Nullable
   public IoSupplier<InputStream> getRootResource(String... elements) {
      return elements.length > 0 && "pack.mcmeta".equals(elements[0]) ? () -> new ByteArrayInputStream(this.packMcmetaBytes) : null;
   }

   @Nullable
   public IoSupplier<InputStream> getResource(PackType type, ResourceLocation location) {
      if (type == PackType.SERVER_DATA && location.getNamespace().equals("dragonminez")) {
         String json = this.getGeneratedJson(location.getPath());
         if (json == null) {
            return null;
         } else {
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            return () -> new ByteArrayInputStream(bytes);
         }
      } else {
         return null;
      }
   }

   private List<DragonRadarDefinition> getMergedRadars() {
      Map<String, DragonRadarDefinition> merged = new LinkedHashMap<>();
      DragonBallPackManager.LoadedDefinitions external = DragonBallPackManager.getCurrent();

      for (DragonRadarDefinition def : DragonBallDefinitions.getBootstrapRadars()) {
         merged.put(def.getId(), def);
      }

      for (DragonRadarDefinition def : external.radars.values()) {
         merged.put(def.getId(), def);
      }

      for (DragonRadarDefinition def : DragonBallDefinitions.getRadars()) {
         merged.put(def.getId(), def);
      }

      return new ArrayList<>(merged.values());
   }

   @Nullable
   private String getGeneratedJson(String path) {
      for (DragonRadarDefinition radarDefinition : this.getMergedRadars()) {
         if (!"earth_radar".equals(radarDefinition.getId()) && !"namek_radar".equals(radarDefinition.getId())) {
            DragonRadarRecipeDefinition recipeDefinition = radarDefinition.resolveRecipeDefinition();
            if (recipeDefinition != null) {
               String recipePath = "recipes/" + radarDefinition.getItemRegistryName() + ".json";
               if (path.equals(recipePath) && recipeDefinition instanceof ShapedDragonRadarRecipeDefinition shaped) {
                  return this.buildShapedRecipeJson(radarDefinition, shaped).toString();
               }
            }
         }
      }

      return null;
   }

   private JsonObject buildShapedRecipeJson(DragonRadarDefinition radarDefinition, ShapedDragonRadarRecipeDefinition recipeDefinition) {
      JsonObject root = new JsonObject();
      root.addProperty("type", "minecraft:crafting_shaped");
      root.addProperty("category", "redstone");
      JsonArray pattern = new JsonArray();
      pattern.add("OCO");
      pattern.add("PGP");
      pattern.add("CPC");
      root.add("pattern", pattern);
      JsonObject key = new JsonObject();
      JsonObject o = new JsonObject();
      o.addProperty("item", "minecraft:observer");
      key.add("O", o);
      JsonObject g = new JsonObject();
      g.addProperty("item", recipeDefinition.getCpuItemId());
      key.add("G", g);
      JsonObject c = new JsonObject();
      c.addProperty("item", recipeDefinition.getChipItemId());
      key.add("C", c);
      JsonObject p = new JsonObject();
      p.addProperty("item", "dragonminez:radar_piece");
      key.add("P", p);
      root.add("key", key);
      JsonObject result = new JsonObject();
      result.addProperty("item", "dragonminez:" + radarDefinition.getItemRegistryName());
      result.addProperty("count", 1);
      root.add("result", result);
      return root;
   }

   public void listResources(PackType type, String namespace, String path, ResourceOutput resourceOutput) {
      if (type == PackType.SERVER_DATA && "dragonminez".equals(namespace)) {
         for (DragonRadarDefinition radarDefinition : this.getMergedRadars()) {
            if (!"earth_radar".equals(radarDefinition.getId()) && !"namek_radar".equals(radarDefinition.getId())) {
               DragonRadarRecipeDefinition recipeDefinition = radarDefinition.resolveRecipeDefinition();
               if (recipeDefinition != null) {
                  this.publishIfMatches(path, "recipes/" + radarDefinition.getItemRegistryName() + ".json", resourceOutput);
               }
            }
         }
      }
   }

   private void publishIfMatches(String requestedPath, String fullPath, ResourceOutput resourceOutput) {
      if (fullPath.startsWith(requestedPath)) {
         resourceOutput.accept(ResourceLocation.fromNamespaceAndPath("dragonminez", fullPath), (IoSupplier)() -> {
            String json = this.getGeneratedJson(fullPath);
            byte[] bytes = json == null ? new byte[0] : json.getBytes(StandardCharsets.UTF_8);
            return new ByteArrayInputStream(bytes);
         });
      }
   }

   public Set<String> getNamespaces(PackType type) {
      return type == PackType.SERVER_DATA ? Set.of("dragonminez") : Collections.emptySet();
   }

   @Nullable
   public <T> T getMetadataSection(MetadataSectionSerializer<T> deserializer) {
      try {
         Object var5;
         try (InputStreamReader reader = new InputStreamReader(new ByteArrayInputStream(this.packMcmetaBytes), StandardCharsets.UTF_8)) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            String sectionName = deserializer.getMetadataSectionName();
            if (json.has(sectionName)) {
               return (T)deserializer.fromJson(json.getAsJsonObject(sectionName));
            }

            var5 = null;
         }

         return (T)var5;
      } catch (Exception var8) {
         return null;
      }
   }

   public PackLocationInfo location() {
      return this.location;
   }

   public void close() {
   }

   public boolean isHidden() {
      return false;
   }
}
