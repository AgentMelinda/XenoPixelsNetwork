package com.dragonminez.common.init.block.entity;

import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.config.GeneralServerConfig;
import com.dragonminez.common.init.MainBlockEntities;
import com.dragonminez.common.init.block.custom.GravityDeviceBlock;
import com.dragonminez.common.init.menu.menutypes.GravityDeviceMenu;
import com.dragonminez.server.energy.StarEnergyStorage;
import com.dragonminez.server.util.GravityDeviceManager;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.energy.IEnergyStorage;
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

public class GravityDeviceBlockEntity extends BlockEntity implements MenuProvider, GeoBlockEntity {
   private final AnimatableInstanceCache cache = new SingletonAnimatableInstanceCache(this);
   private final StarEnergyStorage energyStorage;
   protected final ContainerData data;
   private boolean active = false;
   private int targetGravity = 10;
   private boolean roomValid = false;
   private boolean running = false;
   private BlockPos roomMin = BlockPos.ZERO;
   private BlockPos roomMax = BlockPos.ZERO;
   private double energyAccumulator = 0.0;

   public GravityDeviceBlockEntity(BlockPos pPos, BlockState pBlockState) {
      super((BlockEntityType)MainBlockEntities.GRAVITY_DEVICE_BE.get(), pPos, pBlockState);
      this.energyStorage = new StarEnergyStorage(cfg().getDeviceEnergyCapacity(), 256) {
         @Override
         public void onEnergyChanged() {
            GravityDeviceBlockEntity.this.setChanged();
         }

         public boolean canExtract() {
            return false;
         }
      };
      this.data = new ContainerData() {
         public int get(int pIndex) {
            return switch (pIndex) {
               case 0 -> GravityDeviceBlockEntity.this.active ? 1 : 0;
               case 1 -> GravityDeviceBlockEntity.this.targetGravity;
               case 2 -> GravityDeviceBlockEntity.this.energyStorage.getEnergyStored();
               case 3 -> GravityDeviceBlockEntity.this.energyStorage.getMaxEnergyStored();
               case 4 -> GravityDeviceBlockEntity.this.roomValid ? 1 : 0;
               case 5 -> GravityDeviceBlockEntity.this.running ? 1 : 0;
               default -> 0;
            };
         }

         public void set(int pIndex, int pValue) {
            switch (pIndex) {
               case 0:
                  GravityDeviceBlockEntity.this.active = pValue != 0;
                  break;
               case 1:
                  GravityDeviceBlockEntity.this.targetGravity = pValue;
                  break;
               case 2:
                  GravityDeviceBlockEntity.this.energyStorage.setEnergy(pValue);
               case 3:
               default:
                  break;
               case 4:
                  GravityDeviceBlockEntity.this.roomValid = pValue != 0;
                  break;
               case 5:
                  GravityDeviceBlockEntity.this.running = pValue != 0;
            }
         }

         public int getCount() {
            return 6;
         }
      };
   }

   private static GeneralServerConfig.GravityConfig cfg() {
      return ConfigManager.getServerConfig().getGravity();
   }

   public void setRemoved() {
      super.setRemoved();
      if (this.level != null && !this.level.isClientSide) {
         GravityDeviceManager.unregister(this.level, this.worldPosition);
      }
   }

   public void refreshRoom() {
      this.recomputeRoom();
      this.setChanged();
      if (this.level != null) {
         this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
      }
   }

   public void applyMenuInput(boolean active, int gravity) {
      int max = cfg().getDeviceMaxGravity();
      this.targetGravity = Math.max(1, Math.min(gravity, max));
      this.active = active;
      this.recomputeRoom();
      this.setChanged();
      if (this.level != null) {
         this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
      }
   }

