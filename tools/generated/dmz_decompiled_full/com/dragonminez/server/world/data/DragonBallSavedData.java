package com.dragonminez.server.world.data;

import com.dragonminez.common.dragonball.DragonBallDefinitions;
import com.dragonminez.common.dragonball.DragonBallSetDefinition;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedData.Factory;
import org.jetbrains.annotations.NotNull;

public class DragonBallSavedData extends SavedData {
   private final Map<String, Map<Integer, List<BlockPos>>> activeBallsBySet = new HashMap<>();
   private final Map<String, Map<Integer, List<BlockPos>>> pendingBallsBySet = new HashMap<>();
   private final Set<String> firstSpawnedSetIds = new HashSet<>();

   public DragonBallSavedData() {
      for (DragonBallSetDefinition definition : DragonBallDefinitions.getBallSets()) {
         this.activeBallsBySet.put(definition.getId(), createStarMap(definition));
         this.pendingBallsBySet.put(definition.getId(), createStarMap(definition));
      }
   }

   private static Map<Integer, List<BlockPos>> createStarMap(DragonBallSetDefinition definition) {
      Map<Integer, List<BlockPos>> map = new HashMap<>();

      for (int star : definition.getStars()) {
         map.put(star, new ArrayList<>());
      }

      return map;
   }

   private static Map<Integer, List<BlockPos>> createStarMapForSetId(String setId) {
      DragonBallSetDefinition definition = DragonBallDefinitions.getBallSet(setId);
      return (Map<Integer, List<BlockPos>>)(definition == null ? new HashMap<>() : createStarMap(definition));
   }

   public static DragonBallSavedData get(ServerLevel level) {
      return (DragonBallSavedData)level.getDataStorage().computeIfAbsent(new Factory(DragonBallSavedData::new, DragonBallSavedData::load), "dragon_balls_data");
   }

   public Map<Integer, List<BlockPos>> getActiveBalls(String setId) {
      return this.activeBallsBySet.computeIfAbsent(setId, DragonBallSavedData::createStarMapForSetId);
   }

   public Map<Integer, List<BlockPos>> getPendingBalls(String setId) {
      return this.pendingBallsBySet.computeIfAbsent(setId, DragonBallSavedData::createStarMapForSetId);
   }

   public List<BlockPos> getAllKnownPositionsForRadar(String setId) {
      List<BlockPos> allPos = new ArrayList<>();
      DragonBallSetDefinition definition = DragonBallDefinitions.getBallSet(setId);
      if (definition == null) {
         return allPos;
      } else {
         Map<Integer, List<BlockPos>> active = this.getActiveBalls(setId);
         Map<Integer, List<BlockPos>> pending = this.getPendingBalls(setId);

         for (int star : definition.getStars()) {
            allPos.addAll(active.getOrDefault(star, List.of()));
            allPos.addAll(pending.getOrDefault(star, List.of()));
         }

         return allPos;
      }
   }

   public boolean isFirstSpawnComplete(String setId) {
      return this.firstSpawnedSetIds.contains(setId);
   }

   public void setFirstSpawnComplete(String setId, boolean value) {
      if (value) {
         this.firstSpawnedSetIds.add(setId);
      } else {
         this.firstSpawnedSetIds.remove(setId);
      }

      this.setDirty();
   }

   public static DragonBallSavedData load(CompoundTag tag, Provider registries) {
      DragonBallSavedData data = new DragonBallSavedData();
      if (tag.contains("SetData")) {
         CompoundTag setData = tag.getCompound("SetData");

         for (String setId : setData.getAllKeys()) {
            CompoundTag entry = setData.getCompound(setId);
            loadMap(entry.getList("Active", 10), data.getActiveBalls(setId));
            loadMap(entry.getList("Pending", 10), data.getPendingBalls(setId));
            if (entry.getBoolean("FirstSpawned")) {
               data.firstSpawnedSetIds.add(setId);
            }
         }
      } else {
         loadLegacySet(tag, data, "earth", "ActiveEarth", "PendingEarth", "FirstSpawnEarth");
         loadLegacySet(tag, data, "namek", "ActiveNamek", "PendingNamek", "FirstSpawnNamek");
      }

      return data;
   }

   private static void loadLegacySet(CompoundTag tag, DragonBallSavedData data, String setId, String activeKey, String pendingKey, String firstKey) {
      if (tag.contains(activeKey)) {
         loadMap(tag.getList(activeKey, 10), data.getActiveBalls(setId));
      }

      if (tag.contains(pendingKey)) {
         loadMap(tag.getList(pendingKey, 10), data.getPendingBalls(setId));
      }

      if (tag.getBoolean(firstKey)) {
         data.firstSpawnedSetIds.add(setId);
      }
   }

   @NotNull
   public CompoundTag save(CompoundTag tag, Provider registries) {
      CompoundTag setData = new CompoundTag();

      for (DragonBallSetDefinition definition : DragonBallDefinitions.getBallSets()) {
         String setId = definition.getId();
         CompoundTag entry = new CompoundTag();
         entry.put("Active", saveMap(this.getActiveBalls(setId)));
         entry.put("Pending", saveMap(this.getPendingBalls(setId)));
         entry.putBoolean("FirstSpawned", this.firstSpawnedSetIds.contains(setId));
         setData.put(setId, entry);
      }

      tag.put("SetData", setData);
      return tag;
   }

   private static void loadMap(ListTag list, Map<Integer, List<BlockPos>> map) {
      for (int i = 0; i < list.size(); i++) {
         CompoundTag item = list.getCompound(i);
         int star = item.getInt("Star");
         BlockPos pos = NbtUtils.readBlockPos(item, "Pos").orElse(BlockPos.ZERO);
         map.computeIfAbsent(star, ignored -> new ArrayList<>()).add(pos);
      }
   }

   private static ListTag saveMap(Map<Integer, List<BlockPos>> map) {
      ListTag list = new ListTag();

      for (Entry<Integer, List<BlockPos>> entry : map.entrySet()) {
         for (BlockPos pos : entry.getValue()) {
            CompoundTag item = new CompoundTag();
            item.putInt("Star", entry.getKey());
            item.put("Pos", NbtUtils.writeBlockPos(pos));
            list.add(item);
         }
      }

      return list;
   }
}
