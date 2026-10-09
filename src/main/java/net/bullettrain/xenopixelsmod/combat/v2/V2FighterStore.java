package net.bullettrain.xenopixelsmod.combat.v2;

import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The one map of live v2 fighters.
 *
 * <p>A fighter exists from a player's first v2 input until they log out, die, change dimension,
 * the server stops or the controller mode changes. Bounded by the players online. Server thread
 * only, like every event and handled packet that reaches it.
 */
final class V2FighterStore {

    private static final Map<UUID, V2Fighter> FIGHTERS = new HashMap<>();

    private V2FighterStore() {}

    static V2Fighter get(ServerPlayer player) {
        return FIGHTERS.computeIfAbsent(player.getUUID(), V2Fighter::new);
    }

    /** The fighter if one exists; never creates one. */
    static V2Fighter peek(ServerPlayer player) {
        return player == null ? null : FIGHTERS.get(player.getUUID());
    }

    static V2Fighter peek(UUID id) {
        return id == null ? null : FIGHTERS.get(id);
    }

    static V2Fighter remove(UUID id) {
        return id == null ? null : FIGHTERS.remove(id);
    }

    static Collection<V2Fighter> all() {
        return FIGHTERS.values();
    }

    static boolean isEmpty() {
        return FIGHTERS.isEmpty();
    }

    static void clear() {
        FIGHTERS.clear();
    }
}
