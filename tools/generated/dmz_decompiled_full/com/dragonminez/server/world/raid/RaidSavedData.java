package com.dragonminez.server.world.raid;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedData.Factory;
import net.minecraft.world.level.storage.DimensionDataStorage;

public class RaidSavedData extends SavedData {
   private static final String FILE_NAME = "dragonminez_raids";
   private final Map<UUID, Raid> raids = new HashMap<>();

   public static RaidSavedData get(MinecraftServer server) {
      DimensionDataStorage storage = server.getLevel(Level.OVERWORLD).getDataStorage();
      return (RaidSavedData)storage.computeIfAbsent(new Factory(RaidSavedData::new, RaidSavedData::load), "dragonminez_raids");
   }

   public static RaidSavedData load(CompoundTag tag, Provider registries) {
      RaidSavedData data = new RaidSavedData();
      ListTag list = tag.getList("Raids", 10);

      for (int i = 0; i < list.size(); i++) {
         Raid raid = Raid.load(list.getCompound(i));
         if (!raid.isFinished()) {
            data.raids.put(raid.getRaidId(), raid);
         }
      }

      return data;
   }

   public CompoundTag save(CompoundTag tag, Provider registries) {
      ListTag list = new ListTag();

      for (Raid raid : this.raids.values()) {
         if (!raid.isFinished()) {
            list.add(raid.save());
         }
      }

      tag.put("Raids", list);
      return tag;
   }

   public void addRaid(Raid raid) {
      this.raids.put(raid.getRaidId(), raid);
      this.setDirty();
   }

   public Collection<Raid> getRaids() {
      return this.raids.values();
   }

   public Raid getRaid(UUID id) {
      return this.raids.get(id);
   }

   public void tick(ServerLevel level) {
      if (!this.raids.isEmpty()) {
         boolean changed = false;
         Iterator<Entry<UUID, Raid>> it = this.raids.entrySet().iterator();

         while (it.hasNext()) {
            Raid raid = it.next().getValue();
            if (raid.getDimension().equals(level.dimension())) {
               raid.tick(level);
               if (raid.isFinished()) {
                  it.remove();
               }

               changed = true;
            }
         }

         if (changed) {
            this.setDirty();
         }
      }
   }

   public Raid findNearbyRaid(ServerLevel level, BlockPos pos, double range) {
      Raid best = null;
      double bestSqr = range * range;

      for (Raid raid : this.raids.values()) {
         if (!raid.isFinished() && raid.getDimension().equals(level.dimension())) {
            double d = raid.getCenter().distSqr(pos);
            if (d <= bestSqr) {
               bestSqr = d;
               best = raid;
            }
         }
      }

      return best;
   }
}
