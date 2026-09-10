package com.dragonminez.common.config;

import com.dragonminez.client.util.ColorUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Generated;

public class FormConfig {
   public static final String CURRENT_VERSION = "2.1.3";
   private String configVersion;
   private String groupName;
   private String formType = "superforms";
   private Map<String, FormConfig.FormData> forms = new LinkedHashMap<>();

   public String getGroupName() {
      return this.groupName != null ? this.groupName : "";
   }

   public String getFormType() {
      return this.formType != null ? this.formType : "superforms";
   }

   public Map<String, FormConfig.FormData> getForms() {
      return this.forms != null ? this.forms : Collections.emptyMap();
   }

   public FormConfig.FormData getForm(String formName) {
      if (formName == null) {
         return null;
      } else {
         for (FormConfig.FormData formData : this.getForms().values()) {
            if (formData != null && formData.getName() != null && formData.getName().equalsIgnoreCase(formName)) {
               return formData;
            }
         }

         return null;
      }
   }

   public FormConfig.FormData getFormByKey(String key) {
      return this.forms.get(key);
   }

   @Generated
   public void setConfigVersion(String configVersion) {
      this.configVersion = configVersion;
   }

   @Generated
   public void setGroupName(String groupName) {
      this.groupName = groupName;
   }

   @Generated
   public void setFormType(String formType) {
      this.formType = formType;
   }

   @Generated
   public void setForms(Map<String, FormConfig.FormData> forms) {
      this.forms = forms;
   }

   @Generated
   public String getConfigVersion() {
      return this.configVersion;
   }

   public static class FormData {
      private String name = "";
      private Integer unlockOnSkillLevel = 0;
      private String formCombo = "";
      private String customModel = "";
      private boolean keepBaseFormHeadBones = false;
      private String transformationAnimation = "transf.generic";
      private String bodyColor1 = "";
      private String bodyColor2 = "";
      private String bodyColor3 = "";
      private String extraFormLayer = "";
      private String extraFormColor = "";
      private String hairType = "";
      private String forcedHairCode = "";
      private String hairColor = "";
      private String eye1Color = "";
      private String eye2Color = "";
      private String auraType = "kakarot";
      private Integer auraLayer = 0;
      private String auraColor = "";
      private Integer extraAuraLayer = -1;
      private String extraAuraColor = "#FFFFFF";
      private String extraAuraType = "kakarot";
      private Boolean hasLightnings = false;
      private String lightningColor = "";
      private String tintColor = "#FF0000";
      private Double tintIntensity = 0.0;
      private Float[] modelScaling = new Float[]{0.9375F, 0.9375F, 0.9375F};
      private Double strMultiplier = 1.0;
      private Double skpMultiplier = 1.0;
      private Double stmMultiplier = 1.0;
      private Double defMultiplier = 1.0;
      private Double vitMultiplier = 1.0;
      private Double pwrMultiplier = 1.0;
      private Double eneMultiplier = 1.0;
      private Double speedMultiplier = 1.0;
      private Double staminaDrainMultiplier = 1.0;
      private Double energyDrain = 0.0;
      private Double staminaDrain = 0.0;
      private Double healthDrain = 0.0;
      private Double attackSpeed = 1.0;
      private Double maxMastery = 100.0;
      private Double masteryPerHitDealt = 0.01;
      private Double masteryPerHitReceived = 0.01;
      private Double passiveMasteryEveryFiveSeconds = 0.001;
      private Double maxCostMultiplier = 0.5;
      private Double maxStatsMultiplier = 1.25;
      private String formRequisite = "";
      private String formRequisiteType = "all";
      private Double unlockOnMastery = 0.0;
      private Double stackOnMastery = 0.0;
      private Double instantTransformOnMastery = 40.0;
      private Double allowFreeTransformOnMastery = 50.0;
      private Boolean formStackable = true;
      private Double stackDrainMultiplier = 2.0;
      private List<String> incompatibleWith = new ArrayList<>(List.of("ultimate.ultimate"));
      private List<String> shareMasteryWith = new ArrayList<>();
      private Double shareMasteryMultiplier = 1.0;
      private FormConfig.FormData.OutlineShaderConfig outlineShader = new FormConfig.FormData.OutlineShaderConfig();
      private List<FormConfig.FormData.TriggerItemCost> triggerItemCosts = new ArrayList<>();
      private List<FormConfig.FormData.DurationItemCost> durationItemCosts = new ArrayList<>();
      private List<FormConfig.FormData.MobEffectConfig> mobEffects = new ArrayList<>();
      private transient float[] rgbBodyColor1;
      private transient float[] rgbBodyColor2;
      private transient float[] rgbBodyColor3;
      private transient float[] rgbHairColor;
      private transient float[] rgbEye1Color;
      private transient float[] rgbEye2Color;
      private transient float[] rgbAuraColor;
      private transient float[] rgbExtraFormColor;
      private transient float[] rgbExtraAuraColor;
      private transient float[] rgbTintColor;

