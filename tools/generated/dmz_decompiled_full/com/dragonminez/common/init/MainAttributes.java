package com.dragonminez.common.init;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class MainAttributes {
   public static final DeferredRegister<Attribute> ATTRIBUTES = DeferredRegister.create(BuiltInRegistries.ATTRIBUTE, "dragonminez");
   public static final DeferredHolder<Attribute, Attribute> STRENGTH = registerMainStat("strength", "attribute.dragonminez.strength", 0.0);
   public static final DeferredHolder<Attribute, Attribute> STRIKE_POWER = registerMainStat("strike_power", "attribute.dragonminez.strike_power", 0.0);
   public static final DeferredHolder<Attribute, Attribute> RESISTANCE = registerMainStat("resistance", "attribute.dragonminez.resistance", 0.0);
   public static final DeferredHolder<Attribute, Attribute> VITALITY = registerMainStat("vitality", "attribute.dragonminez.vitality", 0.0);
   public static final DeferredHolder<Attribute, Attribute> KI_POWER = registerMainStat("ki_power", "attribute.dragonminez.ki_power", 0.0);
   public static final DeferredHolder<Attribute, Attribute> ENERGY = registerMainStat("energy", "attribute.dragonminez.energy", 0.0);
   public static final DeferredHolder<Attribute, Attribute> MAX_ENERGY = register("max_energy", "attribute.dragonminez.max_energy", 20.0, 0.0, 2.0E9);
   public static final DeferredHolder<Attribute, Attribute> MAX_STAMINA = register("max_stamina", "attribute.dragonminez.max_stamina", 20.0, 0.0, 2.0E9);
   public static final DeferredHolder<Attribute, Attribute> MAX_POISE = register("max_poise", "attribute.dragonminez.max_poise", 25.0, 0.0, 2.0E9);
   public static final DeferredHolder<Attribute, Attribute> MELEE_DAMAGE = register("melee_damage", "attribute.dragonminez.melee_damage", 1.0, 0.0, 2.0E9);
   public static final DeferredHolder<Attribute, Attribute> STRIKE_DAMAGE = register("strike_damage", "attribute.dragonminez.strike_damage", 1.0, 0.0, 2.0E9);
   public static final DeferredHolder<Attribute, Attribute> KI_DAMAGE = register("ki_damage", "attribute.dragonminez.ki_damage", 0.0, 0.0, 2.0E9);
   public static final DeferredHolder<Attribute, Attribute> DEFENSE = register("defense", "attribute.dragonminez.defense", 0.0, 0.0, 2.0E9);
   public static final DeferredHolder<Attribute, Attribute> CRIT_CHANCE = register("crit_chance", "attribute.dragonminez.critical_chance", 0.05, 0.0, 1.0);
   public static final DeferredHolder<Attribute, Attribute> CRIT_DAMAGE = register("crit_damage", "attribute.dragonminez.critical_damage", 1.5, 1.0, 100.0);

   private static DeferredHolder<Attribute, Attribute> registerMainStat(String registryName, String translationKey, double defaultValue) {
      return register(registryName, translationKey, defaultValue, 0.0, 2.147483647E9);
   }

   private static DeferredHolder<Attribute, Attribute> register(String registryName, String translationKey, double defaultValue, double min, double max) {
      return ATTRIBUTES.register(registryName, () -> new RangedAttribute(translationKey, defaultValue, min, max).setSyncable(true));
   }
}
