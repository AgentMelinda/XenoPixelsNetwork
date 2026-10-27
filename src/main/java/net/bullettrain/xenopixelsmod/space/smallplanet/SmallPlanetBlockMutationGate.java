package net.bullettrain.xenopixelsmod.space.smallplanet;

/** Cheap decision for whether a changed host-chunk write needs small-planet mutation tracking. */
final class SmallPlanetBlockMutationGate {
    private SmallPlanetBlockMutationGate() {}

    static boolean shouldInspect(boolean restoringSnapshots, boolean capturingSnapshots, boolean hasReservations) {
        return !restoringSnapshots && (capturingSnapshots || hasReservations);
    }
}