      public Double getStrMultiplier() {
         return Math.max(0.01, this.strMultiplier);
      }

      public Double getSkpMultiplier() {
         return Math.max(0.01, this.skpMultiplier);
      }

      public Double getStmMultiplier() {
         return Math.max(0.01, this.stmMultiplier);
      }

      public Double getDefMultiplier() {
         return Math.max(0.01, this.defMultiplier);
      }

      public Double getVitMultiplier() {
         return Math.max(0.01, this.vitMultiplier);
      }

      public Double getPwrMultiplier() {
         return Math.max(0.01, this.pwrMultiplier);
      }

      public Double getEneMultiplier() {
         return Math.max(0.01, this.eneMultiplier);
      }

      public Double getSpeedMultiplier() {
         return Math.max(0.01, this.speedMultiplier);
      }

      public Double getStaminaDrainMultiplier() {
         return Math.max(0.0, this.staminaDrainMultiplier);
      }

      public Double getEnergyDrain() {
         return Math.max(0.0, this.energyDrain);
      }

      public Double getStaminaDrain() {
         return Math.max(0.0, this.staminaDrain);
      }

      public Double getHealthDrain() {
         return Math.max(0.0, this.healthDrain);
      }

      public Double getAttackSpeed() {
         return Math.max(0.1, this.attackSpeed);
      }

      public Double getMasteryPerHitDealt() {
         return Math.max(0.0, this.masteryPerHitDealt);
      }

      public Double getMasteryPerHitReceived() {
         return Math.max(0.0, this.masteryPerHitReceived);
      }

      public Double getPassiveMasteryEveryFiveSeconds() {
         return Math.max(0.0, this.passiveMasteryEveryFiveSeconds);
      }

      public Double getMaxCostMultiplier() {
         return Math.max(0.0, this.maxCostMultiplier);
      }

      public Double getMaxStatsMultiplier() {
         return Math.max(0.0, this.maxStatsMultiplier);
      }

      public String getFormRequisite() {
         return this.formRequisite != null ? this.formRequisite.trim() : "";
      }

      public String getFormRequisiteType() {
         return "any".equalsIgnoreCase(this.formRequisiteType != null ? this.formRequisiteType.trim() : "") ? "any" : "all";
      }

      public Double getUnlockOnMastery() {
         return Math.max(0.0, this.unlockOnMastery);
      }

      public Double getStackOnMastery() {
         return Math.max(0.0, this.stackOnMastery);
      }

      public Double getInstantTransformOnMastery() {
         return Math.max(0.0, this.instantTransformOnMastery);
      }

      public Double getAllowFreeTransformOnMastery() {
         return Math.max(0.0, this.allowFreeTransformOnMastery != null ? this.allowFreeTransformOnMastery : 50.0);
      }

      public List<String> getIncompatibleWith() {
         return this.incompatibleWith != null ? this.incompatibleWith : Collections.emptyList();
      }

      public List<String> getShareMasteryWith() {
         return this.shareMasteryWith != null ? this.shareMasteryWith : Collections.emptyList();
      }

      public Double getShareMasteryMultiplier() {
         return Math.max(0.0, this.shareMasteryMultiplier);
      }

