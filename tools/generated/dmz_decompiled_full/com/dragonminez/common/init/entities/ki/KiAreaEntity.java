package com.dragonminez.common.init.entities.ki;

import com.dragonminez.client.util.ColorUtils;
import com.dragonminez.common.combat.util.MultipartTargeting;
import com.dragonminez.common.init.MainEntities;
import com.dragonminez.common.init.MainParticles;
import com.dragonminez.common.init.particles.KiExplosionSplashParticle;
import com.dragonminez.common.init.particles.KiTrailParticle;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.network.syncher.SynchedEntityData.Builder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

public class KiAreaEntity extends AbstractKiProjectile {
   private static final int DEFAULT_DURATION = 200;
   private static final EntityDataAccessor<Float> AREA_RADIUS = SynchedEntityData.defineId(KiAreaEntity.class, EntityDataSerializers.FLOAT);
   private static final EntityDataAccessor<Integer> CAST_TIME = SynchedEntityData.defineId(KiAreaEntity.class, EntityDataSerializers.INT);

   public KiAreaEntity(EntityType<? extends Projectile> type, Level level) {
      super(type, level);
      this.setNoGravity(true);
      this.noPhysics = true;
   }

   public KiAreaEntity(Level level, LivingEntity owner) {
      super((EntityType<? extends Projectile>)MainEntities.KI_AREA.get(), level);
      this.setOwner(owner);
      this.setNoGravity(true);
      this.noPhysics = true;
   }

   @Override
   public int getMaxHits() {
      return Math.max(1, this.firingWindowTicks() / 10);
   }

   public void setupAreaPlayer(LivingEntity owner, float damage, float radius, int colorMain, int colorBorder, int colorOutline) {
      this.setOwner(owner);
      this.setKiDamage(damage);
      this.setAreaRadius(radius);
      this.setColors(colorMain, colorBorder, colorOutline);
      this.setFiring(false);
      this.setCastTime(40);
      this.setMaxLife(99999);
      this.updatePositionToOwner();
      if (!this.level().isClientSide) {
         this.level().addFreshEntity(this);
      }
   }

   public void fireHability(int durationTicks) {
      this.setFiring(true);
      int finalDuration = durationTicks > 0 ? durationTicks : 200;
      this.setMaxLife(this.tickCount + finalDuration);
      this.setFireTick(this.tickCount);
      if (this.getOwner() instanceof Player) {
         this.triggerAnimationPacket("_fire");
      }
   }

   @Override
   protected void defineSynchedData(Builder builder) {
      super.defineSynchedData(builder);
      builder.define(AREA_RADIUS, 5.0F);
      builder.define(CAST_TIME, 40);
   }

   @Override
   public void tick() {
      this.baseTick();
      if (this.getOwner() != null && this.getOwner().isAlive()) {
         if (this.tickCount >= this.getMaxLife()) {
            if (!this.level().isClientSide) {
               this.discard();
            }
         } else {
            this.updatePositionToOwner();
            this.onKiTick();
         }
      } else {
         if (!this.level().isClientSide) {
            this.discard();
         }
      }
   }

   private void updatePositionToOwner() {
      Entity owner = this.getOwner();
      if (owner != null) {
         this.setPos(owner.getX(), owner.getY(), owner.getZ());
         float r = this.getAreaRadius();
         this.setBoundingBox(
            new AABB(
               this.getX() - (double)r, this.getY() - 1.0, this.getZ() - (double)r, this.getX() + (double)r, this.getY() + (double)r, this.getZ() + (double)r
            )
         );
      }
   }

   @Override
   protected void onKiTick() {
      boolean isFiring = this.isFiring();
      float radius = this.getAreaRadius();
      if (!this.level().isClientSide) {
         if (isFiring && this.tickCount % 10 == 0) {
            this.applyAreaEffects(radius);
         }
      } else {
         this.spawnAreaParticles(radius, isFiring);
      }
   }

   private void applyAreaEffects(float radius) {
      AABB area = this.getBoundingBox();

      for (LivingEntity target : MultipartTargeting.collectTargets(this.level(), area)) {
         if (this.isHeal()) {
            if (target == this.getOwner() || !this.shouldDamage(target)) {
               this.applyDamageOrHeal(target, this.getDamagePerHit());
               this.onSuccessfulHit(target);
            }
         } else if (target != this.getOwner() && this.shouldDamage(target)) {
            this.applyDamageOrHeal(target, this.getDamagePerHit());
            this.onSuccessfulHit(target);
         }
      }
   }

   private void spawnAreaParticles(float radius, boolean isFiring) {
      float[] rgb = ColorUtils.rgbIntToFloat(isFiring ? this.getColor() : this.getColorBorder());
      if (isFiring) {
         if (this.tickCount % 4 == 0) {
            for (int i = 0; i < 6; i++) {
               double angle = this.random.nextDouble() * Math.PI * 2.0;
               double px = this.getX() + Math.cos(angle) * (double)radius;
               double pz = this.getZ() + Math.sin(angle) * (double)radius;
               this.spawnAreaVisual(px, this.getY() + 0.1, pz, rgb);
            }
         }
      } else {
         double offsetX = (this.random.nextDouble() - 0.5) * 2.0 * (double)radius;
         double offsetZ = (this.random.nextDouble() - 0.5) * 2.0 * (double)radius;
         this.spawnChargeTrail(this.getX() + offsetX, this.getY() + 0.5, this.getZ() + offsetZ, rgb);
      }
   }

   private void spawnAreaVisual(double x, double y, double z, float[] rgb) {
      if (Minecraft.getInstance().particleEngine.createParticle((ParticleOptions)MainParticles.KI_EXPLOSION_SPLASH.get(), x, y, z, 0.0, 0.0, 0.0) instanceof KiExplosionSplashParticle splash
         )
       {
         splash.setSplashColor(rgb[0], rgb[1], rgb[2]);
         splash.setSplashScale(this.getAreaRadius() * 0.25F);
      }
   }

   private void spawnChargeTrail(double x, double y, double z, float[] rgb) {
      double vx = (this.getX() - x) * 0.15;
      double vz = (this.getZ() - z) * 0.15;
      if (Minecraft.getInstance().particleEngine.createParticle((ParticleOptions)MainParticles.KI_TRAIL.get(), x, y, z, vx, 0.05, vz) instanceof KiTrailParticle trail
         )
       {
         trail.setKiColor(rgb[0], rgb[1], rgb[2]);
      }
   }

   public void setAreaRadius(float r) {
      this.entityData.set(AREA_RADIUS, r);
   }

   public float getAreaRadius() {
      return (Float)this.entityData.get(AREA_RADIUS);
   }

   public void setCastTime(int t) {
      this.entityData.set(CAST_TIME, t);
   }

   public int getCastTime() {
      return (Integer)this.entityData.get(CAST_TIME);
   }

   @Override
   public void addAdditionalSaveData(CompoundTag tag) {
      super.addAdditionalSaveData(tag);
      tag.putFloat("AreaRadius", this.getAreaRadius());
   }

   @Override
   public void readAdditionalSaveData(CompoundTag tag) {
      super.readAdditionalSaveData(tag);
      this.setAreaRadius(tag.getFloat("AreaRadius"));
   }
}
