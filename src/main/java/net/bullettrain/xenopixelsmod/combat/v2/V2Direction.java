package net.bullettrain.xenopixelsmod.combat.v2;

/**
 * The direction key held with an input. Relative to the fighter, not the world. Wire ordinal:
 * append only.
 */
public enum V2Direction {
    NONE,
    FORWARD,
    BACK,
    LEFT,
    RIGHT,
    UP,
    DOWN;

    private static final V2Direction[] VALUES = values();

    /** Decodes a wire ordinal; anything unknown reads as {@link #NONE}. */
    public static V2Direction byOrdinal(int ordinal) {
        return ordinal < 0 || ordinal >= VALUES.length ? NONE : VALUES[ordinal];
    }
}
