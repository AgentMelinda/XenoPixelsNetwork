package com.dragonminez.common.quest.objectives;

import com.dragonminez.common.quest.QuestObjective;
import lombok.Generated;
import net.minecraft.world.level.biome.Biome;

public class BiomeObjective extends QuestObjective {
   private final String biomeId;

   public BiomeObjective(String biomeId) {
      super(QuestObjective.ObjectiveType.BIOME, 1);
      this.biomeId = biomeId;
   }

   @Override
   public boolean checkProgress(Object... params) {
      if (params.length > 0 && params[0] instanceof Biome biome) {
         String currentBiome = biome.toString();
         if (currentBiome.contains(this.biomeId)) {
            this.setProgress(1);
            return true;
         }
      }

      return false;
   }

   @Generated
   public String getBiomeId() {
      return this.biomeId;
   }
}
