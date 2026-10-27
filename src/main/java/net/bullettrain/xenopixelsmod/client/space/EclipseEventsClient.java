package net.bullettrain.xenopixelsmod.client.space;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.space.event.EclipseEventSnapshot;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

import java.util.List;

/** Latest server list of active eclipse event markers, shown by the Galaxy Atlas. Replaced wholesale per packet. */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID, value = Dist.CLIENT)
public final class EclipseEventsClient {
    private static volatile List<EclipseEventSnapshot.Entry> active = List.of();

    private EclipseEventsClient() {}

    public static void apply(EclipseEventSnapshot packet) { active = packet.entries(); }

    public static List<EclipseEventSnapshot.Entry> active() { return active; }

    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { active = List.of(); }
}
