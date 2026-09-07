package net.bullettrain.xenopixelsmod.combat.targeting;

import net.minecraft.world.phys.Vec3;

/**
 * Predicts where a constant-velocity target will be when a projectile fired now would reach it —
 * the classic pursuit-curve intercept solve, closed-form rather than iterative.
 *
 * <p>This is display-only. {@link LockOnState} exposes the result for the client's lead marker;
 * nothing here decides whether an actual shot hits — that is, and must remain, the server's own
 * projectile/hit-scan validation at fire time. A lead marker that looked perfect but still missed
 * because the target juked is the whole point of showing a <i>prediction</i> rather than a
 * guarantee.
 */
public final class LeadCalculator {

    /**
     * @param aimPoint      world point to aim at; equals {@code targetPos} when no solution exists
     * @param interceptTime seconds until intercept; 0 when not solvable
     * @param solvable      false when the target cannot be caught (faster than the projectile
     *                      and moving away, or a degenerate solve) — the caller should fall back
     *                      to aiming straight at the target rather than trust a bogus value
     */
    public record LeadResult(Vec3 aimPoint, double interceptTime, boolean solvable) {
        public static LeadResult none(Vec3 targetPos) {
            return new LeadResult(targetPos, 0.0, false);
        }
    }

    private LeadCalculator() {
    }

    /**
     * Mode A: simple linear lead. Assumes the target holds its current velocity, which is cheap,
     * stable, and the correct default for a fast-moving dogfight where acceleration this instant
     * is a poor predictor of acceleration a second from now anyway.
     *
     * <p>Solves {@code |targetPos + targetVel*t - shooterPos| = projectileSpeed * t} for the
     * smallest non-negative {@code t}, i.e. the standard target-intercept quadratic.
     */
    public static LeadResult linear(Vec3 shooterPos, Vec3 targetPos, Vec3 targetVel, double projectileSpeed) {
        if (projectileSpeed <= 0.0) return LeadResult.none(targetPos);
        Vec3 rel = targetPos.subtract(shooterPos);

        double a = targetVel.lengthSqr() - projectileSpeed * projectileSpeed;
        double b = 2.0 * rel.dot(targetVel);
        double c = rel.lengthSqr();

        double t;
        if (Math.abs(a) < 1.0e-6) {
            // Degenerate: target speed ~= projectile speed, the quadratic term vanishes.
            if (Math.abs(b) < 1.0e-9) return LeadResult.none(targetPos);
            t = -c / b;
        } else {
            double discriminant = b * b - 4.0 * a * c;
            if (discriminant < 0.0) return LeadResult.none(targetPos);
            double sqrtDisc = Math.sqrt(discriminant);
            double t1 = (-b + sqrtDisc) / (2.0 * a);
            double t2 = (-b - sqrtDisc) / (2.0 * a);
            t = smallestNonNegative(t1, t2);
        }
        if (!Double.isFinite(t) || t < 0.0) return LeadResult.none(targetPos);

        Vec3 aim = targetPos.add(targetVel.scale(t));
        return new LeadResult(aim, t, true);
    }

    /**
     * Mode B: linear lead with a coarse ballistic-drop correction on top, for a lobbed rather
     * than hitscan-fast projectile. Solves the flat-trajectory intercept from {@link #linear}
     * first, then raises the aim point by however far a projectile would fall over that flight
     * time — an approximation (it does not re-solve the intercept for the now-longer curved
     * path), which is the deliberate, cheap trade over an iterative ballistic solver.
     *
     * <p>The correction goes <b>up</b>. This record is the point to <i>aim at</i>, not the point
     * the shot passes through: a projectile that falls {@code d} over its flight has to be
     * launched {@code d} above the intercept to arrive at it. Sagging the marker downward — as
     * this did — moved the reticle the wrong way, so following it would have put every lobbed
     * shot twice the drop below the target.
     */
    public static LeadResult withGravityDrop(Vec3 shooterPos, Vec3 targetPos, Vec3 targetVel,
                                              double projectileSpeed, double gravityBlocksPerSecSqr) {
        return withGravityDrop(shooterPos, targetPos, targetVel, Vec3.ZERO,
                projectileSpeed, gravityBlocksPerSecSqr);
    }

    /**
     * Mode C: ballistic lead while inheriting a fraction of the shooter's velocity.
     *
     * <p>The intercept solve uses relative target velocity because the projectile begins with the
     * shooter's motion, but the returned aim point remains in world space and therefore advances
     * with the target's actual velocity. This keeps the marker useful for a moving aircraft rather
     * than accidentally subtracting the pilot's motion from the point they should shoot at.
     */
    public static LeadResult withGravityDrop(Vec3 shooterPos, Vec3 targetPos, Vec3 targetVel,
                                              Vec3 shooterVel, double projectileSpeed,
                                              double gravityBlocksPerSecSqr) {
        Vec3 relativeTargetVel = targetVel.subtract(shooterVel);
        LeadResult relative = linear(shooterPos, targetPos, relativeTargetVel, projectileSpeed);
        if (!relative.solvable()) return LeadResult.none(targetPos);

        Vec3 aimPoint = targetPos.add(targetVel.scale(relative.interceptTime()));
        if (gravityBlocksPerSecSqr > 0.0) {
            double drop = 0.5 * gravityBlocksPerSecSqr * relative.interceptTime()
                    * relative.interceptTime();
            aimPoint = aimPoint.add(0.0, drop, 0.0);
        }
        return new LeadResult(aimPoint, relative.interceptTime(), true);
    }

    private static double smallestNonNegative(double t1, double t2) {
        boolean ok1 = t1 >= 0.0;
        boolean ok2 = t2 >= 0.0;
        if (ok1 && ok2) return Math.min(t1, t2);
        if (ok1) return t1;
        if (ok2) return t2;
        return -1.0;
    }
}
