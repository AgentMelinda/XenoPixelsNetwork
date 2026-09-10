package com.dragonminez.common.init.entities.ki;

import com.dragonminez.client.util.ColorUtils;
import com.dragonminez.common.combat.util.MultipartTargeting;
import com.dragonminez.common.compat.CameraAimHelper;
import com.dragonminez.common.compat.SableCompat;
import com.dragonminez.common.init.MainEntities;
import com.dragonminez.common.init.MainParticles;
import com.dragonminez.common.init.MainSounds;
import com.dragonminez.common.init.particles.KiLightningParticle;
import com.dragonminez.common.init.particles.KiSheddingParticle;
import com.dragonminez.common.init.particles.KiTrailParticle;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.network.syncher.SynchedEntityData.Builder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.level.Level.ExplosionInteraction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;

public class KiLaserEntity extends AbstractKiProjectile {
   private static final EntityDataAccessor<Float> BEAM_LENGTH = SynchedEntityData.defineId(KiLaserEntity.class, EntityDataSerializers.FLOAT);
   private static final EntityDataAccessor<Float> FIXED_YAW = SynchedEntityData.defineId(KiLaserEntity.class, EntityDataSerializers.FLOAT);
   private static final EntityDataAccessor<Float> FIXED_PITCH = SynchedEntityData.defineId(KiLaserEntity.class, EntityDataSerializers.FLOAT);
   private static final EntityDataAccessor<Integer> CAST_TIME = SynchedEntityData.defineId(KiLaserEntity.class, EntityDataSerializers.INT);
   private static final EntityDataAccessor<Float> OFFSET_X = SynchedEntityData.defineId(KiLaserEntity.class, EntityDataSerializers.FLOAT);
   private static final EntityDataAccessor<Float> OFFSET_Y = SynchedEntityData.defineId(KiLaserEntity.class, EntityDataSerializers.FLOAT);
   private static final EntityDataAccessor<Float> OFFSET_Z = SynchedEntityData.defineId(KiLaserEntity.class, EntityDataSerializers.FLOAT);
   private static final float MAX_RANGE = 250.0F;

   public KiLaserEntity(EntityType<? extends Projectile> pEntityType, Level pLevel) {
      super(pEntityType, pLevel);
      this.setNoGravity(true);
      this.noPhysics = true;
      this.setKiType(AbstractKiProjectile.KiType.LASER);
   }

   public KiLaserEntity(Level level, LivingEntity owner) {
      super((EntityType<? extends Projectile>)MainEntities.KI_LASER.get(), level);
      this.setOwner(owner);
      this.setNoGravity(true);
      this.noPhysics = true;
      this.entityData.set(FIXED_YAW, owner.getYRot());
      this.entityData.set(FIXED_PITCH, owner.getXRot());
      this.setYRot(owner.getYRot());
      this.setXRot(owner.getXRot());
      Vec3 look = owner.getLookAngle();
      Vec3 startPos = owner.getEyePosition().add(look.scale(0.5));
      this.setPos(startPos.x, startPos.y, startPos.z);
      this.setKiSpeed(1.5F);
      this.setSize(1.5F);
   }

   public void setupKiLaser(LivingEntity owner, float damage, float speed, int color, int colorBorder, int colorOutline, int castTime) {
      this.setKiRenderType(0);
      this.setSize(1.0F);
      this.setKiDamage(damage);
      this.setKiSpeed(speed);
      this.setColors(color, colorBorder, colorOutline);
      this.setFiring(false);
      this.setCastTime(castTime);
      this.setMaxLife(castTime + 80);
      this.setCastOffsets(0.3F, -0.1F, 0.5F);
      this.playInitialSound((SoundEvent)MainSounds.KI_EXPLOSION_CHARGE.get());
      this.updatePositionRelativeToOwner(owner);
      if (!this.level().isClientSide) {
         this.level().addFreshEntity(this);
      }
   }

   public void setupKiLaser(LivingEntity owner, float damage, float speed, int color, int colorBorder, int castTime) {
      this.setupKiLaser(owner, damage, speed, color, colorBorder, 16777215, castTime);
   }

