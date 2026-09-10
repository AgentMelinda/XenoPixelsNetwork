package com.dragonminez.server.world.feature;

import com.dragonminez.common.init.MainBlocks;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

public class StoneSpikeFeature extends Feature<NoneFeatureConfiguration> {
   public StoneSpikeFeature(Codec<NoneFeatureConfiguration> codec) {
      super(codec);
   }

   public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
      BlockPos pos = context.origin();
      RandomSource random = context.random();
      WorldGenLevel level = context.level();
      if (FeatureUtil.isInsideDmzStructure(level, pos)) {
         return false;
      } else {
         while (level.isEmptyBlock(pos) && pos.getY() > level.getMinBuildHeight() + 2) {
            pos = pos.below();
         }

         if (!level.getBlockState(pos).is((Block)MainBlocks.ROCKY_STONE.get())
            && !level.getBlockState(pos).is(Blocks.GRAVEL)
            && !level.getBlockState(pos).is(Blocks.COARSE_DIRT)) {
            return false;
         } else {
            pos = pos.above();
            int height = 12 + random.nextInt(19);
            float maxRadius = 4.0F + (float)random.nextInt(3);
            float minRadius = maxRadius * (0.4F + random.nextFloat() * 0.15F);
            if (minRadius < 2.0F) {
               minRadius = 2.0F;
            }

            for (int i = 0; i < height; i++) {
               float relativeHeight = (float)i / (float)height;
               float distFromCenter = Math.abs(relativeHeight - 0.5F) * 2.0F;
               float widthFactor = (float)Math.pow((double)distFromCenter, 1.5);
               float currentRadius = minRadius + (maxRadius - minRadius) * widthFactor;
               if (i == height - 1) {
                  currentRadius *= 0.8F;
               }

               int r = Mth.ceil(currentRadius);

               for (int x = -r; x <= r; x++) {
                  for (int z = -r; z <= r; z++) {
                     double distSq = (double)(x * x + z * z);
                     double maxDistSq = (double)(currentRadius * currentRadius);
                     if (distSq <= maxDistSq + 1.0) {
                        boolean placeBlock = true;
                        boolean isEdge = distSq >= ((double)currentRadius - 1.2) * ((double)currentRadius - 1.2);
                        if (i >= 4 && isEdge && random.nextFloat() < 0.05F) {
                           placeBlock = false;
                        }

                        if (placeBlock) {
                           BlockPos placePos = pos.offset(x, i, z);
                           if (this.canReplace(level, placePos)) {
                              BlockState blockState = ((Block)MainBlocks.ROCKY_STONE.get()).defaultBlockState();
                              if (random.nextInt(4) == 0) {
                                 blockState = ((Block)MainBlocks.ROCKY_COBBLESTONE.get()).defaultBlockState();
                              }

                              this.setBlock(level, placePos, blockState);
                              if (i == 0) {
                                 FeatureUtil.groundColumn(level, placePos, ((Block)MainBlocks.ROCKY_STONE.get()).defaultBlockState());
                              }
                           }
                        }
                     }
                  }
               }
            }

            return true;
         }
      }
   }

   public boolean canReplace(WorldGenLevel level, BlockPos pos) {
      BlockState state = level.getBlockState(pos);
      return state.isAir()
         || state.is(Blocks.DIRT)
         || state.is(Blocks.SNOW)
         || state.is(Blocks.SHORT_GRASS)
         || state.liquid()
         || state.is((Block)MainBlocks.ROCKY_DIRT.get())
         || state.canBeReplaced();
   }
}
