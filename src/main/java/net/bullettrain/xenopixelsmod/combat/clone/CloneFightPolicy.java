package net.bullettrain.xenopixelsmod.combat.clone;

/**
 * Who a multi-form copy fights this tick. What the fighter is looking at wins; then the lock-on;
 * then the body that just hit the fighter or a copy; then a nearby hostile when that mode is on.
 *
 * <p>The look slot sits above the lock-on deliberately. A DragonMineZ lock needs the {@code kisense}
 * skill and is dropped again within a few ticks without it, so for most characters the lock-on is
 * simply never there — and with nothing in the other two slots either, the copies would stand in
 * formation no matter what their owner aimed at. Pointing at something is the most direct statement
 * of intent a player can make, so it outranks a lock they may not even be able to hold.
 */
public final class CloneFightPolicy {
    public enum Slot { LOOK, LOCK, RETALIATE, HOSTILE, NONE }

    private CloneFightPolicy() {}

    /**
     * @param lookOn        whether the look slot is enabled at all
     * @param lookAlive     whether a permitted, in-range entity is under the fighter's crosshair
     * @param lockAlive     whether the DragonMineZ lock-on resolves to a permitted, in-range body
     * @param retaliateAlive whether something that recently hit the fighter or a copy is still valid
     * @param hostileAlive  whether a nearby permitted body was found
     */
    public static Slot pick(boolean lookOn, boolean lookAlive,
                            boolean lockAlive,
                            boolean retaliateOn, boolean retaliateAlive,
                            boolean hostileOn, boolean hostileAlive) {
        if (lookOn && lookAlive) {
            return Slot.LOOK;
        }
        if (lockAlive) {
            return Slot.LOCK;
        }
        if (retaliateOn && retaliateAlive) {
            return Slot.RETALIATE;
        }
        if (hostileOn && hostileAlive) {
            return Slot.HOSTILE;
        }
        return Slot.NONE;
    }
}
