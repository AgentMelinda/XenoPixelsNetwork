package com.dragonminez.common.combat.clash;

import com.dragonminez.common.init.MainSounds;
import com.dragonminez.common.init.entities.ki.AbstractKiProjectile;
import com.dragonminez.common.network.NetworkHandler;
import com.dragonminez.common.network.S2C.BeamClashStateS2C;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.EventBusSubscriber.Bus;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent.Post;
import net.neoforged.neoforge.registries.DeferredHolder;

@EventBusSubscriber(
   modid = "dragonminez",
   bus = Bus.GAME
)
public class BeamClashManager {
   private static final double OPPOSITION_DOT = -0.3;
   private static final double CLASH_RADIUS_FACTOR = 1.0;
   private static final double CLASH_RADIUS_PAD = 1.5;
   private static final double MINOR_BREAK_FACTOR = 0.7;
   private static final double MINOR_BREAK_PAD = 0.6;
   private static final List<BeamClash> ACTIVE_CLASHES = new ArrayList<>();
   private static final Set<UUID> CLASHING_OWNERS = new HashSet<>();
   private static final DeferredHolder<SoundEvent, ? extends SoundEvent>[] PUNCH_SOUNDS = new DeferredHolder[]{
      MainSounds.GOLPE1, MainSounds.GOLPE2, MainSounds.GOLPE3, MainSounds.GOLPE4, MainSounds.GOLPE5, MainSounds.GOLPE6
   };

   @SubscribeEvent
   public static void onLevelTick(Post event) {
      if (event.getLevel() instanceof ServerLevel level) {
         advanceActiveClashes();
         ArrayList var8 = new ArrayList();
         ArrayList minors = new ArrayList();

         for (Entity entity : level.getAllEntities()) {
            if (entity instanceof AbstractKiProjectile) {
               AbstractKiProjectile ki = (AbstractKiProjectile)entity;
               if (!ki.isRemoved() && ki.getOwner() instanceof LivingEntity) {
                  AbstractKiProjectile.ClashRole role = ki.getClashRole();
                  if (role == AbstractKiProjectile.ClashRole.MAJOR && ki.isClashableBeam() && !ki.isClashLocked()) {
                     var8.add(ki);
                  } else if (role == AbstractKiProjectile.ClashRole.MINOR) {
                     minors.add(ki);
                  }
               }
            }
         }

         detectNewClashes(var8);
         breakMinorAttacks(level, var8, minors);
         rebuildClashingOwners();
         syncParticipants();
      }
   }

   private static void advanceActiveClashes() {
      ACTIVE_CLASHES.removeIf(clash -> {
         BeamClash.Result result = clash.tick();
         switch (result) {
            case A_WINS:
            case B_WINS:
               notifyEnded(clash);
               clash.resolve(result);
               return true;
            case DISSOLVED:
               notifyEnded(clash);
               clash.dissolve();
               return true;
            default:
               return false;
         }
      });
   }

   private static void detectNewClashes(List<AbstractKiProjectile> majors) {
      for (int i = 0; i < majors.size(); i++) {
         AbstractKiProjectile beamA = majors.get(i);
         if (!beamA.isClashLocked()) {
            Entity j = beamA.getOwner();
            if (j instanceof LivingEntity) {
               LivingEntity ownerA = (LivingEntity)j;

               for (int jx = i + 1; jx < majors.size(); jx++) {
                  AbstractKiProjectile beamB = majors.get(jx);
                  if (!beamB.isClashLocked() && beamB.getOwner() instanceof LivingEntity ownerB && ownerA != ownerB && beamsClash(beamA, beamB)) {
                     BeamClash clash = new BeamClash(new ClashParticipant(beamA, ownerA), new ClashParticipant(beamB, ownerB));
                     beamA.setClashLock(beamA.getClashBeamLength(), ownerB.getUUID());
                     beamB.setClashLock(beamB.getClashBeamLength(), ownerA.getUUID());
                     ACTIVE_CLASHES.add(clash);
                     break;
                  }
               }
            }
         }
      }
   }

   private static boolean beamsClash(AbstractKiProjectile beamA, AbstractKiProjectile beamB) {
      Vec3 dirA = Vec3.directionFromRotation(beamA.getClashPitch(), beamA.getClashYaw());
      Vec3 dirB = Vec3.directionFromRotation(beamB.getClashPitch(), beamB.getClashYaw());
      if (dirA.dot(dirB) > -0.3) {
         return false;
      } else {
         Vec3 a0 = beamA.position();
         Vec3 a1 = a0.add(dirA.scale((double)Math.max(0.1F, beamA.getClashBeamLength())));
         Vec3 b0 = beamB.position();
         Vec3 b1 = b0.add(dirB.scale((double)Math.max(0.1F, beamB.getClashBeamLength())));
         double threshold = (double)(beamA.getSize() + beamB.getSize()) * 1.0 + 1.5;
         return segmentDistanceSq(a0, a1, b0, b1) <= threshold * threshold;
      }
   }

   private static void breakMinorAttacks(ServerLevel level, List<AbstractKiProjectile> majors, List<AbstractKiProjectile> minors) {
      if (!minors.isEmpty()) {
         for (AbstractKiProjectile major : majors) {
            Entity dir = major.getOwner();
            if (dir instanceof LivingEntity) {
               LivingEntity majorOwner = (LivingEntity)dir;
               Vec3 dirx = Vec3.directionFromRotation(major.getClashPitch(), major.getClashYaw());
               Vec3 a0 = major.position();
               Vec3 a1 = a0.add(dirx.scale((double)Math.max(0.1F, major.getClashBeamLength())));

               for (AbstractKiProjectile minor : minors) {
                  if (!minor.isRemoved() && minor.getOwner() != majorOwner) {
                     double threshold = (double)(major.getSize() + minor.getSize()) * 0.7 + 0.6;
                     if (pointSegmentDistanceSq(minor.position(), a0, a1) <= threshold * threshold) {
                        shatterMinor(level, minor);
                     }
                  }
               }
            }
         }
      }
   }

