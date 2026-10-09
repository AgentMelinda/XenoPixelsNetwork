package net.bullettrain.xenopixelsmod.client.combat.v2;

import net.bullettrain.xenopixelsmod.combat.v2.V2ChargeRules;

/** One mouse press is a tap or a charge, never both. A cancelled hold must be released first. */
public final class ChargeGesture {
    public enum Action { NONE, TAP, START, RELEASE, CANCEL }
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
            if (!charging && held >= V2ChargeRules.MIN_TICKS) {
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
    public float progress() { return charging ? V2ChargeRules.progress(held - V2ChargeRules.MIN_TICKS) : 0; }
    public void reset() { wasDown = blocked = charging = false; held = 0; }
}
