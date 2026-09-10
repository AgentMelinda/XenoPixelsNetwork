package com.dragonminez.server.events.players.combat;

import com.dragonminez.common.init.MainEffects;
import com.dragonminez.common.init.MainGameRules;
import com.dragonminez.common.init.MainParticles;
import com.dragonminez.common.init.MainSounds;
import com.dragonminez.common.network.NetworkHandler;
import com.dragonminez.common.network.S2C.TriggerAnimationS2C;
import com.dragonminez.common.network.S2C.TriggerImpactFrameS2C;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsProvider;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.EventBusSubscriber.Bus;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent.Post;

@EventBusSubscriber(
   modid = "dragonminez",
   bus = Bus.GAME
)
public class MomentumImpactHandler {
   private static final Map<UUID, MomentumImpactHandler.CollisionImpactContext> COLLISION_IMPACTS = new HashMap<>();
   private static final Set<UUID> IMPACT_ANIM_PLAYING = new HashSet<>();
   public static final double MOMENTUM_SPEED_THRESHOLD = 0.65;
   public static final double MOMENTUM_MAX_SPEED = 1.5;
   private static final String IMPACT_WALL_ANIM = "base.faint_horizontal";
   private static final String IMPACT_GROUND_ANIM = "base.faint_vertical";

   @SubscribeEvent
   public static void onPlayerTick(Post event) {
      if (!event.getEntity().level().isClientSide) {
         ServerPlayer player = (ServerPlayer)event.getEntity();
         Vec3 currentPos = player.position();
         boolean wasOnGround = player.getPersistentData().getBoolean("dmz_was_grounded");
         boolean isGrounded = player.onGround();
         boolean isFlying = StatsProvider.get(StatsCapability.INSTANCE, player).map(data -> data.getSkills().isSkillActive("fly")).orElse(false);
         if (player.getPersistentData().contains("dmz_last_x")) {
            double lastX = player.getPersistentData().getDouble("dmz_last_x");
            double lastY = player.getPersistentData().getDouble("dmz_last_y");
            double lastZ = player.getPersistentData().getDouble("dmz_last_z");
            double dx = currentPos.x - lastX;
            double dy = currentPos.y - lastY;
            double dz = currentPos.z - lastZ;
            double speed = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (speed < 10.0) {
               player.getPersistentData().putDouble("dmz_server_speed", speed);
               if (speed > 0.05) {
                  player.getPersistentData().putDouble("dmz_momentum_x", dx);
                  player.getPersistentData().putDouble("dmz_momentum_y", dy);
                  player.getPersistentData().putDouble("dmz_momentum_z", dz);
               }
            }
         }

         player.getPersistentData().putDouble("dmz_last_x", currentPos.x);
         player.getPersistentData().putDouble("dmz_last_y", currentPos.y);
         player.getPersistentData().putDouble("dmz_last_z", currentPos.z);
         player.getPersistentData().putBoolean("dmz_was_grounded", isGrounded);
         if (!isGrounded) {
            player.getPersistentData().putBoolean("dmz_was_flying", isFlying);
         } else {
            player.getPersistentData().putBoolean("dmz_was_flying", false);
         }
      }
   }

