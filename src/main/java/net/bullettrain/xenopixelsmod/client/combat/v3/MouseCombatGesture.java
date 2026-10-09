package net.bullettrain.xenopixelsmod.client.combat.v3;

/**
 * One mouse press is a tap or a charge, never both. Minecraft-free and counted in client ticks.
 *
 * <p>An interrupted press (lost lock, screen, death, mode switch) must be physically released
 * before the button can start anything again.
 */
public final class MouseCombatGesture {
    public enum Action { NONE, TAP, START, RELEASE, CANCEL }

    /** Held ticks at which a press stops being a tap. */
    public static final int HOLD_TICKS = 8;
    /** Display-only fill time; the server owns real charge completion. */
    public static final int FULL_CHARGE_TICKS = 20;

    private boolean wasDown, blocked, charging;
    private int held;

    public Action update(boolean down, boolean allowed) {
        if (down && !wasDown) {
            held = 0;
            blocked = !allowed;
        }
        wasDown = down;
        if (!allowed) {
            blocked = down;
            held = 0;
            if (charging) {
                charging = false;
                return Action.CANCEL;
            }
            return Action.NONE;
        }
        if (down) {
            if (blocked) return Action.NONE;
            held++;
            if (!charging && held >= HOLD_TICKS) {
                charging = true;
                return Action.START;
            }
            return Action.NONE;
        }
        if (charging) {
            charging = false;
            held = 0;
            return Action.RELEASE;
        }
        boolean tap = held > 0 && !blocked;
        held = 0;
        blocked = false;
        return tap ? Action.TAP : Action.NONE;
    }

    public boolean charging() { return charging; }

    public float progress() {
        return charging ? Math.clamp((held - HOLD_TICKS) / (float) FULL_CHARGE_TICKS, 0f, 1f) : 0f;
    }

    public void reset() {
        wasDown = blocked = charging = false;
        held = 0;
    }

    /** Both mouse buttons with a single gesture owner: the first one down, left on a tie. */
    public static final class Pair {
        public record Actions(Action left, Action right) {}
        private enum Owner { NONE, LEFT, RIGHT }

        private final MouseCombatGesture left = new MouseCombatGesture();
        private final MouseCombatGesture right = new MouseCombatGesture();
        private Owner owner = Owner.NONE;

        public Actions update(boolean leftDown, boolean rightDown, boolean allowed) {
            if (owner == Owner.NONE) owner = leftDown ? Owner.LEFT : rightDown ? Owner.RIGHT : Owner.NONE;
            Action l = left.update(leftDown, allowed && owner != Owner.RIGHT);
            Action r = right.update(rightDown, allowed && owner != Owner.LEFT);
            if ((owner == Owner.LEFT && !leftDown) || (owner == Owner.RIGHT && !rightDown)) owner = Owner.NONE;
            return new Actions(l, r);
        }

        public MouseCombatGesture left() { return left; }
        public MouseCombatGesture right() { return right; }

        public void reset() {
            left.reset();
            right.reset();
            owner = Owner.NONE;
        }
    }
}
