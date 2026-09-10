package com.dragonminez.common.stats.techniques;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import lombok.Generated;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

public class Techniques {
   public static final int SLOT_COUNT = 8;
   public static final int MAX_UNLOCKED_TECHNIQUES = 256;
   private final Map<String, TechniqueData> unlockedTechniques = new HashMap<>();
   private final String[] equippedSlots = new String[8];
   private int selectedSlot = 0;
   private String chargingTechniqueId = "";
   private float techniqueChargePercent = 0.0F;
   private boolean techniqueCharging = false;
   private boolean chargeHolding = false;
   private transient int homingTargetId = -1;
   private final transient Map<String, Float> fractionalXpRemainder = new HashMap<>();

   public Techniques() {
      for (int i = 0; i < 8; i++) {
         this.equippedSlots[i] = "";
      }
   }

   public void unlockTechnique(TechniqueData data) {
      if (this.unlockedTechniques.containsKey(data.getId()) || this.unlockedTechniques.size() < 256) {
         this.unlockedTechniques.put(data.getId(), data);
      }
   }

   public void equipTechnique(int slotIndex, String techniqueId) {
      if (slotIndex >= 0 && slotIndex < 8 && this.unlockedTechniques.containsKey(techniqueId)) {
         this.equippedSlots[slotIndex] = techniqueId;
      }
   }

   public void removeTechnique(String techniqueId) {
      this.unlockedTechniques.remove(techniqueId);

      for (int i = 0; i < 8; i++) {
         if (this.equippedSlots[i].equals(techniqueId)) {
            this.equippedSlots[i] = "";
            if (this.selectedSlot == i) {
               this.selectedSlot = 0;
            }
         }
      }

      if (this.chargingTechniqueId.equals(techniqueId)) {
         this.clearTechniqueCharge();
      }
   }

   public void selectSlot(int slotIndex) {
      if (slotIndex >= 0 && slotIndex < 8) {
         this.selectedSlot = slotIndex;
      }
   }

   public TechniqueData getSelectedTechnique() {
      String id = this.equippedSlots[this.selectedSlot];
      return id.isEmpty() ? null : this.unlockedTechniques.get(id);
   }

   public String[] getEquippedSlots() {
      return this.equippedSlots;
   }

   public String getChargingTechniqueId() {
      return this.chargingTechniqueId;
   }

   public float getTechniqueChargePercent() {
      return this.techniqueChargePercent;
   }

   public boolean isTechniqueCharging() {
      return this.techniqueCharging;
   }

   public boolean isTechniqueChargeActive() {
      return !this.chargingTechniqueId.isEmpty() && this.techniqueChargePercent > 0.0F;
   }

   public void startTechniqueCharge(String techniqueId) {
      if (techniqueId != null && !techniqueId.isEmpty()) {
         if (!techniqueId.equals(this.chargingTechniqueId)) {
            this.chargingTechniqueId = techniqueId;
            this.techniqueChargePercent = 0.0F;
         }

         this.techniqueCharging = true;
         this.chargeHolding = true;
      }
   }

   public void setTechniqueChargePercent(float percent) {
      this.techniqueChargePercent = Math.max(0.0F, Math.min(200.0F, percent));
   }

   public boolean isChargeHolding() {
      return this.chargeHolding;
   }

   public void clearTechniqueCharge() {
      this.chargingTechniqueId = "";
      this.techniqueChargePercent = 0.0F;
      this.techniqueCharging = false;
      this.chargeHolding = false;
      this.homingTargetId = -1;
   }

   public void clearAllTechniques() {
      this.unlockedTechniques.clear();

      for (int i = 0; i < this.equippedSlots.length; i++) {
         this.equippedSlots[i] = "";
      }

      this.selectedSlot = 0;
      this.clearTechniqueCharge();
   }

   public int getSelectedSlot() {
      return this.selectedSlot;
   }

   public void equipOrSwapTechnique(int slotIndex, String techniqueId) {
      if (slotIndex >= 0 && slotIndex < 8) {
         boolean isEmpty = techniqueId == null || techniqueId.isEmpty();
         if (isEmpty || this.unlockedTechniques.containsKey(techniqueId)) {
            int existingSlot = -1;

            for (int i = 0; i < 8; i++) {
               if (this.equippedSlots[i].equals(techniqueId)) {
                  existingSlot = i;
                  break;
               }
            }

            if (existingSlot != -1) {
               String temp = this.equippedSlots[slotIndex];
               this.equippedSlots[slotIndex] = techniqueId;
               this.equippedSlots[existingSlot] = temp;
            } else {
               this.equippedSlots[slotIndex] = techniqueId;
            }
         }
      }
   }

