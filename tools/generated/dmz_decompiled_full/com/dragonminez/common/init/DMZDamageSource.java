package com.dragonminez.common.init;

import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

public class DMZDamageSource extends DamageSource {
   private static final int KI_VARIANTS = 2;
   private final String messageId;
   private final boolean randomVariant;
   private final Component techniqueName;

   public DMZDamageSource(Holder<DamageType> type, Entity directEntity, Entity causingEntity, String messageId, boolean randomVariant, Component techniqueName) {
      super(type, directEntity, causingEntity);
      this.messageId = messageId;
      this.randomVariant = randomVariant;
      this.techniqueName = techniqueName;
   }

   public Component getLocalizedDeathMessage(LivingEntity victim) {
      String key = "death.attack." + this.messageId;
      if (this.randomVariant) {
         key = key + "." + victim.getRandom().nextInt(2);
      }

      Entity attacker = this.getEntity() != null ? this.getEntity() : this.getDirectEntity();
      Component attackerName = (Component)(attacker != null ? attacker.getDisplayName() : Component.translatable("death.attack.dmz.unknown"));
      return this.techniqueName != null
         ? Component.translatable(key, new Object[]{victim.getDisplayName(), attackerName, this.techniqueName})
         : Component.translatable(key, new Object[]{victim.getDisplayName(), attackerName});
   }
}
