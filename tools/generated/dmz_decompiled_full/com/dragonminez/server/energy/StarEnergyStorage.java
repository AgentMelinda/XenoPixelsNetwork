package com.dragonminez.server.energy;

import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.energy.EnergyStorage;

public class StarEnergyStorage extends EnergyStorage {
   public StarEnergyStorage(int capacity, int maxTransfer) {
      super(capacity, maxTransfer);
   }

   public int extractEnergy(int maxExtract, boolean simulate) {
      int extracted = super.extractEnergy(maxExtract, simulate);
      if (extracted != 0) {
         this.onEnergyChanged();
      }

      return extracted;
   }

   public int receiveEnergy(int maxReceive, boolean simulate) {
      int received = super.receiveEnergy(maxReceive, simulate);
      if (received != 0) {
         this.onEnergyChanged();
      }

      return received;
   }

   public void setEnergy(int energy) {
      this.energy = energy;
      this.onEnergyChanged();
   }

   public void onEnergyChanged() {
   }

   public void saveNBT(CompoundTag tag) {
      tag.putInt("energy", this.energy);
   }

   public void loadNBT(CompoundTag tag) {
      this.energy = tag.getInt("energy");
   }
}
