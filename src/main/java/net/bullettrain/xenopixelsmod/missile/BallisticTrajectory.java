package net.bullettrain.xenopixelsmod.missile;

import net.minecraft.world.phys.Vec3;

/**
 * Ballistic loft planner: pitch, apex world XYZ, ground-track fractions for
 * climb → glide → terminal. Used by entity missiles and VS ship ballistic.
 */
public final class BallisticTrajectory {
    /** Earth gravity converted to blocks/tick² (1 block = 1 metre). */
    public static final double GRAVITY = BallisticCalculator.EARTH_GRAVITY / 400.0;

    private BallisticTrajectory() {}

    /**
     * High-arc launch pitch (radians) to hit {@code target} from {@code launch}
     * with boost-exit speed {@code v0}.
     */
    public static double solvePitchRad(Vec3 launch, Vec3 target, double v0) {
        double dx = target.x - launch.x;
        double dz = target.z - launch.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        double dy = target.y - launch.y;
        if (horizontal < 0.5) {
            return dy >= 0 ? Math.PI / 2.0 : -Math.PI / 2.0;
        }
        double g = GRAVITY;
        double v2 = v0 * v0;
        double disc = v2 * v2 - g * (g * horizontal * horizontal + 2.0 * dy * v2);
        if (disc < 0) {
            // Unreachable at this speed — steep loft so boost still climbs
            return Math.atan2(Math.max(1.0, v2 / (g * horizontal)), 1.0);
        }
        double root = Math.sqrt(disc);
        // High arc (VLS / ship ballistic)
        double tanTheta = (v2 + root) / (g * horizontal);
        return Math.atan(tanTheta);
    }

    public static Vec3 launchDirection(Vec3 launch, Vec3 target, double pitchRad) {
        double dx = target.x - launch.x;
        double dz = target.z - launch.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        if (horizontal < 1.0e-4) {
            return new Vec3(0, Math.sin(pitchRad) >= 0 ? 1 : -1, 0);
        }
        double cosP = Math.cos(pitchRad);
        double sinP = Math.sin(pitchRad);
        return new Vec3(
                (dx / horizontal) * cosP,
                sinP,
                (dz / horizontal) * cosP
        ).normalize();
    }

    public static double boostExitSpeed(double accelPerTick, int boostTicks) {
        // Match ShipBallisticController: configured acceleration * 55 m/s²,
        // integrated for ticks/20 seconds, converted back to blocks/tick.
        return Math.max(0.05, accelPerTick * 55.0 * Math.max(1, boostTicks) / 400.0);
    }

    /** Apex height gain above launch: h = (v sin θ)² / (2g). */
    public static double apexHeightGain(double v0, double pitchRad) {
        double vy = v0 * Math.sin(pitchRad);
        if (vy <= 0) return 0;
        return (vy * vy) / (2.0 * GRAVITY);
    }

    /** Horizontal distance from launch to apex along ground track. */
    public static double horizontalToApex(double v0, double pitchRad) {
        double tApex = (v0 * Math.sin(pitchRad)) / GRAVITY;
        if (tApex < 0) tApex = 0;
        return v0 * Math.cos(pitchRad) * tApex;
    }

    public static int estimateEtaTicks(Vec3 launch, Vec3 target, double avgSpeed) {
        double dist = launch.distanceTo(target);
        if (avgSpeed <= 1.0e-3) return Integer.MAX_VALUE;
        return (int) Math.ceil(dist / avgSpeed);
    }

    public record Solution(double pitchRad, Vec3 direction, double exitSpeed, int etaTicks) {}

    /**
     * Complete loft plan with apex waypoint + phase fractions.
     *
     * @param apexX apex world X (where climb peaks over ground track)
     * @param apexY apex world Y (big altitude to reach before glide)
     * @param apexZ apex world Z
     * @param peakGroundFrac ground-track fraction [0,1] at apex (start of glide)
     * @param terminalGroundFrac ground-track fraction to open terminal dive
     * @param terminalAcquireRange horizontal range (blocks) for terminal pure-pursuit
     */
    public record FlightPlan(
            double pitchRad,
            Vec3 loftDir,
            double exitSpeed,
            double apexX,
            double apexY,
            double apexZ,
            double peakGroundFrac,
            double terminalGroundFrac,
            double rangeHorizontal,
            int etaTicks,
            double terminalAcquireRange,
            BallisticCalculator.Result calculation
    ) {
        /** Back-compat: apex altitude only. */
        public double apexYOnly() {
            return apexY;
        }
    }

