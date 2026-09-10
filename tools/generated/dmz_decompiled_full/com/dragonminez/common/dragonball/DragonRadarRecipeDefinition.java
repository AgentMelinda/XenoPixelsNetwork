package com.dragonminez.common.dragonball;

import com.google.gson.JsonObject;
import net.minecraft.data.recipes.RecipeOutput;

public abstract class DragonRadarRecipeDefinition {
   private final String id;

   protected DragonRadarRecipeDefinition(String id) {
      this.id = id;
   }

   public String getId() {
      return this.id;
   }

   public abstract void buildRecipes(RecipeOutput var1, DragonRadarDefinition var2);

   protected abstract void writeTypeSpecificJson(JsonObject var1);

   public final JsonObject toJson() {
      JsonObject root = new JsonObject();
      root.addProperty("id", this.id);
      this.writeTypeSpecificJson(root);
      return root;
   }

   public static DragonRadarRecipeDefinition fromJson(JsonObject root) {
      String type = root.get("type").getAsString();
      if ("shaped".equals(type)) {
         return ShapedDragonRadarRecipeDefinition.fromJson(root);
      } else {
         throw new IllegalArgumentException("Unsupported dragon radar recipe type: " + type);
      }
   }
}