      public boolean isIncompatibleWith(String groupId, String formId) {
         if (groupId != null && formId != null) {
            String key = (groupId + "." + formId).toLowerCase();

            for (String entry : this.getIncompatibleWith()) {
               if (entry != null && entry.trim().toLowerCase().equals(key)) {
                  return true;
               }
            }

            return false;
         } else {
            return false;
         }
      }

      public Double getStackDrainMultiplier() {
         return Math.max(0.01, this.stackDrainMultiplier);
      }

      public Boolean hasCustomModel() {
         return this.customModel != null && !this.customModel.isEmpty();
      }

      public boolean hasTransformationAnimation() {
         return this.transformationAnimation != null && !this.transformationAnimation.trim().isEmpty();
      }

      public String getTransformationAnimation() {
         return this.transformationAnimation != null ? this.transformationAnimation.trim() : "";
      }

      public Boolean hasBodyColorOverride() {
         return !this.bodyColor1.isEmpty() || !this.bodyColor2.isEmpty() || !this.bodyColor3.isEmpty();
      }

      public String getExtraFormLayer() {
         return this.extraFormLayer != null ? this.extraFormLayer.trim() : "";
      }

      public boolean hasExtraFormLayer() {
         return !this.getExtraFormLayer().isEmpty();
      }

      public Boolean hasDefinedHairType() {
         return this.hairType != null && !this.hairType.isEmpty();
      }

      public Boolean hasHairCodeOverride() {
         return !this.forcedHairCode.isEmpty();
      }

      public Boolean hasHairColorOverride() {
         return this.hairColor != null && !this.hairColor.isEmpty();
      }

      public Boolean hasEyeColorOverride() {
         return !this.eye1Color.isEmpty() || !this.eye2Color.isEmpty();
      }

      public Boolean hasAuraColorOverride() {
         return this.auraColor != null && !this.auraColor.isEmpty();
      }

      public int getExtraAuraLayer() {
         return this.extraAuraLayer != null ? this.extraAuraLayer : -1;
      }

      public boolean hasExtraAura() {
         int layer = this.getExtraAuraLayer();
         return layer >= 1 && layer <= 6;
      }

      public String getExtraAuraColor() {
         return this.extraAuraColor != null && !this.extraAuraColor.isEmpty() ? this.extraAuraColor : "#FFFFFF";
      }

      public String getExtraAuraType() {
         return this.extraAuraType != null && !this.extraAuraType.isEmpty() ? this.extraAuraType : "kakarot";
      }

      public FormConfig.FormData.OutlineShaderConfig getOutlineShader() {
         return this.outlineShader != null ? this.outlineShader : new FormConfig.FormData.OutlineShaderConfig();
      }

      public List<FormConfig.FormData.MobEffectConfig> getMobEffects() {
         return this.mobEffects != null ? this.mobEffects : Collections.emptyList();
      }

      public List<FormConfig.FormData.TriggerItemCost> getTriggerItemCosts() {
         return this.triggerItemCosts != null ? this.triggerItemCosts : Collections.emptyList();
      }

      public List<FormConfig.FormData.DurationItemCost> getDurationItemCosts() {
         return this.durationItemCosts != null ? this.durationItemCosts : Collections.emptyList();
      }

      public boolean hasTriggerItemCosts() {
         return this.triggerItemCosts != null && !this.triggerItemCosts.isEmpty();
      }

      public boolean hasDurationItemCosts() {
         return this.durationItemCosts != null && !this.durationItemCosts.isEmpty();
      }

      public float[] getRgbBodyColor1() {
         if (this.rgbBodyColor1 == null && this.bodyColor1 != null && !this.bodyColor1.isEmpty()) {
            this.rgbBodyColor1 = ColorUtils.hexToRgb(this.bodyColor1);
         }

         return this.rgbBodyColor1;
      }

      public float[] getRgbBodyColor2() {
         if (this.rgbBodyColor2 == null && this.bodyColor2 != null && !this.bodyColor2.isEmpty()) {
            this.rgbBodyColor2 = ColorUtils.hexToRgb(this.bodyColor2);
         }

         return this.rgbBodyColor2;
      }

