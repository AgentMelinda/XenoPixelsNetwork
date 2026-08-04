package net.bullettrain.xenopixelsmod.missile;

/**
 * Flight phases for native XenoPixels ballistic missiles (not Ballistix).
 *
 * <pre>
 * EJECT  → short rail push along silo axis (ignore aero)
 * BOOST  → powered burn; builds loft velocity
 * COAST  → pure ballistic midcourse under gravity + light drag
 * TERMINAL → limited-g seeker toward aim point / lock
 * </pre>
 */
public enum MissilePhase {
    EJECT,
    BOOST,
    COAST,
    TERMINAL,
    DEAD
}
