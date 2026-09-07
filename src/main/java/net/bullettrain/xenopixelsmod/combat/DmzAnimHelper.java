package net.bullettrain.xenopixelsmod.combat;

import com.dragonminez.common.network.NetworkHandler;
import com.dragonminez.common.network.S2C.MeleeAnimationS2C;
import com.dragonminez.common.network.S2C.TriggerAnimationS2C;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.TickTask;

/**
 * Server-side DragonMineZ animation helpers (common-safe: no client-only imports).
 * Client prediction lives in {@link net.bullettrain.xenopixelsmod.client.combat.DmzAnimHelperClient}.
 */
public final class DmzAnimHelper {
    public static final String CHARGE_LIGHT = "base.charge_light_punch";
    public static final String CHARGE_LIGHT_FIRE = "base.charge_light_punch_fire";
    public static final String CHARGE_HEAVY = "base.charge_heavy_punch";
    public static final String CHARGE_HEAVY_FIRE = "base.charge_heavy_punch_fire";
    public static final String KI_CHARGE = "base.ki_charge";
    /** DMZ hold pose for blocking (movement.animation.json). */
    public static final String BLOCK = "base.block";
    public static final String SHIELD_RIGHT = "base.shield_right";
    public static final String SHIELD_LEFT = "base.shield_left";
    public static final String PUNCH_RIGHT = "combat.one_handed_punch_right";
    public static final String PUNCH_LEFT = "combat.one_handed_punch_left";
    public static final String UPPERCUT_RIGHT = "combat.one_handed_uppercut_right";
    public static final String UPPERCUT_LEFT = "combat.one_handed_uppercut_left";
    public static final String KICK_LOW_R = "combat.lowkick_right";
    public static final String KICK_LOW_L = "combat.lowkick_left";
    public static final String KICK_GUT_R = "combat.gutkick_right";
    public static final String KICK_GUT_L = "combat.gutkick_left";
    public static final String DASH_FRONT = "base.dash_front";
    public static final String ATTACK1 = "base.attack1";
    public static final String ATTACK2 = "base.attack2";
    /** Looping palm-out Hakai channel (our clip, not DMZ's). */
    public static final String HAKAI_HOLD = "combat.xeno_hakai_hold";
    /** Short palm snap on Hakai commit. */
    public static final String HAKAI_FIRE = "combat.xeno_hakai_fire";

    private DmzAnimHelper() {}

    public enum ChargeStyle {
        FIST_LIGHT,
        FIST_HEAVY,
        KICK,
        DRAGON
    }

    public static void broadcastChargeStart(ServerPlayer player, ChargeStyle style) {
        String anim = switch (style) {
            case FIST_LIGHT -> CHARGE_LIGHT;
            case FIST_HEAVY, KICK -> CHARGE_HEAVY;
            case DRAGON -> KI_CHARGE;
        };
        try {
            TriggerAnimationS2C pkt = new TriggerAnimationS2C(
                    player.getUUID(),
                    TriggerAnimationS2C.AnimationType.KI_ANIMATION,
                    0,
                    player.getId(),
                    anim);
            // Tracking only — local client already predicts (avoids double anim)
            NetworkHandler.sendToTrackingEntity(pkt, player);
        } catch (Throwable ignored) {
        }
    }

    public static void broadcastChargeStop(ServerPlayer player) {
        try {
            TriggerAnimationS2C pkt = new TriggerAnimationS2C(
                    player.getUUID(),
                    TriggerAnimationS2C.AnimationType.KI_ANIMATION_STOP,
                    0,
                    player.getId(),
                    "");
            NetworkHandler.sendToTrackingEntity(pkt, player);
        } catch (Throwable ignored) {
        }
    }

    /** Start DMZ block hold pose for nearby clients (local client predicts separately). */
    public static void broadcastBlockStart(ServerPlayer player) {
        try {
            TriggerAnimationS2C pkt = new TriggerAnimationS2C(
                    player.getUUID(),
                    TriggerAnimationS2C.AnimationType.KI_ANIMATION,
                    0,
                    player.getId(),
                    BLOCK);
            NetworkHandler.sendToTrackingEntity(pkt, player);
        } catch (Throwable ignored) {
        }
    }

    public static void broadcastBlockStop(ServerPlayer player) {
        broadcastChargeStop(player);
    }

    /** Looping palm-out for the caster and everyone tracking them. */
    public static void broadcastHakaiHold(ServerPlayer player) {
        try {
            TriggerAnimationS2C pkt = new TriggerAnimationS2C(
                    player.getUUID(),
                    TriggerAnimationS2C.AnimationType.KI_ANIMATION,
                    0,
                    player.getId(),
                    HAKAI_HOLD);
            NetworkHandler.sendToTrackingEntityAndSelf(pkt, player);
        } catch (Throwable ignored) {
            try {
                NetworkHandler.sendToTrackingEntity(new TriggerAnimationS2C(
                        player.getUUID(),
                        TriggerAnimationS2C.AnimationType.KI_ANIMATION,
                        0,
                        player.getId(),
                        HAKAI_HOLD), player);
            } catch (Throwable ignored2) {
            }
        }
        broadcastMelee(player, HAKAI_HOLD, false, 1.0f);
    }

