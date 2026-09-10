package com.dragonminez.common.init.item.consumables;

import lombok.Generated;

public enum CapsuleType {
   STR("STR", 5),
   SKP("SKP", 5),
   RES("RES", 5),
   VIT("VIT", 5),
   PWR("PWR", 5),
   ENE("ENE", 5);

   private final String statName;
   private final Integer statPoints;

   @Generated
   public String getStatName() {
      return this.statName;
   }

   @Generated
   public Integer getStatPoints() {
      return this.statPoints;
   }

   @Generated
   private CapsuleType(final String statName, final Integer statPoints) {
      this.statName = statName;
      this.statPoints = statPoints;
   }
}
