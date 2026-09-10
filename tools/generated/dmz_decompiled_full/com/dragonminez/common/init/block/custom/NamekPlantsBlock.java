package com.dragonminez.common.init.block.custom;

import com.dragonminez.common.init.MainBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FlowerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;

public class NamekPlantsBlock extends FlowerBlock {
   public NamekPlantsBlock(Holder<MobEffect> effect, float seconds, Properties properties) {
      super(effect, seconds, properties);
   }

   protected boolean mayPlaceOn(BlockState pState, BlockGetter pLevel, BlockPos pPos) {
      return pState.is((Block)MainBlocks.NAMEK_GRASS_BLOCK.get()) || pState.is((Block)MainBlocks.NAMEK_SACRED_GRASS_BLOCK.get());
   }
}
