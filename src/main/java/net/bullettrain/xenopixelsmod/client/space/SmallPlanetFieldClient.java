package net.bullettrain.xenopixelsmod.client.space;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.space.smallplanet.SmallPlanetFieldSnapshot;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

/** Client cache for the server-authoritative small-planet field inputs. */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID, value = Dist.CLIENT)
public final class SmallPlanetFieldClient {
    private static volatile SmallPlanetFieldSnapshot current;

    private SmallPlanetFieldClient() {}

    public static SmallPlanetFieldSnapshot current() { return current; }
    public static void apply(SmallPlanetFieldSnapshot packet) { current = packet; }

    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { current = null; }
}
