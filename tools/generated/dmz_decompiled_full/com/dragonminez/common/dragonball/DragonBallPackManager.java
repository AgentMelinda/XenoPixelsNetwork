package com.dragonminez.common.dragonball;

import com.dragonminez.Env;
import com.dragonminez.LogUtil;
import com.dragonminez.common.diagnostics.JsonKeys;
import com.dragonminez.common.diagnostics.JsonLoadReport;
import com.dragonminez.common.diagnostics.JsonSchema;
import com.dragonminez.common.util.adapters.GenericItemTypeAdapter;
import com.dragonminez.common.util.adapters.WishTypeAdapter;
import com.dragonminez.common.util.types.items.GenericItemDTO;
import com.dragonminez.common.wish.Wish;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import net.neoforged.fml.loading.FMLPaths;

public final class DragonBallPackManager {
   private static final Gson GSON = new GsonBuilder()
      .registerTypeAdapter(Wish.class, new WishTypeAdapter())
      .registerTypeAdapter(GenericItemDTO.class, new GenericItemTypeAdapter())
      .setPrettyPrinting()
      .create();
   private static final Type WISH_LIST_TYPE = (new TypeToken<ArrayList<Wish>>() {
   }).getType();
   private static final String ROOT_FOLDER_NAME = "dragonballs";
   private static DragonBallPackManager.LoadedDefinitions current = new DragonBallPackManager.LoadedDefinitions();
   private static final Set<String> BALLSET_KEYS = Set.of(
      "id", "dimensions", "copies", "spawn_range", "summon_radius", "blocks", "asset_definition", "display_name"
   );
   private static final Set<String> RADAR_KEYS = Set.of(
      "id",
      "item_registry_name",
      "dimensions",
      "ball_set",
      "tooltip_key",
      "ranges",
      "recipe_definition",
      "asset_definition",
      "chip_item",
      "cpu_item",
      "chip_model_item",
      "cpu_model_item",
      "display_name"
   );
   private static final Set<String> DRAGON_KEYS = Set.of(
      "id", "entity_registry_name", "entity_width", "entity_height", "dimensions", "ball_set", "wish_screen_id", "wish_count", "asset_definition"
   );
   private static final Set<String> WISHES_FILE_KEYS = Set.of("dragon", "wishes");
   private static final Set<String> BALLSET_ASSET_KEYS = Set.of(
      "id", "flat_texture_prefix", "inventory_texture_format", "geo_model", "animation", "geo_texture_prefix", "block_model_template", "item_model_template"
   );
   private static final Set<String> RADAR_ASSET_KEYS = Set.of(
      "id", "item_model", "item_texture", "radar_background_texture", "radar_dot_texture", "radar_overlay_texture"
   );
   private static final Set<String> DRAGON_ASSET_KEYS = Set.of("id", "renderer_type", "model", "texture", "animation");

   private DragonBallPackManager() {
   }

   public static Path getRootDirectory() {
      Path root = FMLPaths.GAMEDIR.get().resolve("dragonballs").toAbsolutePath().normalize();

      try {
         Files.createDirectories(root);
      } catch (Exception var2) {
         LogUtil.warn(Env.COMMON, "Failed to create dragonballs root folder '{}': {}", root, var2.toString());
      }

      return root;
   }

   private static List<Path> getCandidateRootDirectories() {
      LinkedHashSet<Path> roots = new LinkedHashSet<>();
      Path gameDirRoot = getRootDirectory();
      roots.add(gameDirRoot);

      try {
         roots.add(Paths.get("").toAbsolutePath().normalize().resolve("dragonballs"));
      } catch (Exception var6) {
      }

      Path gameDir = FMLPaths.GAMEDIR.get().toAbsolutePath().normalize();
      if (gameDir.getParent() != null) {
         roots.add(gameDir.getParent().resolve("dragonballs"));
      }

      List<Path> result = new ArrayList<>();

      for (Path root : roots) {
         if (root != null && Files.exists(root) && Files.isDirectory(root)) {
            result.add(root);
         }
      }

      if (result.isEmpty()) {
         result.add(gameDirRoot);
      }

      return result;
   }

   public static DragonBallPackManager.LoadedDefinitions getCurrent() {
      return current;
   }

