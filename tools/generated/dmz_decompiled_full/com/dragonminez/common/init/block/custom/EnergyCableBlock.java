package com.dragonminez.common.init.block.custom;

import com.dragonminez.common.init.MainBlockEntities;
import com.dragonminez.common.init.block.entity.EnergyCableBlockEntity;
import com.mojang.serialization.MapCodec;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage;
import org.jetbrains.annotations.Nullable;

public class EnergyCableBlock extends BaseEntityBlock {
   public static final MapCodec<EnergyCableBlock> CODEC = simpleCodec(EnergyCableBlock::new);
   public static final BooleanProperty NORTH = BooleanProperty.create("north");
   public static final BooleanProperty EAST = BooleanProperty.create("east");
   public static final BooleanProperty SOUTH = BooleanProperty.create("south");
   public static final BooleanProperty WEST = BooleanProperty.create("west");
   public static final BooleanProperty UP = BooleanProperty.create("up");
   public static final BooleanProperty DOWN = BooleanProperty.create("down");
   public static final Map<Direction, BooleanProperty> PROPERTY_BY_DIRECTION = new EnumMap<>(Direction.class);
   private static final VoxelShape SHAPE_CENTER = Block.box(5.0, 5.0, 5.0, 11.0, 11.0, 11.0);
   private static final VoxelShape SHAPE_NORTH = Block.box(5.0, 5.0, 0.0, 11.0, 11.0, 5.0);
   private static final VoxelShape SHAPE_SOUTH = Block.box(5.0, 5.0, 11.0, 11.0, 11.0, 16.0);
   private static final VoxelShape SHAPE_EAST = Block.box(11.0, 5.0, 5.0, 16.0, 11.0, 11.0);
   private static final VoxelShape SHAPE_WEST = Block.box(0.0, 5.0, 5.0, 5.0, 11.0, 11.0);
   private static final VoxelShape SHAPE_UP = Block.box(5.0, 11.0, 5.0, 11.0, 16.0, 11.0);
   private static final VoxelShape SHAPE_DOWN = Block.box(5.0, 0.0, 5.0, 11.0, 5.0, 11.0);

   protected MapCodec<? extends BaseEntityBlock> codec() {
      return CODEC;
   }

   public EnergyCableBlock(Properties pProperties) {
      super(pProperties);
      this.registerDefaultState(
         (BlockState)((BlockState)((BlockState)((BlockState)((BlockState)((BlockState)((BlockState)this.stateDefinition.any()).setValue(NORTH, false))
                        .setValue(EAST, false))
                     .setValue(SOUTH, false))
                  .setValue(WEST, false))
               .setValue(UP, false))
            .setValue(DOWN, false)
      );
   }

   protected void createBlockStateDefinition(Builder<Block, BlockState> pBuilder) {
      pBuilder.add(new Property[]{NORTH, EAST, SOUTH, WEST, UP, DOWN});
   }

   public VoxelShape getShape(BlockState pState, BlockGetter pLevel, BlockPos pPos, CollisionContext pContext) {
      VoxelShape shape = SHAPE_CENTER;
      if ((Boolean)pState.getValue(NORTH)) {
         shape = Shapes.or(shape, SHAPE_NORTH);
      }

      if ((Boolean)pState.getValue(SOUTH)) {
         shape = Shapes.or(shape, SHAPE_SOUTH);
      }

      if ((Boolean)pState.getValue(EAST)) {
         shape = Shapes.or(shape, SHAPE_EAST);
      }

      if ((Boolean)pState.getValue(WEST)) {
         shape = Shapes.or(shape, SHAPE_WEST);
      }

      if ((Boolean)pState.getValue(UP)) {
         shape = Shapes.or(shape, SHAPE_UP);
      }

      if ((Boolean)pState.getValue(DOWN)) {
         shape = Shapes.or(shape, SHAPE_DOWN);
      }

      return shape;
   }

   @Nullable
   public BlockState getStateForPlacement(BlockPlaceContext pContext) {
      return this.makeConnections(pContext.getLevel(), pContext.getClickedPos());
   }

   public BlockState updateShape(
      BlockState pState, Direction pDirection, BlockState pNeighborState, LevelAccessor pLevel, BlockPos pCurrentPos, BlockPos pNeighborPos
   ) {
      return pLevel instanceof Level level
         ? this.makeConnections(level, pCurrentPos)
         : super.updateShape(pState, pDirection, pNeighborState, pLevel, pCurrentPos, pNeighborPos);
   }

   public BlockState makeConnections(Level level, BlockPos pos) {
      BlockState state = this.defaultBlockState();

      for (Direction dir : Direction.values()) {
         state = (BlockState)state.setValue((Property)PROPERTY_BY_DIRECTION.get(dir), this.canConnectTo(level, pos.relative(dir), dir.getOpposite()));
      }

      return state;
   }

   private boolean canConnectTo(Level level, BlockPos pos, Direction side) {
      BlockState state = level.getBlockState(pos);
      BlockEntity be = level.getBlockEntity(pos);
      if (state.getBlock() instanceof EnergyCableBlock) {
         return true;
      } else {
         if (be != null) {
            ResourceLocation key = BuiltInRegistries.BLOCK.getKey(state.getBlock());
            if (key != null && key.getNamespace().equals("dragonminez")) {
               return level.getCapability(EnergyStorage.BLOCK, pos, side) != null;
            }
         }

         return false;
      }
   }

   public RenderShape getRenderShape(BlockState pState) {
      return RenderShape.ENTITYBLOCK_ANIMATED;
   }

   @Nullable
   public BlockEntity newBlockEntity(BlockPos pPos, BlockState pState) {
      return new EnergyCableBlockEntity(pPos, pState);
   }

   @Nullable
   public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level pLevel, BlockState pState, BlockEntityType<T> pBlockEntityType) {
      return pLevel.isClientSide()
         ? null
         : createTickerHelper(
            pBlockEntityType,
            (BlockEntityType)MainBlockEntities.ENERGY_CABLE_BE.get(),
            (pLevel1, pPos, pState1, pBlockEntity) -> pBlockEntity.tick(pLevel1, pPos, pState1)
         );
   }

   static {
      PROPERTY_BY_DIRECTION.put(Direction.NORTH, NORTH);
      PROPERTY_BY_DIRECTION.put(Direction.EAST, EAST);
      PROPERTY_BY_DIRECTION.put(Direction.SOUTH, SOUTH);
      PROPERTY_BY_DIRECTION.put(Direction.WEST, WEST);
      PROPERTY_BY_DIRECTION.put(Direction.UP, UP);
      PROPERTY_BY_DIRECTION.put(Direction.DOWN, DOWN);
   }
}
