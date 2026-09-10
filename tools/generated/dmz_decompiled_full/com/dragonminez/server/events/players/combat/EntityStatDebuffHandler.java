package com.dragonminez.server.events.players.combat;

import com.dragonminez.common.init.MainDamageTypes;
import com.dragonminez.common.stats.character.EntityStatDebuffs;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.EventBusSubscriber.Bus;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent.Pre;

@EventBusSubscriber(
   modid = "dragonminez",
   bus = Bus.GAME
)
public class EntityStatDebuffHandler {
   @SubscribeEvent(
      priority = EventPriority.NORMAL
   )
   public static void onLivingHurt(Pre event) {
      if (!event.getEntity().level().isClientSide) {
         float amount = event.getNewDamage();
         DamageSource source = event.getSource();
         boolean isKi = MainDamageTypes.isKiblastDamage(source);
         if (source.getEntity() instanceof LivingEntity attacker && !(attacker instanceof Player)) {
            if (isKi) {
               amount *= (float)EntityStatDebuffs.getMultiplier(attacker, "PWR");
            } else if (source.getDirectEntity() == attacker) {
               amount *= (float)EntityStatDebuffs.getMultiplier(attacker, "STR");
            }
         }

         Entity victim = event.getEntity();
         if (victim instanceof LivingEntity living && !(victim instanceof Player)) {
            double defMult = EntityStatDebuffs.getMultiplier(living, "DEF");
            if (defMult < 1.0) {
               amount *= (float)(2.0 - defMult);
            }
         }

         if (amount != event.getNewDamage()) {
            event.setNewDamage(Math.max(0.0F, amount));
         }
      }
   }

   @SubscribeEvent(
      priority = EventPriority.NORMAL
   )
   public static void onLivingHeal(LivingHealEvent event) {
      if (!event.getEntity().level().isClientSide) {
         if (!(event.getEntity() instanceof Player)) {
            double mult = EntityStatDebuffs.getMultiplier(event.getEntity(), "HP_REGEN");
            if (mult < 1.0) {
               event.setAmount(Math.max(0.0F, (float)((double)event.getAmount() * mult)));
            }
         }
      }
   }
}
