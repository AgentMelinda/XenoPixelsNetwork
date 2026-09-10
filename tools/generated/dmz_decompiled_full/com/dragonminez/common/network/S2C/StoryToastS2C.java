package com.dragonminez.common.network.S2C;

import com.dragonminez.common.network.ClientPacketHandler;
import com.dragonminez.compat.DistExecutor;
import com.dragonminez.compat.network.NetworkEvent;
import java.util.function.Supplier;
import lombok.Generated;
import net.minecraft.network.FriendlyByteBuf;
import net.neoforged.api.distmarker.Dist;

public class StoryToastS2C {
   private final StoryToastS2C.ToastEventType eventType;
   private final String questId;
   private final int objectiveIndex;
   private final int objectiveProgress;
   private final int objectiveRequired;

   public StoryToastS2C(StoryToastS2C.ToastEventType eventType, String questId, int objectiveIndex, int objectiveProgress, int objectiveRequired) {
      this.eventType = eventType;
      this.questId = questId != null && !questId.isBlank() ? questId : "";
      this.objectiveIndex = objectiveIndex;
      this.objectiveProgress = objectiveProgress;
      this.objectiveRequired = objectiveRequired;
   }

   public static StoryToastS2C questStarted(String questId) {
      return new StoryToastS2C(StoryToastS2C.ToastEventType.QUEST_STARTED, questId, -1, -1, -1);
   }

   public static StoryToastS2C questFailed(String questId) {
      return new StoryToastS2C(StoryToastS2C.ToastEventType.QUEST_FAILED, questId, -1, -1, -1);
   }

   public static StoryToastS2C objectiveComplete(String questId, int objectiveIndex, int objectiveProgress, int objectiveRequired) {
      return new StoryToastS2C(StoryToastS2C.ToastEventType.OBJECTIVE_COMPLETE, questId, objectiveIndex, objectiveProgress, objectiveRequired);
   }

   public static StoryToastS2C questComplete(String questId) {
      return new StoryToastS2C(StoryToastS2C.ToastEventType.QUEST_COMPLETE, questId, -1, -1, -1);
   }

   public static void encode(StoryToastS2C msg, FriendlyByteBuf buf) {
      buf.writeEnum(msg.eventType);
      buf.writeUtf(msg.questId);
      buf.writeInt(msg.objectiveIndex);
      buf.writeInt(msg.objectiveProgress);
      buf.writeInt(msg.objectiveRequired);
   }

   public static StoryToastS2C decode(FriendlyByteBuf buf) {
      return new StoryToastS2C(
         (StoryToastS2C.ToastEventType)buf.readEnum(StoryToastS2C.ToastEventType.class), buf.readUtf(), buf.readInt(), buf.readInt(), buf.readInt()
      );
   }

   public static void handle(StoryToastS2C msg, Supplier<NetworkEvent.Context> ctx) {
      ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.handleStoryToastPacket(msg)));
      ctx.get().setPacketHandled(true);
   }

   @Generated
   public StoryToastS2C.ToastEventType getEventType() {
      return this.eventType;
   }

   @Generated
   public String getQuestId() {
      return this.questId;
   }

   @Generated
   public int getObjectiveIndex() {
      return this.objectiveIndex;
   }

   @Generated
   public int getObjectiveProgress() {
      return this.objectiveProgress;
   }

   @Generated
   public int getObjectiveRequired() {
      return this.objectiveRequired;
   }

   public static enum ToastEventType {
      QUEST_STARTED,
      QUEST_FAILED,
      OBJECTIVE_COMPLETE,
      QUEST_COMPLETE;
   }
}
