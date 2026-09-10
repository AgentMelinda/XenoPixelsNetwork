package com.dragonminez.common.init.entities.ki;

import com.dragonminez.client.util.ColorUtils;
import com.dragonminez.common.combat.util.MultipartTargeting;
import com.dragonminez.common.init.MainEntities;
import com.dragonminez.common.init.MainParticles;
import com.dragonminez.common.init.MainSounds;
import com.dragonminez.common.init.particles.KiExplosionSplashParticle;
import com.dragonminez.common.init.particles.KiTrailParticle;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

public class KiExplosionEntity extends AbstractKiProjectile {
   private static final EntityDataAccessor<Float> MAX_RADIUS = SynchedEntityData.defineId(KiExplosionEntity.class, EntityDataSerializers.FLOAT);
   private static final EntityDataAccessor<Integer> CAST_EXPLOSION = SynchedEntityData.defineId(KiExplosionEntity.class, EntityDataSerializers.INT);
   private static final EntityDataAccessor<Integer> OWNER_ID = SynchedEntityData.defineId(KiExplosionEntity.class, EntityDataSerializers.INT);
   private static final float FINAL_EXPLOSION_HP_FLOOR = 0.05F;
   private static final float FINAL_EXPLOSION_DAMAGE_GAIN = 1.8F;
   private static final float FINAL_EXPLOSION_SIZE_GAIN = 0.8F;

   public KiExplosionEntity(EntityType<? extends KiExplosionEntity> type, Level level) {
      super(type, level);
      this.setNoGravity(true);
      this.noPhysics = true;
      this.setKiType(AbstractKiProjectile.KiType.EXPLOSION);
   }

   public KiExplosionEntity(Level level, LivingEntity owner) {
      super((EntityType<? extends Projectile>)MainEntities.KI_EXPLOSION.get(), level);
      this.setOwner(owner);
      this.setNoGravity(true);
      this.noPhysics = true;
   }

   @Override
   public int getMaxHits() {
      return Math.max(1, this.firingWindowTicks() / 20);
   }

   public void setupKiExplosion(LivingEntity owner, float damage, int colorMain, int colorBorder, int colorOutline, int castTime) {
      this.setSize(2.0F);
      this.setMaxRadius(10.0F);
      this.setColors(colorMain, colorBorder, colorOutline);
      this.setKiDamage(damage);
      this.entityData.set(OWNER_ID, owner.getId());
      this.setFiring(false);
      this.setFireTick(-1);
      this.setMaxLife(castTime + 100);
      this.setCastExplosion(castTime);
      this.updatePositionToOwner(owner);
      if (!this.level().isClientSide) {
         this.level().addFreshEntity(this);
      }
   }

   public void setupKiExplosion(LivingEntity owner, float damage, int colorMain, int colorBorder, int castTime) {
      this.setupKiExplosion(owner, damage, colorMain, colorBorder, 16777215, castTime);
   }

   public void setupExplosionPlayer(LivingEntity owner, float damage, float size, int colorMain, int colorBorder, int colorOutline) {
      this.setup(owner, damage, 2.0F, 0.0F, colorMain, colorBorder, colorOutline);
      this.setMaxRadius(size);
      this.entityData.set(OWNER_ID, owner.getId());
      this.setFiring(false);
      this.setFireTick(-1);
      this.setMaxLife(99999);
      this.setCastExplosion(40);
      this.updatePositionToOwner(owner);
   }

   public void setupExplosionPlayer(LivingEntity owner, float damage, float size, int colorMain, int colorBorder) {
      this.setupExplosionPlayer(owner, damage, size, colorMain, colorBorder, 16777215);
   }

   public void fireHability(int finalMaxLife) {
      this.setFiring(true);
      this.setFireTick(this.tickCount);
      this.setMaxLife(this.tickCount + finalMaxLife);
      if (!this.level().isClientSide) {
         this.applyFinalExplosionSacrifice();
         this.createCrater(this.getMaxRadius() * 1.2F);
         this.level().playSound(null, this.getX(), this.getY(), this.getZ(), (SoundEvent)MainSounds.KI_EXPLOSION_IMPACT.get(), SoundSource.HOSTILE, 0.7F, 1.2F);
      }

      if (this.getOwner() instanceof Player) {
         this.triggerAnimationPacket("_fire");
      }
   }

