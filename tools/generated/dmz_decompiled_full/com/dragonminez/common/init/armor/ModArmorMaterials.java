package com.dragonminez.common.init.armor;

import com.dragonminez.common.init.MainItems;
import java.util.EnumMap;
import java.util.List;
import net.minecraft.Util;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ArmorItem.Type;
import net.minecraft.world.item.ArmorMaterial.Layer;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.ItemLike;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModArmorMaterials {
   public static final DeferredRegister<ArmorMaterial> ARMOR_MATERIALS = DeferredRegister.create(Registries.ARMOR_MATERIAL, "dragonminez");
   public static final DeferredHolder<ArmorMaterial, ArmorMaterial> BASIC = ARMOR_MATERIALS.register(
      "basic",
      () -> {
         EnumMap<Type, Integer> defense = (EnumMap<Type, Integer>)Util.make(new EnumMap(Type.class), map -> {
            map.put(Type.BOOTS, 1);
            map.put(Type.LEGGINGS, 2);
            map.put(Type.CHESTPLATE, 3);
            map.put(Type.HELMET, 1);
            map.put(Type.BODY, 3);
         });
         return new ArmorMaterial(
            defense,
            10,
            SoundEvents.ARMOR_EQUIP_IRON,
            () -> Ingredient.of(new ItemLike[]{Items.IRON_INGOT}),
            List.of(new Layer(ResourceLocation.fromNamespaceAndPath("dragonminez", "basic"))),
            0.0F,
            0.0F
         );
      }
   );
   public static final DeferredHolder<ArmorMaterial, ArmorMaterial> KIKONO = ARMOR_MATERIALS.register(
      "kikono",
      () -> {
         EnumMap<Type, Integer> defense = (EnumMap<Type, Integer>)Util.make(new EnumMap(Type.class), map -> {
            map.put(Type.BOOTS, 16);
            map.put(Type.LEGGINGS, 26);
            map.put(Type.CHESTPLATE, 35);
            map.put(Type.HELMET, 2);
            map.put(Type.BODY, 35);
         });
         return new ArmorMaterial(
            defense,
            25,
            SoundEvents.ARMOR_EQUIP_NETHERITE,
            () -> Ingredient.of(new ItemLike[]{(ItemLike)MainItems.KIKONO_SHARD.get()}),
            List.of(new Layer(ResourceLocation.fromNamespaceAndPath("dragonminez", "kikono"))),
            5.0F,
            0.1F
         );
      }
   );

   private ModArmorMaterials() {
   }
}
