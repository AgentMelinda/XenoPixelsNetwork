package com.dragonminez.common.datagen;

import java.util.concurrent.CompletableFuture;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.PackOutput;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.EventBusSubscriber.Bus;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import net.neoforged.neoforge.data.event.GatherDataEvent;

@EventBusSubscriber(
   modid = "dragonminez",
   bus = Bus.MOD
)
public class DatagenManager {
   @SubscribeEvent
   public static void gatherData(GatherDataEvent event) {
      DataGenerator generator = event.getGenerator();
      PackOutput packOutput = generator.getPackOutput();
      CompletableFuture<Provider> lookupProvider = event.getLookupProvider();
      ExistingFileHelper existingFileHelper = event.getExistingFileHelper();
      generator.addProvider(event.includeServer(), new DMZRecipeProvider(packOutput, lookupProvider));
      generator.addProvider(event.includeServer(), DMZLootTableProvider.create(packOutput, lookupProvider));
      generator.addProvider(event.includeClient(), new DMZBlockStateProvider(packOutput, existingFileHelper));
      generator.addProvider(event.includeClient(), new DMZItemModelProvider(packOutput, existingFileHelper));
      DMZBlockTagGenerator blockTagGenerator = (DMZBlockTagGenerator)generator.addProvider(
         event.includeServer(), new DMZBlockTagGenerator(packOutput, lookupProvider, existingFileHelper)
      );
      generator.addProvider(event.includeServer(), new DMZItemTagGenerator(packOutput, lookupProvider, blockTagGenerator.contentsGetter(), existingFileHelper));
      generator.addProvider(event.includeServer(), new DMZEntityTypeTagGenerator(packOutput, lookupProvider, existingFileHelper));
      DMZWorldGenProvider worldGenProvider = (DMZWorldGenProvider)generator.addProvider(
         event.includeServer(), new DMZWorldGenProvider(packOutput, lookupProvider)
      );
      generator.addProvider(event.includeServer(), new DMZBiomeTagGenerator(packOutput, worldGenProvider.getRegistryProvider(), existingFileHelper));
      generator.addProvider(event.includeServer(), new DMZAdvancementsProvider(packOutput, lookupProvider));
      generator.addProvider(event.includeServer(), new DMZSpacePodDestinationProvider(packOutput));
      generator.addProvider(event.includeServer(), new DMZDragonDefinitionProvider(packOutput));
      generator.addProvider(event.includeServer(), new DMZDragonWishProvider(packOutput));
   }
}
