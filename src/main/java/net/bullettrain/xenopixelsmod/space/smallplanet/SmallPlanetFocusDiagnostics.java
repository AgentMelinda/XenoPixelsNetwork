package net.bullettrain.xenopixelsmod.space.smallplanet;

/** One missing-focus diagnostic per chart revision or failure, rearmed after recovery. */
final class SmallPlanetFocusDiagnostics {
    private boolean missing;
    private int revision;
    private String reason;

    boolean missing(int currentRevision, String currentReason) {
        boolean report = !missing || revision != currentRevision || !currentReason.equals(reason);
        missing = true;
        revision = currentRevision;
        reason = currentReason;
        return report;
    }

    boolean recovered() {
        boolean report = missing;
        missing = false;
        reason = null;
        return report;
    }
}
