package com.dragonminez.common.init.block.entity;

import com.dragonminez.common.init.MainBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class TimeChamberPortalBlockEntity extends BlockEntity {
   private BlockPos cachedTargetPos = null;

   public TimeChamberPortalBlockEntity(BlockPos pPos, BlockState pBlockState) {
      super((BlockEntityType)MainBlockEntities.TIME_CHAMBER_PORTAL.get(), pPos, pBlockState);
   }

   public boolean hasCachedTarget() {
      return this.cachedTargetPos != null;
   }

   public BlockPos getCachedTarget() {
      return this.cachedTargetPos;
   }

   public void setCachedTarget(BlockPos target) {
      this.cachedTargetPos = target;
      this.setChanged();
      if (this.level != null) {
         this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
      }
   }

   protected void loadAdditional(CompoundTag pTag, Provider registries) {
      super.loadAdditional(pTag, registries);
      if (pTag.contains("TargetPos")) {
         this.cachedTargetPos = (BlockPos)NbtUtils.readBlockPos(pTag, "TargetPos").orElse(null);
      }
   }

   protected void saveAdditional(CompoundTag pTag, Provider registries) {
      super.saveAdditional(pTag, registries);
      if (this.cachedTargetPos != null) {
         pTag.put("TargetPos", NbtUtils.writeBlockPos(this.cachedTargetPos));
      }
   }
}
