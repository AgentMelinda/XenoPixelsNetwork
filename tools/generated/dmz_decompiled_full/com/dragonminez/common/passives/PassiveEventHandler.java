package com.dragonminez.common.passives;

import com.dragonminez.common.events.DMZEvent;
import com.dragonminez.common.quest.PartyManager;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.EventBusSubscriber.Bus;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent.Pre;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent.Post;

@EventBusSubscriber(
   modid = "dragonminez",
   bus = Bus.GAME
)
public class PassiveEventHandler {
   private static boolean redirecting = false;
   public static boolean suppressHealingBonus = false;

   @SubscribeEvent
   public static void onPlayerTick(Post event) {
      if (!event.getEntity().level().isClientSide) {
         if (event.getEntity() instanceof ServerPlayer player) {
            StatsData data = StatsProvider.get(StatsCapability.INSTANCE, player).orElse(null);
            if (data != null && data.getStatus().isHasCreatedCharacter()) {
               IClassPassive passive = ClassPassives.get(data);
               passive.onPlayerTick(player, data);
               if (player.tickCount % 20 == 0) {
                  passive.onPlayerSecond(player, data);
               }
            }
         }
      }
   }

   @SubscribeEvent
   public static void onHealthRegen(DMZEvent.HealthRegenEvent event) {
      StatsData data = event.getStatsData();
      IClassPassive p = ClassPassives.get(data);
      double amount = (event.getAmount() * p.healthRegenMultiplier(data) + p.bonusHpRegenFromStamina(data)) * p.healingReceivedMultiplier(data);
      event.setAmount(amount);
   }

   @SubscribeEvent
   public static void onStaminaRegen(DMZEvent.StaminaRegenEvent event) {
      StatsData data = event.getStatsData();
      event.setAmount(event.getAmount() * ClassPassives.get(data).staminaRegenMultiplier(data));
   }

   @SubscribeEvent
   public static void onCritChance(DMZEvent.CritChanceEvent event) {
      StatsData data = StatsProvider.get(StatsCapability.INSTANCE, event.getPlayer()).orElse(null);
      if (data != null) {
         event.setChance(event.getChance() + ClassPassives.get(data).critChanceBonus(data));
      }
   }

   @SubscribeEvent
   public static void onDamageModify(DMZEvent.DamageModifyEvent event) {
      StatsData data = StatsProvider.get(StatsCapability.INSTANCE, event.getAttacker()).orElse(null);
      if (data != null) {
         IClassPassive p = ClassPassives.get(data);
         double mult = event.getSourceType() == DMZEvent.DamageSourceType.STRIKE ? p.strikeDamageMultiplier(data, event.getVictim()) : 1.0;
         event.setAmount(event.getAmount() * mult);
         event.setDefensePenetration(event.getDefensePenetration() + p.armorPenBonus(data));
      }
   }

   @SubscribeEvent
   public static void onDamageDealt(DMZEvent.DamageDealtEvent event) {
      if (event.getSourceType() == DMZEvent.DamageSourceType.MELEE) {
         if (event.getAttacker() instanceof ServerPlayer attacker) {
            StatsData data = StatsProvider.get(StatsCapability.INSTANCE, attacker).orElse(null);
            if (data != null) {
               boolean blocked = event.isBlocked() || event.isParried();
               if (blocked) {
                  ClassPassives.get(data).onMeleeHit(attacker, data, event.getVictim(), true);
               } else if (event.getAmount() > 0.0) {
                  ClassPassives.get(data).onMeleeHit(attacker, data, event.getVictim(), false);
               }
            }
         }
      }
   }

   @SubscribeEvent
   public static void onKiAttackFire(DMZEvent.KiAttackFireEvent event) {
      StatsData data = event.getStatsData();
      double mult = ClassPassives.get(data).kiCooldownMultiplier(data, event.getKiAttack());
      event.setCooldownTicks((int)Math.max(1L, Math.round((double)event.getCooldownTicks() * mult)));
   }

