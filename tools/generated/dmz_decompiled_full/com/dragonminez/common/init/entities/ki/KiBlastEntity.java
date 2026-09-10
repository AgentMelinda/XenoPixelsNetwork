package com.dragonminez.common.init.entities.ki;

import com.dragonminez.client.util.ColorUtils;
import com.dragonminez.common.combat.logic.player.TargetHelper;
import com.dragonminez.common.combat.util.MultipartTargeting;
import com.dragonminez.common.compat.CameraAimHelper;
import com.dragonminez.common.compat.SableCompat;
import com.dragonminez.common.init.MainEntities;
import com.dragonminez.common.init.MainParticles;
import com.dragonminez.common.init.MainSounds;
import com.dragonminez.common.init.particles.KiSheddingParticle;
import com.dragonminez.common.init.particles.KiTrailParticle;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.network.syncher.SynchedEntityData.Builder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;

public class KiBlastEntity extends AbstractKiProjectile {
   private boolean hasSpawnedSplash = false;
   public static final int RENDER_SOUL_PUNISHER = 4;
   public static final int RENDER_FAKE_MOON = 11;
   private static final double FAKE_MOON_CLIMB_BLOCKS = 30.0;
   private static final double FAKE_MOON_CLIMB_SPEED = 1.0;
   private static final int FAKE_MOON_GLOW_TICKS = 400;
   private transient double fakeMoonStartY = Double.NaN;
   private transient int fakeMoonFrozenTick = -1;
   private boolean isDetonating = false;
   private float currentDetonationRadius = 0.0F;
   private float maxDetonationRadius = 0.0F;
   private static final EntityDataAccessor<Integer> CAST_TIME = SynchedEntityData.defineId(KiBlastEntity.class, EntityDataSerializers.INT);
   private static final EntityDataAccessor<Float> OFFSET_X = SynchedEntityData.defineId(KiBlastEntity.class, EntityDataSerializers.FLOAT);
   private static final EntityDataAccessor<Float> OFFSET_Y = SynchedEntityData.defineId(KiBlastEntity.class, EntityDataSerializers.FLOAT);
   private static final EntityDataAccessor<Float> OFFSET_Z = SynchedEntityData.defineId(KiBlastEntity.class, EntityDataSerializers.FLOAT);
   private static final EntityDataAccessor<Boolean> IS_CONTROLLABLE = SynchedEntityData.defineId(KiBlastEntity.class, EntityDataSerializers.BOOLEAN);
   private static final EntityDataAccessor<Boolean> IS_PARKED = SynchedEntityData.defineId(KiBlastEntity.class, EntityDataSerializers.BOOLEAN);
   private static final EntityDataAccessor<Float> PARKED_DISTANCE = SynchedEntityData.defineId(KiBlastEntity.class, EntityDataSerializers.FLOAT);
   private static final EntityDataAccessor<Boolean> IS_FIRING = SynchedEntityData.defineId(KiBlastEntity.class, EntityDataSerializers.BOOLEAN);

   public KiBlastEntity(EntityType<? extends Projectile> pEntityType, Level pLevel) {
      super(pEntityType, pLevel);
   }

   public KiBlastEntity(Level level, LivingEntity owner) {
      this((EntityType<? extends Projectile>)MainEntities.KI_BLAST.get(), level);
      this.setOwner(owner);
      this.setKiType(this.getKiRenderType());
   }

   @Override
   public int getMaxHits() {
      return Math.max(1, this.firingWindowTicks() / 20);
   }

   @Override
   public AbstractKiProjectile.ClashRole getClashRole() {
      return this.getKiRenderType() == 0 ? AbstractKiProjectile.ClashRole.MINOR : AbstractKiProjectile.ClashRole.NONE;
   }

   private float calcCenterOffsetY(float sphereSize) {
      return -(sphereSize / 2.0F);
   }

   private float calcForwardOffset(LivingEntity owner, float sphereSize) {
      float ownerHalfWidth = owner.getBbWidth() / 2.0F;
      float sphereRadius = sphereSize / 2.0F;
      return ownerHalfWidth + sphereRadius + 0.1F;
   }

   public void setupKiBlastPlayer(LivingEntity owner, float damage, float speed, int color, int colorBorder, int colorOutline, float size) {
      this.setOwner(owner);
      this.setKiRenderType(1);
      this.setSize(size);
      this.setKiDamage(damage);
      this.setKiSpeed(speed);
      this.setColors(color, colorBorder, colorOutline);
      this.setFiring(false);
      this.setMaxLife(99999);
      this.setCastTime(100);
      this.setCastOffsets(0.0F, this.calcCenterOffsetY(size) + 0.5F, this.calcForwardOffset(owner, size));
      this.updatePositionRelativeToOwner(owner);
      if (!this.level().isClientSide) {
         this.level().addFreshEntity(this);
      }
   }

   public void setupKiBlastPlayer(LivingEntity owner, float damage, float speed, int color, int colorBorder, float size) {
      this.setupKiBlastPlayer(owner, damage, speed, color, colorBorder, 16777215, size);
   }

   public void setupKiBlastPlayer(LivingEntity owner, float damage, float speed, int color, float size) {
      this.setupKiBlastPlayer(owner, damage, speed, color, color, 16777215, size);
   }

   public void setupSoulPunisherPlayer(LivingEntity owner, float damage, float speed, int color, int colorOutline, float size) {
      this.setOwner(owner);
      this.setKiRenderType(4);
      this.setSize(size);
      this.setKiDamage(damage);
      this.setKiSpeed(speed);
      this.setColors(color, color, colorOutline);
      this.setFiring(false);
      this.setMaxLife(99999);
      this.setCastTime(100);
      this.setCastOffsets(0.0F, 0.0F, 2.0F);
      this.updatePositionRelativeToOwner(owner);
      if (!this.level().isClientSide) {
         this.level().addFreshEntity(this);
      }
   }

   public void setupFakeMoonPlayer(LivingEntity owner, float speed, int color, int colorOutline, float size) {
      this.setOwner(owner);
      this.setKiRenderType(11);
      this.setSize(size);
      this.setKiDamage(0.0F);
      this.setKiSpeed(speed);
      this.setColors(color, color, colorOutline);
      this.setFiring(false);
      this.setMaxLife(99999);
      this.setCastTime(60);
      this.setCastOffsets(0.0F, 0.5F, 0.5F);
      this.updatePositionRelativeToOwner(owner);
      if (!this.level().isClientSide) {
         this.level().addFreshEntity(this);
      }
   }

   private boolean isSoulPunisher() {
      return "soul_punisher".equals(this.getTechniqueId());
   }

   private float soulPunisherDamage(Entity target, float baseDamage) {
      boolean reduced = false;
      if (target instanceof Player targetPlayer) {
         Optional<StatsData> resolved = StatsProvider.get(StatsCapability.INSTANCE, targetPlayer).resolve();
         if (resolved.isPresent() && resolved.get().getResources().getAlignment() >= 41) {
            reduced = true;
         }
      }

      if (!reduced && this.getOwner() instanceof Player ownerPlayer && TargetHelper.getRelation(ownerPlayer, target) == TargetHelper.Relation.NEUTRAL) {
         reduced = true;
      }

      return reduced ? baseDamage * 0.25F : baseDamage;
   }

