package net.bullettrain.xenopixelsmod.space;

/** Pure coordinate-frame translation between a planet dimension and its moving Voidsea chart body. */
public final class SpaceHandoffCoordinates {
    private static final double MAX_COORDINATE = 30_000_000.0;

    public record Horizontal(double x, double z) { }

    private SpaceHandoffCoordinates() { }

    /**
     * Keeps a player's planet-local horizontal displacement from the authored spatial anchor when moving
     * to the chart body's current host position. Null means the input or mapped output is outside the
     * supported finite world-coordinate range; callers should refuse the handoff rather than clamp it.
     */
    public static Horizontal outbound(double playerX, double playerZ, double spatialAnchorX,
                                      double spatialAnchorZ, double bodyHostX, double bodyHostZ) {
        if (!supported(playerX) || !supported(playerZ) || !supported(spatialAnchorX)
                || !supported(spatialAnchorZ) || !supported(bodyHostX) || !supported(bodyHostZ)) return null;
        double x = bodyHostX + (playerX - spatialAnchorX);
        double z = bodyHostZ + (playerZ - spatialAnchorZ);
        return supported(x) && supported(z) ? new Horizontal(x, z) : null;
    }

    private static boolean supported(double value) {
        return Double.isFinite(value) && Math.abs(value) < MAX_COORDINATE;
    }
}
