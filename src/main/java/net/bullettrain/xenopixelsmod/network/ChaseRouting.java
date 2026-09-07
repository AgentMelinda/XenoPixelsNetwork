package net.bullettrain.xenopixelsmod.network;

/**
 * Pure decisions behind the chase's obstacle detour, kept Minecraft-free so they can be tested.
 *
 * <p>The chase landing point sits at the target's feet, so a clip toward it grazes whatever the
 * target is standing on. Reading that as an obstruction is what made a chase started from directly
 * above climb away from its target instead of diving onto it: the floor under the target looked
 * like a wall, the detour raised the waypoint relative to the player's already high position, and
 * the next tick blocked again on the same floor.
 */
final class ChaseRouting {

    /** A near-vertical approach has nothing to climb over, so it never detours. */
    static final double MIN_HORIZONTAL_FOR_DETOUR = 1.5;

    /** How close to the destination a hit must be before it counts as the target's own ground. */
    static final double DESTINATION_TOLERANCE = 1.5;

    private ChaseRouting() {
    }

    /**
     * Is something genuinely in the way, rather than the surface the destination rests on?
     *
     * @param hitDistance         distance from the start of the clip to what it struck
     * @param destinationDistance distance from the start of the clip to the destination
     */
    static boolean obstructs(double hitDistance, double destinationDistance) {
        return hitDistance < destinationDistance - DESTINATION_TOLERANCE;
    }

    /** Detour only when the route actually goes somewhere horizontally. */
    static boolean detourWorthwhile(double horizontalDistance) {
        return horizontalDistance >= MIN_HORIZONTAL_FOR_DETOUR;
    }

    /**
     * Height the detour has to reach, measured from the obstruction itself.
     *
     * <p>Measuring from the player instead meant a player already above the obstacle was told to
     * climb again, every tick, forever.
     */
    static double clearanceY(double obstructionY, double bodyHeight, double clearance) {
        return obstructionY + bodyHeight + clearance;
    }

    /** A detour that would not get the player above the obstruction is not worth taking. */
    static boolean detourReachable(double requiredY, double startY, double maxClimb) {
        return requiredY > startY && requiredY <= startY + maxClimb;
    }
}
