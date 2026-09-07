package net.bullettrain.xenopixelsmod.client.combat;

import com.dragonminez.client.animation.IPlayerAnimatable;
import net.bullettrain.xenopixelsmod.client.config.XenoClientConfig;
import net.bullettrain.xenopixelsmod.combat.DmzAnimHelper;
import net.bullettrain.xenopixelsmod.combat.DmzAnimHelper.ChargeStyle;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * Client-only DMZ animation prediction + release VFX.
 */
public final class DmzAnimHelperClient {
    private DmzAnimHelperClient() {}

    /** Delayed kick/punch chain prediction. */
    public static final class ClientStrikeChain {
        private static int ticksLeft;
        private static String pendingAnim;
        private static float pendingSpeed;
        private static int step;

        public static void arm(String anim, float speed, int delayTicks) {
            if (!XenoClientConfig.bt3CombatAnims) return;
            pendingAnim = anim;
            pendingSpeed = speed;
            ticksLeft = Math.max(1, delayTicks);
            step++;
        }

        public static void tick(LocalPlayer player) {
            if (ticksLeft <= 0 || player == null) return;
            ticksLeft--;
            if (ticksLeft == 0 && pendingAnim != null) {
                playLocalMelee(player, pendingAnim, false, pendingSpeed);
                pendingAnim = null;
            }
        }

        public static void clear() {
            ticksLeft = 0;
            pendingAnim = null;
            step = 0;
        }

        /** True while a delayed follow-up kick/punch is queued but not yet played. */
        public static boolean isArmed() {
            return ticksLeft > 0 && pendingAnim != null;
        }
    }

    public static void playLocalChargeStart(Player player, ChargeStyle style) {
        if (!XenoClientConfig.bt3CombatAnims) return;
        String anim = switch (style) {
            case FIST_LIGHT -> DmzAnimHelper.CHARGE_LIGHT;
            case FIST_HEAVY, KICK -> DmzAnimHelper.CHARGE_HEAVY;
            case DRAGON -> DmzAnimHelper.KI_CHARGE;
        };
        tryPlayKi(player, anim, true);
    }

    /** Local DMZ block / guard hold pose ({@code base.block}). */
    public static void playLocalBlockStart(Player player) {
        if (!XenoClientConfig.bt3CombatAnims) return;
        tryPlayKi(player, DmzAnimHelper.BLOCK, true);
    }

    public static void playLocalBlockStop(Player player) {
        playLocalChargeStop(player);
    }

    public static void playLocalHakaiHold(Player player) {
        if (!XenoClientConfig.bt3CombatAnims || player == null) return;
        tryPlayKi(player, DmzAnimHelper.HAKAI_HOLD, true);
        playLocalMelee(player, DmzAnimHelper.HAKAI_HOLD, false, 1.0f);
    }

    public static void playLocalHakaiFire(Player player) {
        if (!XenoClientConfig.bt3CombatAnims || player == null) return;
        playLocalChargeStop(player);
        playLocalMelee(player, DmzAnimHelper.HAKAI_FIRE, false, 1.15f);
    }

    public static void playLocalHakaiStop(Player player) {
        playLocalChargeStop(player);
    }

    /** Punch-only combo flash (overrides mixed DMZ kick strings when enabled). */
    public static void playLocalComboPunch(Player player, int step, boolean finisher) {
        if (!XenoClientConfig.bt3CombatAnims || player == null) return;
        String anim;
        if (finisher) {
            anim = step % 2 == 0 ? DmzAnimHelper.UPPERCUT_LEFT : DmzAnimHelper.UPPERCUT_RIGHT;
        } else {
            anim = step % 2 == 0 ? DmzAnimHelper.PUNCH_LEFT : DmzAnimHelper.PUNCH_RIGHT;
        }
        playLocalMelee(player, anim, false, finisher ? 1.2f : 1.08f);
    }

    public static void playLocalComboKick(Player player, int step, boolean finisher) {
        if (!XenoClientConfig.bt3CombatAnims || player == null) return;
        String anim = step % 4 == 0 ? DmzAnimHelper.KICK_GUT_R : DmzAnimHelper.KICK_LOW_R;
        if (step % 4 == 2) {
            anim = DmzAnimHelper.KICK_GUT_L;
        }
        playLocalMelee(player, anim, false, finisher ? 1.2f : 1.1f);
    }

    public static void playLocalChargeStop(Player player) {
        if (!XenoClientConfig.bt3CombatAnims) return;
        try {
            if (player instanceof IPlayerAnimatable anim) {
                anim.dragonminez$stopKiAnimation();
            }
        } catch (Throwable ignored) {
        }
    }

    public static void playLocalMelee(Player player, String animationName, boolean offhand, float speed) {
        if (!XenoClientConfig.bt3CombatAnims) return;
        try {
            if (player instanceof IPlayerAnimatable anim) {
                anim.dragonminez$playMeleeAnimation(animationName, offhand, speed);
            }
        } catch (Throwable ignored) {
        }
    }

    public static void playLocalChargeRelease(Player player, ChargeStyle style, boolean fullyCharged) {
        playLocalChargeRelease(player, style, fullyCharged, 0);
    }

    public static void playLocalChargeRelease(Player player, ChargeStyle style, boolean fullyCharged, int verticalBias) {
        playLocalChargeStop(player);
        if (!XenoClientConfig.bt3CombatAnims) {
            return;
        }

        float speed = fullyCharged ? 1.2f : 1.05f;
        ClientStrikeChain.clear();

        if (style == ChargeStyle.KICK) {
            String primary = fullyCharged ? DmzAnimHelper.KICK_GUT_R : DmzAnimHelper.KICK_GUT_L;
            if (verticalBias < 0) primary = DmzAnimHelper.KICK_LOW_R;
            playLocalMelee(player, primary, false, speed);
            if (XenoClientConfig.bt3KickChainAnims) {
                String follow = verticalBias > 0 ? DmzAnimHelper.KICK_GUT_R
                        : (verticalBias < 0 ? DmzAnimHelper.KICK_LOW_L : DmzAnimHelper.KICK_LOW_R);
                ClientStrikeChain.arm(follow, speed * 1.05f, 4);
                if (fullyCharged) {
                    // third flash for full charge — queued after second by re-arm in tick is complex;
                    // second delayed only; server sends third
                }
            }
            return;
        }

        if (style == ChargeStyle.DRAGON) {
            playLocalMelee(player, DmzAnimHelper.CHARGE_HEAVY_FIRE, false, speed);
            try {
                if (player instanceof IPlayerAnimatable anim) {
                    anim.dragonminez$triggerDash(0);
                }
            } catch (Throwable ignored) {
            }
            if (XenoClientConfig.bt3KickChainAnims) {
                ClientStrikeChain.arm(DmzAnimHelper.PUNCH_RIGHT, 1.15f, 5);
            }
            return;
        }

        // Fist
        String fire = fullyCharged ? DmzAnimHelper.CHARGE_HEAVY_FIRE : DmzAnimHelper.CHARGE_LIGHT_FIRE;
        playLocalMelee(player, fire, false, speed);
        if (XenoClientConfig.bt3KickChainAnims) {
            ClientStrikeChain.arm(fullyCharged ? DmzAnimHelper.PUNCH_LEFT : DmzAnimHelper.ATTACK2, speed * 1.1f, 3);
        }
    }

    private static void tryPlayKi(Player player, String anim, boolean loop) {
        try {
            if (player instanceof IPlayerAnimatable a) {
                a.dragonminez$playKiAnimation(anim, loop);
            }
        } catch (Throwable ignored) {
        }
    }
}
