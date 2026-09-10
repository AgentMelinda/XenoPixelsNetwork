package com.dragonminez.common.quest.rewards;

import com.dragonminez.common.quest.QuestReward;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsProvider;
import lombok.Generated;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class AlignmentReward extends QuestReward {
   private final int amount;

   public AlignmentReward(int amount) {
      super(QuestReward.RewardType.ALIGNMENT);
      this.amount = amount;
   }

   public int scaledAmount(double rewardMultiplier) {
      return (int)Math.round((double)this.amount * rewardMultiplier);
   }

   @Override
   public void giveReward(ServerPlayer player) {
      this.giveReward(player, 1.0);
   }

   @Override
   public void giveReward(ServerPlayer player, double rewardMultiplier) {
      int scaled = this.scaledAmount(rewardMultiplier);
      if (scaled != 0) {
         StatsProvider.get(StatsCapability.INSTANCE, player).ifPresent(data -> data.getResources().addAlignment(scaled));
      }
   }

   @Override
   public Component getDescription() {
      return Component.translatable("gui.dragonminez.quests.rewards.alignment", new Object[]{this.amount});
   }

   @Override
   public Component getDescription(double rewardMultiplier) {
      return Component.translatable("gui.dragonminez.quests.rewards.alignment", new Object[]{this.scaledAmount(rewardMultiplier)});
   }

   @Generated
   public int getAmount() {
      return this.amount;
   }
}
