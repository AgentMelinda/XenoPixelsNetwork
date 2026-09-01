package net.bullettrain.xenopixelsmod.aero.gravity;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;

/**
 * Records whether our AeroStar mixin has actually neutralized AeroStar's gravity handler.
 *
 * <p>A plain holder rather than a static on the mixin class itself, so ordinary code never has
 * to reference a mixin type — those are transformed into the target and are not a stable thing
 * to link against.
 *
 * <p>This exists because "is the mixin applied?" turned out to be unanswerable from the outside.
 * Across three crash reports the mixin was listed as applied to
 * {@code com.bega.aerostarcomp.physics.OrbitGravitySystem} while its injector was doing
 * nothing at all — class-level application and injector matching are separate things. The only
 * trustworthy signal is a cancel having genuinely executed, which is what this records.
 */
public final class AeroStarState {
    private static volatile boolean cancelledHandler;

    private AeroStarState() {
    }

    /**
     * Called from the mixin at the moment it cancels AeroStar's handler.
     *
     * <p>This runs on every Sable physics tick once the cancel is active, so the steady state
     * is deliberately a volatile <i>read</i> that returns immediately — the store and the log
     * happen only on the first call.
     */
    public static void markHandlerCancelled() {
        if (cancelledHandler) return;
        cancelledHandler = true;
        // Once per server lifetime. This is the confirmation that was missing across three
        // crash reports: "the mixin is listed as applied" never meant its injector had matched,
        // and only a cancel that actually executed proves who owns ship gravity.
        XenoPixelsMod.LOGGER.info(
                "AeroStar's orbital gravity handler was suppressed; XenoPixels now owns ship gravity");
    }

    /** True once we have positively suppressed AeroStar's gravity at least once. */
    public static boolean hasCancelledHandler() {
        return cancelledHandler;
    }
}
