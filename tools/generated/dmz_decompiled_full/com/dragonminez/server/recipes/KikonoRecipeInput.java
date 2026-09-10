package com.dragonminez.server.recipes;

import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeInput;

public record KikonoRecipeInput(NonNullList<ItemStack> items) implements RecipeInput {
   public KikonoRecipeInput(NonNullList<ItemStack> items) {
      if (items.size() < 11) {
         throw new IllegalArgumentException("KikonoRecipeInput requires 11 slots");
      } else {
         this.items = items;
      }
   }

   public ItemStack getItem(int index) {
      return (ItemStack)this.items.get(index);
   }

   public int size() {
      return this.items.size();
   }
}
