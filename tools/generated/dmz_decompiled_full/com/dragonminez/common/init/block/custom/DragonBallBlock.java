package com.dragonminez.common.init.block.custom;

import com.dragonminez.common.dragonball.DragonBallDefinitions;
import com.dragonminez.common.dragonball.DragonBallSetDefinition;
import com.dragonminez.common.dragonball.DragonDefinition;
import com.dragonminez.common.events.DMZEvent;
import com.dragonminez.common.init.MainSounds;
import com.dragonminez.common.init.block.entity.DragonBallBlockEntity;
import com.dragonminez.common.init.entities.dragon.DragonWishEntity;
import com.dragonminez.server.events.DragonBallsHandler;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.common.NeoForge;
import org.jetbrains.annotations.Nullable;

public class DragonBallBlock extends BaseEntityBlock implements EntityBlock {
   private static final Codec<DragonBallType> BALL_TYPE_CODEC = Codec.intRange(1, 7)
      .xmap(stars -> DragonBallType.values()[stars - 1], DragonBallType::getStars);
   public static final MapCodec<DragonBallBlock> CODEC = RecordCodecBuilder.mapCodec(
      instance -> instance.group(
               propertiesCodec(),
               BALL_TYPE_CODEC.fieldOf("ball_type").forGetter(DragonBallBlock::getBallType),
               Codec.STRING.fieldOf("ball_set").forGetter(DragonBallBlock::getBallSetId)
            )
            .apply(instance, DragonBallBlock::new)
   );
   private static final VoxelShape SHAPE = Shapes.box(0.25, 0.0, 0.25, 0.75, 0.5, 0.75);
   public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
   private final DragonBallType ballType;
   private final String ballSetId;

   protected MapCodec<? extends BaseEntityBlock> codec() {
      return CODEC;
   }

   public DragonBallBlock(Properties properties, DragonBallType ballType, String ballSetId) {
      super(properties);
      this.ballType = ballType;
      this.ballSetId = ballSetId;
      this.registerDefaultState((BlockState)((BlockState)this.stateDefinition.any()).setValue(FACING, Direction.NORTH));
   }

   protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
      builder.add(new Property[]{FACING});
   }

   @Nullable
   public BlockState getStateForPlacement(BlockPlaceContext context) {
      return (BlockState)this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
   }

   public BlockState rotate(BlockState state, Rotation rot) {
      return (BlockState)state.setValue(FACING, rot.rotate((Direction)state.getValue(FACING)));
   }

   public BlockState mirror(BlockState state, Mirror mirrorIn) {
      return state.rotate(mirrorIn.getRotation((Direction)state.getValue(FACING)));
   }

   public DragonBallType getBallType() {
      return this.ballType;
   }

   public String getBallSetId() {
      return this.ballSetId;
   }

   public boolean isNamekian() {
      return "namek".equals(this.ballSetId);
   }

   public RenderShape getRenderShape(BlockState state) {
      return RenderShape.ENTITYBLOCK_ANIMATED;
   }

   public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
      return SHAPE;
   }

   public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
      return !level.isEmptyBlock(pos.below());
   }

   @Nullable
   public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
      return new DragonBallBlockEntity(pos, state, this.ballType, this.ballSetId);
   }

   public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
      if (level.isClientSide) {
         return InteractionResult.SUCCESS;
      } else {
         DragonBallSetDefinition ballSetDefinition = DragonBallDefinitions.getBallSet(this.ballSetId);
         if (ballSetDefinition != null && ballSetDefinition.supportsDimension(level.dimension())) {
            DragonDefinition dragonDefinition = DragonBallDefinitions.getDragonForSetAndDimension(this.ballSetId, level.dimension());
            if (dragonDefinition == null) {
               return InteractionResult.PASS;
            } else if (this.areAllDragonBallsNearby(level, pos, ballSetDefinition)) {
               List<BlockPos> consumedPositions = this.removeAllDragonBalls(level, pos, ballSetDefinition);
               if (level instanceof ServerLevel serverLevel) {
                  DragonBallsHandler.unregisterConsumedDragonBalls(serverLevel, consumedPositions, this.ballSetId);
                  if (this.summonDragon(serverLevel, pos, player, dragonDefinition)) {
                     NeoForge.EVENT_BUS
                        .post(new DMZEvent.DragonSummonedEvent(player, serverLevel, pos, dragonDefinition, ballSetDefinition, consumedPositions));
                     serverLevel.playSound(null, pos, (SoundEvent)MainSounds.SHENRON.get(), SoundSource.AMBIENT, 1.0F, 1.0F);
                  }
               }

               return InteractionResult.CONSUME;
            } else {
               return InteractionResult.PASS;
            }
         } else {
            return InteractionResult.PASS;
         }
      }
   }

   private boolean summonDragon(ServerLevel serverLevel, BlockPos pos, Player player, DragonDefinition dragonDefinition) {
      EntityType<?> entityType = (EntityType<?>)BuiltInRegistries.ENTITY_TYPE
         .get(ResourceLocation.fromNamespaceAndPath("dragonminez", dragonDefinition.getId()));
      if (entityType == null) {
         return false;
      } else if (entityType.create(serverLevel) instanceof DragonWishEntity dragon) {
         dragon.setDragonDefinitionId(dragonDefinition.getId());
         dragon.setOwnerName(player.getName().getString());
         dragon.setInvokingTime(serverLevel.getDayTime());
         dragon.setGrantedWish(false);
         dragon.moveTo((double)pos.getX() + 0.5, (double)pos.getY(), (double)pos.getZ() + 0.5, 0.0F, 0.0F);
         return serverLevel.addFreshEntity(dragon);
      } else {
         return false;
      }
   }

   private boolean areAllDragonBallsNearby(Level level, BlockPos pos, DragonBallSetDefinition setDefinition) {
      Set<DragonBallType> foundBalls = new HashSet<>();
      int radius = setDefinition.getSummonRadius();

      for (BlockPos checkPos : BlockPos.betweenClosed(pos.offset(-radius, -radius, -radius), pos.offset(radius, radius, radius))) {
         Block block = level.getBlockState(checkPos).getBlock();
         if (block instanceof DragonBallBlock) {
            DragonBallBlock dragonBall = (DragonBallBlock)block;
            if (this.ballSetId.equals(dragonBall.getBallSetId())) {
               foundBalls.add(dragonBall.getBallType());
            }
         }
      }

      return foundBalls.size() == 7;
   }

   private List<BlockPos> removeAllDragonBalls(Level level, BlockPos pos, DragonBallSetDefinition setDefinition) {
      Set<DragonBallType> removedBalls = new HashSet<>();
      List<BlockPos> removedPositions = new ArrayList<>();
      int radius = setDefinition.getSummonRadius();

      for (BlockPos checkPos : BlockPos.betweenClosed(pos.offset(-radius, -radius, -radius), pos.offset(radius, radius, radius))) {
         Block block = level.getBlockState(checkPos).getBlock();
         if (block instanceof DragonBallBlock) {
            DragonBallBlock dragonBall = (DragonBallBlock)block;
            if (this.ballSetId.equals(dragonBall.getBallSetId()) && !removedBalls.contains(dragonBall.getBallType())) {
               level.removeBlock(checkPos, false);
               removedBalls.add(dragonBall.getBallType());
               removedPositions.add(checkPos.immutable());
            }
         }
      }

      return removedPositions;
   }
}
