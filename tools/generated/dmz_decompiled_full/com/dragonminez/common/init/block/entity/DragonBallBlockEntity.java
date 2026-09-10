package com.dragonminez.common.init.block.entity;

import com.dragonminez.common.init.MainBlockEntities;
import com.dragonminez.common.init.block.custom.DragonBallType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.instance.SingletonAnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager.ControllerRegistrar;
import software.bernie.geckolib.util.RenderUtil;

public class DragonBallBlockEntity extends BlockEntity implements GeoBlockEntity {
   private final AnimatableInstanceCache cache = new SingletonAnimatableInstanceCache(this);
   private final DragonBallType ballType;
   private final String ballSetId;

   public DragonBallBlockEntity(BlockPos pos, BlockState state, DragonBallType ballType, String ballSetId) {
      super((BlockEntityType)MainBlockEntities.DRAGON_BALL_BLOCK_ENTITY.get(), pos, state);
      this.ballType = ballType;
      this.ballSetId = ballSetId;
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

   public AnimatableInstanceCache getAnimatableInstanceCache() {
      return this.cache;
   }

   public void registerControllers(ControllerRegistrar controllerRegistrar) {
   }

   public double getTick(Object blockEntity) {
      return RenderUtil.getCurrentTick();
   }
}