   @SubscribeEvent(
      priority = EventPriority.LOWEST
   )
   public static void onLivingHurt(Pre event) {
      if (!event.getEntity().level().isClientSide) {
         if (event.getSource().getEntity() instanceof ServerPlayer attacker) {
            applyPaladinLifesteal(attacker, event.getEntity(), event.getNewDamage());
         }

         if (!redirecting && event.getEntity() instanceof ServerPlayer victim) {
            applyPaladinRedirect(victim, event);
         }
      }
   }

   private static void applyPaladinLifesteal(ServerPlayer member, LivingEntity victim, float damageDealt) {
      if (!(damageDealt <= 0.0F)) {
         if (!(damageDealt < victim.getMaxHealth() * 0.01F)) {
            ServerPlayer paladin = findPartyPaladin(member, null);
            if (paladin != null) {
               double pct = ClassPassives.value(paladinData(paladin), "lifestealPct", 0.15);
               if (pct > 0.0) {
                  paladin.heal((float)((double)damageDealt * pct));
               }
            }
         }
      }
   }

   private static void applyPaladinRedirect(ServerPlayer victim, Pre event) {
      boolean hasRaw = victim.getPersistentData().contains("dmz_raw_damage");
      double raw = hasRaw ? victim.getPersistentData().getDouble("dmz_raw_damage") : (double)event.getNewDamage();
      if (!(raw < (double)(victim.getMaxHealth() * 0.01F))) {
         ServerPlayer paladin = findPartyPaladin(victim, victim);
         if (paladin != null) {
            if (event.getSource().getEntity() != paladin) {
               double pct = ClassPassives.value(paladinData(paladin), "redirectPct", 0.15);
               if (!(pct <= 0.0)) {
                  double redirect = raw * pct;
                  if (!(redirect <= 0.0)) {
                     if (hasRaw) {
                        victim.getPersistentData().putDouble("dmz_raw_damage", Math.max(0.0, raw - redirect));
                     } else {
                        event.setNewDamage((float)Math.max(0.0, raw - redirect));
                     }

                     redirecting = true;

                     try {
                        paladin.hurt(paladin.damageSources().generic(), (float)redirect);
                     } finally {
                        redirecting = false;
                     }
                  }
               }
            }
         }
      }
   }

   private static StatsData paladinData(ServerPlayer paladin) {
      return StatsProvider.get(StatsCapability.INSTANCE, paladin).orElse(null);
   }

   private static ServerPlayer findPartyPaladin(ServerPlayer member, ServerPlayer exclude) {
      List<ServerPlayer> party = PartyManager.getAllPartyMembers(member);
      if (party != null && party.size() > 1) {
         for (ServerPlayer candidate : party) {
            if (candidate != exclude && candidate.isAlive() && candidate.level() == member.level() && !(candidate.distanceToSqr(member) > 2500.0)) {
               StatsData data = StatsProvider.get(StatsCapability.INSTANCE, candidate).orElse(null);
               if (data != null && ClassPassives.is(data, "paladin")) {
                  return candidate;
               }
            }
         }

         return null;
      } else {
         return null;
      }
   }

   @SubscribeEvent
   public static void onLivingHeal(LivingHealEvent event) {
      if (!event.getEntity().level().isClientSide) {
         if (!suppressHealingBonus) {
            if (event.getEntity() instanceof ServerPlayer player) {
               StatsData data = StatsProvider.get(StatsCapability.INSTANCE, player).orElse(null);
               if (data != null && data.getStatus().isHasCreatedCharacter()) {
                  double mult = ClassPassives.get(data).healingReceivedMultiplier(data);
                  if (mult != 1.0) {
                     event.setAmount((float)((double)event.getAmount() * mult));
                  }
               }
            }
         }
      }
   }

   @SubscribeEvent
   public static void onLogout(PlayerLoggedOutEvent event) {
      PassiveRuntimeState.clear(event.getEntity().getUUID());
   }

   @SubscribeEvent
   public static void onDeath(LivingDeathEvent event) {
      if (event.getEntity() instanceof Player player) {
         PassiveRuntimeState.clear(player.getUUID());
      }
   }
}
