package com.dragonminez.common.init;

import com.google.common.collect.ImmutableSet;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class MainVillagers {
   public static final DeferredRegister<PoiType> POI_TYPES = DeferredRegister.create(BuiltInRegistries.POINT_OF_INTEREST_TYPE, "dragonminez");
   public static final DeferredRegister<VillagerProfession> PROFESSIONS = DeferredRegister.create(BuiltInRegistries.VILLAGER_PROFESSION, "dragonminez");
   public static final DeferredHolder<PoiType, PoiType> CAPSULE_CORP_POI = POI_TYPES.register("capsule_corp", () -> new PoiType(fuelGeneratorStates(), 1, 1));
   public static final DeferredHolder<VillagerProfession, VillagerProfession> CAPSULE_CORP_ASSISTANT = PROFESSIONS.register(
      "capsule_corp_assistant",
      () -> new VillagerProfession(
            "capsule_corp_assistant",
            holder -> holder.is(CAPSULE_CORP_POI.getKey()),
            holder -> holder.is(CAPSULE_CORP_POI.getKey()),
            ImmutableSet.of(),
            ImmutableSet.of(),
            SoundEvents.VILLAGER_WORK_CARTOGRAPHER
         )
   );

   private static Set<BlockState> fuelGeneratorStates() {
      return ImmutableSet.copyOf(((Block)MainBlocks.FUEL_GENERATOR.get()).getStateDefinition().getPossibleStates());
   }

   public static void register(IEventBus modEventBus) {
      POI_TYPES.register(modEventBus);
      PROFESSIONS.register(modEventBus);
   }

   private MainVillagers() {
   }
}
