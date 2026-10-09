package net.bullettrain.xenopixelsmod.client.combat.v2;

/**
 * The two key gestures the v2 input layer reads beyond "is it down". Minecraft-free.
 *
 * <p>Counted in client ticks rather than wall-clock time, so a gesture means the same thing on a
 * slow frame as on a fast one and can be tested without a clock.
 */
public final class TapGesture {

    /** A guard key released within this many ticks of going down was a tap, not a hold. */
    public static final int TAP_TICKS = 4;
    /** Two presses of a key within this many ticks are a double-tap. */
    public static final int DOUBLE_TAP_TICKS = 6;

    private TapGesture() {}

    /**
     * Whether a key that was down for {@code heldTicks} and has just come up was only tapped.
     *
     * @param used the key did its held job while it was down, which makes it a hold however short
     */
    public static boolean isTap(int heldTicks, boolean used) {
        return !used && heldTicks > 0 && heldTicks <= TAP_TICKS;
    }

    /** Detects a double-tap of one key. Feed it the key's state once per tick. */
    public static final class DoubleTap {
        private boolean was;
        /** Ticks since the last lone press, or -1 when there is none to pair with. */
        private int sinceTap = -1;

        /** Whether this tick is a fresh press. Does not advance; call before {@link #update}. */
        public boolean pressed(boolean down) {
            return down && !was;
        }

        /** Advances one tick. @return true on the press that completes a double-tap */
        public boolean update(boolean down) {
            boolean press = down && !was;
            was = down;
            if (sinceTap >= 0 && ++sinceTap > DOUBLE_TAP_TICKS) sinceTap = -1;
            if (!press) return false;
            if (sinceTap >= 0) {
                sinceTap = -1;
                return true;
            }
            sinceTap = 0;
            return false;
        }

        /** Forgets a pending first tap, so the next press starts a new gesture. */
        public void clear() {
            sinceTap = -1;
        }
    }
}
