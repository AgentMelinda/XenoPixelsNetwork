package com.dragonminez.common.datagen;

import com.dragonminez.common.init.MainItems;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementType;
import net.minecraft.advancements.Advancement.Builder;
import net.minecraft.advancements.critereon.PlayerTrigger.TriggerInstance;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.data.PackOutput;
import net.minecraft.data.advancements.AdvancementProvider;
import net.minecraft.data.advancements.AdvancementSubProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ItemLike;

public class DMZAdvancementsProvider extends AdvancementProvider {
   public DMZAdvancementsProvider(PackOutput output, CompletableFuture<Provider> registries) {
      super(output, registries, List.of(new DMZAdvancementsProvider.DMZAdvancements()));
   }

   private static class DMZAdvancements implements AdvancementSubProvider {
      public void generate(Provider provider, Consumer<AdvancementHolder> consumer) {
         Builder.advancement()
            .display(
               (ItemLike)MainItems.DBALL4_BLOCK_ITEM.get(),
               Component.translatable("advancements.dragonminez.root.title"),
               Component.translatable("advancements.dragonminez.root.description"),
               ResourceLocation.fromNamespaceAndPath("dragonminez", "textures/block/rocky_stone.png"),
               AdvancementType.TASK,
               true,
               true,
               false
            )
            .addCriterion("tick", TriggerInstance.tick())
            .save(consumer, "dragonminez:root");
      }
   }
}
