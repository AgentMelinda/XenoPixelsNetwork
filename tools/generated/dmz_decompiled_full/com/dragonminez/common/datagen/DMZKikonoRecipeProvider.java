package com.dragonminez.common.datagen;

import com.dragonminez.common.datagen.builder.KikonoRecipeBuilder;
import com.dragonminez.common.init.MainItems;
import java.util.Map;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ArmorItem.Type;
import net.minecraft.world.level.ItemLike;
import net.neoforged.neoforge.registries.DeferredHolder;

public class DMZKikonoRecipeProvider {
   private final RecipeOutput consumer;

   public DMZKikonoRecipeProvider(RecipeOutput consumer) {
      this.consumer = consumer;
   }

   protected void generate() {
      this.buildArmorNoHelmetSet("goku_kid", MainItems.GOKU_KID_ARMOR, (Item)MainItems.PATTERN_GOKU_KID.get());
      this.buildArmorNoHelmetSet("goku", MainItems.GOKU_ARMOR, (Item)MainItems.PATTERN_GOKU1.get());
      this.buildArmorNoHelmetSet("goku_super", MainItems.GOKU_SUPER_ARMOR, (Item)MainItems.PATTERN_GOKU_SUPER.get());
      this.buildArmorNoHelmetSet("goku_gt", MainItems.GOKU_GT_ARMOR, (Item)MainItems.PATTERN_GOKU_GT.get());
      this.buildArmorNoHelmetSet("yardrat", MainItems.YARDRAT_ARMOR, (Item)MainItems.PATTERN_YARDRAT.get());
      this.buildArmorNoHelmetSet("goten", MainItems.GOTEN_ARMOR, (Item)MainItems.PATTERN_GOTEN.get());
      this.buildArmorNoHelmetSet("goten_super", MainItems.GOTEN_SUPER_ARMOR, (Item)MainItems.PATTERN_GOTEN_SUPER.get());
      this.buildArmorNoHelmetSet("gohan_super", MainItems.GOHAN_SUPER_ARMOR, (Item)MainItems.PATTERN_GOHAN_SUPER.get());
      this.buildFullArmorSet("great_saiyaman", MainItems.GREAT_SAIYAMAN_ARMOR, (Item)MainItems.PATTERN_GREAT_SAIYAMAN.get());
      this.buildArmorNoHelmetSet("future_gohan", MainItems.FUTURE_GOHAN_ARMOR, (Item)MainItems.PATTERN_FUTURE_GOHAN.get());
      this.buildArmorNoHelmetSet("vegeta_saiyan", MainItems.VEGETA_SAIYAN_ARMOR, (Item)MainItems.PATTERN_VEGETA1.get());
      this.buildArmorNoHelmetSet("vegeta_namek", MainItems.VEGETA_NAMEK_ARMOR, (Item)MainItems.PATTERN_VEGETA2.get());
      this.buildArmorNoHelmetSet("vegeta_z", MainItems.VEGETA_Z_ARMOR, (Item)MainItems.PATTERN_VEGETA_Z.get());
      this.buildArmorNoHelmetSet("vegeta_buu", MainItems.VEGETA_BUU_ARMOR, (Item)MainItems.PATTERN_VEGETA_BUU.get());
      this.buildArmorNoHelmetSet("vegeta_super", MainItems.VEGETA_SUPER_ARMOR, (Item)MainItems.PATTERN_VEGETA_SUPER.get());
      this.buildArmorNoHelmetSet("vegetto", MainItems.VEGETTO_ARMOR, (Item)MainItems.PATTERN_VEGETTO.get());
      this.buildArmorNoHelmetSet("gogeta", MainItems.GOGETA_ARMOR, (Item)MainItems.PATTERN_GOGETA.get());
      this.buildFullArmorSet("piccolo", MainItems.PICCOLO_ARMOR, (Item)MainItems.PATTERN_PICCOLO.get());
      this.buildArmorNoHelmetSet("demon_gi_blue", MainItems.DEMON_GI_BLUE_ARMOR, (Item)MainItems.PATTERN_GOHAN1.get());
      this.buildArmorNoHelmetSet("bardock_dbz", MainItems.BARDOCK_DBZ_ARMOR, (Item)MainItems.PATTERN_BARDOCK1.get());
      this.buildArmorNoHelmetSet("bardock_super", MainItems.BARDOCK_SUPER_ARMOR, (Item)MainItems.PATTERN_BARDOCK2.get());
      this.buildArmorNoHelmetSet("turles", MainItems.TURLES_ARMOR, (Item)MainItems.PATTERN_TURLES.get());
      this.buildArmorNoHelmetSet("tien", MainItems.TIEN_ARMOR, (Item)MainItems.PATTERN_TIEN.get());
      this.buildArmorNoHelmetSet("trunks_z", MainItems.TRUNKS_Z_ARMOR, (Item)MainItems.PATTERN_TRUNKS_Z.get());
      this.buildArmorNoHelmetSet("trunks_super", MainItems.TRUNKS_SUPER_ARMOR, (Item)MainItems.PATTERN_TRUNKS_SUPER.get());
      this.buildArmorNoHelmetSet("trunks_kid", MainItems.TRUNKS_KID_ARMOR, (Item)MainItems.PATTERN_TRUNKS_KID.get());
      this.buildArmorNoHelmetSet("broly_z", MainItems.BROLY_Z_ARMOR, (Item)MainItems.PATTERN_BROLY_Z.get());
      this.buildArmorNoHelmetSet("broly_super", MainItems.BROLY_SUPER_ARMOR, (Item)MainItems.PATTERN_BROLY_SUPER.get());
      this.buildArmorNoHelmetSet("shin", MainItems.SHIN_ARMOR, (Item)MainItems.PATTERN_SHIN.get());
      this.buildArmorNoHelmetSet("blackgoku", MainItems.BLACKGOKU_ARMOR, (Item)MainItems.PATTERN_BLACK.get());
      this.buildArmorNoHelmetSet("zamasu", MainItems.ZAMASU_ARMOR, (Item)MainItems.PATTERN_ZAMASU.get());
      this.buildArmorNoHelmetSet("fusion_zamasu", MainItems.FUSION_ZAMASU_ARMOR, (Item)MainItems.PATTERN_FUSION_ZAMASU.get());
      this.buildArmorNoHelmetSet("hit", MainItems.HIT_ARMOR, (Item)MainItems.PATTERN_HIT.get());
      this.buildArmorNoHelmetSet("gas", MainItems.GAS_ARMOR, (Item)MainItems.PATTERN_GAS.get());
      this.buildArmorNoHelmetSet("majin_buu", MainItems.MAJIN_BUU_ARMOR, (Item)MainItems.PATTERN_MAJIN_BUU.get());
      this.buildArmorNoHelmetSet("gamma1", MainItems.GAMMA1_ARMOR, (Item)MainItems.PATTERN_GAMMA1.get());
      this.buildArmorNoHelmetSet("gamma2", MainItems.GAMMA2_ARMOR, (Item)MainItems.PATTERN_GAMMA2.get());
      this.buildArmorNoHelmetSet("pride_troops", MainItems.PRIDE_TROOPS_ARMOR, (Item)MainItems.PATTERN_PRIDE_TROOPS.get());
      this.buildArmorNoHelmetSet("a16", MainItems.A16_ARMOR, (Item)MainItems.PATTERN_A16.get());
      this.buildArmorNoHelmetSet("a17", MainItems.A17_ARMOR, (Item)MainItems.PATTERN_A17.get());
      this.buildArmorNoHelmetSet("a18", MainItems.A18_ARMOR, (Item)MainItems.PATTERN_A18.get());
      this.buildArmorNoHelmetSet("orange_high", MainItems.ORANGE_HIGH_ARMOR, (Item)MainItems.PATTERN_ORANGE_HIGH.get());
      this.buildArmorNoHelmetSet("granola", MainItems.GRANOLA_ARMOR, (Item)MainItems.PATTERN_GRANOLA.get());
      this.buildArmorNoHelmetSet("age1000", MainItems.AGE1000_ARMOR, (Item)MainItems.PATTERN_AGE1000.get());
      this.buildArmorNoHelmetSet("gine", MainItems.GINE_ARMOR, (Item)MainItems.PATTERN_GINE.get());
      this.buildArmorNoHelmetSet("kale", MainItems.KALE_ARMOR, (Item)MainItems.PATTERN_KALE.get());
      this.buildArmorNoHelmetSet("caulifla", MainItems.CAULIFLA_ARMOR, (Item)MainItems.PATTERN_CAULIFLA.get());
      this.buildArmorNoHelmetSet("a17_super", MainItems.A17_SUPER_ARMOR, (Item)MainItems.PATTERN_A17_SUPER.get());
      this.buildArmorNoHelmetSet("a18_kame", MainItems.A18_KAME_ARMOR, (Item)MainItems.PATTERN_A18_KAME.get());
      this.buildArmorNoHelmetSet("a18_tournament", MainItems.A18_TOURNAMENT_ARMOR, (Item)MainItems.PATTERN_A18_TOURNAMENT.get());
      this.buildArmorNoHelmetSet("a18_cell", MainItems.A18_CELL_ARMOR, (Item)MainItems.PATTERN_A18_CELL.get());
      this.buildArmorNoHelmetSet("beerus", MainItems.BEERUS_ARMOR, (Item)MainItems.PATTERN_BEERUS.get());
      this.buildArmorNoHelmetSet("goku_whis", MainItems.GOKU_WHIS_ARMOR, (Item)MainItems.PATTERN_GOKU_WHIS.get());
      this.buildArmorNoHelmetSet("kefla", MainItems.KEFLA_ARMOR, (Item)MainItems.PATTERN_KEFLA.get());
      this.buildArmorNoHelmetSet("majin21", MainItems.MAJIN21_ARMOR, (Item)MainItems.PATTERN_MAJIN21.get());
      this.buildArmorNoHelmetSet("vegeta_whis", MainItems.VEGETA_WHIS_ARMOR, (Item)MainItems.PATTERN_VEGETA_WHIS.get());
      this.buildArmorNoHelmetSet("vegeta_gt", MainItems.VEGETA_GT_ARMOR, (Item)MainItems.PATTERN_VEGETA_GT.get());
      this.buildArmorNoHelmetSet("videl", MainItems.VIDEL_ARMOR, (Item)MainItems.PATTERN_VIDEL.get());
      this.buildArmorNoHelmetSet("whis", MainItems.WHIS_ARMOR, (Item)MainItems.PATTERN_WHIS.get());
      this.buildToolSetNoSword(
         "gete",
         (Item)MainItems.GETE_PICKAXE.get(),
         (Item)MainItems.GETE_AXE.get(),
         (Item)MainItems.GETE_SHOVEL.get(),
         (Item)MainItems.GETE_HOE.get(),
         (Item)MainItems.PATTERN_GETE.get(),
         (Item)MainItems.GETE_INGOT.get(),
         (Item)MainItems.KIKONO_STICK.get()
      );
   }

