package net.bullettrain.xenopixelsmod.api.network;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Internal lifecycle bridge that freezes addon registrations before the DMZ shim flushes them. */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
final class AddonNetworkBootstrap {
    private AddonNetworkBootstrap() {}

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void register(RegisterPayloadHandlersEvent event) {
        AddonNetwork.registerPayloads();
    }
}
