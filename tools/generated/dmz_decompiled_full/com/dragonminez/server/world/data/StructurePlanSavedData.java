package com.dragonminez.server.world.data;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedData.Factory;
import org.jetbrains.annotations.NotNull;

public class StructurePlanSavedData extends SavedData {
   private static final String NAME = "dragonminez_structure_plan";
   private boolean resolved = false;
   private final Map<Integer, ChunkPos> positions = new HashMap<>();

   public static StructurePlanSavedData get(ServerLevel level) {
      return (StructurePlanSavedData)level.getDataStorage()
         .computeIfAbsent(new Factory(StructurePlanSavedData::new, StructurePlanSavedData::load), "dragonminez_structure_plan");
   }

   public boolean isResolved() {
      return this.resolved;
   }

   public Map<Integer, ChunkPos> getPositions() {
      return Collections.unmodifiableMap(this.positions);
   }

   public void setPositions(Map<Integer, ChunkPos> newPositions, boolean complete) {
      this.positions.clear();
      if (newPositions != null) {
         this.positions.putAll(newPositions);
      }

      this.resolved = complete;
      this.setDirty();
   }

   public void removePosition(int salt) {
      this.positions.remove(salt);
      this.resolved = false;
      this.setDirty();
   }

   public static StructurePlanSavedData load(CompoundTag tag, Provider registries) {
      StructurePlanSavedData data = new StructurePlanSavedData();
      data.resolved = tag.getBoolean("resolved");
      ListTag list = tag.getList("positions", 10);

      for (int i = 0; i < list.size(); i++) {
         CompoundTag entry = list.getCompound(i);
         data.positions.put(entry.getInt("salt"), new ChunkPos(entry.getInt("x"), entry.getInt("z")));
      }

      return data;
   }

   @NotNull
   public CompoundTag save(@NotNull CompoundTag tag, Provider registries) {
      tag.putBoolean("resolved", this.resolved);
      ListTag list = new ListTag();

      for (Entry<Integer, ChunkPos> e : this.positions.entrySet()) {
         CompoundTag entry = new CompoundTag();
         entry.putInt("salt", e.getKey());
         entry.putInt("x", e.getValue().x);
         entry.putInt("z", e.getValue().z);
         list.add(entry);
      }

      tag.put("positions", list);
      return tag;
   }
}
