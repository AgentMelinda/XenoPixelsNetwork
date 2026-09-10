package com.dragonminez.common.init.entities.ki;

import com.dragonminez.client.util.ColorUtils;
import com.dragonminez.common.init.MainDamageTypes;
import com.dragonminez.common.init.MainEntities;
import com.dragonminez.common.init.MainParticles;
import com.dragonminez.common.init.MainSounds;
import com.dragonminez.common.init.particles.KiTrailParticle;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.network.syncher.SynchedEntityData.Builder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.Entity.RemovalReason;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

public class KiBarrierEntity extends AbstractKiProjectile {
   private static final EntityDataAccessor<Float> CURRENT_SIZE = SynchedEntityData.defineId(KiBarrierEntity.class, EntityDataSerializers.FLOAT);
   private static final EntityDataAccessor<Integer> CAST_TIME = SynchedEntityData.defineId(KiBarrierEntity.class, EntityDataSerializers.INT);
   private static final EntityDataAccessor<Boolean> IS_FIRING = SynchedEntityData.defineId(KiBarrierEntity.class, EntityDataSerializers.BOOLEAN);
   private static final EntityDataAccessor<Integer> FIRE_TICK = SynchedEntityData.defineId(KiBarrierEntity.class, EntityDataSerializers.INT);
   private static final EntityDataAccessor<Integer> SHIELD_HOST = SynchedEntityData.defineId(KiBarrierEntity.class, EntityDataSerializers.INT);
   private static final EntityDataAccessor<Float> BARRIER_HP = SynchedEntityData.defineId(KiBarrierEntity.class, EntityDataSerializers.FLOAT);
   private static final EntityDataAccessor<Float> BARRIER_MAX_HP = SynchedEntityData.defineId(KiBarrierEntity.class, EntityDataSerializers.FLOAT);
   private static final float CONTACT_DAMAGE_RATIO = 0.1F;
   private static final float HEAL_EXPIRE_RATIO = 0.5F;
   private static final int HEAL_BUFF_INTERVAL = 20;
   private static final int GROW_DURATION = 25;
   private static final int MAX_LIFESPAN = 100;
   private static final float MAX_SIZE = 3.0F;

   public KiBarrierEntity(EntityType<? extends Projectile> pEntityType, Level pLevel) {
      super(pEntityType, pLevel);
      this.setNoGravity(true);
      this.noPhysics = true;
      this.setKiType(AbstractKiProjectile.KiType.SHIELD);
   }

   public KiBarrierEntity(Level level, LivingEntity owner) {
      super((EntityType<? extends Projectile>)MainEntities.KI_BARRIER.get(), level);
      this.setOwner(owner);
      this.setNoGravity(true);
      this.noPhysics = true;
      this.centerOnOwner();
   }

   public void setupKiBarrier(LivingEntity owner, int color, int colorBorder, int colorOutline, int castTime) {
      this.setColors(color, colorBorder, colorOutline);
      this.setCastTime(castTime);
      this.setMaxLife(castTime + 100);
      this.setSize(2.0F);
      this.setFiring(false);
      this.setFireTick(-1);
      if (!this.level().isClientSide) {
         this.level().addFreshEntity(this);
      }
   }

   public void setupKiBarrier(LivingEntity owner, int color, int colorBorder, int castTime) {
      this.setupKiBarrier(owner, color, colorBorder, 16777215, castTime);
   }

   public void setupBarrierPlayer(LivingEntity owner, float damage, float size, int colorMain, int colorBorder, int colorOutline) {
      this.setup(owner, damage, size, 0.0F, colorMain, colorBorder, colorOutline);
      this.setFiring(false);
      this.setFireTick(-1);
      this.setMaxLife(99999);
      this.setCastTime(40);
   }

   public void setupBarrierPlayer(LivingEntity owner, float damage, float size, int colorMain, int colorBorder) {
      this.setupBarrierPlayer(owner, damage, size, colorMain, colorBorder, 16777215);
   }

