package net.bullettrain.xenopixelsmod.space.dragonball;

/** Pure geometry rules for entering a physical SDB and returning above its live hitbox. */
public final class DragonBallSurfaceTravelMath {
    private static final double MAX_COORDINATE = 30_000_000.0;

    private DragonBallSurfaceTravelMath() {}

    /** Tests radial contact without a square root; invalid geometry fails closed. */
    public static boolean touches(double playerX, double playerY, double playerZ,
                                  double centerX, double centerY, double centerZ,
                                  double radius, double margin) {
        if (!finite(playerX, playerY, playerZ, centerX, centerY, centerZ, radius, margin)
                || radius < 0.0 || margin < 0.0) return false;
        double reach = radius + margin;
        if (!Double.isFinite(reach) || reach >= MAX_COORDINATE) return false;
        double dx = playerX - centerX, dy = playerY - centerY, dz = playerZ - centerZ;
        return dx * dx + dy * dy + dz * dz <= reach * reach;
    }

    /** Absolute Y exit point that clears the spherical body by {@code clearance} blocks. */
    public static double exitAltitude(double centerY, double radius, double clearance) {
        if (!finite(centerY, radius, clearance) || radius < 0.0 || clearance < 0.0) return Double.NaN;
        double y = centerY + radius + clearance;
        return Double.isFinite(y) && Math.abs(y) < MAX_COORDINATE ? y : Double.NaN;
    }

    /** Surface-world atmosphere hands off at the same Y threshold used by surface travel. */
    public static double surfaceExitAltitude(int maxBuildHeight) {
        return maxBuildHeight - 16.0;
    }

    /** A heightmap returning minBuildHeight denotes an empty column, not a safe landing surface. */
    public static boolean hasSafeSurfaceY(int surfaceY, int minBuildHeight) {
        return surfaceY > minBuildHeight;
    }

    /** Accepts a rebound body only when the stable deterministic slot identity still matches. */
    public static boolean sameSlotPlanetIdentity(int slot, String tripPlanetKey, String currentPlanetKey) {
        if (slot < 1 || slot > CosmicDragonBalls.COUNT || tripPlanetKey == null || currentPlanetKey == null) {
            return false;
        }
        String expected = DragonBallIdentity.forSlot(slot).planetKey();
        return expected.equals(tripPlanetKey) && expected.equals(currentPlanetKey);
    }

    private static boolean finite(double... values) {
        for (double value : values) if (!Double.isFinite(value)) return false;
        return true;
    }
}