   private void applyFinalExplosionSacrifice() {
      if ("final_explosion".equals(this.getTechniqueId())) {
         if (this.getOwner() instanceof LivingEntity ownerLiving && ownerLiving.isAlive()) {
            float maxHp = ownerLiving.getMaxHealth();
            if (maxHp <= 0.0F) {
               return;
            }

            float floorHp = maxHp * 0.05F;
            float consumed = ownerLiving.getHealth() - floorHp;
            if (consumed <= 0.0F) {
               return;
            }

            ownerLiving.setHealth(floorHp);
            float consumedFraction = consumed / maxHp;
            this.setKiDamage(this.getKiDamage() * (1.0F + consumedFraction * 1.8F));
            this.setMaxRadius(this.getMaxRadius() * (1.0F + consumedFraction * 0.8F));
            return;
         }
      }
   }

   @Override
   protected void defineSynchedData(Builder builder) {
      super.defineSynchedData(builder);
      builder.define(MAX_RADIUS, 15.0F);
      builder.define(OWNER_ID, -1);
      builder.define(CAST_EXPLOSION, 100);
   }

   @Override
   public void tick() {
      this.baseTick();
      if (!this.isFiring() && this.getMaxLife() != 99999 && this.tickCount >= this.getCastExplosion()) {
         this.fireHability(this.getMaxLife() - this.tickCount);
      }

      Entity owner = this.getOwner();
      if (owner == null) {
         int ownerId = (Integer)this.entityData.get(OWNER_ID);
         if (ownerId != -1) {
            owner = this.level().getEntity(ownerId);
         }
      }

      if (owner != null && owner.isAlive()) {
         if (this.tickCount >= this.getMaxLife()) {
            this.discard();
         } else {
            this.onKiTick();
         }
      } else {
         if (!this.level().isClientSide) {
            this.discard();
         }
      }
   }

   private void updatePositionToOwner(Entity owner) {
      double x = owner.getX();
      double y = owner.getY() + (double)(owner.getBbHeight() * 0.5F);
      double z = owner.getZ();
      this.setPos(x, y, z);
      this.setDeltaMovement(0.0, 0.0, 0.0);
      this.setBoundingBox(this.getDimensions(this.getPose()).makeBoundingBox(this.position()));
   }

   @Override
   protected void onKiTick() {
      float maxRad = this.getMaxRadius();
      int castTime = this.getCastExplosion();
      boolean isFiring = this.isFiring();
      Entity owner = this.getOwner();
      if (owner != null && owner.isAlive()) {
         owner.setDeltaMovement(0.0, 0.0, 0.0);
         owner.fallDistance = 0.0F;
         owner.hasImpulse = true;
         if (!isFiring && (float)this.tickCount <= (float)castTime / 2.0F) {
            double riseSpeed = 1.5 / (double)((float)castTime / 2.0F);
            this.setPos(this.getX(), this.getY() + riseSpeed, this.getZ());
         }

         owner.setPos(this.getX(), this.getY() - (double)(owner.getBbHeight() * 0.5F), this.getZ());
      }

      if (!this.level().isClientSide) {
         if (!isFiring) {
            if (this.tickCount == 1) {
               this.level()
                  .playSound(null, this.getX(), this.getY(), this.getZ(), (SoundEvent)MainSounds.KI_EXPLOSION_CHARGE.get(), SoundSource.HOSTILE, 0.7F, 1.0F);
            }
         } else {
            int activeTicks = this.tickCount - this.getFireTick();
            if (activeTicks % 70 == 0) {
               this.level()
                  .playSound(null, this.getX(), this.getY(), this.getZ(), (SoundEvent)MainSounds.KI_EXPLOSION_IMPACT.get(), SoundSource.HOSTILE, 0.2F, 1.2F);
            }

            if (activeTicks % 20 == 0) {
               this.pulseDamage(maxRad);
            }
         }
      } else {
         this.spawnParticles(maxRad, isFiring);
      }
   }

   private void pulseDamage(float radius) {
      float damageRadius = radius * 1.4F;
      AABB area = new AABB(
         this.getX() - (double)damageRadius,
         this.getY() - (double)damageRadius,
         this.getZ() - (double)damageRadius,
         this.getX() + (double)damageRadius,
         this.getY() + (double)damageRadius,
         this.getZ() + (double)damageRadius
      );

      for (LivingEntity target : MultipartTargeting.collectTargets(this.level(), area)) {
         if (this.shouldDamage(target)) {
            boolean wasHit = this.applyDamageOrHeal(target, this.getDamagePerHit() * 2.0F);
            if (wasHit && !this.isHeal()) {
               this.onSuccessfulHit(target);
               double dx = target.getX() - this.getX();
               double dz = target.getZ() - this.getZ();
               target.knockback(0.2, -dx, -dz);
            } else if (wasHit && this.isHeal()) {
               this.onSuccessfulHit(target);
            }
         }
      }
   }

