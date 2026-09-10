package com.dragonminez.common.init.entities.ki;

import com.dragonminez.client.util.ColorUtils;
import com.dragonminez.common.combat.util.MultipartTargeting;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.instance.SingletonAnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.animation.AnimatableManager.ControllerRegistrar;

public class SPBlueHurricaneEntity extends AbstractKiProjectile implements GeoEntity {
   private static final EntityDataAccessor<Integer> CAST_TIME = SynchedEntityData.defineId(SPBlueHurricaneEntity.class, EntityDataSerializers.INT);
   private static final EntityDataAccessor<Boolean> IS_FIRING = SynchedEntityData.defineId(SPBlueHurricaneEntity.class, EntityDataSerializers.BOOLEAN);
   private final AnimatableInstanceCache geoCache = new SingletonAnimatableInstanceCache(this);
   private static final int FIRING_WINDOW = 140;

   public SPBlueHurricaneEntity(EntityType<? extends Projectile> pEntityType, Level pLevel) {
      super(pEntityType, pLevel);
      this.setNoGravity(true);
   }

   public SPBlueHurricaneEntity(Level level, LivingEntity owner) {
      super((EntityType<? extends Projectile>)MainEntities.SP_BLUE_HURRICANE.get(), level);
      this.setOwner(owner);
      this.setNoGravity(true);
   }

   @Override
   public int getMaxHits() {
      return Math.max(1, 7);
   }

   public void setupHurricane(LivingEntity owner, float damage, float speed, int castTime) {
      this.setOwner(owner);
      this.setKiDamage(damage);
      this.setKiSpeed(speed);
      this.setCastTime(castTime);
      this.setFiring(false);
      this.setPos(owner.getX(), owner.getY(), owner.getZ());
      this.setYRot(owner.getYRot());
      this.setXRot(owner.getXRot());
      this.playInitialSound((SoundEvent)MainSounds.KI_EXPLOSION_CHARGE.get());
      if (!this.level().isClientSide) {
         this.level().addFreshEntity(this);
      }
   }

   @Override
   public void tick() {
      this.baseTick();
      Entity owner = this.getOwner();
      if (owner != null && owner.isAlive()) {
         boolean isFiring = this.isFiring();
         if (!isFiring && this.tickCount >= this.getCastTime()) {
            this.setFiring(true);
            isFiring = true;
            if (!this.level().isClientSide) {
               this.level()
                  .playSound(null, this.getX(), this.getY(), this.getZ(), (SoundEvent)MainSounds.KIBLAST_ATTACK.get(), SoundSource.PLAYERS, 1.5F, 0.8F);
            }
         }

         if (!isFiring) {
            this.setPos(owner.getX(), owner.getY(), owner.getZ());
            double preserveGravity = owner.getDeltaMovement().y < 0.0 ? owner.getDeltaMovement().y : 0.0;
            owner.setDeltaMovement(0.0, preserveGravity, 0.0);
            owner.hasImpulse = true;
            if (owner instanceof Player player) {
               player.xxa = 0.0F;
               player.zza = 0.0F;
            }

            if (this.level().isClientSide) {
               float[] rgb = ColorUtils.rgbIntToFloat(4151548);

               for (int i = 0; i < 4; i++) {
                  double radius = 4.0 + this.random.nextDouble() * 2.0;
                  double theta = this.random.nextDouble() * 2.0 * Math.PI;
                  double phi = Math.acos(2.0 * this.random.nextDouble() - 1.0);
                  double offsetX = radius * Math.sin(phi) * Math.cos(theta);
                  double offsetY = radius * Math.cos(phi) + 1.0;
                  double offsetZ = radius * Math.sin(phi) * Math.sin(theta);
                  double spawnX = this.getX() + offsetX;
                  double spawnY = this.getY() + offsetY;
                  double spawnZ = this.getZ() + offsetZ;
                  double vx = this.getX() - spawnX;
                  double vy = this.getY() + 1.0 - spawnY;
                  double vz = this.getZ() - spawnZ;
                  if (Minecraft.getInstance()
                     .particleEngine
                     .createParticle((ParticleOptions)MainParticles.KI_TRAIL.get(), spawnX, spawnY, spawnZ, vx * 0.15, vy * 0.15, vz * 0.15) instanceof KiTrailParticle trail
                     )
                   {
                     trail.setKiColor(rgb[0], rgb[1], rgb[2]);
                     trail.setKiScale(1.5F + this.random.nextFloat() * 1.5F);
                  }
               }
            }
         } else {
            this.setPos(owner.getX(), owner.getY(), owner.getZ());
            this.setBoundingBox(this.getDimensions(this.getPose()).makeBoundingBox(this.position()));
            if (this.level().isClientSide) {
               float[] rgb = ColorUtils.rgbIntToFloat(4151548);

               for (int ix = 0; ix < 10; ix++) {
                  double offsetX = (this.random.nextDouble() - 1.0) * (double)this.getBbWidth();
                  double offsetY = (this.random.nextDouble() - 1.0) * (double)this.getBbHeight() * 5.0;
                  double offsetZ = (this.random.nextDouble() - 1.0) * (double)this.getBbWidth();
                  this.level()
                     .addParticle(
                        (ParticleOptions)MainParticles.KI_TRAIL.get(),
                        this.getX() + offsetX,
                        this.getY() + (double)this.getBbHeight() / 2.0 + offsetY,
                        this.getZ() + offsetZ,
                        (double)rgb[0],
                        (double)rgb[1],
                        (double)rgb[2]
                     );
               }
            }

            if (!this.level().isClientSide && this.tickCount % 10 == 0) {
               this.pulseDamage();
            }
         }

         if (this.tickCount >= this.getCastTime() + 140) {
            this.discard();
         }
      } else {
         if (!this.level().isClientSide) {
            this.discard();
         }
      }
   }

