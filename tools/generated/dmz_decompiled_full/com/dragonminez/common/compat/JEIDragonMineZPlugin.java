package com.dragonminez.common.compat;

import com.dragonminez.client.init.menu.screens.KikonoStationScreen;
import com.dragonminez.common.init.MainBlocks;
import com.dragonminez.server.recipes.KikonoRecipe;
import java.util.ArrayList;
import java.util.List;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.ItemLike;

@JeiPlugin
public class JEIDragonMineZPlugin implements IModPlugin {
   public ResourceLocation getPluginUid() {
      return ResourceLocation.fromNamespaceAndPath("dragonminez", "jei_plugin");
   }

   public void registerCategories(IRecipeCategoryRegistration registration) {
      registration.addRecipeCategories(new IRecipeCategory[]{new KikonoStationCategory(registration.getJeiHelpers().getGuiHelper())});
   }

   public void registerRecipes(IRecipeRegistration registration) {
      RecipeManager recipeManager = Minecraft.getInstance().level.getRecipeManager();
      List<KikonoRecipe> recipes = new ArrayList<>();

      for (RecipeHolder<?> holder : recipeManager.getRecipes()) {
         if (holder.value() instanceof KikonoRecipe kikonoRecipe) {
            recipes.add(kikonoRecipe);
         }
      }

      if (!recipes.isEmpty()) {
         registration.addRecipes(KikonoStationCategory.TYPE, recipes);
      }
   }

   public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
      registration.addRecipeCatalyst(new ItemStack((ItemLike)MainBlocks.KIKONO_STATION.get()), new RecipeType[]{KikonoStationCategory.TYPE});
   }

   public void registerGuiHandlers(IGuiHandlerRegistration registration) {
      registration.addRecipeClickArea(KikonoStationScreen.class, 111, 35, 26, 17, new RecipeType[]{KikonoStationCategory.TYPE});
   }
}
