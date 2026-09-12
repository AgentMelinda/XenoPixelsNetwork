package net.bullettrain.xenopixelsmod.plot;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Charges due plot leases once a second.
 *
 * <p>No packet and no client involvement: a lease is server state, and the sign and
 * {@code /plot lease} both read it from the server like every other plot fact. The interval is
 * deliberately coarser than a tick — rent is not a per-tick decision, and a due tick that lands
 * between checks is simply paid on the next one.</p>
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class PlotLeaseTicker {

    /** How often due leases are settled, in ticks. */
    private static final int CHECK_INTERVAL = 20;

    private PlotLeaseTicker() {
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % CHECK_INTERVAL != 0) {
            return;
        }
        PlotLease.settleDue(server, server.getTickCount());
    }
}