package com.dragonminez.common.stats.extras;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

public class FormMasteries {
   private final Map<String, Double> masteries = new HashMap<>();

   public double getMastery(String formGroup, String formName) {
      String key = this.getKey(formGroup, formName);
      return this.masteries.getOrDefault(key, 0.0);
   }

   public void setMastery(String formGroup, String formName, double mastery, double maxMastery) {
      String key = this.getKey(formGroup, formName);
      this.masteries.put(key, Math.max(0.0, Math.min(maxMastery, mastery)));
   }

   public void addMastery(String formGroup, String formName, double amount, double maxMastery) {
      String key = this.getKey(formGroup, formName);
      double current = this.masteries.getOrDefault(key, 0.0);
      double newMastery = Math.min(maxMastery, current + amount);
      this.masteries.put(key, newMastery);
   }

   public boolean hasMaxMastery(String formGroup, String formName, double maxMastery) {
      return this.getMastery(formGroup, formName) >= maxMastery;
   }

   private String getKey(String formGroup, String formName) {
      return formGroup.toLowerCase() + ":" + formName.toLowerCase();
   }

   public void clear() {
      this.masteries.clear();
   }

   public CompoundTag save() {
      CompoundTag nbt = new CompoundTag();

      for (Entry<String, Double> entry : this.masteries.entrySet()) {
         nbt.putDouble(entry.getKey(), entry.getValue());
      }

      return nbt;
   }

   public void load(CompoundTag nbt) {
      this.masteries.clear();

      for (String key : nbt.getAllKeys()) {
         this.masteries.put(key, nbt.getDouble(key));
      }
   }

   public void toBytes(FriendlyByteBuf buf) {
      buf.writeInt(this.masteries.size());

      for (Entry<String, Double> entry : this.masteries.entrySet()) {
         buf.writeUtf(entry.getKey());
         buf.writeDouble(entry.getValue());
      }
   }

   public void fromBytes(FriendlyByteBuf buf) {
      this.masteries.clear();
      int size = buf.readInt();

      for (int i = 0; i < size; i++) {
         String key = buf.readUtf();
         double value = buf.readDouble();
         this.masteries.put(key, value);
      }
   }

   public void copyFrom(FormMasteries other) {
      this.masteries.clear();
      this.masteries.putAll(other.masteries);
   }
}
