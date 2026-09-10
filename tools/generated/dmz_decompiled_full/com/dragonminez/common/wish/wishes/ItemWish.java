package com.dragonminez.common.wish.wishes;

import com.dragonminez.Env;
import com.dragonminez.LogUtil;
import com.dragonminez.common.wish.Wish;
import com.google.gson.GsonBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class ItemWish extends Wish {
   private final String itemId;
   private final int count;

   public ItemWish(String name, String description, String itemId, int count) {
      super(name, description, "item");
      this.itemId = itemId;
      this.count = count;
   }

   @Override
   public void grant(ServerPlayer player) {
      Item item = (Item)BuiltInRegistries.ITEM.get(ResourceLocation.parse(this.itemId));
      if (item != null) {
         giveOrDrop(player, new ItemStack(item, this.count));
      } else {
         LogUtil.warn(Env.COMMON, "Item with id " + this.itemId + " not found.");
      }
   }

   private static void giveOrDrop(ServerPlayer player, ItemStack stack) {
      player.getInventory().add(stack);
      if (!stack.isEmpty()) {
         ItemEntity drop = player.drop(stack, false);
         if (drop != null) {
            drop.setNoPickUpDelay();
         }
      }
   }

   @Override
   public String toJson() {
      return new GsonBuilder().setPrettyPrinting().create().toJson(this, ItemWish.class);
   }
}
