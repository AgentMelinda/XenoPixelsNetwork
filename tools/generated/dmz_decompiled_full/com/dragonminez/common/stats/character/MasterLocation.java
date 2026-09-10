package com.dragonminez.common.stats.character;

import net.minecraft.core.BlockPos;

public class MasterLocation {
   private final String masterId;
   private final String displayName;
   private final String dimension;
   private final BlockPos position;

   public MasterLocation(String masterId, String displayName, String dimension, BlockPos position) {
      this.masterId = masterId;
      this.displayName = displayName;
      this.dimension = dimension;
      this.position = position;
   }

   public String getMasterId() {
      return this.masterId;
   }

   public String getDisplayName() {
      return this.displayName;
   }

   public String getDimension() {
      return this.dimension;
   }

   public BlockPos getPosition() {
      return this.position;
   }
}