    public static void broadcastHakaiFire(ServerPlayer player) {
        broadcastChargeStop(player);
        broadcastMelee(player, HAKAI_FIRE, false, 1.15f);
    }

    public static void broadcastHakaiStop(ServerPlayer player) {
        broadcastChargeStop(player);
    }

    /**
     * Punch-only combo step anim (left/right, uppercut every finisher beat).
     * @param step 1-based combo step
     */
    public static void broadcastComboPunch(ServerPlayer player, int step, boolean finisher) {
        if (player == null) return;
        String anim;
        if (finisher) {
            anim = step % 2 == 0 ? UPPERCUT_LEFT : UPPERCUT_RIGHT;
        } else {
            anim = step % 2 == 0 ? PUNCH_LEFT : PUNCH_RIGHT;
        }
        broadcastMelee(player, anim, false, finisher ? 1.2f : 1.08f);
    }

    /** Uncharged combo kick (even steps). */
    public static void broadcastComboKick(ServerPlayer player, int step, boolean finisher) {
        if (player == null) return;
        String anim = step % 4 == 0 ? KICK_GUT_R : KICK_LOW_R;
        if (step % 4 == 2) {
            anim = KICK_GUT_L;
        }
        broadcastMelee(player, anim, false, finisher ? 1.2f : 1.1f);
    }

    public static void broadcastMelee(ServerPlayer player, String animationName, boolean offhand, float speed) {
        try {
            MeleeAnimationS2C pkt = new MeleeAnimationS2C(player.getId(), animationName, offhand, speed);
            NetworkHandler.sendToTrackingEntity(pkt, player);
        } catch (Throwable ignored) {
        }
    }

    public static void broadcastDash(ServerPlayer player, int variant) {
        try {
            TriggerAnimationS2C pkt = new TriggerAnimationS2C(
                    player.getUUID(),
                    TriggerAnimationS2C.AnimationType.DASH,
                    variant,
                    player.getId());
            NetworkHandler.sendToTrackingEntity(pkt, player);
        } catch (Throwable ignored) {
        }
    }

    /** Full release: charge fire + follow-up chain (kick/punch). */
    public static void playChargeRelease(ServerPlayer player, ChargeStyle style, boolean fullyCharged) {
        playChargeRelease(player, style, fullyCharged, 0, true);
    }

    /**
     * @param verticalBias kick only: +1 up, -1 down
     * @param chainAnims when false, only the primary fire anim
     */
    public static void playChargeRelease(ServerPlayer player, ChargeStyle style, boolean fullyCharged,
                                         int verticalBias, boolean chainAnims) {
        broadcastChargeStop(player);
        float speed = fullyCharged ? 1.2f : 1.05f;

        if (style == ChargeStyle.KICK) {
            // Primary gut kick (sexy mid hit)
            String primary = fullyCharged ? KICK_GUT_R : KICK_GUT_L;
            if (verticalBias < 0) primary = KICK_LOW_R;
            broadcastMelee(player, primary, false, speed);
            if (chainAnims) {
                // Delayed follow-up: low kick or opposite side gut for a 2-hit chain
                String follow = verticalBias > 0 ? KICK_GUT_R : (verticalBias < 0 ? KICK_LOW_L : KICK_LOW_R);
                scheduleMelee(player, follow, false, speed * 1.05f, 4);
                if (fullyCharged) {
                    scheduleMelee(player, KICK_GUT_R, false, 1.25f, 8);
                }
            }
            return;
        }

        if (style == ChargeStyle.DRAGON) {
            broadcastMelee(player, CHARGE_HEAVY_FIRE, false, speed);
            broadcastDash(player, 0);
            if (chainAnims) {
                scheduleMelee(player, PUNCH_RIGHT, false, 1.15f, 5);
            }
            return;
        }

        // Fist punch
        String fire = fullyCharged ? CHARGE_HEAVY_FIRE : CHARGE_LIGHT_FIRE;
        broadcastMelee(player, fire, false, speed);
        if (chainAnims) {
            // Second punch hand for a snappy combo finish
            scheduleMelee(player, fullyCharged ? PUNCH_LEFT : ATTACK2, false, speed * 1.1f, 3);
            if (fullyCharged) {
                scheduleMelee(player, PUNCH_RIGHT, false, 1.2f, 7);
            }
        }
    }

    private static void scheduleMelee(ServerPlayer player, String anim, boolean offhand, float speed, int delayTicks) {
        if (player.getServer() == null) return;
        int when = player.getServer().getTickCount() + Math.max(1, delayTicks);
        player.getServer().tell(new TickTask(when, () -> {
            if (!player.isAlive()) return;
            broadcastMelee(player, anim, offhand, speed);
        }));
    }
}
