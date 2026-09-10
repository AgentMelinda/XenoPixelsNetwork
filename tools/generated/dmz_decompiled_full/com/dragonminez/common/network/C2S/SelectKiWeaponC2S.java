package com.dragonminez.common.network.C2S;

import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.init.MainEffects;
import com.dragonminez.common.network.NetworkHandler;
import com.dragonminez.common.network.S2C.StatsSyncS2C;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.compat.network.NetworkEvent;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

public class SelectKiWeaponC2S {
   private final String type;

   public SelectKiWeaponC2S(String type) {
      this.type = type != null ? type : "";
   }

   public SelectKiWeaponC2S(FriendlyByteBuf buffer) {
      this.type = buffer.readUtf();
   }

   public void encode(FriendlyByteBuf buffer) {
      buffer.writeUtf(this.type);
   }

   public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
      NetworkEvent.Context context = contextSupplier.get();
      context.enqueueWork(() -> {
         ServerPlayer player = context.getSender();
         if (player != null) {
            if (!player.hasEffect(MainEffects.STUN)) {
               StatsProvider.get(StatsCapability.INSTANCE, player).ifPresent(data -> {
                  if (data.getSkills().hasSkill("kimanipulation")) {
                     List<String> types = ConfigManager.getCombatConfig().getKiWeaponTypes();
                     if (!this.type.isEmpty() && types.contains(this.type.toLowerCase())) {
                        boolean active = data.getSkills().isSkillActive("kimanipulation");
                        String current = data.getStatus().getKiWeaponType();
                        if (active && current != null && current.equalsIgnoreCase(this.type)) {
                           data.getSkills().setSkillActive("kimanipulation", false);
                        } else {
                           data.getStatus().setKiWeaponType(this.type.toLowerCase());
                           if (!active) {
                              data.getSkills().setSkillActive("kimanipulation", true);
                           }
                        }

                        player.refreshDimensions();
                        NetworkHandler.sendToTrackingEntityAndSelf(new StatsSyncS2C(player), player);
                     }
                  }
               });
            }
         }
      });
      context.setPacketHandled(true);
   }
}
