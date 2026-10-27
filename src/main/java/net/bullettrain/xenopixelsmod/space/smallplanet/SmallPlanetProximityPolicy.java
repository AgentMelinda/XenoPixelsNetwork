package net.bullettrain.xenopixelsmod.space.smallplanet;

/** Selection rules for active bodies that may lag behind their deterministic orbital target. */
public final class SmallPlanetProximityPolicy {
    private SmallPlanetProximityPolicy() {}

    public static double nearestDistanceSquared(double analyticDistanceSquared, double liveDistanceSquared) {
        return Math.min(validDistance(analyticDistanceSquared), validDistance(liveDistanceSquared));
    }

    public static boolean shouldSelect(double analyticDistanceSquared, double liveDistanceSquared,
                                       double releaseRange, int bodyRadius,
                                       boolean playerTracksBody, boolean playerIntersectsBody) {
        if (playerTracksBody || playerIntersectsBody) return true;
        if (!Double.isFinite(releaseRange) || releaseRange < 0.0 || bodyRadius < 0) return false;
        double reach = releaseRange + bodyRadius;
        double distanceSquared = nearestDistanceSquared(analyticDistanceSquared, liveDistanceSquared);
        return Double.isFinite(distanceSquared) && distanceSquared <= reach * reach;
    }

    private static double validDistance(double distanceSquared) {
        return Double.isFinite(distanceSquared) && distanceSquared >= 0.0
                ? distanceSquared : Double.POSITIVE_INFINITY;
    }
}