   public void setupKiLaser(LivingEntity owner, float damage, float speed, int color, int castTime) {
      this.setupKiLaser(owner, damage, speed, color, color, 16777215, castTime);
   }

   public void setupKiDodonpa(LivingEntity owner, float damage, float speed, int colorOutline, int castTime) {
      this.setKiRenderType(0);
      this.setSize(0.5F);
      this.setKiDamage(damage);
      this.setKiSpeed(speed);
      this.setColors(16771962, 16770647, colorOutline);
      this.setFiring(false);
      this.setCastTime(castTime);
      this.setMaxLife(castTime + 80);
      this.setCastOffsets(0.3F, 0.1F, 0.5F);
      this.playInitialSound((SoundEvent)MainSounds.KI_EXPLOSION_CHARGE.get());
      this.updatePositionRelativeToOwner(owner);
      if (!this.level().isClientSide) {
         this.level().addFreshEntity(this);
      }
   }

   public void setupKiDodonpa(LivingEntity owner, float damage, float speed, int castTime) {
      this.setupKiDodonpa(owner, damage, speed, 16777215, castTime);
   }

   public void setupKiMakkankosanpo(LivingEntity owner, float damage, float speed, int colorOutline, int castTime) {
      this.setKiRenderType(1);
      this.setSize(1.0F);
      this.setKiDamage(damage);
      this.setKiSpeed(speed);
      this.setColors(16770647, 16770647, colorOutline);
      this.setFiring(false);
      this.setCastTime(castTime);
      this.setMaxLife(castTime + 120);
      this.setCastOffsets(0.3F, -0.1F, 0.5F);
      this.playInitialSound((SoundEvent)MainSounds.KI_EXPLOSION_CHARGE.get());
      this.updatePositionRelativeToOwner(owner);
      if (!this.level().isClientSide) {
         this.level().addFreshEntity(this);
      }
   }

   public void setupKiMakkankosanpo(LivingEntity owner, float damage, float speed, int castTime) {
      this.setupKiMakkankosanpo(owner, damage, speed, 16777215, castTime);
   }

   public void setupKiLaserPlayer(LivingEntity owner, float damage, float speed, int color, int colorBorder, int colorOutline) {
      this.setKiRenderType(0);
      this.setSize(1.0F);
      this.setKiDamage(damage);
      this.setKiSpeed(speed);
      this.setColors(color, colorBorder, colorOutline);
      this.setFiring(false);
      this.setMaxLife(99999);
      this.setCastTime(20);
      this.setCastOffsets(0.3F, 0.4F, 0.5F);
      this.updatePositionRelativeToOwner(owner);
   }

   public void setupKiLaserPlayer(LivingEntity owner, float damage, float speed, int color, int colorBorder) {
      this.setupKiLaserPlayer(owner, damage, speed, color, colorBorder, 16777215);
   }

   public void setupKiMakkankosanpoPlayer(LivingEntity owner, float damage, float speed, int colorOutline) {
      this.setKiRenderType(1);
      this.setSize(1.0F);
      this.setKiDamage(damage);
      this.setKiSpeed(speed);
      this.setColors(16770647, 16098855, colorOutline);
      this.setFiring(false);
      this.setMaxLife(99999);
      this.setCastTime(40);
      this.setCastOffsets(-0.2F, 0.7F, 0.5F);
      this.updatePositionRelativeToOwner(owner);
   }

   public void setupKiMakkankosanpoPlayer(LivingEntity owner, float damage, float speed) {
      this.setupKiMakkankosanpoPlayer(owner, damage, speed, 9115599);
   }

   public void setupKiDodonpaPlayer(LivingEntity owner, float damage, float speed, int colorOutline) {
      this.setKiRenderType(0);
      this.setSize(0.5F);
      this.setKiDamage(damage);
      this.setKiSpeed(speed);
      this.setColors(16771962, 16770647, colorOutline);
      this.setFiring(false);
      this.setMaxLife(99999);
      this.setCastTime(20);
      this.setCastOffsets(0.3F, -0.1F, 0.5F);
      this.updatePositionRelativeToOwner(owner);
   }

