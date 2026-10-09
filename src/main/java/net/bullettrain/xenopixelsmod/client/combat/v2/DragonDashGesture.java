package net.bullettrain.xenopixelsmod.client.combat.v2;

/** A new dash charges on release; its continuation fires once on the next press. */
public final class DragonDashGesture {
    public static final int NONE = -1;
    private boolean wasDown, blocked, consumed;
    private int held;

    public int update(boolean down, boolean allowed, boolean continuation) {
        boolean pressed = down && !wasDown;
        wasDown = down;
        if (pressed) {
            held = 0;
            consumed = false;
            blocked = !allowed;
        }
        if (!allowed) {
            blocked = down;
            held = 0;
            return NONE;
        }
        if (down) {
            if (blocked || consumed) return NONE;
            if (pressed && continuation) {
                consumed = true;
                return 0;
            }
            held++;
            return NONE;
        }
        int charge = held > 0 && !blocked && !consumed ? Math.min(100, held * 5) : NONE;
        held = 0;
        blocked = consumed = false;
        return charge;
    }

    public void reset() { wasDown = blocked = consumed = false; held = 0; }
}