    public static Solution solve(Vec3 launch, Vec3 target, double boostAccel, int boostTicks) {
        double v0 = boostExitSpeed(boostAccel, boostTicks);
        double pitch = solvePitchRad(launch, target, v0);
        Vec3 dir = launchDirection(launch, target, pitch);
        int eta = estimateEtaTicks(launch, target, v0 * 0.55);
        return new Solution(pitch, dir, v0, eta);
    }

    /**
     * Point-mass loft simulation result (planning / ETA). Real ship flight is still
     * closed-loop in {@code ShipBallisticController}; sim is for loft geometry + time.
     */
    public record SimResult(
            double apexX, double apexY, double apexZ,
            double peakGroundFrac,
            double terminalGroundFrac,
            int etaTicks,
            boolean reachedApex,
            boolean nearTarget,
            double missHorizontal
    ) {}

    public static FlightPlan plan(Vec3 launch, Vec3 target, double boostAccel, int boostTicks) {
        return plan(launch, target, boostAccel, boostTicks, 0,
                BallisticCalculator.EARTH_GRAVITY, BallisticCalculator.DEFAULT_DRAG);
    }

    /**
     * @param desiredApexY world Y to reach before glide/terminal; {@code 0} = auto from physics/range
     */
    public static FlightPlan plan(Vec3 launch, Vec3 target, double boostAccel, int boostTicks,
                                  double desiredApexY) {
        return plan(launch, target, boostAccel, boostTicks, desiredApexY,
                BallisticCalculator.EARTH_GRAVITY, BallisticCalculator.DEFAULT_DRAG);
    }

