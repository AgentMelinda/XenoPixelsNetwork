package net.bullettrain.xenopixelsmod.item;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.block.ModBlocks;
import net.bullettrain.xenopixelsmod.item.custom.MetalDetectorItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.awt.*;

public class ModCreativeModTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, XenoPixelsMod.MOD_ID);

    public static final RegistryObject<CreativeModeTab> XENOPIXELS_TAB = CREATIVE_MODE_TABS.register("xenopixels_tab",
            () -> CreativeModeTab.builder().icon(() -> new ItemStack(ModsItems.SAPPHIRE.get()))
                    .title(Component.translatable("creativetab.xenopixels_tab"))
                    .displayItems((pParameters, pOutput) -> {
                        pOutput.accept(ModsItems.SAPPHIRE.get());
                        pOutput.accept(ModsItems.RAW_SAPPHIRE.get());
                        pOutput.accept(ModsItems.STRAWBERRY_SENZU.get());

                        pOutput.accept(ModsItems.METAL_DETECTOR.get());

                        pOutput.accept(ModsItems.SUPER_SOUL_WARRIOR.get());
                        pOutput.accept(ModsItems.SUPER_SOUL_IRON.get());
                        pOutput.accept(ModsItems.SUPER_SOUL_SPARK.get());
                        pOutput.accept(ModsItems.SUPER_SOUL_FINISHER.get());
                        pOutput.accept(ModsItems.SUPER_SOUL_BALANCED.get());

                        //pOutput.accept(ModBlocks.MISSILE_CHUNK_LOADER.get());
                        //pOutput.accept(ModBlocks.SHIP_VLS_GUIDANCE.get());

                        pOutput.accept(ModBlocks.JACKIETONITE_ORE_BLOCK.get());
                        pOutput.accept(ModBlocks.RAW_JACKIETONITE_ORE_BLOCK.get());
                        pOutput.accept(ModBlocks.JACKIETONITE_ORE.get());
                        pOutput.accept(ModBlocks.DEEPSLATE_JACKIETONITE_ORE.get());
                        pOutput.accept(ModBlocks.NETHER_JACKIETONITE_ORE.get());
                        pOutput.accept(ModBlocks.END_STONE_JACKIETONITE_ORE.get());
                        pOutput.accept(ModBlocks.SOUND_BLOCK.get());

                    })
                    .build());

    public static void register(IEventBus eventBus) {
        CREATIVE_MODE_TABS.register(eventBus);
    }
}