   public void setupKiLargeBlastPlayer(LivingEntity owner, float damage, float speed, int color, int colorBorder, int colorOutline, float size) {
      this.setOwner(owner);
      this.setKiRenderType(2);
      this.setSize(size);
      this.setKiDamage(damage);
      this.setKiSpeed(speed);
      this.setColors(color, colorBorder, colorOutline);
      this.setFiring(false);
      this.setMaxLife(99999);
      this.setCastTime(40);
      this.setCastOffsets(0.0F, 5.5F, 0.0F);
      this.updatePositionRelativeToOwner(owner);
      if (!this.level().isClientSide) {
         this.level().addFreshEntity(this);
      }
   }

   public void setupKiLargeBlastPlayer(LivingEntity owner, float damage, float speed, int color, int colorBorder, float size) {
      this.setupKiLargeBlastPlayer(owner, damage, speed, color, colorBorder, 16777215, size);
   }

   public void setupKiLargeBlastPlayer(LivingEntity owner, float damage, float speed, int color, float size) {
      this.setupKiLargeBlastPlayer(owner, damage, speed, color, color, 16777215, size);
   }

   public void setupInvertedKiBlastPlayer(LivingEntity owner, float damage, float speed, int color, int colorBorder, int colorOutline, float size) {
      this.setOwner(owner);
      this.setKiRenderType(3);
      this.setSize(size);
      this.setKiDamage(damage);
      this.setKiSpeed(speed);
      this.setColors(color, colorBorder, colorOutline);
      this.setFiring(false);
      this.setMaxLife(99999);
      this.setCastTime(40);
      this.setCastOffsets(0.0F, -0.5F, 0.5F);
      this.updatePositionRelativeToOwner(owner);
      if (!this.level().isClientSide) {
         this.level().addFreshEntity(this);
      }
   }

   public void setupInvertedKiBlastPlayer(LivingEntity owner, float damage, float speed, int color, int colorBorder, float size) {
      this.setupInvertedKiBlastPlayer(owner, damage, speed, color, colorBorder, 16777215, size);
   }

   public void setupInvertedKiBlastPlayer(LivingEntity owner, float damage, float speed, int color, float size) {
      this.setupInvertedKiBlastPlayer(owner, damage, speed, color, color, 16777215, size);
   }

   public void setupKiSoulsPlayer(LivingEntity owner, float damage, float speed, int color, int colorOutline) {
      this.setOwner(owner);
      this.setKiRenderType(4);
      this.setSize(0.6F);
      this.setKiSpeed(speed);
      this.setKiDamage(damage);
      this.setColors(color, color, colorOutline);
      this.setFiring(false);
      this.setMaxLife(99999);
      this.setCastTime(40);
      this.setCastOffsets(0.0F, -0.5F, 0.5F);
      this.updatePositionRelativeToOwner(owner);
      if (!this.level().isClientSide) {
         this.level().addFreshEntity(this);
      }
   }

   public void setupKiSoulsPlayer(LivingEntity owner, float damage, float speed, int color) {
      this.setupKiSoulsPlayer(owner, damage, speed, color, 16777215);
   }

   public void setupKiGenkiPlayer(LivingEntity owner, float damage, float speed, int colorOutline) {
      this.setOwner(owner);
      this.setKiRenderType(5);
      this.setSize(7.0F);
      this.setKiSpeed(speed);
      this.setKiDamage(damage);
      this.setColors(12910589, 63743, colorOutline);
      this.setFiring(false);
      this.setMaxLife(99999);
      this.setCastTime(100);
      this.setCastOffsets(0.0F, 5.5F, 0.0F);
      this.updatePositionRelativeToOwner(owner);
      if (!this.level().isClientSide) {
         this.level().addFreshEntity(this);
      }
   }

   public void setupKiGenkiPlayer(LivingEntity owner, float damage, float speed) {
      this.setupKiGenkiPlayer(owner, damage, speed, 16777215);
   }

   public void setupKiNovaPlayer(LivingEntity owner, float damage, float speed, int colorOutline) {
      this.setOwner(owner);
      this.setKiRenderType(6);
      this.setSize(5.0F);
      this.setKiSpeed(speed);
      this.setKiDamage(damage);
      this.setColors(16741432, 13182496, colorOutline);
      this.setFiring(false);
      this.setMaxLife(99999);
      this.setCastTime(100);
      this.setCastOffsets(0.0F, 2.5F, 0.0F);
      this.updatePositionRelativeToOwner(owner);
      if (!this.level().isClientSide) {
         this.level().addFreshEntity(this);
      }
   }

   public void setupKiNovaPlayer(LivingEntity owner, float damage, float speed) {
      this.setupKiNovaPlayer(owner, damage, speed, 8392206);
   }

   public void setupKiNovaCoolerPlayer(LivingEntity owner, float damage, float speed) {
      this.setOwner(owner);
      this.setKiRenderType(6);
      this.setSize(5.0F);
      this.setKiSpeed(speed);
      this.setKiDamage(damage);
      this.setColors(16726118, 10687546, 4850454);
      this.setFiring(false);
      this.setMaxLife(99999);
      this.setCastTime(100);
      this.setCastOffsets(0.0F, 2.5F, 0.0F);
      this.updatePositionRelativeToOwner(owner);
      if (!this.level().isClientSide) {
         this.level().addFreshEntity(this);
      }
   }

   public void setupKiDeathBallPlayer(LivingEntity owner, float damage, float speed, int color, int colorBorder, int colorOutline) {
      this.setOwner(owner);
      this.setKiRenderType(7);
      this.setSize(2.5F);
      this.setKiSpeed(speed);
      this.setKiDamage(damage);
      this.setColors(color, ColorUtils.darkenColor(colorBorder, 0.5F), colorOutline);
      this.setFiring(false);
      this.setMaxLife(99999);
      this.setCastTime(60);
      this.setCastOffsets(0.0F, 5.5F, 0.0F);
      this.updatePositionRelativeToOwner(owner);
      if (!this.level().isClientSide) {
         this.level().addFreshEntity(this);
      }
   }

   public void setupKiDeathBallPlayer(LivingEntity owner, float damage, float speed, int color, int colorBorder) {
      this.setupKiDeathBallPlayer(owner, damage, speed, color, colorBorder, 16777215);
   }

   public void setupKiDeathBallPlayer(LivingEntity owner, float damage, float speed, int color) {
      this.setupKiDeathBallPlayer(owner, damage, speed, color, color, 16777215);
   }

   public void setupSokidanPlayer(LivingEntity owner, float damage, float speed, int color, int colorOutline, float size) {
      this.setOwner(owner);
      this.setKiRenderType(8);
      this.setSize(size);
      this.setKiDamage(damage);
      this.setKiSpeed(speed);
      this.setColors(color, color, colorOutline);
      this.setControllable(true);
      this.setFiring(false);
      this.setMaxLife(99999);
      this.setCastTime(40);
      this.setCastOffsets(0.0F, 0.5F, 0.5F);
      this.updatePositionRelativeToOwner(owner);
      if (!this.level().isClientSide) {
         this.level().addFreshEntity(this);
      }
   }

   public void setupSokidanPlayer(LivingEntity owner, float damage, float speed, int color, float size) {
      this.setupSokidanPlayer(owner, damage, speed, color, 16777215, size);
   }

