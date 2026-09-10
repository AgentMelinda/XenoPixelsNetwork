package com.dragonminez.server.world.structure.helper;

import com.dragonminez.server.world.structure.placement.BiomeAwareUniquePlacement;
import com.dragonminez.server.world.structure.placement.FixedStructurePlacement;
import com.dragonminez.server.world.structure.placement.UniqueNearSpawnPlacement;
import com.mojang.datafixers.util.Pair;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureSet.StructureSelectionEntry;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;

public class StructureLocator {
   @Nullable
   public static BlockPos locateStructure(ServerLevel level, ResourceKey<Structure> structureKey, BlockPos searchFrom) {
      Registry<Structure> structureRegistry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
      Registry<StructureSet> structureSetRegistry = level.registryAccess().registryOrThrow(Registries.STRUCTURE_SET);
      List<StructurePlacement> placements = new ArrayList<>();

      for (Entry<ResourceKey<StructureSet>, StructureSet> entry : structureSetRegistry.entrySet()) {
         StructureSet set = entry.getValue();

         for (StructureSelectionEntry structureEntry : set.structures()) {
            if (structureEntry.structure().is(structureKey)) {
               placements.add(set.placement());
               break;
            }
         }
      }

      if (placements.isEmpty()) {
         return null;
      } else {
         BlockPos best = null;
         double bestDist = Double.MAX_VALUE;

         for (StructurePlacement placement : placements) {
            BlockPos pos = getPositionFromPlacement(level, structureKey, structureRegistry, placement);
            if (pos != null) {
               double dist = searchFrom.distSqr(pos);
               if (best == null || dist < bestDist) {
                  best = pos;
                  bestDist = dist;
               }
            }
         }

         if (best != null) {
            return best;
         } else {
            HolderSet<Structure> holderSet = HolderSet.direct(new Holder[]{structureRegistry.getHolderOrThrow(structureKey)});
            Pair<BlockPos, Holder<Structure>> searchResult = level.getChunkSource()
               .getGenerator()
               .findNearestMapStructure(level, holderSet, searchFrom, 100, false);
            if (searchResult != null) {
               best = (BlockPos)searchResult.getFirst();
            }

            return best;
         }
      }
   }

   @Nullable
   private static BlockPos getPositionFromPlacement(
      ServerLevel level, ResourceKey<Structure> structureKey, Registry<Structure> structureRegistry, StructurePlacement placement
   ) {
      if (placement instanceof BiomeAwareUniquePlacement uniquePlacement) {
         ChunkPos chunkPos = uniquePlacement.getStructureChunk(
            level.getSeed(),
            level.getChunkSource().getGenerator().getBiomeSource(),
            level.getChunkSource().randomState(),
            level.getChunkSource().getGeneratorState()
         );
         if (chunkPos != null) {
            return new BlockPos(chunkPos.getMiddleBlockX(), 90, chunkPos.getMiddleBlockZ());
         }
      } else {
         if (placement instanceof FixedStructurePlacement fixedPlacement) {
            int x = (fixedPlacement.getFixedX() << 4) + 8;
            int z = (fixedPlacement.getFixedZ() << 4) + 8;
            return new BlockPos(x, 30, z);
         }

         if (placement instanceof UniqueNearSpawnPlacement spawnPlacement) {
            ChunkPos chunkPos = spawnPlacement.getStructureChunk(level.getSeed());
            return new BlockPos(chunkPos.getMiddleBlockX(), 90, chunkPos.getMiddleBlockZ());
         }
      }

      return null;
   }

   public static int getDistanceTo(BlockPos from, BlockPos to) {
      return (int)Math.sqrt(from.distSqr(to));
   }

   public static boolean usesCustomPlacement(ServerLevel level, ResourceKey<Structure> structureKey) {
      Registry<StructureSet> structureSetRegistry = level.registryAccess().registryOrThrow(Registries.STRUCTURE_SET);

      for (Entry<ResourceKey<StructureSet>, StructureSet> entry : structureSetRegistry.entrySet()) {
         StructureSet set = entry.getValue();

         for (StructureSelectionEntry structureEntry : set.structures()) {
            if (structureEntry.structure().is(structureKey)) {
               StructurePlacement placement = set.placement();
               if (placement instanceof BiomeAwareUniquePlacement
                  || placement instanceof FixedStructurePlacement
                  || placement instanceof UniqueNearSpawnPlacement) {
                  return true;
               }
            }
         }
      }

      return false;
   }
}
