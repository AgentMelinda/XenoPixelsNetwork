package com.dragonminez.common.network;

import net.minecraft.network.FriendlyByteBuf;

public class ITTargetEntry {
   private final ITTargetEntry.Type type;
   private final String id;
   private final String name;
   private final String dimension;
   private final boolean reachable;

   public ITTargetEntry(ITTargetEntry.Type type, String id, String name, String dimension, boolean reachable) {
      this.type = type;
      this.id = id;
      this.name = name;
      this.dimension = dimension;
      this.reachable = reachable;
   }

   public ITTargetEntry.Type getType() {
      return this.type;
   }

   public String getId() {
      return this.id;
   }

   public String getName() {
      return this.name;
   }

   public String getDimension() {
      return this.dimension;
   }

   public boolean isReachable() {
      return this.reachable;
   }

   public int getPriority() {
      return switch (this.type) {
         case MASTER -> 1;
         case PARTY -> 2;
         case EXTERNAL -> 3;
      };
   }

   public void write(FriendlyByteBuf buf) {
      buf.writeEnum(this.type);
      buf.writeUtf(this.id);
      buf.writeUtf(this.name);
      buf.writeUtf(this.dimension);
      buf.writeBoolean(this.reachable);
   }

   public static ITTargetEntry read(FriendlyByteBuf buf) {
      ITTargetEntry.Type type = (ITTargetEntry.Type)buf.readEnum(ITTargetEntry.Type.class);
      String id = buf.readUtf();
      String name = buf.readUtf();
      String dimension = buf.readUtf();
      boolean reachable = buf.readBoolean();
      return new ITTargetEntry(type, id, name, dimension, reachable);
   }

   public static enum Type {
      MASTER,
      PARTY,
      EXTERNAL;
   }
}
