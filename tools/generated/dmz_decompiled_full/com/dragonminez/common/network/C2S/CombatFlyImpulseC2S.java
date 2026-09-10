package com.dragonminez.common.network.C2S;

import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.network.NetworkHandler;
import com.dragonminez.common.network.S2C.ResourceSyncS2C;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.compat.network.NetworkEvent;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

public class CombatFlyImpulseC2S {
   private final byte direction;

   public CombatFlyImpulseC2S(int direction) {
      this.direction = (byte)direction;
   }

   public CombatFlyImpulseC2S(FriendlyByteBuf buf) {
      this.direction = buf.readByte();
   }

   public static void encode(CombatFlyImpulseC2S msg, FriendlyByteBuf buf) {
      buf.writeByte(msg.direction);
   }

   public static CombatFlyImpulseC2S decode(FriendlyByteBuf buf) {
      return new CombatFlyImpulseC2S(buf);
   }

   public static void handle(CombatFlyImpulseC2S msg, Supplier<NetworkEvent.Context> ctx) {
      ctx.get()
         .enqueueWork(
            () -> {
               ServerPlayer player = ctx.get().getSender();
               if (player != null) {
                  StatsProvider.get(StatsCapability.INSTANCE, player)
                     .ifPresent(
                        data -> {
                           if (data.getStatus().isHasCreatedCharacter()) {
                              if (data.getSkills().isSkillActive("fly")) {
                                 if (data.getStatus().getFlightMode() == 1) {
                                    if (!data.getCooldowns().hasCooldown("CombatFlyImpulseCd")) {
                                       if (!player.isCreative() && !player.isSpectator()) {
                                          int kiCost = (int)Math.ceil(
                                             (double)ConfigManager.getCombatConfig().getBaselineFormDrain().intValue()
                                                * ConfigManager.getCombatConfig().getCombatFlyImpulseKiCostPct()
                                          );
                                          if (!(data.getResources().getCurrentEnergy() < (float)kiCost)) {
                                             data.getResources().removeEnergy((float)kiCost);
                                             data.getCooldowns()
                                                .setCooldown("CombatFlyImpulseCd", ConfigManager.getCombatConfig().getCombatFlyImpulseCooldownTicks());
                                             NetworkHandler.sendToTrackingEntityAndSelf(new ResourceSyncS2C(player), player);
                                          }
                                       } else {
                                          data.getCooldowns()
                                             .setCooldown("CombatFlyImpulseCd", ConfigManager.getCombatConfig().getCombatFlyImpulseCooldownTicks());
                                       }
                                    }
                                 }
                              }
                           }
                        }
                     );
               }
            }
         );
      ctx.get().setPacketHandled(true);
   }
}
