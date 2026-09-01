package net.bullettrain.xenopixelsmod;

import net.neoforged.fml.common.EventBusSubscriber;

import com.mojang.logging.LogUtils;
import net.bullettrain.xenopixelsmod.block.ModBlocks;
import net.bullettrain.xenopixelsmod.capability.XenoCapabilities;
import net.bullettrain.xenopixelsmod.effect.ModEffects;
import net.bullettrain.xenopixelsmod.item.ModCreativeModTabs;
import net.bullettrain.xenopixelsmod.item.ModsItems;
import net.bullettrain.xenopixelsmod.network.ModNetwork;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import org.slf4j.Logger;

// The value here should match an entry in the META-INF/mods.toml file
@Mod(XenoPixelsMod.MOD_ID)
public class XenoPixelsMod {
    public static final String MOD_ID = "xenopixelsmod";
    public static final Logger LOGGER = LogUtils.getLogger();

    public XenoPixelsMod(IEventBus modEventBus) {

        ModsItems.register(modEventBus); // -- mods item register
        ModBlocks.register(modEventBus);
        net.bullettrain.xenopixelsmod.block.entity.ModBlockEntities.register(modEventBus);
        net.bullettrain.xenopixelsmod.missile.ModEntities.register(modEventBus);
        ModEffects.register(modEventBus);
        net.bullettrain.xenopixelsmod.sound.ModSounds.register(modEventBus);
        XenoCapabilities.register(modEventBus);
        ModNetwork.register();

        ModCreativeModTabs.register(modEventBus); // -- creative tab register

        modEventBus.addListener(this::commonSetup);

        NeoForge.EVENT_BUS.register(this);
        modEventBus.addListener(this::addCreative);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            net.bullettrain.xenopixelsmod.config.XenoServerConfig.load();
            net.bullettrain.xenopixelsmod.config.XenoPerfConfig.load();
            net.bullettrain.xenopixelsmod.config.XenoPartyConfig.load();
            net.bullettrain.xenopixelsmod.aero.gravity.OrbitalGravityConfig.load();
            net.bullettrain.xenopixelsmod.aero.AeroConfig.load();
            net.bullettrain.xenopixelsmod.combat.targeting.LockOnConfig.load();
            net.bullettrain.xenopixelsmod.features.FeatureManager.bootstrap();
            // CustomNPCs copies ScriptContainer.Data into every new script executor.
            // Install the bridge only when the optional mod is present so a dedicated
            // server without CustomNPCs keeps the same class-loading surface.
            try {
                if (net.neoforged.fml.ModList.get().isLoaded("customnpcs")) {
                    Class.forName("net.bullettrain.xenopixelsmod.compat.npc.NpcXenoScriptApi")
                            .getMethod("install").invoke(null);
                    LOGGER.info("CustomNPCs XenoPixels scripting API installed");
                }
            } catch (Throwable t) {
                LOGGER.warn("CustomNPCs XenoPixels scripting API unavailable: {}", t.toString());
            }
            if (net.bullettrain.xenopixelsmod.config.XenoServerConfig.dmzContentBootstrap) {
                net.bullettrain.xenopixelsmod.dmz.DmzContentBootstrap.installBundledContent();
            }
            // Sable thruster + moving-sub-level ballistic controls
            try {
                net.bullettrain.xenopixelsmod.vs.XenoThrusterControl.ensureRegistered();
                net.bullettrain.xenopixelsmod.vs.ShipBallisticController.ensureRegistered();
                net.bullettrain.xenopixelsmod.vs.ShipGravityControl.ensureRegistered();
                // Replaces AeroStar's OrbitGravitySystem, which crashes on Northstar Redux 0.6.
                net.bullettrain.xenopixelsmod.aero.gravity.OrbitalGravitySystem.ensureRegistered();
                net.bullettrain.xenopixelsmod.aero.control.AeroStabilizerSystem.ensureRegistered();
            } catch (Throwable t) {
                LOGGER.debug("Sable physics registration skipped: {}", t.toString());
            }
            // Optional CC:Tweaked peripherals (thruster + ballistic guidance)
            try {
                Class.forName("net.bullettrain.xenopixelsmod.compat.computercraft.CcCompat")
                        .getMethod("register")
                        .invoke(null);
            } catch (Throwable t) {
                LOGGER.debug("CC compat not registered: {}", t.toString());
            }
            LOGGER.info("Perf: {}", net.bullettrain.xenopixelsmod.config.XenoPerfConfig.statusLine());
        });
    }

    // Add the example block item to the building blocks tab
    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if(event.getTabKey() == CreativeModeTabs.INGREDIENTS) {
            event.accept(ModsItems.SAPPHIRE.get());
            event.accept(ModsItems.RAW_SAPPHIRE.get());
        }
    }

    // You can use SubscribeEvent and let the Event Bus discover methods to call
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {

    }

    // You can use EventBusSubscriber to automatically register all static methods in the class annotated with @SubscribeEvent
    @EventBusSubscriber(modid = MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            event.enqueueWork(() -> {
                net.bullettrain.xenopixelsmod.client.config.XenoClientConfig.load();
                net.bullettrain.xenopixelsmod.client.config.XenoHudConfig.load();
                net.bullettrain.xenopixelsmod.client.config.XenoHotbarConfig.load();
                net.bullettrain.xenopixelsmod.client.config.XenoCooldownHudConfig.load();
                net.bullettrain.xenopixelsmod.client.config.XenoPartyHudConfig.load();
                // Wire GUIs without loading client classes on dedicated server
                net.bullettrain.xenopixelsmod.client.ClientScreens.openTargetTool = () ->
                        net.minecraft.client.Minecraft.getInstance().setScreen(
                                new net.bullettrain.xenopixelsmod.client.gui.TargetScreen());
                // Ballistic Guidance GUI (includes missile speed 1–20)
                net.bullettrain.xenopixelsmod.client.ClientScreens.openGuidance = data ->
                        net.bullettrain.xenopixelsmod.client.ClientGuidance.open(
                                data.computerPos(), data.x(), data.y(), data.z(),
                                data.status(), data.pairedThrusters(), data.speedLevel(),
                                data.apexY(), data.cruiseY(), data.fleetChannel(),
                                data.salvoIntervalTicks(), data.gravitySi(), data.dragCoefficient(),
                                data.missileBase(), data.missileCenter(), data.missileNose(),
                                data.guidanceStopDistance());
                net.bullettrain.xenopixelsmod.client.ClientScreens.receiveFlightPlan = result -> {
                    var screen = net.minecraft.client.Minecraft.getInstance().screen;
                    if (screen instanceof net.bullettrain.xenopixelsmod.client.gui.FlightPlannerScreen planner) {
                        planner.acceptResult(result);
                    }
                };
                net.bullettrain.xenopixelsmod.client.ClientScreens.receiveAeroState = state -> {
                    // The seated pilot's HUD needs this snapshot with no screen open, so it is
                    // cached as well as handed to the planner screen.
                    net.bullettrain.xenopixelsmod.client.flight.ClientFlightState.accept(state);
                    var screen = net.minecraft.client.Minecraft.getInstance().screen;
                    if (screen instanceof net.bullettrain.xenopixelsmod.client.gui.FlightPlannerScreen planner) {
                        planner.acceptAeroState(state);
                    }
                };
                net.bullettrain.xenopixelsmod.client.ClientScreens.receiveCombatFx =
                        net.bullettrain.xenopixelsmod.client.combat.fx.CombatFxClient::accept;
                net.bullettrain.xenopixelsmod.client.ClientScreens.receiveParty =
                        net.bullettrain.xenopixelsmod.client.ClientParty::accept;
                net.bullettrain.xenopixelsmod.client.ClientScreens.receivePartyPing =
                        net.bullettrain.xenopixelsmod.client.ClientParty::acceptPing;
                net.bullettrain.xenopixelsmod.client.ClientScreens.openParty = () ->
                        net.minecraft.client.Minecraft.getInstance().setScreen(
                                new net.bullettrain.xenopixelsmod.client.screen.XenoPartyScreen(
                                        net.minecraft.client.Minecraft.getInstance().screen));
            });
        }
    }
}
