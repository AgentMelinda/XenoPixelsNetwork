package net.bullettrain.xenopixelsmod.combat.v3;

/** V3 directional intent. Append only. */
public enum V3Direction {
    NONE, FORWARD, BACK, LEFT, RIGHT,
    /** Grab throw / vertical launch: jump chord. */
    UP;
    public static V3Direction decode(int ordinal) {
        if (ordinal < 0 || ordinal >= values().length) throw new IllegalArgumentException("Unknown V3 direction");
        return values()[ordinal];
    }
}
