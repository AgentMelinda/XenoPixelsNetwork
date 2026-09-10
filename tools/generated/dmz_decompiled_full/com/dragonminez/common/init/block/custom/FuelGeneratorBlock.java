package com.dragonminez.common.init.block.custom;

import com.dragonminez.common.init.MainBlockEntities;
import com.dragonminez.common.init.block.entity.FuelGeneratorBlockEntity;
import com.dragonminez.common.init.block.entity.KikonoStationBlockEntity;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public class FuelGeneratorBlock extends BaseEntityBlock {
   public static final MapCodec<FuelGeneratorBlock> CODEC = simpleCodec(FuelGeneratorBlock::new);
   public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
   public static final BooleanProperty LIT = BlockStateProperties.LIT;

   protected MapCodec<? extends BaseEntityBlock> codec() {
      return CODEC;
   }

   public FuelGeneratorBlock(Properties pProperties) {
      super(pProperties);
      this.registerDefaultState((BlockState)((BlockState)((BlockState)this.stateDefinition.any()).setValue(FACING, Direction.NORTH)).setValue(LIT, false));
   }

   public RenderShape getRenderShape(BlockState pState) {
      return RenderShape.ENTITYBLOCK_ANIMATED;
   }

   protected void createBlockStateDefinition(Builder<Block, BlockState> pBuilder) {
      pBuilder.add(new Property[]{FACING, LIT});
   }

   @Nullable
   public BlockState getStateForPlacement(BlockPlaceContext pContext) {
      return (BlockState)this.defaultBlockState().setValue(FACING, pContext.getHorizontalDirection().getOpposite());
   }

   public BlockState rotate(BlockState pState, Rotation pRotation) {
      return (BlockState)pState.setValue(FACING, pRotation.rotate((Direction)pState.getValue(FACING)));
   }

   public BlockState mirror(BlockState pState, Mirror pMirror) {
      return pState.rotate(pMirror.getRotation((Direction)pState.getValue(FACING)));
   }

   public InteractionResult useWithoutItem(BlockState pState, Level pLevel, BlockPos pPos, Player pPlayer, BlockHitResult pHit) {
      if (!pLevel.isClientSide() && pLevel.getBlockEntity(pPos) instanceof FuelGeneratorBlockEntity generator && pPlayer instanceof ServerPlayer sp) {
         sp.openMenu(generator, buf -> buf.writeBlockPos(pPos));
      }

      return InteractionResult.sidedSuccess(pLevel.isClientSide());
   }

   public void onRemove(BlockState pState, Level pLevel, BlockPos pPos, BlockState pNewState, boolean pMovedByPiston) {
      if (pState.getBlock() != pNewState.getBlock() && pLevel.getBlockEntity(pPos) instanceof KikonoStationBlockEntity station) {
         station.drops();
      }

      super.onRemove(pState, pLevel, pPos, pNewState, pMovedByPiston);
   }

   @Nullable
   public BlockEntity newBlockEntity(BlockPos pPos, BlockState pState) {
      return new FuelGeneratorBlockEntity(pPos, pState);
   }

   @Nullable
   public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level pLevel, BlockState pState, BlockEntityType<T> pBlockEntityType) {
      return pLevel.isClientSide()
         ? null
         : createTickerHelper(
            pBlockEntityType,
            (BlockEntityType)MainBlockEntities.FUEL_GENERATOR_BE.get(),
            (pLevel1, pPos, pState1, pBlockEntity) -> pBlockEntity.tick(pLevel1, pPos, pState1)
         );
   }

   public void animateTick(BlockState pState, Level pLevel, BlockPos pPos, RandomSource pRandom) {
      if ((Boolean)pState.getValue(LIT)) {
         if (pRandom.nextDouble() < 0.1) {
            pLevel.playLocalSound(
               (double)pPos.getX() + 0.5,
               (double)pPos.getY(),
               (double)pPos.getZ() + 0.5,
               SoundEvents.FURNACE_FIRE_CRACKLE,
               SoundSource.BLOCKS,
               1.0F,
               1.0F,
               false
            );
         }

         double d0 = (double)pPos.getX() + 0.5;
         double d1 = (double)pPos.getY();
         double d2 = (double)pPos.getZ() + 0.5;
         if (pRandom.nextDouble() < 0.1) {
            pLevel.addParticle(ParticleTypes.SMOKE, d0, d1 + 1.1, d2, 0.0, 0.0, 0.0);
         }
      }
   }
}
