package com.dragonminez.common.init;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.Structure;

public class MainTags {
   public static class Biomes {
      public static final TagKey<Biome> IS_NAMEK = create("is_namekworld");
      public static final TagKey<Biome> IS_SACREDLAND = create("is_sacredland");
      public static final TagKey<Biome> IS_HTC = create("is_htc");
      public static final TagKey<Biome> IS_OTHERWORLD = create("is_otherworld");
      public static final TagKey<Biome> HAS_DINOSAURS = create("has_dinosaurs");
      public static final TagKey<Biome> HAS_SABERTOOTH = create("has_sabertooth");
      public static final TagKey<Biome> HAS_ROBOTS = create("has_robots");
      public static final TagKey<Biome> HAS_SAIBAMANS = create("has_saibamans");
      public static final TagKey<Biome> IS_ROCKYBIOME = create("is_rockybiome");
      public static final TagKey<Biome> IS_SACREDKAI = create("is_sacredkai");
      public static final TagKey<Biome> IS_LAND = create("is_land");
      public static final TagKey<Biome> IS_MOUNTAINLIKE = create("is_mountainlike");
      public static final TagKey<Biome> IS_PLAINSLIKE = create("is_plainslike");
      public static final TagKey<Biome> IS_DESERTLIKE = create("is_desertlike");

      private static TagKey<Biome> create(String name) {
         return TagKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath("dragonminez", name));
      }
   }

   public static class Blocks {
      public static final TagKey<Block> NAMEK_ALOG = create("namek_alog");
      public static final TagKey<Block> NAMEK_SLOG = create("namek_slog");
      public static final TagKey<Block> NAMEKDEEPSLATE_REPLACEABLES = create("namek_deepslate_ore_replaceables");
      public static final TagKey<Block> NAMEKSTONE_REPLACEABLES = create("namek_stone_ore_replaceables");
      public static final TagKey<Block> NEEDS_GETE_TOOL = create("needs_gete_tool");

      private static TagKey<Block> create(String name) {
         return TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("dragonminez", name));
      }
   }

   public static class EntityTypes {
      public static final TagKey<EntityType<?>> FRIEZA_SOLDIERS = create("frieza_soldiers");
      public static final TagKey<EntityType<?>> SAIBAMEN = create("saibamen");
      public static final TagKey<EntityType<?>> RED_RIBBON_ROBOTS = create("red_ribbon_robots");

      private static TagKey<EntityType<?>> create(String name) {
         return TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath("dragonminez", name));
      }
   }

   public static class Items {
      public static final TagKey<Item> NAMEK_ALOG = create("namek_alog");
      public static final TagKey<Item> NAMEK_SLOG = create("namek_slog");
      public static final TagKey<Item> WEIGHTED_ITEMS = create("weighted_items");

      private static TagKey<Item> create(String name) {
         return TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("dragonminez", name));
      }
   }

   public static class Structures {
      public static final TagKey<Structure> KI_GRIEFING_PROTECTED = create("ki_griefing_protected");

      private static TagKey<Structure> create(String name) {
         return TagKey.create(Registries.STRUCTURE, ResourceLocation.fromNamespaceAndPath("dragonminez", name));
      }
   }
}
