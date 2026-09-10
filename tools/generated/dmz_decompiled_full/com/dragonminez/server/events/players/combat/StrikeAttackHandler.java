package com.dragonminez.server.events.players.combat;

import com.dragonminez.Env;
import com.dragonminez.LogUtil;
import com.dragonminez.common.combat.logic.player.TargetHelper;
import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.events.DMZEvent;
import com.dragonminez.common.init.MainDamageTypes;
import com.dragonminez.common.init.MainEntities;
import com.dragonminez.common.init.MainParticles;
import com.dragonminez.common.init.MainSounds;
import com.dragonminez.common.init.entities.ki.KiExplosionVisualEntity;
import com.dragonminez.common.init.entities.ki.KiWaveEntity;
import com.dragonminez.common.init.entities.ki.OzaruFistEntity;
import com.dragonminez.common.init.entities.ki.SPDragonFistEntity;
import com.dragonminez.common.network.NetworkHandler;
import com.dragonminez.common.network.S2C.StatsSyncS2C;
import com.dragonminez.common.network.S2C.TriggerAnimationS2C;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.common.stats.techniques.StrikeAttackData;
import com.dragonminez.common.stats.techniques.TechniqueData;
import com.dragonminez.server.dynamicgrowth.DynamicGrowthService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.commands.arguments.EntityAnchorArgument.Anchor;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.EventBusSubscriber.Bus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.entity.PartEntity;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent.Post;

@EventBusSubscriber(
   modid = "dragonminez",
   bus = Bus.GAME
)
public class StrikeAttackHandler {
   private static final int CONNECT_WINDOW_TICKS = 10;
   private static final double CONNECT_RANGE = 4.0;
   private static final double CONE_RANGE = 6.0;
   private static final double CONE_RANGE_FLY = 12.0;
   private static final double CONE_HALF_ANGLE_COS = 0.5;
   private static final double DASH_BASE_DISTANCE = 4.0;
   private static final double DASH_DISTANCE_SCALE = 0.3;
   private static final double KNOCKBACK_FORCE = 1.8;
   private static final double FINAL_HIT_RATIO = 0.35;
   private static final double IMPACT_DAMAGE_RATIO = 0.2;
   private static final int HIT_INTERVAL_TICKS = 10;
   private static final long RECENT_HIT_WINDOW_MS = 10000L;
   private static final String STRIKE_HIT_ANIM = "base.flyback";
   private static final String STRIKE_KNOCKBACK_ANIM = "base.flyback";
   private static final Map<UUID, StrikeAttackHandler.PendingStrike> PENDING = new HashMap<>();
   private static final Map<UUID, StrikeAttackHandler.ActiveStrike> ACTIVE = new HashMap<>();
   private static final Map<UUID, StrikeAttackHandler.RecentHit> RECENTLY_DAMAGED = new HashMap<>();
   private static final Map<UUID, Integer> STRIKE_ANCHOR_PART = new HashMap<>();
   private static final float[] DD_GOLD = new float[]{1.0F, 0.84F, 0.0F};
   private static final float[] DD_CELESTE = new float[]{0.3F, 0.8F, 1.0F};
   private static final float[] DD_YELLOW = new float[]{1.0F, 0.95F, 0.15F};
   private static final float[] DD_YELLOW_DEEP = new float[]{1.0F, 0.78F, 0.05F};

   public static void requestStrike(ServerPlayer player, int preferredTargetId) {
      if (!player.level().isClientSide) {
         StatsProvider.get(StatsCapability.INSTANCE, player)
            .ifPresent(
               stats -> {
                  if (stats.getStatus().isHasCreatedCharacter()) {
                     if (!stats.getStatus().isStunned()) {
                        if (!PENDING.containsKey(player.getUUID()) && !ACTIVE.containsKey(player.getUUID())) {
                           if (stats.getTechniques().getSelectedTechnique() instanceof StrikeAttackData strike) {
                              if (stats.getSkills().getSkillLevel("kicontrol") > 0
                                 && stats.getResources().getPowerRelease() >= 5
                                 && player.getMainHandItem().isEmpty()) {
                                 String cooldownKey = getTechniqueCooldownKey(strike.getId());
                                 if (!stats.getCooldowns().hasCooldown(cooldownKey)) {
                                    double cost = strike.getCalculatedCost(stats);
                                    if (!((double)stats.getResources().getCurrentEnergy() < cost)) {
                                       stats.getResources().removeEnergy((float)((int)Math.ceil(cost)));
                                       NetworkHandler.sendToTrackingEntityAndSelf(new StatsSyncS2C(player), player);
                                       boolean isFlying = stats.getSkills().isSkillActive("fly");
                                       double coneRange = isFlying ? 12.0 : 6.0;
                                       LivingEntity immediateTarget = findConeTarget(player, coneRange, preferredTargetId);
                                       StrikeAttackHandler.PendingStrike pending = new StrikeAttackHandler.PendingStrike(
                                          player.getUUID(),
                                          immediateTarget != null ? immediateTarget.getUUID() : null,
                                          strike.getId(),
                                          strike.getAnimationId(),
                                          strike.getDurationTicks(),
                                          strike.getActualCooldown(),
                                          cost,
                                          10
                                       );
                                       NeoForge.EVENT_BUS.post(new DMZEvent.StrikeAttackCastEvent(player, stats, strike));
                                       if (immediateTarget != null) {
                                          boolean faceTarget = !"dragon_fist".equals(strike.getId());
                                          PartEntity<?> hitPart = nearestPartInSight(player, coneRange);
                                          if (hitPart != null && hitPart.getParent() == immediateTarget) {
                                             teleportToPartFront(player, hitPart, immediateTarget, faceTarget);
                                          } else {
                                             teleportToTargetFront(player, immediateTarget, faceTarget);
                                          }

                                          player.level()
                                             .playSound(
                                                null,
                                                player.getX(),
                                                player.getY(),
                                                player.getZ(),
                                                (SoundEvent)MainSounds.TP_SHORT.get(),
                                                SoundSource.PLAYERS,
                                                1.0F,
                                                1.0F
                                             );
                                          startStrike(player, immediateTarget, pending);
                                       } else {
                                          dashForward(player, isFlying);
                                          PENDING.put(player.getUUID(), pending);
                                       }
                                    }
                                 }
                              }
                           }
                        }
                     }
                  }
               }
            );
      }
   }

   @SubscribeEvent
   public static void onPlayerTick(Post event) {
      if (!event.getEntity().level().isClientSide) {
         if (event.getEntity() instanceof ServerPlayer player) {
            processPending(player);
            processActive(player);
         }
      }
   }

   @SubscribeEvent
   public static void onPlayerLoggedOut(PlayerLoggedOutEvent event) {
      UUID id = event.getEntity().getUUID();
      PENDING.remove(id);
      STRIKE_ANCHOR_PART.remove(id);
      StrikeAttackHandler.ActiveStrike active = ACTIVE.remove(id);
      if (active != null && event.getEntity() instanceof ServerPlayer attacker) {
         clearVictimStrikeLock(attacker, null, active.targetId());
         stopVictimAnimation(attacker, null, active.targetId());
      }
   }

   @SubscribeEvent
   public static void onPlayerLoggedIn(PlayerLoggedInEvent event) {
      if (event.getEntity() instanceof ServerPlayer player) {
         StatsProvider.get(StatsCapability.INSTANCE, player).ifPresent(stats -> {
            if (stats.getStatus().isStrikeLocked()) {
               stats.getStatus().setStrikeLocked(false);
               NetworkHandler.sendToTrackingEntityAndSelf(new StatsSyncS2C(player), player);
            }
         });
      }
   }

