package net.bullettrain.xenopixelsmod.space;

/** Pure classification for the chart gravity pass, which is intended for ships rather than managed planets. */
public final class SpaceGravityBodyPolicy {
    private SpaceGravityBodyPolicy() {}

    /**
     * Chart gravity is additive ship motion. Bodies with their own planetary/orbital controller must not be
     * treated as ships by that pass.
     */
    public static boolean receivesChartGravity(boolean managedPhysicalEarth, boolean generatedSmallPlanet,
                                               boolean dragonBallBodyTagged) {
        // The SDB predicate is deliberately identity-based. A tagged persisted body remains protected from
        // ship-field gravity while its orbit pass is disabled; it resumes motion when that owner is enabled.
        return !managedPhysicalEarth && !generatedSmallPlanet && !dragonBallBodyTagged;
    }
}
