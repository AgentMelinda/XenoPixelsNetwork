package net.bullettrain.tutorialmod.item;

import net.bullettrain.tutorialmod.TutorialMod;
import net.bullettrain.tutorialmod.block.ModBlocks;
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
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, TutorialMod.MOD_ID);

    public static final RegistryObject<CreativeModeTab> TUTORIAL_TAB = CREATIVE_MODE_TABS.register("tutorial_tab",
            () -> CreativeModeTab.builder().icon(() -> new ItemStack(ModsItems.SAPPHIRE.get()))
                    .title(Component.translatable("creativetab.tutorial_tab"))
                    .displayItems((pParameters, pOutput) -> {
                        pOutput.accept(ModsItems.SAPPHIRE.get());
                        pOutput.accept(ModsItems.RAW_SAPPHIRE.get());

                        pOutput.accept(ModBlocks.JACKIETONITE_ORE_BLOCK.get());
                        pOutput.accept(ModBlocks.RAW_JACKIETONITE_ORE_BLOCK.get());
                        pOutput.accept(ModBlocks.JACKIETONITE_ORE.get());
                        pOutput.accept(ModBlocks.DEEPSLATE_JACKIETONITE_ORE.get());
                        pOutput.accept(ModBlocks.NETHER_JACKIETONITE_ORE.get());
                        pOutput.accept(ModBlocks.END_STONE_JACKIETONITE_ORE.get());

                    })
                    .build());

    public static void register(IEventBus eventBus) {
        CREATIVE_MODE_TABS.register(eventBus);
    }
}