   private void pulseDamage() {
      AABB area = this.getBoundingBox().inflate(4.5, 9.0, 4.5);

      for (LivingEntity target : MultipartTargeting.collectTargets(this.level(), area)) {
         if (this.shouldDamage(target) && !target.is(this.getOwner())) {
            this.applyContinuousDamage(target);
            double dx = this.getX() - target.getX();
            double dz = this.getZ() - target.getZ();
            double distance = Math.sqrt(dx * dx + dz * dz);
            if (distance > 0.0) {
               dx /= distance;
               dz /= distance;
            }

            target.setDeltaMovement(dx * 0.4, 0.5, dz * 0.4);
            target.hasImpulse = true;
         }
      }
   }

   @Override
   protected void defineSynchedData(Builder builder) {
      super.defineSynchedData(builder);
      builder.define(CAST_TIME, 0);
      builder.define(IS_FIRING, false);
   }

   public int getCastTime() {
      return (Integer)this.entityData.get(CAST_TIME);
   }

   public void setCastTime(int ticks) {
      this.entityData.set(CAST_TIME, ticks);
   }

   @Override
   public boolean isFiring() {
      return (Boolean)this.entityData.get(IS_FIRING);
   }

   @Override
   public void setFiring(boolean firing) {
      this.entityData.set(IS_FIRING, firing);
   }

   @Override
   protected void addAdditionalSaveData(CompoundTag pCompound) {
      super.addAdditionalSaveData(pCompound);
      pCompound.putInt("CastTime", this.getCastTime());
      pCompound.putBoolean("IsFiring", this.isFiring());
   }

   @Override
   protected void readAdditionalSaveData(CompoundTag pCompound) {
      super.readAdditionalSaveData(pCompound);
      if (pCompound.contains("CastTime")) {
         this.setCastTime(pCompound.getInt("CastTime"));
      }

      if (pCompound.contains("IsFiring")) {
         this.setFiring(pCompound.getBoolean("IsFiring"));
      }
   }

   public void registerControllers(ControllerRegistrar controllers) {
      controllers.add(new AnimationController(this, "controller", 0, this::predicate));
   }

   private PlayState predicate(AnimationState<SPBlueHurricaneEntity> event) {
      return event.setAndContinue(RawAnimation.begin().thenLoop("fire"));
   }

   public AnimatableInstanceCache getAnimatableInstanceCache() {
      return this.geoCache;
   }
}
