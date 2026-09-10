package com.dragonminez.common.datagen.builder;

import com.dragonminez.server.recipes.KikonoRecipe;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.NonNullList;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.ItemLike;

public class KikonoRecipeBuilder {
   private final Item result;
   private final int count;
   private final List<ItemLike> inputs = new ArrayList<>();
   private ItemLike template;
   private ItemLike pattern;
   private int craftingTime = 100;
   private int energyCost = 1000;

   public KikonoRecipeBuilder(ItemLike result, int count) {
      this.result = result.asItem();
      this.count = count;
   }

   public static KikonoRecipeBuilder kikonize(ItemLike result) {
      return new KikonoRecipeBuilder(result, 1);
   }

   public KikonoRecipeBuilder pattern(ItemLike item) {
      this.pattern = item;
      return this;
   }

   public KikonoRecipeBuilder template(ItemLike item) {
      this.template = item;
      return this;
   }

   public KikonoRecipeBuilder input(ItemLike item) {
      if (this.inputs.size() < 9) {
         this.inputs.add(item);
      }

      return this;
   }

   public KikonoRecipeBuilder time(int ticks) {
      this.craftingTime = ticks;
      return this;
   }

   public KikonoRecipeBuilder energy(int fe) {
      this.energyCost = fe;
      return this;
   }

   public void save(RecipeOutput output, ResourceLocation id) {
      if (this.pattern != null && this.template != null) {
         NonNullList<Ingredient> slots = NonNullList.withSize(9, Ingredient.EMPTY);

         for (int i = 0; i < this.inputs.size() && i < 9; i++) {
            ItemLike inputItem = this.inputs.get(i);
            if (inputItem.asItem() != Items.AIR) {
               slots.set(i, Ingredient.of(new ItemLike[]{inputItem}));
            }
         }

         ItemStack resultStack = new ItemStack(this.result, this.count);
         KikonoRecipe recipe = new KikonoRecipe(
            resultStack, slots, Ingredient.of(new ItemLike[]{this.pattern}), Ingredient.of(new ItemLike[]{this.template}), this.craftingTime, this.energyCost
         );
         output.accept(id, recipe, null);
      } else {
         throw new IllegalStateException("Missing pattern or template");
      }
   }
}
