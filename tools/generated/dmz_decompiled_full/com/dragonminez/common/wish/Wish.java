package com.dragonminez.common.wish;

import lombok.Generated;
import net.minecraft.server.level.ServerPlayer;

public abstract class Wish {
   private final String name;
   private final String description;
   private final String type;

   public abstract void grant(ServerPlayer var1);

   public abstract String toJson();

   @Generated
   public String getName() {
      return this.name;
   }

   @Generated
   public String getDescription() {
      return this.description;
   }

   @Generated
   public String getType() {
      return this.type;
   }

   @Generated
   public Wish(String name, String description, String type) {
      this.name = name;
      this.description = description;
      this.type = type;
   }
}
