package net.bullettrain.xenopixelsmod.combat;

import net.bullettrain.xenopixelsmod.combat.anim.Bt3AnimationIntent;

import static net.bullettrain.xenopixelsmod.combat.anim.Bt3AnimationIntent.*;

/**
 * Which pose each beat of a rush string is.
 *
 * <p>Replaces the old {@code step % 2 == 0 ? kick : punch} alternation, which produced
 * punch/kick/punch/kick forever regardless of what the fighter had just done.
 *
 * <p><b>Pure and common.</b> Given the same arguments it always returns the same intent, which is
 * what lets the server pick the beat authoritatively while the attacking client predicts the
 * identical one locally with no round trip. Nothing here reads or writes state, and nothing here
 * moves a player: travel beats such as {@link Bt3AnimationIntent#STEP_IN_DASH} are poses, and the
 * fighter's actual position stays owned by {@code Bt3CombatPacket}.
 *
 * <p><b>Routes must alternate handedness, including across their own wrap.</b> A string longer
 * than its route repeats from the start, so a route whose last beat leads with the same limb as
 * its first throws that limb twice with no recovery between. That is a property of the authored
 * order rather than something fixed at runtime — mirroring a beat on the fly would silently play
 * something other than the choreography that was drawn — and
 * {@code Bt3ComboChoreographyTest#noSameSideStrikeTwiceInARow} enforces it for every route.
 */
public final class Bt3ComboChoreography {

    /** Ordinary held-R loop: our DMZ-faithful left and right punches, alternating forever. */
    private static final Bt3AnimationIntent[] MASH = {
            JAB_LEFT, JAB_RIGHT,
    };

    private static final Bt3AnimationIntent[][] GROUND_ROUTES = {MASH};

    private Bt3ComboChoreography() {
    }

    /** How many ground routes exist, for whoever picks one when a string starts. */
    public static int routeCount() {
        return GROUND_ROUTES.length;
    }

    /**
     * The pose for one beat of a string.
     *
     * @param routeIndex which rush route this string is using; any value is accepted and wrapped
     * @param step       1-based combo step, as owned by {@link Bt3CombatLimiter}
     * @param finisher   whether this step is the string's finisher beat
     * @param airborne   whether the fighter is off the ground
     */
    public static Bt3AnimationIntent resolve(int routeIndex, int step, boolean finisher, boolean airborne) {
        // {@code finisher} is accepted so callers stay unchanged; the pose is the authored beat.
        Bt3AnimationIntent[] route = GROUND_ROUTES[Math.floorMod(routeIndex, GROUND_ROUTES.length)];
        return route[Math.floorMod(Math.max(1, step) - 1, route.length)];
    }

    /** Held-direction override off: walk the authored route. */
    public static final int MASH_STYLE_ROUTE = 0;
    /** Hold the strafe-left key with the mash key: plain right/left crosses. */
    public static final int MASH_STYLE_PUNCH = 1;
    /** Hold the strafe-right key with the mash key: plain right/left uppercuts. */
    public static final int MASH_STYLE_UPPERCUT = 2;

    /**
     * The two-beat cycle a held direction key pins the string to, or {@code null} for
     * {@link #MASH_STYLE_ROUTE} and any value this build does not know.
     *
     * <p>A held direction is a deliberate request for one move repeated, so this deliberately does
     * not consult the route or finisher beat. It still costs ki per hit, and releasing the key
     * drops straight back into {@link #resolve} at the step the string has reached.
     *
     * <p>Right leads, matching how the route's own cross and uppercut runs start.
     *
     * @param style one of the {@code MASH_STYLE_*} constants
     * @param step  1-based combo step, as owned by {@link Bt3CombatLimiter}
     */
    public static Bt3AnimationIntent styled(int style, int step) {
        boolean rightLead = Math.floorMod(Math.max(1, step), 2) == 1;
        return switch (style) {
            case MASH_STYLE_PUNCH -> rightLead ? CROSS_RIGHT : CROSS_LEFT;
            case MASH_STYLE_UPPERCUT -> rightLead ? UPPERCUT_RIGHT : UPPERCUT_LEFT;
            default -> null;
        };
    }

    /** Applies the explicit W-tap launcher without rewriting ordinary held-R punches. */
    public static Bt3AnimationIntent overlay(Bt3AnimationIntent authored, int verticalBias) {
        if (authored == null) {
            authored = JAB_LEFT;
        }
        if (verticalBias > 0) {
            return FLYING_KICK;
        }
        return authored;
    }

    /** Resolve a named BT3-style beat after the server has selected the pose. */
    public static Bt3ComboBeat beat(Bt3AnimationIntent intent, int step, int mashStyle,
                                    int verticalBias) {
        return Bt3ComboBeat.resolve(intent, step, mashStyle, verticalBias);
    }

    /**
     * Steep diagonal off the floor: {@code [x, y, z]} with Y the launcher and XZ the away.
     * Away of zero length faces +Z so a test (and a stacked target) still gets a vector.
     */
    public static double[] launcherDelta(double awayX, double awayZ, double horiz, double up) {
        double len = Math.hypot(awayX, awayZ);
        double nx = len < 1.0e-8 ? 0.0 : awayX / len;
        double nz = len < 1.0e-8 ? 1.0 : awayZ / len;
        return new double[]{nx * horiz, up, nz * horiz};
    }
}
