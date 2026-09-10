package com.dragonminez.common.stats.character;

import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.stats.extras.ActionMode;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.Generated;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

public class Status {
   public static final int FLIGHT_SEARCH = 0;
   public static final int FLIGHT_COMBAT = 1;
   private boolean isAlive = true;
   private boolean forceHalo = false;
   private boolean isHasCreatedCharacter = false;
   private boolean isAuraActive = false;
   private boolean isActionCharging = false;
   private boolean isTailVisible = true;
   private boolean isDescending = false;
   private boolean isInKaioPlanet = false;
   private boolean isChargingKi = false;
   private boolean isBlocking = false;
   private long lastBlockTime = 0L;
   private long lastHurtTime = 0L;
   private boolean friendlyFistEnabled = false;
   private boolean stunEffect = false;
   private boolean isKnockedDown = false;
   private ActionMode selectedAction = ActionMode.FORM;
   private String kiWeaponType = "blade";
   private int drainingTargetId = -1;
   private boolean isFused = false;
   private boolean isFusionLeader = false;
   private UUID fusionPartnerUUID = null;
   private int fusionTimer = 0;
   private String fusionType = "";
   private String fusionName = "";
   private boolean fusionPartyManaged = false;
   private UUID fusionPrevPartyId = null;
   private boolean fusionPrevPartyLeader = false;
   private int potaraPoseTimer = 0;
   private UUID potaraPartnerUUID = null;
   private boolean potaraLeader = false;
   private CompoundTag originalAppearance = new CompoundTag();
   private boolean androidUpgraded = false;
   private boolean renderKatana = false;
   private String backWeapon = "";
   private String scouterItem = "";
   private String pothalaColor = "";
   private boolean isPermanentAura = false;
   private boolean isStrikeLocked = false;
   private int flightMode = 0;
   private final Set<String> visitedDimensions = new LinkedHashSet<>();
   private UUID activeShadowDummyUUID = null;
   private int shadowDummyPercent = 0;
   private int shadowDummyKillCount = 0;

   public void reset() {
      this.isAlive = true;
      this.forceHalo = false;
      this.isHasCreatedCharacter = false;
      this.isAuraActive = false;
      this.isActionCharging = false;
      this.isTailVisible = true;
      this.isDescending = false;
      this.isInKaioPlanet = false;
      this.isChargingKi = false;
      this.isBlocking = false;
      this.lastBlockTime = 0L;
      this.lastHurtTime = 0L;
      this.friendlyFistEnabled = false;
      this.stunEffect = false;
      this.isKnockedDown = false;
      this.selectedAction = ActionMode.FORM;
      this.kiWeaponType = "blade";
      this.drainingTargetId = -1;
      this.isFused = false;
      this.isFusionLeader = false;
      this.fusionPartnerUUID = null;
      this.fusionTimer = 0;
      this.fusionType = "";
      this.fusionName = "";
      this.fusionPartyManaged = false;
      this.fusionPrevPartyId = null;
      this.fusionPrevPartyLeader = false;
      this.potaraPoseTimer = 0;
      this.potaraPartnerUUID = null;
      this.potaraLeader = false;
      this.originalAppearance = new CompoundTag();
      this.androidUpgraded = false;
      this.renderKatana = false;
      this.backWeapon = "";
      this.scouterItem = "";
      this.pothalaColor = "";
      this.isPermanentAura = false;
      this.isStrikeLocked = false;
      this.flightMode = 0;
      this.visitedDimensions.clear();
      this.activeShadowDummyUUID = null;
      this.shadowDummyPercent = 0;
      this.shadowDummyKillCount = 0;
   }

   public boolean isStunned() {
      return this.stunEffect || this.isKnockedDown || this.isStrikeLocked;
   }

   public void validateKiWeaponType() {
      List<String> types = ConfigManager.getCombatConfig().getKiWeaponTypes();
      if (!types.isEmpty()) {
         if (this.kiWeaponType == null || !types.contains(this.kiWeaponType.toLowerCase())) {
            this.kiWeaponType = types.get(0);
         }
      }
   }

