package net.bullettrain.xenopixelsmod.client.space;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.space.SpaceOrbitClockSnapshot;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

/** Client cache of the server's orbital epoch; no local config value can override server orbit mode. */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID, value = net.neoforged.api.distmarker.Dist.CLIENT)
public final class SpaceOrbitClockClient {
    private static volatile SpaceOrbitClockSnapshot snapshot;

    private SpaceOrbitClockClient() {}

    public static void apply(SpaceOrbitClockSnapshot value) { snapshot = value; }
    public static boolean enabled() { return snapshot != null && snapshot.realOrbits(); }

    /** Current canonical orbit seconds from vanilla-synchronized ClientLevel game time and server epoch offset. */
    public static double seconds(float partialTick) {
        var current = snapshot;
        var level = Minecraft.getInstance().level;
        if (current == null || level == null) return Double.NaN;
        return current.clientSecondsAt(level.getGameTime(), partialTick);
    }

    public static void reset() { snapshot = null; }

    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { reset(); }
}
