package com.dragonminez.common.stats.character;

import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.config.GeneralServerConfig;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.FriendlyByteBuf;

public class Effects {
   private final Map<String, Effect> effectMap = new HashMap<>();

   public void addEffect(String name, double power, int duration) {
      this.effectMap.put(name.toLowerCase(), new Effect(name.toLowerCase(), power, duration));
   }

   public void removeEffect(String name) {
      this.effectMap.remove(name.toLowerCase());
   }

   public void removeAllEffects() {
      Effect mutant = null;
      if (this.effectMap.containsKey("mutant")) {
         GeneralServerConfig serverConfig = ConfigManager.getServerConfig();
         if (serverConfig != null && serverConfig.getMutant().getKeepMutantOnDeath()) {
            mutant = this.effectMap.get("mutant");
         }
      }

      this.effectMap.clear();
      if (mutant != null) {
         this.effectMap.put("mutant", mutant);
      }
   }

   public boolean hasEffect(String name) {
      return this.effectMap.containsKey(name.toLowerCase());
   }

   public Effect getEffect(String name) {
      return this.effectMap.get(name.toLowerCase());
   }

   public double getEffectPower(String name) {
      Effect effect = this.effectMap.get(name.toLowerCase());
      return effect != null ? effect.getPower() : 0.0;
   }

   public int getEffectDuration(String name) {
      Effect effect = this.effectMap.get(name.toLowerCase());
      return effect != null ? effect.getDuration() : 0;
   }

   public List<Effect> getAllEffects() {
      return new ArrayList<>(this.effectMap.values());
   }

   public List<Effect> getEffectsSortedByDuration() {
      return this.effectMap.values().stream().sorted(Comparator.comparingInt(Effect::getDuration).reversed()).collect(Collectors.toList());
   }

   public double getTotalEffectMultiplier() {
      if (this.effectMap.isEmpty()) {
         return 1.0;
      } else {
         boolean isMultiplicative = ConfigManager.getServerConfig().getGameplay().getMultiplicationInsteadOfAdditionForMultipliers();
         double totalMultiplier = 1.0;

         for (Effect effect : this.effectMap.values()) {
            if (isMultiplicative) {
               totalMultiplier *= effect.getPower();
            } else {
               totalMultiplier += effect.getPower() - 1.0;
            }
         }

         return totalMultiplier;
      }
   }

   public void tick() {
      List<String> toRemove = new ArrayList<>();

      for (Entry<String, Effect> entry : this.effectMap.entrySet()) {
         Effect effect = entry.getValue();
         if (!effect.isPermanent()) {
            effect.tick();
            if (effect.isExpired()) {
               toRemove.add(entry.getKey());
            }
         }
      }

      for (String name : toRemove) {
         this.effectMap.remove(name);
      }
   }

   public void clear() {
      this.effectMap.clear();
   }

   public CompoundTag save() {
      CompoundTag nbt = new CompoundTag();
      ListTag effectsList = new ListTag();

      for (Effect effect : this.effectMap.values()) {
         effectsList.add(effect.save());
      }

      nbt.put("EffectsList", effectsList);
      return nbt;
   }

   public void load(CompoundTag nbt) {
      this.effectMap.clear();
      if (nbt.contains("EffectsList", 9)) {
         ListTag effectsList = nbt.getList("EffectsList", 10);

         for (int i = 0; i < effectsList.size(); i++) {
            CompoundTag effectTag = effectsList.getCompound(i);
            Effect effect = Effect.load(effectTag);
            this.effectMap.put(effect.getName().toLowerCase(), effect);
         }
      }
   }

   public void toBytes(FriendlyByteBuf buf) {
      buf.writeInt(this.effectMap.size());

      for (Effect effect : this.effectMap.values()) {
         effect.toBytes(buf);
      }
   }

   public void fromBytes(FriendlyByteBuf buf) {
      this.effectMap.clear();
      int size = buf.readInt();

      for (int i = 0; i < size; i++) {
         Effect effect = Effect.fromBytes(buf);
         this.effectMap.put(effect.getName().toLowerCase(), effect);
      }
   }

   public void copyFrom(Effects other) {
      this.effectMap.clear();

      for (Entry<String, Effect> entry : other.effectMap.entrySet()) {
         this.effectMap.put(entry.getKey(), entry.getValue().copy());
      }
   }
}
