package net.bullettrain.xenopixelsmod.npc.path;

import net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile;
import net.bullettrain.xenopixelsmod.npc.XenoNpcEntity;

/**
 * Walks an NPC along its {@link NpcPath}.
 *
 * <p>Uses {@code npc.getNavigation().moveTo(x, y, z, speed)} — the same call
 * {@code XenoNpcBehaviour.tickLeash} already makes to bring a strayed NPC home. One movement path,
 * not two.
 *
 * <p><b>A patrolling NPC stops patrolling when it has a target.</b> Combat is the more urgent
 * thing, and a guard that kept walking its beat while being hit would look broken. The route is
 * picked up again from the point it was heading for once the fight ends.
 */
public final class NpcPathWalker {

    /**
     * How often the route is re-examined.
     *
     * <p>The navigation walks by itself between these; this only has to notice arrivals and hand
     * out the next point. Matches {@code tickLeash}'s own cadence for the same reason.
     */
    public static final int CHECK_TICKS = 10;

    /** Close enough to count as arrived. Just over a block, so standing beside it counts. */
    public static final double ARRIVE_DISTANCE = 1.6;

    /**
     * How long a point may take before it is given up on.
     *
     * <p>Twenty seconds. An NPC wedged on a fence post or pathing around a wall must not stall its
     * whole patrol forever — {@code isDone()} alone is not enough, because a navigation that never
     * started is done immediately and one that is inching sideways never is.
     */
    public static final int POINT_TIMEOUT_TICKS = 400;
    public static final int POINT_PAUSE_TICKS = 20;

    /** -1 begins a dwell, zero has completed it. Timeouts never create a dwell. */
    static int dwellRemaining(boolean pauses, boolean arrived, boolean gaveUp, int remaining) {
        if (!pauses || !arrived || gaveUp) return 0;
        return remaining < 0 ? POINT_PAUSE_TICKS : Math.max(0, remaining - CHECK_TICKS);
    }

    private NpcPathWalker() {
    }

    /**
     * Whether this NPC is walking a route right now.
     *
     * <p>Read by {@code XenoNpcBehaviour.tickLeash}, which must stand down while it is true: the
     * leash drags an NPC back when it leaves its home radius, and a patrol that goes further than
     * that would be fought every twenty ticks. In play that reads as an NPC shuddering in place.
     */
    public static boolean patrolling(XenoNpcEntity npc) {
        return npc != null && npc.pathIndex() >= 0 && npc.getTarget() == null;
    }

