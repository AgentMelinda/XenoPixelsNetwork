package com.dragonminez.common.network.C2S;

import com.dragonminez.common.network.NetworkHandler;
import com.dragonminez.common.network.S2C.ProgressionSyncS2C;
import com.dragonminez.common.quest.PlayerQuestData;
import com.dragonminez.common.quest.QuestRegistry;
import com.dragonminez.common.quest.Saga;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.compat.network.NetworkEvent;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

public class UnlockSagaC2S {
   private final String sagaId;

   public UnlockSagaC2S(String sagaId) {
      this.sagaId = sagaId;
   }

   public UnlockSagaC2S(FriendlyByteBuf buffer) {
      this.sagaId = buffer.readUtf();
   }

   public void encode(FriendlyByteBuf buffer) {
      buffer.writeUtf(this.sagaId);
   }

   public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
      NetworkEvent.Context context = contextSupplier.get();
      context.enqueueWork(() -> {
         ServerPlayer player = context.getSender();
         if (player != null) {
            Saga saga = QuestRegistry.getSaga(this.sagaId);
            if (saga != null) {
               StatsProvider.get(StatsCapability.INSTANCE, player).ifPresent(stats -> {
                  PlayerQuestData pqd = stats.getPlayerQuestData();
                  if (pqd.isSagaLocked(this.sagaId)) {
                     pqd.setSagaUnlocked(this.sagaId, true);
                     NetworkHandler.sendToTrackingEntityAndSelf(new ProgressionSyncS2C(player), player);
                  }
               });
            }
         }
      });
      context.setPacketHandled(true);
   }
}
