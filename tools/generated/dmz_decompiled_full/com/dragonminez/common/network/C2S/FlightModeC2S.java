package com.dragonminez.common.network.C2S;

import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.network.NetworkHandler;
import com.dragonminez.common.network.S2C.StatsSyncS2C;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.common.stats.skills.Skill;
import com.dragonminez.compat.network.NetworkEvent;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

public class FlightModeC2S {
   public FlightModeC2S() {
   }

   public FlightModeC2S(FriendlyByteBuf buf) {
   }

   public static void encode(FlightModeC2S msg, FriendlyByteBuf buf) {
   }

   public static FlightModeC2S decode(FriendlyByteBuf buf) {
      return new FlightModeC2S(buf);
   }

   public static void handle(FlightModeC2S msg, Supplier<NetworkEvent.Context> ctx) {
      ctx.get().enqueueWork(() -> {
         ServerPlayer player = ctx.get().getSender();
         if (player != null) {
            StatsProvider.get(StatsCapability.INSTANCE, player).ifPresent(data -> {
               if (data.getStatus().isHasCreatedCharacter()) {
                  if (!data.getStatus().isStunned()) {
                     Skill flySkill = data.getSkills().getSkill("fly");
                     Skill kiControlSkill = data.getSkills().getSkill("kicontrol");
                     if (flySkill != null && kiControlSkill != null && flySkill.getLevel() > 0 && kiControlSkill.getLevel() > 0) {
                        int currentMode = data.getStatus().getFlightMode();
                        int targetMode = currentMode == 1 ? 0 : 1;
                        if (targetMode != 0 || !data.getCooldowns().hasCooldown("CombatFlyLock")) {
                           boolean wasActive = flySkill.isActive();
                           if (!wasActive) {
                              int flyLevel = flySkill.getLevel();
                              double energyCostPercent = Math.max(0.01, 0.04 - (double)flyLevel * 0.003);
                              int energyCost = (int)Math.ceil((double)ConfigManager.getCombatConfig().getBaselineFormDrain().intValue() * energyCostPercent);
                              if (data.getResources().getCurrentEnergy() < (float)energyCost) {
                                 return;
                              }

                              flySkill.setActive(true);
                              player.getAbilities().mayfly = true;
                              player.getAbilities().flying = false;
                              player.onUpdateAbilities();
                           }

                           data.getStatus().setFlightMode(targetMode);
                           NetworkHandler.sendToTrackingEntityAndSelf(new StatsSyncS2C(player), player);
                        }
                     }
                  }
               }
            });
         }
      });
      ctx.get().setPacketHandled(true);
   }
}
