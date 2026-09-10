package com.dragonminez.common.quest.rewards;

import com.dragonminez.common.quest.QuestReward;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsProvider;
import lombok.Generated;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class SkillReward extends QuestReward {
   private final String skill;
   private final int level;

   public SkillReward(String skill, int level) {
      super(QuestReward.RewardType.SKILL);
      this.skill = skill;
      this.level = level;
   }

   @Override
   public void giveReward(ServerPlayer player) {
      StatsProvider.get(StatsCapability.INSTANCE, player).ifPresent(data -> {
         if (data.getSkills().getSkillLevel(this.skill) < this.level) {
            data.getSkills().setSkillLevel(this.skill, this.level);
         }
      });
   }

   @Override
   public Component getDescription() {
      return Component.translatable("gui.dragonminez.quests.rewards.skill", new Object[]{Component.translatable("skill.dragonminez." + this.skill), this.level});
   }

   @Generated
   public String getSkill() {
      return this.skill;
   }

   @Generated
   public int getLevel() {
      return this.level;
   }
}
