package com.dragonminez.common.datagen;

import com.dragonminez.common.dragonball.DragonBallDefinitions;
import com.dragonminez.common.dragonball.DragonBallPackManager;
import com.dragonminez.common.dragonball.DragonRadarDefinition;
import com.dragonminez.common.dragonball.DragonRadarRecipeDefinition;
import com.dragonminez.common.init.MainBlocks;
import com.dragonminez.common.init.MainItems;
import com.dragonminez.common.init.MainTags;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.data.recipes.ShapelessRecipeBuilder;
import net.minecraft.data.recipes.SimpleCookingRecipeBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.BlastingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.item.crafting.AbstractCookingRecipe.Factory;
import net.minecraft.world.level.ItemLike;
import net.neoforged.neoforge.common.conditions.IConditionBuilder;
import org.jetbrains.annotations.NotNull;

public class DMZRecipeProvider extends RecipeProvider implements IConditionBuilder {
   private static final List<ItemLike> Gete = List.of((ItemLike)MainBlocks.GETE_ORE.get());
   private static final List<ItemLike> Kikono = List.of((ItemLike)MainBlocks.NAMEK_KIKONO_ORE.get());
   private static final List<ItemLike> Diamantes = List.of((ItemLike)MainBlocks.NAMEK_DIAMOND_ORE.get(), (ItemLike)MainBlocks.NAMEK_DEEPSLATE_DIAMOND.get());
   private static final List<ItemLike> Esmeraldas = List.of((ItemLike)MainBlocks.NAMEK_EMERALD_ORE.get(), (ItemLike)MainBlocks.NAMEK_DEEPSLATE_EMERALD.get());
   private static final List<ItemLike> Lapis = List.of((ItemLike)MainBlocks.NAMEK_LAPIS_ORE.get(), (ItemLike)MainBlocks.NAMEK_DEEPSLATE_LAPIS.get());
   private static final List<ItemLike> Redstone = List.of((ItemLike)MainBlocks.NAMEK_REDSTONE_ORE.get(), (ItemLike)MainBlocks.NAMEK_DEEPSLATE_REDSTONE.get());
   private static final List<ItemLike> Hierro = List.of((ItemLike)MainBlocks.NAMEK_IRON_ORE.get(), (ItemLike)MainBlocks.NAMEK_DEEPSLATE_IRON.get());
   private static final List<ItemLike> Oro = List.of((ItemLike)MainBlocks.NAMEK_GOLD_ORE.get(), (ItemLike)MainBlocks.NAMEK_DEEPSLATE_GOLD.get());
   private static final List<ItemLike> Cobre = List.of((ItemLike)MainBlocks.NAMEK_COPPER_ORE.get(), (ItemLike)MainBlocks.NAMEK_DEEPSLATE_COPPER.get());
   private static final List<ItemLike> Carbon = List.of((ItemLike)MainBlocks.NAMEK_COAL_ORE.get(), (ItemLike)MainBlocks.NAMEK_DEEPSLATE_COAL.get());

   public DMZRecipeProvider(PackOutput pOutput, CompletableFuture<Provider> registries) {
      super(pOutput, registries);
   }

