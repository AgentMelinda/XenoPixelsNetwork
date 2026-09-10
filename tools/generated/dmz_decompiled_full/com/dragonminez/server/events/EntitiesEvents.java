package com.dragonminez.server.events;

import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.config.EntitiesConfig;
import com.dragonminez.common.init.EntityAttributes;
import com.dragonminez.common.init.MainDamageTypes;
import com.dragonminez.common.init.MainEffects;
import com.dragonminez.common.init.entities.ITextureVariant;
import com.dragonminez.common.init.entities.MastersEntity;
import com.dragonminez.common.init.entities.ShadowDummyEntity;
import com.dragonminez.common.init.entities.ki.AbstractKiProjectile;
import com.dragonminez.common.init.entities.sagas.DBSagasEntity;
import com.dragonminez.common.network.NetworkHandler;
import com.dragonminez.common.network.C2S.SummonPlayerShadowDummyC2S;
import com.dragonminez.common.network.S2C.AppearanceSyncS2C;
import com.dragonminez.common.quest.Difficulty;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.server.world.data.PartySavedData;
import com.dragonminez.server.world.dimension.SacredKaiDimension;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.MushroomCow;
import net.minecraft.world.entity.animal.MushroomCow.MushroomType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.EventBusSubscriber.Bus;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent.Pre;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent.Added;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerRespawnEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.EntityInteract;
import net.neoforged.neoforge.event.tick.EntityTickEvent.Post;

@EventBusSubscriber(
   modid = "dragonminez",
   bus = Bus.GAME
)
public class EntitiesEvents {
   private static final double QUEST_TETHER_RANGE_SQR = 31250.0;
   private static final double SHADOW_DUMMY_TETHER_SQR = 10000.0;
   private static final int QUEST_COMBAT_TICK_INTERVAL = 20;
   private static final int QUEST_COMBAT_GRACE_TICKS = 100;
   private static final int KI_SLOW_DURATION_TICKS = 30;

   @SubscribeEvent
   public static void onEntityJoinWorld(EntityJoinLevelEvent event) {
      if (!event.getLevel().isClientSide() && event.getEntity() instanceof LivingEntity entity) {
         if (!entity.getPersistentData().getBoolean("dmz_stats_configured")) {
            Difficulty difficulty = Difficulty.fromName(entity.getPersistentData().getString("dmz_difficulty"));
            double hpMult = difficulty.hpMultiplier();
            double dmgMult = difficulty.damageMultiplier();
            boolean isQuestEntity = entity.getPersistentData().contains("dmz_quest_hp");
            if (isQuestEntity) {
               double finalHealth = entity.getPersistentData().getDouble("dmz_quest_hp") * hpMult;
               double finalMelee = entity.getPersistentData().getDouble("dmz_quest_melee") * dmgMult;
               double finalKi = entity.getPersistentData().getDouble("dmz_quest_ki") * dmgMult;
               applyStatsToEntity(entity, finalHealth, finalMelee, finalKi);
               if (entity.getPersistentData().contains("dmz_quest_texture_variant") && entity instanceof ITextureVariant variantEntity) {
                  variantEntity.setTextureVariant(entity.getPersistentData().getInt("dmz_quest_texture_variant"));
               }

               if (entity.getPersistentData().contains("dmz_quest_ai_tier") && entity instanceof DBSagasEntity sagasEntity) {
                  sagasEntity.setAiTierById(entity.getPersistentData().getInt("dmz_quest_ai_tier"));
               }

               if (entity.getPersistentData().getBoolean("dmz_quest_no_transform") && entity instanceof DBSagasEntity sagasEntity) {
                  sagasEntity.setTransformationDisabled(true);
               }
            } else {
               String registryName = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
               EntitiesConfig.EntityStats defaultStats = ConfigManager.getEntityStats(registryName);
               if (defaultStats != null) {
                  double finalHealthx = (defaultStats.getHealth() != null ? defaultStats.getHealth() : 20.0) * hpMult;
                  double finalMeleex = (defaultStats.getMeleeDamage() != null ? defaultStats.getMeleeDamage() : 1.0) * dmgMult;
                  double finalKix = (defaultStats.getKiDamage() != null ? defaultStats.getKiDamage() : 1.0) * dmgMult;
                  applyStatsToEntity(entity, finalHealthx, finalMeleex, finalKix);
               }
            }

            entity.getPersistentData().putBoolean("dmz_stats_configured", true);
         }
      }
   }

   @SubscribeEvent
   public static void onMooshroomSpawnVariant(EntityJoinLevelEvent event) {
      if (!event.getLevel().isClientSide()) {
         if (event.getEntity() instanceof MushroomCow cow) {
            if (event.getLevel().dimension().equals(SacredKaiDimension.SACREDKAI_KEY)) {
               if (!cow.getPersistentData().getBoolean("dmz_mooshroom_variant")) {
                  cow.getPersistentData().putBoolean("dmz_mooshroom_variant", true);
                  if (cow.getRandom().nextBoolean()) {
                     cow.setVariant(MushroomType.BROWN);
                  }
               }
            }
         }
      }
   }

