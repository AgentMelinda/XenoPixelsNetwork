package com.dragonminez.common.init.entities;

import com.dragonminez.common.init.EntityAttributes;
import com.dragonminez.common.init.MainAttributes;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.Holder.Reference;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.neoforged.fml.ModList;

public final class MobBattlePowerHelper {
   private static final double ATTACK_WEIGHT = 5.0;
   private static final double ARMOR_WEIGHT = 5.0;
   private static final double RANGED_WEIGHT = 5.0;
   private static final double MOVEMENT_WEIGHT = 15.0;
   private static final ResourceLocation AUTOLEVELING_PROJECTILE_DAMAGE = ResourceLocation.fromNamespaceAndPath(
      "autoleveling", "monster.projectile_damage_bonus"
   );
   private static final ResourceLocation AUTOLEVELING_EXPLOSION_DAMAGE = ResourceLocation.fromNamespaceAndPath("autoleveling", "monster.explosion_damage_bonus");

   private MobBattlePowerHelper() {
   }

   public static boolean isDmzManaged(LivingEntity entity) {
      if (entity instanceof Player) {
         return true;
      } else {
         ResourceLocation key = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
         return key != null && "dragonminez".equals(key.getNamespace());
      }
   }

   public static int calculate(LivingEntity entity) {
      double maxHealth = attributeValue(entity, Attributes.MAX_HEALTH);
      double attackDamage = attributeValue(entity, Attributes.ATTACK_DAMAGE);
      double armor = attributeValue(entity, Attributes.ARMOR);
      double armorToughness = attributeValue(entity, Attributes.ARMOR_TOUGHNESS);
      double movementSpeed = movementOrFlyingSpeed(entity);
      double kiPower = positivePower(entity, MainAttributes.KI_DAMAGE);
      if (kiPower <= 0.0) {
         kiPower = positivePower(entity, EntityAttributes.KI_BLAST_DAMAGE);
      }

      double projectileDamage = autoLevelingBonus(entity, AUTOLEVELING_PROJECTILE_DAMAGE);
      double explosionDamage = autoLevelingBonus(entity, AUTOLEVELING_EXPLOSION_DAMAGE);
      double rangedPower = kiPower + projectileDamage + explosionDamage;
      double battlePower = maxHealth + attackDamage * 5.0 + (armor + armorToughness) * 5.0 + rangedPower * 5.0 + movementSpeed * 15.0;
      if (!Double.isFinite(battlePower) || battlePower < 5.0) {
         return 5;
      } else {
         return battlePower >= 2.147483647E9 ? Integer.MAX_VALUE : (int)Math.max(5L, Math.round(battlePower));
      }
   }

   private static double movementOrFlyingSpeed(LivingEntity entity) {
      AttributeInstance movement = entity.getAttribute(Attributes.MOVEMENT_SPEED);
      return movement != null ? sanitize(movement.getValue()) : attributeValue(entity, EntityAttributes.FLY_SPEED);
   }

   private static double autoLevelingBonus(LivingEntity entity, ResourceLocation id) {
      if (!ModList.get().isLoaded("autoleveling")) {
         return 0.0;
      } else {
         Optional<Reference<Attribute>> holder = BuiltInRegistries.ATTRIBUTE.getHolder(id);
         if (holder.isEmpty()) {
            return 0.0;
         } else {
            AttributeInstance instance = entity.getAttribute((Holder)holder.get());
            return instance == null ? 0.0 : Math.max(0.0, sanitize(instance.getValue()) - 1.0);
         }
      }
   }

   private static double positivePower(LivingEntity entity, Holder<Attribute> attribute) {
      AttributeInstance instance = entity.getAttribute(attribute);
      return instance == null ? 0.0 : Math.max(0.0, sanitize(instance.getValue()));
   }

   private static double attributeValue(LivingEntity entity, Holder<Attribute> attribute) {
      AttributeInstance instance = entity.getAttribute(attribute);
      return instance == null ? 0.0 : sanitize(instance.getValue());
   }

   private static double sanitize(double value) {
      return Double.isFinite(value) ? value : 0.0;
   }
}