   public void setupKiVolleyPlayer(LivingEntity owner, float damage, float speed, int color, int colorOutline, int castTime) {
      this.setOwner(owner);
      this.setKiRenderType(9);
      this.setSize(0.4F);
      this.setKiDamage(damage);
      this.setKiSpeed(speed);
      this.setColors(color, color, colorOutline);
      this.setFiring(false);
      this.setCastTime(8);
      this.setMaxLife(castTime + 100);
      this.setCastOffsets(0.0F, 0.0F, 0.7F);
      this.playInitialSound((SoundEvent)MainSounds.KI_EXPLOSION_CHARGE.get());
      this.updatePositionRelativeToOwner(owner);
      if (!this.level().isClientSide) {
         this.level().addFreshEntity(this);
      }
   }

   public void setupKiVolleyPlayer(LivingEntity owner, float damage, float speed, int color, int castTime) {
      this.setupKiVolleyPlayer(owner, damage, speed, color, 16777215, castTime);
   }

   public void setupKiSmall(LivingEntity owner, float damage, float speed, int color, int colorOutline) {
      this.setOwner(owner);
      this.setKiRenderType(0);
      this.setSize(0.8F);
      this.setKiSpeed(speed);
      this.setKiDamage(damage);
      this.setColors(color, color, colorOutline);
      this.setFiring(true);
      this.setCastTime(0);
      this.setMaxLife(100);
      this.setCastOffsets(0.0F, -0.5F, 0.5F);
      this.playInitialSound((SoundEvent)MainSounds.KI_EXPLOSION_CHARGE.get());
      this.updatePositionRelativeToOwner(owner);
      if (!this.level().isClientSide) {
         this.level().addFreshEntity(this);
      }
   }

   public void setupKiSmall(LivingEntity owner, float damage, float speed, int color) {
      this.setupKiSmall(owner, damage, speed, color, 16777215);
   }

   public void setupKiBlast(LivingEntity owner, float damage, float speed, int color, int colorBorder, int colorOutline, float size, int castTime) {
      this.setOwner(owner);
      this.setKiRenderType(1);
      this.setSize(size);
      this.setKiDamage(damage);
      this.setKiSpeed(speed);
      this.setColors(color, colorBorder, colorOutline);
      this.setFiring(false);
      this.setCastTime(castTime);
      this.setMaxLife(castTime + 100);
      this.setCastOffsets(0.0F, -0.5F, 0.5F);
      this.playInitialSound((SoundEvent)MainSounds.KI_EXPLOSION_CHARGE.get());
      this.updatePositionRelativeToOwner(owner);
      if (!this.level().isClientSide) {
         this.level().addFreshEntity(this);
      }
   }

   public void setupKiBlast(LivingEntity owner, float damage, float speed, int color, int colorBorder, float size, int castTime) {
      this.setupKiBlast(owner, damage, speed, color, colorBorder, 16777215, size, castTime);
   }

   public void setupKiBlast(LivingEntity owner, float damage, float speed, int color, float size, int castTime) {
      this.setupKiBlast(owner, damage, speed, color, color, 16777215, size, castTime);
   }

   public void setupKiLargeBlast(LivingEntity owner, float damage, float speed, int color, int colorBorder, int colorOutline, float size, int castTime) {
      this.setOwner(owner);
      this.setKiRenderType(2);
      this.setSize(size);
      this.setKiDamage(damage);
      this.setKiSpeed(speed);
      this.setColors(color, colorBorder, colorOutline);
      this.setFiring(false);
      this.setCastTime(castTime);
      this.setMaxLife(castTime + 100);
      this.setCastOffsets(0.0F, 5.2F, 0.2F);
      this.playInitialSound((SoundEvent)MainSounds.KI_EXPLOSION_CHARGE.get());
      this.updatePositionRelativeToOwner(owner);
      if (!this.level().isClientSide) {
         this.level().addFreshEntity(this);
      }
   }

   public void setupKiLargeBlast(LivingEntity owner, float damage, float speed, int color, int colorBorder, float size, int castTime) {
      this.setupKiLargeBlast(owner, damage, speed, color, colorBorder, 16777215, size, castTime);
   }

   public void setupKiLargeBlast(LivingEntity owner, float damage, float speed, int color, float size, int castTime) {
      this.setupKiLargeBlast(owner, damage, speed, color, color, 16777215, size, castTime);
   }

   public void setupInvertedKiBlast(LivingEntity owner, float damage, float speed, int color, int colorBorder, int colorOutline, float size, int castTime) {
      this.setOwner(owner);
      this.setKiRenderType(3);
      this.setSize(size);
      this.setKiDamage(damage);
      this.setKiSpeed(speed);
      this.setColors(color, colorBorder, colorOutline);
      this.setFiring(false);
      this.setCastTime(castTime);
      this.setMaxLife(castTime + 100);
      this.setCastOffsets(0.0F, -0.5F, 0.5F);
      this.playInitialSound((SoundEvent)MainSounds.KI_EXPLOSION_CHARGE.get());
      this.updatePositionRelativeToOwner(owner);
      if (!this.level().isClientSide) {
         this.level().addFreshEntity(this);
      }
   }

   public void setupInvertedKiBlast(LivingEntity owner, float damage, float speed, int color, int colorBorder, float size, int castTime) {
      this.setupInvertedKiBlast(owner, damage, speed, color, colorBorder, 16777215, size, castTime);
   }

   public void setupInvertedKiBlast(LivingEntity owner, float damage, float speed, int color, float size, int castTime) {
      this.setupInvertedKiBlast(owner, damage, speed, color, color, 16777215, size, castTime);
   }

   public void setupKiSouls(LivingEntity owner, float damage, float speed, int color, int colorOutline, int castTime) {
      this.setOwner(owner);
      this.setKiRenderType(4);
      this.setSize(0.8F);
      this.setKiSpeed(speed);
      this.setKiDamage(damage);
      this.setColors(color, color, colorOutline);
      this.setFiring(false);
      this.setCastTime(castTime);
      this.setMaxLife(castTime + 100);
      this.setCastOffsets(0.0F, -0.5F, 0.5F);
      this.playInitialSound((SoundEvent)MainSounds.KI_EXPLOSION_CHARGE.get());
      this.updatePositionRelativeToOwner(owner);
      if (!this.level().isClientSide) {
         this.level().addFreshEntity(this);
      }
   }

   public void setupKiSouls(LivingEntity owner, float damage, float speed, int color, int castTime) {
      this.setupKiSouls(owner, damage, speed, color, 16777215, castTime);
   }

   public void setupKiGenki(LivingEntity owner, float damage, float speed, int colorOutline, int castTime) {
      this.setOwner(owner);
      this.setKiRenderType(5);
      this.setSize(5.0F);
      this.setKiSpeed(speed);
      this.setKiDamage(damage);
      this.setColors(3211249, 63743, colorOutline);
      this.setFiring(false);
      this.setCastTime(castTime);
      this.setMaxLife(castTime + 200);
      this.setCastOffsets(0.0F, 5.5F, 0.0F);
      this.playInitialSound((SoundEvent)MainSounds.KI_EXPLOSION_CHARGE.get());
      this.updatePositionRelativeToOwner(owner);
      if (!this.level().isClientSide) {
         this.level().addFreshEntity(this);
      }
   }

   public void setupKiGenki(LivingEntity owner, float damage, float speed, int castTime) {
      this.setupKiGenki(owner, damage, speed, 16777215, castTime);
   }