   public boolean hasActiveShadowDummy() {
      return this.activeShadowDummyUUID != null;
   }

   public void markVisitedDimension(String dimensionId) {
      if (dimensionId != null && !dimensionId.isBlank() && ResourceLocation.tryParse(dimensionId) != null) {
         this.visitedDimensions.add(dimensionId);
      }
   }

   public boolean hasVisitedDimension(String dimensionId) {
      return dimensionId != null && this.visitedDimensions.contains(dimensionId);
   }

   public CompoundTag save() {
      CompoundTag tag = new CompoundTag();
      tag.putBoolean("IsAlive", this.isAlive);
      tag.putBoolean("ForceHalo", this.forceHalo);
      tag.putBoolean("HasCreatedChar", this.isHasCreatedCharacter);
      tag.putBoolean("AuraActive", this.isAuraActive);
      tag.putBoolean("Transforming", this.isActionCharging);
      tag.putBoolean("TailVisible", this.isTailVisible);
      tag.putBoolean("Descending", this.isDescending);
      tag.putBoolean("InKaioPlanet", this.isInKaioPlanet);
      tag.putBoolean("IsChargingKi", this.isChargingKi);
      tag.putBoolean("IsBlocking", this.isBlocking);
      tag.putLong("LastBlockTime", this.lastBlockTime);
      tag.putLong("LastHurtTime", this.lastHurtTime);
      tag.putBoolean("FriendlyFistEnabled", this.friendlyFistEnabled);
      tag.putBoolean("IsStunned", this.stunEffect);
      tag.putBoolean("IsKnockedDown", this.isKnockedDown);
      tag.putInt("SelectedAction", this.selectedAction.ordinal());
      tag.putString("KiWeaponType", this.kiWeaponType);
      tag.putInt("DrainingTargetId", this.drainingTargetId);
      tag.putBoolean("IsFused", this.isFused);
      tag.putBoolean("IsFusionLeader", this.isFusionLeader);
      if (this.fusionPartnerUUID != null) {
         tag.putUUID("FusionPartnerUUID", this.fusionPartnerUUID);
      }

      tag.putInt("FusionTimer", this.fusionTimer);
      tag.putString("FusionType", this.fusionType);
      tag.putString("FusionName", this.fusionName);
      tag.putBoolean("FusionPartyManaged", this.fusionPartyManaged);
      if (this.fusionPrevPartyId != null) {
         tag.putUUID("FusionPrevPartyId", this.fusionPrevPartyId);
      }

      tag.putBoolean("FusionPrevPartyLeader", this.fusionPrevPartyLeader);
      tag.putInt("PotaraPoseTimer", this.potaraPoseTimer);
      if (this.potaraPartnerUUID != null) {
         tag.putUUID("PotaraPartnerUUID", this.potaraPartnerUUID);
      }

      tag.putBoolean("PotaraLeader", this.potaraLeader);
      tag.put("OriginalAppearance", this.originalAppearance);
      tag.putBoolean("AndroidUpgraded", this.androidUpgraded);
      tag.putBoolean("RenderKatana", this.renderKatana);
      tag.putString("BackWeapon", this.backWeapon);
      tag.putString("ScouterItem", this.scouterItem);
      tag.putString("PothalaColor", this.pothalaColor);
      tag.putBoolean("IsPermanentAura", this.isPermanentAura);
      tag.putBoolean("IsStrikeLocked", this.isStrikeLocked);
      tag.putInt("FlightMode", this.flightMode);
      ListTag visitedDimensionsTag = new ListTag();

      for (String dimensionId : this.visitedDimensions) {
         visitedDimensionsTag.add(StringTag.valueOf(dimensionId));
      }

      tag.put("VisitedDimensions", visitedDimensionsTag);
      if (this.activeShadowDummyUUID != null) {
         tag.putUUID("ActiveShadowDummyUUID", this.activeShadowDummyUUID);
      }

      tag.putInt("ShadowDummyPercent", this.shadowDummyPercent);
      tag.putInt("ShadowDummyKillCount", this.shadowDummyKillCount);
      return tag;
   }

