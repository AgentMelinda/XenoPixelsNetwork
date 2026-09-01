package net.bullettrain.xenopixelsmod.aero.control;

import net.bullettrain.xenopixelsmod.aero.AeroConfig;
import org.joml.Vector3d;

/**
 * Pure, allocation-free aerodynamic model for Aero-controlled ships.
 *
 * <p>Existing Aero ships are fly-by-attitude thrust vectoring: the stabilizer holds heading and
 * the thrusters push along the nose. This model layers a <b>real lift/drag term</b> on top so a
 * ship behaves more like an aircraft than a spaceship — it needs speed to stay up, flaps trade
 * drag for low-speed lift, and it stalls past a critical angle of attack.
 *
 * <p>Everything is computed in <b>world space</b> and written into the caller's scratch vector,
 * so the flight tick (which runs every 50&nbsp;ms) adds zero allocations. The coefficients are
 * game-tuning values in {@link AeroConfig}, not physical constants; they are sized so that lift
 * roughly offsets gravity at a sensible cruise speed, which is what makes sustained flight feel
 * right rather than a physics simulation landing on the exact number.
 *
 * <p>The model is deliberately <i>not</i> a CFD solution. It is a bounded, monotonic, always-
 * finite acceleration so a bad coefficient can only make a ship fly oddly, never explode the
 * physics body — the output is clamped to {@link AeroConfig#maxAeroAccel} before it is used.
 */
public final class AeroAeroModel {

    private AeroAeroModel() {}

    /**
     * Compute the world-space aerodynamic acceleration (lift + drag) into {@code out}.
     *
     * @param vx,vy,vz ship linear velocity, world space (blocks/s)
     * @param nx,ny,nz ship nose direction, world space (need not be normalized)
     * @param flap     current flap extension, 0..1
     * @param out      receives the resulting acceleration (m/s²); always finite
     * @return the speed (blocks/s), handed back so the caller can report it without re-deriving
     */
    public static double compute(double vx, double vy, double vz,
                                 double nx, double ny, double nz,
                                 double flap, Vector3d out) {
        return compute(vx, vy, vz, nx, ny, nz, flap, 1.0, out);
    }