    public static FlightPlan plan(Vec3 launch, Vec3 target, double boostAccel, int boostTicks,
                                  double desiredApexY, double gravitySi, double dragPerBlock) {
        double v0Available = boostExitSpeed(boostAccel, boostTicks);
        BallisticCalculator.Result calculation = BallisticCalculator.calculate(
                launch, target, v0Available, desiredApexY, gravitySi, dragPerBlock);
        double calculatedPitch = Math.toRadians(calculation.selectedAngleDeg());
        Solution sol = new Solution(calculatedPitch,
                launchDirection(launch, target, calculatedPitch), v0Available,
                calculation.flightTimeTicks() >= Integer.MAX_VALUE
                        ? Integer.MAX_VALUE : (int) Math.ceil(calculation.flightTimeTicks()));
        double dx = target.x - launch.x;
        double dz = target.z - launch.z;
        double horiz = Math.sqrt(dx * dx + dz * dz);
        if (horiz < 1.0) {
            double ay = desiredApexY > 0
                    ? Math.max(desiredApexY, Math.max(launch.y, target.y) + 8.0)
                    : Math.max(launch.y, target.y) + 24.0;
            return new FlightPlan(
                    sol.pitchRad(), sol.direction(), sol.exitSpeed(),
                    target.x, ay, target.z,
                    0.5, 0.75, 1.0, sol.etaTicks(), 32.0, calculation);
        }

        double v0 = sol.exitSpeed();
        double pitch = sol.pitchRad();

        /*
         * Analytic loft height (range-scaled). Old +240 cap made 120–400 km shots flat.
         * height ≈ 50 + 8√R + 0.012·R  (soft-capped for VS sky)
         */
        double hGain = Math.max(0.0, calculation.apexY() - launch.y);
        double heightAbove = 50.0 + Math.sqrt(horiz) * 8.0 + horiz * 0.012;
        heightAbove = Math.max(48.0, heightAbove);
        heightAbove = Math.max(heightAbove, hGain);
        double apexY = Math.max(launch.y + heightAbove, target.y + 64.0);

        if (desiredApexY > 0) {
            apexY = Math.max(desiredApexY, launch.y + 16.0);
        }

        double height = Math.max(16.0, apexY - launch.y);

        // Apex XZ: keep climb steep (~32°); long range → peak early on track
        double climbAngle = Math.toRadians(32.0);
        double peakF = height / (horiz * Math.tan(climbAngle));
        if (!Double.isFinite(peakF) || peakF <= 0) peakF = 0.25;
        double peakMax = horiz < 500 ? 0.48 : (horiz < 5_000 ? 0.35 : (horiz < 50_000 ? 0.22 : 0.14));
        peakF = Math.max(0.05, Math.min(peakMax, peakF));

        double apexX = launch.x + dx * peakF;
        double apexZ = launch.z + dz * peakF;

        double terminalF = peakF + (1.0 - peakF) * 0.55;
        terminalF = Math.max(peakF + 0.12, Math.min(0.92, terminalF));

        double remainingAfterApex = horiz * (1.0 - peakF);
        double terminalR = Math.max(64.0, Math.min(2_000.0, remainingAfterApex * 0.25 + 48.0));

        Vec3 apexPt = new Vec3(apexX, apexY, apexZ);
        Vec3 loftToApex = apexPt.subtract(launch);
        if (loftToApex.lengthSqr() < 1.0e-6) {
            loftToApex = new Vec3(0, 1, 0);
        } else {
            loftToApex = loftToApex.normalize();
            if (loftToApex.y < 0.45) {
                double hx = loftToApex.x;
                double hz = loftToApex.z;
                double hLen = Math.sqrt(hx * hx + hz * hz);
                double minY = 0.45;
                double hScale = hLen > 1e-6 ? Math.sqrt(1.0 - minY * minY) / hLen : 0;
                loftToApex = new Vec3(hx * hScale, minY, hz * hScale).normalize();
            }
        }

        // Discrete simulation refines apex location + ETA (critical for 100–400+ km)
        int climbBudget = (int) Math.min(2_400, 40 + height * 0.35 + Math.sqrt(horiz) * 0.8);
        int fuel = Math.max(boostTicks, climbBudget);
        SimResult sim = simulate(launch, target, loftToApex, boostAccel, fuel,
                apexY, peakF, terminalF, terminalR, horiz,
                BallisticCalculator.toTickGravity(gravitySi),
                BallisticCalculator.sanitizeDrag(dragPerBlock));

        if (sim.reachedApex()) {
            // Prefer simulated peak (where vy flips / loft Y hit) for waypoint + fractions
            apexX = sim.apexX();
            apexY = Math.max(apexY, sim.apexY()); // never lower than planned loft ceiling
            apexZ = sim.apexZ();
            peakF = Math.max(0.04, Math.min(0.55, sim.peakGroundFrac()));
            terminalF = Math.max(peakF + 0.1, Math.min(0.95, sim.terminalGroundFrac()));
            // Rebuild loft dir to simulated apex
            apexPt = new Vec3(apexX, apexY, apexZ);
            loftToApex = apexPt.subtract(launch);
            if (loftToApex.lengthSqr() > 1e-6) {
                loftToApex = loftToApex.normalize();
                if (loftToApex.y < 0.45) {
                    double hx = loftToApex.x, hz = loftToApex.z;
                    double hLen = Math.sqrt(hx * hx + hz * hz);
                    double minY = 0.45;
                    double hScale = hLen > 1e-6 ? Math.sqrt(1.0 - minY * minY) / hLen : 0;
                    loftToApex = new Vec3(hx * hScale, minY, hz * hScale).normalize();
                }
            }
        }

        int eta = sim.etaTicks() > 0
                ? sim.etaTicks()
                : estimateEtaTicks(launch, target, Math.max(2.0, v0 * 0.35));

        return new FlightPlan(
                pitch,
                loftToApex,
                v0,
                apexX, apexY, apexZ,
                peakF,
                terminalF,
                horiz,
                eta,
                terminalR,
                calculation
        );
    }

    /**
     * Fast variable-step point-mass sim: boost (climb) → glide → terminal.
     * Used only for planning; real ships use closed-loop force guidance.
     */
    public static SimResult simulate(Vec3 launch, Vec3 target, Vec3 loftDir,
                                     double boostAccel, int boostTicks,
                                     double plannedApexY, double plannedPeakF,
                                     double plannedTerminalF, double terminalR,
                                     double horizRange) {
        return simulate(launch, target, loftDir, boostAccel, boostTicks,
                plannedApexY, plannedPeakF, plannedTerminalF, terminalR, horizRange,
                GRAVITY, BallisticCalculator.DEFAULT_DRAG);
    }