   public void load(CompoundTag tag) {
      this.isAlive = tag.getBoolean("IsAlive");
      this.forceHalo = tag.getBoolean("ForceHalo");
      this.isHasCreatedCharacter = tag.getBoolean("HasCreatedChar");
      this.isAuraActive = tag.getBoolean("AuraActive");
      this.isActionCharging = tag.getBoolean("Transforming");
      this.isTailVisible = tag.getBoolean("TailVisible");
      this.isDescending = tag.getBoolean("Descending");
      this.isInKaioPlanet = tag.getBoolean("InKaioPlanet");
      this.isChargingKi = tag.getBoolean("IsChargingKi");
      this.isBlocking = tag.getBoolean("IsBlocking");
      this.lastBlockTime = tag.getLong("LastBlockTime");
      this.lastHurtTime = tag.getLong("LastHurtTime");
      this.friendlyFistEnabled = tag.getBoolean("FriendlyFistEnabled");
      this.stunEffect = tag.getBoolean("IsStunned");
      this.isKnockedDown = tag.getBoolean("IsKnockedDown");
      if (tag.contains("SelectedAction")) {
         this.selectedAction = ActionMode.values()[tag.getInt("SelectedAction")];
      } else {
         this.selectedAction = ActionMode.FORM;
      }

      this.kiWeaponType = tag.getString("KiWeaponType");
      this.drainingTargetId = tag.getInt("DrainingTargetId");
      this.isFused = tag.getBoolean("IsFused");
      this.isFusionLeader = tag.getBoolean("IsFusionLeader");
      if (tag.hasUUID("FusionPartnerUUID")) {
         this.fusionPartnerUUID = tag.getUUID("FusionPartnerUUID");
      } else {
         this.fusionPartnerUUID = null;
      }

      this.fusionTimer = tag.getInt("FusionTimer");
      this.fusionType = tag.getString("FusionType");
      this.fusionName = tag.getString("FusionName");
      this.fusionPartyManaged = tag.getBoolean("FusionPartyManaged");
      this.fusionPrevPartyId = tag.hasUUID("FusionPrevPartyId") ? tag.getUUID("FusionPrevPartyId") : null;
      this.fusionPrevPartyLeader = tag.getBoolean("FusionPrevPartyLeader");
      this.potaraPoseTimer = tag.getInt("PotaraPoseTimer");
      this.potaraPartnerUUID = tag.hasUUID("PotaraPartnerUUID") ? tag.getUUID("PotaraPartnerUUID") : null;
      this.potaraLeader = tag.getBoolean("PotaraLeader");
      if (tag.contains("OriginalAppearance")) {
         this.originalAppearance = tag.getCompound("OriginalAppearance");
      } else {
         this.originalAppearance = new CompoundTag();
      }

      this.androidUpgraded = tag.getBoolean("AndroidUpgraded");
      this.renderKatana = tag.getBoolean("RenderKatana");
      this.backWeapon = tag.getString("BackWeapon");
      this.scouterItem = tag.getString("ScouterItem");
      this.pothalaColor = tag.getString("PothalaColor");
      this.isPermanentAura = tag.getBoolean("IsPermanentAura");
      this.isStrikeLocked = tag.getBoolean("IsStrikeLocked");
      this.flightMode = tag.getInt("FlightMode");
      this.visitedDimensions.clear();
      if (tag.contains("VisitedDimensions", 9)) {
         for (Tag dimensionTag : tag.getList("VisitedDimensions", 8)) {
            this.markVisitedDimension(dimensionTag.getAsString());
         }
      }

      this.activeShadowDummyUUID = tag.hasUUID("ActiveShadowDummyUUID") ? tag.getUUID("ActiveShadowDummyUUID") : null;
      this.shadowDummyPercent = tag.getInt("ShadowDummyPercent");
      this.shadowDummyKillCount = tag.contains("ShadowDummyKillCount") ? tag.getInt("ShadowDummyKillCount") : 0;
   }