    /**
     * One NPC's turn.
     *
     * <p>Called every tick from {@code aiStep}; returns immediately for the overwhelming majority
     * of NPCs, which have no route at all.
     */
    public static void tick(XenoNpcEntity npc, NpcCombatProfile profile) {
        if (npc != null && net.bullettrain.xenopixelsmod.npc.job.NpcFollowerJob.active(profile)) {
            npc.setPathIndex(-1);
            net.bullettrain.xenopixelsmod.npc.movement.NpcMovementOwner.release(npc,
                    net.bullettrain.xenopixelsmod.npc.movement.NpcMovementOwner.Claim.PATROL);
            return;
        }
        if (npc == null || profile == null || profile.path == null || !profile.path.walkable()) {
            // Nothing to walk. Clear the marker so the leash resumes for an NPC whose route was
            // just deleted, rather than leaving it permanently unleashed.
            if (npc != null && npc.pathIndex() >= 0) {
                npc.setPathIndex(-1);
                // Not on a route any more, so the leash may take over again.
                net.bullettrain.xenopixelsmod.npc.movement.NpcMovementOwner.release(npc,
                        net.bullettrain.xenopixelsmod.npc.movement.NpcMovementOwner.Claim.PATROL);
            }
            return;
        }
        if (npc.getTarget() != null) {
            // In a fight. The index is kept, so the route resumes where it left off, but the
            // marker goes so the leash can still haul it home from a long chase.
            npc.setPathIndex(-1);
            // Not on a route any more, so the leash may take over again.
            net.bullettrain.xenopixelsmod.npc.movement.NpcMovementOwner.release(npc,
                    net.bullettrain.xenopixelsmod.npc.movement.NpcMovementOwner.Claim.PATROL);
            return;
        }
        // Staggered by entity id, like the bard and the battle-power refresh, so a line of guards
        // does not all re-path on the same tick.
        if ((npc.tickCount + npc.getId()) % CHECK_TICKS != 0) {
            return;
        }

        // The route outranks the leash, and yields to a scene. Claimed before any point is
        // chosen so that a scene running on this NPC is not interrupted by its own patrol.
        if (!net.bullettrain.xenopixelsmod.npc.XenoNpcBehaviour.claimMovement(npc,
                net.bullettrain.xenopixelsmod.npc.movement.NpcMovementOwner.Claim.PATROL)) {
            return;
        }

        NpcPath path = profile.path;
        int index = npc.pathIndex();
        if (index < 0 || index >= path.size()) {
            index = nearestPoint(npc, path);
            npc.setPathForward(true);
            head(npc, path, index);
            return;
        }

        NpcPath.Point point = path.get(index);
        if (point == null) {
            npc.setPathIndex(-1);
            // Not on a route any more, so the leash may take over again.
            net.bullettrain.xenopixelsmod.npc.movement.NpcMovementOwner.release(npc,
                    net.bullettrain.xenopixelsmod.npc.movement.NpcMovementOwner.Claim.PATROL);
            return;
        }

        boolean arrived = npc.distanceToSqr(point.x() + 0.5, point.y(), point.z() + 0.5)
                <= ARRIVE_DISTANCE * ARRIVE_DISTANCE;
        boolean gaveUp = npc.pathPointTicks() >= POINT_TIMEOUT_TICKS;
        if (!arrived && !gaveUp) {
            npc.setPathPointTicks(npc.pathPointTicks() + CHECK_TICKS);
            if (npc.getNavigation().isDone()
                    && net.bullettrain.xenopixelsmod.npc.movement.NpcMovementOwner
                            .progressing(npc)) {
                // Re-issued rather than left alone: the navigation gives up on its own when a
                // route is blocked, and without this the NPC would stand still until the timeout.
                //
                // Guarded by progressing(), because re-issuing forever at something it cannot reach
                // is what the shudder was. Once it has plainly failed the NPC holds still and waits
                // out POINT_TIMEOUT_TICKS, then moves on to the next point.
                npc.getNavigation().moveTo(point.x() + 0.5, point.y(), point.z() + 0.5,
                        path.speed());
            }
            return;
        }

        int dwell = dwellRemaining(path.pauses(), arrived, gaveUp, npc.pathDwellTicks());
        npc.setPathDwellTicks(dwell);
        if (dwell > 0) {
            npc.getNavigation().stop();
            return;
        }

        int next = path.next(index, npc.pathForward());
        npc.setPathForward(path.nextForward(index, npc.pathForward()));
        if (next < 0) {
            // A ONCE route that has finished. It stays where it is and the leash takes over again.
            npc.setPathIndex(-1);
            // Not on a route any more, so the leash may take over again.
            net.bullettrain.xenopixelsmod.npc.movement.NpcMovementOwner.release(npc,
                    net.bullettrain.xenopixelsmod.npc.movement.NpcMovementOwner.Claim.PATROL);
            return;
        }
        head(npc, path, next);
    }

    /** Points the NPC at one stop and starts its clock. */
    private static void head(XenoNpcEntity npc, NpcPath path, int index) {
        NpcPath.Point point = path.get(index);
        if (point == null) {
            npc.setPathIndex(-1);
            // Not on a route any more, so the leash may take over again.
            net.bullettrain.xenopixelsmod.npc.movement.NpcMovementOwner.release(npc,
                    net.bullettrain.xenopixelsmod.npc.movement.NpcMovementOwner.Claim.PATROL);
            return;
        }
        npc.setPathIndex(index);
        npc.setPathPointTicks(0);
        npc.setPathDwellTicks(-1);
        // A new destination, so any record of failing to reach the last one is meaningless - an
        // NPC that got wedged once must not refuse to walk for the rest of its life.
        net.bullettrain.xenopixelsmod.npc.movement.NpcMovementOwner.clearProgress(npc);
        // +0.5 so it walks to the middle of the block rather than its corner.
        npc.getNavigation().moveTo(point.x() + 0.5, point.y(), point.z() + 0.5, path.speed());
    }

    /**
     * The point to start from.
     *
     * <p>The nearest one, so an NPC placed anywhere along its route joins it where it stands
     * rather than walking back to the beginning first.
     */
    static int nearestPoint(XenoNpcEntity npc, NpcPath path) {
        int best = 0;
        double bestDistance = Double.MAX_VALUE;
        for (int i = 0; i < path.size(); i++) {
            NpcPath.Point point = path.get(i);
            double distance = npc.distanceToSqr(point.x() + 0.5, point.y(), point.z() + 0.5);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = i;
            }
        }
        return best;
    }
}
