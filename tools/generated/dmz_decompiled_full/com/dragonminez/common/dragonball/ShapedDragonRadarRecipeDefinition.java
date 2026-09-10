package com.dragonminez.common.dragonball;

import com.dragonminez.common.init.MainItems;
import com.google.gson.JsonObject;
import net.minecraft.advancements.Criterion;
import net.minecraft.advancements.critereon.InventoryChangeTrigger.TriggerInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;

public class ShapedDragonRadarRecipeDefinition extends DragonRadarRecipeDefinition {
   private final String chipItemId;
   private final String cpuItemId;

   public ShapedDragonRadarRecipeDefinition(String id, String chipItemId, String cpuItemId) {
      super(id);
      this.chipItemId = chipItemId;
      this.cpuItemId = cpuItemId;
   }

   public String getChipItemId() {
      return this.chipItemId;
   }

   public String getCpuItemId() {
      return this.cpuItemId;
   }

   @Override
   public void buildRecipes(RecipeOutput output, DragonRadarDefinition radarDefinition) {
      Item chipItem = (Item)BuiltInRegistries.ITEM.get(ResourceLocation.parse(this.chipItemId));
      Item cpuItem = (Item)BuiltInRegistries.ITEM.get(ResourceLocation.parse(this.cpuItemId));
      if (chipItem != null && cpuItem != null) {
         ShapedRecipeBuilder.shaped(RecipeCategory.REDSTONE, (ItemLike)MainItems.getDragonRadarItemOrThrow(radarDefinition.getId()).get(), 1)
            .pattern("OCO")
            .pattern("PGP")
            .pattern("CPC")
            .define('O', Items.OBSERVER)
            .define('G', cpuItem)
            .define('C', chipItem)
            .define('P', (ItemLike)MainItems.RADAR_PIECE.get())
            .unlockedBy(getHasName(chipItem), has(chipItem))
            .group("dragonminez")
            .save(output);
      } else {
         throw new IllegalStateException("Missing radar recipe ingredient for radar '" + radarDefinition.getId() + "'");
      }
   }

   @Override
   protected void writeTypeSpecificJson(JsonObject root) {
      root.addProperty("type", "shaped");
      root.addProperty("chip_item", this.chipItemId);
      root.addProperty("cpu_item", this.cpuItemId);
   }

   private static String getHasName(Item item) {
      ResourceLocation key = BuiltInRegistries.ITEM.getKey(item);
      return "has_" + (key == null ? "item" : key.getPath());
   }

   private static Criterion<TriggerInstance> has(Item item) {
      return TriggerInstance.hasItems(new ItemLike[]{item});
   }

   public static ShapedDragonRadarRecipeDefinition fromJson(JsonObject root) {
      String id = root.has("id") ? root.get("id").getAsString() : "generated";
      return new ShapedDragonRadarRecipeDefinition(id, root.get("chip_item").getAsString(), root.get("cpu_item").getAsString());
   }
}
