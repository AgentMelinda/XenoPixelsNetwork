package net.bullettrain.xenopixelsmod.space.smallplanet;

import java.util.UUID;

/** Fail-closed rule for retiring a SavedData release decision after reading its Sable storage slot. */
public final class SmallPlanetReleaseMarkerReadbackPolicy {
    private SmallPlanetReleaseMarkerReadbackPolicy() {}

    static boolean shouldRunStartupAudit(boolean adopted, boolean auditAlreadyRan) {
        return adopted && !auditAlreadyRan;
    }

    public static boolean mayPrune(UUID expectedBody, UUID storedBody, boolean releasedMarker,
                                   boolean releaseCleanupPending) {
        return !releaseCleanupPending && expectedBody != null && expectedBody.equals(storedBody)
                && releasedMarker;
    }

    public static boolean mayRepairMarker(UUID expectedBody, UUID storedBody, boolean releasedMarker,
                                          boolean liveBodyAvailable) {
        return liveBodyAvailable && !releasedMarker && expectedBody != null && expectedBody.equals(storedBody);
    }
}
