package com.dragonminez.common.init.entities.ki;

import com.dragonminez.client.util.ColorUtils;
import com.dragonminez.common.combat.logic.player.TargetHelper;
import com.dragonminez.common.combat.util.MultipartTargeting;
import com.dragonminez.common.init.MainDamageTypes;
import com.dragonminez.common.init.MainEffects;
import com.dragonminez.common.init.MainEntities;
import com.dragonminez.common.init.MainParticles;
import com.dragonminez.common.init.MainSounds;
import com.dragonminez.common.init.particles.KiTrailParticle;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.arguments.EntityAnchorArgument.Anchor;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.network.syncher.SynchedEntityData.Builder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;

public class SPMajinCandyEntity extends AbstractKiProjectile {
   private static final EntityDataAccessor<Integer> CAST_TIME = SynchedEntityData.defineId(SPMajinCandyEntity.class, EntityDataSerializers.INT);
   private static final EntityDataAccessor<Boolean> IS_FIRING = SynchedEntityData.defineId(SPMajinCandyEntity.class, EntityDataSerializers.BOOLEAN);
   private static final EntityDataAccessor<Integer> TARGET_ID = SynchedEntityData.defineId(SPMajinCandyEntity.class, EntityDataSerializers.INT);
   private static final int FIRING_WINDOW = 60;

   public SPMajinCandyEntity(EntityType<? extends Projectile> pEntityType, Level pLevel) {
      super(pEntityType, pLevel);
      this.setNoGravity(true);
   }

   public SPMajinCandyEntity(Level level, LivingEntity owner) {
      super((EntityType<? extends Projectile>)MainEntities.SP_MAJIN_CANDY.get(), level);
      this.setOwner(owner);
      this.setNoGravity(true);
   }

   @Override
   public int getMaxHits() {
      return Math.max(1, 3);
   }

   public void setupCandyBeam(LivingEntity owner, float damage, float speed, int castTime) {
      this.setOwner(owner);
      this.setKiDamage(damage);
      this.setKiSpeed(speed);
      this.setCastTime(castTime);
      this.setFiring(false);
      this.setTargetId(-1);
      this.setPos(owner.getX(), owner.getY() + (double)owner.getEyeHeight() * 0.8, owner.getZ());
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
      if (this.getOwner() instanceof LivingEntity owner && owner.isAlive()) {
         boolean isFiring = this.isFiring();
         if (!isFiring && this.tickCount >= this.getCastTime()) {
            this.setFiring(true);
            isFiring = true;
            if (!this.level().isClientSide) {
               this.level()
                  .playSound(null, this.getX(), this.getY(), this.getZ(), (SoundEvent)MainSounds.KIBLAST_ATTACK.get(), SoundSource.PLAYERS, 1.5F, 1.2F);
               LivingEntity nearest = this.findNearestTarget(owner, 20.0);
               if (nearest != null) {
                  this.setTargetId(nearest.getId());
               }
            }
         }

         if (!isFiring) {
            this.setPos(owner.getX(), owner.getY() + (double)owner.getEyeHeight() * 0.8, owner.getZ());
            double preserveGravity = owner.getDeltaMovement().y < 0.0 ? owner.getDeltaMovement().y : 0.0;
            owner.setDeltaMovement(0.0, preserveGravity, 0.0);
            owner.hasImpulse = true;
            if (owner instanceof Player player) {
               player.xxa = 0.0F;
               player.zza = 0.0F;
            }

            if (this.level().isClientSide) {
               float[] rgb = ColorUtils.rgbIntToFloat(16737996);

               for (int i = 0; i < 4; i++) {
                  double radius = 3.0 + this.random.nextDouble() * 1.5;
                  double theta = this.random.nextDouble() * 2.0 * Math.PI;
                  double phi = Math.acos(2.0 * this.random.nextDouble() - 1.0);
                  double offsetX = radius * Math.sin(phi) * Math.cos(theta);
                  double offsetY = radius * Math.cos(phi);
                  double offsetZ = radius * Math.sin(phi) * Math.sin(theta);
                  double spawnX = this.getX() + offsetX;
                  double spawnY = this.getY() + offsetY;
                  double spawnZ = this.getZ() + offsetZ;
                  double vx = this.getX() - spawnX;
                  double vy = this.getY() - spawnY;
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
            this.setPos(owner.getX(), owner.getY() + (double)owner.getEyeHeight() * 0.8, owner.getZ());
            LivingEntity target = this.getTargetEntity();
            if (target != null && target.isAlive() && (double)target.distanceTo(owner) <= 25.0) {
               owner.lookAt(Anchor.EYES, target.getEyePosition());
               if (!this.level().isClientSide && this.applyContinuousDamage(target)) {
                  target.addEffect(new MobEffectInstance(MainEffects.CANDY, 200, 0, false, true));
               }
            } else if (!this.level().isClientSide) {
               this.discard();
            }
         }

         if (this.tickCount >= this.getCastTime() + 60) {
            this.discard();
         }

         return;
      }

      if (!this.level().isClientSide) {
         this.discard();
      }
   }

   protected void onHitEntity(EntityHitResult result) {
      if (!this.level().isClientSide) {
         Entity hitEntity = TargetHelper.resolveHittable(result.getEntity());
         Entity owner = this.getOwner();
         if (hitEntity instanceof LivingEntity target && target != owner) {
            target.hurt(MainDamageTypes.kiblast(this.level(), this, (LivingEntity)owner), this.getKiDamage());
            target.addEffect(new MobEffectInstance(MainEffects.CANDY, 100, 0, false, true));
            this.discard();
         }
      }
   }

   private LivingEntity findNearestTarget(LivingEntity owner, double range) {
      AABB area = owner.getBoundingBox().inflate(range);
      List<LivingEntity> targets = MultipartTargeting.collectTargets(this.level(), area);
      LivingEntity nearest = null;
      double minDistance = Double.MAX_VALUE;

      for (LivingEntity target : targets) {
         if (this.shouldDamage(target) && !target.is(owner)) {
            double distance = owner.distanceToSqr(target);
            if (distance < minDistance) {
               minDistance = distance;
               nearest = target;
            }
         }
      }

      return nearest;
   }

   public LivingEntity getTargetEntity() {
      int id = this.getTargetId();
      if (id != -1) {
         Entity entity = this.level().getEntity(id);
         if (entity instanceof LivingEntity) {
            return (LivingEntity)entity;
         }
      }

      return null;
   }

   @Override
   protected void defineSynchedData(Builder builder) {
      super.defineSynchedData(builder);
      builder.define(CAST_TIME, 0);
      builder.define(IS_FIRING, false);
      builder.define(TARGET_ID, -1);
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

   public int getTargetId() {
      return (Integer)this.entityData.get(TARGET_ID);
   }

   public void setTargetId(int id) {
      this.entityData.set(TARGET_ID, id);
   }

   @Override
   protected void addAdditionalSaveData(CompoundTag pCompound) {
      super.addAdditionalSaveData(pCompound);
      pCompound.putInt("CastTime", this.getCastTime());
      pCompound.putBoolean("IsFiring", this.isFiring());
      pCompound.putInt("TargetId", this.getTargetId());
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

      if (pCompound.contains("TargetId")) {
         this.setTargetId(pCompound.getInt("TargetId"));
      }
   }
}
