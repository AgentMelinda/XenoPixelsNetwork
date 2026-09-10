package com.dragonminez.server.world.structure.placement;

import com.dragonminez.Env;
import com.dragonminez.LogUtil;
import com.dragonminez.common.config.ConfigManager;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.StructureSet.StructureSelectionEntry;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;

public final class StructureRepairManager {
   private static final int CHECK_INTERVAL_TICKS = 100;
   private static final int MAX_RELOCATIONS = 2;
   private static final Set<String> HANDLED = ConcurrentHashMap.newKeySet();
   private static final Map<String, Integer> RELOCATIONS = new ConcurrentHashMap<>();

   private StructureRepairManager() {
   }

   public static void reset() {
      HANDLED.clear();
      RELOCATIONS.clear();
   }

   public static void tick(ServerLevel level) {
      if (level.getGameTime() % 100L == 0L) {
         if (!level.players().isEmpty()) {
            if (ConfigManager.getServerConfig().getWorldGen().getGenerateCustomStructures()) {
               Map<Integer, ChunkPos> positions = StructureSpawnPlanner.publishedPositions(level);
               if (!positions.isEmpty()) {
                  Map<Integer, Holder<Structure>> structuresBySalt = null;

                  for (Entry<Integer, ChunkPos> entry : positions.entrySet()) {
                     int salt = entry.getKey();
                     ChunkPos pos = entry.getValue();
                     String saltKey = level.dimension().location() + "#" + salt;
                     String key = saltKey + "@" + pos.toLong();
                     if (!HANDLED.contains(key)) {
                        LevelChunk chunk = level.getChunkSource().getChunkNow(pos.x, pos.z);
                        if (chunk != null) {
                           if (structuresBySalt == null) {
                              structuresBySalt = structuresBySalt(level);
                           }

                           Holder<Structure> structure = structuresBySalt.get(salt);
                           if (structure == null) {
                              HANDLED.add(key);
                           } else {
                              StructureStart start = chunk.getStartForStructure((Structure)structure.value());
                              if (start != null && start.isValid()) {
                                 HANDLED.add(key);
                              } else {
                                 String name = structure.unwrapKey().map(k -> k.location().toString()).orElse("salt:" + salt);
                                 if (forcePlace(level, (Structure)structure.value(), pos)) {
                                    HANDLED.add(key);
                                    LogUtil.info(
                                       Env.SERVER,
                                       "[DMZ] Materialized missing structure "
                                          + name
                                          + " at chunk "
                                          + pos.x
                                          + ", "
                                          + pos.z
                                          + " in "
                                          + level.dimension().location()
                                    );
                                 } else {
                                    HANDLED.add(key);
                                    int attempts = RELOCATIONS.merge(saltKey, 1, Integer::sum);
                                    if (attempts <= 2) {
                                       LogUtil.info(Env.SERVER, "[DMZ] Could not materialize " + name + " at chunk " + pos.x + ", " + pos.z + "; relocating.");
                                       StructureSpawnPlanner.relocate(level, salt);
                                    } else {
                                       LogUtil.error(Env.SERVER, "[DMZ] Giving up on relocating " + name + " after 2 attempts this session.");
                                    }
                                 }
                              }
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private static Map<Integer, Holder<Structure>> structuresBySalt(ServerLevel level) {
      Map<Integer, Holder<Structure>> result = new HashMap<>();

      for (Holder<StructureSet> holder : level.getChunkSource().getGeneratorState().possibleStructureSets()) {
         StructureSet set = (StructureSet)holder.value();
         StructurePlacement var6 = set.placement();
         if (var6 instanceof BiomeAwareUniquePlacement) {
            BiomeAwareUniquePlacement placement = (BiomeAwareUniquePlacement)var6;
            if (!set.structures().isEmpty()) {
               result.put(placement.placementSalt(), ((StructureSelectionEntry)set.structures().get(0)).structure());
            }
         }
      }

      return result;
   }

   private static boolean forcePlace(ServerLevel level, Structure structure, ChunkPos chunkPos) {
      try {
         ChunkGenerator generator = level.getChunkSource().getGenerator();
         StructureStart start = structure.generate(
            level.registryAccess(),
            generator,
            generator.getBiomeSource(),
            level.getChunkSource().randomState(),
            level.getStructureManager(),
            level.getSeed(),
            chunkPos,
            0,
            level,
            biome -> true
         );
         if (!start.isValid()) {
            return false;
         } else {
            BoundingBox box = start.getBoundingBox();
            ChunkPos min = new ChunkPos(SectionPos.blockToSectionCoord(box.minX()), SectionPos.blockToSectionCoord(box.minZ()));
            ChunkPos max = new ChunkPos(SectionPos.blockToSectionCoord(box.maxX()), SectionPos.blockToSectionCoord(box.maxZ()));
            ChunkPos.rangeClosed(min, max)
               .forEach(
                  p -> {
                     level.getChunk(p.x, p.z);
                     start.placeInChunk(
                        level,
                        level.structureManager(),
                        generator,
                        level.getRandom(),
                        new BoundingBox(
                           p.getMinBlockX(), level.getMinBuildHeight(), p.getMinBlockZ(), p.getMaxBlockX(), level.getMaxBuildHeight(), p.getMaxBlockZ()
                        ),
                        p
                     );
                  }
               );
            level.getChunk(chunkPos.x, chunkPos.z).setStartForStructure(structure, start);
            ChunkPos.rangeClosed(min, max).forEach(p -> level.getChunk(p.x, p.z).addReferenceForStructure(structure, chunkPos.toLong()));
            return true;
         }
      } catch (Exception var8) {
         LogUtil.error(Env.SERVER, "[DMZ] Force-placing structure at chunk " + chunkPos.x + ", " + chunkPos.z + " failed: " + var8.getMessage());
         return false;
      }
   }
}
