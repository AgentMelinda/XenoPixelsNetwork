package com.dragonminez.common.util.types.items;

import com.google.gson.GsonBuilder;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class EnchantedBookDTO extends EnchantedItemDTO {
   protected String itemType;
   protected Map<String, Integer> enchantments = new HashMap<>();

   public EnchantedBookDTO(Map<String, Integer> enchantments) {
      super("enchanted_book", "minecraft:enchanted_book", 1, enchantments);
      this.enchantments = enchantments;
   }

   @Override
   public ItemStack getItemStack() {
      return new ItemStack(Items.ENCHANTED_BOOK);
   }

   @Override
   public String toJson() {
      return new GsonBuilder().setPrettyPrinting().create().toJson(this, EnchantedBookDTO.class);
   }

   @Override
   public String getItemType() {
      return this.itemType;
   }

   @Override
   public Map<String, Integer> getEnchantments() {
      return this.enchantments;
   }

   @Override
   public void setItemType(String itemType) {
      this.itemType = itemType;
   }

   @Override
   public void setEnchantments(Map<String, Integer> enchantments) {
      this.enchantments = enchantments;
   }

   public EnchantedBookDTO() {
   }
}