   public static DragonBallPackManager.LoadedDefinitions loadAll() {
      JsonLoadReport.clear("dragonballs");
      DragonBallPackManager.LoadedDefinitions loaded = new DragonBallPackManager.LoadedDefinitions();

      for (Path root : getCandidateRootDirectories()) {
         List<Path> packPaths = new ArrayList<>();

         try (Stream<Path> stream = Files.list(root)) {
            stream.filter(path -> Files.isDirectory(path) || path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".zip"))
               .sorted(Comparator.comparing(path -> path.getFileName().toString().toLowerCase(Locale.ROOT)))
               .forEach(packPaths::add);
         } catch (Exception var10) {
            LogUtil.warn(Env.COMMON, "Failed to scan dragonballs root folder '{}': {}", root, var10.toString());
            continue;
         }

         for (Path packPath : packPaths) {
            if (Files.isDirectory(packPath)) {
               loadFolderPack(packPath, loaded);
            } else {
               loadZipPack(packPath, loaded);
            }
         }
      }

      current = loaded;
      LogUtil.info(
         Env.COMMON,
         "Loaded {} external dragonball ballset definition(s), {} radar definition(s), {} dragon definition(s), {} ballset asset definition(s), {} radar asset definition(s), {} dragon asset definition(s), and {} wish list(s) from the dragonballs folder(s)",
         loaded.ballSets.size(),
         loaded.radars.size(),
         loaded.dragons.size(),
         loaded.ballSetAssets.size(),
         loaded.radarAssets.size(),
         loaded.dragonAssets.size(),
         loaded.wishes.size()
      );
      return loaded;
   }

   private static void loadFolderPack(Path packRoot, DragonBallPackManager.LoadedDefinitions loaded) {
      try (Stream<Path> stream = Files.walk(packRoot)) {
         stream.filter(x$0 -> Files.isRegularFile(x$0))
            .filter(path -> path.toString().endsWith(".json"))
            .sorted()
            .forEach(path -> loadFolderFile(path, loaded));
      } catch (Exception var7) {
         LogUtil.warn(Env.COMMON, "Failed to load dragonball folder pack '{}': {}", packRoot.getFileName(), var7.toString());
      }
   }

   private static void loadFolderFile(Path path, DragonBallPackManager.LoadedDefinitions loaded) {
      String normalized = path.toString().replace('\\', '/');

      try {
         try (Reader reader = Files.newBufferedReader(path)) {
            JsonObject root = (JsonObject)GSON.fromJson(reader, JsonObject.class);
            if (root != null) {
               readDefinition(normalized, root, loaded);
               return;
            }
         }
      } catch (Exception var8) {
         LogUtil.warn(Env.COMMON, "Failed to read dragonball definition file '{}': {}", path, var8.toString());
      }
   }

   private static void loadZipPack(Path zipPath, DragonBallPackManager.LoadedDefinitions loaded) {
      try (ZipFile zip = new ZipFile(zipPath.toFile())) {
         List<? extends ZipEntry> entries = Collections.list(zip.entries());
         entries.sort(Comparator.comparing(ZipEntry::getName));

         for (ZipEntry entry : entries) {
            if (!entry.isDirectory() && entry.getName().endsWith(".json")) {
               try (BufferedReader reader = new BufferedReader(new InputStreamReader(zip.getInputStream(entry)))) {
                  JsonObject root = (JsonObject)GSON.fromJson(reader, JsonObject.class);
                  if (root != null) {
                     readDefinition(entry.getName().replace('\\', '/'), root, loaded);
                  }
               } catch (Exception var12) {
                  LogUtil.warn(Env.COMMON, "Failed to read dragonball definition entry '{}::{}': {}", zipPath.getFileName(), entry.getName(), var12.toString());
               }
            }
         }
      } catch (Exception var14) {
         LogUtil.warn(Env.COMMON, "Failed to load dragonball zip pack '{}': {}", zipPath.getFileName(), var14.toString());
      }
   }

   private static String label(String normalizedPath) {
      int index = normalizedPath.indexOf("dragonballs/");
      return index >= 0 ? normalizedPath.substring(index) : normalizedPath;
   }

   private static void validateWishArray(String file, JsonObject root) {
      if (root.has("wishes") && root.get("wishes").isJsonArray()) {
         int i = 0;

         for (JsonElement element : root.getAsJsonArray("wishes")) {
            if (element.isJsonObject()) {
               JsonObject wish = element.getAsJsonObject();
               String type = wish.has("type") && !wish.get("type").isJsonNull() ? wish.get("type").getAsString() : null;
               Class<? extends Wish> target = WishTypeAdapter.classForType(type);
               if (target == null) {
                  JsonKeys.reportBadType("dragonballs", file, "wishes[" + i + "]", type);
               } else {
                  JsonSchema.check("dragonballs", file, "wishes[" + i + "]", wish, target);
               }
            }

            i++;
         }
      }
   }

   private static void readDefinition(String normalizedPath, JsonObject root, DragonBallPackManager.LoadedDefinitions loaded) {
      String file = label(normalizedPath);
      if (normalizedPath.endsWith("/definitions/ballset.json")) {
         JsonKeys.checkObject("dragonballs", file, "ballset", root, BALLSET_KEYS);
         DragonBallSetDefinition definition = DragonBallSetDefinition.fromJson(root);
         loaded.ballSets.put(definition.getId(), definition);
      } else if (normalizedPath.endsWith("/definitions/radar.json")) {
         JsonKeys.checkObject("dragonballs", file, "radar", root, RADAR_KEYS);
         DragonRadarDefinition definition = DragonRadarDefinition.fromJson(root);
         loaded.radars.put(definition.getId(), definition);
         if (definition.getRecipeDefinitionId().isPresent()) {
            DragonRadarRecipeDefinition recipe = DragonBallDefinitions.getRadarRecipe(definition.getRecipeDefinitionId().get());
            if (recipe != null) {
               loaded.radarRecipes.put(recipe.getId(), recipe);
            }
         } else if (definition.getChipItemId().isPresent() && definition.getCpuItemId().isPresent()) {
            String syntheticRecipeId = definition.getId() + "_auto_recipe";
            loaded.radarRecipes
               .put(
                  syntheticRecipeId,
                  new ShapedDragonRadarRecipeDefinition(syntheticRecipeId, definition.getChipItemId().get(), definition.getCpuItemId().get())
               );
         }
      } else if (normalizedPath.endsWith("/definitions/dragon.json")) {
         JsonKeys.checkObject("dragonballs", file, "dragon", root, DRAGON_KEYS);
         DragonDefinition definition = DragonDefinition.fromJson(root);
         loaded.dragons.put(definition.getId(), definition);
      } else if (normalizedPath.endsWith("/definitions/wishes.json")) {
         JsonKeys.checkObject("dragonballs", file, "wishes", root, WISHES_FILE_KEYS);
         validateWishArray(file, root);
         if (root.has("dragon") && root.has("wishes")) {
            String dragonId = root.get("dragon").getAsString();
            List<Wish> wishes = (List<Wish>)GSON.fromJson(root.getAsJsonArray("wishes"), WISH_LIST_TYPE);
            loaded.wishes.put(dragonId, wishes == null ? List.of() : List.copyOf(wishes));
         }
      } else if (normalizedPath.endsWith("/assets/ballset.json")) {
         JsonKeys.checkObject("dragonballs", file, "ballset asset", root, BALLSET_ASSET_KEYS);
         DragonBallSetAssetDefinition definition = DragonBallSetAssetDefinition.fromJson(root);
         loaded.ballSetAssets.put(definition.getId(), definition);
      } else if (normalizedPath.endsWith("/assets/radar.json")) {
         JsonKeys.checkObject("dragonballs", file, "radar asset", root, RADAR_ASSET_KEYS);
         DragonRadarAssetDefinition definition = DragonRadarAssetDefinition.fromJson(root);
         loaded.radarAssets.put(definition.getId(), definition);
      } else {
         if (normalizedPath.endsWith("/assets/dragon.json")) {
            JsonKeys.checkObject("dragonballs", file, "dragon asset", root, DRAGON_ASSET_KEYS);
            DragonAssetDefinition definition = DragonAssetDefinition.fromJson(root);
            loaded.dragonAssets.put(definition.getId(), definition);
         }
      }
   }

   public static final class LoadedDefinitions {
      public final Map<String, DragonBallSetDefinition> ballSets = new LinkedHashMap<>();
      public final Map<String, DragonRadarDefinition> radars = new LinkedHashMap<>();
      public final Map<String, DragonDefinition> dragons = new LinkedHashMap<>();
      public final Map<String, DragonRadarRecipeDefinition> radarRecipes = new LinkedHashMap<>();
      public final Map<String, DragonBallSetAssetDefinition> ballSetAssets = new LinkedHashMap<>();
      public final Map<String, DragonRadarAssetDefinition> radarAssets = new LinkedHashMap<>();
      public final Map<String, DragonAssetDefinition> dragonAssets = new LinkedHashMap<>();
      public final Map<String, List<Wish>> wishes = new LinkedHashMap<>();
   }
}
