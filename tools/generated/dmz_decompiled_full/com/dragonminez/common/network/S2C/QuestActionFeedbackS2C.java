package com.dragonminez.common.network.S2C;

import com.dragonminez.common.network.ClientPacketHandler;
import com.dragonminez.compat.DistExecutor;
import com.dragonminez.compat.network.NetworkEvent;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.neoforged.api.distmarker.Dist;

public class QuestActionFeedbackS2C {
   private final Component message;

   public QuestActionFeedbackS2C(Component message) {
      this.message = (Component)(message == null ? Component.empty() : message);
   }

   public QuestActionFeedbackS2C(FriendlyByteBuf buf) {
      this.message = (Component)buf.readJsonWithCodec(ComponentSerialization.CODEC);
   }

   public void encode(FriendlyByteBuf buf) {
      buf.writeJsonWithCodec(ComponentSerialization.CODEC, this.message);
   }

   public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
      NetworkEvent.Context context = contextSupplier.get();
      context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.handleQuestActionFeedback(this.message)));
      context.setPacketHandled(true);
   }
}