      public float[] getRgbBodyColor3() {
         if (this.rgbBodyColor3 == null && this.bodyColor3 != null && !this.bodyColor3.isEmpty()) {
            this.rgbBodyColor3 = ColorUtils.hexToRgb(this.bodyColor3);
         }

         return this.rgbBodyColor3;
      }

      public float[] getRgbHairColor() {
         if (this.rgbHairColor == null && this.hairColor != null && !this.hairColor.isEmpty()) {
            this.rgbHairColor = ColorUtils.hexToRgb(this.hairColor);
         }

         return this.rgbHairColor;
      }

      public float[] getRgbEye1Color() {
         if (this.rgbEye1Color == null && this.eye1Color != null && !this.eye1Color.isEmpty()) {
            this.rgbEye1Color = ColorUtils.hexToRgb(this.eye1Color);
         }

         return this.rgbEye1Color;
      }

      public float[] getRgbEye2Color() {
         if (this.rgbEye2Color == null && this.eye2Color != null && !this.eye2Color.isEmpty()) {
            this.rgbEye2Color = ColorUtils.hexToRgb(this.eye2Color);
         }

         return this.rgbEye2Color;
      }

      public float[] getRgbAuraColor() {
         if (this.rgbAuraColor == null && this.auraColor != null && !this.auraColor.isEmpty()) {
            this.rgbAuraColor = ColorUtils.hexToRgb(this.auraColor);
         }

         return this.rgbAuraColor;
      }

      public float[] getRgbExtraFormColor() {
         if (this.rgbExtraFormColor == null && this.extraFormColor != null && !this.extraFormColor.isEmpty()) {
            this.rgbExtraFormColor = ColorUtils.hexToRgb(this.extraFormColor);
         }

         return this.rgbExtraFormColor;
      }

      public float[] getRgbExtraAuraColor() {
         if (this.rgbExtraAuraColor == null) {
            this.rgbExtraAuraColor = ColorUtils.hexToRgb(this.getExtraAuraColor());
         }

         return this.rgbExtraAuraColor;
      }

      public String getTintColor() {
         return this.tintColor != null ? this.tintColor : "";
      }

      public double getTintIntensity() {
         return this.tintIntensity != null ? Math.max(0.0, this.tintIntensity) : 0.0;
      }

      public float[] getRgbTintColor() {
         if (this.rgbTintColor == null && this.tintColor != null && !this.tintColor.isEmpty()) {
            this.rgbTintColor = ColorUtils.hexToRgb(this.tintColor);
         }

         return this.rgbTintColor;
      }

      public boolean hasTint() {
         return this.getTintIntensity() > 0.0 && this.getRgbTintColor() != null;
      }

      @Generated
      public void setName(String name) {
         this.name = name;
      }

      @Generated
      public void setUnlockOnSkillLevel(Integer unlockOnSkillLevel) {
         this.unlockOnSkillLevel = unlockOnSkillLevel;
      }

      @Generated
      public void setFormCombo(String formCombo) {
         this.formCombo = formCombo;
      }

      @Generated
      public void setCustomModel(String customModel) {
         this.customModel = customModel;
      }

      @Generated
      public void setKeepBaseFormHeadBones(boolean keepBaseFormHeadBones) {
         this.keepBaseFormHeadBones = keepBaseFormHeadBones;
      }

      @Generated
      public void setTransformationAnimation(String transformationAnimation) {
         this.transformationAnimation = transformationAnimation;
      }

      @Generated
      public void setBodyColor1(String bodyColor1) {
         this.bodyColor1 = bodyColor1;
      }

      @Generated
      public void setBodyColor2(String bodyColor2) {
         this.bodyColor2 = bodyColor2;
      }

      @Generated
      public void setBodyColor3(String bodyColor3) {
         this.bodyColor3 = bodyColor3;
      }

      @Generated
      public void setExtraFormLayer(String extraFormLayer) {
         this.extraFormLayer = extraFormLayer;
      }