   public void copyFrom(Status other) {
      this.isAlive = other.isAlive;
      this.forceHalo = other.forceHalo;
      this.isHasCreatedCharacter = other.isHasCreatedCharacter;
      this.isAuraActive = other.isAuraActive;
      this.isActionCharging = other.isActionCharging;
      this.isTailVisible = other.isTailVisible;
      this.isDescending = other.isDescending;
      this.isInKaioPlanet = other.isInKaioPlanet;
      this.isChargingKi = other.isChargingKi;
      this.isBlocking = other.isBlocking;
      this.lastBlockTime = other.lastBlockTime;
      this.lastHurtTime = other.lastHurtTime;
      this.friendlyFistEnabled = other.friendlyFistEnabled;
      this.stunEffect = other.stunEffect;
      this.isKnockedDown = other.isKnockedDown;
      this.selectedAction = other.selectedAction;
      this.kiWeaponType = other.kiWeaponType;
      this.drainingTargetId = other.drainingTargetId;
      this.isFused = other.isFused;
      this.isFusionLeader = other.isFusionLeader;
      this.fusionPartnerUUID = other.fusionPartnerUUID;
      this.fusionTimer = other.fusionTimer;
      this.fusionType = other.fusionType;
      this.fusionName = other.fusionName;
      this.fusionPartyManaged = other.fusionPartyManaged;
      this.fusionPrevPartyId = other.fusionPrevPartyId;
      this.fusionPrevPartyLeader = other.fusionPrevPartyLeader;
      this.potaraPoseTimer = other.potaraPoseTimer;
      this.potaraPartnerUUID = other.potaraPartnerUUID;
      this.potaraLeader = other.potaraLeader;
      this.originalAppearance = other.originalAppearance.copy();
      this.androidUpgraded = other.androidUpgraded;
      this.renderKatana = other.renderKatana;
      this.backWeapon = other.backWeapon;
      this.pothalaColor = other.pothalaColor;
      this.scouterItem = other.scouterItem;
      this.isPermanentAura = other.isPermanentAura;
      this.isStrikeLocked = other.isStrikeLocked;
      this.flightMode = other.flightMode;
      this.visitedDimensions.clear();
      this.visitedDimensions.addAll(other.visitedDimensions);
      this.activeShadowDummyUUID = other.activeShadowDummyUUID;
      this.shadowDummyPercent = other.shadowDummyPercent;
      this.shadowDummyKillCount = other.shadowDummyKillCount;
   }

   @Generated
   public boolean isAlive() {
      return this.isAlive;
   }

   @Generated
   public boolean isForceHalo() {
      return this.forceHalo;
   }

   @Generated
   public boolean isHasCreatedCharacter() {
      return this.isHasCreatedCharacter;
   }

   @Generated
   public boolean isAuraActive() {
      return this.isAuraActive;
   }

   @Generated
   public boolean isActionCharging() {
      return this.isActionCharging;
   }

   @Generated
   public boolean isTailVisible() {
      return this.isTailVisible;
   }

   @Generated
   public boolean isDescending() {
      return this.isDescending;
   }

   @Generated
   public boolean isInKaioPlanet() {
      return this.isInKaioPlanet;
   }

   @Generated
   public boolean isChargingKi() {
      return this.isChargingKi;
   }

   @Generated
   public boolean isBlocking() {
      return this.isBlocking;
   }

   @Generated
   public long getLastBlockTime() {
      return this.lastBlockTime;
   }

   @Generated
   public long getLastHurtTime() {
      return this.lastHurtTime;
   }

   @Generated
   public boolean isFriendlyFistEnabled() {
      return this.friendlyFistEnabled;
   }

   @Generated
   public boolean isStunEffect() {
      return this.stunEffect;
   }

   @Generated
   public boolean isKnockedDown() {
      return this.isKnockedDown;
   }

   @Generated
   public ActionMode getSelectedAction() {
      return this.selectedAction;
   }

   @Generated
   public String getKiWeaponType() {
      return this.kiWeaponType;
   }

