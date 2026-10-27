package net.bullettrain.xenopixelsmod.client.space;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.space.event.StellarClientTickClock;
import net.bullettrain.xenopixelsmod.space.event.StellarBlastWaveSnapshot;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

/** Client cache for server-owned supernova origins; the render thread extrapolates age between snapshots. */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID, value = Dist.CLIENT)
public final class StellarBlastWaveClient {
    private static StellarBlastWaveSnapshot current;
    private static StellarClientTickClock tickClock;
    private static boolean anchorPending;

    private StellarBlastWaveClient() {}

    public static void apply(StellarBlastWaveSnapshot packet) {
        var level = net.minecraft.client.Minecraft.getInstance().level;
        current = packet;
        anchorPending = level == null;
        tickClock = anchorPending ? null : new StellarClientTickClock(level.getGameTime());
    }

    public static StellarBlastWaveSnapshot current() { return current; }

    public static double ageTicks(StellarBlastWaveSnapshot.Wave wave, long clientGameTick, double partialTick) {
        if (anchorPending) {
            tickClock = new StellarClientTickClock(clientGameTick);
            anchorPending = false;
        }
        if (tickClock == null) tickClock = new StellarClientTickClock(clientGameTick);
        double age = wave.ageTicks() + tickClock.elapsedTicks(clientGameTick, partialTick);
        return Double.isFinite(age) ? Math.max(0.0, age) : wave.ageTicks();
    }

    public static void clear() {
        current = null;
        tickClock = null;
        anchorPending = false;
    }

    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { clear(); }
}
