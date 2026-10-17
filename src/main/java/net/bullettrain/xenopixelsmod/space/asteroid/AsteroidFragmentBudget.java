package net.bullettrain.xenopixelsmod.space.asteroid;

/** Pure Sable split budgeting. The heat-map mixin supplies counts from its pending connectivity map. */
public final class AsteroidFragmentBudget {
    private AsteroidFragmentBudget() {}

    /**
     * Mirrors Sable 2.0.3's split() group-retention branches.
     *
     * @return number of child sublevels Sable will assemble, or {@code -1} if the observation is invalid
     */
    public static int predictedChildren(int componentCount, int representedBlocks, int currentSolidCount) {
        if (componentCount < 0 || representedBlocks < 0 || currentSolidCount < 0
                || representedBlocks > currentSolidCount) return -1;
        if (componentCount == 0) return representedBlocks == 0 ? 0 : -1;
        if (representedBlocks == currentSolidCount) {
            if (componentCount == 1) return 0;
            return componentCount - 1; // Sable retains the largest component in the existing body.
        }
        return componentCount; // The Sable path assembles every represented component.
    }

    /** Returns true only when all already-live and pending children remain within both configured caps. */
    public static boolean fits(int currentForAsteroid, int currentGlobal, int pendingChildren,
                               int perAsteroidCap, int globalCap) {
        if (currentForAsteroid < 0 || currentGlobal < 0 || pendingChildren < 0
                || perAsteroidCap < 0 || globalCap < 0) return false;
        return (long) currentForAsteroid + pendingChildren <= perAsteroidCap
                && (long) currentGlobal + pendingChildren <= globalCap;
    }
}
