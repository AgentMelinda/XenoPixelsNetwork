package com.dragonminez.common.quest.objectives;

import com.dragonminez.common.quest.QuestObjective;
import lombok.Generated;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class ItemObjective extends QuestObjective {
   private final String itemId;
   private final int count;

   public ItemObjective(Item item, int count) {
      super(QuestObjective.ObjectiveType.ITEM, count);
      this.itemId = BuiltInRegistries.ITEM.getKey(item).toString();
      this.count = count;
   }

   @Override
   public boolean checkProgress(Object... params) {
      if (params.length > 0 && params[0] instanceof ItemStack stack) {
         Item requiredItem = (Item)BuiltInRegistries.ITEM.get(ResourceLocation.parse(this.itemId));
         if (stack.is(requiredItem)) {
            this.addProgress(stack.getCount());
            return this.isCompleted();
         }
      }

      return false;
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
