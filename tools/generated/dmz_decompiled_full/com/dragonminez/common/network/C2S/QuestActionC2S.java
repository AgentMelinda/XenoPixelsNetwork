package com.dragonminez.common.network.C2S;

import com.dragonminez.Env;
import com.dragonminez.LogUtil;
import com.dragonminez.common.network.NetworkHandler;
import com.dragonminez.common.network.S2C.QuestActionFeedbackS2C;
import com.dragonminez.common.quest.QuestService;
import com.dragonminez.compat.network.NetworkEvent;
import java.util.function.Supplier;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class QuestActionC2S {
   private final QuestActionC2S.ActionType actionType;
   private final String questId;
   private final String npcId;

   public QuestActionC2S(QuestActionC2S.ActionType actionType, String questId, String npcId) {
      this.actionType = actionType;
      this.questId = questId;
      this.npcId = npcId != null ? npcId : "";
   }

   public QuestActionC2S(FriendlyByteBuf buffer) {
      this.actionType = (QuestActionC2S.ActionType)buffer.readEnum(QuestActionC2S.ActionType.class);
      this.questId = buffer.readUtf();
      this.npcId = buffer.readUtf();
   }

   public void encode(FriendlyByteBuf buffer) {
      buffer.writeEnum(this.actionType);
      buffer.writeUtf(this.questId);
      buffer.writeUtf(this.npcId);
   }

   public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
      NetworkEvent.Context context = contextSupplier.get();
      context.enqueueWork(
         () -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
               try {
                  Component failure = switch (this.actionType) {
                     case START -> QuestService.startQuest(player, this.questId);
                     case RESUMMON -> QuestService.resummonQuest(player, this.questId);
                     case TURN_IN -> QuestService.turnInQuest(player, this.questId, this.npcId);
                  };
                  if (failure != null) {
                     NetworkHandler.sendToPlayer(new QuestActionFeedbackS2C(failure.copy().withStyle(ChatFormatting.RED)), player);
                  }
               } catch (Exception var4) {
                  LogUtil.error(
                     Env.SERVER,
                     "Failed to handle quest action " + this.actionType + " for quest '" + this.questId + "' requested by " + player.getGameProfile().getName(),
                     var4
                  );
                  NetworkHandler.sendToPlayer(
                     new QuestActionFeedbackS2C(Component.translatable("message.dragonminez.quest.start.unavailable").withStyle(ChatFormatting.RED)), player
                  );
               }
            }
         }
      );
      context.setPacketHandled(true);
   }

   public static enum ActionType {
      START,
      RESUMMON,
      TURN_IN;
   }
}
