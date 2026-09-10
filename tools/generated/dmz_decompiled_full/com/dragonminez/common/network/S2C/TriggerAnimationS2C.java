package com.dragonminez.common.network.S2C;

import com.dragonminez.common.network.ClientPacketHandler;
import com.dragonminez.compat.DistExecutor;
import com.dragonminez.compat.network.NetworkEvent;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.neoforged.api.distmarker.Dist;

public class TriggerAnimationS2C {
   private final UUID playerUUID;
   private final TriggerAnimationS2C.AnimationType animationType;
   private final int variant;
   private final int entityId;
   private final String stringPayload;

   public TriggerAnimationS2C(UUID playerUUID, TriggerAnimationS2C.AnimationType animationType, int variant) {
      this.playerUUID = playerUUID;
      this.animationType = animationType;
      this.variant = variant;
      this.entityId = -1;
      this.stringPayload = "";
   }

   public TriggerAnimationS2C(UUID playerUUID, TriggerAnimationS2C.AnimationType animationType, int variant, int entityId) {
      this.playerUUID = playerUUID;
      this.animationType = animationType;
      this.variant = variant;
      this.entityId = entityId;
      this.stringPayload = "";
   }

   public TriggerAnimationS2C(UUID playerUUID, TriggerAnimationS2C.AnimationType animationType, int variant, int entityId, String stringPayload) {
      this.playerUUID = playerUUID;
      this.animationType = animationType;
      this.variant = variant;
      this.entityId = entityId;
      this.stringPayload = stringPayload;
   }

   public TriggerAnimationS2C(FriendlyByteBuf buffer) {
      this.playerUUID = buffer.readUUID();
      this.animationType = (TriggerAnimationS2C.AnimationType)buffer.readEnum(TriggerAnimationS2C.AnimationType.class);
      this.variant = buffer.readInt();
      this.entityId = buffer.readInt();
      this.stringPayload = buffer.readUtf();
   }

   public void encode(FriendlyByteBuf buffer) {
      buffer.writeUUID(this.playerUUID);
      buffer.writeEnum(this.animationType);
      buffer.writeInt(this.variant);
      buffer.writeInt(this.entityId);
      buffer.writeUtf(this.stringPayload);
   }

   public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
      NetworkEvent.Context context = contextSupplier.get();
      context.enqueueWork(
         () -> DistExecutor.unsafeRunWhenOn(
               Dist.CLIENT,
               () -> () -> ClientPacketHandler.handleTriggerAnimationPacket(
                        this.playerUUID, this.animationType, this.variant, this.entityId, this.stringPayload
                     )
            )
      );
      context.setPacketHandled(true);
   }

   public static enum AnimationType {
      EVASION,
      DASH,
      KI_BLAST_SHOT,
      KI_ANIMATION,
      KI_ANIMATION_STOP;
   }
}
