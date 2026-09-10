package com.dragonminez.common.config;

import lombok.Generated;

public class GeneralUserConfig {
   public static final String CURRENT_VERSION = "2.1.3";
   private String configVersion;
   private Boolean firstPersonAnimated = true;
   private boolean impactFramesEnabled = false;
   private Boolean techniqueHotbarRightSide = false;
   private Boolean alwaysVisibleHudValues = false;
   private Boolean hideHudNumbers = false;
   private Integer xenoverseHudPosX = 5;
   private Integer xenoverseHudPosY = 5;
   private Float xenoverseHudScale = 1.0F;
   private Boolean advancedDescription = true;
   private Boolean advancedDescriptionPercentage = true;
   private Boolean alternativeHud = false;
   private Boolean hexagonStatsDisplay = false;
   private Float menuScaleMultiplier = 1.0F;
   private Float utilityMenuScaleMultiplier = 1.0F;
   private Integer healthBarPosX = 10;
   private Integer healthBarPosY = 20;
   private Integer energyBarPosX = 10;
   private Integer energyBarPosY = 10;
   private Integer staminaBarPosX = 10;
   private Integer staminaBarPosY = 10;
   private Boolean cameraMovementDuringFlight = true;
   private Boolean liveCrowdinTranslations = true;
   private Boolean showAccumulativeDamage = true;
   private Boolean taiyokenInvertPalette = false;
   private Boolean transformationOutlines = true;
   private Integer overShoulderMode = 2;
   private Boolean overShoulderLeft = false;
   private Float overShoulderBack = 3.0F;
   private Float overShoulderUp = 0.35F;
   private Float overShoulderSide = 1.45F;
   private Float overShoulderSmoothing = 0.4F;

   public Integer getOverShoulderMode() {
      if (this.overShoulderMode == null || this.overShoulderMode < 0 || this.overShoulderMode > 2) {
         this.overShoulderMode = 2;
      }

      return this.overShoulderMode;
   }

   public Boolean getOverShoulderLeft() {
      if (this.overShoulderLeft == null) {
         this.overShoulderLeft = false;
      }

      return this.overShoulderLeft;
   }

   public Float getOverShoulderBack() {
      if (this.overShoulderBack == null || !Float.isFinite(this.overShoulderBack)) {
         this.overShoulderBack = 3.0F;
      }

      return this.overShoulderBack;
   }

   public Float getOverShoulderUp() {
      if (this.overShoulderUp == null || !Float.isFinite(this.overShoulderUp)) {
         this.overShoulderUp = 0.35F;
      }

      return this.overShoulderUp;
   }

   public Float getOverShoulderSide() {
      if (this.overShoulderSide == null || !Float.isFinite(this.overShoulderSide)) {
         this.overShoulderSide = 1.45F;
      }

      return this.overShoulderSide;
   }

   public Float getOverShoulderSmoothing() {
      if (this.overShoulderSmoothing == null || !Float.isFinite(this.overShoulderSmoothing) || this.overShoulderSmoothing <= 0.0F) {
         this.overShoulderSmoothing = 0.4F;
      }

      return Math.min(this.overShoulderSmoothing, 1.0F);
   }

   public Boolean getTaiyokenInvertPalette() {
      if (this.taiyokenInvertPalette == null) {
         this.taiyokenInvertPalette = false;
      }

      return this.taiyokenInvertPalette;
   }

   public Boolean getTransformationOutlines() {
      if (this.transformationOutlines == null) {
         this.transformationOutlines = true;
      }

      return this.transformationOutlines;
   }

   public Boolean getShowAccumulativeDamage() {
      if (this.showAccumulativeDamage == null) {
         this.showAccumulativeDamage = true;
      }

      return this.showAccumulativeDamage;
   }

   public Float getMenuScaleMultiplier() {
      if (!Float.isFinite(this.menuScaleMultiplier) || this.menuScaleMultiplier <= 0.0F) {
         this.menuScaleMultiplier = 1.0F;
      }

      return this.menuScaleMultiplier;
   }

   public Float getUtilityMenuScaleMultiplier() {
      if (this.utilityMenuScaleMultiplier == null || !Float.isFinite(this.utilityMenuScaleMultiplier) || this.utilityMenuScaleMultiplier <= 0.0F) {
         this.utilityMenuScaleMultiplier = 1.0F;
      }

      return this.utilityMenuScaleMultiplier;
   }

