package com.dragonminez.common.init.block.entity;

import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.init.MainBlockEntities;
import com.dragonminez.common.init.MainRecipes;
import com.dragonminez.common.init.menu.menutypes.KikonoStationMenu;
import com.dragonminez.server.energy.StarEnergyStorage;
import com.dragonminez.server.recipes.KikonoRecipe;
import com.dragonminez.server.recipes.KikonoRecipeInput;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
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

public class KikonoStationBlockEntity extends BlockEntity implements MenuProvider, GeoBlockEntity {
   private final AnimatableInstanceCache cache = new SingletonAnimatableInstanceCache(this);
   private final ItemStackHandler itemHandler = new ItemStackHandler(12) {
      protected void onContentsChanged(int slot) {
         KikonoStationBlockEntity.this.setChanged();
      }
   };
   private final StarEnergyStorage energyStorage = new StarEnergyStorage(2000, 20) {
      @Override
      public void onEnergyChanged() {
         KikonoStationBlockEntity.this.setChanged();
      }

      public boolean canExtract() {
         return false;
      }
   };
   protected final ContainerData data;
   private int progress = 0;
   private int maxProgress = 0;

   public KikonoStationBlockEntity(BlockPos pPos, BlockState pBlockState) {
      super((BlockEntityType)MainBlockEntities.KIKONO_STATION_BE.get(), pPos, pBlockState);
      this.data = new ContainerData() {
         public int get(int pIndex) {
            return switch (pIndex) {
               case 0 -> KikonoStationBlockEntity.this.progress;
               case 1 -> KikonoStationBlockEntity.this.maxProgress;
               case 2 -> KikonoStationBlockEntity.this.energyStorage.getEnergyStored();
               case 3 -> KikonoStationBlockEntity.this.energyStorage.getMaxEnergyStored();
               default -> 0;
            };
         }

         public void set(int pIndex, int pValue) {
            switch (pIndex) {
               case 0:
                  KikonoStationBlockEntity.this.progress = pValue;
                  break;
               case 1:
                  KikonoStationBlockEntity.this.maxProgress = pValue;
                  break;
               case 2:
                  KikonoStationBlockEntity.this.energyStorage.setEnergy(pValue);
            }
         }

         public int getCount() {
            return 4;
         }
      };
   }

   public IItemHandler getItemHandler() {
      return this.itemHandler;
   }

   public IEnergyStorage getEnergyStorage() {
      return this.energyStorage;
   }

   public void tick(Level pLevel, BlockPos pPos, BlockState pState) {
      if (!pLevel.isClientSide) {
         Optional<KikonoRecipe> recipe = this.getCurrentRecipe();
         if (recipe.isPresent() && this.canOutput(recipe.get())) {
            this.maxProgress = recipe.get().getCraftingTime();
            int totalEnergyCost = recipe.get().getEnergyCost();
            int costPerTick = 0;
            if (totalEnergyCost > 0 && this.maxProgress > 0) {
               costPerTick = (totalEnergyCost + this.maxProgress - 1) / this.maxProgress;
            }

            int currentEnergy = this.energyStorage.getEnergyStored();
            if (currentEnergy >= costPerTick) {
               this.energyStorage.setEnergy(currentEnergy - costPerTick);
               this.progress++;
               if (this.progress >= this.maxProgress) {
                  pLevel.playSound(null, pPos, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 1.0F, 1.0F);
                  this.craftItem(recipe.get());
                  this.progress = 0;
               }
            } else if (this.progress > 0) {
               this.progress--;
            }
         } else {
            this.progress = 0;
         }

         this.setChanged();
      }
   }

   private Optional<KikonoRecipe> getCurrentRecipe() {
      if (this.level == null) {
         return Optional.empty();
      } else {
         NonNullList<ItemStack> items = NonNullList.withSize(this.itemHandler.getSlots(), ItemStack.EMPTY);

         for (int i = 0; i < this.itemHandler.getSlots(); i++) {
            items.set(i, this.itemHandler.getStackInSlot(i));
         }

         KikonoRecipeInput input = new KikonoRecipeInput(items);
         return this.level
            .getRecipeManager()
            .getRecipeFor((RecipeType)MainRecipes.KIKONO_TYPE.get(), input, this.level)
            .map(holder -> (KikonoRecipe)holder.value());
      }
   }

   private boolean canOutput(KikonoRecipe recipe) {
      ItemStack result = recipe.getResultItem(this.level != null ? this.level.registryAccess() : null);
      ItemStack outputSlot = this.itemHandler.getStackInSlot(11);
      if (outputSlot.isEmpty()) {
         return true;
      } else {
         return !outputSlot.is(result.getItem()) ? false : outputSlot.getCount() + result.getCount() <= outputSlot.getMaxStackSize();
      }
   }

   private void craftItem(KikonoRecipe recipe) {
      ItemStack result = this.getOutputWithEnchantments(
         recipe.getResultItem(this.level != null ? this.level.registryAccess() : null), this.itemHandler.getStackInSlot(10)
      );

      for (int i = 0; i <= 10; i++) {
         this.itemHandler.extractItem(i, 1, false);
      }

      this.itemHandler.insertItem(11, result, false);
   }

   protected void saveAdditional(CompoundTag pTag, Provider registries) {
      pTag.put("inventory", this.itemHandler.serializeNBT(registries));
      pTag.putInt("progress", this.progress);
      this.energyStorage.saveNBT(pTag);
      super.saveAdditional(pTag, registries);
   }

   protected void loadAdditional(CompoundTag pTag, Provider registries) {
      super.loadAdditional(pTag, registries);
      if (pTag.contains("inventory")) {
         this.itemHandler.deserializeNBT(registries, pTag.getCompound("inventory"));
      }

      this.progress = pTag.getInt("progress");
      this.energyStorage.loadNBT(pTag);
   }

   public Component getDisplayName() {
      return Component.translatable("block.dragonminez.kikono_station");
   }

   public void drops() {
      SimpleContainer inventory = new SimpleContainer(this.itemHandler.getSlots());

      for (int i = 0; i < this.itemHandler.getSlots(); i++) {
         inventory.setItem(i, this.itemHandler.getStackInSlot(i));
      }

      Containers.dropContents(this.level, this.worldPosition, inventory);
   }

   public void registerControllers(ControllerRegistrar controllerRegistrar) {
      controllerRegistrar.add(new AnimationController(this, "controller", 0, this::predicate));
   }

   private <T extends GeoAnimatable> PlayState predicate(AnimationState<T> tAnimationState) {
      return this.progress > 0
         ? tAnimationState.setAndContinue(RawAnimation.begin().then("work", LoopType.LOOP))
         : tAnimationState.setAndContinue(RawAnimation.begin().then("idle", LoopType.LOOP));
   }

   public AnimatableInstanceCache getAnimatableInstanceCache() {
      return this.cache;
   }

   public double getTick(Object blockEntity) {
      return RenderUtil.getCurrentTick();
   }

   @Nullable
   public AbstractContainerMenu createMenu(int pContainerId, Inventory pPlayerInventory, Player pPlayer) {
      return new KikonoStationMenu(pContainerId, pPlayerInventory, this, this.data);
   }

   private ItemStack getOutputWithEnchantments(ItemStack itemStack, ItemStack template) {
      ItemStack output = itemStack.copy();
      if (ConfigManager.getServerConfig().getCrafting().getCopyEnchantmentsFromTemplate()) {
         ItemEnchantments enchantments = (ItemEnchantments)template.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
         if (!enchantments.isEmpty()) {
            output.set(DataComponents.ENCHANTMENTS, enchantments);
         }
      }

      return output;
   }
}
