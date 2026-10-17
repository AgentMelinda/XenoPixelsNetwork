package net.bullettrain.xenopixelsmod.space.asteroid;

/** Pure hard-cap arithmetic for a Sable split that may add physical sublevels. */
final class AsteroidSplitCapacity {
    private AsteroidSplitCapacity() {}

    static boolean fitsGlobal(long managedRoots, long pendingBuilds, long otherLiveBodies,
                              long pendingChildren, long globalCap) {
        if (managedRoots < 0 || pendingBuilds < 0 || otherLiveBodies < 0
                || pendingChildren < 0 || globalCap < 0) return false;
        return managedRoots + pendingBuilds + otherLiveBodies + pendingChildren <= globalCap;
    }

    static boolean fitsNearbyViewer(long currentNearbyBodies, long pendingChildren, long perPlayerCap) {
        if (currentNearbyBodies < 0 || pendingChildren < 0 || perPlayerCap < 0) return false;
        return currentNearbyBodies + pendingChildren <= perPlayerCap;
    }
}