   @SubscribeEvent
   public static void onLivingTick(net.neoforged.neoforge.event.tick.EntityTickEvent.Post event) {
      if (event.getEntity() instanceof LivingEntity living) {
         if (!living.level().isClientSide) {
            if (IMPACT_ANIM_PLAYING.contains(living.getUUID()) && !living.hasEffect(MainEffects.STUN)) {
               IMPACT_ANIM_PLAYING.remove(living.getUUID());
               stopImpactAnimation(living);
            }

            MomentumImpactHandler.CollisionImpactContext impact = COLLISION_IMPACTS.get(living.getUUID());
            if (impact != null) {
               long now = System.currentTimeMillis();
               if (impact.expiryMs() < now) {
                  COLLISION_IMPACTS.remove(living.getUUID());
               } else {
                  boolean wallImpact = impact.type() == MomentumImpactHandler.CollisionImpactType.WALL && living.horizontalCollision;
                  boolean groundImpact = impact.type() == MomentumImpactHandler.CollisionImpactType.GROUND
                     && living.onGround()
                     && (impact.startY() - living.getY() > 0.6 || living.fallDistance > 0.75F);
                  if (wallImpact || groundImpact) {
                     if (impact.momentumDirection() != null) {
                        impact.momentumDirection();
                     } else {
                        living.getDeltaMovement().normalize();
                     }

                     COLLISION_IMPACTS.remove(living.getUUID());
                     playImpactAnimation(living, impact.type());
                     if (living instanceof ServerPlayer) {
                        IMPACT_ANIM_PLAYING.add(living.getUUID());
                     }

                     living.addEffect(new MobEffectInstance(MainEffects.STUN, 30, 0, false, false, true));
                     living.level()
                        .playSound(null, living.getX(), living.getY(), living.getZ(), (SoundEvent)MainSounds.PARRY.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
                     if (living.level() instanceof ServerLevel serverLevel) {
                        spawnRockImpactCircle(serverLevel, living.position(), impact.type() == MomentumImpactHandler.CollisionImpactType.GROUND ? 2.75 : 1.9);
                        createCrater(serverLevel, living.blockPosition(), 1.5, living);
                        NetworkHandler.sendToTrackingEntityAndSelf(new TriggerImpactFrameS2C(0.6F, 0.05F, 2, true), living);
                     }
                  }
               }
            }
         }
      }
   }

   @SubscribeEvent
   public static void onPlayerLoggedOut(PlayerLoggedOutEvent event) {
      UUID id = event.getEntity().getUUID();
      COLLISION_IMPACTS.remove(id);
      IMPACT_ANIM_PLAYING.remove(id);
   }

   public static void registerCollisionImpact(LivingEntity victim, MomentumImpactHandler.CollisionImpactType type, float extraDamage, Vec3 momentumDir) {
      long expiryMs = System.currentTimeMillis() + 1200L;
      COLLISION_IMPACTS.put(victim.getUUID(), new MomentumImpactHandler.CollisionImpactContext(type, expiryMs, victim.getY(), extraDamage, momentumDir));
   }

   private static void createCrater(ServerLevel level, BlockPos center, double radius, LivingEntity source) {
      int r = (int)Math.ceil(radius);

      for (int x = -r; x <= r; x++) {
         for (int y = -r; y <= r; y++) {
            for (int z = -r; z <= r; z++) {
               if ((double)(x * x + y * y + z * z) <= radius * radius) {
                  BlockPos pos = center.offset(x, y, z);
                  BlockState state = level.getBlockState(pos);
                  if (!state.isAir()
                     && state.getDestroySpeed(level, pos) >= 0.0F
                     && state.getDestroySpeed(level, pos) < 50.0F
                     && MainGameRules.canKiGrief(level, pos, source)) {
                     level.destroyBlock(pos, true);
                  }
               }
            }
         }
      }
   }

   public static void spawnDustTrail(ServerLevel level, Vec3 origin, Vec3 dir, int points) {
      Vec3 norm = dir.lengthSqr() > 1.0E-6 ? dir.normalize() : Vec3.ZERO;

      for (int i = 0; i < points; i++) {
         double t = (double)i * 0.35;
         Vec3 pos = origin.subtract(norm.scale(t));
         level.sendParticles((SimpleParticleType)MainParticles.DUST.get(), pos.x, pos.y + 0.05, pos.z, 2, 0.12, 0.05, 0.12, 0.01);
      }
   }

   private static void spawnRockImpactCircle(ServerLevel level, Vec3 center, double radius) {
      for (int i = 0; i < 20; i++) {
         double angle = (Math.PI * 2) * (double)i / 20.0;
         double x = center.x + Math.cos(angle) * radius;
         double z = center.z + Math.sin(angle) * radius;
         level.sendParticles((SimpleParticleType)MainParticles.ROCK.get(), x, center.y + 0.05, z, 1, 0.08, 0.03, 0.08, 0.01);
      }
   }

   private static void playImpactAnimation(LivingEntity living, MomentumImpactHandler.CollisionImpactType type) {
      if (living instanceof ServerPlayer serverPlayer) {
         String anim = type == MomentumImpactHandler.CollisionImpactType.GROUND ? "base.faint_vertical" : "base.faint_horizontal";
         NetworkHandler.sendToTrackingEntityAndSelf(
            new TriggerAnimationS2C(serverPlayer.getUUID(), TriggerAnimationS2C.AnimationType.KI_ANIMATION, 0, -1, anim), serverPlayer
         );
      }
   }

   private static void stopImpactAnimation(LivingEntity living) {
      if (living instanceof ServerPlayer serverPlayer) {
         NetworkHandler.sendToTrackingEntityAndSelf(
            new TriggerAnimationS2C(serverPlayer.getUUID(), TriggerAnimationS2C.AnimationType.KI_ANIMATION_STOP, 0, -1, ""), serverPlayer
         );
      }
   }

   public static record CollisionImpactContext(
      MomentumImpactHandler.CollisionImpactType type, long expiryMs, double startY, float extraDamage, Vec3 momentumDirection
   ) {
   }

   public static enum CollisionImpactType {
      WALL,
      GROUND;
   }
}
