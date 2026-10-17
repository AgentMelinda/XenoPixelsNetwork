package net.bullettrain.xenopixelsmod.space.physics;

/** Selects which generated-body builder may defer Sable's per-physics-tick merged-mass update. */
final class BuildMassDeferralPolicy {
    private BuildMassDeferralPolicy() {}

    /**
     * Deferral is only valid for the repeated {@code ServerLevelPlot.load} builder. The incremental-section writer
     * does not call that loader, and must keep Sable's normal per-tick mass updates while its chunk lifecycle advances.
     */
    static boolean deferPhysicsTickMass(boolean deferralEnabled, boolean incrementalSectionWriter) {
        return deferralEnabled && !incrementalSectionWriter;
    }
}
