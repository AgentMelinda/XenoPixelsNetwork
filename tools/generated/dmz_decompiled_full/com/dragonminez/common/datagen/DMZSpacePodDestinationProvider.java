package com.dragonminez.common.datagen;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.data.PackOutput.Target;

public class DMZSpacePodDestinationProvider implements DataProvider {
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
   private final PackOutput output;

   public DMZSpacePodDestinationProvider(PackOutput output) {
      this.output = output;
   }

   public CompletableFuture<?> run(CachedOutput cachedOutput) {
      JsonObject root = new JsonObject();
      root.addProperty("replace", true);
      JsonArray destinations = new JsonArray();
      destinations.add(
         destination("overworld", "gui.dragonminez.spacepod.overworld", true, "minecraft:overworld", 0, null, null, null, null, true, primitive("ALWAYS"))
      );
      destinations.add(destination("namek", "gui.dragonminez.spacepod.namek", true, "dragonminez:namek", 1, null, null, null, null, true, primitive("ALWAYS")));
      destinations.add(
         destination(
            "otherworld",
            "gui.dragonminez.spacepod.otherworld",
            true,
            "dragonminez:otherworld",
            2,
            null,
            54.0,
            210.0,
            1082.0,
            true,
            and(primitive("OTHERWORLD_ENABLED"), quest("bulma_otherworld_drive"))
         )
      );
      destinations.add(
         destination(
            "sacredkaiplanet",
            "gui.dragonminez.spacepod.sacredkaiplanet",
            true,
            "dragonminez:sacredkaiplanet",
            3,
            null,
            null,
            null,
            null,
            true,
            quest("buu_saga:23")
         )
      );
      destinations.add(
         destination("cereal", "gui.dragonminez.spacepod.cereal", true, "dragonminez:cereal_planet", 4, null, null, null, null, true, primitive("NEVER"))
      );
      destinations.add(
         destination("beerus", "gui.dragonminez.spacepod.beerus", true, "dmzsuper:beerus_planet", 5, null, null, null, null, true, primitive("NEVER"))
      );
      destinations.add(
         destination(
            "time_chamber",
            "gui.dragonminez.spacepod.time_chamber",
            true,
            "dragonminez:time_chamber",
            2,
            null,
            0.5,
            130.0,
            0.5,
            true,
            quest("bulma_time_chamber_link")
         )
      );
      root.add("destinations", destinations);
      Path path = this.output.getOutputFolder(Target.DATA_PACK).resolve("dragonminez").resolve("spacepod").resolve("destinations.json");
      return DataProvider.saveStable(cachedOutput, GSON.toJsonTree(root), path);
   }

   public String getName() {
      return "DragonMineZ Space Pod Destinations";
   }

   private static JsonObject destination(
      String id,
      String name,
      boolean translate,
      String dimension,
      Integer iconIndex,
      String iconTexture,
      Double x,
      Double y,
      Double z,
      boolean showWhenLocked,
      Object unlockRules
   ) {
      JsonObject object = new JsonObject();
      object.addProperty("id", id);
      object.addProperty("name", name);
      object.addProperty("translate", translate);
      object.addProperty("dimension", dimension);
      if (iconIndex != null) {
         object.addProperty("icon_index", iconIndex);
      }

      if (iconTexture != null) {
         object.addProperty("icon_texture", iconTexture);
      }

      if (x != null) {
         object.addProperty("x", x);
      }

      if (y != null) {
         object.addProperty("y", y);
      }

      if (z != null) {
         object.addProperty("z", z);
      }

      object.addProperty("show_when_locked", showWhenLocked);
      object.add("unlock_rules", GSON.toJsonTree(unlockRules));
      return object;
   }

   private static String primitive(String rule) {
      return rule;
   }

   private static JsonObject quest(String questId) {
      JsonObject object = new JsonObject();
      object.addProperty("quest", questId);
      return object;
   }

   private static JsonObject and(Object... children) {
      JsonObject object = new JsonObject();
      JsonArray array = new JsonArray();

      for (Object child : children) {
         array.add(GSON.toJsonTree(child));
      }

      object.add("and", array);
      return object;
   }
}
