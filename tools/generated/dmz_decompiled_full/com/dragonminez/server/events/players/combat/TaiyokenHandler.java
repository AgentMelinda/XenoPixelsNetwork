package com.dragonminez.server.events.players.combat;

import com.dragonminez.common.combat.logic.player.TargetHelper;
import com.dragonminez.common.init.MainSounds;
import com.dragonminez.common.network.NetworkHandler;
import com.dragonminez.common.network.S2C.StatsSyncS2C;
import com.dragonminez.common.network.S2C.TaiyokenBlindS2C;
import com.dragonminez.common.network.S2C.TriggerAnimationS2C;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.common.stats.techniques.KiAttackData;
import com.dragonminez.common.stats.techniques.TechniqueData;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.EventBusSubscriber.Bus;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent.Post;

@EventBusSubscriber(
   modid = "dragonminez",
   bus = Bus.GAME
)
public class TaiyokenHandler {
   public static final String TECHNIQUE_ID = "taiyoken";
   public static final String BLIND_UNTIL_TAG = "dmz_taiyoken_blind_until";
   private static final double RANGE = 20.0;
   private static final int COOLDOWN_TICKS = 900;
   private static final double FULL_LOOK_DOT = 0.93;
   private static final double PARTIAL_LOOK_DOT = 0.5;
   private static final String CAST_ANIMATION = "ki.solarflare_fire";
   private static final Set<LivingEntity> BLINDED_MOBS = new HashSet<>();

   public static void cast(ServerPlayer player) {
      if (!player.level().isClientSide) {
         StatsProvider.get(StatsCapability.INSTANCE, player)
            .ifPresent(
               stats -> {
                  if (stats.getStatus().isHasCreatedCharacter()) {
                     if (!stats.getStatus().isStunned()) {
                        if (!stats.getStatus().isFused() || stats.getStatus().isFusionLeader()) {
                           if (!player.isSpectator()) {
                              if (stats.getSkills().getSkillLevel("kicontrol") > 0) {
                                 if (stats.getResources().getPowerRelease() >= 5) {
                                    if (player.getMainHandItem().isEmpty()) {
                                       TechniqueData unlocked = stats.getTechniques().getUnlockedTechniques().get("taiyoken");
                                       if (unlocked instanceof KiAttackData technique) {
                                          String cooldownKey = "TechniqueCooldown_taiyoken";
                                          if (!stats.getCooldowns().hasCooldown(cooldownKey)) {
                                             double cost = technique.getCalculatedCost(stats);
                                             if (player.isCreative() || !((double)stats.getResources().getCurrentEnergy() < cost)) {
                                                if (!player.isCreative() && cost > 0.0) {
                                                   stats.getResources().removeEnergy((float)((int)Math.ceil(cost)));
                                                }

                                                stats.getCooldowns().setCooldown(cooldownKey, 900);
                                                int xpGain = technique.getXpGainPerHit();
                                                if (xpGain > 0) {
                                                   stats.getTechniques().addExperienceToTechnique("taiyoken", xpGain);
                                                }

                                                NetworkHandler.sendToTrackingEntityAndSelf(new StatsSyncS2C(player), player);
                                                NetworkHandler.sendToTrackingEntityAndSelf(
                                                   new TriggerAnimationS2C(
                                                      player.getUUID(), TriggerAnimationS2C.AnimationType.KI_ANIMATION, 0, -1, "ki.solarflare_fire"
                                                   ),
                                                   player
                                                );
                                                player.level()
                                                   .playSound(
                                                      null,
                                                      player.getX(),
                                                      player.getY(),
                                                      player.getZ(),
                                                      (SoundEvent)MainSounds.KI_EXPLOSION_CHARGE.get(),
                                                      SoundSource.PLAYERS,
                                                      1.2F,
                                                      1.6F
                                                   );
                                                applyBlind(player);
                                             }
                                          }
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

   private static void applyBlind(ServerPlayer caster) {
      Vec3 casterEye = caster.getEyePosition();
      AABB box = caster.getBoundingBox().inflate(20.0);

      for (LivingEntity victim : caster.level().getEntitiesOfClass(LivingEntity.class, box, e -> e != caster && e.isAlive() && e.isPickable())) {
         double distance = (double)caster.distanceTo(victim);
         if (!(distance > 20.0) && victim.hasLineOfSight(caster)) {
            Vec3 victimLook = victim.getViewVector(1.0F).normalize();
            Vec3 toCaster = casterEye.subtract(victim.getEyePosition());
            if (!(toCaster.lengthSqr() < 1.0E-6)) {
               double dot = victimLook.dot(toCaster.normalize());
               if (!(dot < 0.5)) {
                  boolean fullLook = dot >= 0.93;
                  double distanceFrac = Mth.clamp(distance / 20.0, 0.0, 1.0);
                  double seconds = fullLook ? 12.0 - 3.0 * distanceFrac : 9.0 - 3.0 * distanceFrac;
                  if (TargetHelper.getRelation(caster, victim) == TargetHelper.Relation.FRIENDLY) {
                     seconds *= 0.5;
                  }

                  int durationTicks = Math.max(1, (int)Math.round(seconds * 20.0));
                  if (victim instanceof ServerPlayer victimPlayer) {
                     NetworkHandler.sendToPlayer(new TaiyokenBlindS2C(durationTicks), victimPlayer);
                  } else {
                     blindMob(victim, durationTicks);
                  }
               }
            }
         }
      }
   }

   private static void blindMob(LivingEntity victim, int durationTicks) {
      long until = victim.level().getGameTime() + (long)durationTicks;
      victim.getPersistentData().putLong("dmz_taiyoken_blind_until", until);
      if (victim instanceof Mob mob) {
         mob.setTarget(null);
      }

      BLINDED_MOBS.add(victim);
   }

   public static boolean isBlinded(LivingEntity entity) {
      return entity == null ? false : entity.getPersistentData().getLong("dmz_taiyoken_blind_until") > entity.level().getGameTime();
   }

   @SubscribeEvent
   public static void onLevelTick(Post event) {
      if (!event.getLevel().isClientSide) {
         if (!BLINDED_MOBS.isEmpty()) {
            Iterator<LivingEntity> it = BLINDED_MOBS.iterator();

            while (it.hasNext()) {
               LivingEntity entity = it.next();
               if (entity == null || !entity.isAlive() || entity.isRemoved() || !isBlinded(entity)) {
                  it.remove();
               } else if (entity instanceof Mob) {
                  Mob mob = (Mob)entity;
                  if (mob.getTarget() != null) {
                     mob.setTarget(null);
                  }
               }
            }
         }
      }
   }

   @SubscribeEvent
   public static void onPlayerLoggedOut(PlayerLoggedOutEvent event) {
      if (event.getEntity() instanceof Player) {
         BLINDED_MOBS.remove(event.getEntity());
      }
   }
}