   private void spawnParticles(float maxRadius, boolean isFiring) {
      float[] rgbBorder = this.getRgbColorBorder();
      float[] rgbCore = ColorUtils.rgbIntToFloat(this.getColor());
      double floorY = this.getY() + 0.1;
      if (isFiring) {
         if (this.tickCount % 10 == 0) {
            this.spawnSplashRingAt(this.getX(), floorY, this.getZ(), maxRadius * 1.4F, rgbCore);
         }
      } else {
         int particlesPerTick = 4;
         float gatherRadius = maxRadius * 1.3F;

         for (int i = 0; i < particlesPerTick; i++) {
            double offsetX = (this.random.nextDouble() - 0.5) * 2.0 * (double)gatherRadius;
            double offsetY = (this.random.nextDouble() - 0.5) * 2.0 * (double)gatherRadius;
            double offsetZ = (this.random.nextDouble() - 0.5) * 2.0 * (double)gatherRadius;
            double px = this.getX() + offsetX;
            double py = this.getY() + (double)(this.getBbHeight() / 2.0F) + offsetY;
            double pz = this.getZ() + offsetZ;
            double speed = 0.12;
            this.spawnAbsorbTrailAt(px, py, pz, -offsetX * speed, -offsetY * speed, -offsetZ * speed, rgbBorder);
         }
      }
   }

   private void spawnSplashRingAt(double x, double y, double z, float scale, float[] rgb) {
      if (Minecraft.getInstance().particleEngine.createParticle((ParticleOptions)MainParticles.KI_EXPLOSION_SPLASH.get(), x, y, z, 0.0, 0.0, 0.0) instanceof KiExplosionSplashParticle splash
         )
       {
         splash.setSplashColor(rgb[0], rgb[1], rgb[2]);
         splash.setSplashScale(scale);
      }
   }

   private void spawnAbsorbTrailAt(double x, double y, double z, double vx, double vy, double vz, float[] rgb) {
      if (Minecraft.getInstance().particleEngine.createParticle((ParticleOptions)MainParticles.KI_TRAIL.get(), x, y, z, vx, vy, vz) instanceof KiTrailParticle trail
         )
       {
         trail.setColor(rgb[0], rgb[1], rgb[2]);
         trail.setKiScale(1.0F);
      }
   }

   private void createCrater(float radius) {
      if (!this.level().isClientSide) {
         radius = this.scaledDestructionRadius(radius);
         BlockPos center = this.blockPosition();
         int r = (int)Math.ceil((double)radius);

         for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
               for (int z = -r; z <= r; z++) {
                  BlockPos pos = center.offset(x, y, z);
                  if (pos.distToCenterSqr(this.position()) <= (double)(radius * radius)) {
                     BlockState state = this.level().getBlockState(pos);
                     if (!state.isAir() && state.getDestroySpeed(this.level(), pos) >= 0.0F) {
                        this.setKiBlockToAir(pos, 3);
                     }
                  }
               }
            }
         }
      }
   }

   public void setMaxRadius(float radius) {
      this.entityData.set(MAX_RADIUS, radius);
   }

   public float getMaxRadius() {
      return (Float)this.entityData.get(MAX_RADIUS);
   }

   public void setCastExplosion(int ticks) {
      this.entityData.set(CAST_EXPLOSION, ticks);
   }

   public int getCastExplosion() {
      return (Integer)this.entityData.get(CAST_EXPLOSION);
   }

   @Override
   protected void addAdditionalSaveData(CompoundTag pCompound) {
      super.addAdditionalSaveData(pCompound);
      pCompound.putFloat("MaxRadius", this.getMaxRadius());
      pCompound.putInt("CastExplosion", this.getCastExplosion());
   }

   @Override
   protected void readAdditionalSaveData(CompoundTag pCompound) {
      super.readAdditionalSaveData(pCompound);
      if (pCompound.contains("MaxRadius")) {
         this.setMaxRadius(pCompound.getFloat("MaxRadius"));
      }

      if (pCompound.contains("CastExplosion")) {
         this.setCastExplosion(pCompound.getInt("CastExplosion"));
      }
   }
}
