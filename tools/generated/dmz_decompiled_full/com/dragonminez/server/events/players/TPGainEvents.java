package com.dragonminez.server.events.players;

import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.config.GeneralServerConfig;
import com.dragonminez.common.config.TpSource;
import com.dragonminez.common.events.DMZEvent;
import com.dragonminez.common.init.entities.ShadowDummyEntity;
import com.dragonminez.common.init.entities.ki.AbstractKiProjectile;
import com.dragonminez.common.network.NetworkHandler;
import com.dragonminez.common.network.S2C.ResourceSyncS2C;
import com.dragonminez.common.quest.PartyManager;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.common.stats.extras.DynamicGrowthStat;
import com.dragonminez.server.dynamicgrowth.DynamicGrowthService;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.FlyingMob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent.Pre;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.ItemCraftedEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent;
import net.neoforged.neoforge.event.level.BlockEvent.BreakEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent.Post;

@EventBusSubscriber(
   modid = "dragonminez"
)
public class TPGainEvents {
   private static final Map<UUID, Vec3> lastPositions = new HashMap<>();
   private static final Map<UUID, Double> accumulatedDistance = new HashMap<>();
   private static final ThreadLocal<Boolean> IS_SHARING_TP = ThreadLocal.withInitial(() -> false);

   @SubscribeEvent(
      priority = EventPriority.HIGH
   )
   public static void onTPGain(DMZEvent.TPGainEvent event) {
      if (event.getPlayer() instanceof ServerPlayer player) {
         int baseTP = event.getTpGain();
         if (baseTP > 0) {
            if (!IS_SHARING_TP.get()) {
               StatsData data = StatsProvider.get(StatsCapability.INSTANCE, player).orElse(null);
               if (data != null) {
                  int finalTP = data.calculateTPGain(baseTP, TpSource.STORY);
                  if (event.getShareWithParty()) {
                     IS_SHARING_TP.set(true);

                     try {
                        if (data.getStatus().isFused() && data.getStatus().isFusionLeader()) {
                           shareWithFusionPartner(player, data, finalTP);
                        }

                        shareWithParty(player, data, finalTP);
                     } finally {
                        IS_SHARING_TP.set(false);
                     }
                  }

                  event.setTpGain(finalTP);
               }
            }
         }
      }
   }

   @SubscribeEvent
   public static void onPlayerTick(Post event) {
      if (event.getEntity() instanceof ServerPlayer player) {
         StatsProvider.get(StatsCapability.INSTANCE, player).ifPresent(data -> {
            if (data.getStatus().isHasCreatedCharacter()) {
               int passiveTp = ConfigManager.getServerConfig().getGameplay().getPassiveTpGain();
               if (passiveTp > 0 && player.tickCount % 100 == 0) {
                  int boosted = data.applyTpBoosts(TpSource.PASSIVE, passiveTp);
                  data.getResources().addTrainingPoints((float)boosted);
               }

               int tpTravel = ConfigManager.getServerConfig().getGameplay().getTpPer20BlocksTraveled();
               if (tpTravel > 0) {
                  UUID uuid = player.getUUID();
                  Vec3 currentPos = player.position();
                  Vec3 lastPos = lastPositions.getOrDefault(uuid, currentPos);
                  double dist = currentPos.distanceTo(lastPos);
                  if (dist > 0.0 && dist < 10.0) {
                     double accum = accumulatedDistance.getOrDefault(uuid, 0.0) + dist;
                     if (accum >= 20.0) {
                        int times = (int)(accum / 20.0);
                        data.getResources().addTrainingPoints((float)data.applyTpBoosts(TpSource.TRAVEL, tpTravel * times));
                        accum %= 20.0;
                     }

                     accumulatedDistance.put(uuid, accum);
                  }

                  lastPositions.put(uuid, currentPos);
               }
            }
         });
      }
   }

   @SubscribeEvent
   public static void onPlayerLoggedOut(PlayerLoggedOutEvent event) {
      lastPositions.remove(event.getEntity().getUUID());
      accumulatedDistance.remove(event.getEntity().getUUID());
   }

   @SubscribeEvent(
      priority = EventPriority.LOW
   )
   public static void onBlockBreak(BreakEvent event) {
      if (event.getPlayer() instanceof ServerPlayer player && !event.isCanceled()) {
         StatsProvider.get(StatsCapability.INSTANCE, player).ifPresent(data -> {
            if (data.getStatus().isHasCreatedCharacter()) {
               int baseTp = ConfigManager.getServerConfig().getGameplay().getTpPerBlockMined();
               if (baseTp > 0) {
                  data.getResources().addTrainingPoints((float)data.applyTpBoosts(TpSource.MINED, baseTp));
               }
            }
         });
      }
   }

