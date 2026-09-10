package com.dragonminez.common.util.types.items;

import com.google.gson.GsonBuilder;
import java.util.Map;
import lombok.Generated;

public class LingeringPotionDTO extends PotionDTO {
   public LingeringPotionDTO(String potion, Map<String, Integer> mobEffects) {
      super("lingering_potion", "minecraft:lingering_potion", 1, potion, mobEffects);
   }

   @Override
   public String toJson() {
      return new GsonBuilder().setPrettyPrinting().create().toJson(this, LingeringPotionDTO.class);
   }

   @Generated
   public LingeringPotionDTO() {
   }
}
