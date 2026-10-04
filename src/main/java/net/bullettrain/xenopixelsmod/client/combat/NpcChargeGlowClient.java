package net.bullettrain.xenopixelsmod.client.combat;

import net.minecraft.util.Mth;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client copy of an NPC charged punch / kick so the floor ring can draw.
 */
public final class NpcChargeGlowClient {
    public record Glow(boolean kick, long startGameTime, int holdTicks) {}

    private static final Map<UUID, Glow> GLOWS = new ConcurrentHashMap<>();

    private NpcChargeGlowClient() {}

    public static void begin(UUID npc, boolean kick, int holdTicks) {
        if (npc == null) {
            return;
        }
        GLOWS.put(npc, new Glow(kick, System.currentTimeMillis(), Math.max(1, holdTicks)));
    }

    public static void end(UUID npc) {
        if (npc != null) {
            GLOWS.remove(npc);
        }
    }

    public static void clear() {
        GLOWS.clear();
    }

    public static Glow get(UUID npc) {
        return npc == null ? null : GLOWS.get(npc);
    }

    public static Iterable<Map.Entry<UUID, Glow>> entries() {
        return GLOWS.entrySet();
    }

    public static float progress(Glow glow) {
        if (glow == null) {
            return 0.0f;
        }
        float elapsed = (System.currentTimeMillis() - glow.startGameTime()) / 50.0f;
        return Mth.clamp(elapsed / glow.holdTicks(), 0.0f, 1.0f);
    }
}