   @SubscribeEvent
   public static void onItemCrafted(ItemCraftedEvent event) {
      if (event.getEntity() instanceof ServerPlayer player) {
         StatsProvider.get(StatsCapability.INSTANCE, player).ifPresent(data -> {
            if (data.getStatus().isHasCreatedCharacter()) {
               int baseTp = ConfigManager.getServerConfig().getGameplay().getTpPerItemCrafted();
               if (baseTp > 0) {
                  ItemStack stack = event.getCrafting();
                  int amount = stack.getCount();
                  int rarityMult = stack.getRarity().ordinal() + 1;
                  int tierMult = 1;
                  if (stack.getItem() instanceof TieredItem tiered) {
                     tierMult = Math.max(1, (int)(tiered.getTier().getSpeed() / 2.0F));
                  } else if (stack.getItem() instanceof ArmorItem armor) {
                     tierMult = Math.max(1, armor.getDefense() / 4);
                  }

                  int finalMult = Math.max(rarityMult, tierMult);
                  data.getResources().addTrainingPoints((float)data.applyTpBoosts(TpSource.CRAFTED, baseTp * amount * finalMult));
               }
            }
         });
      }
   }

   private static boolean isPlayerOwnedShadow(Entity entity) {
      return entity instanceof ShadowDummyEntity && entity.getPersistentData().getBoolean("dmz_player_shadow");
   }

   private static int applyPlayerShadowTpBonus(Entity entity, int tp) {
      if (tp > 0 && isPlayerOwnedShadow(entity)) {
         int pct = Mth.clamp(entity.getPersistentData().getInt("dmz_shadow_percent"), 25, 75);
         return Math.max(1, (int)Math.round((double)tp * (1.0 + (double)pct / 100.0)));
      } else {
         return tp;
      }
   }

   private static boolean dropTps(Entity entity) {
      List<Class<?>> enemyList = List.of(Monster.class, Animal.class, Player.class, FlyingMob.class, Mob.class);
      return enemyList.stream().anyMatch(clase -> clase.isInstance(entity));
   }

   @SubscribeEvent
   public static void onEntityDeath(LivingDeathEvent event) {
      if (!event.getEntity().level().isClientSide) {
         if (event.getSource().getEntity() instanceof Player attacker) {
            StatsProvider.get(StatsCapability.INSTANCE, attacker).ifPresent(data -> {
               if (dropTps(event.getEntity())) {
                  double ratio = ConfigManager.getServerConfig().getGameplay().getTpHealthRatio();
                  double healthMult = event.getEntity() instanceof ShadowDummyEntity && !isPlayerOwnedShadow(event.getEntity()) ? 0.5 : 1.0;
                  int tpsHealth = (int)Math.round((double)event.getEntity().getMaxHealth() * ratio * healthMult);
                  int killTp = applyDynamicGrowthCombatTpMult(ConfigManager.getServerConfig().getGameplay().getTpPerHit() + tpsHealth);
                  int boostedTp = data.applyTpBoosts(TpSource.KILL, killTp);
                  int finalTp = applyPlayerShadowTpBonus(event.getEntity(), boostedTp);
                  data.getResources().addTrainingPoints((float)finalTp);
               }
            });
         }
      }
   }

   @SubscribeEvent(
      priority = EventPriority.LOW
   )
   public static void onEntityHit(Pre event) {
      if (!event.getEntity().level().isClientSide) {
         if (event.getSource().getEntity() instanceof Player attacker) {
            StatsProvider.get(StatsCapability.INSTANCE, attacker).ifPresent(attackerData -> {
               if (attackerData.getStatus().isHasCreatedCharacter() && event.getNewDamage() >= 1.0F) {
                  int baseTps = applyDynamicGrowthCombatTpMult(ConfigManager.getServerConfig().getGameplay().getTpPerHit());
                  int boostedTps = attackerData.applyTpBoosts(TpSource.HIT, baseTps);
                  int finalTps = applyPlayerShadowTpBonus(event.getEntity(), boostedTps);
                  attackerData.getResources().addTrainingPoints((float)finalTps);
               }
            });
         }
      }
   }

