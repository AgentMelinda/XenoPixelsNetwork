package com.dragonminez.common.quest.rewards;

import com.dragonminez.common.quest.QuestReward;
import lombok.Generated;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class CommandReward extends QuestReward {
   private final String command;
   private final String translationKey;

   public CommandReward(String command, String translationKey) {
      super(QuestReward.RewardType.COMMAND);
      this.command = command;
      this.translationKey = translationKey;
   }

   @Override
   public void giveReward(ServerPlayer player) {
      String commandToExecute = this.command.replace("%player%", player.getName().getString());
      player.getServer().getCommands().performPrefixedCommand(player.getServer().createCommandSourceStack().withPermission(4), commandToExecute);
   }

   @Override
   public Component getDescription() {
      if (this.translationKey != null && !this.translationKey.isEmpty()) {
         return Component.translatable(this.translationKey);
      } else {
         String display = this.command.startsWith("/") ? this.command : "/" + this.command;
         return Component.literal(display);
      }
   }

   @Generated
   public String getCommand() {
      return this.command;
   }

   @Generated
   public String getTranslationKey() {
      return this.translationKey;
   }
}