   public void tick(Level pLevel, BlockPos pPos, BlockState pState) {
      if (!pLevel.isClientSide) {
         long time = pLevel.getGameTime();
         boolean nowRunning = false;
         if (this.active) {
            if (time % 100L == 0L) {
               this.recomputeRoom();
            }

            if (this.roomValid) {
               double perSecond = cfg().getDeviceEnergyPerGravityPerSecond() * (double)this.targetGravity;
               this.energyAccumulator += perSecond / 20.0;
               int toConsume = (int)this.energyAccumulator;
               if (toConsume > 0) {
                  if (this.energyStorage.getEnergyStored() >= toConsume) {
                     this.energyStorage.setEnergy(this.energyStorage.getEnergyStored() - toConsume);
                     this.energyAccumulator -= (double)toConsume;
                     nowRunning = true;
                  }
               } else {
                  nowRunning = this.energyStorage.getEnergyStored() > 0;
               }
            }
         } else {
            this.energyAccumulator = 0.0;
         }

         if (nowRunning) {
            GravityDeviceManager.register(pLevel, pPos, this.roomAABB(), (double)this.targetGravity);
         } else {
            GravityDeviceManager.unregister(pLevel, pPos);
         }

         if (nowRunning != this.running) {
            this.running = nowRunning;
            if (pState.hasProperty(GravityDeviceBlock.ACTIVE) && (Boolean)pState.getValue(GravityDeviceBlock.ACTIVE) != nowRunning) {
               pLevel.setBlock(pPos, (BlockState)pState.setValue(GravityDeviceBlock.ACTIVE, nowRunning), 3);
            }

            pLevel.sendBlockUpdated(pPos, this.getBlockState(), this.getBlockState(), 3);
         }

         this.setChanged();
      }
   }

   private AABB roomAABB() {
      return new AABB(
         (double)this.roomMin.getX(),
         (double)this.roomMin.getY(),
         (double)this.roomMin.getZ(),
         (double)this.roomMax.getX() + 1.0,
         (double)this.roomMax.getY() + 1.0,
         (double)this.roomMax.getZ() + 1.0
      );
   }

   private void recomputeRoom() {
      if (this.level == null) {
         this.roomValid = false;
      } else {
         int minSize = cfg().getDeviceMinRoomSize();
         int maxSize = cfg().getDeviceMaxRoomSize();
         int cellCap = (2 * maxSize + 1) * (2 * maxSize + 1) * (2 * maxSize + 1);
         int ox = this.worldPosition.getX();
         int oy = this.worldPosition.getY();
         int oz = this.worldPosition.getZ();
         Set<Long> visited = new HashSet<>();
         ArrayDeque<BlockPos> queue = new ArrayDeque<>();
         MutableBlockPos cursor = new MutableBlockPos();
         int minX = ox;
         int minY = oy;
         int minZ = oz;
         int maxX = ox;
         int maxY = oy;
         int maxZ = oz;
         boolean invalid = false;

         for (Direction dir : Direction.values()) {
            BlockPos n = this.worldPosition.relative(dir);
            if (withinBounds(n, ox, oy, oz, maxSize) && this.isPassable(n) && visited.add(n.asLong())) {
               queue.add(n);
            } else if (!withinBounds(n, ox, oy, oz, maxSize) && this.isPassable(n)) {
               invalid = true;
            }
         }

         label101:
         while (!invalid && !queue.isEmpty()) {
            if (visited.size() > cellCap) {
               invalid = true;
               break;
            }

            BlockPos c = queue.poll();
            if (c.getX() < minX) {
               minX = c.getX();
            }

            if (c.getY() < minY) {
               minY = c.getY();
            }

            if (c.getZ() < minZ) {
               minZ = c.getZ();
            }

            if (c.getX() > maxX) {
               maxX = c.getX();
            }

            if (c.getY() > maxY) {
               maxY = c.getY();
            }

            if (c.getZ() > maxZ) {
               maxZ = c.getZ();
            }

            for (Direction dirx : Direction.values()) {
               cursor.setWithOffset(c, dirx);
               if (!withinBounds(cursor, ox, oy, oz, maxSize)) {
                  if (this.isPassable(cursor)) {
                     invalid = true;
                     if (invalid) {
                        break label101;
                     }
                     continue label101;
                  }
               } else if (this.isPassable(cursor) && visited.add(cursor.asLong())) {
                  queue.add(cursor.immutable());
               }
            }
            break;
         }

         if (!invalid && !visited.isEmpty()) {
            int dx = maxX - minX + 1;
            int dy = maxY - minY + 1;
            int dz = maxZ - minZ + 1;
            if (dx >= minSize && dy >= minSize && dz >= minSize && dx <= maxSize && dy <= maxSize && dz <= maxSize) {
               this.roomValid = true;
               this.roomMin = new BlockPos(minX, minY, minZ);
               this.roomMax = new BlockPos(maxX, maxY, maxZ);
            } else {
               this.roomValid = false;
            }
         } else {
            this.roomValid = false;
         }
      }
   }

