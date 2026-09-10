package com.dragonminez.common.network.C2S;

import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.init.MainEffects;
import com.dragonminez.common.init.MainSounds;
import com.dragonminez.common.network.NetworkHandler;
import com.dragonminez.common.network.S2C.ResourceSyncS2C;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.common.util.ITTeleportHelper;
import com.dragonminez.common.util.TransformationsHelper;
import com.dragonminez.compat.network.NetworkEvent;
import com.dragonminez.server.events.players.combat.DashHandler;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class InstantTransmissionTapC2S {
   private final UUID targetId;
   private static final double CROSSHAIR_COS_THRESHOLD = 0.93;
   private static final long RECENT_HIT_WINDOW_MS = 6000L;

   public InstantTransmissionTapC2S(UUID targetId) {
      this.targetId = targetId;
   }

   public InstantTransmissionTapC2S(FriendlyByteBuf buf) {
      this.targetId = buf.readBoolean() ? buf.readUUID() : null;
   }

   public void toBytes(FriendlyByteBuf buf) {
      buf.writeBoolean(this.targetId != null);
      if (this.targetId != null) {
         buf.writeUUID(this.targetId);
      }
   }

   public void handle(Supplier<NetworkEvent.Context> ctx) {
      ctx.get()
         .enqueueWork(
            () -> {
               ServerPlayer player = ctx.get().getSender();
               if (player != null) {
                  StatsProvider.get(StatsCapability.INSTANCE, player)
                     .ifPresent(
                        data -> {
                           int skillLevel = data.getSkills().getSkillLevel("instant_transmission");
                           if (skillLevel > 0) {
                              boolean bypassCosts = player.isCreative() || player.isSpectator();
                              if (!bypassCosts && data.getCooldowns().hasCooldown("DashCooldown")) {
                                 fail(player, "dash_cooldown");
                              } else {
                                 ServerLevel level = player.serverLevel();
                                 LivingEntity finalTarget = null;
                                 double range = 25.0 + (double)skillLevel * 10.0;
                                 Vec3 eyePos = player.getEyePosition();
                                 Vec3 viewVec = player.getViewVector(1.0F).normalize();
                                 if (this.targetId != null) {
                                    if (level.getEntity(this.targetId) instanceof LivingEntity le
                                       && le != player
                                       && le.isAlive()
                                       && !isBlockedPlayer(data, le)
                                       && player.hasLineOfSight(le)
                                       && isNearCrosshair(eyePos, viewVec, le, range)) {
                                       finalTarget = le;
                                    }
                                 } else {
                                    AABB searchBox = player.getBoundingBox().inflate(range);
                                    List<LivingEntity> candidates = level.getEntitiesOfClass(
                                       LivingEntity.class,
                                       searchBox,
                                       e -> e != player
                                             && e.isAlive()
                                             && player.hasLineOfSight(e)
                                             && !isBlockedPlayer(data, e)
                                             && isNearCrosshair(eyePos, viewVec, e, range)
                                    );
                                    LivingEntity recentHit = getRecentHitTarget(player, level);
                                    if (recentHit != null && candidates.contains(recentHit)) {
                                       finalTarget = recentHit;
                                    } else if (!candidates.isEmpty()) {
                                       finalTarget = candidates.stream().max(Comparator.comparingDouble(InstantTransmissionTapC2S::entityPower)).orElse(null);
                                    }
                                 }

                                 if (finalTarget == null) {
                                    fail(player, "no_target");
                                 } else {
                                    double distance = player.position().distanceTo(finalTarget.position());
                                    int kiCost = DashHandler.getFlyDashKiCost() + ITTeleportHelper.extraKiCostForDistance(distance);
                                    if (!bypassCosts) {
                                       if (data.getResources().getCurrentEnergy() < (float)kiCost) {
                                          fail(player, "no_ki");
                                          return;
                                       }

                                       data.getResources().removeEnergy((float)kiCost);
                                       NetworkHandler.sendToTrackingEntityAndSelf(new ResourceSyncS2C(player), player);
                                    }

                                    float targetYaw = finalTarget.getYRot();
                                    double rad = Math.toRadians((double)targetYaw);
                                    double xOffset = -Math.sin(rad) * -1.5;
                                    double zOffset = Math.cos(rad) * -1.5;
                                    double newX = finalTarget.getX() + xOffset;
                                    double newY = finalTarget.getY();
                                    double newZ = finalTarget.getZ() + zOffset;
                                    player.teleportTo(level, newX, newY, newZ, finalTarget.getYRot(), player.getXRot());
                                    player.playNotifySound((SoundEvent)MainSounds.TP_SHORT.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
                                    if (!bypassCosts) {
                                       int dashCdTicks = ConfigManager.getCombatConfig().getDashCooldownSeconds() * 20;
                                       data.getCooldowns().setCooldown("DashCooldown", dashCdTicks);
                                       player.addEffect(new MobEffectInstance(MainEffects.DASH_CD, dashCdTicks, 0, false, false, true));
                                    }
                                 }
                              }
                           }
                        }
                     );
               }
            }
         );
      ctx.get().setPacketHandled(true);
   }

   private static void fail(ServerPlayer player, String reason) {
      player.displayClientMessage(Component.translatable("gui.dragonminez.transmission.fail." + reason), true);
   }

   private static boolean isNearCrosshair(Vec3 eyePos, Vec3 viewVec, LivingEntity entity, double range) {
      Vec3 center = entity.position().add(0.0, (double)entity.getBbHeight() * 0.5, 0.0);
      Vec3 toEntity = center.subtract(eyePos);
      double dist = toEntity.length();
      if (dist > range) {
         return false;
      } else {
         return dist < 1.0E-4 ? true : toEntity.scale(1.0 / dist).dot(viewVec) >= 0.93;
      }
   }

   private static LivingEntity getRecentHitTarget(ServerPlayer player, ServerLevel level) {
      long lastTime = player.getPersistentData().getLong("dmz_last_hit_target_time");
      if (lastTime > 0L && System.currentTimeMillis() - lastTime <= 6000L) {
         int id = player.getPersistentData().getInt("dmz_last_hit_target_id");
         if (level.getEntity(id) instanceof LivingEntity le && le.isAlive()) {
            return le;
         }

         return null;
      } else {
         return null;
      }
   }

   private static double entityPower(LivingEntity entity) {
      if (entity instanceof ServerPlayer sp) {
         StatsData data = StatsProvider.get(StatsCapability.INSTANCE, sp).orElse(null);
         if (data != null) {
            return data.getBattlePowerExact();
         }
      }

      return (double)((long)entity.getMaxHealth());
   }

   private static boolean isBlockedPlayer(StatsData requesterData, LivingEntity entity) {
      if (!(entity instanceof ServerPlayer targetPlayer)) {
         return false;
      } else {
         StatsData targetData = StatsProvider.get(StatsCapability.INSTANCE, targetPlayer).orElse(null);
         return targetData != null && TransformationsHelper.isInstantTransmissionBlocked(requesterData, targetData);
      }
   }
}
