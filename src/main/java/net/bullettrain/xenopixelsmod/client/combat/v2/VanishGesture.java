package net.bullettrain.xenopixelsmod.client.combat.v2;

import net.bullettrain.xenopixelsmod.combat.v2.V2Direction;

/** Side-key double taps, using the same 280 ms window as the legacy and BT3 controllers. */
public final class VanishGesture {
    public static final long DOUBLE_TAP_MS = 280L;

    private boolean leftWas, rightWas;
    private long leftTap = -1L, rightTap = -1L;

    /** Samples both keys even without a lock, so walking never arms a combat gesture. */
    public V2Direction update(boolean locked, boolean left, boolean right, long now) {
        boolean leftPressed = left && !leftWas;
        boolean rightPressed = right && !rightWas;
        leftWas = left;
        rightWas = right;
        if (!locked) {
            leftTap = rightTap = -1L;
            return V2Direction.NONE;
        }
        boolean leftTwice = leftPressed && within(leftTap, now);
        boolean rightTwice = rightPressed && within(rightTap, now);
        if (leftPressed) leftTap = leftTwice ? -1L : now;
        if (rightPressed) rightTap = rightTwice ? -1L : now;
        return leftTwice ? V2Direction.LEFT : rightTwice ? V2Direction.RIGHT : V2Direction.NONE;
    }

    private static boolean within(long tap, long now) {
        return tap >= 0L && now >= tap && now - tap <= DOUBLE_TAP_MS;
    }

    public void reset() {
        leftWas = rightWas = false;
        leftTap = rightTap = -1L;
    }
}
