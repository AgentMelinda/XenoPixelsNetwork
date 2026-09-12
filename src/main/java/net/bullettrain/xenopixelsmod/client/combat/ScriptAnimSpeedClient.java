package net.bullettrain.xenopixelsmod.client.combat;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Playback multiplier for a scripted KI-hold clip. DragonMineZ's KI path has no speed argument,
 * so the client stores the multiplier and the main-controller mixin applies it only while an
 * entry exists for that entity.
 */
public final class ScriptAnimSpeedClient {

    private static final Map<UUID, Float> SPEED = new ConcurrentHashMap<>();

    private ScriptAnimSpeedClient() {}

    public static void put(UUID id, float speed) {
        if (id != null) {
            SPEED.put(id, Math.max(0.15f, speed));
        }
    }

    public static Float get(UUID id) {
        return id == null ? null : SPEED.get(id);
    }

    public static void clear(UUID id) {
        if (id != null) {
            SPEED.remove(id);
        }
    }

    public static void clearAll() {
        SPEED.clear();
    }
}
