package com.dragonminez.server.world.data;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedData.Factory;
import net.minecraft.world.level.storage.DimensionDataStorage;

public class MutantSavedData extends SavedData {
   private static final String FILE_NAME = "dragonminez_mutants";
   private final Set<UUID> holders = new HashSet<>();
   private long nextRollTick = -1L;

   public static MutantSavedData get(MinecraftServer server) {
      DimensionDataStorage storage = server.getLevel(Level.OVERWORLD).getDataStorage();
      return (MutantSavedData)storage.computeIfAbsent(new Factory(MutantSavedData::new, MutantSavedData::load), "dragonminez_mutants");
   }

   public static MutantSavedData load(CompoundTag tag, Provider registries) {
      MutantSavedData data = new MutantSavedData();
      ListTag holdersList = tag.getList("Holders", 10);

      for (int i = 0; i < holdersList.size(); i++) {
         CompoundTag holderTag = holdersList.getCompound(i);
         if (holderTag.hasUUID("Id")) {
            data.holders.add(holderTag.getUUID("Id"));
         }
      }

      data.nextRollTick = tag.contains("NextRollTick") ? tag.getLong("NextRollTick") : -1L;
      return data;
   }

   public CompoundTag save(CompoundTag tag, Provider registries) {
      ListTag holdersList = new ListTag();

      for (UUID holder : this.holders) {
         CompoundTag holderTag = new CompoundTag();
         holderTag.putUUID("Id", holder);
         holdersList.add(holderTag);
      }

      tag.put("Holders", holdersList);
      tag.putLong("NextRollTick", this.nextRollTick);
      return tag;
   }

   public boolean isHolder(UUID playerId) {
      return this.holders.contains(playerId);
   }

   public Set<UUID> getHolders() {
      return new HashSet<>(this.holders);
   }

   public int count() {
      return this.holders.size();
   }

   public void addHolder(UUID playerId) {
      if (this.holders.add(playerId)) {
         this.setDirty();
      }
   }

   public void removeHolder(UUID playerId) {
      if (this.holders.remove(playerId)) {
         this.setDirty();
      }
   }

   public void setNextRollTick(long tick) {
      this.nextRollTick = tick;
      this.setDirty();
   }

   public long getNextRollTick() {
      return this.nextRollTick;
   }
}
