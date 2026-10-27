package net.bullettrain.xenopixelsmod.space.smallplanet;

/** Pure admission policy for prioritizing released-body serialization audits. */
final class SmallPlanetReleaseMarkerAuditPolicy {
    private SmallPlanetReleaseMarkerAuditPolicy() {}

    static boolean shouldPrioritize(boolean hasReleaseDecision, boolean releasePending) {
        return hasReleaseDecision && !releasePending;
    }

    static boolean shouldWarnReadbackFailure(int attempts, int failedReadbacks, int pruned,
                                             long lastWarningTick, long nowTick, long cooldownTicks) {
        if (attempts <= 0 || failedReadbacks != attempts || pruned != 0 || cooldownTicks <= 0) return false;
        if (lastWarningTick == Long.MIN_VALUE) return true;
        return nowTick >= lastWarningTick && nowTick - lastWarningTick >= cooldownTicks;
    }

    static boolean shouldWarnUnresolvablePointer(int pointerlessCandidates, long lastWarningTick,
                                                 long nowTick, long cooldownTicks) {
        if (pointerlessCandidates <= 0 || cooldownTicks <= 0) return false;
        if (lastWarningTick == Long.MIN_VALUE) return true;
        return nowTick >= lastWarningTick && nowTick - lastWarningTick >= cooldownTicks;
    }
}