   public void setupKiBeamPlayer(LivingEntity owner, float damage, float speed, int color, int colorBorder, int colorOutline) {
      this.setKiRenderType(2);
      this.setSize(1.0F);
      this.setKiDamage(damage);
      this.setKiSpeed(speed);
      this.setColors(color, colorBorder, colorOutline);
      this.setFiring(false);
      this.setMaxLife(99999);
      this.setCastTime(20);
      this.setCastOffsets(-0.2F, 0.7F, 0.5F);
      this.updatePositionRelativeToOwner(owner);
   }

   public void setupKiBeamPlayer(LivingEntity owner, float damage, float speed, int color, int colorBorder) {
      this.setupKiBeamPlayer(owner, damage, speed, color, colorBorder, 16777215);
   }

   public void setupKiDodonpaPlayer(LivingEntity owner, float damage, float speed) {
      this.setupKiDodonpaPlayer(owner, damage, speed, 16777215);
   }

   public void fireHability(int finalMaxLife) {
      this.setFiring(true);
      this.setMaxLife(this.tickCount + finalMaxLife);
      this.setFireTick(this.tickCount);
      if (this.getOwner() instanceof LivingEntity livingOwner) {
         Vec3 aim = CameraAimHelper.resolve(livingOwner);
         this.updatePositionRelativeToOwner(livingOwner, aim);
         float yaw = CameraAimHelper.yaw(livingOwner, aim);
         float pitch = CameraAimHelper.pitch(aim);
         this.entityData.set(FIXED_YAW, yaw);
         this.entityData.set(FIXED_PITCH, pitch);
         this.setYRot(yaw);
         this.setXRot(pitch);
         int renderType = this.getKiRenderType();
         SoundEvent fireSound = renderType != 1 && renderType != 2 ? (SoundEvent)MainSounds.KI_LASER.get() : (SoundEvent)MainSounds.KI_BEAM_FIRE.get();
         this.level().playSound(null, this.getX(), this.getY(), this.getZ(), fireSound, SoundSource.PLAYERS, 0.7F, 1.0F + this.random.nextFloat() * 0.2F);
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
   public AbstractKiProjectile.ClashRole getClashRole() {
      return this.getKiRenderType() == 1 ? AbstractKiProjectile.ClashRole.MAJOR : AbstractKiProjectile.ClashRole.MINOR;
   }

   @Override
   public float getClashYaw() {
      return this.getFixedYaw();
   }

   @Override
   public float getClashPitch() {
      return this.getFixedPitch();
   }

   @Override
   public float getClashBeamLength() {
      return this.getBeamLength();
   }

   @Override
   protected void defineSynchedData(Builder builder) {
      super.defineSynchedData(builder);
      builder.define(BEAM_LENGTH, 0.0F);
      builder.define(FIXED_YAW, 0.0F);
      builder.define(FIXED_PITCH, 0.0F);
      builder.define(CAST_TIME, 0);
      builder.define(OFFSET_X, 0.0F);
      builder.define(OFFSET_Y, 0.0F);
      builder.define(OFFSET_Z, 0.0F);
   }

   public float getBeamLength() {
      return (Float)this.entityData.get(BEAM_LENGTH);
   }

   private void setBeamLength(float len) {
      this.entityData.set(BEAM_LENGTH, len);
   }

   public float getFixedYaw() {
      return (Float)this.entityData.get(FIXED_YAW);
   }

   public float getFixedPitch() {
      return (Float)this.entityData.get(FIXED_PITCH);
   }

   public void setCastTime(int ticks) {
      this.entityData.set(CAST_TIME, ticks);
   }

   public int getCastTime() {
      return (Integer)this.entityData.get(CAST_TIME);
   }

   public void setCastOffsets(float x, float y, float z) {
      this.entityData.set(OFFSET_X, x);
      this.entityData.set(OFFSET_Y, y);
      this.entityData.set(OFFSET_Z, z);
   }

   @Override
   public void tick() {
      this.baseTick();
      if (!this.level().isClientSide && !this.isFiring() && this.getMaxLife() != 99999 && this.tickCount >= this.getCastTime()) {
         this.fireHability(this.getMaxLife() - this.tickCount);
      }

      boolean isFiring = this.isFiring();
      label66:
      if (!isFiring && this.getCastTime() > 0) {
         if (this.getOwner() instanceof LivingEntity livingOwner && livingOwner.isAlive()) {
            this.updatePositionRelativeToOwner(livingOwner);
            this.setDeltaMovement(0.0, 0.0, 0.0);
            this.entityData.set(FIXED_YAW, livingOwner.getYRot());
            this.entityData.set(FIXED_PITCH, livingOwner.getXRot());
            this.setYRot(livingOwner.getYRot());
            this.setXRot(livingOwner.getXRot());
            if (!this.level().isClientSide) {
               int renderType = this.getKiRenderType();
               if ((renderType == 1 || renderType == 2) && this.tickCount == 1) {
                  this.playSound((SoundEvent)MainSounds.KI_BEAM_CHARGE.get(), 0.7F, 1.0F);
               }
            }
            break label66;
         }

         if (!this.level().isClientSide) {
            this.discard();
            return;
         }
      } else {
         this.setDeltaMovement(0.0, 0.0, 0.0);
         if (!this.level().isClientSide) {
            if (this.isClashLocked()) {
               this.setBeamLength(this.getClashLockedLength());
               this.onKiTick();
               return;
            }

            Vec3 startPos = this.position();
            Vec3 dir = Vec3.directionFromRotation(this.getFixedPitch(), this.getFixedYaw());
            float currentLen = this.getBeamLength();
            float targetLen = currentLen + this.getKiSpeed();
            Vec3 endPosRay = startPos.add(dir.scale(250.0));
            HitResult hitResult = this.level().clip(new ClipContext(startPos, endPosRay, Block.COLLIDER, Fluid.NONE, this));
            double distToWall = 250.0;
            if (hitResult.getType() != Type.MISS) {
               Vec3 worldHit = SableCompat.projectToWorld(this.level(), hitResult.getLocation());
               distToWall = worldHit.distanceTo(startPos);
               if (hitResult.getType() == Type.BLOCK && (double)targetLen >= distToWall) {
                  this.explodeAndDie(worldHit);
                  return;
               }

               distToWall += 0.1;
            }

            if ((double)targetLen > distToWall) {
               targetLen = (float)distToWall;
            }

            this.setBeamLength(targetLen);
            this.damageEntitiesInBeam(startPos, dir, targetLen);
            if (this.tickCount > this.getMaxLife()) {
               this.discard();
               return;
            }
         }
      }

      if (this.level().isClientSide) {
         this.spawnLaserParticles();
      }

      this.onKiTick();
   }

   private void updatePositionRelativeToOwner(LivingEntity owner) {
      this.updatePositionRelativeToOwner(owner, owner.getLookAngle());
   }

   private void updatePositionRelativeToOwner(LivingEntity owner, Vec3 look) {
      Vec3 right = look.cross(new Vec3(0.0, 1.0, 0.0)).normalize();
      Vec3 up = right.cross(look).normalize();
      double centerX = owner.getX();
      double centerY = owner.getY() + (double)owner.getBbHeight() / 2.0;
      double centerZ = owner.getZ();
      Vec3 hitboxCenter = new Vec3(centerX, centerY, centerZ);
      Vec3 offset;
      if (!this.isFiring()) {
         offset = right.scale((double)((Float)this.entityData.get(OFFSET_X)).floatValue())
            .add(up.scale((double)((Float)this.entityData.get(OFFSET_Y)).floatValue()))
            .add(look.scale((double)((Float)this.entityData.get(OFFSET_Z)).floatValue()));
      } else {
         double forwardDistance = (double)owner.getBbWidth() / 2.0 + 0.3;
         float fireOffsetX = 0.0F;
         float fireOffsetY = 0.0F;
         float fireOffsetZ = 0.0F;
         int renderType = this.getKiRenderType();
         if (renderType != 1 && renderType != 2) {
            fireOffsetX = 0.1F;
            fireOffsetY = 0.5F;
            fireOffsetZ = 0.6F;
         } else {
            fireOffsetX = 0.1F;
            fireOffsetY = 0.5F;
            fireOffsetZ = 0.6F;
         }

         offset = right.scale((double)fireOffsetX).add(up.scale((double)fireOffsetY)).add(look.scale((double)fireOffsetZ));
      }

      Vec3 newPos = hitboxCenter.add(offset);
      this.setPos(newPos.x, newPos.y, newPos.z);
      this.setYRot(CameraAimHelper.yaw(owner, look));
      this.setXRot(CameraAimHelper.pitch(look));
   }

   private void spawnLaserParticles() {
      float yaw = this.getFixedYaw();
      float pitch = this.getFixedPitch();
      Vec3 dir = Vec3.directionFromRotation(pitch, yaw);
      Vec3 startPos = this.position();
      float length = this.getBeamLength();
      float scale = this.getSize();
      float[] rgbMain = ColorUtils.rgbIntToFloat(this.getColor());
      float[] rgbBorder = this.getRgbColorBorder();
      if (this.tickCount == 1) {
         this.level()
            .addParticle(
               (ParticleOptions)MainParticles.KI_SPLASH.get(),
               startPos.x,
               startPos.y,
               startPos.z,
               (double)rgbBorder[0],
               (double)rgbBorder[1],
               (double)rgbBorder[2]
            );
         if (this.getKiRenderType() == 1) {
            this.spawnInitialLightning();
         }
      }

      for (int i = 0; i < 3; i++) {
         double absDist = (double)scale * 2.0;
         double theta = this.random.nextDouble() * Math.PI * 2.0;
         double phi = Math.acos(2.0 * this.random.nextDouble() - 1.0);
         double sx = absDist * Math.sin(phi) * Math.cos(theta);
         double sy = absDist * Math.sin(phi) * Math.sin(theta);
         double sz = absDist * Math.cos(phi);
         double vx = -sx * 0.15;
         double vy = -sy * 0.15;
         double vz = -sz * 0.15;
         if (Minecraft.getInstance()
            .particleEngine
            .createParticle((ParticleOptions)MainParticles.KI_SHEDDING.get(), startPos.x + sx, startPos.y + sy, startPos.z + sz, vx, vy, vz) instanceof KiSheddingParticle kiParticle
            )
          {
            kiParticle.setKiColor(rgbBorder[0], rgbBorder[1], rgbBorder[2]);
         }
      }

      if (length > 0.5F) {
         Vec3 tipPos = startPos.add(dir.scale((double)length));

         for (int ix = 0; ix < 3; ix++) {
            double radius = (double)scale * 0.8;
            double theta = this.random.nextDouble() * 2.0 * Math.PI;
            double phi = Math.acos(2.0 * this.random.nextDouble() - 1.0);
            double dx = radius * Math.sin(phi) * Math.cos(theta);
            double dy = radius * Math.sin(phi) * Math.sin(theta);
            double dz = radius * Math.cos(phi);
            double vx = dx * 0.25;
            double vy = dy * 0.25;
            double vz = dz * 0.25;
            if (Minecraft.getInstance()
               .particleEngine
               .createParticle((ParticleOptions)MainParticles.KI_TRAIL.get(), tipPos.x + dx, tipPos.y + dy, tipPos.z + dz, vx, vy, vz) instanceof KiTrailParticle trail
               )
             {
               trail.setKiColor(rgbBorder[0], rgbBorder[1], rgbBorder[2]);
               trail.setKiScale(scale * 0.6F);
            }
         }
      }
   }

   private void spawnInitialLightning() {
      float scale = this.getSize();
      float[] rgbMain = ColorUtils.rgbIntToFloat(this.getColor());
      float[] rgbBorder = ColorUtils.rgbIntToFloat(15663359);
      Vec3 startPos = this.position();
      int lightningCount = 25;

      for (int i = 0; i < lightningCount; i++) {
         float[] chosenColor = i % 2 == 0 ? rgbMain : rgbBorder;
         this.spawnLightningAt(startPos, scale * 2.5F, chosenColor);
      }
   }

   private void spawnLightningAt(Vec3 pos, float scaleRadius, float[] rgb) {
      double offsetX = (this.random.nextDouble() - 0.5) * (double)scaleRadius * 2.0;
      double offsetY = (this.random.nextDouble() - 0.5) * (double)scaleRadius * 2.0;
      double offsetZ = (this.random.nextDouble() - 0.5) * (double)scaleRadius * 2.0;
      double vx = offsetX * 0.1;
      double vy = offsetY * 0.1;
      double vz = offsetZ * 0.1;
      if (Minecraft.getInstance()
         .particleEngine
         .createParticle((ParticleOptions)MainParticles.KI_LIGHTNING.get(), pos.x + offsetX, pos.y + offsetY, pos.z + offsetZ, vx, vy, vz) instanceof KiLightningParticle lightning
         )
       {
         lightning.setLightningColor(rgb[0], rgb[1], rgb[2]);
         float randomScale = scaleRadius * 0.8F + this.random.nextFloat() * scaleRadius * 0.5F;
         lightning.setLightningScale(randomScale);
      }
   }

   private void damageEntitiesInBeam(Vec3 start, Vec3 dir, float length) {
      Vec3 end = start.add(dir.scale((double)length));
      double searchRadius = (double)this.getSize() * 0.5;
      AABB searchBox = new AABB(start, end).inflate(searchRadius);
      List<LivingEntity> targets = MultipartTargeting.collectTargets(this.level(), searchBox);
      int hitInterval = 10;

      for (LivingEntity target : targets) {
         if (this.shouldDamage(target) && !target.is(this.getOwner()) && target.invulnerableTime <= 0) {
            float hitPrecision = this.getSize() / 3.0F;
            boolean beamHit = false;

            for (AABB hb : MultipartTargeting.hitBoxes(target)) {
               AABB targetBox = hb.inflate((double)hitPrecision);
               if (targetBox.clip(start, end).isPresent() || targetBox.contains(start)) {
                  beamHit = true;
                  break;
               }
            }

            if (beamHit) {
               boolean wasHit = this.applyDamageOrHeal(target, this.getDamagePerHit());
               if (wasHit) {
                  this.onSuccessfulHit(target);
                  target.invulnerableTime = hitInterval;
                  if (this.level() instanceof ServerLevel serverLevel) {
                     double colorData = (double)this.getColor();
                     double sizeData = (double)this.getSize();
                     serverLevel.sendParticles(
                        (SimpleParticleType)MainParticles.KI_SPLASH_WAVE.get(),
                        target.getX(),
                        target.getY() + (double)target.getBbHeight() / 2.0,
                        target.getZ(),
                        0,
                        colorData,
                        sizeData,
                        0.0,
                        1.0
                     );
                  }
               }
            }
         }
      }
   }

   private void explodeAndDie(Vec3 pos) {
      float radius = this.getSize();
      AABB area = new AABB(pos, pos).inflate((double)radius);

      for (LivingEntity target : MultipartTargeting.collectTargets(this.level(), area)) {
         if (this.shouldDamage(target) && MultipartTargeting.withinRadius(target, pos, (double)radius)) {
            boolean wasHit = this.applyDamageOrHeal(target, this.getKiDamage());
            if (wasHit) {
               this.onSuccessfulHit(target);
            }
         }
      }

      this.level().addParticle(ParticleTypes.EXPLOSION_EMITTER, pos.x, pos.y, pos.z, 1.0, 0.0, 0.0);
      this.level().playSound(null, pos.x, pos.y, pos.z, SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 4.0F, 0.7F);
      ExplosionInteraction interaction = this.getKiExplosionInteraction(BlockPos.containing(pos));
      float blastRadius = this.scaledDestructionRadius(radius);
      this.level().explode(this, this.damageSources().explosion(this, this.getOwner()), null, pos.x, pos.y, pos.z, blastRadius, false, interaction);
      this.discard();
   }
}
