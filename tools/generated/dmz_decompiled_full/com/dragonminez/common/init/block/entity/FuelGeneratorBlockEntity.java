package com.dragonminez.common.init.block.entity;

import com.dragonminez.common.init.MainBlockEntities;
import com.dragonminez.common.init.block.custom.FuelGeneratorBlock;
import com.dragonminez.common.init.menu.menutypes.FuelGeneratorMenu;
import com.dragonminez.server.energy.StarEnergyStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
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

public class FuelGeneratorBlockEntity extends BlockEntity implements MenuProvider, GeoBlockEntity {
   private final AnimatableInstanceCache cache = new SingletonAnimatableInstanceCache(this);
   private final ItemStackHandler itemHandler = new ItemStackHandler(1) {
      protected void onContentsChanged(int slot) {
         FuelGeneratorBlockEntity.this.setChanged();
      }

      public boolean isItemValid(int slot, @NotNull ItemStack stack) {
         return stack.getBurnTime(RecipeType.SMELTING) > 0;
      }
   };
   private final StarEnergyStorage energyStorage = new StarEnergyStorage(1000, 20) {
      @Override
      public void onEnergyChanged() {
         FuelGeneratorBlockEntity.this.setChanged();
      }

      public boolean canReceive() {
         return false;
      }
   };
   protected final ContainerData data = new ContainerData() {
      public int get(int index) {
         return switch (index) {
            case 0 -> FuelGeneratorBlockEntity.this.burnTime;
            case 1 -> FuelGeneratorBlockEntity.this.maxBurnTime;
            case 2 -> FuelGeneratorBlockEntity.this.energyStorage.getEnergyStored();
            case 3 -> FuelGeneratorBlockEntity.this.energyStorage.getMaxEnergyStored();
            default -> 0;
         };
      }

      public void set(int index, int value) {
         switch (index) {
            case 0:
               FuelGeneratorBlockEntity.this.burnTime = value;
               break;
            case 1:
               FuelGeneratorBlockEntity.this.maxBurnTime = value;
               break;
            case 2:
               FuelGeneratorBlockEntity.this.energyStorage.setEnergy(value);
         }
      }

      public int getCount() {
         return 4;
      }
   };
   private int burnTime;
   private int maxBurnTime;

   public FuelGeneratorBlockEntity(BlockPos pPos, BlockState pBlockState) {
      super((BlockEntityType)MainBlockEntities.FUEL_GENERATOR_BE.get(), pPos, pBlockState);
   }

   public IItemHandler getItemHandler() {
      return this.itemHandler;
   }

   public IEnergyStorage getEnergyStorage() {
      return this.energyStorage;
   }

   public void tick(Level pLevel, BlockPos pPos, BlockState pState) {
      if (!pLevel.isClientSide) {
         boolean isBurning = this.burnTime > 0;
         boolean changed = false;
         if (isBurning) {
            this.burnTime--;
            if (this.burnTime % 4 != 0) {
               int current = this.energyStorage.getEnergyStored();
               int max = this.energyStorage.getMaxEnergyStored();
               this.energyStorage.setEnergy(Math.min(current + 1, max));
            }

            changed = true;
         }

         if (this.burnTime <= 0 && this.energyStorage.getEnergyStored() < this.energyStorage.getMaxEnergyStored()) {
            ItemStack fuel = this.itemHandler.getStackInSlot(0);
            Item fuelItem = fuel.getItem();
            if (!fuel.isEmpty()) {
               int fuelTime = fuel.getBurnTime(RecipeType.SMELTING);
               if (fuelTime > 0) {
                  this.burnTime = fuelTime;
                  this.maxBurnTime = fuelTime;
                  fuel.shrink(1);
                  if (fuel.isEmpty()) {
                     ItemStack remainder = fuelItem.getCraftingRemainingItem(fuel);
                     this.itemHandler.setStackInSlot(0, remainder);
                  } else {
                     this.itemHandler.setStackInSlot(0, fuel);
                  }

                  changed = true;
                  if (!isBurning) {
                     pLevel.setBlock(pPos, (BlockState)pState.setValue(FuelGeneratorBlock.LIT, true), 3);
                  }
               }
            } else if (isBurning) {
               pLevel.setBlock(pPos, (BlockState)pState.setValue(FuelGeneratorBlock.LIT, false), 3);
            }
         }

         this.distributeEnergy();
         if (changed) {
            this.setChanged();
         }
      }
   }

   private void distributeEnergy() {
      if (this.energyStorage.getEnergyStored() > 0 && this.level != null) {
         for (Direction dir : Direction.values()) {
            BlockPos neighbor = this.worldPosition.relative(dir);
            IEnergyStorage target = (IEnergyStorage)this.level.getCapability(EnergyStorage.BLOCK, neighbor, dir.getOpposite());
            if (target != null && target.canReceive()) {
               int sent = target.receiveEnergy(Math.min(this.energyStorage.getEnergyStored(), 256), false);
               this.energyStorage.extractEnergy(sent, false);
            }
         }
      }
   }

   protected void saveAdditional(CompoundTag pTag, Provider registries) {
      pTag.put("inventory", this.itemHandler.serializeNBT(registries));
      pTag.putInt("burnTime", this.burnTime);
      this.energyStorage.saveNBT(pTag);
      super.saveAdditional(pTag, registries);
   }

   protected void loadAdditional(CompoundTag pTag, Provider registries) {
      super.loadAdditional(pTag, registries);
      if (pTag.contains("inventory")) {
         this.itemHandler.deserializeNBT(registries, pTag.getCompound("inventory"));
      }

      this.burnTime = pTag.getInt("burnTime");
      this.energyStorage.loadNBT(pTag);
   }

   public Component getDisplayName() {
      return Component.translatable("block.dragonminez.fuel_generator");
   }

   public void registerControllers(ControllerRegistrar controllerRegistrar) {
      controllerRegistrar.add(new AnimationController(this, "controller", 0, this::predicate));
   }

   private <T extends GeoAnimatable> PlayState predicate(AnimationState<T> tAnimationState) {
      return tAnimationState.setAndContinue(RawAnimation.begin().then("animation.fuel_generator.idle", LoopType.LOOP));
   }

   public AnimatableInstanceCache getAnimatableInstanceCache() {
      return this.cache;
   }

   public double getTick(Object blockEntity) {
      return RenderUtil.getCurrentTick();
   }

   @Nullable
   public AbstractContainerMenu createMenu(int pContainerId, Inventory pPlayerInventory, Player pPlayer) {
      return new FuelGeneratorMenu(pContainerId, pPlayerInventory, this, this.data);
   }
}
