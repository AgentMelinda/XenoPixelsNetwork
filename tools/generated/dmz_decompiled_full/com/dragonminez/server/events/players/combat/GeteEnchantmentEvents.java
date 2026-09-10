package com.dragonminez.server.events.players.combat;

import com.dragonminez.common.init.MainEnchants;
import com.dragonminez.server.events.players.TickHandler;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;

@EventBusSubscriber(
   modid = "dragonminez"
)
public class GeteEnchantmentEvents {
   @SubscribeEvent
   public static void onKnockback(LivingKnockBackEvent event) {
      int level = TickHandler.getTotalArmorEnchantmentLevel(MainEnchants.GRAVITY_FORGED, event.getEntity());
      if (level > 0) {
         double factor = Math.max(0.4, 1.0 - (double)level * 0.15);
         event.setStrength((float)((double)event.getStrength() * factor));
      }
   }
}