    /**
     * As {@link #compute(double, double, double, double, double, double, double, Vector3d)},
     * with control over how much of the hull's <i>base</i> lift this model still supplies.
     *
     * @param baseLiftFactor scales {@link AeroConfig#clBase} only. A craft built with wing
     *                       panels gets its base lift from those panels through Sable's own
     *                       per-block lift pass, so passing 0 here stops the same lift being
     *                       counted twice. Flap lift is deliberately <i>not</i> scaled: flaps
     *                       are a high-lift device on top of whatever wing exists, and a winged
     *                       craft should still gain lift when the pilot extends them.
     */
    public static double compute(double vx, double vy, double vz,
                                 double nx, double ny, double nz,
                                 double flap, double baseLiftFactor, Vector3d out) {
        double speed2 = vx * vx + vy * vy + vz * vz;
        if (speed2 < 1.0e-4) {
            // Below a crawl there is no aero; a stationary wing produces no lift or drag.
            out.set(0.0, 0.0, 0.0);
            return 0.0;
        }
        double speed = Math.sqrt(speed2);
        double invSpeed = 1.0 / speed;

        // Velocity unit vector.
        double vhx = vx * invSpeed, vhy = vy * invSpeed, vhz = vz * invSpeed;

        // Nose unit vector (defensive normalize — the caller's vector is calibrated, not unit).
        double nl = Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (nl < 1.0e-6) {
            out.set(0.0, 0.0, 0.0);
            return speed;
        }
        double nhx = nx / nl, nhy = ny / nl, nhz = nz / nl;

        // How far forward the nose points along the velocity (1 = perfectly aligned).
        double fwd = clamp(nhx * vhx + nhy * vhy + nhz * vhz, -1.0, 1.0);

        // The nose's component perpendicular to the velocity IS the lift direction; its length is
        // sin(AoA). When the nose is exactly along the velocity there is no lift axis at all.
        double lx = nhx - fwd * vhx;
        double ly = nhy - fwd * vhy;
        double lz = nhz - fwd * vhz;
        double ll = Math.sqrt(lx * lx + ly * ly + lz * lz);

        double sinAoa = Math.min(1.0, ll);
        double aoaDeg = Math.toDegrees(Math.asin(sinAoa));
        double absAoa = Math.abs(aoaDeg);

        // Past the stall angle lift collapses and drag spikes. A smooth roll-off keeps the
        // transition continuous so the ship noses over instead of being snapped to zero lift.
        double stall = 1.0 - smoothstep(AeroConfig.stallAoADeg,
                AeroConfig.stallAoADeg + AeroConfig.stallDropDeg, absAoa);

        // Dynamic pressure per unit mass: q = rho * (S/m) * v^2.
        double q = AeroConfig.airDensity * AeroConfig.wingAreaPerMass * speed2;

        // Drag opposes velocity always; it grows with speed^2, with AoA^2 (induced), and with flap.
        double cd = AeroConfig.cdBase
                + AeroConfig.cdAoa * sinAoa * sinAoa
                + AeroConfig.cdFlapPerExt * flap
                + (1.0 - stall) * AeroConfig.stallDragPenalty;
        double dragMag = q * cd;

        if (ll < 1.0e-4) {
            // Nose aligned with velocity: drag only, no lift axis.
            out.set(-vhx * dragMag, -vhy * dragMag, -vhz * dragMag);
            return speed;
        }

        // Lift is perpendicular to the velocity, toward the side the nose points up.
        double invLl = 1.0 / ll;
        lx *= invLl;
        ly *= invLl;
        lz *= invLl;

        // A little baseline lift even near zero AoA so a level ship can hold altitude without a
        // constant pitch-up; the remainder scales with |AoA| up to a soft knee, then stalls.
        double aoaFactor = AeroConfig.clZeroFloor
                + (1.0 - AeroConfig.clZeroFloor) * Math.min(1.0, absAoa / 15.0);
        double cl = (AeroConfig.clBase * Math.max(0.0, baseLiftFactor)
                + AeroConfig.clFlapPerExt * flap) * aoaFactor * stall;
        double liftMag = q * cl;

        out.set(lx * liftMag - vhx * dragMag,
                ly * liftMag - vhy * dragMag,
                lz * liftMag - vhz * dragMag);

        double len = out.length();
        if (len > AeroConfig.maxAeroAccel) {
            out.mul(AeroConfig.maxAeroAccel / len);
        }
        return speed;
    }

    /**
     * Angle between the nose and the velocity, in degrees; 0 when either is too small to have a
     * direction. Exposed so callers can report a stall without re-deriving the geometry — the
     * lift/drag term itself already accounts for it via {@link #compute}.
     */
    public static double angleOfAttackDeg(double vx, double vy, double vz,
                                          double nx, double ny, double nz) {
        double speed2 = vx * vx + vy * vy + vz * vz;
        double nose2 = nx * nx + ny * ny + nz * nz;
        if (speed2 < 1.0e-4 || nose2 < 1.0e-12) return 0.0;
        double dot = (vx * nx + vy * ny + vz * nz) / Math.sqrt(speed2 * nose2);
        return Math.toDegrees(Math.acos(clamp(dot, -1.0, 1.0)));
    }

    private static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    /** Hermite smoothstep, 0 below {@code e0}, 1 above {@code e1}. */
    private static double smoothstep(double e0, double e1, double x) {
        if (e1 == e0) return x < e0 ? 0.0 : 1.0;
        double t = clamp((x - e0) / (e1 - e0), 0.0, 1.0);
        return t * t * (3.0 - 2.0 * t);
    }
}
