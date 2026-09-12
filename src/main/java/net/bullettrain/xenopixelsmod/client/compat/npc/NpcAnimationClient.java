package net.bullettrain.xenopixelsmod.client.compat.npc;

import net.bullettrain.xenopixelsmod.client.combat.ScriptAnimSpeedClient;
import net.bullettrain.xenopixelsmod.compat.npc.NpcDmzAnim;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Clips the server has told this client to play on an NPC, waiting for that NPC's next frame.
 *
 * <p>A real player is applied immediately: they already exist and implement
 * {@code IPlayerAnimatable}. An NPC is queued until {@code NpcFullDmzRenderer} can hand the clip
 * to its synthetic player.
 *
 * <p><b>Consume after delivery.</b> DragonMineZ's attack controller reads
 * {@code dragonminez$currentMeleeAnim}, calls {@code forceAnimationReset()} and clears the field,
 * so handing it the same clip on every frame restarts the animation. The renderer therefore peeks,
 * applies the clip, and removes exactly that pending value only after the call succeeds.
 */
public final class NpcAnimationClient {

    /** @param speed playback multiplier, already clamped by the sender */
    public record Pending(String animation, float speed, boolean hold, boolean stop) {
        public Pending(String animation, float speed) {
            this(animation, speed, false, false);
        }
    }

    private static final Map<UUID, Pending> PENDING = new ConcurrentHashMap<>();

    private NpcAnimationClient() {
    }

    /** The sentinel name that cancels a queued clip instead of queueing one. */
    public static final String STOP = "xeno:stop";

    public static void receive(UUID npc, String animation, float speed, int flags) {
        if (npc == null || animation == null || animation.isBlank()) {
            return;
        }
        boolean stop = STOP.equals(animation) || (flags & NpcDmzAnim.FLAG_STOP) != 0;
        boolean hold = (flags & NpcDmzAnim.FLAG_HOLD) != 0;
        Pending pending = new Pending(animation, Math.max(0.15f, speed), hold, stop);
        if (applyToPlayer(npc, pending)) {
            return;
        }
        PENDING.put(npc, pending);
    }

    public static void queue(UUID npc, String animation, float speed) {
        receive(npc, animation, speed, 0);
    }

    static boolean applyToPlayer(UUID npc, Pending pending) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.level == null
                    || !(mc.level.getPlayerByUUID(npc) instanceof AbstractClientPlayer player)) {
                return false;
            }
            if (!(player instanceof com.dragonminez.client.animation.IPlayerAnimatable animatable)) {
                return false;
            }
            apply(player, animatable, pending);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static void apply(AbstractClientPlayer player,
                             com.dragonminez.client.animation.IPlayerAnimatable animatable,
                             Pending pending) {
        if (pending == null || animatable == null) {
            return;
        }
        UUID id = player.getUUID();
        if (pending.stop()) {
            animatable.dragonminez$stopKiAnimation();
            ScriptAnimSpeedClient.clear(id);
            return;
        }
        if (pending.hold()) {
            ScriptAnimSpeedClient.put(id, pending.speed());
            animatable.dragonminez$playKiAnimation(pending.animation(), true);
            return;
        }
        animatable.dragonminez$playMeleeAnimation(pending.animation(), false, pending.speed());
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
        ScriptAnimSpeedClient.clearAll();
    }
}
