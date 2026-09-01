package net.bullettrain.xenopixelsmod.client.flight;

import net.bullettrain.xenopixelsmod.aero.AeroStateSnapshot;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;

/**
 * The last authoritative controller snapshot this client was sent, kept for the flight HUD.
 *
 * <p>The HUD renders this and nothing else. Everything the pilot sees — throttle, flap travel,
 * speed, power tier, status — is a value the server published, so the readout cannot drift away
 * from what the ship is actually doing, and a rejected command visibly does not take effect.
 *
 * <p>Snapshots arrive a few times a second while seated (the seat pushes them) and whenever the
 * planner screen polls. Anything older than {@link #STALE_MILLIS} is treated as no data rather
 * than shown as if it were live.
 */
public final class ClientFlightState {

    private static final long STALE_MILLIS = 2_000L;

    private static @Nullable AeroStateSnapshot state;
    private static long receivedAtMillis;

    private ClientFlightState() {
    }

    public static void accept(@Nullable AeroStateSnapshot snapshot) {
        state = snapshot;
        receivedAtMillis = System.currentTimeMillis();
    }

    /** The live snapshot, or null when there is none or it has gone stale. */
    public static @Nullable AeroStateSnapshot get() {
        if (state == null) return null;
        if (System.currentTimeMillis() - receivedAtMillis > STALE_MILLIS) return null;
        return state;
    }

    public static void clear() {
        state = null;
        receivedAtMillis = 0L;
    }

    /** True when the local player is seated and has fresh controller data to render. */
    public static boolean hasLiveFlight() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && XenoFlightControls.seated() && get() != null;
    }
}
