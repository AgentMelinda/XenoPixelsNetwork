package net.bullettrain.xenopixelsmod.combat;

/** Layout and expiry of real Zanzoken decoys along the movement path. */
public final class ZanzokenTrail {
    private ZanzokenTrail() {}

    public static int count(int configured) {
        return Math.max(1, Math.min(16, configured));
    }

    public static double fraction(int index, int configured) {
        int count = count(configured);
        return Math.max(0, Math.min(count - 1, index)) / (double) count;
    }

    /** The last image retains the server lifetime; earlier images disappear first. */
    public static int lifetime(int configured, int index, int count) {
        int maximum = Math.max(1, configured);
        int copies = count(count);
        double order = copies == 1 ? 1 : Math.max(0, Math.min(copies - 1, index)) / (double) (copies - 1);
        return Math.max(1, (int) Math.round(maximum * (0.8 + 0.2 * order)));
    }
}
