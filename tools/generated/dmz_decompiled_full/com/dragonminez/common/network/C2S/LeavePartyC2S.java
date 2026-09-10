package com.dragonminez.common.network.C2S;

import com.dragonminez.common.quest.PartyManager;
import com.dragonminez.compat.network.NetworkEvent;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class LeavePartyC2S {
   public LeavePartyC2S() {
   }

   public LeavePartyC2S(FriendlyByteBuf ignored) {
   }

   public void encode(FriendlyByteBuf ignored) {
   }

   public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
      NetworkEvent.Context context = contextSupplier.get();
      context.enqueueWork(
         () -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
               if (!PartyManager.isInParty(player)) {
                  player.sendSystemMessage(Component.translatable("quest.dmz.party.leave.solo").withStyle(ChatFormatting.RED));
               } else {
                  boolean leaderLeaving = PartyManager.isPartyLeader(player);
                  List<ServerPlayer> members = PartyManager.getAllPartyMembers(player);
                  if (leaderLeaving) {
                     PartyManager.disbandParty(player);
                  } else {
                     PartyManager.leaveParty(player);
                  }

                  if (leaderLeaving) {
                     player.sendSystemMessage(Component.translatable("quest.dmz.party.disbanded.self").withStyle(ChatFormatting.YELLOW));

                     for (ServerPlayer member : members) {
                        if (!member.equals(player)) {
                           member.sendSystemMessage(
                              Component.translatable("quest.dmz.party.disbanded.other", new Object[]{player.getName()}).withStyle(ChatFormatting.YELLOW)
                           );
                        }
                     }
                  } else {
                     player.sendSystemMessage(Component.translatable("quest.dmz.party.left").withStyle(ChatFormatting.YELLOW));

                     for (ServerPlayer memberx : members) {
                        if (!memberx.equals(player)) {
                           memberx.sendSystemMessage(
                              Component.translatable("quest.dmz.party.player.left", new Object[]{player.getName()}).withStyle(ChatFormatting.YELLOW)
                           );
                        }
                     }
                  }
               }
            }
         }
      );
      context.setPacketHandled(true);
   }
}