    public static SimResult simulate(Vec3 launch, Vec3 target, Vec3 loftDir,
                                     double boostAccel, int boostTicks,
                                     double plannedApexY, double plannedPeakF,
                                     double plannedTerminalF, double terminalR,
                                     double horizRange, double gravityPerTick,
                                     double dragPerBlock) {
        double dx = target.x - launch.x;
        double dz = target.z - launch.z;
        double range = Math.max(1.0, horizRange > 0 ? horizRange : Math.sqrt(dx * dx + dz * dz));

        // Variable dt: long range uses larger steps (still accurate enough for loft/ETA)
        double dt = Math.max(1.0, Math.min(50.0, range / 10_000.0));
        int maxSteps = Math.min(20_000, (int) (20 * 60 * 40 / dt)); // ~40 min wall

        double x = launch.x, y = launch.y, z = launch.z;
        double vx = 0, vy = 0, vz = 0;
        double g = Math.max(0.000025, Math.min(0.25, gravityPerTick));
        double drag = BallisticCalculator.sanitizeDrag(dragPerBlock);

        // Climb accel (game units / tick²) — scaled a bit for long burns
        double aBoost = Math.max(0.002, boostAccel * 55.0 / 400.0 * 0.85);

        double apexX = launch.x, apexY = launch.y, apexZ = launch.z;
        double peakF = plannedPeakF;
        boolean reachedApex = false;
        int fuel = Math.max(1, boostTicks);
        double time = 0;
        int phase = 0; // 0 boost, 1 glide, 2 terminal

        double ux = loftDir.x, uy = loftDir.y, uz = loftDir.z;
        double ul = Math.sqrt(ux * ux + uy * uy + uz * uz);
        if (ul < 1e-6) {
            ux = 0;
            uy = 1;
            uz = 0;
        } else {
            ux /= ul;
            uy /= ul;
            uz /= ul;
        }

        for (int step = 0; step < maxSteps; step++) {
            double gFrac = groundFrac(launch.x, launch.z, target.x, target.z, x, z);
            double hx = target.x - x;
            double hz = target.z - z;
            double hErr = Math.sqrt(hx * hx + hz * hz);
            double dist = Math.sqrt(hx * hx + (target.y - y) * (target.y - y) + hz * hz);

            if (dist < 8.0) {
                return new SimResult(apexX, apexY, apexZ, peakF,
                        Math.max(peakF + 0.1, plannedTerminalF),
                        (int) Math.ceil(time), reachedApex, true, hErr);
            }

            // Phase machine (mirrors ship controller, simplified)
            if (phase == 0) {
                boolean heightOk = y >= plannedApexY - 4.0;
                boolean fuelOut = fuel <= 0;
                if (heightOk || (fuelOut && y > launch.y + (plannedApexY - launch.y) * 0.75)) {
                    reachedApex = true;
                    apexX = x;
                    apexY = Math.max(y, plannedApexY * 0.9);
                    apexZ = z;
                    peakF = Math.max(0.04, Math.min(0.6, gFrac));
                    phase = 1;
                }
            } else if (phase == 1) {
                if (reachedApex && (gFrac >= plannedTerminalF || hErr < terminalR)) {
                    phase = 2;
                }
            }

            // Acceleration
            double ax = 0, ay = -g, az = 0;
            if (phase == 0 && fuel > 0) {
                // Steep climb toward planned loft
                double aimX = ux, aimY = uy, aimZ = uz;
                if (y < plannedApexY - 16) {
                    // Bias more vertical while climbing
                    aimY = Math.max(aimY, 0.55);
                    double hl = Math.sqrt(aimX * aimX + aimZ * aimZ);
                    if (hl > 1e-6) {
                        double s = Math.sqrt(1.0 - aimY * aimY) / hl;
                        aimX *= s;
                        aimZ *= s;
                    }
                }
                double al = Math.sqrt(aimX * aimX + aimY * aimY + aimZ * aimZ);
                aimX /= al;
                aimY /= al;
                aimZ /= al;
                ax += aimX * aBoost;
                ay += aimY * aBoost;
                az += aimZ * aBoost;
                fuel -= (int) Math.max(1, dt);
            } else if (phase == 1) {
                // Glide: mild accel toward corridor lead point
                double leadF = Math.min(1.0, gFrac + 0.08);
                Vec3 lead = corridorPoint(launch.x, launch.y, launch.z,
                        target.x, target.y, target.z, plannedApexY, peakF, leadF);
                double lx = lead.x - x, ly = lead.y - y, lz = lead.z - z;
                double ll = Math.sqrt(lx * lx + ly * ly + lz * lz);
                if (ll > 1e-3) {
                    double aG = aBoost * 0.35;
                    ax += lx / ll * aG;
                    ay += ly / ll * aG;
                    az += lz / ll * aG;
                }
            } else {
                // Terminal pure pursuit + light vel cancel
                double lx = hx, ly = target.y - y, lz = hz;
                if (hErr > 40 && y > target.y + 30) {
                    ly = (target.y + hErr * 0.1) - y;
                }
                double ll = Math.sqrt(lx * lx + ly * ly + lz * lz);
                if (ll > 1e-3) {
                    double aT = aBoost * 0.7;
                    ax += lx / ll * aT - vx * 0.02;
                    ay += ly / ll * aT - vy * 0.01;
                    az += lz / ll * aT - vz * 0.02;
                }
            }

            double speedBefore = Math.sqrt(vx * vx + vy * vy + vz * vz);
            if (speedBefore > 1.0e-9 && drag > 0.0) {
                double dragAccel = drag * BallisticCalculator.airDensityFactor(y)
                        * speedBefore * speedBefore;
                ax -= dragAccel * vx / speedBefore;
                ay -= dragAccel * vy / speedBefore;
                az -= dragAccel * vz / speedBefore;
            }
            vx += ax * dt;
            vy += ay * dt;
            vz += az * dt;
            // Soft speed cap for stability
            double spd = Math.sqrt(vx * vx + vy * vy + vz * vz);
            double maxSpd = Math.max(8.0, boostExitSpeed(boostAccel, boostTicks) * 2.5);
            if (spd > maxSpd) {
                double s = maxSpd / spd;
                vx *= s;
                vy *= s;
                vz *= s;
            }
            x += vx * dt;
            y += vy * dt;
            z += vz * dt;
            time += dt;

            // Track highest point as apex if we overshoot planned
            if (!reachedApex && y > apexY) {
                apexY = y;
                apexX = x;
                apexZ = z;
            }
        }

        double miss = Math.sqrt(
                (target.x - x) * (target.x - x) + (target.z - z) * (target.z - z));
        if (!reachedApex) {
            peakF = plannedPeakF;
            apexY = Math.max(apexY, plannedApexY);
        }
        return new SimResult(apexX, apexY, apexZ, peakF, plannedTerminalF,
                (int) Math.ceil(time), reachedApex, miss < 64.0, miss);
    }

