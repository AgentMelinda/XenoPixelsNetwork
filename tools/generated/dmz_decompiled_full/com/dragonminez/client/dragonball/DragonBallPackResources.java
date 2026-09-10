package com.dragonminez.client.dragonball;

import com.dragonminez.common.dragonball.DragonBallDefinitions;
import com.dragonminez.common.dragonball.DragonBallPackManager;
import com.dragonminez.common.dragonball.DragonBallSetAssetDefinition;
import com.dragonminez.common.dragonball.DragonBallSetDefinition;
import com.dragonminez.common.dragonball.DragonRadarAssetDefinition;
import com.dragonminez.common.dragonball.DragonRadarDefinition;
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
import java.util.Locale;
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

public class DragonBallPackResources implements PackResources {
   private final PackLocationInfo location;
   private final byte[] packMcmetaBytes;

   public DragonBallPackResources(PackLocationInfo location) {
      this.location = location;
      JsonObject packInfo = new JsonObject();
      packInfo.addProperty("description", "DMZ Dragonballs Runtime Resources");
      packInfo.addProperty("pack_format", 34);
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
      if (type == PackType.CLIENT_RESOURCES && location.getNamespace().equals("dragonminez")) {
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

   private List<DragonBallSetDefinition> getMergedBallSets() {
      Map<String, DragonBallSetDefinition> merged = new LinkedHashMap<>();

      for (DragonBallSetDefinition def : DragonBallDefinitions.getBootstrapBallSets()) {
         merged.put(def.getId(), def);
      }

      DragonBallPackManager.LoadedDefinitions external = DragonBallPackManager.getCurrent();

      for (DragonBallSetDefinition def : external.ballSets.values()) {
         merged.put(def.getId(), def);
      }

      for (DragonBallSetDefinition def : DragonBallDefinitions.getBallSets()) {
         merged.put(def.getId(), def);
      }

      return new ArrayList<>(merged.values());
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

   private String humanize(String raw) {
      String[] parts = raw.replace('-', '_').split("_");
      StringBuilder out = new StringBuilder();

      for (String part : parts) {
         if (!part.isBlank()) {
            if (out.length() > 0) {
               out.append(' ');
            }

            out.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
               out.append(part.substring(1).toLowerCase(Locale.ROOT));
            }
         }
      }

      return out.length() == 0 ? raw : out.toString();
   }

   private boolean isBuiltinVanillaStyleSet(String setId) {
      return "earth".equals(setId) || "namek".equals(setId);
   }

   private String getSetName(DragonBallSetDefinition setDefinition) {
      return setDefinition.getDisplayName().orElseGet(() -> this.humanize(setDefinition.getId()));
   }

   private String getRadarDisplay(DragonRadarDefinition radarDefinition) {
      if (radarDefinition.getBallSetId() != null) {
         for (DragonBallSetDefinition set : this.getMergedBallSets()) {
            if (radarDefinition.getBallSetId().equals(set.getId())) {
               String setName = this.getSetName(set);
               return "§eDragon Radar (§9" + setName + "§e)";
            }
         }
      }

      String setName = radarDefinition.getDisplayName().orElseGet(() -> this.humanize(radarDefinition.getItemRegistryName()));
      return "§eDragon Radar (§9" + setName + "§e)";
   }

   private String getBallDisplay(DragonBallSetDefinition setDefinition, int star) {
      String setName = this.getSetName(setDefinition);
      return "§9" + setName + "'s §rDragon Ball (" + star + " Star)";
   }

   private String getRadarTooltip(DragonRadarDefinition radarDefinition) {
      if (radarDefinition.getBallSetId() != null) {
         for (DragonBallSetDefinition set : this.getMergedBallSets()) {
            if (radarDefinition.getBallSetId().equals(set.getId())) {
               String setName = this.getSetName(set);
               return "§7Let's go catch the " + setName + " Dragon Balls!";
            }
         }
      }

      String setName = radarDefinition.getDisplayName().orElseGet(() -> this.humanize(radarDefinition.getItemRegistryName()));
      return "§7Let's go catch the " + setName + " Dragon Balls!";
   }

   private String getChipDisplay(DragonRadarDefinition radarDefinition) {
      if (radarDefinition.getBallSetId() != null) {
         for (DragonBallSetDefinition set : this.getMergedBallSets()) {
            if (radarDefinition.getBallSetId().equals(set.getId())) {
               return "Radar's Chip (" + this.getSetName(set) + ")";
            }
         }
      }

      String setName = radarDefinition.getDisplayName().orElseGet(() -> this.humanize(radarDefinition.getItemRegistryName()));
      return "Radar's Chip (" + setName + ")";
   }

   private String getCpuDisplay(DragonRadarDefinition radarDefinition) {
      if (radarDefinition.getBallSetId() != null) {
         for (DragonBallSetDefinition set : this.getMergedBallSets()) {
            if (radarDefinition.getBallSetId().equals(set.getId())) {
               return "Radar's CPU (" + this.getSetName(set) + ")";
            }
         }
      }

      String setName = radarDefinition.getDisplayName().orElseGet(() -> this.humanize(radarDefinition.getItemRegistryName()));
      return "Radar's CPU (" + setName + ")";
   }

   private String generateLangJson() {
      JsonObject root = new JsonObject();

      for (DragonBallSetDefinition setDefinition : this.getMergedBallSets()) {
         if (!this.isBuiltinVanillaStyleSet(setDefinition.getId())) {
            for (int star : setDefinition.getStars()) {
               String registryName = setDefinition.getBlockRegistryNameForStar(star);
               if (registryName != null) {
                  String label = this.getBallDisplay(setDefinition, star);
                  root.addProperty("block.dragonminez." + registryName, label);
                  root.addProperty("item.dragonminez." + registryName, label);
               }
            }
         }
      }

      for (DragonRadarDefinition radarDefinition : this.getMergedRadars()) {
         if (radarDefinition.getBallSetId() == null || !this.isBuiltinVanillaStyleSet(radarDefinition.getBallSetId())) {
            String label = this.getRadarDisplay(radarDefinition);
            root.addProperty("item.dragonminez." + radarDefinition.getItemRegistryName(), label);
            root.addProperty(radarDefinition.getTooltipKey(), this.getRadarTooltip(radarDefinition));
            radarDefinition.getChipRegistryName()
               .ifPresent(registryNamex -> root.addProperty("item.dragonminez." + registryNamex, this.getChipDisplay(radarDefinition)));
            radarDefinition.getCpuRegistryName()
               .ifPresent(registryNamex -> root.addProperty("item.dragonminez." + registryNamex, this.getCpuDisplay(radarDefinition)));
         }
      }

      return root.toString();
   }

   private String getChipTexture(DragonRadarDefinition radarDefinition) {
      String source = radarDefinition.getChipModelItemId().orElse("dragonminez:t1_radar_chip");
      ResourceLocation rl = ResourceLocation.tryParse(source);
      return rl == null ? "dragonminez:item/t1_radar_chip" : rl.getNamespace() + ":item/" + rl.getPath();
   }

   private String getCpuTexture(DragonRadarDefinition radarDefinition) {
      String source = radarDefinition.getCpuModelItemId().orElse("dragonminez:t1_radar_cpu");
      ResourceLocation rl = ResourceLocation.tryParse(source);
      return rl == null ? "dragonminez:item/t1_radar_cpu" : rl.getNamespace() + ":item/" + rl.getPath();
   }

   @Nullable
   private String getGeneratedJson(String path) {
      if (path.equals("lang/en_us.json")) {
         return this.generateLangJson();
      } else {
         for (DragonBallSetDefinition setDefinition : this.getMergedBallSets()) {
            DragonBallSetAssetDefinition assets = setDefinition.resolveAssetDefinition();

            for (int star : setDefinition.getStars()) {
               String registryName = setDefinition.getBlockRegistryNameForStar(star);
               if (registryName != null) {
                  if (path.equals("blockstates/" + registryName + ".json")) {
                     JsonObject root = new JsonObject();
                     JsonObject variants = new JsonObject();
                     JsonObject variant = new JsonObject();
                     variant.addProperty("model", "dragonminez:block/" + registryName);
                     variants.add("", variant);
                     root.add("variants", variants);
                     return root.toString();
                  }

                  if (path.equals("models/block/" + registryName + ".json")) {
                     String texture = assets != null && assets.getFlatTexturePathForStar(star).isPresent()
                        ? assets.getFlatTexturePathForStar(star).get()
                        : "dragonminez:item/" + registryName;
                     JsonObject root = new JsonObject();
                     root.addProperty("parent", "minecraft:block/cube_all");
                     JsonObject textures = new JsonObject();
                     textures.addProperty("all", texture);
                     root.add("textures", textures);
                     return root.toString();
                  }

                  if (path.equals("models/item/" + registryName + ".json")) {
                     String texture = assets != null && assets.getInventoryTexturePathForStar(star).isPresent()
                        ? assets.getInventoryTexturePathForStar(star).get()
                        : "dragonminez:item/" + registryName;
                     JsonObject root = new JsonObject();
                     root.addProperty("parent", "minecraft:item/generated");
                     JsonObject textures = new JsonObject();
                     textures.addProperty("layer0", texture);
                     root.add("textures", textures);
                     return root.toString();
                  }
               }
            }
         }

         for (DragonRadarDefinition radarDefinition : this.getMergedRadars()) {
            if (path.equals("models/item/" + radarDefinition.getItemRegistryName() + ".json")) {
               DragonRadarAssetDefinition assets = radarDefinition.resolveAssetDefinition();
               String texture = assets != null && assets.getItemTexturePath().isPresent() ? assets.getItemTexturePath().get() : "dragonminez:item/dball_radar";
               JsonObject root = new JsonObject();
               root.addProperty("parent", "minecraft:item/generated");
               JsonObject textures = new JsonObject();
               textures.addProperty("layer0", texture);
               root.add("textures", textures);
               return root.toString();
            }

            radarDefinition.getChipRegistryName().ifPresent(registryName -> {
            });
            if (radarDefinition.getChipRegistryName().isPresent() && path.equals("models/item/" + radarDefinition.getChipRegistryName().get() + ".json")) {
               JsonObject root = new JsonObject();
               root.addProperty("parent", "minecraft:item/generated");
               JsonObject textures = new JsonObject();
               textures.addProperty("layer0", this.getChipTexture(radarDefinition));
               root.add("textures", textures);
               return root.toString();
            }

            if (radarDefinition.getCpuRegistryName().isPresent() && path.equals("models/item/" + radarDefinition.getCpuRegistryName().get() + ".json")) {
               JsonObject root = new JsonObject();
               root.addProperty("parent", "minecraft:item/generated");
               JsonObject textures = new JsonObject();
               textures.addProperty("layer0", this.getCpuTexture(radarDefinition));
               root.add("textures", textures);
               return root.toString();
            }
         }

         return null;
      }
   }

   public void listResources(PackType type, String namespace, String path, ResourceOutput resourceOutput) {
      if (type == PackType.CLIENT_RESOURCES && "dragonminez".equals(namespace)) {
         this.publishIfMatches(path, "lang/en_us.json", resourceOutput);

         for (DragonBallSetDefinition setDefinition : this.getMergedBallSets()) {
            for (int star : setDefinition.getStars()) {
               String registryName = setDefinition.getBlockRegistryNameForStar(star);
               if (registryName != null) {
                  this.publishIfMatches(path, "blockstates/" + registryName + ".json", resourceOutput);
                  this.publishIfMatches(path, "models/block/" + registryName + ".json", resourceOutput);
                  this.publishIfMatches(path, "models/item/" + registryName + ".json", resourceOutput);
               }
            }
         }

         for (DragonRadarDefinition radarDefinition : this.getMergedRadars()) {
            this.publishIfMatches(path, "models/item/" + radarDefinition.getItemRegistryName() + ".json", resourceOutput);
            radarDefinition.getChipRegistryName()
               .ifPresent(registryNamex -> this.publishIfMatches(path, "models/item/" + registryNamex + ".json", resourceOutput));
            radarDefinition.getCpuRegistryName()
               .ifPresent(registryNamex -> this.publishIfMatches(path, "models/item/" + registryNamex + ".json", resourceOutput));
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
      return type == PackType.CLIENT_RESOURCES ? Set.of("dragonminez") : Collections.emptySet();
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
