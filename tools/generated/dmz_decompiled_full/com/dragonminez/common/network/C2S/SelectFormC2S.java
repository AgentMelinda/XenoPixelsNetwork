package com.dragonminez.common.network.C2S;

import com.dragonminez.common.init.MainEffects;
import com.dragonminez.common.network.NetworkHandler;
import com.dragonminez.common.network.S2C.StatsSyncS2C;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.common.stats.extras.ActionMode;
import com.dragonminez.common.util.TransformationsHelper;
import com.dragonminez.compat.network.NetworkEvent;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

public class SelectFormC2S {
   private final String group;
   private final String form;
   private final boolean stack;

   public SelectFormC2S(String group, String form, boolean stack) {
      this.group = group != null ? group : "";
      this.form = form != null ? form : "";
      this.stack = stack;
   }

   public SelectFormC2S(FriendlyByteBuf buffer) {
      this.group = buffer.readUtf();
      this.form = buffer.readUtf();
      this.stack = buffer.readBoolean();
   }

   public void encode(FriendlyByteBuf buffer) {
      buffer.writeUtf(this.group);
      buffer.writeUtf(this.form);
      buffer.writeBoolean(this.stack);
   }

   public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
      NetworkEvent.Context context = contextSupplier.get();
      context.enqueueWork(() -> {
         ServerPlayer player = context.getSender();
         if (player != null) {
            if (!player.hasEffect(MainEffects.STUN)) {
               if (!this.group.isEmpty() && !this.form.isEmpty()) {
                  StatsProvider.get(StatsCapability.INSTANCE, player).ifPresent(data -> {
                     if (this.stack) {
                        if (!TransformationsHelper.isSelectableStackForm(data, this.group, this.form)) {
                           return;
                        }

                        data.getStatus().setSelectedAction(ActionMode.STACK);
                        data.getCharacter().setSelectedStackFormGroup(this.group);
                        data.getCharacter().setSelectedStackForm(this.form);
                     } else {
                        if (!TransformationsHelper.isSelectableForm(data, this.group, this.form)) {
                           return;
                        }

                        data.getStatus().setSelectedAction(ActionMode.FORM);
                        data.getCharacter().setSelectedFormGroup(this.group);
                        data.getCharacter().setSelectedForm(this.form);
                     }

                     NetworkHandler.sendToTrackingEntityAndSelf(new StatsSyncS2C(player), player);
                  });
               }
            }
         }
      });
      context.setPacketHandled(true);
   }
}