   public void fireHability(int finalMaxLife) {
      this.setFiring(true);
      this.setFireTick(this.tickCount);
      this.setMaxLife(this.tickCount + finalMaxLife);
      float hp = Math.max(1.0F, this.getKiDamage());
      this.setBarrierMaxHp(hp);
      this.setBarrierHp(hp);
      if (!this.level().isClientSide) {
         this.level().playSound(null, this.getX(), this.getY(), this.getZ(), (SoundEvent)MainSounds.KI_EXPLOSION_IMPACT.get(), SoundSource.PLAYERS, 0.7F, 1.2F);
      }

      if (this.getOwner() instanceof Player) {
         this.triggerAnimationPacket("_fire");
      }
   }

   @Override
   public int getMaxHits() {
      return Math.max(1, this.firingWindowTicks() / 20);
   }

   @Override
   protected void defineSynchedData(Builder builder) {
      super.defineSynchedData(builder);
      builder.define(CURRENT_SIZE, 0.1F);
      builder.define(CAST_TIME, 0);
      builder.define(IS_FIRING, false);
      builder.define(FIRE_TICK, -1);
      builder.define(SHIELD_HOST, -1);
      builder.define(BARRIER_HP, 0.0F);
      builder.define(BARRIER_MAX_HP, 0.0F);
   }

   public void setBarrierMaxHp(float hp) {
      this.entityData.set(BARRIER_MAX_HP, Math.max(0.0F, hp));
   }

   public float getBarrierMaxHp() {
      return (Float)this.entityData.get(BARRIER_MAX_HP);
   }

   public void setBarrierHp(float hp) {
      this.entityData.set(BARRIER_HP, Math.max(0.0F, hp));
   }

   public float getBarrierHp() {
      return (Float)this.entityData.get(BARRIER_HP);
   }

   public boolean isActive() {
      return this.isFiring() && this.getBarrierHp() > 0.0F;
   }

   public boolean protects(Entity entity) {
      LivingEntity anchor = this.getAnchor();
      return anchor != null && anchor.is(entity);
   }

   public void absorbDamage(float amount, Entity attacker) {
      if (!this.level().isClientSide && this.isFiring() && !(amount <= 0.0F)) {
         this.level().playSound(null, this.getX(), this.getY(), this.getZ(), (SoundEvent)MainSounds.BLOCK1.get(), SoundSource.PLAYERS, 0.8F, 1.0F);
         float remaining = this.getBarrierHp() - amount;
         if (remaining <= 0.0F) {
            this.setBarrierHp(0.0F);
            this.breakBarrier(attacker);
         } else {
            this.setBarrierHp(remaining);
         }
      }
   }

   private void breakBarrier(Entity breaker) {
      if (!this.isHeal() && breaker != null) {
         this.applyTechniqueSecondaryEffect(breaker);
      }

      this.playEndEffects(1.4F);
      this.discard();
   }

   private void expireBarrier() {
      if (this.isHeal()) {
         LivingEntity anchor = this.getAnchor();
         if (anchor != null && anchor.isAlive()) {
            float healAmount = this.getBarrierHp() * 0.5F;
            if (healAmount > 0.0F) {
               anchor.heal(healAmount);
            }
         }
      }

      this.playEndEffects(0.9F);
      this.discard();
   }

   private void playEndEffects(float pitch) {
      if (!this.level().isClientSide) {
         this.level()
            .playSound(null, this.getX(), this.getY(), this.getZ(), (SoundEvent)MainSounds.KI_EXPLOSION_IMPACT.get(), SoundSource.PLAYERS, 1.0F, pitch);
      }
   }

   public void setShieldHost(int id) {
      this.entityData.set(SHIELD_HOST, id);
   }

   public int getShieldHost() {
      return (Integer)this.entityData.get(SHIELD_HOST);
   }

