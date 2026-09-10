package com.dragonminez.common.init.block.custom;

import com.dragonminez.common.init.MainBlocks;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.neoforged.neoforge.common.ItemAbility;

public class FlammableRotatedPillarBlock extends RotatedPillarBlock {
   public FlammableRotatedPillarBlock(Properties properties) {
      super(properties);
   }

   public boolean isFlammable(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
      return true;
   }

   public int getFlammability(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
      return 5;
   }

   public int getFireSpreadSpeed(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
      return 5;
   }

   @Nullable
   public BlockState getToolModifiedState(BlockState state, UseOnContext context, ItemAbility toolAction, boolean simulate) {
      if (context.getItemInHand().getItem() instanceof AxeItem) {
         if (state.is((Block)MainBlocks.NAMEK_AJISSA_WOOD.get())) {
            return (BlockState)((Block)MainBlocks.NAMEK_STRIPPED_AJISSA_WOOD.get()).defaultBlockState().setValue(AXIS, (Axis)state.getValue(AXIS));
         }

         if (state.is((Block)MainBlocks.NAMEK_SACRED_WOOD.get())) {
            return (BlockState)((Block)MainBlocks.NAMEK_STRIPPED_SACRED_WOOD.get()).defaultBlockState().setValue(AXIS, (Axis)state.getValue(AXIS));
         }
      }

      return super.getToolModifiedState(state, context, toolAction, simulate);
   }
}
