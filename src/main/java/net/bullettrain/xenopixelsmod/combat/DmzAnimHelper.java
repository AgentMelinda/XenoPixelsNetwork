package net.bullettrain.xenopixelsmod.combat;

import com.dragonminez.client.animation.IPlayerAnimatable;
import com.dragonminez.common.network.NetworkHandler;
import com.dragonminez.common.network.S2C.MeleeAnimationS2C;
import com.dragonminez.common.network.S2C.TriggerAnimationS2C;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.loading.FMLEnvironment;

/**
 * Plays DragonMineZ player animations the same way DMZ combat does
 * (MeleeAnimationS2C / TriggerAnimationS2C + local IPlayerAnimatable).
 */
public final class DmzAnimHelper {
    // DMZ movement.animation.json / combat.animation.json keys
    public static final String CHARGE_LIGHT = "base.charge_light_punch";
    public static final String CHARGE_LIGHT_FIRE = "base.charge_light_punch_fire";
    public static final String CHARGE_HEAVY = "base.charge_heavy_punch";
    public static final String CHARGE_HEAVY_FIRE = "base.charge_heavy_punch_fire";
    public static final String KI_CHARGE = "base.ki_charge";
    public static final String PUNCH_RIGHT = "combat.one_handed_punch_right";
    public static final String PUNCH_LEFT = "combat.one_handed_punch_left";
    public static final String KICK_LOW_R = "combat.lowkick_right";
    public static final String KICK_LOW_L = "combat.lowkick_left";
    public static final String KICK_GUT_R = "combat.gutkick_right";
    public static final String KICK_GUT_L = "combat.gutkick_left";
    public static final String DASH_FRONT = "base.dash_front";
    public static final String ATTACK1 = "base.attack1";
    public static final String ATTACK2 = "base.attack2";

    private DmzAnimHelper() {}

    public enum ChargeStyle {
        FIST_LIGHT,
        FIST_HEAVY,
        KICK,
        DRAGON
    }