   protected void buildFullArmorSet(String name, Map<Type, DeferredHolder<Item, ? extends Item>> armorSet, Item pattern) {
      this.buildHelmetRecipes(name, (Item)armorSet.get(Type.HELMET).get(), pattern);
      this.buildChestplateRecipes(name, (Item)armorSet.get(Type.CHESTPLATE).get(), pattern);
      this.buildLeggingsRecipes(name, (Item)armorSet.get(Type.LEGGINGS).get(), pattern);
      this.buildBootsRecipes(name, (Item)armorSet.get(Type.BOOTS).get(), pattern);
   }

   protected void buildArmorNoHelmetSet(String name, Map<Type, DeferredHolder<Item, ? extends Item>> armorSet, Item pattern) {
      this.buildChestplateRecipes(name, (Item)armorSet.get(Type.CHESTPLATE).get(), pattern);
      this.buildLeggingsRecipes(name, (Item)armorSet.get(Type.LEGGINGS).get(), pattern);
      this.buildBootsRecipes(name, (Item)armorSet.get(Type.BOOTS).get(), pattern);
   }

   protected void buildToolSetNoSword(String name, Item pickaxe, Item axe, Item shovel, Item hoe, Item pattern, Item material, Item stick) {
      this.buildPickaxeRecipes(name, pickaxe, pattern, material, stick);
      this.buildAxeRecipes(name, axe, pattern, material, stick);
      this.buildShovelRecipes(name, shovel, pattern, material, stick);
      this.buildHoeRecipes(name, hoe, pattern, material, stick);
   }

