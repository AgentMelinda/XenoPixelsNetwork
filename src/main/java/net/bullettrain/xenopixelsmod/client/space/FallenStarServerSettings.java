package net.bullettrain.xenopixelsmod.client.space;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.bus.api.SubscribeEvent;

/** Client cache of the server's master switch, separate from the local density preference. */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID, value = net.neoforged.api.distmarker.Dist.CLIENT)
public final class FallenStarServerSettings {
    private static volatile boolean enabled = true;

    private FallenStarServerSettings() {}

    public static boolean enabled() { return enabled; }
    public static void apply(boolean value) { enabled = value; }

    public static void reset() { enabled = true; }

    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        reset();
    }
}
