package com.dragonminez.common.stats.character;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.FriendlyByteBuf;

public class SecondaryStatEffects {
   public static final String STR = "STR";
   public static final String SKP = "SKP";
   public static final String DEF = "DEF";
   public static final String PWR = "PWR";
   public static final String STM_REGEN = "STM_REGEN";
   public static final String HP_REGEN = "HP_REGEN";
   public static final String ENE_REGEN = "ENE_REGEN";
   private final Map<String, SecondaryStatEffects.Mod> mods = new HashMap<>();

   public void apply(String stat, double factor, int durationTicks) {
      if (stat != null && durationTicks > 0 && factor != 0.0) {
         String key = stat.toUpperCase();
         SecondaryStatEffects.Mod existing = this.mods.get(key);
         if (existing == null) {
            this.mods.put(key, new SecondaryStatEffects.Mod(key, factor, durationTicks));
         } else {
            if (Math.abs(factor) >= Math.abs(existing.factor)) {
               existing.factor = factor;
            }

            existing.duration = Math.max(existing.duration, durationTicks);
         }
      }
   }

   public double getMultiplier(String stat) {
      if (stat == null) {
         return 1.0;
      } else {
         SecondaryStatEffects.Mod mod = this.mods.get(stat.toUpperCase());
         return mod == null ? 1.0 : Math.max(0.0, 1.0 + mod.factor);
      }
   }

   public boolean hasModifier(String stat) {
      return stat != null && this.mods.containsKey(stat.toUpperCase());
   }

   public boolean hasOtherActiveBuff(String stat) {
      String key = stat == null ? null : stat.toUpperCase();

      for (SecondaryStatEffects.Mod mod : this.mods.values()) {
         if (mod.factor > 0.0 && !mod.stat.equals(key)) {
            return true;
         }
      }

      return false;
   }

   public List<SecondaryStatEffects.Mod> getActiveModifiers() {
      return new ArrayList<>(this.mods.values());
   }

   public boolean isEmpty() {
      return this.mods.isEmpty();
   }

   public void tick() {
      if (!this.mods.isEmpty()) {
         this.mods.values().removeIf(mod -> {
            if (mod.duration > 0) {
               mod.duration--;
            }

            return mod.duration <= 0;
         });
      }
   }

   public void clear() {
      this.mods.clear();
   }

   public CompoundTag save() {
      CompoundTag nbt = new CompoundTag();
      ListTag list = new ListTag();

      for (SecondaryStatEffects.Mod mod : this.mods.values()) {
         CompoundTag modTag = new CompoundTag();
         modTag.putString("Stat", mod.stat);
         modTag.putDouble("Factor", mod.factor);
         modTag.putInt("Duration", mod.duration);
         list.add(modTag);
      }

      nbt.put("Modifiers", list);
      return nbt;
   }

   public void load(CompoundTag nbt) {
      this.mods.clear();
      if (nbt.contains("Modifiers", 9)) {
         ListTag list = nbt.getList("Modifiers", 10);

         for (int i = 0; i < list.size(); i++) {
            CompoundTag modTag = list.getCompound(i);
            String stat = modTag.getString("Stat");
            if (stat != null && !stat.isEmpty()) {
               this.mods.put(stat.toUpperCase(), new SecondaryStatEffects.Mod(stat.toUpperCase(), modTag.getDouble("Factor"), modTag.getInt("Duration")));
            }
         }
      }
   }

   public void toBytes(FriendlyByteBuf buf) {
      buf.writeInt(this.mods.size());

      for (SecondaryStatEffects.Mod mod : this.mods.values()) {
         buf.writeUtf(mod.stat);
         buf.writeDouble(mod.factor);
         buf.writeInt(mod.duration);
      }
   }

   public void fromBytes(FriendlyByteBuf buf) {
      this.mods.clear();
      int size = buf.readInt();

      for (int i = 0; i < size; i++) {
         String stat = buf.readUtf().toUpperCase();
         double factor = buf.readDouble();
         int duration = buf.readInt();
         this.mods.put(stat, new SecondaryStatEffects.Mod(stat, factor, duration));
      }
   }

   public void copyFrom(SecondaryStatEffects other) {
      this.mods.clear();

      for (SecondaryStatEffects.Mod mod : other.mods.values()) {
         this.mods.put(mod.stat, new SecondaryStatEffects.Mod(mod.stat, mod.factor, mod.duration));
      }
   }

   public static class Mod {
      private final String stat;
      private double factor;
      private int duration;

      public Mod(String stat, double factor, int duration) {
         this.stat = stat;
         this.factor = factor;
         this.duration = duration;
      }

      public String getStat() {
         return this.stat;
      }

      public double getFactor() {
         return this.factor;
      }

      public int getDuration() {
         return this.duration;
      }
   }
}
