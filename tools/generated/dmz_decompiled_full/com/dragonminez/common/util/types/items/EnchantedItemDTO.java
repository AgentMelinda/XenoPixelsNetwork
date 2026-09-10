package com.dragonminez.common.util.types.items;

import com.google.gson.GsonBuilder;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class EnchantedItemDTO extends GenericItemDTO {
   protected Map<String, Integer> enchantments = new HashMap<>();

   public EnchantedItemDTO(String itemId, int count) {
      super("enchanted_item", itemId, count);
   }

   public EnchantedItemDTO(String itemId, int count, Map<String, Integer> enchantments) {
      super("enchanted_item", itemId, count);
      this.enchantments = enchantments;
   }

   public EnchantedItemDTO(String itemType, String itemId, int count) {
      this.itemType = itemType;
      this.itemId = itemId;
      this.count = count;
   }

   public EnchantedItemDTO(String itemType, String itemId, int count, Map<String, Integer> enchantments) {
      super(itemType, itemId, count);
      this.enchantments = enchantments;
   }

   @Override
   public ItemStack getItemStack() {
      Item item = (Item)BuiltInRegistries.ITEM.get(ResourceLocation.tryParse(this.getItemId()));
      return item != null ? new ItemStack(item, this.count) : ItemStack.EMPTY;
   }

   @Override
   public String toJson() {
      return new GsonBuilder().setPrettyPrinting().create().toJson(this, EnchantedItemDTO.class);
   }

   public Map<String, Integer> getEnchantments() {
      return this.enchantments;
   }

   public void setEnchantments(Map<String, Integer> enchantments) {
      this.enchantments = enchantments;
   }

   public EnchantedItemDTO() {
   }
}
