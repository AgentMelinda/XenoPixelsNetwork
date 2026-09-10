package com.dragonminez.common.init.block.custom;

import com.dragonminez.common.init.block.entity.TimeChamberPortalBlockEntity;
import com.dragonminez.server.world.dimension.HTCDimension;
import com.dragonminez.server.world.structure.helper.DMZStructures;
import com.dragonminez.server.world.structure.helper.StructureLocator;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.Holder.Reference;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public class TimeChamberPortalBlock extends BaseEntityBlock {
   public static final MapCodec<TimeChamberPortalBlock> CODEC = simpleCodec(TimeChamberPortalBlock::new);

   protected MapCodec<? extends BaseEntityBlock> codec() {
      return CODEC;
   }

   public TimeChamberPortalBlock() {
      this(Properties.ofFullCopy(Blocks.QUARTZ_BLOCK).noLootTable().strength(-1.0F, 3600000.0F));
   }

   public TimeChamberPortalBlock(Properties properties) {
      super(properties);
   }

   public InteractionResult useWithoutItem(BlockState pState, Level pLevel, BlockPos pPos, Player pPlayer, BlockHitResult pHit) {
      if (!pLevel.isClientSide) {
         if (pLevel.getBlockEntity(pPos) instanceof TimeChamberPortalBlockEntity tile) {
            boolean onHTC = pLevel.dimension().equals(HTCDimension.HTC_KEY);
            ResourceKey<Level> targetDimKey = onHTC ? Level.OVERWORLD : HTCDimension.HTC_KEY;
            ServerLevel targetLevel = pPlayer.getServer().getLevel(targetDimKey);
            if (targetLevel != null && !pPlayer.isPassenger()) {
               BlockPos targetPos = null;
               if (onHTC) {
                  if (tile.hasCachedTarget()) {
                     targetPos = tile.getCachedTarget();
                  } else {
                     targetPos = this.findTargetAndCache(targetLevel, true, pPlayer, tile, this);
                  }
               } else {
                  targetPos = this.findTargetAndCache(targetLevel, false, pPlayer, tile, this);
               }

               if (targetPos != null) {
                  this.teleportPlayer(pPlayer, targetLevel, targetPos, onHTC ? 180.0F : 90.0F);
               } else {
                  BlockPos backup;
                  if (onHTC) {
                     BlockPos kami = StructureLocator.locateStructure(targetLevel, DMZStructures.KAMILOOKOUT, BlockPos.ZERO);
                     backup = kami != null ? kami : targetLevel.getSharedSpawnPos();
                  } else {
                     backup = new BlockPos(0, 130, 0);
                  }

                  this.teleportPlayer(pPlayer, targetLevel, backup, 90.0F);
               }
            }

            return InteractionResult.SUCCESS;
         } else {
            return InteractionResult.FAIL;
         }
      } else {
         return InteractionResult.CONSUME;
      }
   }

   private BlockPos findTargetAndCache(ServerLevel targetLevel, boolean onHTC, Player player, TimeChamberPortalBlockEntity tile, Block targetBlock) {
      ResourceKey<Structure> targetStructureKey = onHTC ? DMZStructures.KAMILOOKOUT : DMZStructures.TIMECHAMBER;
      BlockPos searchCenter = onHTC ? BlockPos.ZERO : player.blockPosition();
      BlockPos structurePos = StructureLocator.locateStructure(targetLevel, targetStructureKey, searchCenter);
      BlockPos finalPos = null;
      if (structurePos != null) {
         targetLevel.getChunk(structurePos.getX() >> 4, structurePos.getZ() >> 4, ChunkStatus.FULL, true);
         finalPos = this.findPortalInStructureMeta(targetLevel, structurePos, targetStructureKey, targetBlock);
         if (finalPos == null) {
            finalPos = this.findPortalByAreaScan(targetLevel, structurePos, onHTC, targetBlock);
         }
      }

      if (finalPos != null) {
         tile.setCachedTarget(finalPos);
      }

      return finalPos;
   }

   private BlockPos findPortalInStructureMeta(ServerLevel level, BlockPos structureCenter, ResourceKey<Structure> structureKey, Block targetBlock) {
      Registry<Structure> structureRegistry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
      Reference<Structure> structureHolder = (Reference<Structure>)structureRegistry.getHolder(structureKey).orElse(null);
      if (structureHolder == null) {
         return null;
      } else {
         level.getChunk(structureCenter.getX() >> 4, structureCenter.getZ() >> 4, ChunkStatus.STRUCTURE_STARTS);
         StructureStart start = level.structureManager().getStructureAt(structureCenter, (Structure)structureHolder.value());
         if (start == null || !start.isValid()) {
            Structure targetStructure = (Structure)structureHolder.value();

            for (StructureStart candidate : level.structureManager().startsForStructure(new ChunkPos(structureCenter), s -> s == targetStructure)) {
               if (candidate != null && candidate.isValid()) {
                  start = candidate;
                  break;
               }
            }
         }

         if (start != null && start.isValid()) {
            BoundingBox boundingBox = start.getBoundingBox();

            for (int cx = boundingBox.minX() >> 4; cx <= boundingBox.maxX() >> 4; cx++) {
               for (int cz = boundingBox.minZ() >> 4; cz <= boundingBox.maxZ() >> 4; cz++) {
                  level.getChunk(cx, cz, ChunkStatus.FULL, true);
               }
            }

            for (BlockPos pos : BlockPos.betweenClosed(
               boundingBox.minX(), boundingBox.minY(), boundingBox.minZ(), boundingBox.maxX(), boundingBox.maxY(), boundingBox.maxZ()
            )) {
               if (level.getBlockState(pos).is(targetBlock)) {
                  return pos.above();
               }
            }
         }

         return null;
      }
   }

   private BlockPos findPortalByAreaScan(ServerLevel level, BlockPos center, boolean onHTC, Block targetBlock) {
      int offX;
      int offY;
      int offZ;
      if (onHTC) {
         offX = 45;
         offY = 125;
         offZ = 75;
      } else {
         offX = 62;
         offY = 4;
         offZ = 66;
      }

      BlockPos[] candidates = new BlockPos[]{
         center.offset(offX, offY, offZ), center.offset(-offZ, offY, offX), center.offset(-offX, offY, -offZ), center.offset(offZ, offY, -offX)
      };
      int searchRadius = 24;
      int verticalRadius = 30;

      for (BlockPos p : candidates) {
         level.getChunk(p.getX() >> 4, p.getZ() >> 4, ChunkStatus.FULL, true);

         for (BlockPos checkPos : BlockPos.betweenClosed(
            p.getX() - searchRadius,
            p.getY() - verticalRadius,
            p.getZ() - searchRadius,
            p.getX() + searchRadius,
            p.getY() + verticalRadius,
            p.getZ() + searchRadius
         )) {
            if (level.getBlockState(checkPos).is(targetBlock)) {
               return checkPos.above();
            }
         }
      }

      return null;
   }

   private void teleportPlayer(Player player, ServerLevel targetLevel, BlockPos targetPos, float rotX) {
      Vec3 pos = new Vec3((double)targetPos.getX() + 0.5, (double)targetPos.getY(), (double)targetPos.getZ() + 0.5);
      player.changeDimension(new DimensionTransition(targetLevel, pos, Vec3.ZERO, rotX, 0.0F, DimensionTransition.DO_NOTHING));
   }

   @Nullable
   public BlockEntity newBlockEntity(BlockPos pPos, BlockState pState) {
      return new TimeChamberPortalBlockEntity(pPos, pState);
   }

   public RenderShape getRenderShape(BlockState pState) {
      return RenderShape.MODEL;
   }
}
