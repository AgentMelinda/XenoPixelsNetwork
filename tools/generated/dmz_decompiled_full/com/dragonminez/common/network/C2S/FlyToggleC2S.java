package com.dragonminez.common.network.C2S;

import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.network.NetworkHandler;
import com.dragonminez.common.network.S2C.ProgressionSyncS2C;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.common.stats.skills.Skill;
import com.dragonminez.compat.network.NetworkEvent;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

public class FlyToggleC2S {
   private final boolean enable;

   public FlyToggleC2S(boolean enable) {
      this.enable = enable;
   }

   public FlyToggleC2S(FriendlyByteBuf buf) {
      this.enable = buf.readBoolean();
   }

   public static void encode(FlyToggleC2S msg, FriendlyByteBuf buf) {
      buf.writeBoolean(msg.enable);
   }

   public static FlyToggleC2S decode(FriendlyByteBuf buf) {
      return new FlyToggleC2S(buf);
   }

   public static void handle(FlyToggleC2S msg, Supplier<NetworkEvent.Context> ctx) {
      ctx.get().enqueueWork(() -> {
         ServerPlayer player = ctx.get().getSender();
         if (player != null) {
            StatsProvider.get(StatsCapability.INSTANCE, player).ifPresent(data -> {
               Skill flySkill = data.getSkills().getSkill("fly");
               if (flySkill != null && flySkill.getLevel() > 0) {
                  if (flySkill.isActive() != msg.enable) {
                     int flyLevel = flySkill.getLevel();
                     double energyCostPercent = Math.max(0.01, 0.04 - (double)flyLevel * 0.003);
                     int energyCost = (int)Math.ceil((double)ConfigManager.getCombatConfig().getBaselineFormDrain().intValue() * energyCostPercent);
                     if (!msg.enable || player.isCreative() || player.isSpectator() || !(data.getResources().getCurrentEnergy() < (float)energyCost)) {
                        flySkill.setActive(msg.enable);
                        if (flySkill.isActive()) {
                           player.getAbilities().mayfly = true;
                           player.getAbilities().flying = false;
                           player.onUpdateAbilities();
                        } else {
                           player.resetFallDistance();
                           if (!player.isCreative() && !player.isSpectator()) {
                              player.getAbilities().mayfly = false;
                              player.getAbilities().flying = false;
                              player.onUpdateAbilities();
                           }
                        }

                        NetworkHandler.sendToTrackingEntityAndSelf(new ProgressionSyncS2C(player), player);
                     }
                  }
               }
            });
         }
      });
      ctx.get().setPacketHandled(true);
   }
}
