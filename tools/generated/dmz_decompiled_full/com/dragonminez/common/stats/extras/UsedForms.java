package com.dragonminez.common.stats.extras;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

public class UsedForms {
   private final Map<String, List<String>> usedForms = new HashMap<>();

   public List<String> getFormGroup(String formGroup) {
      if (!this.usedForms.containsKey(formGroup)) {
         this.usedForms.put(formGroup, new ArrayList<>());
      }

      return this.usedForms.get(formGroup);
   }

   public void putForm(String formGroup, String formName) {
      List<String> existingGroupForms = this.usedForms.get(formGroup);
      if (existingGroupForms == null) {
         List<String> groupForms = new ArrayList<>();
         groupForms.add(formName);
         this.usedForms.put(formGroup, groupForms);
      } else if (!existingGroupForms.contains(formName)) {
         List<String> groupForms = new ArrayList<>(existingGroupForms);
         groupForms.add(formName);
         this.usedForms.put(formGroup, groupForms);
      }
   }

   public void clear() {
      this.usedForms.clear();
   }

   public CompoundTag save() {
      CompoundTag nbt = new CompoundTag();

      for (Entry<String, List<String>> entry : this.usedForms.entrySet()) {
         nbt.putString(entry.getKey(), String.join(":", entry.getValue()));
      }

      return nbt;
   }

   public void load(CompoundTag nbt) {
      this.usedForms.clear();

      for (String key : nbt.getAllKeys()) {
         this.usedForms.put(key, Arrays.stream(nbt.getString(key).split(":")).toList());
      }
   }

   public void toBytes(FriendlyByteBuf buf) {
      buf.writeInt(this.usedForms.size());

      for (Entry<String, List<String>> entry : this.usedForms.entrySet()) {
         buf.writeUtf(entry.getKey());
         buf.writeUtf(String.join(":", entry.getValue()));
      }
   }

   public void fromBytes(FriendlyByteBuf buf) {
      this.usedForms.clear();
      int size = buf.readInt();

      for (int i = 0; i < size; i++) {
         String key = buf.readUtf();
         List<String> value = Arrays.stream(buf.readUtf().split(":")).toList();
         this.usedForms.put(key, value);
      }
   }

   public void copyFrom(UsedForms other) {
      this.usedForms.clear();
      this.usedForms.putAll(other.usedForms);
   }
}
