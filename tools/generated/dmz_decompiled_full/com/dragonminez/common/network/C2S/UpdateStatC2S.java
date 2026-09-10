package com.dragonminez.common.network.C2S;

import com.dragonminez.common.init.MainEffects;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.compat.network.NetworkEvent;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

public class UpdateStatC2S {
   private static final String BLOCK_END_TIME_TAG = "dmz_block_end_time";
   private static final long BLOCK_REACTIVATION_DELAY_MS = 250L;
   private final UpdateStatC2S.StatAction statusKey;
   private final boolean value;

   public UpdateStatC2S(UpdateStatC2S.StatAction statusKey, boolean value) {
      this.statusKey = statusKey;
      this.value = value;
   }

   public static void encode(UpdateStatC2S msg, FriendlyByteBuf buf) {
      buf.writeEnum(msg.statusKey);
      buf.writeBoolean(msg.value);
   }

   public static UpdateStatC2S decode(FriendlyByteBuf buf) {
      return new UpdateStatC2S((UpdateStatC2S.StatAction)buf.readEnum(UpdateStatC2S.StatAction.class), buf.readBoolean());
   }

   public static void handle(UpdateStatC2S msg, Supplier<NetworkEvent.Context> ctx) {
      ctx.get().enqueueWork(() -> {
         ServerPlayer player = ctx.get().getSender();
         if (player != null) {
            StatsProvider.get(StatsCapability.INSTANCE, player).ifPresent(data -> {
               if (player.hasEffect(MainEffects.STUN) && msg.statusKey != UpdateStatC2S.StatAction.BLOCK) {
                  if (msg.statusKey == UpdateStatC2S.StatAction.CHARGE_KI && data.getStatus().isChargingKi()) {
                     data.getStatus().setChargingKi(false);
                  }

                  if (msg.statusKey == UpdateStatC2S.StatAction.DESCEND && data.getStatus().isDescending()) {
                     data.getStatus().setDescending(false);
                  }

                  if (msg.statusKey == UpdateStatC2S.StatAction.ACTION_CHARGE && data.getStatus().isActionCharging()) {
                     data.getStatus().setActionCharging(false);
                  }
               } else {
                  switch (msg.statusKey) {
                     case CHARGE_KI:
                        if (data.getStatus().isChargingKi() != msg.value) {
                           data.getStatus().setChargingKi(msg.value);
                        }
                        break;
                     case DESCEND:
                        if (data.getStatus().isDescending() != msg.value) {
                           data.getStatus().setDescending(msg.value);
                        }
                        break;
                     case ACTION_CHARGE:
                        if (data.getStatus().isActionCharging() != msg.value) {
                           data.getStatus().setActionCharging(msg.value);
                        }
                        break;
                     case BLOCK:
                        long now = System.currentTimeMillis();
                        if (msg.value) {
                           if (!data.getStatus().isBlocking() && now - player.getPersistentData().getLong("dmz_block_end_time") >= 250L) {
                              data.getStatus().setBlocking(true);
                              data.getStatus().setLastBlockTime(now);
                           }
                        } else if (data.getStatus().isBlocking()) {
                           data.getStatus().setBlocking(false);
                           player.getPersistentData().putLong("dmz_block_end_time", now);
                        }
                  }
               }
            });
         }
      });
      ctx.get().setPacketHandled(true);
   }

   public static enum StatAction {
      CHARGE_KI,
      DESCEND,
      ACTION_CHARGE,
      BLOCK;
   }
}
