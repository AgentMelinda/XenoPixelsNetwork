package com.dragonminez.server.world.feature;

import java.util.Map.Entry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;

final class FeatureUtil {
   private static final int MAX_GROUND_DEPTH = 64;
   private static final int STRUCTURE_PROTECT_MARGIN = 6;

   private FeatureUtil() {
   }

   static void groundColumn(WorldGenLevel level, BlockPos basePos, BlockState fill) {
      MutableBlockPos cursor = basePos.mutable().move(0, -1, 0);

      for (int depth = 0; depth < 64; depth++) {
         BlockState state = level.getBlockState(cursor);
         if (!state.isAir() && !state.canBeReplaced() && !state.liquid()) {
            break;
         }

         level.setBlock(cursor, fill, 2);
         cursor.move(0, -1, 0);
      }
   }

   static void scatterSacredKaiPlant(WorldGenLevel level, BlockPos pos, RandomSource random) {
      if (level.isEmptyBlock(pos)) {
         if (!(random.nextFloat() < 0.5F)) {
            float t = random.nextFloat();
            BlockState plant;
            if (t < 0.6F) {
               plant = Blocks.SHORT_GRASS.defaultBlockState();
            } else if (t < 0.8F) {
               plant = Blocks.FERN.defaultBlockState();
            } else {
               plant = randomSacredKaiFlower(random);
            }

            level.setBlock(pos, plant, 2);
         }
      }
   }

   private static BlockState randomSacredKaiFlower(RandomSource random) {
      return switch (random.nextInt(5)) {
         case 0 -> Blocks.DANDELION.defaultBlockState();
         case 1 -> Blocks.POPPY.defaultBlockState();
         case 2 -> Blocks.AZURE_BLUET.defaultBlockState();
         case 3 -> Blocks.OXEYE_DAISY.defaultBlockState();
         default -> Blocks.CORNFLOWER.defaultBlockState();
      };
   }

   static boolean isInsideDmzStructure(WorldGenLevel level, BlockPos pos) {
      Registry<Structure> structures = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
      int chunkX = pos.getX() >> 4;
      int chunkZ = pos.getZ() >> 4;

      for (int dx = -1; dx <= 1; dx++) {
         for (int dz = -1; dz <= 1; dz++) {
            ChunkAccess chunk = level.getChunk(chunkX + dx, chunkZ + dz, ChunkStatus.STRUCTURE_STARTS, false);
            if (chunk != null) {
               for (Entry<Structure, StructureStart> entry : chunk.getAllStarts().entrySet()) {
                  StructureStart start = entry.getValue();
                  if (start != null && start.isValid()) {
                     ResourceLocation id = structures.getKey(entry.getKey());
                     if (id != null && "dragonminez".equals(id.getNamespace())) {
                        BoundingBox box = start.getBoundingBox();
                        if (pos.getX() >= box.minX() - 6 && pos.getX() <= box.maxX() + 6 && pos.getZ() >= box.minZ() - 6 && pos.getZ() <= box.maxZ() + 6) {
                           return true;
                        }
                     }
                  }
               }
            }
         }
      }

      return false;
   }
}
