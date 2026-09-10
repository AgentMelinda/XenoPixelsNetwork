package com.dragonminez.common.quest;

import java.util.EnumSet;
import java.util.Set;
import lombok.Generated;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public abstract class QuestReward {
   private final QuestReward.RewardType type;
   private Set<Difficulty> difficulties = EnumSet.allOf(Difficulty.class);

   public QuestReward(QuestReward.RewardType type) {
      this.type = type;
   }

   public void setDifficulties(Set<Difficulty> difficulties) {
      this.difficulties = difficulties != null && !difficulties.isEmpty() ? EnumSet.copyOf(difficulties) : EnumSet.allOf(Difficulty.class);
   }

   public abstract void giveReward(ServerPlayer var1);

   public void giveReward(ServerPlayer player, double rewardMultiplier) {
      this.giveReward(player);
   }

   public abstract Component getDescription();

   public Component getDescription(double rewardMultiplier) {
      return this.getDescription();
   }

   public boolean isUnlockedFor(Difficulty difficulty) {
      return this.difficulties.contains(difficulty != null ? difficulty : Difficulty.NORMAL);
   }

   @Generated
   public QuestReward.RewardType getType() {
      return this.type;
   }

   @Generated
   public Set<Difficulty> getDifficulties() {
      return this.difficulties;
   }

   public static enum RewardType {
      ITEM,
      GENERIC_ITEM,
      COMMAND,
      TPS,
      SKILL,
      ALIGNMENT,
      TRANSFORMATION,
      KI_TECHNIQUE;
   }
}
