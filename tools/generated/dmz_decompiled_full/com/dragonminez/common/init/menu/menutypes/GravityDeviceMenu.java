package com.dragonminez.common.init.menu.menutypes;

import com.dragonminez.common.init.MainBlocks;
import com.dragonminez.common.init.MainMenus;
import com.dragonminez.common.init.block.entity.GravityDeviceBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;

public class GravityDeviceMenu extends AbstractContainerMenu {
   public final GravityDeviceBlockEntity blockEntity;
   private final Level level;
   private final ContainerData data;

   public GravityDeviceMenu(int pContainerId, Inventory inv, FriendlyByteBuf extraData) {
      this(pContainerId, inv, inv.player.level().getBlockEntity(extraData.readBlockPos()), new SimpleContainerData(6));
   }

   public GravityDeviceMenu(int pContainerId, Inventory inv, BlockEntity entity, ContainerData data) {
      super((MenuType)MainMenus.GRAVITY_DEVICE_MENU.get(), pContainerId);
      this.blockEntity = (GravityDeviceBlockEntity)entity;
      this.level = inv.player.level();
      this.data = data;
      this.addPlayerInv(inv);
      this.addPlayerHotbar(inv);
      this.addDataSlots(data);
   }

   public BlockPos getBlockPos() {
      return this.blockEntity.getBlockPos();
   }

   public boolean isActive() {
      return this.data.get(0) > 0;
   }

   public int getTargetGravity() {
      return this.data.get(1);
   }

   public int getEnergy() {
      return this.data.get(2);
   }

   public int getMaxEnergy() {
      return this.data.get(3);
   }

   public boolean isRoomValid() {
      return this.data.get(4) > 0;
   }

   public boolean isRunning() {
      return this.data.get(5) > 0;
   }

   public int getScaledEnergy() {
      int energy = this.data.get(2);
      int maxEnergy = this.data.get(3);
      int barHeight = 60;
      return maxEnergy != 0 && energy != 0 ? energy * barHeight / maxEnergy : 0;
   }

   public ItemStack quickMoveStack(Player playerIn, int pIndex) {
      return ItemStack.EMPTY;
   }

   public boolean stillValid(Player pPlayer) {
      return stillValid(ContainerLevelAccess.create(this.level, this.blockEntity.getBlockPos()), pPlayer, (Block)MainBlocks.GRAVITY_DEVICE.get());
   }

   private void addPlayerInv(Inventory playerInv) {
      for (int i = 0; i < 3; i++) {
         for (int l = 0; l < 9; l++) {
            this.addSlot(new Slot(playerInv, l + i * 9 + 9, 8 + l * 18, 84 + i * 18));
         }
      }
   }

   private void addPlayerHotbar(Inventory playerInv) {
      for (int i = 0; i < 9; i++) {
         this.addSlot(new Slot(playerInv, i, 8 + i * 18, 142));
      }
   }
}