   @Generated
   public int getDrainingTargetId() {
      return this.drainingTargetId;
   }

   @Generated
   public boolean isFused() {
      return this.isFused;
   }

   @Generated
   public boolean isFusionLeader() {
      return this.isFusionLeader;
   }

   @Generated
   public UUID getFusionPartnerUUID() {
      return this.fusionPartnerUUID;
   }

   @Generated
   public int getFusionTimer() {
      return this.fusionTimer;
   }

   @Generated
   public String getFusionType() {
      return this.fusionType;
   }

   @Generated
   public String getFusionName() {
      return this.fusionName;
   }

   @Generated
   public boolean isFusionPartyManaged() {
      return this.fusionPartyManaged;
   }

   @Generated
   public UUID getFusionPrevPartyId() {
      return this.fusionPrevPartyId;
   }

   @Generated
   public boolean isFusionPrevPartyLeader() {
      return this.fusionPrevPartyLeader;
   }

   @Generated
   public int getPotaraPoseTimer() {
      return this.potaraPoseTimer;
   }

   @Generated
   public UUID getPotaraPartnerUUID() {
      return this.potaraPartnerUUID;
   }

   @Generated
   public boolean isPotaraLeader() {
      return this.potaraLeader;
   }

   @Generated
   public CompoundTag getOriginalAppearance() {
      return this.originalAppearance;
   }

   @Generated
   public boolean isAndroidUpgraded() {
      return this.androidUpgraded;
   }

   @Generated
   public boolean isRenderKatana() {
      return this.renderKatana;
   }

   @Generated
   public String getBackWeapon() {
      return this.backWeapon;
   }

   @Generated
   public String getScouterItem() {
      return this.scouterItem;
   }

   @Generated
   public String getPothalaColor() {
      return this.pothalaColor;
   }

   @Generated
   public boolean isPermanentAura() {
      return this.isPermanentAura;
   }

   @Generated
   public boolean isStrikeLocked() {
      return this.isStrikeLocked;
   }

   @Generated
   public int getFlightMode() {
      return this.flightMode;
   }

   @Generated
   public Set<String> getVisitedDimensions() {
      return this.visitedDimensions;
   }

   @Generated
   public UUID getActiveShadowDummyUUID() {
      return this.activeShadowDummyUUID;
   }

   @Generated
   public int getShadowDummyPercent() {
      return this.shadowDummyPercent;
   }

   @Generated
   public int getShadowDummyKillCount() {
      return this.shadowDummyKillCount;
   }

   @Generated
   public void setAlive(boolean isAlive) {
      this.isAlive = isAlive;
   }

   @Generated
   public void setForceHalo(boolean forceHalo) {
      this.forceHalo = forceHalo;
   }

   @Generated
   public void setHasCreatedCharacter(boolean isHasCreatedCharacter) {
      this.isHasCreatedCharacter = isHasCreatedCharacter;
   }

   @Generated
   public void setAuraActive(boolean isAuraActive) {
      this.isAuraActive = isAuraActive;
   }

   @Generated
   public void setActionCharging(boolean isActionCharging) {
      this.isActionCharging = isActionCharging;
   }

   @Generated
   public void setTailVisible(boolean isTailVisible) {
      this.isTailVisible = isTailVisible;
   }

   @Generated
   public void setDescending(boolean isDescending) {
      this.isDescending = isDescending;
   }

   @Generated
   public void setInKaioPlanet(boolean isInKaioPlanet) {
      this.isInKaioPlanet = isInKaioPlanet;
   }

   @Generated
   public void setChargingKi(boolean isChargingKi) {
      this.isChargingKi = isChargingKi;
   }

   @Generated
   public void setBlocking(boolean isBlocking) {
      this.isBlocking = isBlocking;
   }

   @Generated
   public void setLastBlockTime(long lastBlockTime) {
      this.lastBlockTime = lastBlockTime;
   }

   @Generated
   public void setLastHurtTime(long lastHurtTime) {
      this.lastHurtTime = lastHurtTime;
   }

