package net.bullettrain.xenopixelsmod.space.smallplanet;

/** Classifies the prerequisites required to reconstruct an adopted small planet's analytic orbit. */
public final class SmallPlanetOrbitAdmission {
    public enum Result { READY, MISSING_SYSTEM, MISSING_HOME_FOCUS }

    private SmallPlanetOrbitAdmission() {}

    public static Result classify(boolean systemResolved, boolean homeSystem, boolean homeFocusResolved) {
        if (!systemResolved) return Result.MISSING_SYSTEM;
        if (homeSystem && !homeFocusResolved) return Result.MISSING_HOME_FOCUS;
        return Result.READY;
    }

    public static boolean retryDue(long currentTick, long nextRetryTick) {
        return currentTick >= nextRetryTick;
    }

    /** Gives a body with no analytic orbit a hold target at its current pose and clears stale orbit velocity. */
    public static void writeStationaryTarget(double[] currentPosition, double[] targetPosition,
                                             double[] targetVelocity, double[] feedForward) {
        System.arraycopy(currentPosition, 0, targetPosition, 0, 3);
        java.util.Arrays.fill(targetVelocity, 0, 3, 0.0);
        java.util.Arrays.fill(feedForward, 0, 3, 0.0);
    }
}