   public void setupKiNova(LivingEntity owner, float damage, float speed, int colorOutline, int castTime) {
      this.setOwner(owner);
      this.setKiRenderType(6);
      this.setSize(5.0F);
      this.setKiSpeed(speed);
      this.setKiDamage(damage);
      this.setColors(10354688, 10354688, colorOutline);
      this.setFiring(false);
      this.setCastTime(castTime);
      this.setMaxLife(castTime + 200);
      this.setCastOffsets(0.0F, 5.5F, 0.0F);
      this.playInitialSound((SoundEvent)MainSounds.KI_EXPLOSION_CHARGE.get());
      this.updatePositionRelativeToOwner(owner);
      if (!this.level().isClientSide) {
         this.level().addFreshEntity(this);
      }
   }

   public void setupKiNova(LivingEntity owner, float damage, float speed, int castTime) {
      this.setupKiNova(owner, damage, speed, 16777215, castTime);
   }

   public void setupKiDeathBall(LivingEntity owner, float damage, float speed, int color, int colorBorder, int colorOutline, int castTime) {
      this.setOwner(owner);
      this.setKiRenderType(7);
      this.setSize(2.5F);
      this.setKiSpeed(speed);
      this.setKiDamage(damage);
      this.setColors(color, ColorUtils.darkenColor(colorBorder, 0.5F), colorOutline);
      this.setFiring(false);
      this.setCastTime(castTime);
      this.setMaxLife(castTime + 150);
      this.setCastOffsets(0.0F, 2.5F, 0.0F);
      this.playInitialSound((SoundEvent)MainSounds.KI_EXPLOSION_CHARGE.get());
      this.updatePositionRelativeToOwner(owner);
      if (!this.level().isClientSide) {
         this.level().addFreshEntity(this);
      }
   }

   public void setupKiDeathBall(LivingEntity owner, float damage, float speed, int color, int colorBorder, int castTime) {
      this.setupKiDeathBall(owner, damage, speed, color, colorBorder, 16777215, castTime);
   }

   public void setupKiDeathBall(LivingEntity owner, float damage, float speed, int color, int castTime) {
      this.setupKiDeathBall(owner, damage, speed, color, color, 16777215, castTime);
   }

   public void setupSokidan(LivingEntity owner, float damage, float speed, int color, int colorOutline, float size, int castTime) {
      this.setOwner(owner);
      this.setKiRenderType(8);
      this.setSize(size);
      this.setKiDamage(damage);
      this.setKiSpeed(speed);
      this.setColors(color, color, colorOutline);
      this.setControllable(true);
      this.setFiring(false);
      this.setCastTime(castTime);
      this.setMaxLife(castTime + 500);
      this.setCastOffsets(0.0F, 0.5F, 0.5F);
      this.playInitialSound((SoundEvent)MainSounds.KI_EXPLOSION_CHARGE.get());
      this.updatePositionRelativeToOwner(owner);
      if (!this.level().isClientSide) {
         this.level().addFreshEntity(this);
      }
   }

   public void setupSokidan(LivingEntity owner, float damage, float speed, int color, float size, int castTime) {
      this.setupSokidan(owner, damage, speed, color, 16777215, size, castTime);
   }

   public void setupKiVolley(LivingEntity owner, float damage, float speed, int color, int colorOutline, int castTime) {
      this.setOwner(owner);
      this.setKiRenderType(9);
      this.setSize(0.0F);
      this.setKiDamage(damage);
      this.setKiSpeed(speed);
      this.setColors(color, color, colorOutline);
      this.setFiring(false);
      this.setCastTime(castTime);
      this.setMaxLife(castTime * 2);
      this.setCastOffsets(0.0F, 0.2F, 0.5F);
      this.playInitialSound((SoundEvent)MainSounds.KI_EXPLOSION_CHARGE.get());
      this.updatePositionRelativeToOwner(owner);
      if (!this.level().isClientSide) {
         this.level().addFreshEntity(this);
      }
   }

   public void setupKiVolley(LivingEntity owner, float damage, float speed, int color, int castTime) {
      this.setupKiVolley(owner, damage, speed, color, 16777215, castTime);
   }

   public void setupKiAirVolley(LivingEntity owner, float damage, float speed, int color, int colorOutline, int castTime) {
      this.setOwner(owner);
      this.setKiRenderType(10);
      this.setSize(0.0F);
      this.setKiDamage(damage);
      this.setKiSpeed(speed);
      this.setColors(color, color, colorOutline);
      this.setFiring(false);
      this.setCastTime(castTime);
      this.setMaxLife(castTime * 2);
      this.setCastOffsets(0.0F, 1.5F, 0.0F);
      this.playInitialSound((SoundEvent)MainSounds.KI_EXPLOSION_CHARGE.get());
      this.updatePositionRelativeToOwner(owner);
      if (!this.level().isClientSide) {
         this.level().addFreshEntity(this);
      }
   }

   public void toggleSokidanControl() {
      if (this.isControllable()) {
         boolean currentMode = this.isParked();
         this.setParked(!currentMode);
         if (this.isParked()) {
            if (this.getOwner() instanceof LivingEntity owner) {
               float dist = (float)this.position().distanceTo(owner.getEyePosition());
               this.setParkedDistance(dist);
            }

            this.setDeltaMovement(0.0, 0.0, 0.0);
         } else if (this.getOwner() instanceof LivingEntity owner) {
            Vec3 look = CameraAimHelper.resolve(owner);
            this.shootFromRotation(owner, CameraAimHelper.pitch(look), CameraAimHelper.yaw(owner, look), 0.0F, this.getKiSpeed(), 0.0F);
         }
      }
   }

   public void fireHability(int finalMaxLife) {
      this.setFiring(true);
      this.setMaxLife(this.tickCount + finalMaxLife);
      this.setFireTick(this.tickCount);
      if (this.getOwner() instanceof LivingEntity livingOwner) {
         if (this.getKiRenderType() == 9) {
            if (this.getOwner() instanceof Player) {
               this.triggerAnimationPacket("_fire");
            }

            return;
         }

         Vec3 eyePos = livingOwner.getEyePosition();
         Vec3 lookDir = CameraAimHelper.resolve(livingOwner);
         double reach = 100.0;
         Vec3 endPos = eyePos.add(lookDir.scale(reach));
         BlockHitResult blockHit = this.level().clip(new ClipContext(eyePos, endPos, Block.COLLIDER, Fluid.NONE, livingOwner));
         if (blockHit.getType() != Type.MISS) {
            endPos = SableCompat.projectToWorld(this.level(), blockHit.getLocation());
            reach = eyePos.distanceTo(endPos);
         }

         AABB searchBox = livingOwner.getBoundingBox().expandTowards(lookDir.scale(reach)).inflate(1.0);

         for (Entity entity : this.level().getEntities(livingOwner, searchBox, e -> !e.isSpectator() && e.isPickable())) {
            AABB hitbox = entity.getBoundingBox().inflate(0.3);
            Optional<Vec3> hit = hitbox.clip(eyePos, endPos);
            if (hit.isPresent()) {
               double dist = eyePos.distanceTo(hit.get());
               if (dist < reach) {
                  reach = dist;
                  endPos = hit.get();
               }
            }
         }

         Vec3 kiPos = new Vec3(this.getX(), this.getVisualCenterY(), this.getZ());
         Vec3 newTrajectory = endPos.subtract(kiPos).normalize();
         this.shoot(newTrajectory.x, newTrajectory.y, newTrajectory.z, this.getKiSpeed(), 0.0F);
         this.hasImpulse = true;
         SoundEvent fireSound;
         if ("burning_attack".equals(this.getTechniqueId())) {
            fireSound = (SoundEvent)MainSounds.KI_BURNING_FIRE.get();
         } else if (this.getKiRenderType() == 5) {
            fireSound = (SoundEvent)MainSounds.KI_SPIRITBOMB_FIRE.get();
         } else if (this.getKiRenderType() == 6) {
            fireSound = (SoundEvent)MainSounds.KI_SUPERNOVA_FIRE.get();
         } else {
            fireSound = (SoundEvent)MainSounds.KIBLAST_ATTACK.get();
         }

         this.level().playSound(null, this.getX(), this.getY(), this.getZ(), fireSound, SoundSource.PLAYERS, 0.7F, 1.0F);
      }

      if (this.getOwner() instanceof Player) {
         this.triggerAnimationPacket("_fire");
      }
   }

