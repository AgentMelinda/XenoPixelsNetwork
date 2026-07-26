package net.bullettrain.xenopixelsmod;

import com.mojang.logging.LogUtils;
import net.bullettrain.xenopixelsmod.block.ModBlocks;
import net.bullettrain.xenopixelsmod.effect.ModEffects;
import net.bullettrain.xenopixelsmod.item.ModCreativeModTabs;
import net.bullettrain.xenopixelsmod.item.ModsItems;
import net.bullettrain.xenopixelsmod.network.ModNetwork;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

// The value here should match an entry in the META-INF/mods.toml file
@Mod(XenoPixelsMod.MOD_ID)
public class XenoPixelsMod {
    public static final String MOD_ID = "xenopixelsmod";
    public static final Logger LOGGER = LogUtils.getLogger();

    public XenoPixelsMod() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        ModsItems.register(modEventBus); // -- mods item register
        ModBlocks.register(modEventBus);
        net.bullettrain.xenopixelsmod.block.entity.ModBlockEntities.register(modEventBus);
        ModEffects.register(modEventBus);
        ModNetwork.register();

        ModCreativeModTabs.register(modEventBus); // -- creative tab register

        modEventBus.addListener(this::commonSetup);

        MinecraftForge.EVENT_BUS.register(this);
        modEventBus.addListener(this::addCreative);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            net.bullettrain.xenopixelsmod.config.XenoServerConfig.load();
            net.bullettrain.xenopixelsmod.config.XenoPerfConfig.load();
            net.bullettrain.xenopixelsmod.features.FeatureManager.bootstrap();
            if (net.bullettrain.xenopixelsmod.config.XenoServerConfig.dmzContentBootstrap) {
                net.bullettrain.xenopixelsmod.dmz.DmzContentBootstrap.installBundledContent();
            }
            LOGGER.info("Perf: {}", net.bullettrain.xenopixelsmod.config.XenoPerfConfig.statusLine());
        });
    }

    // Add the example block item to the building blocks tab
    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if(event.getTabKey() == CreativeModeTabs.INGREDIENTS) {
            event.accept(ModsItems.SAPPHIRE);
            event.accept(ModsItems.RAW_SAPPHIRE);
        }
    }

    // You can use SubscribeEvent and let the Event Bus discover methods to call
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {

    }

    // You can use EventBusSubscriber to automatically register all static methods in the class annotated with @SubscribeEvent
    @Mod.EventBusSubscriber(modid = MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            event.enqueueWork(() -> {
                net.bullettrain.xenopixelsmod.client.config.XenoClientConfig.load();
                net.bullettrain.xenopixelsmod.client.config.XenoHudConfig.load();
                net.bullettrain.xenopixelsmod.client.config.XenoHotbarConfig.load();
                net.bullettrain.xenopixelsmod.client.config.XenoCooldownHudConfig.load();
            });
        }
    }
}
