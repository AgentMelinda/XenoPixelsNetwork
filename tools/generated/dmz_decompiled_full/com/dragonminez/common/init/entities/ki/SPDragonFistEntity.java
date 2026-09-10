package com.dragonminez.common.init.entities.ki;

import com.dragonminez.common.init.MainDamageTypes;
import com.dragonminez.common.init.MainEntities;
import com.dragonminez.common.init.MainParticles;
import com.dragonminez.common.init.MainSounds;
import com.dragonminez.common.init.particles.KiTrailParticle;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.network.syncher.SynchedEntityData.Builder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.instance.SingletonAnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.animation.AnimatableManager.ControllerRegistrar;

public class SPDragonFistEntity extends AbstractKiProjectile implements GeoEntity {
   private static final EntityDataAccessor<Float> LOCKED_YAW = SynchedEntityData.defineId(SPDragonFistEntity.class, EntityDataSerializers.FLOAT);
   private static final EntityDataAccessor<Float> LOCKED_PITCH = SynchedEntityData.defineId(SPDragonFistEntity.class, EntityDataSerializers.FLOAT);
   private final AnimatableInstanceCache geoCache = new SingletonAnimatableInstanceCache(this);
   private Vec3 fixedDirection = null;

   public SPDragonFistEntity(EntityType<? extends Projectile> pEntityType, Level pLevel) {
      super(pEntityType, pLevel);
      this.setNoGravity(true);
      this.noPhysics = true;
   }

   public SPDragonFistEntity(Level level, LivingEntity owner) {
      super((EntityType<? extends Projectile>)MainEntities.SP_DRAGON_FIST.get(), level);
      this.setOwner(owner);
      this.setNoGravity(true);
      this.noPhysics = true;
   }

   @Override
   public int getMaxHits() {
      return this.getMaxLife() / 20;
   }