   private static boolean withinBounds(BlockPos p, int ox, int oy, int oz, int maxSize) {
      return Math.abs(p.getX() - ox) <= maxSize && Math.abs(p.getY() - oy) <= maxSize && Math.abs(p.getZ() - oz) <= maxSize;
   }

   private boolean isPassable(BlockPos pos) {
      if (pos.equals(this.worldPosition)) {
         return false;
      } else {
         BlockState state = this.level.getBlockState(pos);
         if (state.getBlock() instanceof GravityDeviceBlock) {
            return false;
         } else {
            return !(state.getBlock() instanceof DoorBlock) && !(state.getBlock() instanceof TrapDoorBlock)
               ? state.getCollisionShape(this.level, pos).isEmpty()
               : false;
         }
      }
   }

   public IEnergyStorage getEnergyStorage() {
      return this.energyStorage;
   }

   public boolean isRoomValid() {
      return this.roomValid;
   }

   public boolean isActive() {
      return this.active;
   }

   public int getTargetGravity() {
      return this.targetGravity;
   }

   public BlockPos getRoomMin() {
      return this.roomMin;
   }

   public BlockPos getRoomMax() {
      return this.roomMax;
   }

   protected void saveAdditional(CompoundTag pTag, Provider registries) {
      pTag.putBoolean("active", this.active);
      pTag.putInt("targetGravity", this.targetGravity);
      pTag.putBoolean("roomValid", this.roomValid);
      pTag.putLong("roomMin", this.roomMin.asLong());
      pTag.putLong("roomMax", this.roomMax.asLong());
      this.energyStorage.saveNBT(pTag);
      super.saveAdditional(pTag, registries);
   }

   protected void loadAdditional(CompoundTag pTag, Provider registries) {
      super.loadAdditional(pTag, registries);
      this.active = pTag.getBoolean("active");
      this.targetGravity = pTag.getInt("targetGravity");
      this.roomValid = pTag.getBoolean("roomValid");
      this.roomMin = BlockPos.of(pTag.getLong("roomMin"));
      this.roomMax = BlockPos.of(pTag.getLong("roomMax"));
      this.energyStorage.loadNBT(pTag);
   }

   public CompoundTag getUpdateTag(Provider registries) {
      CompoundTag tag = super.getUpdateTag(registries);
      tag.putBoolean("active", this.active);
      tag.putBoolean("roomValid", this.roomValid);
      tag.putInt("targetGravity", this.targetGravity);
      tag.putLong("roomMin", this.roomMin.asLong());
      tag.putLong("roomMax", this.roomMax.asLong());
      return tag;
   }

   public void handleUpdateTag(CompoundTag tag, Provider registries) {
      this.active = tag.getBoolean("active");
      this.roomValid = tag.getBoolean("roomValid");
      this.targetGravity = tag.getInt("targetGravity");
      this.roomMin = BlockPos.of(tag.getLong("roomMin"));
      this.roomMax = BlockPos.of(tag.getLong("roomMax"));
   }

   @Nullable
   public Packet<ClientGamePacketListener> getUpdatePacket() {
      return ClientboundBlockEntityDataPacket.create(this);
   }

   public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt, Provider registries) {
      if (pkt.getTag() != null) {
         this.handleUpdateTag(pkt.getTag(), registries);
      }
   }

   public Component getDisplayName() {
      return Component.translatable("block.dragonminez.gravity_device");
   }

   public void registerControllers(ControllerRegistrar controllerRegistrar) {
      controllerRegistrar.add(new AnimationController(this, "controller", 0, this::predicate));
   }

   private <T extends GeoAnimatable> PlayState predicate(AnimationState<T> tAnimationState) {
      BlockState state = this.getBlockState();
      boolean on = state.hasProperty(GravityDeviceBlock.ACTIVE) && (Boolean)state.getValue(GravityDeviceBlock.ACTIVE);
      return on
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
      return new GravityDeviceMenu(pContainerId, pPlayerInventory, this, this.data);
   }
}
