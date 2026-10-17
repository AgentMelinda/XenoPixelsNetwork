package net.bullettrain.xenopixelsmod.space.physics;

import net.bullettrain.xenopixelsmod.space.math.KeplerSolver;
import net.bullettrain.xenopixelsmod.space.math.OrbitalElements;

/**
 * Station keeping for a managed physical planet: the acceleration that brings a body back onto its target
 * track (an orbit around its parent, or a pinned offset) and keeps it there.
 *
 * <p>{@code a = feedForward + clamp(k·(target − position) + c·(targetVelocity − velocity), ceiling)}. The
 * feed-forward term is the target's own centripetal acceleration, so a body already on its orbit needs no
 * correction. Only the corrective part is clamped: that ceiling is the most the planet resists, so ships
 * pushing harder than {@code ceiling × mass} move it, and when they let go the spring brings it home.
 *
 * <p>Vectors are Minecraft world axes. Kepler's XY reference plane maps to horizontal XZ, the same mapping
 * {@link PlanetShell#orbitSeed} uses.
 */
public final class PlanetOrbitHold {
    private PlanetOrbitHold() {}

    /**
     * Parent-relative orbital offset, velocity and centripetal acceleration at {@code timeSeconds}, in world
     * axes. Writes {@code position}, {@code velocity} and {@code acceleration} (each length 3).
     */
    public static void orbitTrack(OrbitalElements orbit, double mu, double timeSeconds,
                                  double[] position, double[] velocity, double[] acceleration) {
        KeplerSolver.positionAndVelocity(orbit, timeSeconds, position, velocity);
        swapYZ(position);
        swapYZ(velocity);
        double r2 = position[0] * position[0] + position[1] * position[1] + position[2] * position[2];
        double scale = r2 < 1e-9 || !(mu > 0) ? 0 : -mu / (r2 * Math.sqrt(r2));
        acceleration[0] = position[0] * scale;
        acceleration[1] = position[1] * scale;
        acceleration[2] = position[2] * scale;
    }

    /**
     * Writes the commanded acceleration into {@code out}: feed-forward plus the clamped spring-damper.
     *
     * @return the length of the corrective (clamped) part, for diagnostics
     */
    public static double acceleration(double[] position, double[] velocity,
                                      double[] targetPosition, double[] targetVelocity, double[] feedForward,
                                      double stiffness, double damping, double ceiling, double[] out) {
        double cx = stiffness * (targetPosition[0] - position[0]) + damping * (targetVelocity[0] - velocity[0]);
        double cy = stiffness * (targetPosition[1] - position[1]) + damping * (targetVelocity[1] - velocity[1]);
        double cz = stiffness * (targetPosition[2] - position[2]) + damping * (targetVelocity[2] - velocity[2]);
        double length = Math.sqrt(cx * cx + cy * cy + cz * cz);
        if (!Double.isFinite(length)) { cx = cy = cz = 0; length = 0; }
        else if (length > ceiling) {
            double scale = ceiling <= 0 ? 0 : ceiling / length;
            cx *= scale; cy *= scale; cz *= scale;
            length = Math.max(0, ceiling);
        }
        out[0] = feedForward[0] + cx;
        out[1] = feedForward[1] + cy;
        out[2] = feedForward[2] + cz;
        return length;
    }

    /**
     * The release-return law: fly toward the target at a speed proportional to the distance, capped at
     * {@code maxSpeed}, steering with {@code damping·(desired − relative velocity)} clamped to {@code ceiling}.
     * Unsaturated it equals {@link #acceleration}; far away it cruises instead of building up a speed it
     * cannot shed, so a planet dragged a long way comes home without overshooting.
     *
     * @return the length of the corrective (clamped) part, for diagnostics
     */
    public static double returnAcceleration(double[] position, double[] velocity,
                                            double[] targetPosition, double[] targetVelocity, double[] feedForward,
                                            double stiffness, double damping, double ceiling, double maxSpeed,
                                            double[] out) {
        if (!(damping > 0)) return acceleration(position, velocity, targetPosition, targetVelocity, feedForward,
                stiffness, damping, ceiling, out);
        double gain = stiffness / damping;
        double dx = gain * (targetPosition[0] - position[0]);
        double dy = gain * (targetPosition[1] - position[1]);
        double dz = gain * (targetPosition[2] - position[2]);
        double speed = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (speed > maxSpeed && speed > 0) { double s = maxSpeed / speed; dx *= s; dy *= s; dz *= s; }
        double cx = damping * (dx - (velocity[0] - targetVelocity[0]));
        double cy = damping * (dy - (velocity[1] - targetVelocity[1]));
        double cz = damping * (dz - (velocity[2] - targetVelocity[2]));
        double length = Math.sqrt(cx * cx + cy * cy + cz * cz);
        if (!Double.isFinite(length)) { cx = cy = cz = 0; length = 0; }
        else if (length > ceiling) {
            double scale = ceiling <= 0 ? 0 : ceiling / length;
            cx *= scale; cy *= scale; cz *= scale;
            length = Math.max(0, ceiling);
        }
        out[0] = feedForward[0] + cx;
        out[1] = feedForward[1] + cy;
        out[2] = feedForward[2] + cz;
        return length;
    }

    /**
     * Tells "something is pushing this planet" from "released". The outside acceleration is what the body
     * measurably did minus what the controller commanded; the command of either of the last two ticks is
     * accepted, since an impulse can land one tick late. Smoothed, and held for {@code releaseTicks} after
     * the last push, so a tug that pauses for a moment is not mistaken for a release.
     */
    public static final class ReleaseDetector {
        private final double[] smoothed = new double[3];
        private int quietTicks = Integer.MAX_VALUE / 2;

        /** Feeds one tick and returns true while the planet counts as held. */
        public boolean update(double[] measured, double[] command, double[] previousCommand,
                              double threshold, int releaseTicks, int elapsedTicks) {
            double currentError = distanceSquared(measured, command);
            double previousError = distanceSquared(measured, previousCommand);
            double[] pick = previousError < currentError ? previousCommand : command;
            for (int i = 0; i < 3; i++) {
                double outside = measured[i] - pick[i];
                if (Double.isFinite(outside)) smoothed[i] += 0.3 * (outside - smoothed[i]);
            }
            double commandLength = Math.sqrt(pick[0] * pick[0] + pick[1] * pick[1] + pick[2] * pick[2]);
            double limit = threshold + 0.1 * commandLength;
            if (outside() > limit) quietTicks = 0;
            else quietTicks = Math.min(Integer.MAX_VALUE / 2, quietTicks + Math.max(1, elapsedTicks));
            return held(releaseTicks);
        }
        private static double distanceSquared(double[] first, double[] second) {
            double x = first[0] - second[0], y = first[1] - second[1], z = first[2] - second[2];
            return x * x + y * y + z * z;
        }
        public boolean held(int releaseTicks) { return quietTicks < releaseTicks; }
        public double outside() {
            return Math.sqrt(smoothed[0] * smoothed[0] + smoothed[1] * smoothed[1] + smoothed[2] * smoothed[2]);
        }
        public void reset() { smoothed[0] = smoothed[1] = smoothed[2] = 0; quietTicks = Integer.MAX_VALUE / 2; }
    }

    private static void swapYZ(double[] v) {
        double y = v[1];
        v[1] = v[2];
        v[2] = y;
    }
}
