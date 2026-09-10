package com.dragonminez.common.util.types.items;

import com.google.gson.GsonBuilder;
import java.util.Map;
import lombok.Generated;

public class TippedArrowDTO extends PotionDTO {
   public TippedArrowDTO(String potion, Integer count, Map<String, Integer> mobEffects) {
      super("tipped_arrow", "minecraft:tipped_arrow", count, potion, mobEffects);
   }

   @Override
   public String toJson() {
      return new GsonBuilder().setPrettyPrinting().create().toJson(this, TippedArrowDTO.class);
   }

   @Generated
   public TippedArrowDTO() {
   }
}
