package net.bullettrain.xenopixelsmod.item;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.block.ModBlocks;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

public class ModCreativeModTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, XenoPixelsMod.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> XENOPIXELS_TAB = CREATIVE_MODE_TABS.register("xenopixels_tab",
            () -> CreativeModeTab.builder().icon(() -> new ItemStack(ModsItems.SAPPHIRE.get()))
                    .title(Component.translatable("creativetab.xenopixels_tab"))
                    .displayItems((pParameters, pOutput) -> {
                        pOutput.accept(ModsItems.SAPPHIRE.get());
                        pOutput.accept(ModsItems.RAW_SAPPHIRE.get());
                        pOutput.accept(ModsItems.STRAWBERRY_SENZU.get());
                        pOutput.accept(ModsItems.METAL_DETECTOR.get());
                        pOutput.accept(ModsItems.TARGET_TOOL.get());
                        pOutput.accept(ModsItems.TARGET_TOOL_FORK.get());
                        pOutput.accept(ModsItems.MISSILE_RADAR.get());
                        pOutput.accept(ModsItems.PANEL_CONFIGURATOR.get());
                        pOutput.accept(ModsItems.PANEL_CONFIGURATOR_FORK.get());
                        pOutput.accept(ModsItems.XENO_NPC_WAND.get());
                        // The path tool was registered but never listed here, so the only way to
                        // get one was /give - which is a tool nobody can find.
                        pOutput.accept(ModsItems.XENO_NPC_PATH_TOOL.get());
                        pOutput.accept(ModsItems.XENO_NPC_CLONER.get());
                        pOutput.accept(ModsItems.XENO_NPC_JAR.get());
                        pOutput.accept(ModsItems.XENO_NPC_MOUNTER.get());
                        pOutput.accept(ModsItems.XENO_NPC_TELEPORTER.get());
                        pOutput.accept(ModsItems.XENO_NPC_SCRIPT_TOOL.get());
                        pOutput.accept(ModsItems.ZENI_1.get());
                        pOutput.accept(ModsItems.ZENI_10.get());
                        pOutput.accept(ModsItems.ZENI_25.get());
                        pOutput.accept(ModsItems.ZENI_50.get());
                        pOutput.accept(ModsItems.ZENI_100.get());
                        pOutput.accept(ModsItems.ZENI_200.get());
                        pOutput.accept(ModsItems.ZENI_250.get());
                        pOutput.accept(ModsItems.ZENI_500.get());
                        pOutput.accept(ModsItems.ZENI_1000.get());
                        pOutput.accept(ModsItems.ZENI_NOTE_10000.get());
                        pOutput.accept(ModsItems.ZENI_NOTE_100000.get());
                        pOutput.accept(ModsItems.ZENI_NOTE_1000000.get());
                        pOutput.accept(ModsItems.SUPER_SOUL_WARRIOR.get());
                        pOutput.accept(ModsItems.SUPER_SOUL_IRON.get());
                        pOutput.accept(ModsItems.SUPER_SOUL_SPARK.get());
                        pOutput.accept(ModsItems.SUPER_SOUL_FINISHER.get());
                        pOutput.accept(ModsItems.SUPER_SOUL_BALANCED.get());
                        pOutput.accept(ModsItems.MISSILE_SMALL.get());
                        pOutput.accept(ModsItems.MISSILE_MEDIUM.get());
                        pOutput.accept(ModsItems.MISSILE_LARGE.get());
                        pOutput.accept(ModsItems.MISSILE_MEGA.get());

                        pOutput.accept(ModBlocks.SHIP_VLS_GUIDANCE.get());
                        pOutput.accept(ModBlocks.SHIP_VLS_GUIDANCE_FORK.get());
                        pOutput.accept(ModBlocks.MISSILE_TUBE.get());
                        pOutput.accept(ModBlocks.MISSILE_TUBE_FORK.get());
                        pOutput.accept(ModBlocks.MISSILE_CHUNK_LOADER.get());
                        pOutput.accept(ModBlocks.MISSILE_CHUNK_LOADER_FORK.get());
                        pOutput.accept(ModBlocks.SHIP_THRUSTER.get());
                        pOutput.accept(ModBlocks.SHIP_THRUSTER_FORK.get());
                        pOutput.accept(ModBlocks.PILOT_SEAT.get());
                        pOutput.accept(ModBlocks.PILOT_SEAT_FORK.get());
                        pOutput.accept(ModBlocks.WING_PANEL.get());
                        pOutput.accept(ModBlocks.WING_PANEL_FORK.get());
                        pOutput.accept(ModBlocks.WING_FLAP_HORIZONTAL.get());
                        pOutput.accept(ModBlocks.WING_FLAP_HORIZONTAL_FORK.get());
                        pOutput.accept(ModBlocks.WING_FLAP_VERTICAL.get());
                        pOutput.accept(ModBlocks.WING_FLAP_VERTICAL_FORK.get());
                        pOutput.accept(ModBlocks.COPYCAT_WING_PANEL.get());
                        pOutput.accept(ModBlocks.COPYCAT_WING_PANEL_FORK.get());
                        pOutput.accept(ModBlocks.COPYCAT_WING_PANEL_LIT.get());
                        pOutput.accept(ModBlocks.COPYCAT_WING_PANEL_LIT_FORK.get());
                        pOutput.accept(ModBlocks.COPYCAT_WING_FLAP_HORIZONTAL.get());
                        pOutput.accept(ModBlocks.COPYCAT_WING_FLAP_HORIZONTAL_FORK.get());
                        pOutput.accept(ModBlocks.COPYCAT_WING_FLAP_HORIZONTAL_LIT.get());
                        pOutput.accept(ModBlocks.COPYCAT_WING_FLAP_HORIZONTAL_LIT_FORK.get());
                        pOutput.accept(ModBlocks.COPYCAT_WING_FLAP_VERTICAL.get());
                        pOutput.accept(ModBlocks.COPYCAT_WING_FLAP_VERTICAL_FORK.get());
                        pOutput.accept(ModBlocks.COPYCAT_WING_FLAP_VERTICAL_LIT.get());
                        pOutput.accept(ModBlocks.COPYCAT_WING_FLAP_VERTICAL_LIT_FORK.get());
                        pOutput.accept(ModBlocks.COPYCAT_GLOWSTONE.get());

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