   private static void clearVictimStrikeLock(ServerPlayer player, LivingEntity target, UUID targetId) {
      if (targetId != null && player.getServer() != null) {
         ServerPlayer victim = player.getServer().getPlayerList().getPlayer(targetId);
         if (victim != null) {
            setStrikeLocked(victim, false);
            return;
         }
      }

      if (target instanceof ServerPlayer serverTarget) {
         setStrikeLocked(serverTarget, false);
      }
   }

   private static void stopVictimAnimation(ServerPlayer player, LivingEntity target, UUID targetId) {
      if (targetId != null && player.getServer() != null) {
         ServerPlayer victim = player.getServer().getPlayerList().getPlayer(targetId);
         if (victim != null) {
            NetworkHandler.sendToTrackingEntityAndSelf(
               new TriggerAnimationS2C(victim.getUUID(), TriggerAnimationS2C.AnimationType.KI_ANIMATION_STOP, 0, -1, ""), victim
            );
            return;
         }
      }

      if (target instanceof ServerPlayer serverTarget) {
         NetworkHandler.sendToTrackingEntityAndSelf(
            new TriggerAnimationS2C(serverTarget.getUUID(), TriggerAnimationS2C.AnimationType.KI_ANIMATION_STOP, 0, -1, ""), serverTarget
         );
      }
   }

   private static void processPending(ServerPlayer player) {
      StrikeAttackHandler.PendingStrike pending = PENDING.get(player.getUUID());
      if (pending != null) {
         if (pending.ticksRemaining() <= 0) {
            failPending(player, pending);
         } else {
            LivingEntity target = resolveTargetForPending(player, pending);
            if (target != null) {
               PENDING.remove(player.getUUID());
               startStrike(player, target, pending);
            } else {
               PENDING.put(player.getUUID(), pending.withTicksRemaining(pending.ticksRemaining() - 1));
            }
         }
      }
   }

   private static void processActive(ServerPlayer player) {
      try {
         processActiveInternal(player);
      } catch (Exception var4) {
         LogUtil.error(Env.SERVER, "Error procesando ActiveStrike de " + player.getName().getString() + ", forzando cierre", var4);
         StrikeAttackHandler.ActiveStrike active = ACTIVE.get(player.getUUID());
         if (active != null) {
            LivingEntity target = resolveLiving(player, active.targetId());
            endStrike(player, target, active);
         }
      }
   }

