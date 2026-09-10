package com.dragonminez.server.world.npc;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.Map.Entry;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedData.Factory;
import org.jetbrains.annotations.NotNull;

public class NPCPlacementSavedData extends SavedData {
   private static final String DATA_NAME = "dragonminez_npc_placements";
   private static final String PLACEMENTS_KEY = "placements";
   private final Map<String, UUID> placements = new HashMap<>();

   public static NPCPlacementSavedData get(ServerLevel level) {
      return (NPCPlacementSavedData)level.getDataStorage()
         .computeIfAbsent(new Factory(NPCPlacementSavedData::new, NPCPlacementSavedData::load), "dragonminez_npc_placements");
   }

   public boolean hasPlacement(String placementId) {
      return this.placements.containsKey(placementId);
   }

   public Optional<UUID> getEntityUuid(String placementId) {
      return Optional.ofNullable(this.placements.get(placementId));
   }

   public void markSpawned(String placementId, UUID uuid) {
      if (placementId != null && !placementId.isBlank() && uuid != null) {
         this.placements.put(placementId, uuid);
         this.setDirty();
      }
   }

   public void clear(String placementId) {
      if (this.placements.remove(placementId) != null) {
         this.setDirty();
      }
   }

   public static NPCPlacementSavedData load(CompoundTag tag, Provider registries) {
      NPCPlacementSavedData data = new NPCPlacementSavedData();
      CompoundTag placementsTag = tag.getCompound("placements");

      for (String placementId : placementsTag.getAllKeys()) {
         try {
            data.placements.put(placementId, UUID.fromString(placementsTag.getString(placementId)));
         } catch (IllegalArgumentException var7) {
         }
      }

      return data;
   }

   @NotNull
   public CompoundTag save(@NotNull CompoundTag tag, Provider registries) {
      CompoundTag placementsTag = new CompoundTag();

      for (Entry<String, UUID> entry : this.placements.entrySet()) {
         placementsTag.putString(entry.getKey(), entry.getValue().toString());
      }

      tag.put("placements", placementsTag);
      return tag;
   }
}