   private LivingEntity getAnchor() {
      int hostId = this.getShieldHost();
      if (hostId >= 0) {
         if (this.level() instanceof ServerLevel sl && sl.getEntity(hostId) instanceof LivingEntity host && host.isAlive()) {
            return host;
         }

         return null;
      } else {
         return this.getOwner() instanceof LivingEntity owner ? owner : null;
      }
   }

   private void centerOn(LivingEntity anchor) {
      double bodyCenterY = anchor.getY() + (double)anchor.getBbHeight() / 2.0;
      this.setPos(anchor.getX(), bodyCenterY - (double)this.getBbHeight() * 0.5, anchor.getZ());
   }

   public float getCurrentSize() {
      return (Float)this.entityData.get(CURRENT_SIZE);
   }

   public void setCurrentSize(float size) {
      this.entityData.set(CURRENT_SIZE, size);
   }

   public void setCastTime(int ticks) {
      this.entityData.set(CAST_TIME, ticks);
   }

   public int getCastTime() {
      return (Integer)this.entityData.get(CAST_TIME);
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
   public int getFireTick() {
      return (Integer)this.entityData.get(FIRE_TICK);
   }

   @Override
   public void setFireTick(int tick) {
      this.entityData.set(FIRE_TICK, tick);
   }

   private void centerOnOwner() {
      if (this.getOwner() instanceof LivingEntity owner) {
         double bodyCenterY = owner.getY() + (double)owner.getBbHeight() / 2.0;
         this.setPos(owner.getX(), bodyCenterY - (double)this.getBbHeight() * 0.5, owner.getZ());
      }
   }

   @Override
   public void tick() {
      this.baseTick();
      if (!this.isFiring() && this.getMaxLife() != 99999 && this.tickCount >= this.getCastTime()) {
         this.fireHability(this.getMaxLife() - this.tickCount);
      }

      LivingEntity anchor = this.getAnchor();
      boolean hostMode = this.getShieldHost() >= 0;
      if (anchor != null && anchor.isAlive()) {
         this.centerOn(anchor);
         this.setDeltaMovement(0.0, 0.0, 0.0);
      } else {
         if (!hostMode || !this.level().isClientSide) {
            if (!this.level().isClientSide) {
               this.discard();
            }

            return;
         }

         this.setDeltaMovement(0.0, 0.0, 0.0);
      }

      boolean isFiring = this.isFiring();
      if (!this.level().isClientSide) {
         if (!isFiring) {
            this.setCurrentSize(0.1F);
         } else {
            int activeTicks = this.tickCount - this.getFireTick();
            float maxSize = this.getSize();
            float progress = (float)activeTicks / 10.0F;
            float size = Math.min(maxSize, 1.0F + maxSize * progress);
            this.setCurrentSize(size);
            this.breakIncomingProjectiles();
            if (this.isHeal()) {
               if (this.tickCount % 20 == 0) {
                  LivingEntity target = this.getAnchor();
                  if (target != null) {
                     this.applyTechniqueSecondaryEffect(target);
                     this.onSuccessfulHit(target);
                  }
               }
            } else {
               this.pushEntitiesAway();
            }
         }
      }

      if (this.level().isClientSide) {
         if (!isFiring) {
            this.spawnAbsorptionParticles();
         } else {
            this.spawnBarrierParticles();
         }
      }

      if (this.tickCount >= this.getMaxLife() && !this.level().isClientSide) {
         this.expireBarrier();
      }
   }

   private void breakIncomingProjectiles() {
      AABB area = this.getBoundingBox().inflate(0.3);

      for (Entity target : this.level().getEntities(this, area)) {
         if (target instanceof Projectile) {
            Projectile projectile = (Projectile)target;
            if (!(projectile instanceof AbstractKiProjectile) && (projectile.getOwner() == null || !projectile.getOwner().is(this.getOwner()))) {
               projectile.remove(RemovalReason.DISCARDED);
            }
         }
      }
   }

   private void pushEntitiesAway() {
      AABB area = this.getBoundingBox().inflate(0.3);
      List<Entity> targets = this.level().getEntities(this, area);
      LivingEntity shieldedAnchor = this.getAnchor();
      Entity shielded = (Entity)(shieldedAnchor != null ? shieldedAnchor : this.getOwner());
      float contactDamage = this.getBarrierMaxHp() * 0.1F;

      for (Entity target : targets) {
         if ((shielded == null || !target.is(shielded)) && !target.is(this.getOwner()) && target instanceof LivingEntity) {
            LivingEntity living = (LivingEntity)target;
            double dx = target.getX() - this.getX();
            double dz = target.getZ() - this.getZ();
            double dy = target.getY() - (this.getY() + (double)this.getBbHeight() * 0.5);
            Vec3 vec = new Vec3(dx, dy, dz).normalize().scale(1.5);
            target.setDeltaMovement(vec);
            target.hasImpulse = true;
            if (contactDamage > 0.0F && living.hurt(MainDamageTypes.kiblast(this.level(), this, this.getOwner()), contactDamage)) {
               this.applyTechniqueSecondaryEffect(living);
            }
         }
      }
   }

   private void spawnBarrierParticles() {
      float size = this.getCurrentSize();
      if (!(size < 0.2F)) {
         float[] rgb = ColorUtils.rgbIntToFloat(this.getColor());

         for (int i = 0; i < 3; i++) {
            double theta = this.random.nextDouble() * Math.PI * 2.0;
            double phi = this.random.nextDouble() * Math.PI;
            double r = (double)size / 2.0;
            double dx = r * Math.sin(phi) * Math.cos(theta);
            double dy = r * Math.cos(phi);
            double dz = r * Math.sin(phi) * Math.sin(theta);
            if (Minecraft.getInstance()
               .particleEngine
               .createParticle(
                  (ParticleOptions)MainParticles.KI_TRAIL.get(),
                  this.getX() + dx,
                  this.getY() + (double)this.getBbHeight() / 2.0 + dy,
                  this.getZ() + dz,
                  dx * 0.1,
                  dy * 0.1,
                  dz * 0.1
               ) instanceof KiTrailParticle trail) {
               trail.setKiColor(rgb[0], rgb[1], rgb[2]);
               trail.setKiScale(size * 0.3F);
            }
         }
      }
   }

   private void spawnAbsorptionParticles() {
      float[] rgb = ColorUtils.rgbIntToFloat(this.getColor());

      for (int i = 0; i < 3; i++) {
         double r = 2.5;
         double theta = this.random.nextDouble() * Math.PI * 2.0;
         double phi = Math.acos(2.0 * this.random.nextDouble() - 1.0);
         double dx = r * Math.sin(phi) * Math.cos(theta);
         double dy = r * Math.sin(phi) * Math.sin(theta);
         double dz = r * Math.cos(phi);
         if (Minecraft.getInstance()
            .particleEngine
            .createParticle(
               (ParticleOptions)MainParticles.KI_TRAIL.get(),
               this.getX() + dx,
               this.getY() + (double)this.getBbHeight() * 0.5 + dy,
               this.getZ() + dz,
               -dx * 0.15,
               -dy * 0.15,
               -dz * 0.15
            ) instanceof KiTrailParticle trail) {
            trail.setKiColor(rgb[0], rgb[1], rgb[2]);
            trail.setKiScale(0.3F);
         }
      }
   }

   @Override
   public EntityDimensions getDimensions(Pose pPose) {
      float size = this.getCurrentSize();
      return EntityDimensions.scalable(size, size);
   }

   @Override
   public void onSyncedDataUpdated(EntityDataAccessor<?> pKey) {
      if (CURRENT_SIZE.equals(pKey)) {
         this.refreshDimensions();
      }

      super.onSyncedDataUpdated(pKey);
   }

   public boolean hurt(DamageSource pSource, float pAmount) {
      return false;
   }

   protected void onHitEntity(EntityHitResult pResult) {
   }

   protected void onHitBlock(BlockHitResult pResult) {
   }

   @Override
   protected void onKiTick() {
   }
}
