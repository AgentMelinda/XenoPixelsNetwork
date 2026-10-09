package net.bullettrain.xenopixelsmod.client.combat.v2;

import net.bullettrain.xenopixelsmod.combat.v2.V2State;
import net.bullettrain.xenopixelsmod.combat.v2.combo.BranchFlavor;
import net.bullettrain.xenopixelsmod.combat.v2.combo.ComboInput;
import net.bullettrain.xenopixelsmod.combat.v2.grab.GrabRules;
import net.bullettrain.xenopixelsmod.network.packet.CombatV2StatePacket;

/**
 * What the server last said about this client's own v2 fight.
 *
 * <p>Written only by {@link CombatV2StatePacket}. The client never decides a window is open; it
 * only counts down the ticks the server gave it, one per client tick, so the prompt keeps moving
 * between packets and simply closes if the server goes quiet.
 */
public final class V2ClientState {

    private static V2State state = V2State.NEUTRAL;
    private static int branchMask;
    private static int windowTicksLeft;
    private static int windowTicksTotal;
    private static int counterTicksLeft;
    private static int counterTicksTotal;
    private static int counterAttackerId = -1;
    private static int homingTicksLeft;
    private static int homingTicksTotal;
    private static int homingTargetId = -1;
    private static boolean grabReady;
    private static float grabRange = 2.6f;
    private static int grabTicksLeft;
    private static int grabTicksTotal;
    /** Client ticks spent in the current grab state since the server last changed it. */
    private static int stateTicks;
    private static int dashTicksLeft, dashTicksTotal;
    private static int dashTargetId = -1;

    private V2ClientState() {}

    public static void apply(CombatV2StatePacket packet) {
        V2State next = V2State.byOrdinal(packet.state());
        branchMask = packet.branchMask();
        windowTicksLeft = Math.max(0, packet.windowTicksLeft());
        windowTicksTotal = Math.max(windowTicksLeft, packet.windowTicksTotal());
        // A countdown's full length is whatever it stood at when it opened. A later packet about
        // the same window only ever carries less, so the larger of the two is the opening value.
        int counter = Math.max(0, packet.counterTicksLeft());
        counterTicksTotal = counterTicksLeft > 0 && counter > 0 ? Math.max(counterTicksTotal, counter) : counter;
        counterTicksLeft = counter;
        counterAttackerId = counter > 0 ? packet.counterAttackerId() : -1;
        int homing = Math.max(0, packet.homingTicksLeft());
        homingTicksTotal = homingTicksLeft > 0 && homing > 0 ? Math.max(homingTicksTotal, homing) : homing;
        homingTicksLeft = homing;
        homingTargetId = packet.homingTargetId();
        grabReady = packet.grabReady();
        grabRange = Math.max(0.5f, packet.grabRange());
        int grab = Math.max(0, packet.grabTicksLeft());
        grabTicksTotal = next == state && grabTicksLeft > 0 && grab > 0 ? Math.max(grabTicksTotal, grab) : grab;
        grabTicksLeft = grab;
        if (next != state) stateTicks = 0;
        state = next;
        int dash = Math.max(0, packet.dashTicksLeft());
        dashTicksTotal = dashTicksLeft > 0 && dash > 0 && dashTargetId == packet.dashTargetId()
                ? Math.max(dashTicksTotal, dash) : dash;
        dashTicksLeft = dash;
        dashTargetId = dash > 0 ? packet.dashTargetId() : -1;
    }

    /** One client tick has passed: every countdown loses one. */
    public static void tick() {
        if (dashTicksLeft > 0 && --dashTicksLeft == 0) dashTargetId = -1;
        if (windowTicksLeft > 0 && --windowTicksLeft == 0) branchMask = 0;
        if (counterTicksLeft > 0 && --counterTicksLeft == 0) counterAttackerId = -1;
        if (homingTicksLeft > 0 && --homingTicksLeft == 0) homingTargetId = -1;
        if (grabTicksLeft > 0) grabTicksLeft--;
        // The windows above close by themselves if the server goes quiet; a state would not.
        // While a grab has this fighter the client reads no other combat key, so a grab the
        // server never reported the end of must not be able to hold the keys for good.
        if (!inGrab()) {
            stateTicks = 0;
        } else if (GrabRules.outstays(true, ++stateTicks)) {
            state = V2State.NEUTRAL;
            stateTicks = 0;
            grabTicksLeft = 0;
        }
    }

    /** In a grab at either end: winding one up, holding someone, or held by someone. */
    public static boolean inGrab() {
        return state == V2State.GRAB_STARTUP || state == V2State.GRAB_HOLD || state == V2State.GRABBED;
    }

    public static void reset() {
        dashTicksLeft = dashTicksTotal = 0;
        dashTargetId = -1;
        state = V2State.NEUTRAL;
        branchMask = 0;
        windowTicksLeft = 0;
        windowTicksTotal = 0;
        counterTicksLeft = 0;
        counterTicksTotal = 0;
        counterAttackerId = -1;
        homingTicksLeft = 0;
        homingTicksTotal = 0;
        homingTargetId = -1;
        grabReady = false;
        grabTicksLeft = 0;
        grabTicksTotal = 0;
        stateTicks = 0;
    }

    public static V2State state() {
        return state;
    }

    public static boolean dashOpenAgainst(int targetId) {
        return dashTicksLeft > 0 && targetId >= 0 && dashTargetId == targetId;
    }

    public static boolean dashOpen() { return dashTicksLeft > 0; }
    public static float dashFraction() { return fraction(dashTicksLeft, dashTicksTotal); }

    public static boolean branchOpen(ComboInput input) {
        return windowTicksLeft > 0 && ComboInput.has(branchMask, input);
    }

    /** What the open light or heavy branch leads to: a punch, a kick, a launcher, a smash. */
    public static BranchFlavor branchFlavor(ComboInput input) {
        return BranchFlavor.unpack(branchMask, input);
    }

    /** 0..1, how much of the time to pick a branch is left. */
    public static float windowFraction() {
        return fraction(windowTicksLeft, windowTicksTotal);
    }

    public static boolean counterOpen() {
        return counterTicksLeft > 0;
    }

    /**
     * Whether a counter can be thrown at {@code lockedEntityId}: the window is open and it was
     * that entity, the fighter's own lock, that landed the hit. A counter answers nobody else.
     */
    public static boolean counterOpenAgainst(int lockedEntityId) {
        return counterTicksLeft > 0 && lockedEntityId >= 0 && counterAttackerId == lockedEntityId;
    }

    public static float counterFraction() {
        return fraction(counterTicksLeft, counterTicksTotal);
    }

    public static boolean homingOpen() {
        return homingTicksLeft > 0;
    }

    public static float homingFraction() {
        return fraction(homingTicksLeft, homingTicksTotal);
    }

    public static int homingTargetId() {
        return homingTicksLeft > 0 ? homingTargetId : -1;
    }

    public static boolean grabReady() {
        return grabReady;
    }

    public static float grabRange() {
        return grabRange;
    }

    /** 0..1 of the hold (grabber) or tech window (victim) still to run. */
    public static float grabFraction() {
        return fraction(grabTicksLeft, grabTicksTotal);
    }

    public static int grabTicksLeft() {
        return grabTicksLeft;
    }

    private static float fraction(int left, int total) {
        return total <= 0 ? 0f : Math.max(0f, Math.min(1f, left / (float) total));
    }
}
