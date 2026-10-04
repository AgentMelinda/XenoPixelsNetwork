package net.bullettrain.xenopixelsmod.npc.movement;

import net.bullettrain.xenopixelsmod.npc.movement.NpcMovementOwner.Claim;

/**
 * One NPC's movement claim and its progress record.
 *
 * <p>Split out of {@link NpcMovementOwner} so the rules can be tested. Everything here takes a game
 * time and a position as plain numbers and knows nothing about entities or levels, which is what
 * makes "a leash cannot interrupt a live combat claim" a unit test rather than something that has
 * to be watched in a running world.
 *
 * <p>Not thread safe on its own; instances are held per NPC in a concurrent map and touched from
 * the server thread.
 */
public final class NpcMovementLedger {

    /**
     * How long a claim survives without being renewed.
     *
     * <p>A claim is released explicitly when its owner finishes, but an owner can also simply stop
     * running — a scene deleted mid-playback, a brain that disengaged, a chunk unloaded between
     * ticks. The timeout is what stops one of those leaving an NPC permanently unsteerable by
     * anything else. Five seconds: longer than any gap between checks (the leash's is 20 ticks, the
     * walker's 10), short enough that a wedged NPC frees up while somebody is still watching it.
     */
    public static final int CLAIM_TIMEOUT_TICKS = 100;

    /**
     * How far an NPC must move between re-issues to count as making progress.
     *
     * <p>Squared. Half a block is under one walk step but well above the drift of a mob standing on
     * a slab edge, so it separates "inching around an obstacle" from "wedged and juddering".
     */
    public static final double PROGRESS_EPSILON_SQR = 0.25;

    /** How many re-issues with no progress before the mover is told to stop asking. */
    public static final int STUCK_STRIKES = 3;

    private Claim held;
    private long expiresAt;

    private boolean tracking;
    private double lastX;
    private double lastY;
    private double lastZ;
    private int strikes;

    /** @see NpcMovementOwner#claim */
    public boolean claim(Claim claim, long gameTime) {
        if (claim == null) {
            return false;
        }
        if (held != null && gameTime < expiresAt && held.ordinal() < claim.ordinal()) {
            return false;
        }
        if (held != claim) {
            // A different system has taken over, so the previous one's progress record means
            // nothing — it was measuring movement toward somewhere else entirely.
            clearProgress();
        }
        held = claim;
        expiresAt = gameTime + CLAIM_TIMEOUT_TICKS;
        return true;
    }

    /** @see NpcMovementOwner#release */
    public void release(Claim claim) {
        if (held == claim) {
            held = null;
            expiresAt = 0L;
            clearProgress();
        }
    }

    /** The live claim, or null when there is none or it has expired. */
    public Claim current(long gameTime) {
        if (held == null || gameTime >= expiresAt) {
            return null;
        }
        return held;
    }

    /** @see NpcMovementOwner#progressing */
    public boolean progressing(double x, double y, double z) {
        if (!tracking) {
            remember(x, y, z);
            return true;
        }
        double dx = x - lastX;
        double dy = y - lastY;
        double dz = z - lastZ;
        if (dx * dx + dy * dy + dz * dz > PROGRESS_EPSILON_SQR) {
            remember(x, y, z);
            return true;
        }
        // The remembered position deliberately stays where it was. Measuring each check against the
        // last one instead would let an NPC shuffling a few centimetres back and forth against a
        // wall look like progress forever, which is the exact motion this is meant to catch.
        strikes++;
        return strikes < STUCK_STRIKES;
    }

    /** Whether this NPC has given up on where it was going. */
    public boolean stuck() {
        return tracking && strikes >= STUCK_STRIKES;
    }

    /** @see NpcMovementOwner#clearProgress */
    public void clearProgress() {
        tracking = false;
        strikes = 0;
    }

    private void remember(double x, double y, double z) {
        tracking = true;
        lastX = x;
        lastY = y;
        lastZ = z;
        strikes = 0;
    }
}