      @Generated
      public void setExtraFormColor(String extraFormColor) {
         this.extraFormColor = extraFormColor;
      }

      @Generated
      public void setHairType(String hairType) {
         this.hairType = hairType;
      }

      @Generated
      public void setForcedHairCode(String forcedHairCode) {
         this.forcedHairCode = forcedHairCode;
      }

      @Generated
      public void setHairColor(String hairColor) {
         this.hairColor = hairColor;
      }

      @Generated
      public void setEye1Color(String eye1Color) {
         this.eye1Color = eye1Color;
      }

      @Generated
      public void setEye2Color(String eye2Color) {
         this.eye2Color = eye2Color;
      }

      @Generated
      public void setAuraType(String auraType) {
         this.auraType = auraType;
      }

      @Generated
      public void setAuraLayer(Integer auraLayer) {
         this.auraLayer = auraLayer;
      }

      @Generated
      public void setAuraColor(String auraColor) {
         this.auraColor = auraColor;
      }

      @Generated
      public void setExtraAuraLayer(Integer extraAuraLayer) {
         this.extraAuraLayer = extraAuraLayer;
      }

      @Generated
      public void setExtraAuraColor(String extraAuraColor) {
         this.extraAuraColor = extraAuraColor;
      }

      @Generated
      public void setExtraAuraType(String extraAuraType) {
         this.extraAuraType = extraAuraType;
      }

      @Generated
      public void setHasLightnings(Boolean hasLightnings) {
         this.hasLightnings = hasLightnings;
      }

      @Generated
      public void setLightningColor(String lightningColor) {
         this.lightningColor = lightningColor;
      }

      @Generated
      public void setTintColor(String tintColor) {
         this.tintColor = tintColor;
      }

      @Generated
      public void setTintIntensity(Double tintIntensity) {
         this.tintIntensity = tintIntensity;
      }

      @Generated
      public void setModelScaling(Float[] modelScaling) {
         this.modelScaling = modelScaling;
      }

      @Generated
      public void setStrMultiplier(Double strMultiplier) {
         this.strMultiplier = strMultiplier;
      }

      @Generated
      public void setSkpMultiplier(Double skpMultiplier) {
         this.skpMultiplier = skpMultiplier;
      }

      @Generated
      public void setStmMultiplier(Double stmMultiplier) {
         this.stmMultiplier = stmMultiplier;
      }

      @Generated
      public void setDefMultiplier(Double defMultiplier) {
         this.defMultiplier = defMultiplier;
      }

      @Generated
      public void setVitMultiplier(Double vitMultiplier) {
         this.vitMultiplier = vitMultiplier;
      }

      @Generated
      public void setPwrMultiplier(Double pwrMultiplier) {
         this.pwrMultiplier = pwrMultiplier;
      }

      @Generated
      public void setEneMultiplier(Double eneMultiplier) {
         this.eneMultiplier = eneMultiplier;
      }

      @Generated
      public void setSpeedMultiplier(Double speedMultiplier) {
         this.speedMultiplier = speedMultiplier;
      }

      @Generated
      public void setStaminaDrainMultiplier(Double staminaDrainMultiplier) {
         this.staminaDrainMultiplier = staminaDrainMultiplier;
      }

      @Generated
      public void setEnergyDrain(Double energyDrain) {
         this.energyDrain = energyDrain;
      }

      @Generated
      public void setStaminaDrain(Double staminaDrain) {
         this.staminaDrain = staminaDrain;
      }

      @Generated
      public void setHealthDrain(Double healthDrain) {
         this.healthDrain = healthDrain;
      }

      @Generated
      public void setAttackSpeed(Double attackSpeed) {
         this.attackSpeed = attackSpeed;
      }

      @Generated
      public void setMaxMastery(Double maxMastery) {
         this.maxMastery = maxMastery;
      }

      @Generated
      public void setMasteryPerHitDealt(Double masteryPerHitDealt) {
         this.masteryPerHitDealt = masteryPerHitDealt;
      }

