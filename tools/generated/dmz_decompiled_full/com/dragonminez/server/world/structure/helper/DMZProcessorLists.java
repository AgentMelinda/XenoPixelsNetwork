package com.dragonminez.server.world.structure.helper;

import com.dragonminez.server.world.structure.processor.FoundationProcessor;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;

public class DMZProcessorLists {
   public static final ResourceKey<StructureProcessorList> FOUNDATION = createKey("foundation");

   public static void bootstrap(BootstrapContext<StructureProcessorList> context) {
      context.register(FOUNDATION, new StructureProcessorList(List.of(new FoundationProcessor(32))));
   }

   private static ResourceKey<StructureProcessorList> createKey(String name) {
      return ResourceKey.create(Registries.PROCESSOR_LIST, ResourceLocation.fromNamespaceAndPath("dragonminez", name));
   }
}