    private static double groundFrac(double lx, double lz, double tx, double tz, double x, double z) {
        double dx = tx - lx;
        double dz = tz - lz;
        double r2 = dx * dx + dz * dz;
        if (r2 < 1.0) return 1.0;
        double along = ((x - lx) * dx + (z - lz) * dz) / r2;
        return Math.max(0.0, Math.min(1.5, along));
    }

    /**
     * Corridor altitude at ground-track fraction f (0=launch, 1=target).
     * Peaks at {@code peakF} with height {@code apexY}, then glides to target Y.
     */
    public static double corridorAltitude(double launchY, double targetY, double apexY,
                                         double peakF, double groundFrac) {
        double f = Math.max(0.0, Math.min(1.2, groundFrac));
        double pf = Math.max(0.1, Math.min(0.7, peakF));
        if (f <= pf) {
            double t = f / pf;
            double ease = 1.0 - (1.0 - t) * (1.0 - t); // ease-out climb
            return launchY + (apexY - launchY) * ease;
        }
        double t = Math.min(1.0, (f - pf) / Math.max(1.0e-3, 1.0 - pf));
        double ease = t * t; // ease-in descent
        return apexY + (targetY - apexY) * ease;
    }

    /**
     * Point on ground track at fraction f (XZ only), Y = corridor altitude.
     */
    public static Vec3 corridorPoint(double launchX, double launchY, double launchZ,
                                    double targetX, double targetY, double targetZ,
                                    double apexY, double peakF, double groundFrac) {
        double f = Math.max(0.0, Math.min(1.0, groundFrac));
        double x = launchX + (targetX - launchX) * f;
        double z = launchZ + (targetZ - launchZ) * f;
        double y = corridorAltitude(launchY, targetY, apexY, peakF, f);
        return new Vec3(x, y, z);
    }

    /** Velocity / attitude hint along the loft arc. */
    public static Vec3 arcVelocityHint(Vec3 launch, Vec3 target, double apexY, double groundFrac) {
        double peakF = 0.35;
        double f = Math.max(0.0, Math.min(1.0, groundFrac));
        double dx = target.x - launch.x;
        double dz = target.z - launch.z;
        double horiz = Math.sqrt(dx * dx + dz * dz);
        double vx = horiz < 1.0e-3 ? 0 : dx / horiz;
        double vz = horiz < 1.0e-3 ? 0 : dz / horiz;
        double dySign = (peakF - f); // up before peak, down after
        double yPeak = apexY - launch.y;
        double vy = dySign * Math.max(0.4, yPeak / 40.0);
        Vec3 v = new Vec3(vx, vy, vz);
        double len = v.length();
        return len < 1.0e-6 ? new Vec3(0, 1, 0) : v.scale(1.0 / len);
    }
}