   protected void buildRecipes(@NotNull RecipeOutput pWriter) {
      DragonBallPackManager.LoadedDefinitions externalDragonballPacks = DragonBallPackManager.loadAll();
      Map<String, DragonRadarDefinition> radarDefinitions = new LinkedHashMap<>();

      for (DragonRadarDefinition radarDefinition : DragonBallDefinitions.getBootstrapRadars()) {
         radarDefinitions.put(radarDefinition.getId(), radarDefinition);
      }

      for (DragonRadarDefinition radarDefinition : externalDragonballPacks.radars.values()) {
         radarDefinitions.put(radarDefinition.getId(), radarDefinition);
      }

      for (DragonRadarDefinition radarDefinition : DragonBallDefinitions.getRadars()) {
         radarDefinitions.put(radarDefinition.getId(), radarDefinition);
      }

      for (DragonRadarDefinition radarDefinition : radarDefinitions.values()) {
         if (!"earth_radar".equals(radarDefinition.getId()) && !"namek_radar".equals(radarDefinition.getId())) {
            DragonRadarRecipeDefinition recipeDefinition = radarDefinition.resolveRecipeDefinition();
            if (recipeDefinition != null) {
               recipeDefinition.buildRecipes(pWriter, radarDefinition);
            }
         }
      }

      oreBlasting(pWriter, Gete, RecipeCategory.MISC, (ItemLike)MainItems.GETE_SCRAP.get(), 3.5F, 100, "gete");
      oreSmelting(pWriter, Gete, RecipeCategory.MISC, (ItemLike)MainItems.GETE_SCRAP.get(), 3.5F, 200, "gete");
      oreBlasting(pWriter, Kikono, RecipeCategory.MISC, (ItemLike)MainItems.KIKONO_SHARD.get(), 2.5F, 100, "kikono");
      oreSmelting(pWriter, Kikono, RecipeCategory.MISC, (ItemLike)MainItems.KIKONO_SHARD.get(), 2.5F, 200, "kikono");
      oreBlasting(pWriter, Diamantes, RecipeCategory.MISC, Items.DIAMOND, 1.0F, 100, "diamond");
      oreSmelting(pWriter, Diamantes, RecipeCategory.MISC, Items.DIAMOND, 1.0F, 200, "diamond");
      oreBlasting(pWriter, Esmeraldas, RecipeCategory.MISC, Items.EMERALD, 1.0F, 100, "emerald");
      oreSmelting(pWriter, Esmeraldas, RecipeCategory.MISC, Items.EMERALD, 1.0F, 200, "emerald");
      oreBlasting(pWriter, Lapis, RecipeCategory.MISC, Items.LAPIS_LAZULI, 0.2F, 100, "lapis_lazuli");
      oreSmelting(pWriter, Lapis, RecipeCategory.MISC, Items.LAPIS_LAZULI, 0.2F, 200, "lapis_lazuli");
      oreBlasting(pWriter, Redstone, RecipeCategory.MISC, Items.REDSTONE, 0.7F, 100, "redstone");
      oreSmelting(pWriter, Redstone, RecipeCategory.MISC, Items.REDSTONE, 0.7F, 200, "redstone");
      oreBlasting(pWriter, Hierro, RecipeCategory.MISC, Items.IRON_INGOT, 0.7F, 100, "iron_ingot");
      oreSmelting(pWriter, Hierro, RecipeCategory.MISC, Items.IRON_INGOT, 0.7F, 200, "iron_ingot");
      oreBlasting(pWriter, Oro, RecipeCategory.MISC, Items.GOLD_INGOT, 1.0F, 100, "gold_ingot");
      oreSmelting(pWriter, Oro, RecipeCategory.MISC, Items.GOLD_INGOT, 1.0F, 200, "gold_ingot");
      oreBlasting(pWriter, Cobre, RecipeCategory.MISC, Items.COPPER_INGOT, 0.7F, 100, "copper_ingot");
      oreSmelting(pWriter, Cobre, RecipeCategory.MISC, Items.COPPER_INGOT, 0.7F, 200, "copper_ingot");
      oreBlasting(pWriter, Carbon, RecipeCategory.MISC, Items.COAL, 0.1F, 100, "coal");
      oreSmelting(pWriter, Carbon, RecipeCategory.MISC, Items.COAL, 0.1F, 200, "coal");
      ShapedRecipeBuilder.shaped(RecipeCategory.MISC, (ItemLike)MainBlocks.GRAVITY_DEVICE.get())
         .pattern("RWR")
         .pattern("ICI")
         .pattern("RAR")
         .define('R', Items.REPEATER)
         .define('W', Ingredient.of(MainTags.Items.WEIGHTED_ITEMS))
         .define('I', Items.IRON_BLOCK)
         .define('C', (ItemLike)MainItems.T1_RADAR_CPU.get())
         .define('A', Items.ANVIL)
         .unlockedBy("has_radar_cpu", has((ItemLike)MainItems.T1_RADAR_CPU.get()))
         .save(pWriter);
      this.geteCapsule(pWriter, (ItemLike)MainItems.RED_CAPSULE.get(), (ItemLike)MainItems.GETE_RED_CAPSULE.get(), "gete_red_capsule");
      this.geteCapsule(pWriter, (ItemLike)MainItems.PURPLE_CAPSULE.get(), (ItemLike)MainItems.GETE_PURPLE_CAPSULE.get(), "gete_purple_capsule");
      this.geteCapsule(pWriter, (ItemLike)MainItems.YELLOW_CAPSULE.get(), (ItemLike)MainItems.GETE_YELLOW_CAPSULE.get(), "gete_yellow_capsule");
      this.geteCapsule(pWriter, (ItemLike)MainItems.GREEN_CAPSULE.get(), (ItemLike)MainItems.GETE_GREEN_CAPSULE.get(), "gete_green_capsule");
      this.geteCapsule(pWriter, (ItemLike)MainItems.ORANGE_CAPSULE.get(), (ItemLike)MainItems.GETE_ORANGE_CAPSULE.get(), "gete_orange_capsule");
      this.geteCapsule(pWriter, (ItemLike)MainItems.BLUE_CAPSULE.get(), (ItemLike)MainItems.GETE_BLUE_CAPSULE.get(), "gete_blue_capsule");
      ShapedRecipeBuilder.shaped(RecipeCategory.MISC, (ItemLike)MainItems.GETE_SMITHING_TEMPLATE.get(), 1)
         .pattern("IGI")
         .pattern("GTG")
         .pattern("IGI")
         .define('T', Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE)
         .define('G', (ItemLike)MainItems.GETE_SCRAP.get())
         .define('I', Items.IRON_INGOT)
         .unlockedBy(getHasName((ItemLike)MainItems.GETE_SCRAP.get()), has((ItemLike)MainItems.GETE_SCRAP.get()))
         .group("dragonminez")
         .save(pWriter);
      ShapedRecipeBuilder.shaped(RecipeCategory.MISC, (ItemLike)MainItems.KI_BATTERY.get())
         .pattern("RLR")
         .pattern("LGL")
         .pattern("RLR")
         .define('R', Items.REDSTONE)
         .define('L', Items.LAPIS_LAZULI)
         .define('G', (ItemLike)MainItems.GETE_INGOT.get())
         .unlockedBy(getHasName((ItemLike)MainItems.GETE_INGOT.get()), has((ItemLike)MainItems.GETE_INGOT.get()))
         .group("dragonminez")
         .save(pWriter, ResourceLocation.fromNamespaceAndPath("dragonminez", "ki_battery"));
      SimpleCookingRecipeBuilder.smelting(
            Ingredient.of(new ItemLike[]{(ItemLike)MainItems.FROG_LEGS_RAW.get()}), RecipeCategory.FOOD, (ItemLike)MainItems.FROG_LEGS_COOKED.get(), 0.35F, 200
         )
         .unlockedBy(getHasName((ItemLike)MainItems.FROG_LEGS_RAW.get()), has((ItemLike)MainItems.FROG_LEGS_RAW.get()))
         .group("dragonminez")
         .save(pWriter, ResourceLocation.fromNamespaceAndPath("dragonminez", "frog_legs_cooked"));
      SimpleCookingRecipeBuilder.smoking(
            Ingredient.of(new ItemLike[]{(ItemLike)MainItems.FROG_LEGS_RAW.get()}), RecipeCategory.FOOD, (ItemLike)MainItems.FROG_LEGS_COOKED.get(), 0.35F, 100
         )
         .unlockedBy(getHasName((ItemLike)MainItems.FROG_LEGS_RAW.get()), has((ItemLike)MainItems.FROG_LEGS_RAW.get()))
         .group("dragonminez")
         .save(pWriter, ResourceLocation.fromNamespaceAndPath("dragonminez", "frog_legs_cooked_smoking"));
      SimpleCookingRecipeBuilder.campfireCooking(
            Ingredient.of(new ItemLike[]{(ItemLike)MainItems.FROG_LEGS_RAW.get()}), RecipeCategory.FOOD, (ItemLike)MainItems.FROG_LEGS_COOKED.get(), 0.35F, 600
         )
         .unlockedBy(getHasName((ItemLike)MainItems.FROG_LEGS_RAW.get()), has((ItemLike)MainItems.FROG_LEGS_RAW.get()))
         .group("dragonminez")
         .save(pWriter, ResourceLocation.fromNamespaceAndPath("dragonminez", "frog_legs_cooked_campfire"));
      ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, (ItemLike)MainBlocks.NAMEK_AJISSA_PRESSURE_PLATE.get(), 1)
         .pattern("##")
         .define('#', (ItemLike)MainBlocks.NAMEK_AJISSA_PLANKS.get())
         .unlockedBy(getHasName((ItemLike)MainBlocks.NAMEK_AJISSA_PLANKS.get()), has((ItemLike)MainBlocks.NAMEK_AJISSA_PLANKS.get()))
         .group("dragonminez")
         .save(pWriter);
      ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, (ItemLike)MainBlocks.NAMEK_SACRED_PRESSURE_PLATE.get(), 1)
         .pattern("##")
         .define('#', (ItemLike)MainBlocks.NAMEK_SACRED_PLANKS.get())
         .unlockedBy(getHasName((ItemLike)MainBlocks.NAMEK_SACRED_PLANKS.get()), has((ItemLike)MainBlocks.NAMEK_SACRED_PLANKS.get()))
         .group("dragonminez")
         .save(pWriter);
      ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, (ItemLike)MainBlocks.NAMEK_STONE_SLAB.get(), 6)
         .pattern("###")
         .define('#', (ItemLike)MainBlocks.NAMEK_STONE.get())
         .unlockedBy(getHasName((ItemLike)MainBlocks.NAMEK_STONE.get()), has((ItemLike)MainBlocks.NAMEK_STONE.get()))
         .group("dragonminez")
         .save(pWriter);
      ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, (ItemLike)MainBlocks.NAMEK_STONE_STAIRS.get(), 4)
         .pattern("#  ")
         .pattern("## ")
         .pattern("###")
         .define('#', (ItemLike)MainBlocks.NAMEK_STONE.get())
         .unlockedBy(getHasName((ItemLike)MainBlocks.NAMEK_STONE.get()), has((ItemLike)MainBlocks.NAMEK_STONE.get()))
         .group("dragonminez")
         .save(pWriter);
      ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, (ItemLike)MainBlocks.NAMEK_STONE_WALL.get(), 6)
         .pattern("###")
         .pattern("###")
         .define('#', (ItemLike)MainBlocks.NAMEK_STONE.get())
         .unlockedBy(getHasName((ItemLike)MainBlocks.NAMEK_STONE.get()), has((ItemLike)MainBlocks.NAMEK_STONE.get()))
         .group("dragonminez")
         .save(pWriter);
      ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, (ItemLike)MainBlocks.NAMEK_COBBLESTONE_SLAB.get(), 4)
         .pattern("###")
         .define('#', (ItemLike)MainBlocks.NAMEK_COBBLESTONE.get())
         .unlockedBy(getHasName((ItemLike)MainBlocks.NAMEK_COBBLESTONE.get()), has((ItemLike)MainBlocks.NAMEK_COBBLESTONE.get()))
         .group("dragonminez")
         .save(pWriter);
      ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, (ItemLike)MainBlocks.NAMEK_COBBLESTONE_STAIRS.get(), 4)
         .pattern("#  ")
         .pattern("## ")
         .pattern("###")
         .define('#', (ItemLike)MainBlocks.NAMEK_COBBLESTONE.get())
         .unlockedBy(getHasName((ItemLike)MainBlocks.NAMEK_COBBLESTONE.get()), has((ItemLike)MainBlocks.NAMEK_COBBLESTONE.get()))
         .group("dragonminez")
         .save(pWriter);
      ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, (ItemLike)MainBlocks.NAMEK_COBBLESTONE_WALL.get(), 6)
         .pattern("###")
         .pattern("###")
         .define('#', (ItemLike)MainBlocks.NAMEK_COBBLESTONE.get())
         .unlockedBy(getHasName((ItemLike)MainBlocks.NAMEK_COBBLESTONE.get()), has((ItemLike)MainBlocks.NAMEK_COBBLESTONE.get()))
         .group("dragonminez")
         .save(pWriter);
      ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, (ItemLike)MainBlocks.NAMEK_DEEPSLATE_SLAB.get(), 6)
         .pattern("###")
         .define('#', (ItemLike)MainBlocks.NAMEK_DEEPSLATE.get())
         .unlockedBy(getHasName((ItemLike)MainBlocks.NAMEK_DEEPSLATE.get()), has((ItemLike)MainBlocks.NAMEK_DEEPSLATE.get()))
         .group("dragonminez")
         .save(pWriter);
      ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, (ItemLike)MainBlocks.NAMEK_DEEPSLATE_STAIRS.get(), 4)
         .pattern("#  ")
         .pattern("## ")
         .pattern("###")
         .define('#', (ItemLike)MainBlocks.NAMEK_DEEPSLATE.get())
         .unlockedBy(getHasName((ItemLike)MainBlocks.NAMEK_DEEPSLATE.get()), has((ItemLike)MainBlocks.NAMEK_DEEPSLATE.get()))
         .group("dragonminez")
         .save(pWriter);
      ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, (ItemLike)MainBlocks.NAMEK_DEEPSLATE_WALL.get(), 6)
         .pattern("###")
         .pattern("###")
         .define('#', (ItemLike)MainBlocks.NAMEK_DEEPSLATE.get())
         .unlockedBy(getHasName((ItemLike)MainBlocks.NAMEK_DEEPSLATE.get()), has((ItemLike)MainBlocks.NAMEK_DEEPSLATE.get()))
         .group("dragonminez")
         .save(pWriter);
      ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, (ItemLike)MainBlocks.ROCKY_STONE_SLAB.get(), 6)
         .pattern("###")
         .define('#', (ItemLike)MainBlocks.ROCKY_STONE.get())
         .unlockedBy(getHasName((ItemLike)MainBlocks.ROCKY_STONE.get()), has((ItemLike)MainBlocks.ROCKY_STONE.get()))
         .group("dragonminez")
         .save(pWriter);
      ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, (ItemLike)MainBlocks.ROCKY_STONE_STAIRS.get(), 4)
         .pattern("#  ")
         .pattern("## ")
         .pattern("###")
         .define('#', (ItemLike)MainBlocks.ROCKY_STONE.get())
         .unlockedBy(getHasName((ItemLike)MainBlocks.ROCKY_STONE.get()), has((ItemLike)MainBlocks.ROCKY_STONE.get()))
         .group("dragonminez")
         .save(pWriter);
      ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, (ItemLike)MainBlocks.ROCKY_STONE_WALL.get(), 6)
         .pattern("###")
         .pattern("###")
         .define('#', (ItemLike)MainBlocks.ROCKY_STONE.get())
         .unlockedBy(getHasName((ItemLike)MainBlocks.ROCKY_STONE.get()), has((ItemLike)MainBlocks.ROCKY_STONE.get()))
         .group("dragonminez")
         .save(pWriter);
      ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, (ItemLike)MainBlocks.ROCKY_COBBLESTONE_SLAB.get(), 6)
         .pattern("###")
         .define('#', (ItemLike)MainBlocks.ROCKY_COBBLESTONE.get())
         .unlockedBy(getHasName((ItemLike)MainBlocks.ROCKY_COBBLESTONE.get()), has((ItemLike)MainBlocks.ROCKY_COBBLESTONE.get()))
         .group("dragonminez")
         .save(pWriter);
      ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, (ItemLike)MainBlocks.ROCKY_COBBLESTONE_STAIRS.get(), 4)
         .pattern("#  ")
         .pattern("## ")
         .pattern("###")
         .define('#', (ItemLike)MainBlocks.ROCKY_COBBLESTONE.get())
         .unlockedBy(getHasName((ItemLike)MainBlocks.ROCKY_COBBLESTONE.get()), has((ItemLike)MainBlocks.ROCKY_COBBLESTONE.get()))
         .group("dragonminez")
         .save(pWriter);
      ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, (ItemLike)MainBlocks.ROCKY_COBBLESTONE_WALL.get(), 6)
         .pattern("###")
         .pattern("###")
         .define('#', (ItemLike)MainBlocks.ROCKY_COBBLESTONE.get())
         .unlockedBy(getHasName((ItemLike)MainBlocks.ROCKY_COBBLESTONE.get()), has((ItemLike)MainBlocks.ROCKY_COBBLESTONE.get()))
         .group("dragonminez")
         .save(pWriter);
      ShapedRecipeBuilder.shaped(RecipeCategory.REDSTONE, (ItemLike)MainItems.RADAR_PIECE.get(), 1)
         .pattern("SIS")
         .pattern("IRI")
         .pattern("SIS")
         .define('S', Items.STRING)
         .define('I', Items.IRON_INGOT)
         .define('R', Items.REDSTONE)
         .unlockedBy(getHasName(Items.REDSTONE), has(Items.REDSTONE))
         .group("dragonminez")
         .save(pWriter);
      ShapedRecipeBuilder.shaped(RecipeCategory.REDSTONE, (ItemLike)MainItems.T1_RADAR_CHIP.get(), 1)
         .pattern("RBR")
         .pattern("GPG")
         .pattern("RBR")
         .define('R', Items.REDSTONE)
         .define('G', Items.GREEN_CONCRETE)
         .define('P', (ItemLike)MainItems.RADAR_PIECE.get())
         .define('B', Items.REPEATER)
         .unlockedBy(getHasName((ItemLike)MainItems.RADAR_PIECE.get()), has((ItemLike)MainItems.RADAR_PIECE.get()))
         .group("dragonminez")
         .save(pWriter);
      ShapedRecipeBuilder.shaped(RecipeCategory.REDSTONE, (ItemLike)MainItems.T1_RADAR_CPU.get(), 1)
         .pattern("RBR")
         .pattern("CPC")
         .pattern("ROR")
         .define('R', Items.COMPARATOR)
         .define('O', Items.OBSERVER)
         .define('C', (ItemLike)MainItems.T1_RADAR_CHIP.get())
         .define('B', Items.REPEATER)
         .define('P', (ItemLike)MainItems.RADAR_PIECE.get())
         .unlockedBy(getHasName((ItemLike)MainItems.T1_RADAR_CHIP.get()), has((ItemLike)MainItems.T1_RADAR_CHIP.get()))
         .group("dragonminez")
         .save(pWriter);
      ShapedRecipeBuilder.shaped(RecipeCategory.REDSTONE, (ItemLike)MainItems.T2_RADAR_CPU.get(), 1)
         .pattern("RCR")
         .pattern("TTT")
         .pattern("OCO")
         .define('R', Items.COMPARATOR)
         .define('O', Items.OBSERVER)
         .define('C', (ItemLike)MainItems.T2_RADAR_CHIP.get())
         .define('T', (ItemLike)MainItems.T1_RADAR_CPU.get())
         .unlockedBy(getHasName((ItemLike)MainItems.T2_RADAR_CHIP.get()), has((ItemLike)MainItems.T2_RADAR_CHIP.get()))
         .group("dragonminez")
         .save(pWriter);
      ShapedRecipeBuilder.shaped(RecipeCategory.TRANSPORTATION, (ItemLike)MainItems.NAVE_SAIYAN_ITEM.get(), 1)
         .pattern("QCQ")
         .pattern("CTC")
         .pattern("RML")
         .define('Q', Items.QUARTZ_BLOCK)
         .define('C', (ItemLike)MainItems.T2_RADAR_CHIP.get())
         .define('R', Items.REPEATER)
         .define('M', Items.MINECART)
         .define('L', Items.RED_WOOL)
         .define('T', (ItemLike)MainItems.T2_RADAR_CPU.get())
         .unlockedBy(getHasName((ItemLike)MainItems.T2_RADAR_CPU.get()), has((ItemLike)MainItems.T2_RADAR_CPU.get()))
         .group("dragonminez")
         .save(pWriter);
      ShapedRecipeBuilder.shaped(RecipeCategory.REDSTONE, (ItemLike)MainItems.T2_RADAR_CHIP.get(), 1)
         .pattern("ROR")
         .pattern("BPB")
         .pattern("TTT")
         .define('O', Items.OBSERVER)
         .define('R', Items.REDSTONE_TORCH)
         .define('B', Items.BLUE_CONCRETE)
         .define('P', (ItemLike)MainItems.RADAR_PIECE.get())
         .define('T', (ItemLike)MainItems.T1_RADAR_CPU.get())
         .unlockedBy(getHasName((ItemLike)MainItems.RADAR_PIECE.get()), has((ItemLike)MainItems.RADAR_PIECE.get()))
         .group("dragonminez")
         .save(pWriter);

      for (DragonRadarDefinition radarDefinitionx : DragonBallDefinitions.getRadars()) {
         DragonRadarRecipeDefinition recipeDefinition = radarDefinitionx.resolveRecipeDefinition();
         if (recipeDefinition != null) {
            recipeDefinition.buildRecipes(pWriter, radarDefinitionx);
         }
      }

      ShapedRecipeBuilder.shaped(RecipeCategory.COMBAT, (ItemLike)MainItems.KATANA_YAJIROBE.get(), 1)
         .pattern("  I")
         .pattern("GI ")
         .pattern("SG ")
         .define('I', Items.IRON_INGOT)
         .define('G', Items.GOLD_INGOT)
         .define('S', Items.STICK)
         .unlockedBy(getHasName(Items.IRON_INGOT), has(Items.IRON_INGOT))
         .group("dragonminez")
         .save(pWriter);
      ShapedRecipeBuilder.shaped(RecipeCategory.TOOLS, (ItemLike)MainItems.RED_SCOUTER.get(), 1)
         .pattern("  R")
         .pattern("PPT")
         .pattern(" B ")
         .define('R', (ItemLike)MainItems.RADAR_PIECE.get())
         .define('P', Items.RED_STAINED_GLASS_PANE)
         .define('T', (ItemLike)MainItems.T1_RADAR_CPU.get())
         .define('B', Items.STONE_BUTTON)
         .unlockedBy(getHasName((ItemLike)MainItems.T1_RADAR_CPU.get()), has((ItemLike)MainItems.T1_RADAR_CPU.get()))
         .group("dragonminez")
         .save(pWriter);
      ShapedRecipeBuilder.shaped(RecipeCategory.TOOLS, (ItemLike)MainItems.BLUE_SCOUTER.get(), 1)
         .pattern("  R")
         .pattern("PPT")
         .pattern(" B ")
         .define('R', (ItemLike)MainItems.RADAR_PIECE.get())
         .define('P', Items.BLUE_STAINED_GLASS_PANE)
         .define('T', (ItemLike)MainItems.T1_RADAR_CPU.get())
         .define('B', Items.STONE_BUTTON)
         .unlockedBy(getHasName((ItemLike)MainItems.T1_RADAR_CPU.get()), has((ItemLike)MainItems.T1_RADAR_CPU.get()))
         .group("dragonminez")
         .save(pWriter);
      ShapedRecipeBuilder.shaped(RecipeCategory.TOOLS, (ItemLike)MainItems.GREEN_SCOUTER.get(), 1)
         .pattern("  R")
         .pattern("PPT")
         .pattern(" B ")
         .define('R', (ItemLike)MainItems.RADAR_PIECE.get())
         .define('P', Items.GREEN_STAINED_GLASS_PANE)
         .define('T', (ItemLike)MainItems.T1_RADAR_CPU.get())
         .define('B', Items.STONE_BUTTON)
         .unlockedBy(getHasName((ItemLike)MainItems.T1_RADAR_CPU.get()), has((ItemLike)MainItems.T1_RADAR_CPU.get()))
         .group("dragonminez")
         .save(pWriter);
      ShapedRecipeBuilder.shaped(RecipeCategory.TOOLS, (ItemLike)MainItems.PURPLE_SCOUTER.get(), 1)
         .pattern("  R")
         .pattern("PPT")
         .pattern(" B ")
         .define('R', (ItemLike)MainItems.RADAR_PIECE.get())
         .define('P', Items.PURPLE_STAINED_GLASS_PANE)
         .define('T', (ItemLike)MainItems.T1_RADAR_CPU.get())
         .define('B', Items.STONE_BUTTON)
         .unlockedBy(getHasName((ItemLike)MainItems.T1_RADAR_CPU.get()), has((ItemLike)MainItems.T1_RADAR_CPU.get()))
         .group("dragonminez")
         .save(pWriter);
      ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, (ItemLike)MainBlocks.KIKONO_STATION.get(), 1)
         .pattern("ACA")
         .pattern("KTK")
         .pattern("PSP")
         .define('A', Items.ANVIL)
         .define('C', Items.DIAMOND_CHESTPLATE)
         .define('K', (ItemLike)MainBlocks.KIKONO_BLOCK.get())
         .define('T', (ItemLike)MainItems.T2_RADAR_CPU.get())
         .define('P', Items.HEAVY_WEIGHTED_PRESSURE_PLATE)
         .define('S', Items.SMITHING_TABLE)
         .unlockedBy(getHasName((ItemLike)MainItems.T2_RADAR_CPU.get()), has((ItemLike)MainItems.T2_RADAR_CPU.get()))
         .group("dragonminez")
         .save(pWriter);
      ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, (ItemLike)MainBlocks.FUEL_GENERATOR.get(), 1)
         .pattern("OOO")
         .pattern("OTO")
         .pattern("OOO")
         .define('O', Items.CRYING_OBSIDIAN)
         .define('T', (ItemLike)MainItems.T1_RADAR_CHIP.get())
         .unlockedBy(getHasName(Items.CRYING_OBSIDIAN), has(Items.CRYING_OBSIDIAN))
         .group("dragonminez")
         .save(pWriter);
      ShapedRecipeBuilder.shaped(RecipeCategory.REDSTONE, (ItemLike)MainBlocks.ENERGY_CABLE.get(), 4)
         .pattern(" W ")
         .pattern("WCW")
         .pattern(" W ")
         .define('W', Items.BLACK_WOOL)
         .define('C', (ItemLike)MainItems.T1_RADAR_CHIP.get())
         .unlockedBy(getHasName((ItemLike)MainItems.T1_RADAR_CHIP.get()), has((ItemLike)MainItems.T1_RADAR_CHIP.get()))
         .group("dragonminez")
         .save(pWriter);
      ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, (ItemLike)MainItems.KIKONO_SHARD.get(), 9)
         .requires((ItemLike)MainBlocks.KIKONO_BLOCK.get())
         .unlockedBy(getHasName((ItemLike)MainBlocks.KIKONO_BLOCK.get()), has((ItemLike)MainBlocks.KIKONO_BLOCK.get()))
         .group("dragonminez")
         .save(pWriter);
      ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, (ItemLike)MainBlocks.KIKONO_BLOCK.get(), 1)
         .pattern("KKK")
         .pattern("KKK")
         .pattern("KKK")
         .define('K', (ItemLike)MainItems.KIKONO_SHARD.get())
         .unlockedBy(getHasName((ItemLike)MainItems.KIKONO_SHARD.get()), has((ItemLike)MainItems.KIKONO_SHARD.get()))
         .group("dragonminez")
         .save(pWriter);
      ShapelessRecipeBuilder.shapeless(RecipeCategory.TOOLS, (ItemLike)MainItems.ARMOR_CRAFTING_KIT.get(), 1)
         .requires(Items.RED_WOOL)
         .requires(Items.SHEARS)
         .unlockedBy(getHasName(Items.SHEARS), has(Items.SHEARS))
         .group("dragonminez")
         .save(pWriter);
      ShapedRecipeBuilder.shaped(RecipeCategory.REDSTONE, (ItemLike)MainItems.KIKONO_STICK.get(), 2)
         .pattern("K")
         .pattern("K")
         .define('K', (ItemLike)MainItems.KIKONO_SHARD.get())
         .unlockedBy(getHasName((ItemLike)MainItems.KIKONO_SHARD.get()), has((ItemLike)MainItems.KIKONO_SHARD.get()))
         .group("dragonminez")
         .save(pWriter);
      ShapelessRecipeBuilder.shapeless(RecipeCategory.REDSTONE, (ItemLike)MainItems.KIKONO_STRING.get(), 2)
         .requires((ItemLike)MainItems.KIKONO_SHARD.get())
         .requires((ItemLike)MainItems.ARMOR_CRAFTING_KIT.get())
         .unlockedBy(getHasName((ItemLike)MainItems.KIKONO_SHARD.get()), has((ItemLike)MainItems.KIKONO_SHARD.get()))
         .group("dragonminez")
         .save(pWriter);
      ShapelessRecipeBuilder.shapeless(RecipeCategory.REDSTONE, (ItemLike)MainItems.KIKONO_CLOTH.get(), 1)
         .requires((ItemLike)MainItems.KIKONO_STRING.get(), 4)
         .requires((ItemLike)MainItems.ARMOR_CRAFTING_KIT.get())
         .unlockedBy(getHasName((ItemLike)MainItems.KIKONO_STRING.get()), has((ItemLike)MainItems.KIKONO_STRING.get()))
         .group("dragonminez")
         .save(pWriter);
      ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, (ItemLike)MainItems.BLANK_PATTERN_Z.get(), 1)
         .pattern("RRR")
         .pattern("#W#")
         .pattern("RRR")
         .define('#', Items.PAPER)
         .define('W', Items.WHITE_WOOL)
         .define('R', Items.RED_WOOL)
         .unlockedBy(getHasName(Items.PAPER), has(Items.PAPER))
         .group("dragonminez")
         .save(pWriter);
      ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, (ItemLike)MainItems.BLANK_PATTERN_SUPER.get(), 1)
         .pattern("CPC")
         .pattern("#W#")
         .pattern("CPC")
         .define('#', Items.PAPER)
         .define('W', Items.WHITE_WOOL)
         .define('P', (ItemLike)MainItems.BLANK_PATTERN_Z.get())
         .define('C', Items.CYAN_WOOL)
         .unlockedBy(getHasName(Items.PAPER), has(Items.PAPER))
         .group("dragonminez")
         .save(pWriter);
      ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, (ItemLike)MainItems.PATTERN_GOKU_KID.get(), 1)
         .pattern("R#R")
         .pattern("R R")
         .pattern("RRR")
         .define('R', Items.RED_DYE)
         .define('#', (ItemLike)MainItems.BLANK_PATTERN_Z.get())
         .unlockedBy(getHasName((ItemLike)MainItems.BLANK_PATTERN_Z.get()), has((ItemLike)MainItems.BLANK_PATTERN_Z.get()))
         .group("dragonminez")
         .save(pWriter);
      ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, (ItemLike)MainItems.PATTERN_GOKU1.get(), 1)
         .pattern("B#B")
         .pattern("OBO")
         .pattern("OOO")
         .define('B', Items.BLUE_DYE)
         .define('O', Items.ORANGE_DYE)
         .define('#', (ItemLike)MainItems.BLANK_PATTERN_Z.get())
         .unlockedBy(getHasName((ItemLike)MainItems.BLANK_PATTERN_Z.get()), has((ItemLike)MainItems.BLANK_PATTERN_Z.get()))
         .group("dragonminez")
         .save(pWriter);
      ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, (ItemLike)MainItems.PATTERN_GOKU_SUPER.get(), 1)
         .pattern("C#C")
         .pattern("OCO")
         .pattern("OWO")
         .define('C', Items.CYAN_DYE)
         .define('O', Items.ORANGE_DYE)
         .define('W', Items.WHITE_DYE)
         .define('#', (ItemLike)MainItems.BLANK_PATTERN_SUPER.get())
         .unlockedBy(getHasName((ItemLike)MainItems.BLANK_PATTERN_SUPER.get()), has((ItemLike)MainItems.BLANK_PATTERN_SUPER.get()))
         .group("dragonminez")
         .save(pWriter);
      ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, (ItemLike)MainItems.PATTERN_GOKU_GT.get(), 1)
         .pattern("L#L")
         .pattern("LLL")
         .pattern("YWY")
         .define('L', Items.LIGHT_BLUE_DYE)
         .define('Y', Items.YELLOW_DYE)
         .define('W', Items.WHITE_DYE)
         .define('#', (ItemLike)MainItems.BLANK_PATTERN_Z.get())
         .unlockedBy(getHasName((ItemLike)MainItems.BLANK_PATTERN_Z.get()), has((ItemLike)MainItems.BLANK_PATTERN_Z.get()))
         .group("dragonminez")
         .save(pWriter);
      ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, (ItemLike)MainItems.PATTERN_YARDRAT.get(), 1)
         .pattern("W#P")
         .pattern("WBW")
         .pattern("BYB")
         .define('W', Items.WHITE_DYE)
         .define('P', Items.PINK_DYE)
         .define('B', Items.BLUE_DYE)
         .define('Y', Items.YELLOW_DYE)
         .define('#', (ItemLike)MainItems.BLANK_PATTERN_Z.get())
         .unlockedBy(getHasName((ItemLike)MainItems.BLANK_PATTERN_Z.get()), has((ItemLike)MainItems.BLANK_PATTERN_Z.get()))
         .group("dragonminez")
         .save(pWriter);
      ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, (ItemLike)MainItems.PATTERN_GOTEN.get(), 1)
         .pattern("B#B")
         .pattern("OBO")
         .pattern("ONO")
         .define('B', Items.BLUE_DYE)
         .define('O', Items.ORANGE_DYE)
         .define('N', Items.BLACK_DYE)
         .define('#', (ItemLike)MainItems.BLANK_PATTERN_Z.get())
         .unlockedBy(getHasName(Items.BLUE_DYE), has(Items.BLUE_DYE))
         .group("dragonminez")
         .save(pWriter);
      ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, (ItemLike)MainItems.PATTERN_GOTEN_SUPER.get(), 1)
         .pattern("G#G")
         .pattern("GGG")
         .pattern("BBB")
         .define('G', Items.GREEN_DYE)
         .define('B', Items.BLUE_DYE)
         .define('#', (ItemLike)MainItems.BLANK_PATTERN_SUPER.get())
         .unlockedBy(getHasName((ItemLike)MainItems.BLANK_PATTERN_SUPER.get()), has((ItemLike)MainItems.BLANK_PATTERN_SUPER.get()))
         .group("dragonminez")
         .save(pWriter);
      ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, (ItemLike)MainItems.PATTERN_GOHAN_SUPER.get(), 1)
         .pattern("P#P")
         .pattern("PPP")
         .pattern("RRR")
         .define('P', Items.PURPLE_DYE)
         .define('R', Items.RED_DYE)
         .define('#', (ItemLike)MainItems.BLANK_PATTERN_SUPER.get())
         .unlockedBy(getHasName((ItemLike)MainItems.BLANK_PATTERN_SUPER.get()), has((ItemLike)MainItems.BLANK_PATTERN_SUPER.get()))
         .group("dragonminez")
         .save(pWriter);
      ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, (ItemLike)MainItems.PATTERN_GREAT_SAIYAMAN.get(), 1)
         .pattern("R#R")
         .pattern("GGG")
         .pattern("BYB")
         .define('R', Items.RED_DYE)
         .define('G', Items.GREEN_DYE)
         .define('B', Items.BLACK_DYE)
         .define('Y', Items.YELLOW_DYE)
         .define('#', (ItemLike)MainItems.BLANK_PATTERN_Z.get())
         .unlockedBy(getHasName((ItemLike)MainItems.BLANK_PATTERN_Z.get()), has((ItemLike)MainItems.BLANK_PATTERN_Z.get()))
         .group("dragonminez")
         .save(pWriter);
      ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, (ItemLike)MainItems.PATTERN_FUTURE_GOHAN.get(), 1)
         .pattern("B#B")
         .pattern("OBO")
         .pattern("ORO")
         .define('B', Items.BLUE_DYE)
         .define('O', Items.ORANGE_DYE)
         .define('R', Items.CLOCK)
         .define('#', (ItemLike)MainItems.BLANK_PATTERN_Z.get())
         .unlockedBy(getHasName((ItemLike)MainItems.BLANK_PATTERN_Z.get()), has((ItemLike)MainItems.BLANK_PATTERN_Z.get()))
         .group("dragonminez")
         .save(pWriter);
      ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, (ItemLike)MainItems.PATTERN_VEGETA1.get(), 1)
         .pattern("Y#Y")
         .pattern("BWB")
         .pattern("BYB")
         .define('Y', Items.YELLOW_DYE)
         .define('B', Items.BLUE_DYE)
         .define('W', Items.WHITE_DYE)
         .define('#', (ItemLike)MainItems.BLANK_PATTERN_Z.get())
         .unlockedBy(getHasName(Items.BLUE_DYE), has(Items.BLUE_DYE))
         .group("dragonminez")
         .save(pWriter);
      ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, (ItemLike)MainItems.PATTERN_VEGETA2.get(), 1)
         .pattern("B#B")
         .pattern("BWB")
         .pattern("BYB")
         .define('B', Items.BLUE_DYE)
         .define('W', Items.WHITE_DYE)
         .define('Y', Items.YELLOW_DYE)
         .define('#', (ItemLike)MainItems.BLANK_PATTERN_Z.get())
         .unlockedBy(getHasName(Items.BLUE_DYE), has(Items.BLUE_DYE))
         .group("dragonminez")
         .save(pWriter);
      ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, (ItemLike)MainItems.PATTERN_VEGETA_Z.get(), 1)
         .pattern("Y#Y")
         .pattern("BWB")
         .pattern("YBY")
         .define('B', Items.BLUE_DYE)
         .define('W', Items.WHITE_DYE)
         .define('Y', Items.YELLOW_DYE)
         .define('#', (ItemLike)MainItems.BLANK_PATTERN_Z.get())
         .unlockedBy(getHasName((ItemLike)MainItems.BLANK_PATTERN_Z.get()), has((ItemLike)MainItems.BLANK_PATTERN_Z.get()))
         .group("dragonminez")
         .save(pWriter);
      ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, (ItemLike)MainItems.PATTERN_VEGETA_BUU.get(), 1)
         .pattern("C#C")
         .pattern("BCB")
         .pattern("BBB")
         .define('B', Items.BLUE_DYE)
         .define('C', Items.CYAN_DYE)
         .define('#', (ItemLike)MainItems.BLANK_PATTERN_Z.get())
         .unlockedBy(getHasName((ItemLike)MainItems.BLANK_PATTERN_Z.get()), has((ItemLike)MainItems.BLANK_PATTERN_Z.get()))
         .group("dragonminez")
         .save(pWriter);
      ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, (ItemLike)MainItems.PATTERN_VEGETA_SUPER.get(), 1)
         .pattern("Y#Y")
         .pattern("CWC")
         .pattern("CYC")
         .define('C', Items.CYAN_DYE)
         .define('W', Items.WHITE_DYE)
         .define('Y', Items.YELLOW_DYE)
         .define('#', (ItemLike)MainItems.BLANK_PATTERN_SUPER.get())
         .unlockedBy(getHasName((ItemLike)MainItems.BLANK_PATTERN_SUPER.get()), has((ItemLike)MainItems.BLANK_PATTERN_SUPER.get()))
         .group("dragonminez")
         .save(pWriter);
      ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, (ItemLike)MainItems.PATTERN_VEGETTO.get(), 1)
         .pattern("B#B")
         .pattern("BOB")
         .pattern("WCW")
         .define('B', Items.BLUE_DYE)
         .define('W', Items.WHITE_DYE)
         .define('O', Items.ORANGE_DYE)
         .define('C', Items.CYAN_DYE)
         .define('#', (ItemLike)MainItems.BLANK_PATTERN_Z.get())
         .unlockedBy(getHasName((ItemLike)MainItems.BLANK_PATTERN_Z.get()), has((ItemLike)MainItems.BLANK_PATTERN_Z.get()))
         .group("dragonminez")
         .save(pWriter);
      ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, (ItemLike)MainItems.PATTERN_GOGETA.get(), 1)
         .pattern("Y#Y")
         .pattern("B B")
         .pattern("CCC")
         .define('Y', Items.YELLOW_DYE)
         .define('B', Items.BLACK_DYE)
         .define('C', Items.CYAN_DYE)
         .define('#', (ItemLike)MainItems.BLANK_PATTERN_SUPER.get())
         .unlockedBy(getHasName((ItemLike)MainItems.BLANK_PATTERN_SUPER.get()), has((ItemLike)MainItems.BLANK_PATTERN_SUPER.get()))
         .group("dragonminez")
         .save(pWriter);
      ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, (ItemLike)MainItems.PATTERN_PICCOLO.get(), 1)
         .pattern("P#P")
         .pattern("PPP")
         .pattern("RRR")
         .define('P', Items.PURPLE_DYE)
         .define('R', Items.RED_DYE)
         .define('#', (ItemLike)MainItems.BLANK_PATTERN_Z.get())
         .unlockedBy(getHasName((ItemLike)MainItems.BLANK_PATTERN_Z.get()), has((ItemLike)MainItems.BLANK_PATTERN_Z.get()))
         .group("dragonminez")
         .save(pWriter);
      ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, (ItemLike)MainItems.PATTERN_GOHAN1.get(), 1)
         .pattern("P#P")
         .pattern("PPP")
         .pattern("BBB")
         .define('P', Items.PURPLE_DYE)
         .define('B', Items.BLUE_DYE)
         .define('#', (ItemLike)MainItems.BLANK_PATTERN_Z.get())
         .unlockedBy(getHasName(Items.BLUE_DYE), has(Items.BLUE_DYE))
         .group("dragonminez")
         .save(pWriter);
      ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, (ItemLike)MainItems.PATTERN_BARDOCK1.get(), 1)
         .pattern("G#G")
         .pattern("BBB")
         .pattern("RGR")
         .define('B', Items.BLACK_DYE)
         .define('G', Items.GREEN_DYE)
         .define('R', Items.RED_DYE)
         .define('#', (ItemLike)MainItems.BLANK_PATTERN_Z.get())
         .unlockedBy(getHasName((ItemLike)MainItems.BLANK_PATTERN_Z.get()), has((ItemLike)MainItems.BLANK_PATTERN_Z.get()))
         .group("dragonminez")
         .save(pWriter);
      ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, (ItemLike)MainItems.PATTERN_BARDOCK2.get(), 1)
         .pattern("Y#Y")
         .pattern("BBB")
         .pattern("CYC")
         .define('Y', Items.YELLOW_DYE)
         .define('B', Items.BLACK_DYE)
         .define('C', Items.CYAN_DYE)
         .define('#', (ItemLike)MainItems.BLANK_PATTERN_SUPER.get())
         .unlockedBy(getHasName((ItemLike)MainItems.BLANK_PATTERN_SUPER.get()), has((ItemLike)MainItems.BLANK_PATTERN_SUPER.get()))
         .group("dragonminez")
         .save(pWriter);
      ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, (ItemLike)MainItems.PATTERN_TURLES.get(), 1)
         .pattern("B#B")
         .pattern("NNN")
         .pattern("BBB")
         .define('B', Items.BLUE_DYE)
         .define('N', Items.BLACK_DYE)
         .define('#', (ItemLike)MainItems.BLANK_PATTERN_Z.get())
         .unlockedBy(getHasName((ItemLike)MainItems.BLANK_PATTERN_Z.get()), has((ItemLike)MainItems.BLANK_PATTERN_Z.get()))
         .group("dragonminez")
         .save(pWriter);
      ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, (ItemLike)MainItems.PATTERN_TIEN.get(), 1)
         .pattern(" #G")
         .pattern("GGG")
         .pattern("RRR")
         .define('G', Items.GREEN_DYE)
         .define('R', Items.RED_DYE)
         .define('#', (ItemLike)MainItems.BLANK_PATTERN_Z.get())
         .unlockedBy(getHasName((ItemLike)MainItems.BLANK_PATTERN_Z.get()), has((ItemLike)MainItems.BLANK_PATTERN_Z.get()))
         .group("dragonminez")
         .save(pWriter);
      ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, (ItemLike)MainItems.PATTERN_TRUNKS_Z.get(), 1)
         .pattern("B#B")
         .pattern("NSN")
         .pattern("NYN")
         .define('B', Items.BLUE_DYE)
         .define('N', Items.BLACK_DYE)
         .define('Y', Items.YELLOW_DYE)
         .define('S', Items.IRON_SWORD)
         .define('#', (ItemLike)MainItems.BLANK_PATTERN_Z.get())
         .unlockedBy(getHasName((ItemLike)MainItems.BLANK_PATTERN_Z.get()), has((ItemLike)MainItems.BLANK_PATTERN_Z.get()))
         .group("dragonminez")
         .save(pWriter);
      ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, (ItemLike)MainItems.PATTERN_TRUNKS_SUPER.get(), 1)
         .pattern("S#C")
         .pattern("CRC")
         .pattern("BBB")
         .define('B', Items.BLACK_DYE)
         .define('C', Items.CYAN_DYE)
         .define('R', Items.RED_DYE)
         .define('S', Items.IRON_SWORD)
         .define('#', (ItemLike)MainItems.BLANK_PATTERN_SUPER.get())
         .unlockedBy(getHasName((ItemLike)MainItems.BLANK_PATTERN_SUPER.get()), has((ItemLike)MainItems.BLANK_PATTERN_SUPER.get()))
         .group("dragonminez")
         .save(pWriter);
      ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, (ItemLike)MainItems.PATTERN_TRUNKS_KID.get(), 1)
         .pattern("G#G")
         .pattern("GGG")
         .pattern("OOO")
         .define('G', Items.GREEN_DYE)
         .define('O', Items.ORANGE_DYE)
         .define('#', (ItemLike)MainItems.BLANK_PATTERN_Z.get())
         .unlockedBy(getHasName((ItemLike)MainItems.BLANK_PATTERN_Z.get()), has((ItemLike)MainItems.BLANK_PATTERN_Z.get()))
         .group("dragonminez")
         .save(pWriter);
      ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, (ItemLike)MainItems.PATTERN_BROLY_Z.get(), 1)
         .pattern("Y#Y")
         .pattern(" I ")
         .pattern("RIR")
         .define('Y', Items.GOLD_NUGGET)
         .define('I', Items.GOLD_INGOT)
         .define('R', Items.RED_DYE)
         .define('#', (ItemLike)MainItems.BLANK_PATTERN_Z.get())
         .unlockedBy(getHasName((ItemLike)MainItems.BLANK_PATTERN_Z.get()), has((ItemLike)MainItems.BLANK_PATTERN_Z.get()))
         .group("dragonminez")
         .save(pWriter);
      ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, (ItemLike)MainItems.PATTERN_BROLY_SUPER.get(), 1)
         .pattern("G#G")
         .pattern("BBB")
         .pattern("LPL")
         .define('G', Items.GREEN_DYE)
         .define('B', Items.BLACK_DYE)
         .define('L', Items.LIME_DYE)
         .define('P', Items.PURPLE_DYE)
         .define('#', (ItemLike)MainItems.BLANK_PATTERN_SUPER.get())
         .unlockedBy(getHasName((ItemLike)MainItems.BLANK_PATTERN_SUPER.get()), has((ItemLike)MainItems.BLANK_PATTERN_SUPER.get()))
         .group("dragonminez")
         .save(pWriter);
      ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, (ItemLike)MainItems.PATTERN_SHIN.get(), 1)
         .pattern("C#C")
         .pattern("LRL")
         .pattern("OCO")
         .define('C', Items.CYAN_DYE)
         .define('L', Items.LIGHT_BLUE_DYE)
         .define('R', Items.RED_DYE)
         .define('O', Items.ORANGE_DYE)
         .define('#', (ItemLike)MainItems.BLANK_PATTERN_Z.get())
         .unlockedBy(getHasName((ItemLike)MainItems.BLANK_PATTERN_Z.get()), has((ItemLike)MainItems.BLANK_PATTERN_Z.get()))
         .group("dragonminez")
         .save(pWriter);
      ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, (ItemLike)MainItems.PATTERN_BLACK.get(), 1)
         .pattern("B#B")
         .pattern("GBG")
         .pattern("RGR")
         .define('B', Items.BLACK_DYE)
         .define('G', Items.GRAY_DYE)
         .define('R', Items.RED_DYE)
         .define('#', (ItemLike)MainItems.BLANK_PATTERN_SUPER.get())
         .unlockedBy(getHasName((ItemLike)MainItems.BLANK_PATTERN_SUPER.get()), has((ItemLike)MainItems.BLANK_PATTERN_SUPER.get()))
         .group("dragonminez")
         .save(pWriter);
      ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, (ItemLike)MainItems.PATTERN_ZAMASU.get(), 1)
         .pattern("B#B")
         .pattern("PBP")
         .pattern("LLL")
         .define('B', Items.BLACK_DYE)
         .define('P', Items.PURPLE_DYE)
         .define('L', Items.LIGHT_BLUE_DYE)
         .define('#', (ItemLike)MainItems.BLANK_PATTERN_SUPER.get())
         .unlockedBy(getHasName((ItemLike)MainItems.BLANK_PATTERN_SUPER.get()), has((ItemLike)MainItems.BLANK_PATTERN_SUPER.get()))
         .group("dragonminez")
         .save(pWriter);
      ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, (ItemLike)MainItems.PATTERN_FUSION_ZAMASU.get(), 1)
         .pattern("G#G")
         .pattern("BGB")
         .pattern("RRR")
         .define('G', Items.GRAY_DYE)
         .define('B', Items.BLACK_DYE)
         .define('R', Items.RED_DYE)
         .define('#', (ItemLike)MainItems.BLANK_PATTERN_SUPER.get())
         .unlockedBy(getHasName((ItemLike)MainItems.BLANK_PATTERN_SUPER.get()), has((ItemLike)MainItems.BLANK_PATTERN_SUPER.get()))
         .group("dragonminez")
         .save(pWriter);
      ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, (ItemLike)MainItems.PATTERN_PRIDE_TROOPS.get(), 1)
         .pattern("B#B")
         .pattern("RBR")
         .pattern("RBR")
         .define('B', Items.BLACK_DYE)
         .define('R', Items.RED_DYE)
         .define('#', (ItemLike)MainItems.BLANK_PATTERN_SUPER.get())
         .unlockedBy(getHasName((ItemLike)MainItems.BLANK_PATTERN_SUPER.get()), has((ItemLike)MainItems.BLANK_PATTERN_SUPER.get()))
         .group("dragonminez")
         .save(pWriter);
      ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, (ItemLike)MainItems.PATTERN_HIT.get(), 1)
         .pattern("P#P")
         .pattern("CPC")
         .pattern("CYC")
         .define('P', Items.PURPLE_DYE)
         .define('C', Items.CYAN_DYE)
         .define('Y', Items.YELLOW_DYE)
         .define('#', (ItemLike)MainItems.BLANK_PATTERN_SUPER.get())
         .unlockedBy(getHasName((ItemLike)MainItems.BLANK_PATTERN_SUPER.get()), has((ItemLike)MainItems.BLANK_PATTERN_SUPER.get()))
         .group("dragonminez")
         .save(pWriter);
      ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, (ItemLike)MainItems.PATTERN_GAS.get(), 1)
         .pattern("Y#Y")
         .pattern("RYR")
         .pattern("RWR")
         .define('Y', Items.YELLOW_DYE)
         .define('R', Items.RED_DYE)
         .define('W', Items.WHITE_DYE)
         .define('#', (ItemLike)MainItems.BLANK_PATTERN_SUPER.get())
         .unlockedBy(getHasName((ItemLike)MainItems.BLANK_PATTERN_SUPER.get()), has((ItemLike)MainItems.BLANK_PATTERN_SUPER.get()))
         .group("dragonminez")
         .save(pWriter);
      ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, (ItemLike)MainItems.PATTERN_MAJIN_BUU.get(), 1)
         .pattern("B#B")
         .pattern("MMM")
         .pattern("BYB")
         .define('B', Items.BLACK_DYE)
         .define('M', Items.MAGENTA_DYE)
         .define('Y', Items.YELLOW_DYE)
         .define('#', (ItemLike)MainItems.BLANK_PATTERN_Z.get())
         .unlockedBy(getHasName((ItemLike)MainItems.BLANK_PATTERN_Z.get()), has((ItemLike)MainItems.BLANK_PATTERN_Z.get()))
         .group("dragonminez")
         .save(pWriter);
      ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, (ItemLike)MainItems.PATTERN_GAMMA1.get(), 1)
         .pattern("R#R")
         .pattern("YYY")
         .pattern("BGB")
         .define('R', Items.RED_DYE)
         .define('Y', Items.YELLOW_DYE)
         .define('B', Items.BLACK_DYE)
         .define('G', Items.GRAY_DYE)
         .define('#', (ItemLike)MainItems.BLANK_PATTERN_SUPER.get())
         .unlockedBy(getHasName((ItemLike)MainItems.BLANK_PATTERN_SUPER.get()), has((ItemLike)MainItems.BLANK_PATTERN_SUPER.get()))
         .group("dragonminez")
         .save(pWriter);
      ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, (ItemLike)MainItems.PATTERN_GAMMA2.get(), 1)
         .pattern("C#C")
         .pattern("YYY")
         .pattern("BGB")
         .define('C', Items.CYAN_DYE)
         .define('Y', Items.YELLOW_DYE)
         .define('B', Items.BLACK_DYE)
         .define('G', Items.GRAY_DYE)
         .define('#', (ItemLike)MainItems.BLANK_PATTERN_SUPER.get())
         .unlockedBy(getHasName((ItemLike)MainItems.BLANK_PATTERN_SUPER.get()), has((ItemLike)MainItems.BLANK_PATTERN_SUPER.get()))
         .group("dragonminez")
         .save(pWriter);
      ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, (ItemLike)MainItems.PATTERN_A16.get(), 1)
         .pattern("O#O")
         .pattern("GBG")
         .pattern("GGG")
         .define('O', Items.ORANGE_DYE)
         .define('G', Items.GREEN_DYE)
         .define('B', Items.BLACK_DYE)
         .define('#', (ItemLike)MainItems.BLANK_PATTERN_Z.get())
         .unlockedBy(getHasName((ItemLike)MainItems.BLANK_PATTERN_Z.get()), has((ItemLike)MainItems.BLANK_PATTERN_Z.get()))
         .group("dragonminez")
         .save(pWriter);
      ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, (ItemLike)MainItems.PATTERN_A17.get(), 1)
         .pattern("B#B")
         .pattern("GGG")
         .pattern("OBO")
         .define('B', Items.BLACK_DYE)
         .define('G', Items.GREEN_DYE)
         .define('O', Items.ORANGE_DYE)
         .define('#', (ItemLike)MainItems.BLANK_PATTERN_Z.get())
         .unlockedBy(getHasName((ItemLike)MainItems.BLANK_PATTERN_Z.get()), has((ItemLike)MainItems.BLANK_PATTERN_Z.get()))
         .group("dragonminez")
         .save(pWriter);
      ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, (ItemLike)MainItems.PATTERN_A18.get(), 1)
         .pattern("Y#Y")
         .pattern("LBL")
         .pattern("BWB")
         .define('Y', Items.YELLOW_DYE)
         .define('L', Items.LIGHT_BLUE_DYE)
         .define('B', Items.BLACK_DYE)
         .define('W', Items.WHITE_DYE)
         .define('#', (ItemLike)MainItems.BLANK_PATTERN_Z.get())
         .unlockedBy(getHasName((ItemLike)MainItems.BLANK_PATTERN_Z.get()), has((ItemLike)MainItems.BLANK_PATTERN_Z.get()))
         .group("dragonminez")
         .save(pWriter);
      ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, (ItemLike)MainItems.PATTERN_A17_SUPER.get(), 1)
         .pattern("G#G")
         .pattern("BWB")
         .pattern("GBG")
         .define('G', Items.GREEN_DYE)
         .define('B', Items.BLACK_DYE)
         .define('W', Items.WHITE_DYE)
         .define('#', (ItemLike)MainItems.BLANK_PATTERN_SUPER.get())
         .unlockedBy(getHasName((ItemLike)MainItems.BLANK_PATTERN_SUPER.get()), has((ItemLike)MainItems.BLANK_PATTERN_SUPER.get()))
         .group("dragonminez")
         .save(pWriter);
      ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, (ItemLike)MainItems.PATTERN_A18_KAME.get(), 1)
         .pattern("Y#Y")
         .pattern("WBW")
         .pattern("LLL")
         .define('Y', Items.YELLOW_DYE)
         .define('W', Items.WHITE_DYE)
         .define('B', Items.BLACK_DYE)
         .define('L', Items.LIGHT_BLUE_DYE)
         .define('#', (ItemLike)MainItems.BLANK_PATTERN_Z.get())
         .unlockedBy(getHasName((ItemLike)MainItems.BLANK_PATTERN_Z.get()), has((ItemLike)MainItems.BLANK_PATTERN_Z.get()))
         .group("dragonminez")
         .save(pWriter);
      ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, (ItemLike)MainItems.PATTERN_A18_TOURNAMENT.get(), 1)
         .pattern("Y#Y")
         .pattern("BWB")
         .pattern("OLO")
         .define('Y', Items.YELLOW_DYE)
         .define('B', Items.BLACK_DYE)
         .define('W', Items.WHITE_DYE)
         .define('O', Items.ORANGE_DYE)
         .define('L', Items.LIGHT_BLUE_DYE)
         .define('#', (ItemLike)MainItems.BLANK_PATTERN_Z.get())
         .unlockedBy(getHasName((ItemLike)MainItems.BLANK_PATTERN_Z.get()), has((ItemLike)MainItems.BLANK_PATTERN_Z.get()))
         .group("dragonminez")
         .save(pWriter);
      ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, (ItemLike)MainItems.PATTERN_A18_CELL.get(), 1)
         .pattern("Y#Y")
         .pattern("LWL")
         .pattern("BBB")
         .define('Y', Items.YELLOW_DYE)
         .define('L', Items.LIGHT_BLUE_DYE)
         .define('W', Items.WHITE_DYE)
         .define('B', Items.BLACK_DYE)
         .define('#', (ItemLike)MainItems.BLANK_PATTERN_Z.get())
         .unlockedBy(getHasName((ItemLike)MainItems.BLANK_PATTERN_Z.get()), has((ItemLike)MainItems.BLANK_PATTERN_Z.get()))
         .group("dragonminez")
         .save(pWriter);
      ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, (ItemLike)MainItems.PATTERN_ORANGE_HIGH.get(), 1)
         .pattern("O#O")
         .pattern("WWW")
         .pattern("BBB")
         .define('O', Items.ORANGE_DYE)
         .define('W', Items.WHITE_DYE)
         .define('B', Items.BLACK_DYE)
         .define('#', (ItemLike)MainItems.BLANK_PATTERN_Z.get())
         .unlockedBy(getHasName((ItemLike)MainItems.BLANK_PATTERN_Z.get()), has((ItemLike)MainItems.BLANK_PATTERN_Z.get()))
         .group("dragonminez")
         .save(pWriter);
      ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, (ItemLike)MainItems.PATTERN_VIDEL.get(), 1)
         .pattern("W#W")
         .pattern("BPB")
         .pattern("LLL")
         .define('W', Items.WHITE_DYE)
         .define('B', Items.BLACK_DYE)
         .define('P', Items.PINK_DYE)
         .define('L', Items.LIGHT_BLUE_DYE)
         .define('#', (ItemLike)MainItems.BLANK_PATTERN_Z.get())
         .unlockedBy(getHasName((ItemLike)MainItems.BLANK_PATTERN_Z.get()), has((ItemLike)MainItems.BLANK_PATTERN_Z.get()))
         .group("dragonminez")
         .save(pWriter);
      ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, (ItemLike)MainItems.PATTERN_GINE.get(), 1)
         .pattern("R#R")
         .pattern("WBW")
         .pattern("BBB")
         .define('R', Items.RED_DYE)
         .define('W', Items.WHITE_DYE)
         .define('B', Items.BLACK_DYE)
         .define('#', (ItemLike)MainItems.BLANK_PATTERN_Z.get())
         .unlockedBy(getHasName((ItemLike)MainItems.BLANK_PATTERN_Z.get()), has((ItemLike)MainItems.BLANK_PATTERN_Z.get()))
         .group("dragonminez")
         .save(pWriter);
      ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, (ItemLike)MainItems.PATTERN_AGE1000.get(), 1)
         .pattern("L#L")
         .pattern("BWB")
         .pattern("GGG")
         .define('L', Items.LIGHT_BLUE_DYE)
         .define('B', Items.BLACK_DYE)
         .define('W', Items.WHITE_DYE)
         .define('G', Items.GRAY_DYE)
         .define('#', (ItemLike)MainItems.BLANK_PATTERN_Z.get())
         .unlockedBy(getHasName((ItemLike)MainItems.BLANK_PATTERN_Z.get()), has((ItemLike)MainItems.BLANK_PATTERN_Z.get()))
         .group("dragonminez")
         .save(pWriter);
      ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, (ItemLike)MainItems.PATTERN_VEGETA_GT.get(), 1)
         .pattern("B#B")
         .pattern("YWY")
         .pattern("BLB")
         .define('B', Items.BLACK_DYE)
         .define('Y', Items.YELLOW_DYE)
         .define('W', Items.WHITE_DYE)
         .define('L', Items.LIGHT_BLUE_DYE)
         .define('#', (ItemLike)MainItems.BLANK_PATTERN_Z.get())
         .unlockedBy(getHasName((ItemLike)MainItems.BLANK_PATTERN_Z.get()), has((ItemLike)MainItems.BLANK_PATTERN_Z.get()))
         .group("dragonminez")
         .save(pWriter);
      ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, (ItemLike)MainItems.PATTERN_BEERUS.get(), 1)
         .pattern("P#P")
         .pattern("BPB")
         .pattern("LWL")
         .define('P', Items.PURPLE_DYE)
         .define('B', Items.BLUE_DYE)
         .define('L', Items.LIGHT_BLUE_DYE)
         .define('W', Items.WHITE_DYE)
         .define('#', (ItemLike)MainItems.BLANK_PATTERN_SUPER.get())
         .unlockedBy(getHasName((ItemLike)MainItems.BLANK_PATTERN_SUPER.get()), has((ItemLike)MainItems.BLANK_PATTERN_SUPER.get()))
         .group("dragonminez")
         .save(pWriter);
      ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, (ItemLike)MainItems.PATTERN_WHIS.get(), 1)
         .pattern("L#L")
         .pattern("WUW")
         .pattern("AAA")
         .define('L', Items.LIGHT_BLUE_DYE)
         .define('W', Items.WHITE_DYE)
         .define('U', Items.BLUE_DYE)
         .define('A', Items.GRAY_DYE)
         .define('#', (ItemLike)MainItems.BLANK_PATTERN_SUPER.get())
         .unlockedBy(getHasName((ItemLike)MainItems.BLANK_PATTERN_SUPER.get()), has((ItemLike)MainItems.BLANK_PATTERN_SUPER.get()))
         .group("dragonminez")
         .save(pWriter);
      ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, (ItemLike)MainItems.PATTERN_GOKU_WHIS.get(), 1)
         .pattern("O#O")
         .pattern("OWO")
         .pattern("BBB")
         .define('O', Items.ORANGE_DYE)
         .define('W', Items.WHITE_DYE)
         .define('B', Items.BLUE_DYE)
         .define('#', (ItemLike)MainItems.BLANK_PATTERN_SUPER.get())
         .unlockedBy(getHasName((ItemLike)MainItems.BLANK_PATTERN_SUPER.get()), has((ItemLike)MainItems.BLANK_PATTERN_SUPER.get()))
         .group("dragonminez")
         .save(pWriter);
      ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, (ItemLike)MainItems.PATTERN_VEGETA_WHIS.get(), 1)
         .pattern("A#A")
         .pattern("AWA")
         .pattern("YYY")
         .define('A', Items.GRAY_DYE)
         .define('W', Items.WHITE_DYE)
         .define('Y', Items.YELLOW_DYE)
         .define('#', (ItemLike)MainItems.BLANK_PATTERN_SUPER.get())
         .unlockedBy(getHasName((ItemLike)MainItems.BLANK_PATTERN_SUPER.get()), has((ItemLike)MainItems.BLANK_PATTERN_SUPER.get()))
         .group("dragonminez")
         .save(pWriter);
      ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, (ItemLike)MainItems.PATTERN_KEFLA.get(), 1)
         .pattern("G#G")
         .pattern("GYG")
         .pattern("BBB")
         .define('G', Items.GREEN_DYE)
         .define('Y', Items.YELLOW_DYE)
         .define('B', Items.BLACK_DYE)
         .define('#', (ItemLike)MainItems.BLANK_PATTERN_SUPER.get())
         .unlockedBy(getHasName((ItemLike)MainItems.BLANK_PATTERN_SUPER.get()), has((ItemLike)MainItems.BLANK_PATTERN_SUPER.get()))
         .group("dragonminez")
         .save(pWriter);
      ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, (ItemLike)MainItems.PATTERN_KALE.get(), 1)
         .pattern("G#G")
         .pattern("BGB")
         .pattern("RRR")
         .define('G', Items.GREEN_DYE)
         .define('B', Items.BLACK_DYE)
         .define('R', Items.RED_DYE)
         .define('#', (ItemLike)MainItems.BLANK_PATTERN_SUPER.get())
         .unlockedBy(getHasName((ItemLike)MainItems.BLANK_PATTERN_SUPER.get()), has((ItemLike)MainItems.BLANK_PATTERN_SUPER.get()))
         .group("dragonminez")
         .save(pWriter);
      ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, (ItemLike)MainItems.PATTERN_CAULIFLA.get(), 1)
         .pattern("B#B")
         .pattern("GMG")
         .pattern("RRR")
         .define('B', Items.BLACK_DYE)
         .define('G', Items.GREEN_DYE)
         .define('M', Items.MAGENTA_DYE)
         .define('R', Items.RED_DYE)
         .define('#', (ItemLike)MainItems.BLANK_PATTERN_SUPER.get())
         .unlockedBy(getHasName((ItemLike)MainItems.BLANK_PATTERN_SUPER.get()), has((ItemLike)MainItems.BLANK_PATTERN_SUPER.get()))
         .group("dragonminez")
         .save(pWriter);
      ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, (ItemLike)MainItems.PATTERN_MAJIN21.get(), 1)
         .pattern("P#P")
         .pattern("RWR")
         .pattern("BPB")
         .define('P', Items.PINK_DYE)
         .define('R', Items.RED_DYE)
         .define('W', Items.WHITE_DYE)
         .define('B', Items.BLACK_DYE)
         .define('#', (ItemLike)MainItems.BLANK_PATTERN_SUPER.get())
         .unlockedBy(getHasName((ItemLike)MainItems.BLANK_PATTERN_SUPER.get()), has((ItemLike)MainItems.BLANK_PATTERN_SUPER.get()))
         .group("dragonminez")
         .save(pWriter);
      ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, (ItemLike)MainItems.PATTERN_GRANOLA.get(), 1)
         .pattern("O#O")
         .pattern("RBR")
         .pattern("WWW")
         .define('O', Items.ORANGE_DYE)
         .define('R', Items.RED_DYE)
         .define('B', Items.BLACK_DYE)
         .define('W', Items.WHITE_DYE)
         .define('#', (ItemLike)MainItems.BLANK_PATTERN_SUPER.get())
         .unlockedBy(getHasName((ItemLike)MainItems.BLANK_PATTERN_SUPER.get()), has((ItemLike)MainItems.BLANK_PATTERN_SUPER.get()))
         .group("dragonminez")
         .save(pWriter);
      new DMZKikonoRecipeProvider(pWriter).generate();
   }

   protected static void oreSmelting(
      @NotNull RecipeOutput pRecipeOutputConsumer,
      List<ItemLike> pIngredients,
      @NotNull RecipeCategory pCategory,
      @NotNull ItemLike pResult,
      float pExperience,
      int pCookingTIme,
      @NotNull String pGroup
   ) {
      oreCooking(
         pRecipeOutputConsumer,
         RecipeSerializer.SMELTING_RECIPE,
         SmeltingRecipe::new,
         pIngredients,
         pCategory,
         pResult,
         pExperience,
         pCookingTIme,
         pGroup,
         "_from_smelting"
      );
   }

   protected static void oreBlasting(
      @NotNull RecipeOutput pRecipeOutputConsumer,
      List<ItemLike> pIngredients,
      @NotNull RecipeCategory pCategory,
      @NotNull ItemLike pResult,
      float pExperience,
      int pCookingTime,
      @NotNull String pGroup
   ) {
      oreCooking(
         pRecipeOutputConsumer,
         RecipeSerializer.BLASTING_RECIPE,
         BlastingRecipe::new,
         pIngredients,
         pCategory,
         pResult,
         pExperience,
         pCookingTime,
         pGroup,
         "_from_blasting"
      );
   }

   protected static <T extends AbstractCookingRecipe> void oreCooking(
      @NotNull RecipeOutput pRecipeOutputConsumer,
      @NotNull RecipeSerializer<T> pCookingSerializer,
      @NotNull Factory<T> recipeFactory,
      List<ItemLike> pIngredients,
      @NotNull RecipeCategory pCategory,
      @NotNull ItemLike pResult,
      float pExperience,
      int pCookingTime,
      @NotNull String pGroup,
      String pRecipeName
   ) {
      for (ItemLike itemlike : pIngredients) {
         SimpleCookingRecipeBuilder.generic(
               Ingredient.of(new ItemLike[]{itemlike}), pCategory, pResult, pExperience, pCookingTime, pCookingSerializer, recipeFactory
            )
            .group(pGroup)
            .unlockedBy(getHasName(itemlike), has(itemlike))
            .save(pRecipeOutputConsumer, "dragonminez:" + getItemName(pResult) + pRecipeName + "_" + getItemName(itemlike));
      }
   }

   private void geteCapsule(RecipeOutput w, ItemLike base, ItemLike result, String id) {
      ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, result)
         .requires(base)
         .requires((ItemLike)MainItems.GETE_INGOT.get())
         .unlockedBy(getHasName((ItemLike)MainItems.GETE_INGOT.get()), has((ItemLike)MainItems.GETE_INGOT.get()))
         .group("dragonminez")
         .save(w, ResourceLocation.fromNamespaceAndPath("dragonminez", id));
   }
}
