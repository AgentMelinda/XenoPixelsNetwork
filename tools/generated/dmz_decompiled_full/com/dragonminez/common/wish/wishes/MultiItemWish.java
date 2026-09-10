package com.dragonminez.common.wish.wishes;

import com.dragonminez.Env;
import com.dragonminez.LogUtil;
import com.dragonminez.common.wish.Wish;
import com.google.gson.GsonBuilder;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Tuple;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class MultiItemWish extends Wish {
   private final List<Tuple<String, Integer>> items;

   public MultiItemWish(String name, String description, List<Tuple<String, Integer>> items) {
      super(name, description, "multi_wish");
      this.items = items;
   }

   @Override
   public void grant(ServerPlayer player) {
      for (Tuple<String, Integer> itemInfo : this.items) {
         Item item = (Item)BuiltInRegistries.ITEM.get(ResourceLocation.parse((String)itemInfo.getA()));
         if (item != null) {
            giveOrDrop(player, new ItemStack(item, (Integer)itemInfo.getB()));
         } else {
            LogUtil.warn(Env.COMMON, "Item with id " + (String)itemInfo.getA() + " not found.");
         }
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
      return new GsonBuilder().setPrettyPrinting().create().toJson(this, MultiItemWish.class);
   }
}
