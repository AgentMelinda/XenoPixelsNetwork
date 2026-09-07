package net.bullettrain.xenopixelsmod.network;

/** A chase can undo its own grant, never an already-active mode or a later native disable. */
final class ChaseFlightOwnership {
    private ChaseFlightOwnership() {}

    static boolean mayRestoreGrant(boolean activeBefore, boolean activeNow, int currentMode) {
        return !activeBefore && activeNow && currentMode == 0;
    }

    static boolean mustStop(boolean touchedFlight, boolean activeNow, boolean alive,
                            boolean sameWorld, boolean seated, boolean enabled) {
        return !alive || !sameWorld || seated || !enabled || (touchedFlight && !activeNow);
    }
}
