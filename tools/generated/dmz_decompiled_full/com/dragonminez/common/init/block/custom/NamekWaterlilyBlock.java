package com.dragonminez.common.init.block.custom;

import com.dragonminez.common.init.MainFluids;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class NamekWaterlilyBlock extends BushBlock {
   public static final MapCodec<NamekWaterlilyBlock> CODEC = simpleCodec(NamekWaterlilyBlock::new);
   protected static final VoxelShape AABB = Block.box(1.0, 0.0, 1.0, 15.0, 1.5, 15.0);

   protected MapCodec<? extends BushBlock> codec() {
      return CODEC;
   }

   public NamekWaterlilyBlock(Properties pProperties) {
      super(pProperties);
   }

   public void entityInside(BlockState pState, Level pLevel, BlockPos pPos, Entity pEntity) {
      super.entityInside(pState, pLevel, pPos, pEntity);
      if (pLevel instanceof ServerLevel && pEntity instanceof Boat) {
         pLevel.destroyBlock(pPos, true, pEntity);
      }
   }

   public VoxelShape getShape(BlockState pState, BlockGetter pLevel, BlockPos pPos, CollisionContext pContext) {
      return AABB;
   }

   public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
      BlockPos blockBelow = pos.below();
      FluidState fluidStateBelow = level.getFluidState(blockBelow);
      return fluidStateBelow.is(FluidTags.WATER) || fluidStateBelow.getType() == MainFluids.SOURCE_NAMEK.get();
   }

   protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
      FluidState fluidStateBelow = level.getFluidState(pos);
      return fluidStateBelow.is(FluidTags.WATER) || fluidStateBelow.getType() == MainFluids.SOURCE_NAMEK.get();
   }

   public BlockState getStateForPlacement(BlockPlaceContext context) {
      Level level = context.getLevel();
      BlockPos pos = context.getClickedPos();
      BlockPos posBelow = pos.below();
      FluidState fluidState = level.getFluidState(posBelow);
      return !fluidState.is(FluidTags.WATER) && fluidState.getType() != MainFluids.SOURCE_NAMEK.get() ? null : this.defaultBlockState();
   }
}