   private void finalizeSetupAndShoot(LivingEntity owner, float speed) {
      this.setOwner(owner);
      if (this.getCastTime() <= 0) {
         Vec3 lookDir = owner.getLookAngle();
         Vec3 spawnPos = owner.getEyePosition().add(lookDir.scale(0.5));
         this.setPos(spawnPos.x, spawnPos.y - 0.2, spawnPos.z);
         this.shootFromRotation(owner, owner.getXRot(), owner.getYRot(), 0.0F, speed, 0.0F);
         this.level()
            .playSound(
               null,
               this.getX(),
               this.getY(),
               this.getZ(),
               (SoundEvent)MainSounds.KIBLAST_ATTACK.get(),
               SoundSource.PLAYERS,
               0.1F,
               1.0F + this.random.nextFloat() * 0.2F
            );
      } else {
         this.updatePositionRelativeToOwner(owner);
      }

      if (!this.level().isClientSide) {
         this.level().addFreshEntity(this);
      }
   }

   @Override
   public void tick() {
      if (this.level().isClientSide && this.isControllable() && this.getOwner() instanceof Player player && player.isLocalPlayer()) {
         CameraAimHelper.trackLocalSokidan(this.getId(), this.isActivelyControlledSokidan());
      }

      if (!this.level().isClientSide && !this.isFiring() && this.getMaxLife() != 99999 && this.tickCount >= this.getCastTime()) {
         this.fireHability(this.getMaxLife() - this.tickCount);
      }

      boolean isFiring;
      isFiring = this.isFiring();
      label47:
      if (!isFiring && this.getCastTime() > 0 || this.getKiRenderType() == 9 || this.getKiRenderType() == 10) {
         if (this.getOwner() instanceof LivingEntity livingOwner && livingOwner.isAlive()) {
            this.updatePositionRelativeToOwner(livingOwner);
            this.setDeltaMovement(0.0, 0.0, 0.0);
            break label47;
         }

         if (!this.level().isClientSide) {
            this.discard();
            return;
         }
      }

      super.tick();
      if (!isFiring && this.getCastTime() > 0) {
         this.setDeltaMovement(0.0, 0.0, 0.0);
      }
   }