   protected void buildFullToolSet(String name, Item pickaxe, Item axe, Item sword, Item shovel, Item hoe, Item scythe, Item pattern, Item material, Item stick) {
      this.buildPickaxeRecipes(name, pickaxe, pattern, material, stick);
      this.buildAxeRecipes(name, axe, pattern, material, stick);
      this.buildSwordRecipes(name, sword, pattern, material, stick);
      this.buildShovelRecipes(name, shovel, pattern, material, stick);
      this.buildHoeRecipes(name, hoe, pattern, material, stick);
      this.buildScytheRecipes(name, scythe, pattern, material, stick);
   }

   protected void buildHelmetRecipes(String name, Item output, Item pattern) {
      KikonoRecipeBuilder.kikonize(output)
         .pattern(pattern)
         .template(Items.IRON_HELMET)
         .input((ItemLike)MainItems.KIKONO_CLOTH.get())
         .input((ItemLike)MainItems.KIKONO_STRING.get())
         .input((ItemLike)MainItems.KIKONO_CLOTH.get())
         .input((ItemLike)MainItems.KIKONO_STRING.get())
         .input(Items.AIR)
         .input((ItemLike)MainItems.KIKONO_STRING.get())
         .input(Items.AIR)
         .input(Items.AIR)
         .input(Items.AIR)
         .time(200)
         .energy(1000)
         .save(this.consumer, ResourceLocation.fromNamespaceAndPath("dragonminez", name + "_armor_helmet"));
   }

