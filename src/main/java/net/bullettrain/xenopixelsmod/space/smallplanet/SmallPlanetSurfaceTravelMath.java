package net.bullettrain.xenopixelsmod.space.smallplanet;

/** Pure coordinate rules for returning from a generated planet surface cell to its body in Voidsea. */
final class SmallPlanetSurfaceTravelMath {
    private static final double EXIT_CLEARANCE = 24.0;

    private SmallPlanetSurfaceTravelMath() {}

    static double voidseaExitY(double bodyCenterY, int bodyRadius) {
        if (!Double.isFinite(bodyCenterY) || bodyRadius <= 0)
            throw new IllegalArgumentException("planet exit requires a finite center and positive radius");
        return bodyCenterY + bodyRadius + EXIT_CLEARANCE;
    }

    static boolean arrivalFitsRemovalBand(double feetY, double entityHeight, double minY, double maxY,
                                           int minBuildHeight, boolean unboundedHeight) {
        if (!Double.isFinite(feetY) || !Double.isFinite(entityHeight) || entityHeight <= 0.0) return false;
        return SmallPlanetOrbitSafety.currentBoundsFitRemovalBand(feetY, feetY + entityHeight,
                minY, maxY, minBuildHeight, unboundedHeight);
    }
}
