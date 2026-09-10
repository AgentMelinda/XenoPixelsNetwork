package com.dragonminez.common.init.block.entity;

import com.dragonminez.common.init.MainBlockEntities;
import com.dragonminez.server.energy.StarEnergyStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.instance.SingletonAnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.animation.AnimatableManager.ControllerRegistrar;
import software.bernie.geckolib.animation.Animation.LoopType;
import software.bernie.geckolib.util.RenderUtil;

public class EnergyCableBlockEntity extends BlockEntity implements GeoBlockEntity {
   private final AnimatableInstanceCache cache = new SingletonAnimatableInstanceCache(this);
   private final StarEnergyStorage energyStorage = new StarEnergyStorage(150, 15) {
      @Override
      public int receiveEnergy(int maxReceive, boolean simulate) {
         int received = super.receiveEnergy(maxReceive, simulate);
         if (received > 0 && !simulate) {
            this.onEnergyChanged();
         }

         return received;
      }

      @Override
      public void onEnergyChanged() {
         EnergyCableBlockEntity.this.setChanged();
      }
   };

   public EnergyCableBlockEntity(BlockPos pPos, BlockState pState) {
      super((BlockEntityType)MainBlockEntities.ENERGY_CABLE_BE.get(), pPos, pState);
   }

   public IEnergyStorage getEnergyStorage() {
      return this.energyStorage;
   }

   public void tick(Level pLevel, BlockPos pPos, BlockState pState) {
      if (pLevel != null && !pLevel.isClientSide) {
         if (this.energyStorage.getEnergyStored() > 0) {
            int toDistribute = this.energyStorage.getEnergyStored();
            int distributed = 0;

            for (Direction dir : Direction.values()) {
               BlockPos neighborPos = pPos.relative(dir);
               BlockEntity be = pLevel.getBlockEntity(neighborPos);
               if (be != null && !(be instanceof EnergyCableBlockEntity) && this.isDMZBlock(pLevel.getBlockState(neighborPos).getBlock())) {
                  distributed += this.pushTo(pLevel, neighborPos, dir.getOpposite(), toDistribute - distributed);
                  if (distributed >= toDistribute) {
                     break;
                  }
               }
            }

            if (distributed < toDistribute) {
               for (Direction dirx : Direction.values()) {
                  BlockEntity be = pLevel.getBlockEntity(pPos.relative(dirx));
                  if (be instanceof EnergyCableBlockEntity) {
                     distributed += this.pushTo(pLevel, pPos.relative(dirx), dirx.getOpposite(), toDistribute - distributed);
                     if (distributed >= toDistribute) {
                        break;
                     }
                  }
               }
            }

            this.energyStorage.extractEnergy(distributed, false);
         }
      }
   }

   private boolean isDMZBlock(Block block) {
      ResourceLocation key = BuiltInRegistries.BLOCK.getKey(block);
      return key != null && key.getNamespace().equals("dragonminez");
   }

   private int pushTo(Level level, BlockPos pos, Direction side, int amount) {
      IEnergyStorage energy = (IEnergyStorage)level.getCapability(EnergyStorage.BLOCK, pos, side);
      return energy == null ? 0 : energy.receiveEnergy(amount, false);
   }

   public void registerControllers(ControllerRegistrar controllerRegistrar) {
      controllerRegistrar.add(new AnimationController(this, "controller", 0, this::predicate));
   }

   private <T extends GeoAnimatable> PlayState predicate(AnimationState<T> tAnimationState) {
      return tAnimationState.setAndContinue(RawAnimation.begin().then("animation.energy_cable.idle", LoopType.LOOP));
   }

   public AnimatableInstanceCache getAnimatableInstanceCache() {
      return this.cache;
   }

   public double getTick(Object blockEntity) {
      return RenderUtil.getCurrentTick();
   }
}
