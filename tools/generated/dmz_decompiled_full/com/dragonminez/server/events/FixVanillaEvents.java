package com.dragonminez.server.events;

import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent.Post;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent.Pre;

public class FixVanillaEvents {
   @SubscribeEvent
   public void onLivingHurt(Pre e) {
      float dmg = e.getNewDamage();
      LivingEntity le = e.getEntity();
      if (Float.isNaN(dmg)) {
         e.setNewDamage(0.0F);
         this.rectify(le);
      }
   }

   @SubscribeEvent
   public void onLivingDamage(Post e) {
      LivingEntity le = e.getEntity();
      float dmg = e.getNewDamage();
      if (Float.isNaN(dmg)) {
         this.rectify(le);
      }
   }

   @SubscribeEvent
   public void onAttackEntity(LivingIncomingDamageEvent e) {
      LivingEntity le = e.getEntity();
      float dmg = e.getAmount();
      if (Float.isNaN(dmg)) {
         e.setCanceled(true);
         this.rectify(le);
      }
   }

   @SubscribeEvent
   public void onLivingHeal(LivingHealEvent e) {
      float amount = e.getAmount();
      LivingEntity le = e.getEntity();
      if (Float.isNaN(amount)) {
         e.setCanceled(true);
      } else {
         if (Float.isNaN(le.getHealth())) {
            e.setCanceled(true);
            this.rectify(le);
         }
      }
   }

   @SubscribeEvent
   public void onLivingDeath(LivingDeathEvent e) {
      LivingEntity le = e.getEntity();
      float hp = le.getHealth();
      if (Float.isNaN(hp)) {
         e.setCanceled(true);
         this.rectify(le);
      }
   }

   private void rectify(LivingEntity le) {
      le.setHealth(le.getMaxHealth());
      le.setAbsorptionAmount(0.0F);
   }
}
