package com.dragonminez.common.init.entities.ki;

import com.dragonminez.common.init.MainEntities;
import com.dragonminez.common.init.MainParticles;
import com.dragonminez.common.init.MainSounds;
import com.dragonminez.common.init.particles.KiTrailParticle;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.nbt.CompoundTag;
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

public class OzaruFistEntity extends AbstractKiProjectile implements GeoEntity {
   private final AnimatableInstanceCache geoCache = new SingletonAnimatableInstanceCache(this);

   public OzaruFistEntity(EntityType<? extends Projectile> pEntityType, Level pLevel) {
      super(pEntityType, pLevel);
      this.setNoGravity(true);
      this.noPhysics = true;
   }

   public OzaruFistEntity(Level level, LivingEntity owner) {
      super((EntityType<? extends Projectile>)MainEntities.SP_OZARU_FIST.get(), level);
      this.setOwner(owner);
      this.setNoGravity(true);
      this.noPhysics = true;
   }

   @Override
   public int getMaxHits() {
      return Math.max(1, (this.getMaxLife() + 20 - 1) / 20);
   }

   public void setupOzaruFist(LivingEntity owner, float damage, float speed) {
      this.setup(owner, damage, 1.5F, speed, 16777215, 9127187);
      this.setFiring(true);
      this.setMaxLife(30);
      this.setKiDamage(damage);
      this.setPos(owner.getX(), owner.getY(), owner.getZ());
      this.setBoundingBox(this.getDimensions(this.getPose()).makeBoundingBox(this.position()));
      this.level().playSound(null, owner, (SoundEvent)MainSounds.OOZARU_FIST.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
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
         int currentTick = this.tickCount;
         this.setPos(owner.getX(), owner.getY(), owner.getZ());
         this.setBoundingBox(this.getDimensions(this.getPose()).makeBoundingBox(this.position()));
         if (currentTick < this.getMaxLife()) {
            double upwardForce = 1.8;
            owner.setDeltaMovement(0.0, upwardForce, 0.0);
            owner.hasImpulse = true;
            owner.fallDistance = 0.0F;
            if (!this.level().isClientSide) {
               this.devastateEnemies(owner, upwardForce);
            } else {
               this.spawnAuraParticles(owner);
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

   private void devastateEnemies(Entity owner, double upwardForce) {
      AABB hitbox = this.getBoundingBox().inflate(4.0, 2.0, 4.0);
      List<Entity> targets = this.level().getEntities(this, hitbox, this::shouldDamage);
      double holdHeight = 2.0;

      for (Entity target : targets) {
         if (this.applyContinuousDamage(target)) {
            this.onSuccessfulHit(target);
            this.applyStrikeStun(target);
         }

         Vec3 ownerCenter = new Vec3(owner.getX(), owner.getY() + (double)owner.getBbHeight() * 0.5, owner.getZ());
         Vec3 holdPos = this.clipAgainstBlocks(ownerCenter, new Vec3(owner.getX(), owner.getY() + holdHeight, owner.getZ()));
         this.moveEntityTowards(target, holdPos);
         target.setDeltaMovement(0.0, upwardForce, 0.0);
         target.hasImpulse = true;
         target.fallDistance = 0.0F;
      }
   }

   private void spawnAuraParticles(Entity owner) {
      float[] rgb = this.getRgbColorMain();

      for (int i = 0; i < 10; i++) {
         double dx = (this.random.nextDouble() - 0.5) * 4.0;
         double dy = (this.random.nextDouble() - 0.5) * 4.0;
         double dz = (this.random.nextDouble() - 0.5) * 4.0;
         double vx = (this.random.nextDouble() - 0.5) * 0.2;
         double vy = -1.5;
         double vz = (this.random.nextDouble() - 0.5) * 0.2;
         float scale = 3.0F + this.random.nextFloat() * 2.0F;
         if (Minecraft.getInstance()
            .particleEngine
            .createParticle((ParticleOptions)MainParticles.KI_TRAIL.get(), this.getX() + dx, this.getY() + dy, this.getZ() + dz, vx, vy, vz) instanceof KiTrailParticle trail
            )
          {
            trail.setKiColor(rgb[0], rgb[1], rgb[2]);
            trail.setKiScale(scale);
         }
      }
   }

   @Override
   protected void defineSynchedData(Builder builder) {
      super.defineSynchedData(builder);
   }

   @Override
   protected void addAdditionalSaveData(CompoundTag pCompound) {
      super.addAdditionalSaveData(pCompound);
   }

   @Override
   protected void readAdditionalSaveData(CompoundTag pCompound) {
      super.readAdditionalSaveData(pCompound);
   }

   public void registerControllers(ControllerRegistrar controllers) {
      controllers.add(new AnimationController(this, "controller", 0, this::predicate));
   }

   private PlayState predicate(AnimationState<OzaruFistEntity> event) {
      return event.setAndContinue(RawAnimation.begin().thenLoop("idle"));
   }

   public AnimatableInstanceCache getAnimatableInstanceCache() {
      return this.geoCache;
   }
}