    /** Server: start looping charge pose for nearby clients (DMZ KI_ANIMATION). */
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
            NetworkHandler.sendToTrackingEntityAndSelf(pkt, player);
        } catch (Throwable ignored) {
            // DMZ missing / API change — silent
        }
    }

    /** Server: stop charge pose. */
    public static void broadcastChargeStop(ServerPlayer player) {
        try {
            TriggerAnimationS2C pkt = new TriggerAnimationS2C(
                    player.getUUID(),
                    TriggerAnimationS2C.AnimationType.KI_ANIMATION_STOP,
                    0,
                    player.getId(),
                    "");
            NetworkHandler.sendToTrackingEntityAndSelf(pkt, player);
        } catch (Throwable ignored) {
        }
    }

    /** Server: play a one-shot melee / fire animation. */
    public static void broadcastMelee(ServerPlayer player, String animationName, boolean offhand, float speed) {
        try {
            MeleeAnimationS2C pkt = new MeleeAnimationS2C(player.getId(), animationName, offhand, speed);
            NetworkHandler.sendToTrackingEntityAndSelf(pkt, player);
        } catch (Throwable ignored) {
        }
    }

    /** Server: dash / evasion trigger. variant: 0 front, 1 back, 2 left, 3 right (DMZ convention). */
    public static void broadcastDash(ServerPlayer player, int variant) {
        try {
            TriggerAnimationS2C pkt = new TriggerAnimationS2C(
                    player.getUUID(),
                    TriggerAnimationS2C.AnimationType.DASH,
                    variant,
                    player.getId());
            NetworkHandler.sendToTrackingEntityAndSelf(pkt, player);
        } catch (Throwable ignored) {
        }
    }

    public static void playChargeRelease(ServerPlayer player, ChargeStyle style, boolean fullyCharged) {
        broadcastChargeStop(player);
        String fire = switch (style) {
            case FIST_LIGHT -> fullyCharged ? CHARGE_HEAVY_FIRE : CHARGE_LIGHT_FIRE;
            case FIST_HEAVY -> CHARGE_HEAVY_FIRE;
            case KICK -> fullyCharged ? KICK_GUT_R : KICK_LOW_R;
            case DRAGON -> CHARGE_HEAVY_FIRE;
        };
        float speed = fullyCharged ? 1.15f : 1.0f;
        broadcastMelee(player, fire, false, speed);
        if (style == ChargeStyle.DRAGON) {
            broadcastDash(player, 0);
        }
        // Follow-up punch/kick for clarity
        if (style == ChargeStyle.KICK) {
            broadcastMelee(player, fullyCharged ? KICK_GUT_R : KICK_LOW_R, false, speed);
        } else if (style == ChargeStyle.FIST_LIGHT || style == ChargeStyle.FIST_HEAVY) {
            broadcastMelee(player, fullyCharged ? PUNCH_RIGHT : ATTACK1, false, speed);
        }
    }

    /** Client-local prediction while charging (instant feedback). */
    @OnlyIn(Dist.CLIENT)
    public static void playLocalChargeStart(Player player, ChargeStyle style) {
        if (!FMLEnvironment.dist.isClient()) return;
        String anim = switch (style) {
            case FIST_LIGHT -> CHARGE_LIGHT;
            case FIST_HEAVY, KICK -> CHARGE_HEAVY;
            case DRAGON -> KI_CHARGE;
        };
        tryPlayKi(player, anim, true);
    }

    @OnlyIn(Dist.CLIENT)
    public static void playLocalChargeStop(Player player) {
        if (!FMLEnvironment.dist.isClient()) return;
        try {
            if (player instanceof IPlayerAnimatable anim) {
                anim.dragonminez$stopKiAnimation();
            }
        } catch (Throwable ignored) {
        }
    }

    @OnlyIn(Dist.CLIENT)
    public static void playLocalMelee(Player player, String animationName, boolean offhand, float speed) {
        if (!FMLEnvironment.dist.isClient()) return;
        try {
            if (player instanceof IPlayerAnimatable anim) {
                anim.dragonminez$playMeleeAnimation(animationName, offhand, speed);
            }
        } catch (Throwable ignored) {
        }
    }

    @OnlyIn(Dist.CLIENT)
    public static void playLocalChargeRelease(Player player, ChargeStyle style, boolean fullyCharged) {
        playLocalChargeStop(player);
        String fire = switch (style) {
            case FIST_LIGHT -> fullyCharged ? CHARGE_HEAVY_FIRE : CHARGE_LIGHT_FIRE;
            case FIST_HEAVY -> CHARGE_HEAVY_FIRE;
            case KICK -> fullyCharged ? KICK_GUT_R : KICK_LOW_R;
            case DRAGON -> CHARGE_HEAVY_FIRE;
        };
        float speed = fullyCharged ? 1.15f : 1.0f;
        playLocalMelee(player, fire, false, speed);
        if (style == ChargeStyle.DRAGON) {
            try {
                if (player instanceof IPlayerAnimatable anim) {
                    anim.dragonminez$triggerDash(0);
                }
            } catch (Throwable ignored) {
            }
        }
        if (style == ChargeStyle.KICK) {
            playLocalMelee(player, fullyCharged ? KICK_GUT_R : KICK_LOW_R, false, speed);
        } else if (style != ChargeStyle.DRAGON) {
            playLocalMelee(player, fullyCharged ? PUNCH_RIGHT : ATTACK1, false, speed);
        }
    }

    @OnlyIn(Dist.CLIENT)
    private static void tryPlayKi(Player player, String anim, boolean loop) {
        try {
            if (player instanceof IPlayerAnimatable a) {
                a.dragonminez$playKiAnimation(anim, loop);
            }
        } catch (Throwable ignored) {
        }
    }
}
