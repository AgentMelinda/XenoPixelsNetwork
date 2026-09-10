package com.dragonminez.server.world.tree;

import com.dragonminez.server.world.feature.NamekConfiguredFeatures;
import java.util.Optional;
import net.minecraft.world.level.block.grower.TreeGrower;

public final class NamekAjissaGrower {
   public static final TreeGrower INSTANCE = new TreeGrower(
      "namek_ajissa", Optional.empty(), Optional.of(NamekConfiguredFeatures.AJISSA_TREE), Optional.empty()
   );

   private NamekAjissaGrower() {
   }
}
