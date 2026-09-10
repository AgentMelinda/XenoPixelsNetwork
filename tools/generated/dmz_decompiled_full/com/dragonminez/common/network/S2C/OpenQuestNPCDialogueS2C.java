package com.dragonminez.common.network.S2C;

import com.dragonminez.common.network.ClientPacketHandler;
import com.dragonminez.compat.DistExecutor;
import com.dragonminez.compat.network.NetworkEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.neoforged.api.distmarker.Dist;

public class OpenQuestNPCDialogueS2C {
   private final String npcId;
   private final List<String> offerableQuestIds;
   private final List<String> turnInQuestIds;
   private final List<String> inProgressQuestIds;
   private final boolean masterNpc;
   private final int entityId;

   public OpenQuestNPCDialogueS2C(String npcId, List<String> offerableQuestIds, List<String> turnInQuestIds, List<String> inProgressQuestIds) {
      this(npcId, offerableQuestIds, turnInQuestIds, inProgressQuestIds, false, -1);
   }

   public OpenQuestNPCDialogueS2C(
      String npcId, List<String> offerableQuestIds, List<String> turnInQuestIds, List<String> inProgressQuestIds, boolean masterNpc, int entityId
   ) {
      this.npcId = npcId;
      this.offerableQuestIds = offerableQuestIds;
      this.turnInQuestIds = turnInQuestIds;
      this.inProgressQuestIds = inProgressQuestIds;
      this.masterNpc = masterNpc;
      this.entityId = entityId;
   }

   public OpenQuestNPCDialogueS2C(FriendlyByteBuf buffer) {
      this.npcId = buffer.readUtf();
      int offerCount = buffer.readVarInt();
      this.offerableQuestIds = new ArrayList<>(offerCount);

      for (int i = 0; i < offerCount; i++) {
         this.offerableQuestIds.add(buffer.readUtf());
      }

      int turnInCount = buffer.readVarInt();
      this.turnInQuestIds = new ArrayList<>(turnInCount);

      for (int i = 0; i < turnInCount; i++) {
         this.turnInQuestIds.add(buffer.readUtf());
      }

      int progressCount = buffer.readVarInt();
      this.inProgressQuestIds = new ArrayList<>(progressCount);

      for (int i = 0; i < progressCount; i++) {
         this.inProgressQuestIds.add(buffer.readUtf());
      }

      this.masterNpc = buffer.readBoolean();
      this.entityId = buffer.readVarInt();
   }

   public void encode(FriendlyByteBuf buffer) {
      buffer.writeUtf(this.npcId);
      buffer.writeVarInt(this.offerableQuestIds.size());

      for (String id : this.offerableQuestIds) {
         buffer.writeUtf(id);
      }

      buffer.writeVarInt(this.turnInQuestIds.size());

      for (String id : this.turnInQuestIds) {
         buffer.writeUtf(id);
      }

      buffer.writeVarInt(this.inProgressQuestIds.size());

      for (String id : this.inProgressQuestIds) {
         buffer.writeUtf(id);
      }

      buffer.writeBoolean(this.masterNpc);
      buffer.writeVarInt(this.entityId);
   }

   public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
      NetworkEvent.Context context = contextSupplier.get();
      context.enqueueWork(
         () -> DistExecutor.unsafeRunWhenOn(
               Dist.CLIENT,
               () -> () -> ClientPacketHandler.handleOpenQuestNpcDialoguePacket(
                        this.npcId, this.offerableQuestIds, this.turnInQuestIds, this.inProgressQuestIds, this.masterNpc, this.entityId
                     )
            )
      );
      context.setPacketHandled(true);
   }
}
