package net.bullettrain.xenopixelsmod.combat.v2.combo;

/**
 * The inputs a combo node can branch on. {@link #bit()} is the mask the server sends so the
 * prompt can show which branches are open. Append only: the mask is on the wire.
 */
public enum ComboInput {
    LIGHT,
    HEAVY,
    GRAB,
    /** The automatic cinematic rush, offered after a full light string. */
    RUSH;

    public int bit() {
        return 1 << ordinal();
    }

    public static boolean has(int mask, ComboInput input) {
        return input != null && (mask & input.bit()) != 0;
    }
}