   private static void processActiveInternal(ServerPlayer player) {
      StrikeAttackHandler.ActiveStrike active = ACTIVE.get(player.getUUID());
      if (active != null) {
         LivingEntity target = resolveLiving(player, active.targetId());
         if (target == null || !target.isAlive() || !player.isAlive()) {
            endStrike(player, target, active);
         } else if ("dragon_fist".equals(active.techniqueId())) {
            if (active.ticksElapsed() == 5) {
               SPDragonFistEntity dragonFist = new SPDragonFistEntity(player.level(), player);
               dragonFist.setupDragonFist(player, (float)active.totalDamage(), 1.0F);

               try {
                  dragonFist.setStrikeStun(active.durationTicks() / 2, active.targetId());
               } catch (Exception var21) {
               }
            }

            if (active.ticksElapsed() >= active.durationTicks()) {
               if (!player.level().isClientSide) {
                  try {
                     KiExplosionVisualEntity explosion = new KiExplosionVisualEntity((EntityType<?>)MainEntities.KI_EXPLOSION_VISUAL.get(), player.level());
                     explosion.setPos(target.getX(), target.getY() + 1.0, target.getZ());
                     explosion.setupExplosion(16766720, 16747520, 5.0F);
                     player.level().addFreshEntity(explosion);
                  } catch (Exception var20) {
                  }
               }

               endStrike(player, target, active);
            } else {
               ACTIVE.put(player.getUUID(), active.withTicksElapsed(active.ticksElapsed() + 1));
            }
         } else if ("oozaru_fist".equals(active.techniqueId())) {
            int currentTick = active.ticksElapsed();
            if (currentTick < 10) {
               Vec3 lookDownPos = player.getEyePosition().add(0.0, -10.0, 0.0);
               player.lookAt(Anchor.EYES, lookDownPos);
               player.setXRot(90.0F);
               freezeEntity(player);
               freezeEntity(target);
            } else if (currentTick == 10) {
               Vec3 lookDownPos = player.getEyePosition().add(0.0, -10.0, 0.0);
               player.lookAt(Anchor.EYES, lookDownPos);
               player.setXRot(90.0F);
               KiWaveEntity kamehameha = new KiWaveEntity(player.level(), player);
               kamehameha.setupKiHame(player, (float)active.totalDamage() * 0.2F, 2.0F, 0.5F, 5);
               kamehameha.setFiring(true);
               kamehameha.setMaxLife(15);
               kamehameha.setBlockDestructionEnabled(false);
               player.level().addFreshEntity(kamehameha);
               player.level()
                  .playSound(null, player.getX(), player.getY(), player.getZ(), (SoundEvent)MainSounds.KI_KAME_FIRE.get(), SoundSource.PLAYERS, 2.0F, 1.0F);
            } else if (currentTick > 10 && currentTick < 20) {
               faceStrikeTarget(player, target);
               freezeEntity(target);
            } else if (currentTick == 20) {
               faceStrikeTarget(player, target);
               player.level()
                  .playSound(
                     null, player.getX(), player.getY(), player.getZ(), (SoundEvent)MainSounds.OOZARU_GROWL_PLAYER.get(), SoundSource.PLAYERS, 2.0F, 1.0F
                  );
               OzaruFistEntity ozaruFist = new OzaruFistEntity(player.level(), player);
               ozaruFist.setupOzaruFist(player, (float)active.totalDamage(), 1.0F);

               try {
                  ozaruFist.setStrikeStun(active.durationTicks() / 2, active.targetId());
               } catch (Exception var23) {
               }
            }

            if (currentTick >= active.durationTicks()) {
               if (!player.level().isClientSide) {
                  try {
                     KiExplosionVisualEntity explosion = new KiExplosionVisualEntity((EntityType<?>)MainEntities.KI_EXPLOSION_VISUAL.get(), player.level());
                     explosion.setPos(target.getX(), target.getY() + 1.0, target.getZ());
                     explosion.setupExplosion(16777215, 7602158, 3.0F);
                     player.level().addFreshEntity(explosion);
                  } catch (Exception var22) {
                  }
               }

               endStrike(player, target, active);
            } else {
               ACTIVE.put(player.getUUID(), active.withTicksElapsed(currentTick + 1));
            }
         } else if ("meteor".equals(active.techniqueId())) {
            if (target instanceof ServerPlayer targetPlayer) {
               faceEntity(targetPlayer, player);
            } else {
               faceEntity(target, player);
            }

            player.invulnerableTime = 20;
            Vec3 lookVec = Vec3.directionFromRotation(0.0F, player.getYRot()).normalize();
            double advanceSpeed = 0.25;
            player.setDeltaMovement(lookVec.x * advanceSpeed, player.getDeltaMovement().y, lookVec.z * advanceSpeed);
            player.hurtMarked = true;
            double distance = 1.5;
            double targetX = player.getX() + lookVec.x * distance;
            double targetY = player.getY();
            double targetZ = player.getZ() + lookVec.z * distance;
            target.setPos(targetX, targetY, targetZ);
            target.setDeltaMovement(0.0, target.getDeltaMovement().y, 0.0);
            target.hurtMarked = true;
            int nextTick = active.ticksElapsed() + 1;
            if (nextTick % active.hitIntervalTicks() == 0 && nextTick < active.durationTicks()) {
               applyStrikeDamage(player, target, active.perHitDamage(), active.techniqueId(), false);
               player.level()
                  .playSound(
                     null,
                     target.getX(),
                     target.getY(),
                     target.getZ(),
                     (SoundEvent)MainSounds.GOLPE1.get(),
                     SoundSource.PLAYERS,
                     1.0F,
                     0.8F + player.getRandom().nextFloat() * 0.4F
                  );
            }

            if (nextTick < active.durationTicks()) {
               ACTIVE.put(player.getUUID(), active.withTicksElapsed(nextTick));
            } else {
               applyStrikeDamage(player, target, active.finalDamage(), active.techniqueId(), true);
               grantKillXpIfNeeded(player, target, active.techniqueId());
               player.level()
                  .playSound(null, target.getX(), target.getY(), target.getZ(), (SoundEvent)MainSounds.CRITICO1.get(), SoundSource.PLAYERS, 2.0F, 1.0F);
               Vec3 pushDir = player.getLookAngle().normalize();
               KnockbackHelper.apply(target, new Vec3(pushDir.x * 2.5, 0.4, pushDir.z * 2.5));
               playStrikeKnockbackAnimation(target);
               MomentumImpactHandler.CollisionImpactType impactType = !target.onGround() && !(pushDir.y < -0.5)
                  ? MomentumImpactHandler.CollisionImpactType.WALL
                  : MomentumImpactHandler.CollisionImpactType.GROUND;
               MomentumImpactHandler.registerCollisionImpact(target, impactType, (float)(active.totalDamage() * 0.2), pushDir);
               endStrike(player, target, active);
            }
         } else if ("super_god_fist".equals(active.techniqueId())) {
            int nextTickx = active.ticksElapsed() + 1;
            if (nextTickx < 14) {
               faceStrikeTarget(player, target);
               if (target instanceof ServerPlayer targetPlayer) {
                  faceEntity(targetPlayer, player);
               } else {
                  faceEntity(target, player);
               }
            }

            player.invulnerableTime = 20;
            if (nextTickx <= 12) {
               target.invulnerableTime = 20;
               double dist = (double)player.distanceTo(target);
               if (dist > 1.5) {
                  Vec3 dir = target.position().subtract(player.position()).normalize();
                  double dashSpeed = 1.5;
                  player.setDeltaMovement(dir.x * dashSpeed, player.getDeltaMovement().y, dir.z * dashSpeed);
                  player.hurtMarked = true;
               } else {
                  freezeEntity(player);
               }

               freezeEntity(target);
            } else if (nextTickx == 13) {
               target.invulnerableTime = 20;
               freezeEntity(player);
               freezeEntity(target);
            } else if (nextTickx == 14) {
               target.invulnerableTime = 0;
               applyStrikeDamage(player, target, active.totalDamage(), active.techniqueId(), true);
               grantKillXpIfNeeded(player, target, active.techniqueId());
               player.level()
                  .playSound(null, target.getX(), target.getY(), target.getZ(), (SoundEvent)MainSounds.CRITICO2.get(), SoundSource.PLAYERS, 2.5F, 0.7F);
               if (!player.level().isClientSide) {
                  try {
                     KiExplosionVisualEntity impact = new KiExplosionVisualEntity((EntityType<?>)MainEntities.KI_EXPLOSION_VISUAL.get(), player.level());
                     impact.setPos(target.getX(), target.getY() + (double)target.getBbHeight() * 0.5, target.getZ());
                     impact.setupExplosion(16777215, 16106791, 0.5F);
                     player.level().addFreshEntity(impact);
                  } catch (Exception var24) {
                  }

                  spawnSuperGodFistImpactParticles(player.serverLevel(), target);
               }

               Vec3 pushDir = player.getLookAngle().normalize();
               double knockbackPower = 4.0;
               KnockbackHelper.apply(target, new Vec3(pushDir.x * knockbackPower, 0.6, pushDir.z * knockbackPower));
               playStrikeKnockbackAnimation(target);
               MomentumImpactHandler.CollisionImpactType impactType = !target.onGround() && !(pushDir.y < -0.5)
                  ? MomentumImpactHandler.CollisionImpactType.WALL
                  : MomentumImpactHandler.CollisionImpactType.GROUND;
               MomentumImpactHandler.registerCollisionImpact(target, impactType, (float)(active.totalDamage() * 0.2), pushDir);
               freezeEntity(player);
            } else if (nextTickx < 25) {
               freezeEntity(player);
            }

            if (nextTickx >= 35) {
               endStrike(player, target, active);
            } else {
               ACTIVE.put(player.getUUID(), active.withTicksElapsed(nextTickx));
            }
         } else if ("deadly_dance".equals(active.techniqueId()) || "deadly_dance_vegetto".equals(active.techniqueId())) {
            boolean vegetto = "deadly_dance_vegetto".equals(active.techniqueId());
            if (target instanceof ServerPlayer targetPlayer) {
               faceEntity(targetPlayer, player);
            } else {
               faceEntity(target, player);
            }

            player.invulnerableTime = 20;
            Vec3 lookVecx = Vec3.directionFromRotation(0.0F, player.getYRot()).normalize();
            double distancex = 1.5;
            double targetXx = player.getX() + lookVecx.x * distancex;
            double targetYx = player.getY();
            double targetZx = player.getZ() + lookVecx.z * distancex;
            boolean blocked = player.horizontalCollision || !canOccupy(target, targetXx, targetYx, targetZx);
            if (!blocked) {
               double advanceSpeedx = 0.25;
               player.setDeltaMovement(lookVecx.x * advanceSpeedx, player.getDeltaMovement().y, lookVecx.z * advanceSpeedx);
               player.hurtMarked = true;
               target.setPos(targetXx, targetYx, targetZx);
               target.setDeltaMovement(0.0, target.getDeltaMovement().y, 0.0);
               target.hurtMarked = true;
            } else {
               player.setDeltaMovement(0.0, player.getDeltaMovement().y, 0.0);
               player.hurtMarked = true;
               target.setDeltaMovement(0.0, target.getDeltaMovement().y, 0.0);
               target.hurtMarked = true;
            }

            int nextTickxx = active.ticksElapsed() + 1;
            if (nextTickxx % active.hitIntervalTicks() == 0 && nextTickxx < 30) {
               applyStrikeDamage(player, target, active.perHitDamage(), active.techniqueId(), false);
               if (!player.level().isClientSide) {
                  spawnDeadlyDanceHitParticles(player.serverLevel(), target, vegetto);
               }

               player.level()
                  .playSound(
                     null,
                     target.getX(),
                     target.getY(),
                     target.getZ(),
                     (SoundEvent)MainSounds.GOLPE1.get(),
                     SoundSource.PLAYERS,
                     1.0F,
                     0.8F + player.getRandom().nextFloat() * 0.4F
                  );
            }

            if (nextTickxx >= 30) {
               applyStrikeDamage(player, target, active.finalDamage(), active.techniqueId(), true);
               grantKillXpIfNeeded(player, target, active.techniqueId());
               if (!player.level().isClientSide) {
                  spawnDeadlyDanceFinalParticles(player.serverLevel(), target, vegetto);
               }

               player.level()
                  .playSound(null, target.getX(), target.getY(), target.getZ(), (SoundEvent)MainSounds.CRITICO2.get(), SoundSource.PLAYERS, 2.0F, 1.0F);
               Vec3 pushDir = player.getLookAngle().normalize();
               double upwardForce = 1.5;
               double forwardForce = 0.5;
               KnockbackHelper.apply(target, new Vec3(pushDir.x * forwardForce, upwardForce, pushDir.z * forwardForce));
               playStrikeKnockbackAnimation(target);
               MomentumImpactHandler.registerCollisionImpact(
                  target, MomentumImpactHandler.CollisionImpactType.GROUND, (float)(active.totalDamage() * 0.2), new Vec3(0.0, 1.0, 0.0)
               );
               endStrike(player, target, active);
            } else {
               ACTIVE.put(player.getUUID(), active.withTicksElapsed(nextTickxx));
            }
         } else if ("kaioken_attack".equals(active.techniqueId())) {
            StatsProvider.get(StatsCapability.INSTANCE, player).ifPresent(stats -> {
               stats.getStatus().setAuraActive(true);
               if (active.ticksElapsed() % 10 == 0) {
                  NetworkHandler.sendToTrackingEntityAndSelf(new StatsSyncS2C(player), player);
               }
            });
            int nextTickxxx = active.ticksElapsed() + 1;
            if (nextTickxxx < 20) {
               faceStrikeTarget(player, target);
               if (target instanceof ServerPlayer targetPlayer) {
                  faceEntity(targetPlayer, player);
               } else {
                  faceEntity(target, player);
               }
            }

            player.invulnerableTime = 20;
            if (nextTickxxx < 10) {
               double dist = (double)player.distanceTo(target);
               if (dist > 1.5) {
                  Vec3 dir = target.position().subtract(player.position()).normalize();
                  player.setDeltaMovement(dir.scale(1.5));
                  player.hurtMarked = true;
               } else {
                  freezeEntity(player);
               }

               freezeEntity(target);
            } else if (nextTickxxx == 10) {
               applyStrikeDamage(player, target, active.perHitDamage(), active.techniqueId(), false);
               player.level()
                  .playSound(null, target.getX(), target.getY(), target.getZ(), (SoundEvent)MainSounds.GOLPE1.get(), SoundSource.PLAYERS, 1.5F, 1.0F);
               Vec3 pushDir = player.getLookAngle().normalize();
               KnockbackHelper.apply(target, new Vec3(pushDir.x * 1.5, 0.4, pushDir.z * 1.5));
               freezeEntity(player);
            } else if (nextTickxxx < 15) {
               Vec3 dir = target.position().subtract(player.position()).normalize();
               player.setDeltaMovement(dir.scale(2.5));
               player.hurtMarked = true;
            } else if (nextTickxxx == 15) {
               applyStrikeDamage(player, target, active.perHitDamage(), active.techniqueId(), false);
               player.level()
                  .playSound(null, target.getX(), target.getY(), target.getZ(), (SoundEvent)MainSounds.CRITICO2.get(), SoundSource.PLAYERS, 1.5F, 1.2F);
               freezeEntity(target);
               freezeEntity(player);
            } else if (nextTickxxx < 20) {
               freezeEntity(target);
               freezeEntity(player);
            } else if (nextTickxxx == 20) {
               applyStrikeDamage(player, target, active.perHitDamage(), active.techniqueId(), false);
               player.level()
                  .playSound(null, target.getX(), target.getY(), target.getZ(), (SoundEvent)MainSounds.CRITICO2.get(), SoundSource.PLAYERS, 2.0F, 0.8F);
               Vec3 pushDir = player.getLookAngle().normalize();
               KnockbackHelper.apply(target, new Vec3(pushDir.x * 3.5, 0.2, pushDir.z * 3.5));
               playStrikeKnockbackAnimation(target);
               freezeEntity(player);
            } else if (nextTickxxx < 34) {
               freezeEntity(player);
               if (nextTickxxx == 21) {
                  player.level()
                     .playSound(
                        null, player.getX(), player.getY(), player.getZ(), (SoundEvent)MainSounds.KI_EXPLOSION_CHARGE.get(), SoundSource.PLAYERS, 1.0F, 1.0F
                     );
               }
            } else if (nextTickxxx == 34) {
               freezeEntity(player);
               applyStrikeDamage(player, target, active.finalDamage() * 0.1, active.techniqueId(), false);
               KiWaveEntity kamehameha = new KiWaveEntity(player.level(), player);
               kamehameha.setupKiHame(player, (float)active.finalDamage() * 0.9F, 2.0F, 1.0F, 10);
               kamehameha.setFiring(true);
               kamehameha.setMaxLife(40);
               player.level().addFreshEntity(kamehameha);
               player.level()
                  .playSound(null, player.getX(), player.getY(), player.getZ(), (SoundEvent)MainSounds.KI_KAME_FIRE.get(), SoundSource.PLAYERS, 2.0F, 1.0F);
            } else if (nextTickxxx < 50) {
               freezeEntity(player);
            } else if (nextTickxxx >= 50) {
               grantKillXpIfNeeded(player, target, active.techniqueId());
               StatsProvider.get(StatsCapability.INSTANCE, player).ifPresent(stats -> {
                  stats.getStatus().setAuraActive(false);
                  NetworkHandler.sendToTrackingEntityAndSelf(new StatsSyncS2C(player), player);
               });
               endStrike(player, target, active);
               return;
            }

            ACTIVE.put(player.getUUID(), active.withTicksElapsed(nextTickxxx));
         } else if ("wolf_fang".equals(active.techniqueId())) {
            freezeEntity(player);
            freezeEntity(target);
            faceStrikeTarget(player, target);
            if (target instanceof ServerPlayer targetPlayer) {
               faceEntity(targetPlayer, player);
            }

            player.invulnerableTime = 20;
            int wolfTick = active.ticksElapsed() + 1;
            int wolfDuration = active.durationTicks();
            if (wolfTick % active.hitIntervalTicks() == 0 && wolfTick < wolfDuration) {
               applyStrikeDamage(player, target, active.perHitDamage(), active.techniqueId(), false);
            }

            if (wolfTick < wolfDuration - 3 && wolfTick % 4 == 0) {
               spawnWolfFangJab(player, target, wolfTick);
            }

            if (wolfTick >= wolfDuration) {
               applyStrikeDamage(player, target, active.finalDamage(), active.techniqueId(), true);
               grantKillXpIfNeeded(player, target, active.techniqueId());
               double sx = target.getX();
               double sy = target.getY() + (double)target.getBbHeight() * 0.5;
               double sz = target.getZ();
               player.level().playSound(null, sx, sy, sz, (SoundEvent)MainSounds.CRITICO2.get(), SoundSource.PLAYERS, 2.0F, 0.7F);
               player.level().playSound(null, sx, sy, sz, (SoundEvent)MainSounds.KI_EXPLOSION_IMPACT.get(), SoundSource.PLAYERS, 2.5F, 1.0F);
               player.level().playSound(null, sx, sy, sz, (SoundEvent)MainSounds.OOZARU_GROWL_PLAYER.get(), SoundSource.PLAYERS, 3.0F, 1.15F);
               if (!player.level().isClientSide) {
                  spawnWolfFangFinalParticles(player.serverLevel(), target);
               }

               applyKnockback(player, target, active.totalDamage());
               endStrike(player, target, active);
            } else {
               ACTIVE.put(player.getUUID(), active.withTicksElapsed(wolfTick));
            }
         } else {
            freezeEntity(player);
            freezeEntity(target);
            faceStrikeTarget(player, target);
            if (target instanceof ServerPlayer targetPlayer) {
               faceEntity(targetPlayer, player);
            }

            int nextTickxxxx = active.ticksElapsed() + 1;
            if (nextTickxxxx % active.hitIntervalTicks() == 0 && nextTickxxxx < active.durationTicks()) {
               applyStrikeDamage(player, target, active.perHitDamage(), active.techniqueId(), false);
            }

            if (nextTickxxxx >= active.durationTicks()) {
               applyStrikeDamage(player, target, active.finalDamage(), active.techniqueId(), true);
               grantKillXpIfNeeded(player, target, active.techniqueId());
               applyKnockback(player, target, active.totalDamage());
               endStrike(player, target, active);
            } else {
               ACTIVE.put(player.getUUID(), active.withTicksElapsed(nextTickxxxx));
            }
         }
      }
   }

