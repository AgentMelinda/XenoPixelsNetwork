package net.bullettrain.xenopixelsmod.client.combat;

import com.dragonminez.client.animation.IPlayerAnimatable;
import net.bullettrain.xenopixelsmod.client.config.XenoClientConfig;
import net.bullettrain.xenopixelsmod.combat.DmzAnimHelper;
import net.bullettrain.xenopixelsmod.combat.DmzAnimHelper.ChargeStyle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleTypes;
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
            spawnReleaseBurst(player, style, fullyCharged);
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
            spawnReleaseBurst(player, style, fullyCharged);
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
            spawnReleaseBurst(player, style, fullyCharged);
            return;
        }

        // Fist
        String fire = fullyCharged ? DmzAnimHelper.CHARGE_HEAVY_FIRE : DmzAnimHelper.CHARGE_LIGHT_FIRE;
        playLocalMelee(player, fire, false, speed);
        if (XenoClientConfig.bt3KickChainAnims) {
            ClientStrikeChain.arm(fullyCharged ? DmzAnimHelper.PUNCH_LEFT : DmzAnimHelper.ATTACK2, speed * 1.1f, 3);
        }
        spawnReleaseBurst(player, style, fullyCharged);
    }

    /** Soft impact / charge-release particles at the player. */
    public static void spawnReleaseBurst(Player player, ChargeStyle style, boolean full) {
        if (!XenoClientConfig.bt3CombatParticles) return;
        if (player.level() == null) return;
        var rand = player.getRandom();
        float h = player.getBbHeight();
        int count = full ? 10 : 5;
        for (int i = 0; i < count; i++) {
            double x = player.getX() + (rand.nextDouble() - 0.5) * 0.8;
            double y = player.getY() + rand.nextDouble() * h * 0.9;
            double z = player.getZ() + (rand.nextDouble() - 0.5) * 0.8;
            if (style == ChargeStyle.KICK) {
                player.level().addParticle(ParticleTypes.CRIT, x, y, z, 0, 0.02, 0);
                if (full) {
                    player.level().addParticle(ParticleTypes.ELECTRIC_SPARK, x, y, z, 0, 0.01, 0);
                }
            } else if (style == ChargeStyle.DRAGON) {
                player.level().addParticle(ParticleTypes.FLAME, x, y, z, 0, 0.02, 0);
            } else {
                player.level().addParticle(ParticleTypes.CRIT, x, y, z, 0, 0.015, 0);
                if (full) {
                    player.level().addParticle(ParticleTypes.SWEEP_ATTACK, x, y + 0.2, z, 0, 0, 0);
                }
            }
        }
    }

    /** Hit spark at a world position (target impact). */
    public static void spawnHitSpark(double x, double y, double z, boolean heavy) {
        if (!XenoClientConfig.bt3CombatParticles) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        int n = heavy ? 8 : 4;
        for (int i = 0; i < n; i++) {
            mc.level.addParticle(ParticleTypes.CRIT,
                    x + (mc.level.random.nextDouble() - 0.5) * 0.5,
                    y + mc.level.random.nextDouble() * 0.4,
                    z + (mc.level.random.nextDouble() - 0.5) * 0.5,
                    0, 0.02, 0);
        }
        if (heavy) {
            mc.level.addParticle(ParticleTypes.SWEEP_ATTACK, x, y + 0.5, z, 0, 0, 0);
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
