package com.dragonminez.common.util.types.items;

import com.google.gson.GsonBuilder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import net.minecraft.core.Holder;
import net.minecraft.core.Holder.Reference;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;

public class PotionDTO extends GenericItemDTO {
   protected String potion;
   protected Map<String, Integer> mobEffects = new HashMap<>();

   public PotionDTO(String potion, Map<String, Integer> mobEffects) {
      super("potion", "minecraft:potion", 1);
      this.potion = potion;
      this.mobEffects = mobEffects;
   }

   public PotionDTO(String itemType, String itemId, int count, String potion, Map<String, Integer> mobEffects) {
      super(itemType, itemId, count);
      this.potion = potion;
      this.mobEffects = mobEffects;
   }

   @Override
   public ItemStack getItemStack() {
      ItemStack itemStack = super.getItemStack();
      Holder<Potion> potionHolder = Potions.WATER;
      if (this.potion != null && !this.potion.isBlank()) {
         ResourceLocation id = ResourceLocation.tryParse(this.potion.contains(":") ? this.potion : "minecraft:" + this.potion);
         if (id != null) {
            Optional<Reference<Potion>> registered = BuiltInRegistries.POTION.getHolder(id);
            if (registered.isPresent()) {
               potionHolder = (Holder<Potion>)registered.get();
            }
         }
      }

      List<MobEffectInstance> customs = new ArrayList<>(this.getMobEffects());
      itemStack.set(DataComponents.POTION_CONTENTS, new PotionContents(Optional.of(potionHolder), Optional.empty(), customs));
      return itemStack;
   }

   @Override
   public String toJson() {
      return new GsonBuilder().setPrettyPrinting().create().toJson(this, PotionDTO.class);
   }

   protected Collection<MobEffectInstance> getMobEffects() {
      return this.mobEffects.keySet().stream().map(key -> this.getMobEffect(key, this.mobEffects.get(key))).filter(e -> e != null).collect(Collectors.toList());
   }

   protected MobEffectInstance getMobEffect(String id, Integer amplifier) {
      ResourceLocation resourceLocation = ResourceLocation.tryParse(id);
      if (resourceLocation != null) {
         Optional<Reference<MobEffect>> effect = BuiltInRegistries.MOB_EFFECT.getHolder(resourceLocation);
         return effect.isPresent() ? new MobEffectInstance((Holder)effect.get(), 1, amplifier == null ? 0 : amplifier) : null;
      } else {
         return null;
      }
   }

   public String getPotion() {
      return this.potion;
   }

   public void setPotion(String potion) {
      this.potion = potion;
   }

   public void setMobEffects(Map<String, Integer> mobEffects) {
      this.mobEffects = mobEffects;
   }

   public PotionDTO() {
   }
}
