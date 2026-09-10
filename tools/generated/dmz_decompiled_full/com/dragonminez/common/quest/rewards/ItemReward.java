package com.dragonminez.common.quest.rewards;

import com.dragonminez.common.quest.QuestReward;
import lombok.Generated;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class ItemReward extends QuestReward {
   private final String itemId;
   private final int count;

   public ItemReward(ItemStack itemStack) {
      super(QuestReward.RewardType.ITEM);
      this.itemId = BuiltInRegistries.ITEM.getKey(itemStack.getItem()).toString();
      this.count = itemStack.getCount();
   }

   @Override
   public void giveReward(ServerPlayer player) {
      this.giveReward(player, 1.0);
   }

   @Override
   public void giveReward(ServerPlayer player, double rewardMultiplier) {
      Item item = (Item)BuiltInRegistries.ITEM.get(ResourceLocation.parse(this.itemId));
      if (item != null) {
         int scaledCount = this.scaledCount(rewardMultiplier);
         if (scaledCount > 0) {
            ItemStack stack = new ItemStack(item, scaledCount);
            player.getInventory().add(stack);
            if (!stack.isEmpty()) {
               ItemEntity drop = player.drop(stack, false);
               if (drop != null) {
                  drop.setNoPickUpDelay();
               }
            }
         }
      }
   }

   public int scaledCount(double rewardMultiplier) {
      return this.count <= 0 ? 0 : Math.max(1, (int)Math.round((double)this.count * rewardMultiplier));
   }

   @Override
   public Component getDescription() {
      return this.describe(this.count);
   }

   @Override
   public Component getDescription(double rewardMultiplier) {
      return this.describe(this.scaledCount(rewardMultiplier));
   }

   private Component describe(int shownCount) {
      return Component.translatable(
         "gui.dragonminez.quests.rewards.item", new Object[]{shownCount, Component.translatable("item." + ResourceLocation.parse(this.itemId).toLanguageKey())}
      );
   }

   @Generated
   public String getItemId() {
      return this.itemId;
   }

   @Generated
   public int getCount() {
      return this.count;
   }
}
