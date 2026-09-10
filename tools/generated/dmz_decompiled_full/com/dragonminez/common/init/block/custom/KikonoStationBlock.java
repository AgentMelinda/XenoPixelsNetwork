package com.dragonminez.common.init.block.custom;

import com.dragonminez.common.init.MainBlockEntities;
import com.dragonminez.common.init.block.entity.KikonoStationBlockEntity;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class KikonoStationBlock extends BaseEntityBlock {
   public static final MapCodec<KikonoStationBlock> CODEC = simpleCodec(KikonoStationBlock::new);
   public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
   public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
   private static final VoxelShape SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, 16.0, 16.0);

   protected MapCodec<? extends BaseEntityBlock> codec() {
      return CODEC;
   }

   public KikonoStationBlock(Properties pProperties) {
      super(pProperties);
      this.registerDefaultState(
         (BlockState)((BlockState)((BlockState)this.stateDefinition.any()).setValue(FACING, Direction.NORTH)).setValue(HALF, DoubleBlockHalf.LOWER)
      );
   }

   protected void createBlockStateDefinition(Builder<Block, BlockState> pBuilder) {
      pBuilder.add(new Property[]{FACING, HALF});
   }

   public VoxelShape getShape(BlockState pState, BlockGetter pLevel, BlockPos pPos, CollisionContext pContext) {
      return SHAPE;
   }

   public RenderShape getRenderShape(BlockState pState) {
      return RenderShape.ENTITYBLOCK_ANIMATED;
   }

   @Nullable
   public BlockState getStateForPlacement(BlockPlaceContext pContext) {
      BlockPos pos = pContext.getClickedPos();
      Level level = pContext.getLevel();
      return pos.getY() < level.getMaxBuildHeight() - 1 && level.getBlockState(pos.above()).canBeReplaced(pContext)
         ? (BlockState)((BlockState)this.defaultBlockState().setValue(FACING, pContext.getHorizontalDirection().getOpposite()))
            .setValue(HALF, DoubleBlockHalf.LOWER)
         : null;
   }

   public void setPlacedBy(Level pLevel, BlockPos pPos, BlockState pState, @Nullable LivingEntity pPlacer, ItemStack pStack) {
      pLevel.setBlock(pPos.above(), (BlockState)pState.setValue(HALF, DoubleBlockHalf.UPPER), 3);
   }

   public BlockState updateShape(BlockState pState, Direction pFacing, BlockState pFacingState, LevelAccessor pLevel, BlockPos pCurrentPos, BlockPos pFacingPos) {
      DoubleBlockHalf half = (DoubleBlockHalf)pState.getValue(HALF);
      if (pFacing.getAxis() == Axis.Y && half == DoubleBlockHalf.LOWER == (pFacing == Direction.UP)) {
         return pFacingState.is(this) && pFacingState.getValue(HALF) != half ? pState : Blocks.AIR.defaultBlockState();
      } else {
         return half == DoubleBlockHalf.LOWER && pFacing == Direction.DOWN && !pState.canSurvive(pLevel, pCurrentPos)
            ? Blocks.AIR.defaultBlockState()
            : super.updateShape(pState, pFacing, pFacingState, pLevel, pCurrentPos, pFacingPos);
      }
   }

   public BlockState playerWillDestroy(Level pLevel, BlockPos pPos, BlockState pState, Player pPlayer) {
      if (!pLevel.isClientSide && pPlayer.isCreative()) {
         preventCreativeDropFromBottomPart(pLevel, pPos, pState, pPlayer);
      }

      return super.playerWillDestroy(pLevel, pPos, pState, pPlayer);
   }

   protected static void preventCreativeDropFromBottomPart(Level pLevel, BlockPos pPos, BlockState pState, Player pPlayer) {
      DoubleBlockHalf half = (DoubleBlockHalf)pState.getValue(HALF);
      if (half == DoubleBlockHalf.UPPER) {
         BlockPos belowPos = pPos.below();
         BlockState belowState = pLevel.getBlockState(belowPos);
         if (belowState.is(pState.getBlock()) && belowState.getValue(HALF) == DoubleBlockHalf.LOWER) {
            pLevel.setBlock(belowPos, Blocks.AIR.defaultBlockState(), 35);
            pLevel.levelEvent(pPlayer, 2001, belowPos, Block.getId(belowState));
         }
      }
   }

   public void onRemove(BlockState pState, Level pLevel, BlockPos pPos, BlockState pNewState, boolean pMovedByPiston) {
      if (pState.getBlock() != pNewState.getBlock()
         && pState.getValue(HALF) == DoubleBlockHalf.LOWER
         && pLevel.getBlockEntity(pPos) instanceof KikonoStationBlockEntity station) {
         station.drops();
      }

      super.onRemove(pState, pLevel, pPos, pNewState, pMovedByPiston);
   }

   public InteractionResult useWithoutItem(BlockState pState, Level pLevel, BlockPos pPos, Player pPlayer, BlockHitResult pHit) {
      if (!pLevel.isClientSide()) {
         BlockPos targetPos = pState.getValue(HALF) == DoubleBlockHalf.UPPER ? pPos.below() : pPos;
         if (!(pLevel.getBlockEntity(targetPos) instanceof KikonoStationBlockEntity station)) {
            throw new IllegalStateException("Container provider missing at " + targetPos);
         }

         if (pPlayer instanceof ServerPlayer sp) {
            sp.openMenu(station, buf -> buf.writeBlockPos(targetPos));
         }
      }

      return InteractionResult.sidedSuccess(pLevel.isClientSide());
   }

   @Nullable
   public BlockEntity newBlockEntity(BlockPos pPos, BlockState pState) {
      return pState.getValue(HALF) == DoubleBlockHalf.LOWER ? new KikonoStationBlockEntity(pPos, pState) : null;
   }

   @Nullable
   public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level pLevel, BlockState pState, BlockEntityType<T> pBlockEntityType) {
      return !pLevel.isClientSide() && pState.getValue(HALF) != DoubleBlockHalf.UPPER
         ? createTickerHelper(
            pBlockEntityType,
            (BlockEntityType)MainBlockEntities.KIKONO_STATION_BE.get(),
            (pLevel1, pPos, pState1, pBlockEntity) -> pBlockEntity.tick(pLevel1, pPos, pState1)
         )
         : null;
   }
}
