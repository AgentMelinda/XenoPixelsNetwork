package com.dragonminez.client.systems.kisense;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.Map.Entry;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

public final class CombatIndicators {
   private static final float EPSILON = 0.05F;
   public static final long POPUP_LIFETIME = 30L;
   private static final long ACCUM_RESET = 40L;
   private static final Random RAND = new Random();
   private static final Map<Integer, CombatIndicators.Track> TRACKS = new HashMap<>();

   private CombatIndicators() {
   }

   public static void clear() {
      TRACKS.clear();
   }

   public static void tick() {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null) {
         TRACKS.clear();
      } else {
         long now = mc.level.getGameTime();
         Set<Integer> combat = KiSenseScan.getCombatEntities();

         for (int id : combat) {
            Entity entity = mc.level.getEntity(id);
            if (entity instanceof LivingEntity) {
               LivingEntity living = (LivingEntity)entity;
               CombatIndicators.Track track = TRACKS.computeIfAbsent(id, k -> new CombatIndicators.Track());
               float hp = living.getHealth();
               if (track.initialized) {
                  float delta = hp - track.lastHp;
                  if (delta < -0.05F) {
                     registerChange(track, -delta, false, now);
                  } else if (delta > 0.05F) {
                     registerChange(track, delta, true, now);
                  }
               }

               track.lastHp = hp;
               track.initialized = true;
            }
         }

         Iterator<Entry<Integer, CombatIndicators.Track>> it = TRACKS.entrySet().iterator();

         while (it.hasNext()) {
            Entry<Integer, CombatIndicators.Track> entry = it.next();
            CombatIndicators.Track track = entry.getValue();
            if (now - track.damageTick > 40L) {
               track.accumDamage = 0.0F;
            }

            if (now - track.healTick > 40L) {
               track.accumHeal = 0.0F;
            }

            track.popups.removeIf(p -> now - p.bornTick() > 30L);
            boolean stale = !combat.contains(entry.getKey());
            boolean empty = track.accumDamage <= 0.0F && track.accumHeal <= 0.0F && track.popups.isEmpty();
            if (stale && empty) {
               it.remove();
            }
         }
      }
   }

   private static void registerChange(CombatIndicators.Track track, float amount, boolean heal, long now) {
      if (heal) {
         if (track.accumHeal <= 0.0F) {
            track.healOffX = randX();
            track.healOffY = randY();
         }

         track.accumHeal += amount;
         track.healTick = now;
      } else {
         if (track.accumDamage <= 0.0F) {
            track.damageOffX = randX();
            track.damageOffY = randY();
         }

         track.accumDamage += amount;
         track.damageTick = now;
      }

      track.popups.add(new CombatIndicators.DamagePopup(amount, heal, now, randX(), randY()));
   }

   private static float randX() {
      return (RAND.nextFloat() - 0.5F) * 26.0F;
   }

   private static float randY() {
      return -14.0F - RAND.nextFloat() * 12.0F;
   }

   public static float getAccumDamage(int id) {
      CombatIndicators.Track t = TRACKS.get(id);
      return t != null ? t.accumDamage : 0.0F;
   }

   public static float getAccumHeal(int id) {
      CombatIndicators.Track t = TRACKS.get(id);
      return t != null ? t.accumHeal : 0.0F;
   }

   public static long getDamageTick(int id) {
      CombatIndicators.Track t = TRACKS.get(id);
      return t != null ? t.damageTick : 0L;
   }

   public static long getHealTick(int id) {
      CombatIndicators.Track t = TRACKS.get(id);
      return t != null ? t.healTick : 0L;
   }

   public static float getDamageOffX(int id) {
      CombatIndicators.Track t = TRACKS.get(id);
      return t != null ? t.damageOffX : 0.0F;
   }

   public static float getDamageOffY(int id) {
      CombatIndicators.Track t = TRACKS.get(id);
      return t != null ? t.damageOffY : 0.0F;
   }

   public static float getHealOffX(int id) {
      CombatIndicators.Track t = TRACKS.get(id);
      return t != null ? t.healOffX : 0.0F;
   }

   public static float getHealOffY(int id) {
      CombatIndicators.Track t = TRACKS.get(id);
      return t != null ? t.healOffY : 0.0F;
   }

   public static List<CombatIndicators.DamagePopup> getPopups(int id) {
      CombatIndicators.Track t = TRACKS.get(id);
      return t != null ? t.popups : List.of();
   }

   public static record DamagePopup(float value, boolean heal, long bornTick, float offX, float offY) {
   }

   private static final class Track {
      boolean initialized;
      float lastHp;
      float accumDamage;
      float accumHeal;
      long damageTick;
      long healTick;
      float damageOffX;
      float damageOffY;
      float healOffX;
      float healOffY;
      final List<CombatIndicators.DamagePopup> popups = new ArrayList<>();
   }
}