   protected void buildChestplateRecipes(String name, Item output, Item pattern) {
      KikonoRecipeBuilder.kikonize(output)
         .pattern(pattern)
         .template(Items.IRON_CHESTPLATE)
         .input((ItemLike)MainItems.KIKONO_CLOTH.get())
         .input(Items.AIR)
         .input((ItemLike)MainItems.KIKONO_CLOTH.get())
         .input((ItemLike)MainItems.KIKONO_STRING.get())
         .input((ItemLike)MainItems.KIKONO_STRING.get())
         .input((ItemLike)MainItems.KIKONO_STRING.get())
         .input((ItemLike)MainItems.KIKONO_CLOTH.get())
         .input((ItemLike)MainItems.KIKONO_CLOTH.get())
         .input((ItemLike)MainItems.KIKONO_CLOTH.get())
         .time(200)
         .energy(1000)
         .save(this.consumer, ResourceLocation.fromNamespaceAndPath("dragonminez", name + "_armor_chestplate"));
   }

   protected void buildLeggingsRecipes(String name, Item output, Item pattern) {
      KikonoRecipeBuilder.kikonize(output)
         .pattern(pattern)
         .template(Items.IRON_LEGGINGS)
         .input((ItemLike)MainItems.KIKONO_CLOTH.get())
         .input((ItemLike)MainItems.KIKONO_CLOTH.get())
         .input((ItemLike)MainItems.KIKONO_CLOTH.get())
         .input((ItemLike)MainItems.KIKONO_CLOTH.get())
         .input(Items.AIR)
         .input((ItemLike)MainItems.KIKONO_CLOTH.get())
         .input((ItemLike)MainItems.KIKONO_STRING.get())
         .input(Items.AIR)
         .input((ItemLike)MainItems.KIKONO_STRING.get())
         .time(200)
         .energy(1000)
         .save(this.consumer, ResourceLocation.fromNamespaceAndPath("dragonminez", name + "_armor_leggings"));
   }

