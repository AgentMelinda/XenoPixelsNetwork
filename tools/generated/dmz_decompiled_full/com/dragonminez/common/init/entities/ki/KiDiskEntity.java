package com.dragonminez.common.init.entities.ki;

import com.dragonminez.client.util.ColorUtils;
import com.dragonminez.common.combat.util.MultipartTargeting;
import com.dragonminez.common.compat.CameraAimHelper;
import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.config.RaceCharacterConfig;
import com.dragonminez.common.init.MainEntities;
import com.dragonminez.common.init.MainParticles;
import com.dragonminez.common.init.MainSounds;
import com.dragonminez.common.init.particles.KiTrailParticle;
import com.dragonminez.common.network.NetworkHandler;
import com.dragonminez.common.network.S2C.AppearanceSyncS2C;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.common.stats.character.Character;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.network.syncher.SynchedEntityData.Builder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

public class KiDiskEntity extends AbstractKiProjectile {
   private boolean hasSpawnedSplash = false;
   private static final EntityDataAccessor<Integer> CAST_TIME = SynchedEntityData.defineId(KiDiskEntity.class, EntityDataSerializers.INT);
   private static final EntityDataAccessor<Float> OFFSET_X = SynchedEntityData.defineId(KiDiskEntity.class, EntityDataSerializers.FLOAT);
   private static final EntityDataAccessor<Float> OFFSET_Y = SynchedEntityData.defineId(KiDiskEntity.class, EntityDataSerializers.FLOAT);
   private static final EntityDataAccessor<Float> OFFSET_Z = SynchedEntityData.defineId(KiDiskEntity.class, EntityDataSerializers.FLOAT);
   private static final EntityDataAccessor<Boolean> IS_FIRING = SynchedEntityData.defineId(KiDiskEntity.class, EntityDataSerializers.BOOLEAN);

   public KiDiskEntity(EntityType<? extends Projectile> pEntityType, Level pLevel) {
      super(pEntityType, pLevel);
      this.refreshDimensions();
      this.setKiType(AbstractKiProjectile.KiType.DISK);
   }

   public KiDiskEntity(Level level, LivingEntity owner) {
      super((EntityType<? extends Projectile>)MainEntities.KI_DISC.get(), level);
      this.setOwner(owner);
      Vec3 look = owner.getLookAngle();
      Vec3 spawnPos = owner.getEyePosition().add(look.scale(0.5));
      this.setPos(spawnPos.x, spawnPos.y - 0.2, spawnPos.z);
      this.refreshDimensions();
   }

   public void setupKiDisk(LivingEntity owner, float damage, float speed, int colorMain, int colorBorder, int colorOutline, float size, int castTime) {
      this.setOwner(owner);
      this.setSize(size);
      this.setKiDamage(damage);
      this.setKiSpeed(speed);
      this.setColors(colorMain, colorBorder, colorOutline);
      this.setFiring(false);
      this.setCastTime(castTime);
      this.setMaxLife(castTime + 100);
      this.setCastOffsets(0.4F, 0.7F, 0.2F);
      this.playInitialSound((SoundEvent)MainSounds.KI_EXPLOSION_CHARGE.get());
      this.updatePositionRelativeToOwner(owner);
      if (!this.level().isClientSide) {
         this.level().addFreshEntity(this);
      }
   }

   public void setupKiDisk(LivingEntity owner, float damage, float speed, int color, float size, int castTime) {
      this.setupKiDisk(owner, damage, speed, color, color, 16777215, size, castTime);
   }

   public void setupKiDiskPlayer(LivingEntity owner, float damage, float speed, int colorMain, int colorBorder, int colorOutline, float size) {
      this.setOwner(owner);
      this.setSize(size);
      this.setKiDamage(damage);
      this.setKiSpeed(speed);
      this.setColors(colorMain, colorBorder, colorOutline);
      this.setFiring(false);
      this.setMaxLife(99999);
      this.setCastTime(40);
      this.setCastOffsets(0.4F, 0.7F, 0.2F);
      this.updatePositionRelativeToOwner(owner);
   }

   public void setupKiDiskPlayer(LivingEntity owner, float damage, float speed, int color, float size) {
      this.setupKiDiskPlayer(owner, damage, speed, color, color, 16777215, size);
   }

   public void fireHability(int finalMaxLife) {
      this.setFiring(true);
      this.setMaxLife(this.tickCount + finalMaxLife);
      this.setFireTick(this.tickCount);
      if (this.getOwner() instanceof LivingEntity livingOwner) {
         Vec3 lookDir = CameraAimHelper.resolve(livingOwner);
         Vec3 spawnPos = livingOwner.getEyePosition().add(lookDir.scale(0.5));
         this.setPos(spawnPos.x, spawnPos.y - 0.2, spawnPos.z);
         this.shoot(lookDir.x, lookDir.y, lookDir.z, this.getKiSpeed(), 0.0F);
         this.setYRot(CameraAimHelper.yaw(livingOwner, lookDir));
         this.setXRot(CameraAimHelper.pitch(lookDir));
         this.playSound((SoundEvent)MainSounds.KI_DISK_FIRE.get(), 0.7F, 1.5F);
      }

      if (this.getOwner() instanceof Player) {
         this.triggerAnimationPacket("_fire");
      }
   }

