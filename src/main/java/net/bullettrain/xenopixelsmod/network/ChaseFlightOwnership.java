package net.bullettrain.xenopixelsmod.network;

/**
 * A chase can undo its own grant, never an already-active mode or a later native disable.
 *
 * <p>Public so the XenoCombat v2 mover applies the same rule to the flight it borrows.
 */
public final class ChaseFlightOwnership {
    private ChaseFlightOwnership() {}

    public static boolean mayRestoreGrant(boolean activeBefore, boolean activeNow, int currentMode) {
        return !activeBefore && activeNow && currentMode == 0;
    }

    /** V2 may also borrow an existing Combat Fly mode, restoring it only while Search Fly remains. */
    public static boolean mayRestoreSearchMode(boolean activeNow, int currentMode) {
        return activeNow && currentMode == 0;
    }

    public static boolean mustStop(boolean touchedFlight, boolean activeNow, boolean alive,
                            boolean sameWorld, boolean seated, boolean enabled) {
        return !alive || !sameWorld || seated || !enabled || (touchedFlight && !activeNow);
    }
}
