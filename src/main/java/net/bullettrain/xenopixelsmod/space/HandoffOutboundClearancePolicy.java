package net.bullettrain.xenopixelsmod.space;

/** Pure hysteresis rule for preventing an outbound surface arrival from immediately triggering inbound travel. */
public final class HandoffOutboundClearancePolicy {
    private HandoffOutboundClearancePolicy() {}

    public record Decision(String armedBody, boolean suppressCandidate) {}

    /**
     * Retains the outbound body latch until the player is at least {@code requiredClearanceGap} beyond its surface.
     * Invalid distance data fails closed for that same body, while other bodies remain enterable.
     */
    public static Decision evaluate(String armedBody, String candidateBody, double surfaceGap,
                                    double requiredClearanceGap) {
        if (armedBody == null || armedBody.isBlank()) return new Decision(null, false);
        if (Double.isFinite(surfaceGap) && Double.isFinite(requiredClearanceGap)
                && requiredClearanceGap >= 0.0 && surfaceGap >= requiredClearanceGap)
            return new Decision(null, false);
        return new Decision(armedBody, armedBody.equals(candidateBody));
    }
}