   @Override
   public void tick() {
      if (!this.level().isClientSide && !this.isFiring() && this.getMaxLife() != 99999 && this.tickCount >= this.getCastTime()) {
         this.fireHability(this.getMaxLife() - this.tickCount);
      }

      boolean isFiring;
      isFiring = this.isFiring();
      label29:
      if (!isFiring) {
         if (this.getOwner() instanceof LivingEntity livingOwner && livingOwner.isAlive()) {
            this.updatePositionRelativeToOwner(livingOwner);
            this.setDeltaMovement(0.0, 0.0, 0.0);
            this.setXRot(-90.0F);
            break label29;
         }

         if (!this.level().isClientSide) {
            this.discard();
            return;
         }
      }

      super.tick();
      if (!isFiring) {
         this.setDeltaMovement(0.0, 0.0, 0.0);
      }
   }

   private void updatePositionRelativeToOwner(LivingEntity owner) {
      Vec3 look = owner.getLookAngle();
      Vec3 right = look.cross(new Vec3(0.0, 1.0, 0.0)).normalize();
      Vec3 up = right.cross(look).normalize();
      Vec3 offset = right.scale((double)((Float)this.entityData.get(OFFSET_X)).floatValue())
         .add(up.scale((double)((Float)this.entityData.get(OFFSET_Y)).floatValue()))
         .add(look.scale((double)((Float)this.entityData.get(OFFSET_Z)).floatValue()));
      Vec3 newPos = owner.getEyePosition().add(offset);
      this.setPos(newPos.x, newPos.y, newPos.z);
   }

   @Override
   public int getMaxHits() {
      return Math.max(1, this.firingWindowTicks() / 20);
   }

   @Override
   public AbstractKiProjectile.ClashRole getClashRole() {
      return AbstractKiProjectile.ClashRole.MINOR;
   }

   @Override
   public EntityDimensions getDimensions(Pose pPose) {
      float scale = this.getSize();
      float width = 1.0F * scale;
      float height = Math.max(0.0625F * scale, 0.15F);
      return EntityDimensions.scalable(width, height);
   }

   @Override
   public void onSyncedDataUpdated(EntityDataAccessor<?> pKey) {
      super.onSyncedDataUpdated(pKey);
      this.refreshDimensions();
   }

   @Override
   protected void onKiTick() {
      if (!this.level().isClientSide && this.getOwner() == null) {
         this.discard();
      } else {
         boolean isFiring = this.isFiring();
         if (!this.level().isClientSide && !isFiring) {
            if (this.tickCount == 1) {
               this.playSound((SoundEvent)MainSounds.KI_DISK_CHARGE.get(), 0.7F, 1.2F);
            }

            if (this.tickCount % 10 == 0) {
               this.pulseAreaDamage();
            }
         }

         if (this.level().isClientSide) {
            float[] rgb = ColorUtils.rgbIntToFloat(this.getColor());
            float scale = this.getSize();
            if (!isFiring) {
               for (int i = 0; i < 4; i++) {
                  double spawnRadius = (double)scale * 2.5;
                  double theta = this.random.nextDouble() * 2.0 * Math.PI;
                  double phi = Math.acos(2.0 * this.random.nextDouble() - 1.0);
                  double dx = spawnRadius * Math.sin(phi) * Math.cos(theta);
                  double dy = spawnRadius * Math.sin(phi) * Math.sin(theta);
                  double dz = spawnRadius * Math.cos(phi);
                  double vx = -dx * 0.15;
                  double vy = -dy * 0.15;
                  double vz = -dz * 0.15;
                  if (Minecraft.getInstance()
                     .particleEngine
                     .createParticle(
                        (ParticleOptions)MainParticles.KI_TRAIL.get(),
                        this.getX() + dx,
                        this.getY() + (double)this.getBbHeight() / 2.0 + dy,
                        this.getZ() + dz,
                        vx,
                        vy,
                        vz
                     ) instanceof KiTrailParticle trail) {
                     trail.setKiColor(rgb[0], rgb[1], rgb[2]);
                     trail.setKiScale(scale * 0.3F);
                  }
               }
            } else {
               for (int ix = 0; ix < 4; ix++) {
                  double angle = this.random.nextDouble() * Math.PI * 2.0;
                  double radius = (double)scale * 0.8;
                  double dx = Math.cos(angle) * radius;
                  double dz = Math.sin(angle) * radius;
                  double dy = (this.random.nextDouble() - 0.5) * 0.1;
                  double vx = -this.getDeltaMovement().x * 0.3;
                  double vy = -this.getDeltaMovement().y * 0.3;
                  double vz = -this.getDeltaMovement().z * 0.3;
                  if (Minecraft.getInstance()
                     .particleEngine
                     .createParticle(
                        (ParticleOptions)MainParticles.KI_TRAIL.get(),
                        this.getX() + dx,
                        this.getY() + (double)this.getBbHeight() / 2.0 + dy,
                        this.getZ() + dz,
                        vx,
                        vy,
                        vz
                     ) instanceof KiTrailParticle trail) {
                     trail.setKiColor(rgb[0], rgb[1], rgb[2]);
                     trail.setKiScale(scale * 0.6F);
                  }
               }
            }

            if (!this.hasSpawnedSplash) {
               this.level()
                  .addParticle(
                     (ParticleOptions)MainParticles.KI_SPLASH.get(),
                     this.getX(),
                     this.getY() + (double)this.getBbHeight() / 2.0,
                     this.getZ(),
                     (double)rgb[0],
                     (double)rgb[1],
                     (double)rgb[2]
                  );
               this.hasSpawnedSplash = true;
            }
         }
      }
   }

