package com.dragonminez.common.util.types.items;

import com.google.gson.GsonBuilder;
import lombok.Generated;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class GenericItemDTO {
   protected String itemType;
   protected String itemId;
   protected int count = 1;

   public GenericItemDTO(String itemId, int count) {
      this.itemType = "generic_item";
      this.itemId = itemId;
      this.count = count;
   }

   public GenericItemDTO(String itemType, String itemId, int count) {
      this.itemType = itemType;
      this.itemId = itemId;
      this.count = count;
   }

   public ItemStack getItemStack() {
      Item item = (Item)BuiltInRegistries.ITEM.get(ResourceLocation.parse(this.getItemId()));
      return item != null ? new ItemStack(item, this.count) : ItemStack.EMPTY;
   }

   public String toJson() {
      return new GsonBuilder().setPrettyPrinting().create().toJson(this, GenericItemDTO.class);
   }

   @Generated
   public String getItemType() {
      return this.itemType;
   }

   @Generated
   public String getItemId() {
      return this.itemId;
   }

   @Generated
   public int getCount() {
      return this.count;
   }

   @Generated
   public void setItemType(String itemType) {
      this.itemType = itemType;
   }

   @Generated
   public void setItemId(String itemId) {
      this.itemId = itemId;
   }

   @Generated
   public void setCount(int count) {
      this.count = count;
   }

   @Generated
   public GenericItemDTO() {
   }
}
