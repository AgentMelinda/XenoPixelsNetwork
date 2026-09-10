package com.dragonminez.server.world.tree;

import com.dragonminez.server.world.feature.NamekConfiguredFeatures;
import java.util.Optional;
import net.minecraft.world.level.block.grower.TreeGrower;

public final class NamekSacredGrower {
   public static final TreeGrower INSTANCE = new TreeGrower(
      "namek_sacred", Optional.empty(), Optional.of(NamekConfiguredFeatures.SACRED_TREE), Optional.empty()
   );

   private NamekSacredGrower() {
   }
}