   @SubscribeEvent
   public static void onStunnedEntityAttack(LivingIncomingDamageEvent event) {
      if (!event.getEntity().level().isClientSide()) {
         if (event.getSource().getDirectEntity() instanceof LivingEntity attacker && attacker.hasEffect(MainEffects.STUN)) {
            event.setCanceled(true);
         }
      }
   }

   @SubscribeEvent
   public static void onStunApplied(Added event) {
      LivingEntity owned = event.getEntity();
      if (owned instanceof LivingEntity) {
         LivingEntity entity = owned;
         if (!entity.level().isClientSide() && !(entity instanceof Player)) {
            if (event.getEffectInstance().getEffect() == MainEffects.STUN) {
               if (entity instanceof DBSagasEntity saga && saga.isCasting()) {
                  saga.stopCasting();
               }

               for (AbstractKiProjectile projectile : entity.level()
                  .getEntitiesOfClass(
                     AbstractKiProjectile.class,
                     entity.getBoundingBox().inflate(64.0),
                     p -> p.getOwner() != null && p.getOwner().getUUID().equals(entity.getUUID())
                  )) {
                  projectile.discard();
               }
            }
         }
      }
   }

   @SubscribeEvent
   public static void onStunnedEntityTick(Post event) {
      if (event.getEntity() instanceof LivingEntity entity) {
         if (!entity.level().isClientSide() && !(entity instanceof Player)) {
            if (entity.hasEffect(MainEffects.STUN)) {
               Vec3 movement = entity.getDeltaMovement();
               entity.setDeltaMovement(0.0, Math.min(movement.y, 0.0), 0.0);
               if (entity instanceof Mob mob) {
                  mob.getNavigation().stop();
                  mob.setJumping(false);
               }
            }
         }
      }
   }

   @SubscribeEvent
   public static void onKiHitSlow(Pre event) {
      if (!event.getEntity().level().isClientSide()) {
         if (MainDamageTypes.isKiblastDamage(event.getSource())) {
            event.getEntity().addEffect(new MobEffectInstance(MainEffects.KI_SLOW, 30, 0, false, false, true));
         }
      }
   }

   private static void applyStatsToEntity(LivingEntity entity, double health, double melee, double ki) {
      if (entity.getAttributes().hasAttribute(Attributes.MAX_HEALTH)) {
         entity.getAttribute(Attributes.MAX_HEALTH).setBaseValue(health);
         entity.heal((float)health);
      }

      if (entity.getAttributes().hasAttribute(Attributes.ATTACK_DAMAGE)) {
         entity.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(melee);
      }

      if (entity.getAttributes().hasAttribute(EntityAttributes.KI_BLAST_DAMAGE)) {
         entity.getAttribute(EntityAttributes.KI_BLAST_DAMAGE).setBaseValue(ki);
      }
   }

   @SubscribeEvent
   public static void onPlayerLoggedOut(PlayerLoggedOutEvent event) {
      if (!event.getEntity().level().isClientSide() && event.getEntity() instanceof ServerPlayer player) {
         cleanupQuestEntities(player.serverLevel(), player.getUUID());
      }
   }

   @SubscribeEvent
   public static void onPlayerChangedDimension(PlayerChangedDimensionEvent event) {
      if (!event.getEntity().level().isClientSide() && event.getEntity() instanceof ServerPlayer player) {
         ServerLevel oldLevel = player.getServer().getLevel(event.getFrom());
         if (oldLevel != null) {
            cleanupQuestEntities(oldLevel, player.getUUID());
         }
      }
   }

   @SubscribeEvent
   public static void onPlayerDeath(PlayerRespawnEvent event) {
      if (!event.getEntity().level().isClientSide() && event.getEntity() instanceof ServerPlayer player) {
         cleanupQuestEntities(player.serverLevel(), player.getUUID());
      }
   }

   @SubscribeEvent
   public static void onEntityTick(Post event) {
      if (event.getEntity() instanceof LivingEntity entity) {
         if (!entity.level().isClientSide() && entity.tickCount % 1000 == 0) {
            if (entity.getPersistentData().contains("dmz_quest_owner")) {
               String ownerUUIDStr = entity.getPersistentData().getString("dmz_quest_owner");

               try {
                  UUID ownerUUID = UUID.fromString(ownerUUIDStr);
                  MinecraftServer server = entity.getServer();
                  if (entity.getPersistentData().contains("dmz_quest_key")) {
                     if (!hasQuestGuardianInRange(server, entity, ownerUUID, null)) {
                        entity.discard();
                     }

                     return;
                  }

                  ServerPlayer player = server.getPlayerList().getPlayer(ownerUUID);
                  if (entity instanceof ShadowDummyEntity shadow && entity.getPersistentData().getBoolean("dmz_player_shadow")) {
                     if (player == null || player.level() != entity.level() || entity.distanceToSqr(player) > 10000.0) {
                        SummonPlayerShadowDummyC2S.dismissByDummy(shadow);
                     }

                     return;
                  }

                  if (player == null || player.level() != entity.level() || entity.distanceToSqr(player) > 31250.0) {
                     entity.discard();
                  }
               } catch (Exception var7) {
                  entity.discard();
               }
            }
         }
      }
   }

