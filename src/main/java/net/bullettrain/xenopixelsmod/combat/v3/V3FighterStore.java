package net.bullettrain.xenopixelsmod.combat.v3;

import net.minecraft.server.level.ServerPlayer;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Internal server-thread session storage. */
public final class V3FighterStore {
    private static final Map<UUID, V3Fighter> FIGHTERS = new HashMap<>();
    private V3FighterStore() {}
    public static V3Fighter get(ServerPlayer player) { return FIGHTERS.computeIfAbsent(player.getUUID(), V3Fighter::new); }
    public static V3Fighter peek(ServerPlayer player) { return FIGHTERS.get(player.getUUID()); }
    public static void remove(UUID player) { FIGHTERS.remove(player); }
    static void clearAll() { FIGHTERS.clear(); }
}
