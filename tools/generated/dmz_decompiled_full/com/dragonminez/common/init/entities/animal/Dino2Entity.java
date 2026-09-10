package com.dragonminez.common.init.entities.animal;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier.Builder;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;

public class Dino2Entity extends DinoGlobalEntity {
   public Dino2Entity(EntityType<? extends Monster> pEntityType, Level pLevel) {
      super(pEntityType, pLevel);
   }

   public static Builder createAttributes() {
      return Monster.createMonsterAttributes()
         .add(Attributes.MAX_HEALTH, 200.0)
         .add(Attributes.MOVEMENT_SPEED, 0.2)
         .add(Attributes.ATTACK_DAMAGE, 20.0)
         .add(Attributes.KNOCKBACK_RESISTANCE, 0.6);
   }
}