   @SubscribeEvent
   public static void onQuestCombatTick(Post event) {
      if (event.getEntity() instanceof LivingEntity entity) {
         if (!entity.level().isClientSide() && entity.tickCount % 20 == 0) {
            if (entity instanceof Mob mob) {
               if (mob.getPersistentData().contains("dmz_quest_key") && mob.getPersistentData().contains("dmz_quest_owner")) {
                  MinecraftServer server = mob.getServer();
                  if (server != null) {
                     UUID ownerUUID;
                     try {
                        ownerUUID = UUID.fromString(mob.getPersistentData().getString("dmz_quest_owner"));
                     } catch (IllegalArgumentException var13) {
                        return;
                     }

                     boolean anyLivingGuardian = false;
                     ServerPlayer nearestLivingInRange = null;
                     double nearestSqr = Double.MAX_VALUE;

                     for (ServerPlayer guardian : questGuardians(server, ownerUUID)) {
                        if (!guardian.isDeadOrDying() && !guardian.isSpectator()) {
                           anyLivingGuardian = true;
                           if (guardian.level() == mob.level()) {
                              double distSqr = mob.distanceToSqr(guardian);
                              if (distSqr <= 31250.0 && distSqr < nearestSqr) {
                                 nearestSqr = distSqr;
                                 nearestLivingInRange = guardian;
                              }
                           }
                        }
                     }

                     if (!anyLivingGuardian) {
                        if (mob.tickCount >= 100) {
                           mob.discard();
                        }
                     } else {
                        LivingEntity current = mob.getTarget();
                        if ((current == null || !current.isAlive()) && nearestLivingInRange != null) {
                           mob.setTarget(nearestLivingInRange);
                        }
                     }
                  }
               }
            }
         }
      }
   }

   public static void cleanupQuestEntities(ServerLevel level, UUID playerUUID) {
      String uuidStr = playerUUID.toString();
      MinecraftServer server = level.getServer();

      for (Entity entity : level.getAllEntities()) {
         if (entity instanceof LivingEntity && entity.getPersistentData().contains("dmz_quest_owner")) {
            String ownerUUID = entity.getPersistentData().getString("dmz_quest_owner");
            if (ownerUUID.equals(uuidStr)) {
               if (entity instanceof ShadowDummyEntity) {
                  ShadowDummyEntity shadow = (ShadowDummyEntity)entity;
                  if (shadow.getPersistentData().getBoolean("dmz_player_shadow")) {
                     SummonPlayerShadowDummyC2S.dismissByDummy(shadow);
                     continue;
                  }
               }

               if (!entity.getPersistentData().contains("dmz_quest_key") || !hasQuestGuardianInRange(server, entity, playerUUID, playerUUID)) {
                  entity.discard();
               }
            }
         }
      }
   }

   private static boolean hasQuestGuardianInRange(MinecraftServer server, Entity questEntity, UUID ownerUUID, UUID excluded) {
      for (ServerPlayer guardian : questGuardians(server, ownerUUID)) {
         if ((excluded == null || !guardian.getUUID().equals(excluded))
            && guardian.level() == questEntity.level()
            && questEntity.distanceToSqr(guardian) <= 31250.0) {
            return true;
         }
      }

      return false;
   }

   private static List<ServerPlayer> questGuardians(MinecraftServer server, UUID ownerUUID) {
      List<ServerPlayer> guardians = new ArrayList<>();
      if (server != null && ownerUUID != null) {
         PartySavedData.PartyInstance party = PartySavedData.get(server).getPartyOf(ownerUUID);
         if (party != null) {
            for (UUID memberId : party.getMembers()) {
               ServerPlayer member = server.getPlayerList().getPlayer(memberId);
               if (member != null) {
                  guardians.add(member);
               }
            }

            return guardians;
         } else {
            ServerPlayer owner = server.getPlayerList().getPlayer(ownerUUID);
            if (owner != null) {
               guardians.add(owner);
            }

            return guardians;
         }
      } else {
         return guardians;
      }
   }

   @SubscribeEvent
   public static void onEntityInteract(EntityInteract event) {
      if (!event.getLevel().isClientSide && event.getTarget() instanceof MastersEntity master) {
         ServerPlayer player = (ServerPlayer)event.getEntity();
         StatsProvider.get(StatsCapability.INSTANCE, player)
            .ifPresent(
               data -> {
                  if (data.getSkills().getSkillLevel("instant_transmission") >= 1) {
                     String masterId = master.getStringUUID();
                     boolean alreadyKnown = data.getCharacter().getInteractedMasters().containsKey(masterId);
                     String dimId = player.level().dimension().location().toString();
                     data.getCharacter().addInteractedMaster(masterId, master.getName().getString(), dimId, master.blockPosition());
                     NetworkHandler.sendToTrackingEntityAndSelf(new AppearanceSyncS2C(player), player);
                     if (!alreadyKnown) {
                        player.displayClientMessage(
                           Component.translatable("message.dragonminez.instant_transmission.ki_learned", new Object[]{master.getName()}), false
                        );
                     }
                  }
               }
            );
      }
   }
}