   private static void startStrike(ServerPlayer player, LivingEntity target, StrikeAttackHandler.PendingStrike pending) {
      StatsProvider.get(StatsCapability.INSTANCE, player)
         .ifPresent(
            stats -> {
               TechniqueData tech = stats.getTechniques().getUnlockedTechniques().get(pending.techniqueId());
               if (tech instanceof StrikeAttackData strike) {
                  double totalDamage = stats.getStrikeDamage()
                     * (double)strike.getDamageMultiplier()
                     * Math.max(0.0, ConfigManager.getTechniqueConfig().getStrikeConfig(strike.getId()).getDamageMultiplier());
                  DMZEvent.DamageModifyEvent modifyEvent = new DMZEvent.DamageModifyEvent(player, target, totalDamage, 0.0, DMZEvent.DamageSourceType.STRIKE);
                  totalDamage = ((DMZEvent.DamageModifyEvent)NeoForge.EVENT_BUS.post(modifyEvent)).isCanceled() ? 0.0 : Math.max(0.0, modifyEvent.getAmount());
                  int durationTicks = Math.max(20, pending.durationTicks());
                  int hitCount = Math.max(1, (int)Math.ceil((double)durationTicks / 10.0));
                  double perHitDamage = totalDamage * 0.65 / (double)hitCount;
                  double finalDamage = totalDamage * 0.35;
                  StrikeAttackHandler.ActiveStrike active = new StrikeAttackHandler.ActiveStrike(
                     player.getUUID(),
                     target.getUUID(),
                     pending.techniqueId(),
                     pending.animationId(),
                     durationTicks,
                     pending.cooldownTicks(),
                     totalDamage,
                     perHitDamage,
                     finalDamage,
                     10,
                     0
                  );
                  ACTIVE.put(player.getUUID(), active);
                  player.invulnerableTime = 20;
                  NeoForge.EVENT_BUS.post(new DMZEvent.StrikeAttackFireEvent(player, stats, strike, target));
                  applyStrikeDamage(player, target, perHitDamage, pending.techniqueId(), false);
                  setStrikeLocked(player, true);
                  setStrikeLocked(target, true);
                  PartEntity<?> anchorPart = nearestPartInSight(player, 12.0);
                  if (anchorPart != null && anchorPart.getParent() == target) {
                     STRIKE_ANCHOR_PART.put(player.getUUID(), anchorPart.getId());
                  } else {
                     STRIKE_ANCHOR_PART.remove(player.getUUID());
                  }

                  if (!"dragon_fist".equals(pending.techniqueId())) {
                     faceStrikeTarget(player, target);
                  }

                  if (target instanceof ServerPlayer targetPlayer) {
                     faceEntity(targetPlayer, player);
                  }

                  playStrikeAnimation(player, pending.animationId());
               } else {
                  failPending(player, pending);
               }
            }
         );
   }