   private static void shatterMinor(ServerLevel level, AbstractKiProjectile minor) {
      Vec3 p = minor.position();
      level.sendParticles(ParticleTypes.POOF, p.x, p.y, p.z, 6, 0.2, 0.2, 0.2, 0.02);
      level.playSound(null, p.x, p.y, p.z, (SoundEvent)MainSounds.KI_EXPLOSION_IMPACT.get(), SoundSource.PLAYERS, 0.6F, 1.3F);
      minor.discard();
   }

   private static double segmentDistanceSq(Vec3 p1, Vec3 q1, Vec3 p2, Vec3 q2) {
      Vec3 d1 = q1.subtract(p1);
      Vec3 d2 = q2.subtract(p2);
      Vec3 r = p1.subtract(p2);
      double a = d1.dot(d1);
      double e = d2.dot(d2);
      double f = d2.dot(r);
      double EPS = 1.0E-9;
      if (a <= 1.0E-9 && e <= 1.0E-9) {
         return r.dot(r);
      } else {
         double s;
         double t;
         if (a <= 1.0E-9) {
            s = 0.0;
            t = clamp01(f / e);
         } else {
            double c = d1.dot(r);
            if (e <= 1.0E-9) {
               t = 0.0;
               s = clamp01(-c / a);
            } else {
               double b = d1.dot(d2);
               double denom = a * e - b * b;
               s = denom > 1.0E-9 ? clamp01((b * f - c * e) / denom) : 0.0;
               t = (b * s + f) / e;
               if (t < 0.0) {
                  t = 0.0;
                  s = clamp01(-c / a);
               } else if (t > 1.0) {
                  t = 1.0;
                  s = clamp01((b - c) / a);
               }
            }
         }

         Vec3 c1 = p1.add(d1.scale(s));
         Vec3 c2 = p2.add(d2.scale(t));
         Vec3 diff = c1.subtract(c2);
         return diff.dot(diff);
      }
   }

   private static double pointSegmentDistanceSq(Vec3 p, Vec3 s0, Vec3 s1) {
      Vec3 d = s1.subtract(s0);
      double len2 = d.dot(d);
      if (len2 <= 1.0E-9) {
         return p.subtract(s0).lengthSqr();
      } else {
         double t = clamp01(p.subtract(s0).dot(d) / len2);
         Vec3 proj = s0.add(d.scale(t));
         return p.subtract(proj).lengthSqr();
      }
   }

   private static double clamp01(double v) {
      return v < 0.0 ? 0.0 : Math.min(v, 1.0);
   }

   private static void rebuildClashingOwners() {
      CLASHING_OWNERS.clear();

      for (BeamClash clash : ACTIVE_CLASHES) {
         CLASHING_OWNERS.add(clash.a().owner().getUUID());
         CLASHING_OWNERS.add(clash.b().owner().getUUID());
      }
   }

   private static void syncParticipants() {
      for (BeamClash clash : ACTIVE_CLASHES) {
         sendState(clash, clash.a());
         sendState(clash, clash.b());
      }
   }

   private static void sendState(BeamClash clash, ClashParticipant participant) {
      if (participant.owner() instanceof ServerPlayer player) {
         float var5 = clash.advantageFor(player);
         ClashParticipant opponent = participant == clash.a() ? clash.b() : clash.a();
         NetworkHandler.sendToPlayer(
            new BeamClashStateS2C(true, participant.meterPhase(), 0.78F, 0.96F, var5, participant.beam().getColorBorder(), opponent.owner().getId()), player
         );
      }
   }

   private static void notifyEnded(BeamClash clash) {
      notifyEnded(clash.a());
      notifyEnded(clash.b());
   }

   private static void notifyEnded(ClashParticipant participant) {
      if (participant.owner() instanceof ServerPlayer player) {
         NetworkHandler.sendToPlayer(BeamClashStateS2C.inactive(), player);
      }
   }

   public static boolean isClashing(UUID ownerId) {
      return CLASHING_OWNERS.contains(ownerId);
   }

   public static void handlePlayerPress(ServerPlayer player) {
      for (BeamClash clash : ACTIVE_CLASHES) {
         ClashParticipant participant = clash.participantFor(player.getUUID());
         if (participant != null) {
            participant.registerPlayerPress();
            playPunchSound(player);
            return;
         }
      }
   }

   private static void playPunchSound(ServerPlayer player) {
      SoundEvent sound = (SoundEvent)PUNCH_SOUNDS[player.getRandom().nextInt(PUNCH_SOUNDS.length)].get();
      float pitch = 0.9F + player.getRandom().nextFloat() * 0.2F;
      player.level().playSound(null, player.getX(), player.getY(), player.getZ(), sound, SoundSource.PLAYERS, 1.0F, pitch);
   }

   @SubscribeEvent(
      priority = EventPriority.HIGHEST
   )
   public static void onClashingHurt(LivingIncomingDamageEvent event) {
      LivingEntity victim = event.getEntity();
      if (!victim.level().isClientSide) {
         if (isClashing(victim.getUUID())) {
            event.setCanceled(true);
         }
      }
   }
}
