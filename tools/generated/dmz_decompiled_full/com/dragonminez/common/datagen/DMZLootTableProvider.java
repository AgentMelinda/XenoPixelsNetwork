package com.dragonminez.common.datagen;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.data.PackOutput;
import net.minecraft.data.loot.LootTableProvider;
import net.minecraft.data.loot.LootTableProvider.SubProviderEntry;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;

public class DMZLootTableProvider {
   public static LootTableProvider create(PackOutput output, CompletableFuture<Provider> registries) {
      return new LootTableProvider(
         output, Set.of(), List.of(new SubProviderEntry(regs -> new DMZBlockLootTables(regs), LootContextParamSets.BLOCK)), registries
      );
   }
}
