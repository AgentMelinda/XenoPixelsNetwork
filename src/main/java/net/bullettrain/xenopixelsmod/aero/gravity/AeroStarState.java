package net.bullettrain.xenopixelsmod.aero.gravity;

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

    /** Called from the mixin at the moment it cancels AeroStar's handler. */
    public static void markHandlerCancelled() {
        cancelledHandler = true;
    }

    /** True once we have positively suppressed AeroStar's gravity at least once. */
    public static boolean hasCancelledHandler() {
        return cancelledHandler;
    }
}