   @Generated
   public void setFriendlyFistEnabled(boolean friendlyFistEnabled) {
      this.friendlyFistEnabled = friendlyFistEnabled;
   }

   @Generated
   public void setStunEffect(boolean stunEffect) {
      this.stunEffect = stunEffect;
   }

   @Generated
   public void setKnockedDown(boolean isKnockedDown) {
      this.isKnockedDown = isKnockedDown;
   }

   @Generated
   public void setSelectedAction(ActionMode selectedAction) {
      this.selectedAction = selectedAction;
   }

   @Generated
   public void setKiWeaponType(String kiWeaponType) {
      this.kiWeaponType = kiWeaponType;
   }

   @Generated
   public void setDrainingTargetId(int drainingTargetId) {
      this.drainingTargetId = drainingTargetId;
   }

   @Generated
   public void setFused(boolean isFused) {
      this.isFused = isFused;
   }

   @Generated
   public void setFusionLeader(boolean isFusionLeader) {
      this.isFusionLeader = isFusionLeader;
   }

   @Generated
   public void setFusionPartnerUUID(UUID fusionPartnerUUID) {
      this.fusionPartnerUUID = fusionPartnerUUID;
   }

   @Generated
   public void setFusionTimer(int fusionTimer) {
      this.fusionTimer = fusionTimer;
   }

   @Generated
   public void setFusionType(String fusionType) {
      this.fusionType = fusionType;
   }

   @Generated
   public void setFusionName(String fusionName) {
      this.fusionName = fusionName;
   }

   @Generated
   public void setFusionPartyManaged(boolean fusionPartyManaged) {
      this.fusionPartyManaged = fusionPartyManaged;
   }

   @Generated
   public void setFusionPrevPartyId(UUID fusionPrevPartyId) {
      this.fusionPrevPartyId = fusionPrevPartyId;
   }

   @Generated
   public void setFusionPrevPartyLeader(boolean fusionPrevPartyLeader) {
      this.fusionPrevPartyLeader = fusionPrevPartyLeader;
   }

   @Generated
   public void setPotaraPoseTimer(int potaraPoseTimer) {
      this.potaraPoseTimer = potaraPoseTimer;
   }

   @Generated
   public void setPotaraPartnerUUID(UUID potaraPartnerUUID) {
      this.potaraPartnerUUID = potaraPartnerUUID;
   }

   @Generated
   public void setPotaraLeader(boolean potaraLeader) {
      this.potaraLeader = potaraLeader;
   }

   @Generated
   public void setOriginalAppearance(CompoundTag originalAppearance) {
      this.originalAppearance = originalAppearance;
   }

   @Generated
   public void setAndroidUpgraded(boolean androidUpgraded) {
      this.androidUpgraded = androidUpgraded;
   }

   @Generated
   public void setRenderKatana(boolean renderKatana) {
      this.renderKatana = renderKatana;
   }

   @Generated
   public void setBackWeapon(String backWeapon) {
      this.backWeapon = backWeapon;
   }

   @Generated
   public void setScouterItem(String scouterItem) {
      this.scouterItem = scouterItem;
   }

   @Generated
   public void setPothalaColor(String pothalaColor) {
      this.pothalaColor = pothalaColor;
   }

   @Generated
   public void setPermanentAura(boolean isPermanentAura) {
      this.isPermanentAura = isPermanentAura;
   }

   @Generated
   public void setStrikeLocked(boolean isStrikeLocked) {
      this.isStrikeLocked = isStrikeLocked;
   }

   @Generated
   public void setFlightMode(int flightMode) {
      this.flightMode = flightMode;
   }

   @Generated
   public void setActiveShadowDummyUUID(UUID activeShadowDummyUUID) {
      this.activeShadowDummyUUID = activeShadowDummyUUID;
   }

   @Generated
   public void setShadowDummyPercent(int shadowDummyPercent) {
      this.shadowDummyPercent = shadowDummyPercent;
   }

   @Generated
   public void setShadowDummyKillCount(int shadowDummyKillCount) {
      this.shadowDummyKillCount = shadowDummyKillCount;
   }
}