   @Override
   protected void onKiTick() {
      if (!this.level().isClientSide && this.getOwner() == null) {
         this.discard();
      } else {
         boolean isCasting = !this.isFiring();
         int type = this.getKiRenderType();
         Entity ownerEntity = this.getOwner();
         if (type != 9 && type != 10) {
            if (type == 11) {
               if (!this.level().isClientSide) {
                  if (this.isFiring()) {
                     if (Double.isNaN(this.fakeMoonStartY)) {
                        this.fakeMoonStartY = this.getY();
                     }

                     if (this.fakeMoonFrozenTick < 0) {
                        if (this.getY() - this.fakeMoonStartY < 30.0) {
                           this.setDeltaMovement(0.0, 1.0, 0.0);
                        } else {
                           this.setDeltaMovement(0.0, 0.0, 0.0);
                           this.fakeMoonFrozenTick = this.tickCount;
                           this.setParked(true);
                        }
                     } else {
                        this.setDeltaMovement(0.0, 0.0, 0.0);
                        if (this.tickCount - this.fakeMoonFrozenTick >= 400) {
                           this.discard();
                        }
                     }
                  } else {
                     this.setDeltaMovement(0.0, 0.0, 0.0);
                  }
               }
            } else {
               if (this.isActivelyControlledSokidan() && ownerEntity instanceof LivingEntity owner) {
                  Vec3 eyePos = owner.getEyePosition();
                  Vec3 look = CameraAimHelper.resolve(owner);
                  Vec3 targetPos = eyePos.add(look.scale((double)this.getParkedDistance()));
                  Vec3 diff = targetPos.subtract(this.position());
                  if (diff.lengthSqr() > 0.02) {
                     double followSpeed = (double)this.getKiSpeed() * 1.5;
                     this.setDeltaMovement(diff.normalize().scale(Math.min(diff.length(), followSpeed)));
                  } else {
                     this.setDeltaMovement(0.0, 0.0, 0.0);
                     this.setPos(targetPos.x, targetPos.y, targetPos.z);
                  }

                  this.setYRot(CameraAimHelper.yaw(owner, look));
                  this.setXRot(CameraAimHelper.pitch(look));
                  if (!this.level().isClientSide) {
                     this.hasImpulse = true;
                  }
               }

               if (!this.level().isClientSide) {
                  if (isCasting) {
                     if (type == 5 && this.tickCount == 1) {
                        this.playSound((SoundEvent)MainSounds.KI_SPIRITBOMB_CHARGE.get(), 0.7F, 1.0F);
                     } else if (type == 6 && this.tickCount == 1) {
                        this.playSound((SoundEvent)MainSounds.KI_SUPERNOVA_CHARGE.get(), 0.7F, 1.0F);
                     }
                  } else {
                     if (this.isDetonating) {
                        this.processDetonation();
                        return;
                     }

                     if ((type == 5 || type == 6) && this.tickCount % 20 == 0) {
                        if (this.destroyBlocksInPath()) {
                           this.setDeltaMovement(this.getDeltaMovement().scale(0.95));
                        }

                        if (this.getDeltaMovement().lengthSqr() < 0.01) {
                           this.explodeAndDie();
                           return;
                        }
                     }

                     if (!this.isSoulPunisher() && this.tickCount % 10 == 0) {
                        this.pulseAreaDamage();
                     }
                  }

                  if (this.tickCount >= this.getMaxLife()) {
                     if (this.isSoulPunisher()) {
                        this.discard();
                     } else {
                        this.explodeAndDie();
                     }

                     return;
                  }
               }

               if (this.level().isClientSide) {
                  float scale = this.getSize();
                  float[] borderColor = this.getRgbColorBorder();
                  float pr = borderColor[0];
                  float pg = borderColor[1];
                  float pb = borderColor[2];
                  if (type == 4) {
                     float speed = (float)this.tickCount * 0.5F;
                     pr = (float)(Math.sin((double)speed) * 0.5 + 0.5);
                     pg = (float)(Math.sin((double)speed + 2.0944) * 0.5 + 0.5);
                     pb = (float)(Math.sin((double)speed + 4.1888) * 0.5 + 0.5);
                  }

                  if (type >= 1 && !isCasting) {
                     for (int i = 0; i < 3; i++) {
                        double radius = (double)scale * 1.2;
                        double theta = this.random.nextDouble() * 2.0 * Math.PI;
                        double phi = Math.acos(2.0 * this.random.nextDouble() - 1.0);
                        double dx = radius * Math.sin(phi) * Math.cos(theta);
                        double dy = radius * Math.sin(phi) * Math.sin(theta);
                        double dz = radius * Math.cos(phi);
                        double vx = dx * 0.15;
                        double vy = dy * 0.15;
                        double vz = dz * 0.15;
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
                           trail.setKiColor(pr, pg, pb);
                           trail.setKiScale(scale);
                        }
                     }
                  }

                  if (type == 2 || type == 5 || type == 6) {
                     for (int ix = 0; ix < 10; ix++) {
                        double absDist = (double)(scale * 3.0F);
                        double angle = this.random.nextDouble() * Math.PI * 2.0;
                        double sx = Math.cos(angle) * absDist;
                        double sz = Math.sin(angle) * absDist;
                        double sy = (this.random.nextDouble() - 0.5) * 2.0 * absDist;
                        if (Minecraft.getInstance()
                           .particleEngine
                           .createParticle(
                              (ParticleOptions)MainParticles.KI_SHEDDING.get(),
                              this.getX() + sx,
                              this.getY() + (double)(this.getBbHeight() / 2.0F) + sy,
                              this.getZ() + sz,
                              -sx * 0.15,
                              -sy * 0.15,
                              -sz * 0.15
                           ) instanceof KiSheddingParticle kiParticle) {
                           kiParticle.setKiColor(borderColor[0], borderColor[1], borderColor[2]);
                        }
                     }
                  }
               }

               if (this.level().isClientSide && !this.hasSpawnedSplash) {
                  if (type != 0) {
                     float[] rgb = this.getRgbColorBorder();
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
                  }

                  this.hasSpawnedSplash = true;
               }
            }
         } else {
            if (ownerEntity instanceof LivingEntity owner && owner.isAlive()) {
               if (type == 10) {
                  if (isCasting) {
                     owner.setDeltaMovement(0.0, 0.05, 0.0);
                  } else {
                     owner.setDeltaMovement(0.0, 0.0, 0.0);
                  }
               } else {
                  owner.setDeltaMovement(0.0, 0.0, 0.0);
               }

               owner.fallDistance = 0.0F;
               owner.hasImpulse = true;
               if (!this.level().isClientSide) {
                  if (!isCasting) {
                     if (type == 9) {
                        if (this.tickCount % 2 == 0) {
                           for (int ixx = 0; ixx < 5; ixx++) {
                              KiBlastEntity bullet = new KiBlastEntity(this.level(), owner);
                              bullet.setupKiSmall(owner, this.getKiDamage(), this.getKiSpeed(), this.getColor());
                              bullet.setTechniqueId(this.getTechniqueId());
                              bullet.shootFromRotation(owner, owner.getXRot(), owner.getYRot(), 0.0F, this.getKiSpeed(), 6.0F);
                              this.level().addFreshEntity(bullet);
                           }

                           this.level()
                              .playSound(
                                 null,
                                 this.getX(),
                                 this.getY(),
                                 this.getZ(),
                                 (SoundEvent)MainSounds.KIBLAST_ATTACK.get(),
                                 SoundSource.PLAYERS,
                                 0.1F,
                                 1.6F + this.random.nextFloat() * 0.5F
                              );
                        }
                     } else if (type == 10 && this.tickCount % 2 == 0) {
                        for (int ixx = 0; ixx < 10; ixx++) {
                           KiBlastEntity bullet = new KiBlastEntity(this.level(), owner);
                           bullet.setupKiSmall(owner, this.getKiDamage(), this.getKiSpeed(), this.getColor());
                           bullet.setTechniqueId(this.getTechniqueId());
                           double spawnX = owner.getX();
                           double spawnY = owner.getY() + (double)owner.getBbHeight() / 2.0;
                           double spawnZ = owner.getZ();
                           bullet.setPos(spawnX, spawnY, spawnZ);
                           float randomPitch = this.random.nextFloat() * 180.0F - 90.0F;
                           float randomYaw = this.random.nextFloat() * 360.0F;
                           bullet.shootFromRotation(owner, randomPitch, randomYaw, 0.0F, this.getKiSpeed() / 3.0F, 0.0F);
                           this.level().addFreshEntity(bullet);
                        }

                        this.level()
                           .playSound(
                              null,
                              this.getX(),
                              this.getY(),
                              this.getZ(),
                              (SoundEvent)MainSounds.KIBLAST_ATTACK.get(),
                              SoundSource.PLAYERS,
                              0.15F,
                              1.2F + this.random.nextFloat() * 0.4F
                           );
                     }
                  }

                  if (this.tickCount >= this.getMaxLife()) {
                     this.discard();
                     return;
                  }
               }

               return;
            }

            if (!this.level().isClientSide) {
               this.discard();
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
      builder.define(IS_CONTROLLABLE, false);
      builder.define(IS_PARKED, false);
      builder.define(PARKED_DISTANCE, 0.0F);
      builder.define(IS_FIRING, false);
   }

   public void setCastTime(int ticks) {
      this.entityData.set(CAST_TIME, ticks);
   }

   public int getCastTime() {
      return (Integer)this.entityData.get(CAST_TIME);
   }

   public void setCastOffsets(float offsetX, float offsetY, float offsetZ) {
      this.entityData.set(OFFSET_X, offsetX);
      this.entityData.set(OFFSET_Y, offsetY);
      this.entityData.set(OFFSET_Z, offsetZ);
   }

   public void setControllable(boolean controllable) {
      this.entityData.set(IS_CONTROLLABLE, controllable);
   }

   public boolean isControllable() {
      return (Boolean)this.entityData.get(IS_CONTROLLABLE);
   }

   public void setParked(boolean parked) {
      this.entityData.set(IS_PARKED, parked);
   }

   public boolean isParked() {
      return (Boolean)this.entityData.get(IS_PARKED);
   }

   public void setParkedDistance(float dist) {
      this.entityData.set(PARKED_DISTANCE, dist);
   }

   public float getParkedDistance() {
      return (Float)this.entityData.get(PARKED_DISTANCE);
   }

   @Override
   public boolean isFiring() {
      return (Boolean)this.entityData.get(IS_FIRING);
   }

   @Override
   public void setFiring(boolean firing) {
      this.entityData.set(IS_FIRING, firing);
   }

   public boolean isActivelyControlledSokidan() {
      return this.isFiring() && this.isParked() && this.isControllable();
   }

   private boolean isLocallyPredictedSokidan() {
      return this.level().isClientSide && this.isActivelyControlledSokidan() && this.getOwner() instanceof Player player && player.isLocalPlayer();
   }

   public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
      if (!this.isLocallyPredictedSokidan()) {
         super.lerpTo(x, y, z, yRot, xRot, steps);
      }
   }

   public void lerpMotion(double x, double y, double z) {
      if (!this.isLocallyPredictedSokidan()) {
         super.lerpMotion(x, y, z);
      }
   }

   @Override
   public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
      super.onSyncedDataUpdated(key);
      if (IS_PARKED.equals(key) && this.level().isClientSide && this.isControllable() && this.getOwner() instanceof Player player && player.isLocalPlayer()) {
         CameraAimHelper.trackLocalSokidan(this.getId(), this.isActivelyControlledSokidan());
         this.setDeltaMovement(this.isParked() ? Vec3.ZERO : CameraAimHelper.resolve(player).scale((double)this.getKiSpeed()));
      }
   }

