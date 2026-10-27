package net.bullettrain.xenopixelsmod.compat.npc;

import java.util.UUID;

/** Transient pursuit intent, independent of navigation and entity velocity. */
final class NpcPursuitCurve {
    /** How long one lateral decision is held before the coin gets another chance. */
    private static final int HOLD_TICKS = 12;

    private UUID target;
    private int lastTick = Integer.MIN_VALUE;
    private int sideUntil;
    private int blendTick;
    private double side, from, wanted;
    private boolean chasing = true;

    double update(UUID target, int tick, double distance, double reach, boolean inReach, int choice) {
        if (!target.equals(this.target) || tick < lastTick || (long) tick - lastTick > 2) {
            this.target = target;
            side = from = 0;
            wanted = choice < 0 ? -1 : 1;
            blendTick = tick;
            sideUntil = tick + HOLD_TICKS;
            chasing = true;
        }
        if (tick == lastTick) return side;
        lastTick = tick;
        if (inReach) chasing = false;
        else if (distance > reach + 0.45) chasing = true;
        if (tick >= sideUntil) {
            from = side;
            wanted = choice < 0 ? -1 : 1;
            blendTick = tick;
            sideUntil = tick + HOLD_TICKS;
        }
        double blend = Math.min(1, (tick - blendTick + 1) / 4.0);
        side = from + (wanted - from) * blend;
        return side;
    }

    /**
     * Commits to the opposite lateral side and returns the new one.
     *
     * <p>Called when the approach is going nowhere. The arc is not what failed - the side is - so
     * this turns the NPC the other way and holds that decision for {@link #HOLD_TICKS}, which is what
     * lets Minecraft's own path finding get round the obstacle. Zeroing the bend instead, the way
     * this path used to react to {@code horizontalCollision}, aimed the NPC back at the wall it had
     * just been pushed off and called that obstacle avoidance.
     *
     * <p>Unlike a re-roll this does not blend: an NPC already pressed into geometry needs the next
     * tick's waypoint to be somewhere else, not a quarter of the way there. The returned side is the
     * one {@link #update} keeps reporting until the hold expires, so caller and curve cannot disagree.
     */
    double switchSide(int tick) {
        wanted = side >= 0 ? -1 : 1;
        from = side = wanted;
        blendTick = tick;
        sideUntil = tick + HOLD_TICKS;
        return side;
    }

    boolean chasing() { return chasing; }
}
