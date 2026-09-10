package com.dragonminez.server.world.raid;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

public class RaidReward {
   private final List<Supplier<ItemStack>> itemRewards;

   private RaidReward(List<Supplier<ItemStack>> itemRewards) {
      this.itemRewards = itemRewards;
   }

   public void grant(ServerLevel level, List<ServerPlayer> participants, BlockPos center) {
      for (ServerPlayer player : participants) {
         for (Supplier<ItemStack> supplier : this.itemRewards) {
            ItemStack stack = supplier.get();
            if (!stack.isEmpty() && !player.getInventory().add(stack)) {
               player.drop(stack, false);
            }
         }
      }
   }

   public boolean isEmpty() {
      return this.itemRewards.isEmpty();
   }

   public static RaidReward.Builder builder() {
      return new RaidReward.Builder();
   }

   public static class Builder {
      private final List<Supplier<ItemStack>> itemRewards = new ArrayList<>();

      public RaidReward.Builder item(Supplier<? extends Item> item, int count) {
         this.itemRewards.add(() -> new ItemStack((ItemLike)item.get(), count));
         return this;
      }

      public RaidReward build() {
         return new RaidReward(List.copyOf(this.itemRewards));
      }
   }
}
