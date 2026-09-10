package com.dragonminez.common.init.entities;

import com.dragonminez.client.util.KeyBinds;
import com.dragonminez.common.init.MainItems;
import com.dragonminez.common.init.MainParticles;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.chat.Component;
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

public class FlyingNimbusEntity extends Mob implements GeoEntity {
   private final AnimatableInstanceCache geoCache = new SingletonAnimatableInstanceCache(this);
   private static final int BOOST_DURATION = 45;
   private static final double BOOST_INITIAL = 7.2;
   private static final double BOOST_SUSTAIN = 0.65;
   private int boostTicks = 0;
   private double boostStrength = 0.0;
   private boolean boostKeyWasDown = false;

   public FlyingNimbusEntity(EntityType<? extends Mob> pEntityType, Level pLevel) {
      super(pEntityType, pLevel);
      this.setNoGravity(true);
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

   public void tick() {
      super.tick();
      if (this.level().isClientSide) {
         this.spawnAuraParticles(16775250);
      }
   }

   private void spawnAuraParticles(int colorHex) {
      int particleCount = 15;

      for (int i = 0; i < particleCount; i++) {
         double offsetX = (this.random.nextDouble() - 0.1) * (double)this.getBbWidth() * 0.01;
         double offsetY = (this.random.nextDouble() - 0.02) * (double)this.getBbHeight() * 0.01;
         double offsetZ = (this.random.nextDouble() - 0.1) * (double)this.getBbWidth() * 0.01;
         double spawnX = this.getX() + offsetX - 0.5;
         double spawnY = this.getY() + offsetY + 1.0;
         double spawnZ = this.getZ() + offsetZ;
         this.level().addParticle((ParticleOptions)MainParticles.KINTON.get(), spawnX, spawnY, spawnZ, (double)colorHex, 0.0, 0.0);
      }
   }

   private void spawnBoostTrail(int colorHex) {
      Vec3 back = new Vec3(0.0, 0.0, 1.0).yRot((float)(-Math.toRadians((double)this.getYRot()))).scale(-1.0);
      int count = 18;

      for (int i = 0; i < count; i++) {
         double spread = 0.7;
         double ox = (this.random.nextDouble() - 0.5) * spread;
         double oy = (this.random.nextDouble() - 0.5) * spread;
         double oz = (this.random.nextDouble() - 0.5) * spread;
         double dist = 0.5 + this.random.nextDouble() * 1.2;
         double spawnX = this.getX() + back.x * dist + ox;
         double spawnY = this.getY() + 1.0 + oy;
         double spawnZ = this.getZ() + back.z * dist + oz;
         this.level().addParticle((ParticleOptions)MainParticles.KINTON.get(), spawnX, spawnY, spawnZ, (double)colorHex, 0.0, 0.0);
      }
   }

   private void spawnBoostBurst(int colorHex) {
      Vec3 back = new Vec3(0.0, 0.0, 1.0).yRot((float)(-Math.toRadians((double)this.getYRot()))).scale(-1.0);
      double centerX = this.getX() + back.x * 0.8;
      double centerY = this.getY() + 1.0;
      double centerZ = this.getZ() + back.z * 0.8;
      int count = 60;

      for (int i = 0; i < count; i++) {
         double radius = this.random.nextDouble() * 1.6;
         double theta = this.random.nextDouble() * Math.PI * 2.0;
         double phi = (this.random.nextDouble() - 0.5) * Math.PI;
         double spawnX = centerX + Math.cos(theta) * Math.cos(phi) * radius;
         double spawnY = centerY + Math.sin(phi) * radius;
         double spawnZ = centerZ + Math.sin(theta) * Math.cos(phi) * radius;
         this.level().addParticle((ParticleOptions)MainParticles.KINTON.get(), spawnX, spawnY, spawnZ, (double)colorHex, 0.0, 0.0);
      }
   }

   public void travel(Vec3 pTravelVector) {
      if (this.isAlive()) {
         Entity controllingPassenger = this.getControllingPassenger();
         if (controllingPassenger instanceof Player passenger) {
            this.setYRot(passenger.getYRot());
            this.yRotO = this.getYRot();
            this.setXRot(passenger.getXRot() * 0.5F);
            this.setRot(this.getYRot(), this.getXRot());
            this.yBodyRot = this.getYRot();
            double speed = this.getAttributeValue(Attributes.FLYING_SPEED) * 0.5;
            double verticalSpeed = 0.0;
            if (this.level().isClientSide) {
               if (Minecraft.getInstance().options.keyJump.isDown()) {
                  verticalSpeed = 0.4;
               } else if (KeyBinds.SECOND_FUNCTION_KEY.isDown()) {
                  verticalSpeed = -0.4;
               }
            }

            float forwardInput = passenger.zza;
            float strafeInput = passenger.xxa;
            Vec3 inputVector = new Vec3((double)strafeInput, 0.0, (double)forwardInput);
            Vec3 moveVector = inputVector.yRot((float)(-Math.toRadians((double)this.getYRot())));
            if (moveVector.lengthSqr() > 1.0E-7) {
               moveVector = moveVector.normalize().scale(speed);
            }

            if (this.level().isClientSide) {
               boolean keyDown = KeyBinds.DASH_KEY.isDown();
               if (keyDown && !this.boostKeyWasDown && this.boostTicks <= 0) {
                  this.boostTicks = 45;
                  this.boostStrength = 7.2;
                  this.spawnBoostBurst(16775250);
               }

               this.boostKeyWasDown = keyDown;
               if (this.boostTicks > 0) {
                  this.boostTicks--;
                  this.boostStrength = 0.65 + (this.boostStrength - 0.65) * 0.8;
               } else if (this.boostStrength > 0.01) {
                  this.boostStrength *= 0.88;
                  if (this.boostStrength < 0.01) {
                     this.boostStrength = 0.0;
                  }
               }

               if (this.boostStrength > 0.05) {
                  this.spawnBoostTrail(16775250);
               }
            }

            if (this.boostStrength > 0.01) {
               Vec3 forward = new Vec3(0.0, 0.0, 1.0).yRot((float)(-Math.toRadians((double)this.getYRot())));
               moveVector = moveVector.add(forward.scale(this.boostStrength));
            }

            this.setDeltaMovement(moveVector.x, verticalSpeed, moveVector.z);
            this.move(MoverType.SELF, this.getDeltaMovement());
            return;
         }
      }

      super.travel(pTravelVector);
   }

   public LivingEntity getControllingPassenger() {
      return this.getFirstPassenger() instanceof LivingEntity entity ? entity : null;
   }

   protected Vec3 getPassengerAttachmentPoint(Entity entity, EntityDimensions dimensions, float partialTick) {
      return new Vec3(0.0, 0.9, 0.0);
   }

   public void positionRider(Entity passenger, MoveFunction callback) {
      if (this.hasPassenger(passenger)) {
         int index = this.getPassengers().indexOf(passenger);
         float xOffset = 0.0F;
         float zOffset = index == 0 ? 0.4F : -0.4F;
         double yOffset = 0.4;
         float yaw = -this.getYRot() * (float) (Math.PI / 180.0);
         Vec3 vec3 = new Vec3((double)xOffset, 0.0, (double)zOffset).yRot(yaw);
         callback.accept(passenger, this.getX() + vec3.x, this.getY() + yOffset, this.getZ() + vec3.z);
      }
   }

   protected InteractionResult mobInteract(Player player, InteractionHand hand) {
      if (!this.level().isClientSide) {
         int alignment = StatsProvider.get(StatsCapability.INSTANCE, player).map(data -> data.getResources().getAlignment()).orElse(0);
         if (alignment <= 66) {
            player.displayClientMessage(Component.translatable("message.dragonminez.nimbus.not_pure"), true);
            return InteractionResult.SUCCESS;
         }

         player.startRiding(this);
      }

      return InteractionResult.SUCCESS;
   }

   public boolean hurt(DamageSource pSource, float pAmount) {
      if ("player".equals(pSource.getMsgId()) && pSource.getEntity() instanceof Player) {
         if (!this.level().isClientSide && this.isAlive()) {
            this.spawnAtLocation((ItemLike)MainItems.NUBE_ITEM.get());
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

   protected boolean canAddPassenger(Entity pPassenger) {
      return this.getPassengers().size() < 2;
   }

   private <T extends GeoAnimatable> PlayState predicate(AnimationState<T> tAnimationState) {
      tAnimationState.getController().setAnimation(RawAnimation.begin().thenLoop("idle"));
      return PlayState.CONTINUE;
   }

   public AnimatableInstanceCache getAnimatableInstanceCache() {
      return this.geoCache;
   }
}