   public void setUtilityMenuScaleMultiplier(Float utilityMenuScaleMultiplier) {
      if (utilityMenuScaleMultiplier != null && Float.isFinite(utilityMenuScaleMultiplier) && !(utilityMenuScaleMultiplier <= 0.0F)) {
         this.utilityMenuScaleMultiplier = utilityMenuScaleMultiplier;
      } else {
         this.utilityMenuScaleMultiplier = 1.0F;
      }
   }

   public Float getXenoverseHudScale() {
      if (this.xenoverseHudScale == null || !Float.isFinite(this.xenoverseHudScale) || this.xenoverseHudScale <= 0.0F) {
         this.xenoverseHudScale = 1.0F;
      }

      return this.xenoverseHudScale;
   }

   public void setXenoverseHudScale(Float xenoverseHudScale) {
      if (xenoverseHudScale != null && Float.isFinite(xenoverseHudScale) && !(xenoverseHudScale <= 0.0F)) {
         this.xenoverseHudScale = xenoverseHudScale;
      } else {
         this.xenoverseHudScale = 1.0F;
      }
   }

   public Boolean getTechniqueHotbarRightSide() {
      if (this.techniqueHotbarRightSide == null) {
         this.techniqueHotbarRightSide = false;
      }

      return this.techniqueHotbarRightSide;
   }

   public Boolean getHideHudNumbers() {
      if (this.hideHudNumbers == null) {
         this.hideHudNumbers = false;
      }

      return this.hideHudNumbers;
   }

   public void setMenuScaleMultiplier(Float menuScaleMultiplier) {
      if (Float.isFinite(menuScaleMultiplier) && !(menuScaleMultiplier <= 0.0F)) {
         this.menuScaleMultiplier = menuScaleMultiplier;
      } else {
         this.menuScaleMultiplier = 1.0F;
      }
   }

   @Generated
   public String getConfigVersion() {
      return this.configVersion;
   }

   @Generated
   public Boolean getFirstPersonAnimated() {
      return this.firstPersonAnimated;
   }

   @Generated
   public boolean isImpactFramesEnabled() {
      return this.impactFramesEnabled;
   }

   @Generated
   public Boolean getAlwaysVisibleHudValues() {
      return this.alwaysVisibleHudValues;
   }

   @Generated
   public Integer getXenoverseHudPosX() {
      return this.xenoverseHudPosX;
   }

   @Generated
   public Integer getXenoverseHudPosY() {
      return this.xenoverseHudPosY;
   }

   @Generated
   public Boolean getAdvancedDescription() {
      return this.advancedDescription;
   }

   @Generated
   public Boolean getAdvancedDescriptionPercentage() {
      return this.advancedDescriptionPercentage;
   }

   @Generated
   public Boolean getAlternativeHud() {
      return this.alternativeHud;
   }

   @Generated
   public Boolean getHexagonStatsDisplay() {
      return this.hexagonStatsDisplay;
   }

   @Generated
   public Integer getHealthBarPosX() {
      return this.healthBarPosX;
   }

   @Generated
   public Integer getHealthBarPosY() {
      return this.healthBarPosY;
   }

   @Generated
   public Integer getEnergyBarPosX() {
      return this.energyBarPosX;
   }

   @Generated
   public Integer getEnergyBarPosY() {
      return this.energyBarPosY;
   }

   @Generated
   public Integer getStaminaBarPosX() {
      return this.staminaBarPosX;
   }

   @Generated
   public Integer getStaminaBarPosY() {
      return this.staminaBarPosY;
   }

   @Generated
   public Boolean getCameraMovementDuringFlight() {
      return this.cameraMovementDuringFlight;
   }

   @Generated
   public Boolean getLiveCrowdinTranslations() {
      return this.liveCrowdinTranslations;
   }

   @Generated
   public void setFirstPersonAnimated(Boolean firstPersonAnimated) {
      this.firstPersonAnimated = firstPersonAnimated;
   }

   @Generated
   public void setImpactFramesEnabled(boolean impactFramesEnabled) {
      this.impactFramesEnabled = impactFramesEnabled;
   }

