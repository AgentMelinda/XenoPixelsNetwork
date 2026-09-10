package com.dragonminez.compat.registries;

import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.Fluid;

public final class ForgeRegistries {
   public static final Registry<Item> ITEMS = BuiltInRegistries.ITEM;
   public static final Registry<Block> BLOCKS = BuiltInRegistries.BLOCK;
   public static final Registry<EntityType<?>> ENTITY_TYPES = BuiltInRegistries.ENTITY_TYPE;
   public static final Registry<Attribute> ATTRIBUTES = BuiltInRegistries.ATTRIBUTE;
   public static final Registry<MobEffect> MOB_EFFECTS = BuiltInRegistries.MOB_EFFECT;
   public static final Registry<SoundEvent> SOUND_EVENTS = BuiltInRegistries.SOUND_EVENT;
   public static final Registry<Fluid> FLUIDS = BuiltInRegistries.FLUID;
   public static final Registry<MenuType<?>> MENU_TYPES = BuiltInRegistries.MENU;
   public static final Registry<ParticleType<?>> PARTICLE_TYPES = BuiltInRegistries.PARTICLE_TYPE;
   public static final Registry<BlockEntityType<?>> BLOCK_ENTITY_TYPES = BuiltInRegistries.BLOCK_ENTITY_TYPE;
   public static final Registry<RecipeType<?>> RECIPE_TYPES = BuiltInRegistries.RECIPE_TYPE;
   public static final Registry<RecipeSerializer<?>> RECIPE_SERIALIZERS = BuiltInRegistries.RECIPE_SERIALIZER;
   public static final Registry<VillagerProfession> VILLAGER_PROFESSIONS = BuiltInRegistries.VILLAGER_PROFESSION;
   public static final Registry<PoiType> POI_TYPES = BuiltInRegistries.POINT_OF_INTEREST_TYPE;
   public static final ResourceKey<Registry<Enchantment>> ENCHANTMENTS = Registries.ENCHANTMENT;

   private ForgeRegistries() {
   }
}