   protected void buildBootsRecipes(String name, Item output, Item pattern) {
      KikonoRecipeBuilder.kikonize(output)
         .pattern(pattern)
         .template(Items.IRON_BOOTS)
         .input(Items.AIR)
         .input(Items.AIR)
         .input(Items.AIR)
         .input((ItemLike)MainItems.KIKONO_CLOTH.get())
         .input(Items.AIR)
         .input((ItemLike)MainItems.KIKONO_CLOTH.get())
         .input((ItemLike)MainItems.KIKONO_STRING.get())
         .input(Items.AIR)
         .input((ItemLike)MainItems.KIKONO_STRING.get())
         .time(200)
         .energy(1000)
         .save(this.consumer, ResourceLocation.fromNamespaceAndPath("dragonminez", name + "_armor_boots"));
   }

   protected void buildPickaxeRecipes(String name, Item output, Item pattern, Item material, Item stick) {
      KikonoRecipeBuilder.kikonize(output)
         .pattern(pattern)
         .template(Items.IRON_PICKAXE)
         .input(material)
         .input(material)
         .input(material)
         .input(Items.AIR)
         .input(stick)
         .input(Items.AIR)
         .input(Items.AIR)
         .input(stick)
         .input(Items.AIR)
         .time(100)
         .energy(1000)
         .save(this.consumer, ResourceLocation.fromNamespaceAndPath("dragonminez", name + "_pickaxe"));
   }

   protected void buildAxeRecipes(String name, Item output, Item pattern, Item material, Item stick) {
      KikonoRecipeBuilder.kikonize(output)
         .pattern(pattern)
         .template(Items.IRON_AXE)
         .input(material)
         .input(material)
         .input(Items.AIR)
         .input(material)
         .input(stick)
         .input(Items.AIR)
         .input(Items.AIR)
         .input(stick)
         .input(Items.AIR)
         .time(100)
         .energy(1000)
         .save(this.consumer, ResourceLocation.fromNamespaceAndPath("dragonminez", name + "_axe"));
   }

   protected void buildSwordRecipes(String name, Item output, Item pattern, Item material, Item stick) {
      KikonoRecipeBuilder.kikonize(output)
         .pattern(pattern)
         .template(Items.IRON_SWORD)
         .input(Items.AIR)
         .input(material)
         .input(Items.AIR)
         .input(Items.AIR)
         .input(material)
         .input(Items.AIR)
         .input(Items.AIR)
         .input(stick)
         .input(Items.AIR)
         .time(100)
         .energy(1000)
         .save(this.consumer, ResourceLocation.fromNamespaceAndPath("dragonminez", name + "_sword"));
   }

   protected void buildShovelRecipes(String name, Item output, Item pattern, Item material, Item stick) {
      KikonoRecipeBuilder.kikonize(output)
         .pattern(pattern)
         .template(Items.IRON_SHOVEL)
         .input(Items.AIR)
         .input(material)
         .input(Items.AIR)
         .input(Items.AIR)
         .input(stick)
         .input(Items.AIR)
         .input(Items.AIR)
         .input(stick)
         .input(Items.AIR)
         .time(100)
         .energy(1000)
         .save(this.consumer, ResourceLocation.fromNamespaceAndPath("dragonminez", name + "_shovel"));
   }

   protected void buildHoeRecipes(String name, Item output, Item pattern, Item material, Item stick) {
      KikonoRecipeBuilder.kikonize(output)
         .pattern(pattern)
         .template(Items.IRON_HOE)
         .input(material)
         .input(material)
         .input(Items.AIR)
         .input(Items.AIR)
         .input(stick)
         .input(Items.AIR)
         .input(Items.AIR)
         .input(stick)
         .input(Items.AIR)
         .time(100)
         .energy(1000)
         .save(this.consumer, ResourceLocation.fromNamespaceAndPath("dragonminez", name + "_hoe"));
   }

   protected void buildScytheRecipes(String name, Item output, Item pattern, Item material, Item stick) {
      KikonoRecipeBuilder.kikonize(output)
         .pattern(pattern)
         .template(Items.IRON_SWORD)
         .input(Items.AIR)
         .input(material)
         .input(material)
         .input(Items.AIR)
         .input(stick)
         .input(material)
         .input(stick)
         .input(Items.AIR)
         .input(Items.AIR)
         .time(100)
         .energy(1000)
         .save(this.consumer, ResourceLocation.fromNamespaceAndPath("dragonminez", name + "_scythe"));
   }
}
