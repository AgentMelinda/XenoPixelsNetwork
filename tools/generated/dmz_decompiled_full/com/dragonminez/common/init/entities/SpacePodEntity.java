package com.dragonminez.common.init.entities;

import com.dragonminez.client.util.KeyBinds;
import com.dragonminez.common.init.MainItems;
import com.dragonminez.common.init.MainSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.network.syncher.SynchedEntityData.Builder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Entity.MoveFunction;
import net.minecraft.world.entity.Entity.RemovalReason;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.instance.SingletonAnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.animation.AnimatableManager.ControllerRegistrar;
import software.bernie.geckolib.animation.Animation.LoopType;

public class SpacePodEntity extends Mob implements GeoEntity {
   private static final EntityDataAccessor<Boolean> IS_OPEN = SynchedEntityData.defineId(SpacePodEntity.class, EntityDataSerializers.BOOLEAN);
   private static final RawAnimation ANIM_ABIERTO = RawAnimation.begin().then("open", LoopType.HOLD_ON_LAST_FRAME);
   private static final RawAnimation ANIM_CERRADO = RawAnimation.begin().then("close", LoopType.HOLD_ON_LAST_FRAME);
   private final AnimatableInstanceCache geoCache = new SingletonAnimatableInstanceCache(this);

   public SpacePodEntity(EntityType<? extends Mob> pEntityType, Level pLevel) {
      super(pEntityType, pLevel);
      this.setNoGravity(true);
      this.setPersistenceRequired();
   }

   public boolean removeWhenFarAway(double pDistanceToClosestPlayer) {
      return false;
   }

   public static AttributeSupplier createAttributes() {
      return Mob.createMobAttributes()
         .add(Attributes.MAX_HEALTH, 50.0)
         .add(Attributes.ATTACK_DAMAGE, 50.0)
         .add(Attributes.MOVEMENT_SPEED, 0.5)
         .add(Attributes.FLYING_SPEED, 2.4F)
         .build();
   }

   protected void registerGoals() {
      this.goalSelector.addGoal(1, new FloatGoal(this));
   }

   protected void defineSynchedData(Builder builder) {
      super.defineSynchedData(builder);
      builder.define(IS_OPEN, false);
   }

   public void travel(Vec3 pTravelVector) {
      if (this.isAlive() && this.getControllingPassenger() instanceof Player passenger) {
         this.setYRot(passenger.getYRot());
         this.yRotO = this.getYRot();
         this.setXRot(passenger.getXRot() * 0.5F);
         this.setRot(this.getYRot(), this.getXRot());
         this.yBodyRot = this.getYRot();
         this.yHeadRot = this.getYRot();
         double speed = this.getAttributeValue(Attributes.FLYING_SPEED) * 0.45;
         double verticalSpeed = 0.0;
         if (this.level().isClientSide) {
            if (Minecraft.getInstance().options.keyJump.isDown()) {
               verticalSpeed = 0.35;
            } else if (KeyBinds.SECOND_FUNCTION_KEY.isDown()) {
               verticalSpeed = -0.35;
            }
         }

         float forwardInput = passenger.zza;
         float strafeInput = passenger.xxa;
         Vec3 inputVector = new Vec3((double)strafeInput, 0.0, (double)forwardInput);
         Vec3 moveVector = inputVector.yRot((float)(-Math.toRadians((double)this.getYRot())));
         if (moveVector.lengthSqr() > 1.0E-7) {
            moveVector = moveVector.normalize().scale(speed);
         }

         this.setDeltaMovement(moveVector.x, verticalSpeed, moveVector.z);
         this.move(MoverType.SELF, this.getDeltaMovement());
      } else {
         Vec3 currentMotion = this.getDeltaMovement();
         this.setDeltaMovement(currentMotion.x * 0.9, -0.07, currentMotion.z * 0.9);
         super.travel(pTravelVector);
      }
   }

   public LivingEntity getControllingPassenger() {
      return this.getFirstPassenger() instanceof LivingEntity entity ? entity : null;
   }

   protected Vec3 getPassengerAttachmentPoint(Entity entity, EntityDimensions dimensions, float partialTick) {
      return new Vec3(0.0, 0.4, 0.0);
   }

   public void positionRider(Entity passenger, MoveFunction callback) {
      if (this.hasPassenger(passenger)) {
         double yOffset = 0.4;
         Vec3 vec3 = new Vec3(0.0, 0.0, 0.0).yRot(-this.getYRot() * (float) (Math.PI / 180.0) - (float) (Math.PI / 2));
         callback.accept(passenger, this.getX() + vec3.x, this.getY() + yOffset, this.getZ() + vec3.z);
         if (passenger instanceof LivingEntity livingPassenger) {
            livingPassenger.yBodyRot = this.getYRot();
            livingPassenger.setYHeadRot(livingPassenger.getYHeadRot());
         }
      }
   }

   protected InteractionResult mobInteract(Player player, InteractionHand hand) {
      if (!this.level().isClientSide) {
         if (!this.isOpen()) {
            if (!player.isPassenger()) {
               this.setOpenNave(true);
               player.level().playSound(null, this.getOnPos(), (SoundEvent)MainSounds.NAVE_OPEN.get(), SoundSource.NEUTRAL, 0.5F, 1.0F);
            }
         } else {
            this.setOpenNave(false);
            if (!player.isPassenger()) {
               player.startRiding(this);
            }
         }
      }

      return InteractionResult.SUCCESS;
   }

   public boolean isOpen() {
      return (Boolean)this.entityData.get(IS_OPEN);
   }

   public void setOpenNave(boolean open) {
      this.entityData.set(IS_OPEN, open);
   }

   public boolean hurt(DamageSource pSource, float pAmount) {
      if ("player".equals(pSource.getMsgId()) && pSource.getEntity() instanceof Player) {
         if (!this.level().isClientSide && this.isAlive()) {
            this.spawnAtLocation((ItemLike)MainItems.NAVE_SAIYAN_ITEM.get());
            this.remove(RemovalReason.KILLED);
         }

         return true;
      } else {
         return false;
      }
   }

   public boolean isInvulnerableTo(DamageSource pSource) {
      return !"player".equals(pSource.getMsgId()) || super.isInvulnerableTo(pSource);
   }

   public boolean causeFallDamage(float pFallDistance, float pMultiplier, DamageSource pSource) {
      return false;
   }

   public void registerControllers(ControllerRegistrar controllerRegistrar) {
      controllerRegistrar.add(new AnimationController(this, "controller", 0, this::predicate));
   }

   private <T extends GeoAnimatable> PlayState predicate(AnimationState<T> tAnimationState) {
      AnimationController<?> controller = tAnimationState.getController();
      if (this.isOpen()) {
         controller.setAnimation(ANIM_ABIERTO);
      } else {
         controller.setAnimation(ANIM_CERRADO);
      }

      return PlayState.CONTINUE;
   }

   public AnimatableInstanceCache getAnimatableInstanceCache() {
      return this.geoCache;
   }
}