   private static void endStrike(ServerPlayer player, LivingEntity target, StrikeAttackHandler.ActiveStrike active) {
      ACTIVE.remove(player.getUUID());
      STRIKE_ANCHOR_PART.remove(player.getUUID());
      setStrikeLocked(player, false);
      clearVictimStrikeLock(player, target, active.targetId());
      stopStrikeAnimation(player);
      stopVictimAnimation(player, target, active.targetId());
      StatsProvider.get(StatsCapability.INSTANCE, player).ifPresent(stats -> {
         String cooldownKey = getTechniqueCooldownKey(active.techniqueId());
         stats.getCooldowns().setCooldown(cooldownKey, active.cooldownTicks());
         NetworkHandler.sendToTrackingEntityAndSelf(new StatsSyncS2C(player), player);
      });
   }

   private static void failPending(ServerPlayer player, StrikeAttackHandler.PendingStrike pending) {
      PENDING.remove(player.getUUID());
      STRIKE_ANCHOR_PART.remove(player.getUUID());
      StatsProvider.get(StatsCapability.INSTANCE, player).ifPresent(stats -> {
         String cooldownKey = getTechniqueCooldownKey(pending.techniqueId());
         int halfCooldown = Math.max(1, pending.cooldownTicks() / 2);
         stats.getCooldowns().setCooldown(cooldownKey, halfCooldown);
         stats.getResources().addEnergy((float)((int)Math.ceil(pending.energyCost() * 0.4)));
         NetworkHandler.sendToTrackingEntityAndSelf(new StatsSyncS2C(player), player);
      });
   }

   private static LivingEntity resolvePreferredTarget(ServerPlayer player, int targetId) {
      if (targetId <= 0) {
         return null;
      } else if (player.level().getEntity(targetId) instanceof LivingEntity living) {
         if (!living.isAlive()) {
            return null;
         } else if ((double)player.distanceTo(living) > 4.0) {
            return null;
         } else if (!player.hasLineOfSight(living)) {
            return null;
         } else {
            return !TargetHelper.canAttack(player, living, 4.0) ? null : living;
         }
      } else {
         return null;
      }
   }

   private static LivingEntity resolveTargetForPending(ServerPlayer player, StrikeAttackHandler.PendingStrike pending) {
      LivingEntity preferred = pending.preferredTargetId() != null ? resolveLiving(player, pending.preferredTargetId()) : null;
      return preferred != null
            && (double)player.distanceTo(preferred) <= 4.0
            && player.hasLineOfSight(preferred)
            && TargetHelper.canAttack(player, preferred, 4.0)
         ? preferred
         : findTargetInFront(player, 4.0).orElse(null);
   }

