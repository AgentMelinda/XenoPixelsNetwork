package com.dragonminez.common.network.C2S;

import com.dragonminez.common.quest.PartyManager;
import com.dragonminez.compat.network.NetworkEvent;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class InvitePartyMemberC2S {
   private static final Map<UUID, Long> LAST_INVITE_TICK = new ConcurrentHashMap<>();
   private static final long INVITE_COOLDOWN_TICKS = 60L;
   private final UUID targetPlayerId;

   public InvitePartyMemberC2S(UUID targetPlayerId) {
      this.targetPlayerId = targetPlayerId;
   }

   public InvitePartyMemberC2S(FriendlyByteBuf buffer) {
      this.targetPlayerId = buffer.readUUID();
   }

   public void encode(FriendlyByteBuf buffer) {
      buffer.writeUUID(this.targetPlayerId);
   }

   public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
      NetworkEvent.Context context = contextSupplier.get();
      context.enqueueWork(() -> {
         ServerPlayer inviter = context.getSender();
         if (inviter != null) {
            long now = inviter.level().getGameTime();
            Long lastInvite = LAST_INVITE_TICK.get(inviter.getUUID());
            if (lastInvite == null || now - lastInvite >= 60L) {
               LAST_INVITE_TICK.put(inviter.getUUID(), now);
               ServerPlayer invitee = inviter.getServer().getPlayerList().getPlayer(this.targetPlayerId);
               if (invitee != null) {
                  if (invitee.getUUID().equals(inviter.getUUID())) {
                     inviter.sendSystemMessage(Component.translatable("quest.dmz.party.invite.self"));
                  } else {
                     PartyManager.InviteRequestResult result = PartyManager.requestInvite(inviter, invitee);
                     switch (result) {
                        case INVITED:
                           inviter.sendSystemMessage(Component.translatable("quest.dmz.party.invite.sent", new Object[]{invitee.getGameProfile().getName()}));
                           break;
                        case ALREADY_IN_PARTY:
                           inviter.sendSystemMessage(Component.translatable("quest.dmz.party.invite.same_party"));
                           break;
                        case NO_PERMISSION:
                           inviter.sendSystemMessage(Component.translatable("quest.dmz.party.invite.leader_only"));
                           break;
                        case LEVEL_GAP:
                           inviter.sendSystemMessage(Component.translatable("quest.dmz.party.invite.level_gap"));
                           break;
                        case PARTY_FULL:
                           inviter.sendSystemMessage(Component.translatable("quest.dmz.party.invite.party_full"));
                     }
                  }
               }
            }
         }
      });
      context.setPacketHandled(true);
   }
}
