package net.bullettrain.xenopixelsmod.client.aura;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Which players have their second aura switched on, as the server told this client
 * ({@code SecondAuraStatePacket}). Keyed by entity id, like {@code SparkingClientState}.
 */
public final class SecondAuraClientState {
    private static final Set<Integer> ON = ConcurrentHashMap.newKeySet();

    private SecondAuraClientState() {
    }

    public static void set(int entityId, boolean on) {
        if (on) {
            ON.add(entityId);
        } else {
            ON.remove(entityId);
        }
    }

    public static boolean on(int entityId) {
        return ON.contains(entityId);
    }

    /** Dropped on disconnect so a rejoin cannot inherit a stale aura. */
    public static void clear() {
        ON.clear();
    }
}