   @Generated
   public void setTechniqueHotbarRightSide(Boolean techniqueHotbarRightSide) {
      this.techniqueHotbarRightSide = techniqueHotbarRightSide;
   }

   @Generated
   public void setAlwaysVisibleHudValues(Boolean alwaysVisibleHudValues) {
      this.alwaysVisibleHudValues = alwaysVisibleHudValues;
   }

   @Generated
   public void setHideHudNumbers(Boolean hideHudNumbers) {
      this.hideHudNumbers = hideHudNumbers;
   }

   @Generated
   public void setXenoverseHudPosX(Integer xenoverseHudPosX) {
      this.xenoverseHudPosX = xenoverseHudPosX;
   }

   @Generated
   public void setXenoverseHudPosY(Integer xenoverseHudPosY) {
      this.xenoverseHudPosY = xenoverseHudPosY;
   }

   @Generated
   public void setAdvancedDescription(Boolean advancedDescription) {
      this.advancedDescription = advancedDescription;
   }

   @Generated
   public void setAdvancedDescriptionPercentage(Boolean advancedDescriptionPercentage) {
      this.advancedDescriptionPercentage = advancedDescriptionPercentage;
   }

   @Generated
   public void setAlternativeHud(Boolean alternativeHud) {
      this.alternativeHud = alternativeHud;
   }

   @Generated
   public void setHexagonStatsDisplay(Boolean hexagonStatsDisplay) {
      this.hexagonStatsDisplay = hexagonStatsDisplay;
   }

   @Generated
   public void setHealthBarPosX(Integer healthBarPosX) {
      this.healthBarPosX = healthBarPosX;
   }

   @Generated
   public void setHealthBarPosY(Integer healthBarPosY) {
      this.healthBarPosY = healthBarPosY;
   }

   @Generated
   public void setEnergyBarPosX(Integer energyBarPosX) {
      this.energyBarPosX = energyBarPosX;
   }

   @Generated
   public void setEnergyBarPosY(Integer energyBarPosY) {
      this.energyBarPosY = energyBarPosY;
   }

   @Generated
   public void setStaminaBarPosX(Integer staminaBarPosX) {
      this.staminaBarPosX = staminaBarPosX;
   }

   @Generated
   public void setStaminaBarPosY(Integer staminaBarPosY) {
      this.staminaBarPosY = staminaBarPosY;
   }

   @Generated
   public void setCameraMovementDuringFlight(Boolean cameraMovementDuringFlight) {
      this.cameraMovementDuringFlight = cameraMovementDuringFlight;
   }

   @Generated
   public void setLiveCrowdinTranslations(Boolean liveCrowdinTranslations) {
      this.liveCrowdinTranslations = liveCrowdinTranslations;
   }

   @Generated
   public void setShowAccumulativeDamage(Boolean showAccumulativeDamage) {
      this.showAccumulativeDamage = showAccumulativeDamage;
   }

   @Generated
   public void setTaiyokenInvertPalette(Boolean taiyokenInvertPalette) {
      this.taiyokenInvertPalette = taiyokenInvertPalette;
   }

   @Generated
   public void setTransformationOutlines(Boolean transformationOutlines) {
      this.transformationOutlines = transformationOutlines;
   }

   @Generated
   public void setOverShoulderMode(Integer overShoulderMode) {
      this.overShoulderMode = overShoulderMode;
   }

   @Generated
   public void setOverShoulderLeft(Boolean overShoulderLeft) {
      this.overShoulderLeft = overShoulderLeft;
   }

   @Generated
   public void setOverShoulderBack(Float overShoulderBack) {
      this.overShoulderBack = overShoulderBack;
   }

   @Generated
   public void setOverShoulderUp(Float overShoulderUp) {
      this.overShoulderUp = overShoulderUp;
   }

   @Generated
   public void setOverShoulderSide(Float overShoulderSide) {
      this.overShoulderSide = overShoulderSide;
   }

   @Generated
   public void setOverShoulderSmoothing(Float overShoulderSmoothing) {
      this.overShoulderSmoothing = overShoulderSmoothing;
   }

   @Generated
   public void setConfigVersion(String configVersion) {
      this.configVersion = configVersion;
   }
}
