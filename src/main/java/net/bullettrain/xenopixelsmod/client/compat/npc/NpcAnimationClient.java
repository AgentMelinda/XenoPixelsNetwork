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
 * {@code IPlayerAnimatable}. An NPC is queued until the Full renderer hands the clip to its
 * synthetic player or the native Gecko renderer hands it to the NPC's controller.
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

    /**
     * Last KI-hold clip the server asked this entity to keep. A first lookup can miss when the
     * library packet has not baked yet; {@link #retryActiveHolds()} replays after a bake.
     */
    private static final Map<UUID, Pending> ACTIVE_HOLD = new ConcurrentHashMap<>();

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
        boolean chargePunch = (flags & NpcDmzAnim.FLAG_CHARGE_PUNCH) != 0;
        boolean chargeKick = (flags & NpcDmzAnim.FLAG_CHARGE_KICK) != 0;
        if (chargePunch || chargeKick) {
            net.bullettrain.xenopixelsmod.client.combat.NpcChargeGlowClient.begin(
                    npc, chargeKick, Math.max(1, Math.round(speed)));
        }
        if (stop) {
            net.bullettrain.xenopixelsmod.client.combat.NpcChargeGlowClient.end(npc);
        }
        if (NpcDmzAnim.CHARGE_GLOW.equals(animation)) {
            if (stop && !chargePunch && !chargeKick) {
                net.bullettrain.xenopixelsmod.client.combat.NpcChargeGlowClient.end(npc);
            }
            return;
        }
        Pending pending = new Pending(animation, Math.max(0.15f, speed), hold, stop);
        if (stop) {
            ACTIVE_HOLD.remove(npc);
        } else if (hold) {
            ACTIVE_HOLD.put(npc, pending);
        } else {
            ACTIVE_HOLD.remove(npc);
        }
        if (applyToPlayer(npc, pending)) {
            xeno$traceOnce("applied directly to a player", animation);
            return;
        }
        PENDING.put(npc, pending);
        // Queued for a renderer to drain. An NPC always lands here - getPlayerByUUID finds only
        // real players - so this line plus a missing "drained" line is the signature of a clip
        // that arrived and was never drawn, which is invisible without saying so.
        xeno$traceOnce("queued for a renderer", animation);
    }

    /**
     * Says once per clip name what happened to it.
     *
     * <p>Diagnostic. The animation path collapses several decisions into booleans nobody reads, so
     * a clip that is broadcast, received, queued and never drawn produces no output anywhere. One
     * line per stage per name is enough to find where it stops without flooding a log.
     */
    private static final java.util.Set<String> TRACED =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    static void xeno$traceOnce(String stage, String animation) {
        if (TRACED.add(stage + "/" + animation)) {
            net.bullettrain.xenopixelsmod.XenoPixelsMod.LOGGER.info(
                    "Clip trace: {} - {}", animation, stage);
        }
    }

    /** Records that a native GeckoLib NPC consumed a queued clip. */
    public static void traceGeoDelivery(String animation) {
        xeno$traceOnce("drained by the Gecko renderer", animation);
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
        net.bullettrain.xenopixelsmod.client.combat.anim.Bt3AnimationBinding
                .registerAnimationName(pending.animation());
        if (pending.hold()) {
            ScriptAnimSpeedClient.put(id, pending.speed());
            // DragonMineZ only calls setAnimation when the KI name changes. A miss (library not
            // baked yet) still stores lastKiAnim, so the same clip never looks up again. Stop
            // first so the next controller tick searches XenoStudioClipCache.
            animatable.dragonminez$stopKiAnimation();
            animatable.dragonminez$playKiAnimation(pending.animation(), true);
            return;
        }
        animatable.dragonminez$playMeleeAnimation(pending.animation(), false, pending.speed());
    }

    /**
     * Re-delivers every KI-hold after the server library bakes, so a clip that arrived late
     * does not stay stuck on the failed first lookup.
     */
    public static void retryActiveHolds() {
        for (Map.Entry<UUID, Pending> entry : ACTIVE_HOLD.entrySet()) {
            Pending pending = entry.getValue();
            if (pending == null || pending.stop()) {
                continue;
            }
            if (!applyToPlayer(entry.getKey(), pending)) {
                PENDING.put(entry.getKey(), pending);
            }
        }
    }

    /** The clip waiting for this NPC. Null when there is nothing queued. */
    public static Pending peek(UUID npc) {
        return npc == null ? null : PENDING.get(npc);
    }

    /** Last KI-hold the server asked this NPC to keep, if any. */
    public static Pending activeHold(UUID npc) {
        return npc == null ? null : ACTIVE_HOLD.get(npc);
    }

    /** Removes {@code delivered} only if a newer packet has not replaced it in the meantime. */
    public static boolean consume(UUID npc, Pending delivered) {
        return npc != null && delivered != null && PENDING.remove(npc, delivered);
    }

    /** Dropped on disconnect so a stale clip cannot fire at a reused UUID in the next world. */
    public static void clear() {
        PENDING.clear();
        ACTIVE_HOLD.clear();
        ScriptAnimSpeedClient.clearAll();
        net.bullettrain.xenopixelsmod.client.combat.NpcChargeGlowClient.clear();
    }
}
