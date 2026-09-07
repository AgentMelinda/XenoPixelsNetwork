package net.bullettrain.xenopixelsmod.client.compat.npc;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Clips the server has told this client to play on an NPC, waiting for that NPC's next frame.
 *
 * <p><b>Consume after delivery.</b> DragonMineZ's attack controller reads
 * {@code dragonminez$currentMeleeAnim}, calls {@code forceAnimationReset()} and clears the field,
 * so handing it the same clip on every frame restarts the animation. The renderer therefore peeks,
 * applies the clip, and removes exactly that pending value only after the call succeeds.
 *
 * <p>Entries are held rather than applied directly because the packet can arrive before the NPC's
 * renderer runs - or before the entity exists at all, since these are keyed by UUID.
 */
public final class NpcAnimationClient {

    /** @param speed playback multiplier, already clamped by the sender */
    public record Pending(String animation, float speed) {
    }

    private static final Map<UUID, Pending> PENDING = new ConcurrentHashMap<>();

    private NpcAnimationClient() {
    }

    public static void queue(UUID npc, String animation, float speed) {
        if (npc == null || animation == null || animation.isBlank()) {
            return;
        }
        PENDING.put(npc, new Pending(animation, Math.max(0.15f, speed)));
    }

    /** The clip waiting for this NPC. Null when there is nothing queued. */
    public static Pending peek(UUID npc) {
        return npc == null ? null : PENDING.get(npc);
    }

    /** Removes {@code delivered} only if a newer packet has not replaced it in the meantime. */
    public static boolean consume(UUID npc, Pending delivered) {
        return npc != null && delivered != null && PENDING.remove(npc, delivered);
    }

    /** Dropped on disconnect so a stale clip cannot fire at a reused UUID in the next world. */
    public static void clear() {
        PENDING.clear();
    }
}