   @SubscribeEvent(
      priority = EventPriority.LOWEST
   )
   public static void onLivingHurtDynamicGrowth(Pre event) {
      if (!event.getEntity().level().isClientSide) {
         if (ConfigManager.getServerConfig().getDynamicGrowth().isEnabled()) {
            LivingEntity target = event.getEntity();
            DamageSource source = event.getSource();
            Entity sourceEntity = source.getEntity();
            Entity directEntity = source.getDirectEntity();
            float damage = event.getNewDamage();
            if (!(damage <= 0.0F)) {
               if (!isPlayerOwnedShadow(target) && !isPlayerOwnedShadow(sourceEntity)) {
                  if (sourceEntity instanceof ServerPlayer attacker && !attacker.is(target)) {
                     StatsProvider.get(StatsCapability.INSTANCE, attacker).ifPresent(attackerData -> {
                        DynamicGrowthService.markCombat(attackerData);
                        if (isKiDamage(source, directEntity)) {
                           double damageXp = DynamicGrowthService.practiceDamageXp(attacker, target, damage);
                           DynamicGrowthService.award(attacker, attackerData, DynamicGrowthStat.PWR, damageXp, target);
                        } else if (isPlayerMeleeDamage(source, attacker)) {
                           CompoundTag pdata = attacker.getPersistentData();
                           double growthDamage = pdata.contains("dmz_growth_melee_damage") ? pdata.getDouble("dmz_growth_melee_damage") : (double)damage;
                           boolean kiWeaponInUse = pdata.getBoolean("dmz_growth_ki_weapon");
                           boolean kiInfuseActive = pdata.getBoolean("dmz_growth_ki_infuse");
                           pdata.remove("dmz_growth_melee_damage");
                           pdata.remove("dmz_growth_ki_weapon");
                           pdata.remove("dmz_growth_ki_infuse");
                           double growthXp = DynamicGrowthService.practiceDamageXp(attacker, target, (float)growthDamage);
                           if (kiWeaponInUse) {
                              DynamicGrowthService.award(attacker, attackerData, DynamicGrowthStat.PWR, growthXp, target);
                           } else if (kiInfuseActive) {
                              DynamicGrowthService.award(attacker, attackerData, DynamicGrowthStat.STR, growthXp, target);
                              double pwrShare = ConfigManager.getServerConfig().getDynamicGrowth().getKiWeaponMeleePwrShare();
                              DynamicGrowthService.award(attacker, attackerData, DynamicGrowthStat.PWR, growthXp * pwrShare, target);
                           } else {
                              DynamicGrowthService.award(attacker, attackerData, DynamicGrowthStat.STR, growthXp, target);
                           }
                        }
                     });
                  }

                  if (target instanceof ServerPlayer victim && sourceEntity instanceof LivingEntity attacker && !sourceEntity.is(victim)) {
                     StatsProvider.get(StatsCapability.INSTANCE, victim).ifPresent(victimData -> {
                        DynamicGrowthService.markCombat(victimData);
                        double vitalityXp = DynamicGrowthService.practiceDamageXp(victim, attacker, damage);
                        DynamicGrowthService.award(victim, victimData, DynamicGrowthStat.VIT, vitalityXp, attacker);
                     });
                  }
               }
            }
         }
      }
   }

   private static boolean isPlayerMeleeDamage(DamageSource source, ServerPlayer attacker) {
      return "player".equals(source.getMsgId()) && (source.getDirectEntity() == null || source.getDirectEntity().is(attacker));
   }

   private static boolean isKiDamage(DamageSource source, Entity directEntity) {
      return directEntity instanceof AbstractKiProjectile ? true : source.getMsgId().toLowerCase(Locale.ROOT).contains("kiblast");
   }

   private static int applyDynamicGrowthCombatTpMult(int tp) {
      if (tp <= 0) {
         return tp;
      } else {
         GeneralServerConfig.DynamicGrowthConfig dynamicGrowth = ConfigManager.getServerConfig().getDynamicGrowth();
         if (!dynamicGrowth.isEnabled()) {
            return tp;
         } else {
            double mult = dynamicGrowth.getNaturalCombatTpMultiplier();
            if (mult == 1.0) {
               return tp;
            } else {
               return mult <= 0.0 ? 0 : Math.max(1, (int)Math.round((double)tp * mult));
            }
         }
      }
   }

   private static void shareWithFusionPartner(ServerPlayer leader, StatsData leaderData, int totalTP) {
      UUID partnerUUID = leaderData.getStatus().getFusionPartnerUUID();
      if (partnerUUID != null) {
         ServerPlayer partner = leader.getServer().getPlayerList().getPlayer(partnerUUID);
         if (partner != null) {
            StatsProvider.get(StatsCapability.INSTANCE, partner).ifPresent(pData -> {
               pData.getResources().addTrainingPoints((float)(totalTP / 2));
               NetworkHandler.sendToTrackingEntityAndSelf(new ResourceSyncS2C(partner), partner);
            });
         }
      }
   }

   private static void shareWithParty(ServerPlayer earner, StatsData data, int tp) {
      if (data.getPlayerQuestData().isInParty()) {
         double shareRatio = ConfigManager.getServerConfig().getGameplay().getPartyTpShareRatio();
         if (!(shareRatio <= 0.0)) {
            int sharedTP = (int)((double)tp * shareRatio);
            if (sharedTP > 0) {
               for (ServerPlayer member : PartyManager.getAllPartyMembers(earner)) {
                  if (!member.getUUID().equals(earner.getUUID())) {
                     StatsProvider.get(StatsCapability.INSTANCE, member).ifPresent(pData -> {
                        if (pData.getStatus().isAlive()) {
                           pData.getResources().addTrainingPoints((float)sharedTP);
                           NetworkHandler.sendToTrackingEntityAndSelf(new ResourceSyncS2C(member), member);
                        }
                     });
                  }
               }
            }
         }
      }
   }
}
