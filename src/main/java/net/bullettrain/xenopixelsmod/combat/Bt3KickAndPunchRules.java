package net.bullettrain.xenopixelsmod.combat;

import net.minecraft.world.phys.Vec3;

/** Pure input and knockback rules for punches and kicks (testable without a game). */
public final class Bt3KickAndPunchRules {
    private Bt3KickAndPunchRules() {
    }

    /**
     * Whether a punch-string beat launches its target. Tapping or holding W during a punch string
     * used to (the mash launcher and the dragon-dash pursue); launching belongs to W + the charged
     * kick now, and the old route is the {@code /xenobind mashlauncher} switch (off by default).
     */
    public static boolean mashLaunches(boolean wTapArmed, boolean wHeldPursue, boolean mashLauncherRoute) {
        return mashLauncherRoute && (wTapArmed || wHeldPursue);
    }

    /** A single click of the kick key kicks at the tap strength; holding charges beyond it. */
    public static float kickCharge(float charge, float tapCharge) {
        return Math.max(charge, Math.max(0.25f, Math.min(1.0f, tapCharge)));
    }

    /**
     * The charged punch's knockback velocity. {@code distance} scales how far it goes (1.0 = the
     * original shove). With {@code parabolic} the target is thrown in an arc whose launch speed
     * upward is {@code arcHeight} at full charge (half of it at the weakest), instead of the small
     * original hop.
     */
    public static Vec3 chargePunchKnockback(Vec3 awayFlat, float charge, float distance, boolean parabolic,
                                            float arcHeight) {
        double horiz = 0.85 * (0.6 + charge) * Math.max(0.0, distance);
        double up = parabolic
                ? Math.max(0.0, arcHeight) * (0.5 + 0.5 * charge)
                : 0.18 + charge * 0.15;
        return awayFlat.scale(horiz).add(0, up, 0);
    }
}