      @Generated
      public void setMasteryPerHitReceived(Double masteryPerHitReceived) {
         this.masteryPerHitReceived = masteryPerHitReceived;
      }

      @Generated
      public void setPassiveMasteryEveryFiveSeconds(Double passiveMasteryEveryFiveSeconds) {
         this.passiveMasteryEveryFiveSeconds = passiveMasteryEveryFiveSeconds;
      }

      @Generated
      public void setMaxCostMultiplier(Double maxCostMultiplier) {
         this.maxCostMultiplier = maxCostMultiplier;
      }

      @Generated
      public void setMaxStatsMultiplier(Double maxStatsMultiplier) {
         this.maxStatsMultiplier = maxStatsMultiplier;
      }

      @Generated
      public void setFormRequisite(String formRequisite) {
         this.formRequisite = formRequisite;
      }

      @Generated
      public void setFormRequisiteType(String formRequisiteType) {
         this.formRequisiteType = formRequisiteType;
      }

      @Generated
      public void setUnlockOnMastery(Double unlockOnMastery) {
         this.unlockOnMastery = unlockOnMastery;
      }

      @Generated
      public void setStackOnMastery(Double stackOnMastery) {
         this.stackOnMastery = stackOnMastery;
      }

      @Generated
      public void setInstantTransformOnMastery(Double instantTransformOnMastery) {
         this.instantTransformOnMastery = instantTransformOnMastery;
      }

      @Generated
      public void setAllowFreeTransformOnMastery(Double allowFreeTransformOnMastery) {
         this.allowFreeTransformOnMastery = allowFreeTransformOnMastery;
      }

      @Generated
      public void setFormStackable(Boolean formStackable) {
         this.formStackable = formStackable;
      }

      @Generated
      public void setStackDrainMultiplier(Double stackDrainMultiplier) {
         this.stackDrainMultiplier = stackDrainMultiplier;
      }

      @Generated
      public void setIncompatibleWith(List<String> incompatibleWith) {
         this.incompatibleWith = incompatibleWith;
      }

      @Generated
      public void setShareMasteryWith(List<String> shareMasteryWith) {
         this.shareMasteryWith = shareMasteryWith;
      }

      @Generated
      public void setShareMasteryMultiplier(Double shareMasteryMultiplier) {
         this.shareMasteryMultiplier = shareMasteryMultiplier;
      }

      @Generated
      public void setOutlineShader(FormConfig.FormData.OutlineShaderConfig outlineShader) {
         this.outlineShader = outlineShader;
      }

      @Generated
      public void setTriggerItemCosts(List<FormConfig.FormData.TriggerItemCost> triggerItemCosts) {
         this.triggerItemCosts = triggerItemCosts;
      }

      @Generated
      public void setDurationItemCosts(List<FormConfig.FormData.DurationItemCost> durationItemCosts) {
         this.durationItemCosts = durationItemCosts;
      }

      @Generated
      public void setMobEffects(List<FormConfig.FormData.MobEffectConfig> mobEffects) {
         this.mobEffects = mobEffects;
      }

      @Generated
      public void setRgbBodyColor1(float[] rgbBodyColor1) {
         this.rgbBodyColor1 = rgbBodyColor1;
      }

      @Generated
      public void setRgbBodyColor2(float[] rgbBodyColor2) {
         this.rgbBodyColor2 = rgbBodyColor2;
      }

      @Generated
      public void setRgbBodyColor3(float[] rgbBodyColor3) {
         this.rgbBodyColor3 = rgbBodyColor3;
      }

      @Generated
      public void setRgbHairColor(float[] rgbHairColor) {
         this.rgbHairColor = rgbHairColor;
      }

      @Generated
      public void setRgbEye1Color(float[] rgbEye1Color) {
         this.rgbEye1Color = rgbEye1Color;
      }

      @Generated
      public void setRgbEye2Color(float[] rgbEye2Color) {
         this.rgbEye2Color = rgbEye2Color;
      }

      @Generated
      public void setRgbAuraColor(float[] rgbAuraColor) {
         this.rgbAuraColor = rgbAuraColor;
      }

