package net.bullettrain.xenopixelsmod.combat.v2;

/**
 * Everything a v2 client may ask for. The client states intent only; range, cost, timing and the
 * combo node are all re-derived on the server.
 *
 * <p>Sent as an ordinal by {@code CombatV2InputPacket}, so <b>append new values at the end</b>
 * and bump the network protocol rather than inserting in the middle.
 */
public enum V2Input {
    LIGHT_PRESS,
    LIGHT_HOLD,
    HEAVY_PRESS,
    HEAVY_HOLD,
    /** Guard + light: grab the target in front, or tech a grab that has just caught you. */
    GRAB,
    /** Retired step slot. Kept only to preserve subsequent wire ordinals; never accepted. */
    STEP,
    /** Fly at a launched or distant target. */
    CHASE,
    /** Released the chase input. */
    CHASE_STOP,
    DRAGON_DASH,
    /** Automatic multi-hit rush after three landed lights. */
    CINEMATIC_RUSH,
    /** Kept for wire order. {@link #VANISH} decides for itself whether it is a counter. */
    COUNTER,
    /**
     * Vanish. A super counter if a counter window is open, otherwise a vanish behind the target,
     * otherwise a short blink in the held direction. Appended.
     */
    VANISH,
    LIGHT_CHARGE_START,
    HEAVY_CHARGE_START,
    CHARGE_CANCEL;

    private static final V2Input[] VALUES = values();

    /** Decodes a wire ordinal, or null when a peer sent one this build does not know. */
    public static V2Input byOrdinal(int ordinal) {
        return ordinal < 0 || ordinal >= VALUES.length || VALUES[ordinal] == STEP ? null : VALUES[ordinal];
    }
}