   private void pulseAreaDamage() {
      AABB area = this.getBoundingBox().inflate(0.5, 0.2, 0.5);

      for (LivingEntity target : MultipartTargeting.collectTargets(this.level(), area)) {
         if (this.shouldDamage(target)) {
            boolean wasHit = this.applyDamageOrHeal(target, this.getKiDamage());
            if (wasHit) {
               this.onSuccessfulHit(target);
            }
         }
      }
   }

   @Override
   protected void defineSynchedData(Builder builder) {
      super.defineSynchedData(builder);
      builder.define(CAST_TIME, 0);
      builder.define(OFFSET_X, 0.0F);
      builder.define(OFFSET_Y, 0.0F);
      builder.define(OFFSET_Z, 0.0F);
      builder.define(IS_FIRING, false);
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
   }

   @Override
   protected void readAdditionalSaveData(CompoundTag pCompound) {
      super.readAdditionalSaveData(pCompound);
      if (pCompound.contains("CastTime")) {
         this.setCastTime(pCompound.getInt("CastTime"));
      }
   }

   protected void onHitEntity(EntityHitResult pResult) {
      if (this.isFiring()) {
         super.onHitEntity(pResult);
         if (!this.level().isClientSide) {
            Entity targetEntity = pResult.getEntity();
            if (this.shouldDamage(targetEntity)) {
               boolean wasHit = this.applyDamageOrHeal(targetEntity, this.getKiDamage());
               if (wasHit) {
                  this.onSuccessfulHit(targetEntity);
                  this.tryCutSaiyanTail(targetEntity);
                  if (this.level() instanceof ServerLevel serverLevel) {
                     double colorData = (double)this.getColor();
                     double sizeData = (double)this.getBbWidth();
                     serverLevel.sendParticles(
                        (SimpleParticleType)MainParticles.KI_SPLASH_WAVE.get(),
                        targetEntity.getX(),
                        targetEntity.getY() + (double)targetEntity.getBbHeight() / 2.0,
                        targetEntity.getZ(),
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

   private void tryCutSaiyanTail(Entity targetEntity) {
      if (!this.level().isClientSide) {
         if (targetEntity instanceof ServerPlayer targetPlayer) {
            StatsData data = StatsProvider.get(StatsCapability.INSTANCE, targetPlayer).orElse(null);
            if (data != null) {
               Character character = data.getCharacter();
               if (character.isHasSaiyanTail() && canHaveTail(data)) {
                  float diskPower = this.getKiDamage();
                  if (!((double)diskPower <= data.getDefense()) && !(diskPower <= targetPlayer.getHealth())) {
                     character.setHasSaiyanTail(false);
                     NetworkHandler.sendToTrackingEntityAndSelf(new AppearanceSyncS2C(targetPlayer), targetPlayer);
                     targetPlayer.level()
                        .playSound(
                           null,
                           targetPlayer.getX(),
                           targetPlayer.getY(),
                           targetPlayer.getZ(),
                           (SoundEvent)MainSounds.KATANA_SLASH.get(),
                           SoundSource.PLAYERS,
                           1.0F,
                           1.0F
                        );
                  }
               }
            }
         }
      }
   }

   private static boolean canHaveTail(StatsData data) {
      String race = data.getCharacter().getRaceName();
      if ("saiyan".equalsIgnoreCase(race)) {
         return true;
      } else {
         RaceCharacterConfig config = ConfigManager.getRaceCharacter(race);
         return config != null && Boolean.TRUE.equals(config.getHasSaiyanTail());
      }
   }
}
