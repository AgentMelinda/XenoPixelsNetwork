package com.dragonminez.server.world.gen;

import com.dragonminez.common.init.MainBlocks;
import com.dragonminez.server.world.biome.OverworldBiomes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.SurfaceRules;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import net.minecraft.world.level.levelgen.SurfaceRules.ConditionSource;
import net.minecraft.world.level.levelgen.SurfaceRules.RuleSource;

public class OverworldSurfaceRules {
   private static final RuleSource ROCKY_DIRT = SurfaceRules.state(((Block)MainBlocks.ROCKY_DIRT.get()).defaultBlockState());
   private static final RuleSource ROCKY_STONE = SurfaceRules.state(((Block)MainBlocks.ROCKY_STONE.get()).defaultBlockState());
   private static final RuleSource BEDROCK = SurfaceRules.state(Blocks.BEDROCK.defaultBlockState());

   public static RuleSource makeRules() {
      ConditionSource isRockyWasteland = SurfaceRules.isBiome(new ResourceKey[]{OverworldBiomes.ROCKY});
      return SurfaceRules.sequence(
         new RuleSource[]{
            SurfaceRules.ifTrue(SurfaceRules.verticalGradient("bedrock_floor", VerticalAnchor.bottom(), VerticalAnchor.aboveBottom(5)), BEDROCK),
            SurfaceRules.ifTrue(
               isRockyWasteland,
               SurfaceRules.ifTrue(
                  SurfaceRules.yBlockCheck(VerticalAnchor.absolute(50), 1),
                  SurfaceRules.sequence(
                     new RuleSource[]{
                        SurfaceRules.ifTrue(SurfaceRules.ON_FLOOR, ROCKY_DIRT),
                        SurfaceRules.ifTrue(SurfaceRules.UNDER_FLOOR, ROCKY_STONE),
                        SurfaceRules.ifTrue(SurfaceRules.DEEP_UNDER_FLOOR, ROCKY_STONE)
                     }
                  )
               )
            )
         }
      );
   }
}