      @Generated
      public void setRgbExtraFormColor(float[] rgbExtraFormColor) {
         this.rgbExtraFormColor = rgbExtraFormColor;
      }

      @Generated
      public void setRgbExtraAuraColor(float[] rgbExtraAuraColor) {
         this.rgbExtraAuraColor = rgbExtraAuraColor;
      }

      @Generated
      public void setRgbTintColor(float[] rgbTintColor) {
         this.rgbTintColor = rgbTintColor;
      }

      @Generated
      public String getName() {
         return this.name;
      }

      @Generated
      public Integer getUnlockOnSkillLevel() {
         return this.unlockOnSkillLevel;
      }

      @Generated
      public String getFormCombo() {
         return this.formCombo;
      }

      @Generated
      public String getCustomModel() {
         return this.customModel;
      }

      @Generated
      public boolean isKeepBaseFormHeadBones() {
         return this.keepBaseFormHeadBones;
      }

      @Generated
      public String getBodyColor1() {
         return this.bodyColor1;
      }

      @Generated
      public String getBodyColor2() {
         return this.bodyColor2;
      }

      @Generated
      public String getBodyColor3() {
         return this.bodyColor3;
      }

      @Generated
      public String getExtraFormColor() {
         return this.extraFormColor;
      }

      @Generated
      public String getHairType() {
         return this.hairType;
      }

      @Generated
      public String getForcedHairCode() {
         return this.forcedHairCode;
      }

      @Generated
      public String getHairColor() {
         return this.hairColor;
      }

      @Generated
      public String getEye1Color() {
         return this.eye1Color;
      }

      @Generated
      public String getEye2Color() {
         return this.eye2Color;
      }

      @Generated
      public String getAuraType() {
         return this.auraType;
      }

      @Generated
      public Integer getAuraLayer() {
         return this.auraLayer;
      }

      @Generated
      public String getAuraColor() {
         return this.auraColor;
      }

      @Generated
      public Boolean getHasLightnings() {
         return this.hasLightnings;
      }

      @Generated
      public String getLightningColor() {
         return this.lightningColor;
      }

      @Generated
      public Float[] getModelScaling() {
         return this.modelScaling;
      }

      @Generated
      public Double getMaxMastery() {
         return this.maxMastery;
      }

      @Generated
      public Boolean getFormStackable() {
         return this.formStackable;
      }

      public static class DurationItemCost {
         private String itemId = "";
         private String itemTag = "";
         private String nbt = "";
         private Integer durationSeconds = 1;

         public String getItemId() {
            return this.itemId != null ? this.itemId.trim() : "";
         }

         public String getItemTag() {
            return this.itemTag != null ? this.itemTag.trim() : "";
         }

         public String getNbt() {
            return this.nbt != null ? this.nbt.trim() : "";
         }

         public int getDurationSeconds() {
            return Math.max(1, this.durationSeconds != null ? this.durationSeconds : 1);
         }

         public boolean hasItemId() {
            return !this.getItemId().isEmpty();
         }

         public boolean hasItemTag() {
            return !this.getItemTag().isEmpty();
         }

         public boolean hasNbt() {
            return !this.getNbt().isEmpty();
         }

         @Generated
         public void setItemId(String itemId) {
            this.itemId = itemId;
         }

         @Generated
         public void setItemTag(String itemTag) {
            this.itemTag = itemTag;
         }

         @Generated
         public void setNbt(String nbt) {
            this.nbt = nbt;
         }

         @Generated
         public void setDurationSeconds(Integer durationSeconds) {
            this.durationSeconds = durationSeconds;
         }
      }

      public static class MobEffectConfig {
         private String effectId = "";
         private Integer amplifier = 0;
         private Integer durationTicks = -1;
         private Boolean ambient = false;
         private Boolean visible = true;
         private Boolean showIcon = true;

         public String getEffectId() {
            return this.effectId != null ? this.effectId.trim() : "";
         }

         public int getAmplifier() {
            return Math.max(0, this.amplifier != null ? this.amplifier : 0);
         }

