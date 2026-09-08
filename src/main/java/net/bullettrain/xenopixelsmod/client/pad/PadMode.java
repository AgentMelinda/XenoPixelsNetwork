package net.bullettrain.xenopixelsmod.client.pad;

/** Which controller layer owns gameplay input. */
public enum PadMode {
    NORMAL,
    BT3;

    public static PadMode parse(String value) {
        if (value == null) return BT3;
        try {
            return valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return BT3;
        }
    }
}
