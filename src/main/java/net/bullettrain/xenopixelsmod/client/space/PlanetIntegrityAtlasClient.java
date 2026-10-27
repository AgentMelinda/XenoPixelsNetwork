package net.bullettrain.xenopixelsmod.client.space;

import net.bullettrain.xenopixelsmod.api.network.AddonNetwork;
import net.bullettrain.xenopixelsmod.space.hakai.PlanetIntegrityAtlasRequest;
import net.bullettrain.xenopixelsmod.space.hakai.PlanetIntegrityAtlasSnapshot;

/** Client-owned latest response to the finite Atlas position request. */
public final class PlanetIntegrityAtlasClient {
    private static long nextRequestId;
    private static long latestRequestId;
    private static PlanetIntegrityAtlasSnapshot snapshot;

    private PlanetIntegrityAtlasClient() {}

    public static long request() {
        if (nextRequestId == Long.MAX_VALUE) nextRequestId = 0;
        latestRequestId = ++nextRequestId;
        AddonNetwork.sendToServer(new PlanetIntegrityAtlasRequest(latestRequestId));
        return latestRequestId;
    }

    public static boolean apply(PlanetIntegrityAtlasSnapshot update) {
        if (update.requestId() != latestRequestId) return false;
        snapshot = update;
        return true;
    }

    public static PlanetIntegrityAtlasSnapshot snapshot() { return snapshot; }

    public static void clear() {
        snapshot = null;
        nextRequestId = 0;
        latestRequestId = 0;
    }
}
