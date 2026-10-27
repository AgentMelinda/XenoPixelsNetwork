package net.bullettrain.xenopixelsmod.client.space;

import net.bullettrain.xenopixelsmod.space.hakai.PlanetIntegritySnapshot;
import net.bullettrain.xenopixelsmod.space.hakai.PlanetIntegrityUpdate;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

/** Client cache for the latest complete server-authoritative integrity snapshot. */
@EventBusSubscriber(modid = "xenopixelsmod", value = Dist.CLIENT)
public final class PlanetIntegrityClient {
    private static final PlanetIntegritySnapshotAssembler SNAPSHOT = new PlanetIntegritySnapshotAssembler();

    private PlanetIntegrityClient() {}

    public static void apply(PlanetIntegritySnapshot page) {
        SNAPSHOT.accept(page);
    }

    public static void apply(PlanetIntegrityUpdate update) {
        SNAPSHOT.accept(update);
    }

    public static double integrity(String bodyId) { return SNAPSHOT.integrity(bodyId); }

    public static net.bullettrain.xenopixelsmod.space.hakai.PlanetIntegrityRules.Stage stage(String bodyId) {
        return SNAPSHOT.stage(bodyId);
    }

    public static long revision() { return SNAPSHOT.revision(); }
    public static java.util.Map<String, Double> values() { return SNAPSHOT.values(); }

    @SubscribeEvent public static void login(ClientPlayerNetworkEvent.LoggingIn event) { clear(); }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { clear(); }

    private static void clear() {
        SNAPSHOT.clear();
        PlanetIntegrityAtlasClient.clear();
    }
}
