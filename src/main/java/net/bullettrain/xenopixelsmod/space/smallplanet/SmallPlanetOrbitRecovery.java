package net.bullettrain.xenopixelsmod.space.smallplanet;

/** Keeps resumed physical bodies on the same deterministic orbit used by client LOD and travel fallbacks. */
public final class SmallPlanetOrbitRecovery {
    private SmallPlanetOrbitRecovery() {}

    /**
     * Rejoin the candidate's canonical seeded orbit. The physical controller eases the body toward this path;
     * it must not derive a new radius, period, or plane from a temporary parked position.
     */
    public static SmallPlanetOrbit canonicalSeedOrbit(SmallPlanetField.Candidate identity,
                                                       double starX, double starY, double starZ) {
        return SmallPlanetOrbit.fromCandidate(identity, starX, starY, starZ);
    }
}