   private void updatePositionRelativeToOwner(LivingEntity owner) {
      Vec3 look = owner.getLookAngle();
      Vec3 worldUp = new Vec3(0.0, 1.0, 0.0);
      Vec3 right = look.cross(worldUp).normalize();
      if (right.lengthSqr() < 1.0E-6) {
         right = new Vec3(1.0, 0.0, 0.0);
      }

      Vec3 offset = right.scale((double)((Float)this.entityData.get(OFFSET_X)).floatValue())
         .add(worldUp.scale((double)((Float)this.entityData.get(OFFSET_Y)).floatValue()))
         .add(look.scale((double)((Float)this.entityData.get(OFFSET_Z)).floatValue()));
      double centerX = owner.getX();
      double centerY = owner.getY() + (double)owner.getBbHeight() / 2.0;
      double centerZ = owner.getZ();
      Vec3 newPos = new Vec3(centerX, centerY, centerZ).add(offset);
      this.setPos(newPos.x, newPos.y, newPos.z);
      this.setYRot(owner.getYRot());
      this.setXRot(owner.getXRot());
   }

   private void pulseAreaDamage() {
      AABB area = this.getBoundingBox().inflate(5.0);
      List<LivingEntity> nearby = MultipartTargeting.collectTargets(this.level(), area);
      Vec3 center = new Vec3(this.getX(), this.getVisualCenterY(), this.getZ());
      double radius = (double)this.getSize() / 2.0;

      for (LivingEntity target : nearby) {
         if (MultipartTargeting.withinRadius(target, center, radius + 5.0) && this.shouldDamage(target)) {
            boolean wasHit = this.applyDamageOrHeal(target, this.getDamagePerHit());
            if (wasHit) {
               this.onSuccessfulHit(target);
            }
         }
      }
   }

   protected void onHitEntity(EntityHitResult pResult) {
      boolean isCasting = !this.isFiring();
      if (!isCasting) {
         if (this.getKiRenderType() != 11) {
            if (this.isSoulPunisher()) {
               if (!this.level().isClientSide) {
                  Entity targetEntity = pResult.getEntity();
                  if (this.shouldDamage(targetEntity)) {
                     float dealt = this.soulPunisherDamage(targetEntity, this.getKiDamage());
                     boolean wasHit = this.applyDamageOrHeal(targetEntity, dealt);
                     if (wasHit) {
                        this.onSuccessfulHit(targetEntity);
                        if (this.level() instanceof ServerLevel serverLevel) {
                           serverLevel.sendParticles(
                              (SimpleParticleType)MainParticles.KI_SPLASH_WAVE.get(),
                              targetEntity.getX(),
                              targetEntity.getY() + (double)targetEntity.getBbHeight() / 2.0,
                              targetEntity.getZ(),
                              0,
                              (double)this.getColorBorder(),
                              (double)this.getSize(),
                              0.0,
                              1.0
                           );
                        }
                     }
                  }

                  this.discard();
               }
            } else {
               super.onHitEntity(pResult);
               if (!this.level().isClientSide) {
                  Entity targetEntity = pResult.getEntity();
                  if (this.shouldDamage(targetEntity)) {
                     boolean wasHit = this.applyDamageOrHeal(targetEntity, this.getKiDamage());
                     if (wasHit) {
                        this.onSuccessfulHit(targetEntity);
                        if (this.level() instanceof ServerLevel serverLevel) {
                           double colorData = (double)this.getColorBorder();
                           double sizeData = (double)this.getSize();
                           double pX = targetEntity.getX();
                           double pY = targetEntity.getY() + (double)targetEntity.getBbHeight() / 2.0;
                           double pZ = targetEntity.getZ();
                           serverLevel.sendParticles((SimpleParticleType)MainParticles.KI_SPLASH_WAVE.get(), pX, pY, pZ, 0, colorData, sizeData, 0.0, 1.0);
                        }
                     }
                  }

                  if (this.isControllable()) {
                     return;
                  }

                  int type = this.getKiRenderType();
                  if (type != 5 && type != 6) {
                     this.explodeAndDie();
                  } else {
                     this.setDeltaMovement(this.getDeltaMovement().scale(0.85));
                     if (this.getDeltaMovement().lengthSqr() < 0.01) {
                        this.explodeAndDie();
                     }
                  }
               }
            }
         }
      }
   }

   protected void onHitBlock(BlockHitResult pResult) {
      boolean isCasting = !this.isFiring();
      if (!isCasting) {
         int type = this.getKiRenderType();
         if (type != 11) {
            if (this.isSoulPunisher()) {
               if (!this.level().isClientSide) {
                  this.discard();
               }
            } else {
               if (type != 5 && type != 6) {
                  super.onHitBlock(pResult);
                  if (!this.level().isClientSide) {
                     this.explodeAndDie();
                  }
               }
            }
         }
      }
   }

