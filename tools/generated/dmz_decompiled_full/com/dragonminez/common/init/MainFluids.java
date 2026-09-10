package com.dragonminez.common.init;

import com.dragonminez.common.init.fluid.SimpleFluid;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.fluids.BaseFlowingFluid.Flowing;
import net.neoforged.neoforge.fluids.BaseFlowingFluid.Source;
import net.neoforged.neoforge.fluids.FluidType.Properties;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries.Keys;

public final class MainFluids {
   public static final DeferredRegister<FluidType> FLUID_TYPE_REGISTER = DeferredRegister.create(Keys.FLUID_TYPES, "dragonminez");
   public static final DeferredRegister<Fluid> FLUIDS_REGISTER = DeferredRegister.create(BuiltInRegistries.FLUID, "dragonminez");
   public static final DeferredHolder<FluidType, FluidType> HEALING_FLUID_TYPE = FLUID_TYPE_REGISTER.register(
      "healing_fluid_type", () -> new SimpleFluid(10799479, Properties.create().lightLevel(5).canDrown(false).canSwim(true).canExtinguish(true))
   );
   public static final DeferredHolder<Fluid, FlowingFluid> SOURCE_HEALING = FLUIDS_REGISTER.register(
      "healing_fluid", () -> new Source(MainFluids.HEALING_FLUID_PROPERTIES)
   );
   public static final DeferredHolder<Fluid, FlowingFluid> FLOWING_HEALING = FLUIDS_REGISTER.register(
      "flowing_healing_fluid", () -> new Flowing(MainFluids.HEALING_FLUID_PROPERTIES)
   );
   public static final net.neoforged.neoforge.fluids.BaseFlowingFluid.Properties HEALING_FLUID_PROPERTIES = new net.neoforged.neoforge.fluids.BaseFlowingFluid.Properties(
         HEALING_FLUID_TYPE, SOURCE_HEALING, FLOWING_HEALING
      )
      .block(MainBlocks.HEALING_LIQUID)
      .bucket(MainItems.HEALING_BUCKET)
      .slopeFindDistance(4)
      .levelDecreasePerBlock(1);
   public static final DeferredHolder<FluidType, FluidType> NAMEK_FLUID_TYPE = FLUID_TYPE_REGISTER.register(
      "namek_water_fluid_type",
      () -> new SimpleFluid(11465561, Properties.create().lightLevel(5).density(1000).viscosity(1000).canSwim(true).canDrown(true).canExtinguish(true))
   );
   public static final DeferredHolder<Fluid, FlowingFluid> SOURCE_NAMEK = FLUIDS_REGISTER.register(
      "namek_water_fluid", () -> new Source(MainFluids.NAMEK_FLUID_PROPERTIES)
   );
   public static final DeferredHolder<Fluid, FlowingFluid> FLOWING_NAMEK = FLUIDS_REGISTER.register(
      "flowing_namek_water_fluid", () -> new Flowing(MainFluids.NAMEK_FLUID_PROPERTIES)
   );
   public static final net.neoforged.neoforge.fluids.BaseFlowingFluid.Properties NAMEK_FLUID_PROPERTIES = new net.neoforged.neoforge.fluids.BaseFlowingFluid.Properties(
         NAMEK_FLUID_TYPE, SOURCE_NAMEK, FLOWING_NAMEK
      )
      .block(MainBlocks.NAMEK_WATER_LIQUID)
      .bucket(MainItems.NAMEK_WATER_BUCKET)
      .slopeFindDistance(4)
      .levelDecreasePerBlock(1);

   public static void register(IEventBus bus) {
      FLUID_TYPE_REGISTER.register(bus);
      FLUIDS_REGISTER.register(bus);
   }
}