         public int getDurationTicks() {
            return this.durationTicks != null ? this.durationTicks : -1;
         }

         public boolean isPersistent() {
            return this.getDurationTicks() < 0;
         }

         public boolean isAmbient() {
            return Boolean.TRUE.equals(this.ambient);
         }

         public boolean isVisible() {
            return this.visible == null || this.visible;
         }

         public boolean isShowIcon() {
            return this.showIcon == null || this.showIcon;
         }

         @Generated
         public void setEffectId(String effectId) {
            this.effectId = effectId;
         }

         @Generated
         public void setAmplifier(Integer amplifier) {
            this.amplifier = amplifier;
         }

         @Generated
         public void setDurationTicks(Integer durationTicks) {
            this.durationTicks = durationTicks;
         }

         @Generated
         public void setAmbient(Boolean ambient) {
            this.ambient = ambient;
         }

         @Generated
         public void setVisible(Boolean visible) {
            this.visible = visible;
         }

         @Generated
         public void setShowIcon(Boolean showIcon) {
            this.showIcon = showIcon;
         }

         @Generated
         public Boolean getAmbient() {
            return this.ambient;
         }

         @Generated
         public Boolean getVisible() {
            return this.visible;
         }

         @Generated
         public Boolean getShowIcon() {
            return this.showIcon;
         }
      }

      public static class OutlineShaderConfig {
         private Boolean enabled = false;
         private String primaryColor = "#7FFFFF";
         private String secondaryColor = "#7FFFFF";
         private Double outlineThickness = 1.5;

         public boolean isEnabled() {
            return Boolean.TRUE.equals(this.enabled);
         }

         public String getPrimaryColor() {
            return this.primaryColor != null && !this.primaryColor.isEmpty() ? this.primaryColor : "#7FFFFF";
         }

         public String getSecondaryColor() {
            return this.secondaryColor != null && !this.secondaryColor.isEmpty() ? this.secondaryColor : "#FFD970";
         }

         public double getOutlineThickness() {
            return Math.max(0.0, this.outlineThickness != null ? this.outlineThickness : 1.5);
         }

         @Generated
         public void setEnabled(Boolean enabled) {
            this.enabled = enabled;
         }

         @Generated
         public void setPrimaryColor(String primaryColor) {
            this.primaryColor = primaryColor;
         }

         @Generated
         public void setSecondaryColor(String secondaryColor) {
            this.secondaryColor = secondaryColor;
         }

         @Generated
         public void setOutlineThickness(Double outlineThickness) {
            this.outlineThickness = outlineThickness;
         }

         @Generated
         public Boolean getEnabled() {
            return this.enabled;
         }
      }

      public static class TriggerItemCost {
         private String itemId = "";
         private String itemTag = "";
         private String nbt = "";
         private Integer count = 1;
         private Boolean consume = true;

         public String getItemId() {
            return this.itemId != null ? this.itemId.trim() : "";
         }

         public String getItemTag() {
            return this.itemTag != null ? this.itemTag.trim() : "";
         }

         public String getNbt() {
            return this.nbt != null ? this.nbt.trim() : "";
         }

         public int getCount() {
            return Math.max(1, this.count != null ? this.count : 1);
         }

         public boolean isConsume() {
            return this.consume == null || this.consume;
         }

         public boolean hasItemId() {
            return !this.getItemId().isEmpty();
         }

         public boolean hasItemTag() {
            return !this.getItemTag().isEmpty();
         }

         public boolean hasNbt() {
            return !this.getNbt().isEmpty();
         }

         @Generated
         public void setItemId(String itemId) {
            this.itemId = itemId;
         }

         @Generated
         public void setItemTag(String itemTag) {
            this.itemTag = itemTag;
         }

         @Generated
         public void setNbt(String nbt) {
            this.nbt = nbt;
         }

         @Generated
         public void setCount(Integer count) {
            this.count = count;
         }

         @Generated
         public void setConsume(Boolean consume) {
            this.consume = consume;
         }

         @Generated
         public Boolean getConsume() {
            return this.consume;
         }
      }
   }
}