   public void addExperienceToSelected(int amount) {
      TechniqueData active = this.getSelectedTechnique();
      if (active != null) {
         active.addExperience(amount);
      }
   }

   public void addExperienceToTechnique(String id, int amount) {
      if (this.unlockedTechniques.containsKey(id)) {
         this.unlockedTechniques.get(id).addExperience(amount);
      }
   }

   public void addFractionalExperienceToTechnique(String id, float amount) {
      if (!(amount <= 0.0F) && this.unlockedTechniques.containsKey(id)) {
         float total = this.fractionalXpRemainder.merge(id, amount, Float::sum);
         int whole = (int)total;
         if (whole > 0) {
            this.unlockedTechniques.get(id).addExperience(whole);
            this.fractionalXpRemainder.put(id, total - (float)whole);
         }
      }
   }

   public CompoundTag save() {
      CompoundTag tag = new CompoundTag();
      tag.putInt("SelectedSlot", this.selectedSlot);
      tag.putString("ChargingTechniqueId", this.chargingTechniqueId);
      tag.putFloat("TechniqueChargePercent", this.techniqueChargePercent);
      tag.putBoolean("TechniqueCharging", this.techniqueCharging);
      tag.putBoolean("ChargeHolding", this.chargeHolding);
      CompoundTag slotsTag = new CompoundTag();

      for (int i = 0; i < 8; i++) {
         slotsTag.putString("Slot" + i, this.equippedSlots[i]);
      }

      tag.put("EquippedSlots", slotsTag);
      ListTag unlockedTag = new ListTag();

      for (TechniqueData tech : this.unlockedTechniques.values()) {
         CompoundTag techTag = tech.save();
         techTag.putString("TechClassType", tech instanceof KiAttackData ? "KI" : "STRIKE");
         unlockedTag.add(techTag);
      }

      tag.put("UnlockedTechniques", unlockedTag);
      return tag;
   }

   public void load(CompoundTag tag) {
      this.selectedSlot = tag.getInt("SelectedSlot");
      this.chargingTechniqueId = tag.getString("ChargingTechniqueId");
      this.techniqueChargePercent = Math.max(0.0F, Math.min(200.0F, tag.getFloat("TechniqueChargePercent")));
      this.techniqueCharging = tag.getBoolean("TechniqueCharging");
      this.chargeHolding = tag.getBoolean("ChargeHolding");
      CompoundTag slotsTag = tag.getCompound("EquippedSlots");

      for (int i = 0; i < 8; i++) {
         this.equippedSlots[i] = slotsTag.getString("Slot" + i);
      }

      this.unlockedTechniques.clear();
      ListTag unlockedTag = tag.getList("UnlockedTechniques", 10);

      for (int i = 0; i < unlockedTag.size(); i++) {
         CompoundTag techTag = unlockedTag.getCompound(i);
         String type = techTag.getString("TechClassType");
         TechniqueData tech = (TechniqueData)(type.equals("KI") ? new KiAttackData() : new StrikeAttackData());
         tech.load(techTag);
         this.unlockedTechniques.put(tech.getId(), tech);
      }
   }

   public void copyFrom(Techniques other) {
      this.selectedSlot = other.selectedSlot;
      this.chargingTechniqueId = other.chargingTechniqueId;
      this.techniqueChargePercent = other.techniqueChargePercent;
      this.techniqueCharging = other.techniqueCharging;
      this.chargeHolding = other.chargeHolding;
      System.arraycopy(other.equippedSlots, 0, this.equippedSlots, 0, 8);
      this.unlockedTechniques.clear();

      for (Entry<String, TechniqueData> entry : other.unlockedTechniques.entrySet()) {
         TechniqueData clone = (TechniqueData)(entry.getValue() instanceof KiAttackData ? new KiAttackData() : new StrikeAttackData());
         clone.load(entry.getValue().save());
         this.unlockedTechniques.put(entry.getKey(), clone);
      }
   }

   @Generated
   public Map<String, TechniqueData> getUnlockedTechniques() {
      return this.unlockedTechniques;
   }

   @Generated
   public void setTechniqueCharging(boolean techniqueCharging) {
      this.techniqueCharging = techniqueCharging;
   }

   @Generated
   public void setChargeHolding(boolean chargeHolding) {
      this.chargeHolding = chargeHolding;
   }

   @Generated
   public int getHomingTargetId() {
      return this.homingTargetId;
   }

   @Generated
   public void setHomingTargetId(int homingTargetId) {
      this.homingTargetId = homingTargetId;
   }
}