   public void setupDragonFist(LivingEntity owner, float damage, float speed) {
      this.setup(owner, damage, 1.5F, speed, 16766720, 16747520);
      this.setFiring(true);
      this.setMaxLife(40);
      float yaw = owner.getYHeadRot();
      float pitch = owner.getXRot();
      this.entityData.set(LOCKED_YAW, yaw);
      this.entityData.set(LOCKED_PITCH, pitch);
      this.setYRot(yaw);
      this.setXRot(pitch);
      this.setKiDamage(damage);
      Vec3 spawnPos = owner.position().add(Vec3.directionFromRotation(pitch, yaw).normalize().scale(1.5));
      this.setPos(spawnPos.x, owner.getY(), spawnPos.z);
      this.setBoundingBox(this.getDimensions(this.getPose()).makeBoundingBox(this.position()));
      this.level().playSound(null, owner, (SoundEvent)MainSounds.DRAGON_FIST.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
      if (!this.level().isClientSide) {
         this.level().addFreshEntity(this);
      }
   }

   @Override
   public void tick() {
      this.baseTick();
      this.onKiTick();
      Entity owner = this.getOwner();
      if (owner != null && owner.isAlive()) {
         if (this.fixedDirection == null) {
            float yaw = (Float)this.entityData.get(LOCKED_YAW);
            float pitch = (Float)this.entityData.get(LOCKED_PITCH);
            this.fixedDirection = Vec3.directionFromRotation(pitch, yaw).normalize();
            if (yaw == 0.0F && pitch == 0.0F) {
               this.fixedDirection = owner.getViewVector(1.0F).normalize();
            }
         }

         int currentTick = this.tickCount;
         Vec3 dragonBase = owner.position().add(0.0, (double)owner.getBbHeight() * 0.5, 0.0);
         Vec3 dragonPos = this.clipAgainstBlocks(dragonBase, dragonBase.add(this.fixedDirection.scale(1.5)));
         this.setPos(dragonPos.x, owner.getY(), dragonPos.z);
         this.setBoundingBox(this.getDimensions(this.getPose()).makeBoundingBox(this.position()));
         if (currentTick < this.getMaxLife()) {
            owner.setDeltaMovement(this.fixedDirection.scale(2.5).add(0.0, 0.1, 0.0));
            owner.hasImpulse = true;
            owner.fallDistance = 0.0F;
            if (currentTick % 10 == 0) {
               this.level()
                  .playSound(null, this.getX(), this.getY(), this.getZ(), (SoundEvent)MainSounds.KI_EXPLOSION_IMPACT.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
            }

            if (!this.level().isClientSide) {
               this.devastateEnemies(owner, this.fixedDirection);
            } else {
               this.spawnDragonAuraParticles(owner, this.fixedDirection);
            }
         } else {
            owner.setDeltaMovement(0.0, 0.0, 0.0);
            this.discard();
         }
      } else {
         if (!this.level().isClientSide) {
            this.discard();
         }
      }
   }

   private void devastateEnemies(Entity owner, Vec3 fixedDirection) {
      AABB hitbox = this.getBoundingBox().inflate(5.0);

      for (Entity target : this.level().getEntities(this, hitbox, this::shouldDamage)) {
         if (target.invulnerableTime <= 0) {
            boolean hit = target.hurt(MainDamageTypes.strikeAttack(this.level(), owner, "dragon_fist"), this.getDamagePerHit());
            if (hit) {
               target.invulnerableTime = 20;
               this.onSuccessfulHit(target);
               this.applyStrikeStun(target);
            }
         }

         this.holdTargetAtCaster(target, owner.position());
         target.setDeltaMovement(0.0, 0.0, 0.0);
         target.hasImpulse = true;
         target.fallDistance = 0.0F;
      }
   }

   private void spawnDragonAuraParticles(Entity owner, Vec3 fixedDirection) {
      float[] rgb = this.getRgbColorMain();

      for (int i = 0; i < 10; i++) {
         double dx = (this.random.nextDouble() - 0.5) * 4.0;
         double dy = (this.random.nextDouble() - 0.5) * 4.0;
         double dz = (this.random.nextDouble() - 0.5) * 4.0;
         double vx = -fixedDirection.x * 0.5;
         double vy = (this.random.nextDouble() - 0.5) * 0.2;
         double vz = -fixedDirection.z * 0.5;
         float scale = 3.0F + this.random.nextFloat() * 2.0F;
         if (Minecraft.getInstance()
            .particleEngine
            .createParticle((ParticleOptions)MainParticles.KI_TRAIL.get(), this.getX() + dx, this.getY() + 1.0 + dy, this.getZ() + dz, vx, vy, vz) instanceof KiTrailParticle trail
            )
          {
            trail.setKiColor(rgb[0], rgb[1], rgb[2]);
            trail.setKiScale(scale);
         }
      }
   }

   public float getLockedYaw() {
      return (Float)this.entityData.get(LOCKED_YAW);
   }

   public float getLockedPitch() {
      return (Float)this.entityData.get(LOCKED_PITCH);
   }

   @Override
   protected void defineSynchedData(Builder builder) {
      super.defineSynchedData(builder);
      builder.define(LOCKED_YAW, 0.0F);
      builder.define(LOCKED_PITCH, 0.0F);
   }

   @Override
   protected void addAdditionalSaveData(CompoundTag pCompound) {
      super.addAdditionalSaveData(pCompound);
      pCompound.putFloat("LockedYaw", (Float)this.entityData.get(LOCKED_YAW));
      pCompound.putFloat("LockedPitch", (Float)this.entityData.get(LOCKED_PITCH));
   }

   @Override
   protected void readAdditionalSaveData(CompoundTag pCompound) {
      super.readAdditionalSaveData(pCompound);
      if (pCompound.contains("LockedYaw")) {
         this.entityData.set(LOCKED_YAW, pCompound.getFloat("LockedYaw"));
      }

      if (pCompound.contains("LockedPitch")) {
         this.entityData.set(LOCKED_PITCH, pCompound.getFloat("LockedPitch"));
      }
   }

   public void registerControllers(ControllerRegistrar controllers) {
      controllers.add(new AnimationController(this, "controller", 0, this::predicate));
   }

   private PlayState predicate(AnimationState<SPDragonFistEntity> event) {
      return event.setAndContinue(RawAnimation.begin().thenLoop("idle"));
   }

   public AnimatableInstanceCache getAnimatableInstanceCache() {
      return this.geoCache;
   }
}
