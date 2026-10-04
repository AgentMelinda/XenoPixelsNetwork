package net.bullettrain.xenopixelsmod.client.aero;

/**
 * Live-tunable extra twist for the guidance-computer GeckoLib rig, set via {@code /xenocomp}.
 *
 * <p>Client-set, in-memory, not persisted — same scratch pad idea as {@code /xenowing}.
 * Applied only while guidance v2 is active so v1 keeps the stock GeckoLib mount.
 */
public final class FlightControllerDebugRotation {

    /** Extra degrees after the facing rotation, applied X then Y then Z to the vertex. */
    public static volatile int extraX;
    public static volatile int extraY;
    public static volatile int extraZ;

    private FlightControllerDebugRotation() {
    }

    public static void reset() {
        extraX = 0;
        extraY = 0;
        extraZ = 0;
    }
}
