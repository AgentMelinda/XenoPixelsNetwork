package com.dragonminez.common.network.C2S;

import com.dragonminez.common.network.NetworkHandler;
import com.dragonminez.common.network.S2C.ProgressionSyncS2C;
import com.dragonminez.common.quest.PlayerQuestData;
import com.dragonminez.common.quest.QuestRegistry;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.compat.network.NetworkEvent;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

public class SetTrackedQuestC2S {
   private final String questId;

   public SetTrackedQuestC2S(String questId) {
      this.questId = questId == null ? "" : questId;
   }

   public SetTrackedQuestC2S(FriendlyByteBuf buffer) {
      this.questId = buffer.readUtf();
   }

   public void encode(FriendlyByteBuf buffer) {
      buffer.writeUtf(this.questId);
   }

   public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
      NetworkEvent.Context context = contextSupplier.get();
      context.enqueueWork(() -> {
         ServerPlayer player = context.getSender();
         if (player != null) {
            StatsProvider.get(StatsCapability.INSTANCE, player).ifPresent(data -> {
               PlayerQuestData pqd = data.getPlayerQuestData();
               if (this.questId.isBlank()) {
                  pqd.setTrackedQuestId(null);
               } else {
                  boolean exists = QuestRegistry.getQuest(this.questId) != null;
                  boolean active = pqd.isQuestAccepted(this.questId) && !pqd.isQuestCompleted(this.questId);
                  pqd.setTrackedQuestId(exists && active ? this.questId : null);
               }

               NetworkHandler.sendToPlayer(new ProgressionSyncS2C(player), player);
            });
         }
      });
      context.setPacketHandled(true);
   }
}
