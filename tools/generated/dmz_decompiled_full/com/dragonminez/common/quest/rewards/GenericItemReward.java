package com.dragonminez.common.quest.rewards;

import com.dragonminez.common.quest.QuestReward;
import com.dragonminez.common.util.types.items.GenericItemDTO;
import lombok.Generated;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class GenericItemReward extends QuestReward {
   protected GenericItemDTO itemReward;

   public GenericItemReward(GenericItemDTO itemReward) {
      super(QuestReward.RewardType.GENERIC_ITEM);
      this.itemReward = itemReward;
   }

   @Override
   public void giveReward(ServerPlayer player) {
      this.giveReward(player, 1.0);
   }

   @Override
   public void giveReward(ServerPlayer player, double rewardMultiplier) {
      Item item = (Item)BuiltInRegistries.ITEM.get(ResourceLocation.parse(this.itemReward.getItemId()));
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
      return this.itemReward.getCount() <= 0 ? 0 : Math.max(1, (int)Math.round((double)this.itemReward.getCount() * rewardMultiplier));
   }

   @Override
   public Component getDescription() {
      return this.describe(this.itemReward.getCount());
   }

   @Override
   public Component getDescription(double rewardMultiplier) {
      return this.describe(this.scaledCount(rewardMultiplier));
   }

   private Component describe(int shownCount) {
      return Component.translatable(
         "gui.dragonminez.quests.rewards.item",
         new Object[]{shownCount, Component.translatable("item." + ResourceLocation.parse(this.itemReward.getItemId()).toLanguageKey())}
      );
   }

   @Generated
   public GenericItemDTO getItemReward() {
      return this.itemReward;
   }

   @Generated
   public void setItemReward(GenericItemDTO itemReward) {
      this.itemReward = itemReward;
   }
}