   private void explodeAndDie() {
      if (!this.isRemoved() && !this.isDetonating) {
         int type = this.getKiRenderType();
         double centerY = this.getVisualCenterY();
         if ((type == 5 || type == 6) && !this.level().isClientSide) {
            this.isDetonating = true;
            this.maxDetonationRadius = this.getSize() / 2.0F * 5.0F;
            this.currentDetonationRadius = 0.0F;
            this.setDeltaMovement(0.0, 0.0, 0.0);
            AABB damageArea = new AABB(this.getX(), centerY, this.getZ(), this.getX(), centerY, this.getZ()).inflate((double)this.maxDetonationRadius);

            for (LivingEntity target : MultipartTargeting.collectTargets(this.level(), damageArea)) {
               if (this.shouldDamage(target)) {
                  boolean wasHit = this.applyDamageOrHeal(target, this.getKiDamage());
                  if (wasHit) {
                     this.onSuccessfulHit(target);
                  }
               }
            }

            float visualParticleSize = this.maxDetonationRadius * 1.8F;
            if (this.level() instanceof ServerLevel serverLevel) {
               serverLevel.sendParticles(
                  (SimpleParticleType)MainParticles.KI_EXPLOSION.get(), this.getX(), centerY, this.getZ(), 0, (double)visualParticleSize, 0.0, 0.0, 1.0
               );
               serverLevel.playSound(null, this.getX(), centerY, this.getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 5.0F, 0.6F);
               KiExplosionVisualEntity explosionVisual = new KiExplosionVisualEntity((EntityType<?>)MainEntities.KI_EXPLOSION_VISUAL.get(), this.level());
               explosionVisual.setPos(this.getX(), centerY, this.getZ());
               explosionVisual.setupExplosion(this.getColor(), this.getColorBorder(), this.getColorOutline(), this.getSize() * 1.2F);
               this.level().addFreshEntity(explosionVisual);
            }
         } else {
            float explosionRadius = this.getSize() / 2.0F * 3.0F;
            float visualParticleSize = explosionRadius * 1.8F;
            AABB damageArea = new AABB(this.getX(), centerY, this.getZ(), this.getX(), centerY, this.getZ()).inflate((double)explosionRadius);

            for (LivingEntity targetx : MultipartTargeting.collectTargets(this.level(), damageArea)) {
               if (this.shouldDamage(targetx)) {
                  boolean wasHit = this.applyDamageOrHeal(targetx, this.getKiDamage());
                  if (wasHit) {
                     this.onSuccessfulHit(targetx);
                  }
               }
            }

            if (!this.level().isClientSide) {
               BlockPos center = BlockPos.containing(this.getX(), centerY, this.getZ());
               float destructionRadius = this.scaledDestructionRadius(explosionRadius);
               int blockRadius = Math.round(destructionRadius);

               for (int x = -blockRadius; x <= blockRadius; x++) {
                  for (int y = -blockRadius; y <= blockRadius; y++) {
                     for (int z = -blockRadius; z <= blockRadius; z++) {
                        if ((float)(x * x + y * y + z * z) <= destructionRadius * destructionRadius) {
                           BlockPos targetPos = center.offset(x, y, z);
                           if (this.level().getBlockState(targetPos).getExplosionResistance(this.level(), targetPos, null) < 1000.0F) {
                              this.setKiBlockToAir(targetPos, 2);
                           }
                        }
                     }
                  }
               }

               if (this.level() instanceof ServerLevel serverLevel) {
                  serverLevel.sendParticles(
                     (SimpleParticleType)MainParticles.KI_EXPLOSION.get(), this.getX(), centerY, this.getZ(), 0, (double)visualParticleSize, 0.0, 0.0, 1.0
                  );
                  serverLevel.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 5.0F, 0.6F);
                  KiExplosionVisualEntity explosionVisual = new KiExplosionVisualEntity((EntityType<?>)MainEntities.KI_EXPLOSION_VISUAL.get(), this.level());
                  explosionVisual.setPos(this.getX(), centerY, this.getZ());
                  explosionVisual.setupExplosion(this.getColor(), this.getColorBorder(), this.getColorOutline(), this.getSize());
                  this.level().addFreshEntity(explosionVisual);
               }
            }

            this.discard();
         }
      }
   }

   private void processDetonation() {
      float prevRadius = this.currentDetonationRadius;
      this.currentDetonationRadius += 2.0F;
      float destructionMult = (float)this.getDestructionMultiplier();
      float scaledRadius = this.currentDetonationRadius * destructionMult;
      float scaledPrevRadius = prevRadius * destructionMult;
      int bRad = Math.round(scaledRadius);
      BlockPos center = BlockPos.containing(this.getX(), this.getVisualCenterY(), this.getZ());
      Level level = this.level();
      float radSq = scaledRadius * scaledRadius;
      float prevRadSq = scaledPrevRadius * scaledPrevRadius;

      for (int x = -bRad; x <= bRad; x++) {
         for (int y = -bRad; y <= bRad; y++) {
            for (int z = -bRad; z <= bRad; z++) {
               float distSq = (float)(x * x + y * y + z * z);
               if (distSq <= radSq && distSq > prevRadSq) {
                  BlockPos targetPos = center.offset(x, y, z);
                  if (!level.getBlockState(targetPos).isAir() && level.getBlockState(targetPos).getExplosionResistance(level, targetPos, null) < 1000.0F) {
                     this.setKiBlockToAir(targetPos, 2);
                  }
               }
            }
         }
      }

      if (this.currentDetonationRadius >= this.maxDetonationRadius) {
         this.discard();
      }
   }

   private boolean destroyBlocksInPath() {
      boolean hitSomething = false;
      float eatRadius = this.scaledDestructionRadius(this.getSize() * 2.0F);
      int bRad = Math.round(eatRadius);
      BlockPos center = BlockPos.containing(this.getX(), this.getVisualCenterY(), this.getZ());
      Level level = this.level();

      for (int x = -bRad; x <= bRad; x++) {
         for (int y = -bRad; y <= bRad; y++) {
            for (int z = -bRad; z <= bRad; z++) {
               if ((float)(x * x + y * y + z * z) <= eatRadius * eatRadius) {
                  BlockPos targetPos = center.offset(x, y, z);
                  if (!level.getBlockState(targetPos).isAir()
                     && level.getBlockState(targetPos).getExplosionResistance(level, targetPos, null) < 1000.0F
                     && this.destroyKiBlock(targetPos, false)) {
                     hitSomething = true;
                     if (level instanceof ServerLevel) {
                        ServerLevel serverLevel = (ServerLevel)level;
                        if (this.random.nextFloat() < 0.25F) {
                           serverLevel.sendParticles(
                              ParticleTypes.CAMPFIRE_COSY_SMOKE,
                              (double)targetPos.getX() + 0.5,
                              (double)targetPos.getY() + 0.5,
                              (double)targetPos.getZ() + 0.5,
                              1,
                              0.5,
                              0.5,
                              0.5,
                              0.05
                           );
                        }
                     }
                  }
               }
            }
         }
      }

      if (hitSomething && !this.level().isClientSide) {
         KiExplosionVisualEntity explosionVisual = new KiExplosionVisualEntity((EntityType<?>)MainEntities.KI_EXPLOSION_VISUAL.get(), this.level());
         explosionVisual.setPos(this.getX(), this.getVisualCenterY(), this.getZ());
         explosionVisual.setupExplosion(this.getColor(), this.getColorBorder(), this.getColorOutline(), this.getSize());
         this.level().addFreshEntity(explosionVisual);
      }

      return hitSomething;
   }

   @Override
   protected void addAdditionalSaveData(CompoundTag pCompound) {
      super.addAdditionalSaveData(pCompound);
      pCompound.putInt("CastTime", this.getCastTime());
      pCompound.putFloat("OffsetX", (Float)this.entityData.get(OFFSET_X));
      pCompound.putFloat("OffsetY", (Float)this.entityData.get(OFFSET_Y));
      pCompound.putFloat("OffsetZ", (Float)this.entityData.get(OFFSET_Z));
      pCompound.putBoolean("IsControllable", this.isControllable());
      pCompound.putBoolean("IsParked", this.isParked());
      pCompound.putFloat("ParkedDistance", this.getParkedDistance());
   }

   @Override
   protected void readAdditionalSaveData(CompoundTag pCompound) {
      super.readAdditionalSaveData(pCompound);
      if (pCompound.contains("CastTime")) {
         this.setCastTime(pCompound.getInt("CastTime"));
      }

      if (pCompound.contains("OffsetX")) {
         this.entityData.set(OFFSET_X, pCompound.getFloat("OffsetX"));
      }

      if (pCompound.contains("OffsetY")) {
         this.entityData.set(OFFSET_Y, pCompound.getFloat("OffsetY"));
      }

      if (pCompound.contains("OffsetZ")) {
         this.entityData.set(OFFSET_Z, pCompound.getFloat("OffsetZ"));
      }

      if (pCompound.contains("IsControllable")) {
         this.setControllable(pCompound.getBoolean("IsControllable"));
      }

      if (pCompound.contains("IsParked")) {
         this.setParked(pCompound.getBoolean("IsParked"));
      }

      if (pCompound.contains("ParkedDistance")) {
         this.setParkedDistance(pCompound.getFloat("ParkedDistance"));
      }
   }

   private double getVisualCenterY() {
      return this.getY() + (double)this.getSize() / 2.0;
   }
}