   private static LivingEntity resolveLiving(ServerPlayer player, UUID id) {
      return id == null
         ? null
         : player.level()
            .getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(64.0))
            .stream()
            .filter(e -> e.getUUID().equals(id))
            .findFirst()
            .orElse(null);
   }

   private static Optional<LivingEntity> findTargetInFront(ServerPlayer player, double range) {
      Vec3 eyePos = player.getEyePosition();
      Vec3 viewVec = player.getViewVector(1.0F);
      Vec3 endPos = eyePos.add(viewVec.scale(range));
      AABB searchBox = player.getBoundingBox().expandTowards(viewVec.scale(range)).inflate(1.0);
      List<LivingEntity> list = player.level()
         .getEntitiesOfClass(LivingEntity.class, searchBox, ex -> ex != player && ex.isAlive() && ex.isPickable() && TargetHelper.canAttack(player, ex, range));
      LivingEntity closest = null;
      double closestDist = range * range;

      for (LivingEntity e : list) {
         AABB axisalignedbb = e.getBoundingBox().inflate((double)e.getPickRadius());
         Optional<Vec3> hit = axisalignedbb.clip(eyePos, endPos);
         if (!e.isInvisible() && !e.isInvisibleTo(player) && player.hasLineOfSight(e)) {
            if (axisalignedbb.contains(eyePos)) {
               if (closestDist >= 0.0) {
                  closest = e;
                  closestDist = 0.0;
               }
            } else if (hit.isPresent()) {
               double dist = eyePos.distanceToSqr(hit.get());
               if (dist < closestDist) {
                  closest = e;
                  closestDist = dist;
               }
            }
         }
      }

      if (closest == null) {
         closest = nearestPartParentInSight(player, range);
      }

      return Optional.ofNullable(closest);
   }

   private static LivingEntity nearestPartParentInSight(ServerPlayer player, double range) {
      PartEntity<?> part = nearestPartInSight(player, range);
      return part != null && part.getParent() instanceof LivingEntity parent ? parent : null;
   }

   private static PartEntity<?> nearestPartInSight(ServerPlayer player, double range) {
      if (player.level() instanceof ServerLevel level) {
         Vec3 eyePos = player.getEyePosition();
         Vec3 endPos = eyePos.add(player.getViewVector(1.0F).scale(range));
         PartEntity best = null;
         double bestDist = range * range;

         for (PartEntity<?> part : level.getPartEntities()) {
            Entity box = part.getParent();
            if (box instanceof LivingEntity) {
               LivingEntity parent = (LivingEntity)box;
               if (parent.isAlive()
                  && TargetHelper.canAttack(player, parent, range)
                  && !((double)player.distanceTo(part) > range + 8.0)
                  && player.hasLineOfSight(part)) {
                  AABB boxx = part.getBoundingBox().inflate((double)part.getPickRadius());
                  if (boxx.contains(eyePos)) {
                     return part;
                  }

                  Optional<Vec3> hit = boxx.clip(eyePos, endPos);
                  if (hit.isPresent()) {
                     double dist = eyePos.distanceToSqr(hit.get());
                     if (dist < bestDist) {
                        best = part;
                        bestDist = dist;
                     }
                  }
               }
            }
         }

         return best;
      } else {
         return null;
      }
   }

   private static void teleportToPartFront(ServerPlayer player, PartEntity<?> part, LivingEntity parent, boolean faceTarget) {
      Vec3 center = part.getBoundingBox().getCenter();
      Vec3 look = parent.getLookAngle();
      if (look.horizontalDistanceSqr() < 1.0E-6) {
         look = player.getLookAngle();
      }

      double distance = 1.3 + (double)part.getBbWidth() * 0.5;
      Vec3 teleportPos = center.subtract(look.scale(distance));
      player.teleportTo(teleportPos.x, center.y - (double)player.getEyeHeight(), teleportPos.z);
      if (faceTarget) {
         player.lookAt(Anchor.EYES, center);
      }
   }

   private static void dashForward(ServerPlayer player, boolean isFlying) {
      double speedMultiplier = player.getAttributeValue(Attributes.MOVEMENT_SPEED) / 0.1;
      double distance = 4.0 * speedMultiplier;
      if (isFlying) {
         distance *= 3.0;
      }

      Vec3 direction = Vec3.directionFromRotation(0.0F, player.getYRot()).normalize();
      Vec3 velocity = direction.scale(distance * 0.3);
      double yVel = player.onGround() ? 0.35 : 0.2;
      player.setDeltaMovement(player.getDeltaMovement().add(velocity.x, yVel, velocity.z));
      player.hurtMarked = true;
   }

   private static LivingEntity findConeTarget(ServerPlayer player, double range, int preferredTargetId) {
      if (preferredTargetId > 0) {
         LivingEntity pref = TargetHelper.resolveHittable(TargetHelper.getEntityOrPart(player.level(), preferredTargetId)) instanceof LivingEntity l ? l : null;
         if (pref != null
            && pref.isAlive()
            && (double)player.distanceTo(pref) <= range
            && isInFrontCone(player, pref)
            && player.hasLineOfSight(pref)
            && TargetHelper.canAttack(player, pref, range)) {
            return pref;
         }
      }

      AABB searchBox = player.getBoundingBox().inflate(range);
      List<LivingEntity> candidates = new ArrayList<>(
         player.level()
            .getEntitiesOfClass(
               LivingEntity.class,
               searchBox,
               ex -> ex != player
                     && ex.isAlive()
                     && ex.isPickable()
                     && TargetHelper.canAttack(player, ex, range)
                     && (double)player.distanceTo(ex) <= range
                     && isInFrontCone(player, ex)
                     && player.hasLineOfSight(ex)
            )
      );
      if (candidates.isEmpty()) {
         return nearestPartParentInSight(player, range);
      } else if (candidates.size() == 1) {
         return candidates.get(0);
      } else {
         StrikeAttackHandler.RecentHit recent = RECENTLY_DAMAGED.get(player.getUUID());
         if (recent != null && System.currentTimeMillis() - recent.timestamp() <= 10000L) {
            for (LivingEntity e : candidates) {
               if (e.getUUID().equals(recent.targetId())) {
                  return e;
               }
            }
         }

         Vec3 eyePos = player.getEyePosition();
         Vec3 viewVec = player.getViewVector(1.0F);
         Vec3 endPos = eyePos.add(viewVec.scale(range));
         LivingEntity best = null;
         double bestDist = Double.MAX_VALUE;

         for (LivingEntity ex : candidates) {
            AABB bb = ex.getBoundingBox().inflate((double)ex.getPickRadius());
            Optional<Vec3> hit = bb.clip(eyePos, endPos);
            double dist;
            if (bb.contains(eyePos)) {
               dist = 0.0;
            } else if (hit.isPresent()) {
               dist = eyePos.distanceToSqr(hit.get());
            } else {
               dist = eyePos.distanceToSqr(ex.getEyePosition());
            }

            if (dist < bestDist) {
               best = ex;
               bestDist = dist;
            }
         }

         return best;
      }
   }

   private static boolean isInFrontCone(ServerPlayer player, LivingEntity target) {
      Vec3 look = player.getLookAngle();
      Vec3 toTarget = target.getEyePosition().subtract(player.getEyePosition()).normalize();
      return look.dot(toTarget) >= 0.5;
   }

   private static void teleportToTargetFront(ServerPlayer player, LivingEntity target, boolean faceTarget) {
      Vec3 targetPos = target.position();
      Vec3 targetLook = target.getLookAngle();
      Vec3 teleportPos = targetPos.subtract(targetLook.scale(1.3));
      player.teleportTo(teleportPos.x, targetPos.y, teleportPos.z);
      if (faceTarget) {
         player.lookAt(Anchor.EYES, target.getEyePosition());
      }
   }

   private static void applyStrikeDamage(ServerPlayer player, LivingEntity target, double damage, String techniqueId, boolean isFinalHit) {
      if (!(damage <= 0.0)) {
         if (!isFinalHit && (double)target.getHealth() - damage <= 1.0) {
            damage = (double)Math.max(0.01F, target.getHealth() - 1.0F);
         }

         playStrikeHitAnimation(target);
         target.hurt(MainDamageTypes.strikeAttack(player.level(), player, techniqueId), (float)damage);
         RECENTLY_DAMAGED.put(player.getUUID(), new StrikeAttackHandler.RecentHit(target.getUUID(), System.currentTimeMillis()));
         double finalDamage = damage;
         StatsProvider.get(StatsCapability.INSTANCE, player).ifPresent(stats -> {
            TechniqueData tech = stats.getTechniques().getUnlockedTechniques().get(techniqueId);
            if (tech instanceof StrikeAttackData strike) {
               int xpGain = strike.getXpGainPerHit();
               if (xpGain > 0) {
                  stats.getTechniques().addExperienceToTechnique(techniqueId, xpGain);
               }
            }

            DynamicGrowthService.markCombat(stats);
            DynamicGrowthService.awardStrike(player, stats, target, finalDamage);
         });
      }
   }

   private static void grantKillXpIfNeeded(ServerPlayer player, LivingEntity target, String techniqueId) {
      if (target != null && !target.isAlive()) {
         StatsProvider.get(StatsCapability.INSTANCE, player).ifPresent(stats -> {
            TechniqueData tech = stats.getTechniques().getUnlockedTechniques().get(techniqueId);
            if (tech instanceof StrikeAttackData strike) {
               int xpGain = strike.getXpGainPerKill();
               if (xpGain > 0) {
                  stats.getTechniques().addExperienceToTechnique(techniqueId, xpGain);
               }
            }
         });
      }
   }

   private static void applyKnockback(ServerPlayer player, LivingEntity target, double totalDamage) {
      Vec3 dir = target.position().subtract(player.position()).normalize();
      if (dir.lengthSqr() < 1.0E-6) {
         dir = player.getLookAngle();
      }

      KnockbackHelper.apply(target, dir.scale(1.8));
      playStrikeKnockbackAnimation(target);
      MomentumImpactHandler.CollisionImpactType impactType = !target.onGround() && !(dir.y < -0.5)
         ? MomentumImpactHandler.CollisionImpactType.WALL
         : MomentumImpactHandler.CollisionImpactType.GROUND;
      MomentumImpactHandler.registerCollisionImpact(target, impactType, (float)(totalDamage * 0.2), dir);
   }

   private static void spawnSuperGodFistImpactParticles(ServerLevel level, LivingEntity target) {
      double x = target.getX();
      double y = target.getY() + (double)target.getBbHeight() * 0.5;
      double z = target.getZ();
      level.sendParticles((SimpleParticleType)MainParticles.PUNCH_PARTICLE.get(), x, y, z, 0, 1.0, 1.0, 1.0, 1.0);

      for (int i = 0; i < 8; i++) {
         double ox = (level.random.nextDouble() - 0.5) * 0.8;
         double oy = (level.random.nextDouble() - 0.5) * 0.8;
         double oz = (level.random.nextDouble() - 0.5) * 0.8;
         level.sendParticles((SimpleParticleType)MainParticles.SPARKS.get(), x + ox, y + oy, z + oz, 0, 0.96, 0.77, 0.15, 1.0);
      }

      level.sendParticles(ParticleTypes.CRIT, x, y, z, 18, 0.4, 0.4, 0.4, 0.6);
      level.sendParticles(ParticleTypes.EXPLOSION, x, y, z, 2, 0.15, 0.15, 0.15, 0.0);
   }

   private static boolean canOccupy(LivingEntity entity, double x, double y, double z) {
      AABB moved = entity.getBoundingBox().move(x - entity.getX(), y - entity.getY(), z - entity.getZ());
      return entity.level().noCollision(entity, moved);
   }

   private static float[] deadlyDanceColor(boolean vegetto, int i) {
      if (vegetto) {
         return i % 2 == 0 ? DD_GOLD : DD_CELESTE;
      } else {
         return i % 2 == 0 ? DD_YELLOW : DD_YELLOW_DEEP;
      }
   }

   private static void spawnDeadlyDanceHitParticles(ServerLevel level, LivingEntity target, boolean vegetto) {
      double x = target.getX();
      double y = target.getY() + (double)target.getBbHeight() * 0.6;
      double z = target.getZ();
      float[] main = deadlyDanceColor(vegetto, 0);
      level.sendParticles((SimpleParticleType)MainParticles.PUNCH_PARTICLE.get(), x, y, z, 0, (double)main[0], (double)main[1], (double)main[2], 1.0);
      int sparkCount = vegetto ? 2 : 1;

      for (int i = 0; i < sparkCount; i++) {
         float[] c = deadlyDanceColor(vegetto, i);
         double ox = (level.random.nextDouble() - 0.5) * 0.9;
         double oy = (level.random.nextDouble() - 0.5) * 0.9;
         double oz = (level.random.nextDouble() - 0.5) * 0.9;
         level.sendParticles((SimpleParticleType)MainParticles.SPARKS.get(), x + ox, y + oy, z + oz, 0, (double)c[0], (double)c[1], (double)c[2], 1.0);
      }

      if (vegetto) {
         level.sendParticles(ParticleTypes.CRIT, x, y, z, 2, 0.3, 0.3, 0.3, 0.6);
         level.sendParticles(ParticleTypes.ENCHANTED_HIT, x, y, z, 4, 0.3, 0.3, 0.3, 0.4);
      }
   }

   private static void spawnDeadlyDanceFinalParticles(ServerLevel level, LivingEntity target, boolean vegetto) {
      double x = target.getX();
      double y = target.getY() + (double)target.getBbHeight() * 0.5;
      double z = target.getZ();
      float[] main = deadlyDanceColor(vegetto, 0);
      level.sendParticles((SimpleParticleType)MainParticles.PUNCH_PARTICLE.get(), x, y, z, 0, (double)main[0], (double)main[1], (double)main[2], 1.0);
      if (!vegetto) {
         for (int i = 0; i < 3; i++) {
            double dirX = level.random.nextDouble() - 0.5;
            double dirY = level.random.nextDouble() - 0.5;
            double dirZ = level.random.nextDouble() - 0.5;
            double len = Math.sqrt(dirX * dirX + dirY * dirY + dirZ * dirZ);
            if (!(len < 1.0E-4)) {
               float[] c = deadlyDanceColor(false, i);
               double radius = 0.5 + level.random.nextDouble() * 1.2;
               double px = x + dirX / len * radius;
               double py = y + dirY / len * radius;
               double pz = z + dirZ / len * radius;
               level.sendParticles((SimpleParticleType)MainParticles.SPARKS.get(), px, py, pz, 0, (double)c[0], (double)c[1], (double)c[2], 1.0);
            }
         }

         level.sendParticles(ParticleTypes.CRIT, x, y, z, 4, 0.4, 0.4, 0.4, 0.5);
      } else {
         for (int ix = 0; ix < 5; ix++) {
            double dirX = level.random.nextDouble() - 0.5;
            double dirY = level.random.nextDouble() - 0.5;
            double dirZ = level.random.nextDouble() - 0.5;
            double len = Math.sqrt(dirX * dirX + dirY * dirY + dirZ * dirZ);
            if (!(len < 1.0E-4)) {
               float[] c = deadlyDanceColor(true, ix);
               double radius = 0.5 + level.random.nextDouble() * 2.5;
               double px = x + dirX / len * radius;
               double py = y + dirY / len * radius;
               double pz = z + dirZ / len * radius;
               level.sendParticles((SimpleParticleType)MainParticles.SPARKS.get(), px, py, pz, 0, (double)c[0], (double)c[1], (double)c[2], 1.0);
            }
         }

         int ringPoints = 5;

         for (int ixx = 0; ixx < ringPoints; ixx++) {
            double angle = (Math.PI * 2) * (double)ixx / (double)ringPoints;
            float[] c = deadlyDanceColor(true, ixx);

            for (double ry : new double[]{-0.4, 0.4}) {
               double px = x + Math.cos(angle) * 1.3;
               double pz = z + Math.sin(angle) * 1.3;
               level.sendParticles((SimpleParticleType)MainParticles.SPARKS.get(), px, y + ry, pz, 0, (double)c[0], (double)c[1], (double)c[2], 1.0);
            }
         }

         level.sendParticles(ParticleTypes.CRIT, x, y, z, 30, 0.6, 0.6, 0.6, 0.8);
         level.sendParticles(ParticleTypes.ENCHANTED_HIT, x, y, z, 20, 0.5, 0.5, 0.5, 0.6);
         level.sendParticles(ParticleTypes.FIREWORK, x, y, z, 40, 0.3, 0.3, 0.3, 0.35);
         level.sendParticles(ParticleTypes.FLASH, x, y, z, 1, 0.0, 0.0, 0.0, 0.0);
      }
   }

   private static void spawnWolfFangJab(ServerPlayer player, LivingEntity target, int beat) {
      if (player.level() instanceof ServerLevel level) {
         double var19 = target.getX();
         double y = target.getY() + (double)target.getBbHeight() * 0.6;
         double z = target.getZ();
         SoundEvent[] punches = new SoundEvent[]{
            (SoundEvent)MainSounds.GOLPE1.get(),
            (SoundEvent)MainSounds.GOLPE2.get(),
            (SoundEvent)MainSounds.GOLPE3.get(),
            (SoundEvent)MainSounds.GOLPE4.get(),
            (SoundEvent)MainSounds.GOLPE5.get(),
            (SoundEvent)MainSounds.GOLPE6.get()
         };
         SoundEvent punch = punches[Math.floorMod(beat / 4, punches.length)];
         level.playSound(null, var19, y, z, punch, SoundSource.PLAYERS, 1.0F, 1.1F + level.random.nextFloat() * 0.3F);
         level.sendParticles((SimpleParticleType)MainParticles.PUNCH_PARTICLE.get(), var19, y, z, 0, 0.3, 0.62, 1.0, 1.0);

         for (int i = 0; i < 4; i++) {
            double ox = (level.random.nextDouble() - 0.5) * 0.7;
            double oy = (level.random.nextDouble() - 0.5) * 0.7;
            double oz = (level.random.nextDouble() - 0.5) * 0.7;
            level.sendParticles((SimpleParticleType)MainParticles.SPARKS.get(), var19 + ox, y + oy, z + oz, 0, 0.25, 0.55, 1.0, 1.0);
         }

         level.sendParticles(ParticleTypes.CRIT, var19, y, z, 6, 0.3, 0.3, 0.3, 0.5);
      }
   }

   private static void spawnWolfFangFinalParticles(ServerLevel level, LivingEntity target) {
      double x = target.getX();
      double y = target.getY() + (double)target.getBbHeight() * 0.5;
      double z = target.getZ();
      level.sendParticles((SimpleParticleType)MainParticles.PUNCH_PARTICLE.get(), x, y, z, 0, 0.3, 0.62, 1.0, 1.0);

      for (int i = 0; i < 90; i++) {
         double dirX = level.random.nextDouble() - 0.5;
         double dirY = level.random.nextDouble() - 0.5;
         double dirZ = level.random.nextDouble() - 0.5;
         double len = Math.sqrt(dirX * dirX + dirY * dirY + dirZ * dirZ);
         if (!(len < 1.0E-4)) {
            double radius = 0.5 + level.random.nextDouble() * 2.5;
            double px = x + dirX / len * radius;
            double py = y + dirY / len * radius;
            double pz = z + dirZ / len * radius;
            level.sendParticles((SimpleParticleType)MainParticles.SPARKS.get(), px, py, pz, 0, 0.25, 0.55, 1.0, 1.0);
         }
      }
   }

   private static void playStrikeAnimation(ServerPlayer player, String animationId) {
      if (animationId != null && !animationId.isEmpty()) {
         NetworkHandler.sendToTrackingEntityAndSelf(
            new TriggerAnimationS2C(player.getUUID(), TriggerAnimationS2C.AnimationType.KI_ANIMATION, 0, -1, animationId), player
         );
      }
   }

   private static void stopStrikeAnimation(ServerPlayer player) {
      NetworkHandler.sendToTrackingEntityAndSelf(
         new TriggerAnimationS2C(player.getUUID(), TriggerAnimationS2C.AnimationType.KI_ANIMATION_STOP, 0, -1, ""), player
      );
   }

   private static void freezeEntity(LivingEntity entity) {
      entity.setDeltaMovement(Vec3.ZERO);
      entity.hurtMarked = true;
   }

   private static void faceEntity(LivingEntity source, LivingEntity target) {
      if (source != null && target != null) {
         source.lookAt(Anchor.EYES, target.getEyePosition());
         source.setYHeadRot(source.getYRot());
      }
   }

   private static void faceStrikeTarget(ServerPlayer player, LivingEntity target) {
      Integer partId = STRIKE_ANCHOR_PART.get(player.getUUID());
      if (partId != null && TargetHelper.getEntityOrPart(player.level(), partId) instanceof PartEntity<?> part && part.getParent() == target) {
         player.lookAt(Anchor.EYES, part.getBoundingBox().getCenter());
         player.setYHeadRot(player.getYRot());
      } else {
         faceEntity(player, target);
      }
   }

   private static void playStrikeHitAnimation(LivingEntity target) {
      if (target instanceof ServerPlayer serverPlayer) {
         NetworkHandler.sendToTrackingEntityAndSelf(
            new TriggerAnimationS2C(serverPlayer.getUUID(), TriggerAnimationS2C.AnimationType.KI_ANIMATION, 0, -1, "base.flyback"), serverPlayer
         );
      }
   }

   private static void playStrikeKnockbackAnimation(LivingEntity target) {
      if (target instanceof ServerPlayer serverPlayer) {
         NetworkHandler.sendToTrackingEntityAndSelf(
            new TriggerAnimationS2C(serverPlayer.getUUID(), TriggerAnimationS2C.AnimationType.KI_ANIMATION, 0, -1, "base.flyback"), serverPlayer
         );
      }
   }

   private static void setStrikeLocked(LivingEntity entity, boolean locked) {
      if (entity instanceof ServerPlayer serverPlayer) {
         StatsProvider.get(StatsCapability.INSTANCE, serverPlayer).ifPresent(stats -> {
            stats.getStatus().setStrikeLocked(locked);
            NetworkHandler.sendToTrackingEntityAndSelf(new StatsSyncS2C(serverPlayer), serverPlayer);
         });
      }
   }

   private static String getTechniqueCooldownKey(String techniqueId) {
      return "TechniqueCooldown_" + techniqueId;
   }

   private static record ActiveStrike(
      UUID playerId,
      UUID targetId,
      String techniqueId,
      String animationId,
      int durationTicks,
      int cooldownTicks,
      double totalDamage,
      double perHitDamage,
      double finalDamage,
      int hitIntervalTicks,
      int ticksElapsed
   ) {
      private StrikeAttackHandler.ActiveStrike withTicksElapsed(int ticksElapsed) {
         return new StrikeAttackHandler.ActiveStrike(
            this.playerId,
            this.targetId,
            this.techniqueId,
            this.animationId,
            this.durationTicks,
            this.cooldownTicks,
            this.totalDamage,
            this.perHitDamage,
            this.finalDamage,
            this.hitIntervalTicks,
            ticksElapsed
         );
      }
   }

   private static record PendingStrike(
      UUID playerId,
      UUID preferredTargetId,
      String techniqueId,
      String animationId,
      int durationTicks,
      int cooldownTicks,
      double energyCost,
      int ticksRemaining
   ) {
      private StrikeAttackHandler.PendingStrike withTicksRemaining(int ticksRemaining) {
         return new StrikeAttackHandler.PendingStrike(
            this.playerId, this.preferredTargetId, this.techniqueId, this.animationId, this.durationTicks, this.cooldownTicks, this.energyCost, ticksRemaining
         );
      }
   }

   private static record RecentHit(UUID targetId, long timestamp) {
   }
}
