package com.dragonminez.common.util.types.items;

import com.google.gson.GsonBuilder;
import java.util.Map;
import net.minecraft.world.item.ItemStack;

public class TrimmedArmorDTO extends EnchantedItemDTO {
   private String material;
   private String pattern;

   public TrimmedArmorDTO(String itemId, String material, String pattern) {
      super("trimmed_armor", itemId, 1);
      this.material = material;
      this.pattern = pattern;
   }

   public TrimmedArmorDTO(String itemId, Map<String, Integer> enchantments, String material, String pattern) {
      super("trimmed_armor", itemId, 1, enchantments);
      this.material = material;
      this.pattern = pattern;
   }

   @Override
   public ItemStack getItemStack() {
      return super.getItemStack();
   }

   @Override
   public String toJson() {
      return new GsonBuilder().setPrettyPrinting().create().toJson(this, TrimmedArmorDTO.class);
   }

   public String getMaterial() {
      return this.material;
   }

   public String getPattern() {
      return this.pattern;
   }

   public void setMaterial(String material) {
      this.material = material;
   }

   public void setPattern(String pattern) {
      this.pattern = pattern;
   }

   public TrimmedArmorDTO() {
   }
}
