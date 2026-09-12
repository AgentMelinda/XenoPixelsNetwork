package net.bullettrain.xenopixelsmod.missile;

import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Server-authoritative, chunk-neutral planner used for previews and launches. */
public final class BallisticPlanOptimizer {
    private static final int GRAPH_SAMPLES = 128;

    /** Returns a loaded surface Y, or NaN when that chunk is not presently loaded. */
    @FunctionalInterface
    public interface TerrainProbe { double surfaceY(int blockX, int blockZ); }

    private BallisticPlanOptimizer() {
    }

    public static BallisticFlightPlan.Result optimize(
            int revision, Vec3 launch, Vec3 targetPosition, Vec3 targetVelocityMps,
            long targetShipId, BallisticFlightPlan.Settings settings,
            double boostAccel, int boostTicks, double gravitySi, double dragCoefficient) {
        return optimize(revision, launch, targetPosition, targetVelocityMps, targetShipId, settings,
                boostAccel, boostTicks, gravitySi, dragCoefficient, null);
    }

    public static BallisticFlightPlan.Result optimize(
            int revision, Vec3 launch, Vec3 targetPosition, Vec3 targetVelocityMps,
            long targetShipId, BallisticFlightPlan.Settings settings,
            double boostAccel, int boostTicks, double gravitySi, double dragCoefficient,
            TerrainProbe terrainProbe) {
        return optimize(revision, launch, targetPosition, targetVelocityMps, targetShipId, settings,
                boostAccel, boostTicks, gravitySi, dragCoefficient, terrainProbe, 0.05);
    }

    public static BallisticFlightPlan.Result optimize(
            int revision, Vec3 launch, Vec3 targetPosition, Vec3 targetVelocityMps,
            long targetShipId, BallisticFlightPlan.Settings settings,
            double boostAccel, int boostTicks, double gravitySi, double dragCoefficient,
            TerrainProbe terrainProbe, double secondsPerServerTick) {
        BallisticFlightPlan.Settings safe = settings == null
                ? BallisticFlightPlan.Settings.defaults() : settings;
        double tickSeconds = Math.max(0.05, Math.min(0.5,
                Double.isFinite(secondsPerServerTick) ? secondsPerServerTick : 0.05));
        Vec3 velocity = targetVelocityMps == null ? Vec3.ZERO : targetVelocityMps;
        Vec3 resolved = targetPosition;
        BallisticCalculator.Result calculation = null;

        // Fixed-point lead: flight time changes as the intercept point moves.
        for (int i = 0; i < (targetShipId >= 0 ? 5 : 1); i++) {
            calculation = chooseCalculation(launch, resolved, safe, boostAccel, boostTicks,
                    gravitySi, dragCoefficient);
            double seconds = Math.max(0.0, Math.min(20.0 * 60.0 * 60.0,
                    calculation.flightTimeTicks() * tickSeconds));
            Vec3 next = targetPosition.add(velocity.scale(seconds));
            if (next.distanceToSqr(resolved) < 0.25) break;
            resolved = next;
        }

        BallisticTrajectory.FlightPlan base = BallisticTrajectory.plan(
                launch, resolved, boostAccel, boostTicks,
                safe.altitudeLayerEnabled() ? Math.max(0.0, safe.minimumClearanceY()) : 0.0,
                gravitySi, dragCoefficient);
        calculation = chooseCalculation(launch, resolved, safe, boostAccel, boostTicks,
                gravitySi, dragCoefficient);

        List<String> warnings = new ArrayList<>();
        if (!calculation.reachable() && safe.mode() == BallisticFlightPlan.FlightMode.PURE_BALLISTIC) {
            warnings.add("Pure ballistic arc is not reachable with available boost-exit speed");
        }

        double apex = base.apexY();
        double horizontalRange = Math.hypot(resolved.x - launch.x, resolved.z - launch.z);
        if (safe.desiredAngleDeg() > 0.0) {
            double launchSpeed = Double.isFinite(calculation.requiredSpeed())
                    ? Math.max(calculation.availableSpeed(), calculation.requiredSpeed())
                    : calculation.availableSpeed();
            double verticalSpeed = launchSpeed * Math.sin(Math.toRadians(safe.desiredAngleDeg()));
            double angleApex = launch.y + verticalSpeed * verticalSpeed
                    / (2.0 * BallisticCalculator.toTickGravity(gravitySi));
            apex = safe.mode() == BallisticFlightPlan.FlightMode.PURE_BALLISTIC
                    ? angleApex : Math.max(apex, angleApex);
        }
        apex = switch (safe.profile()) {
            case DIRECT -> Math.max(launch.y, resolved.y);
            case LOW_ARC -> Math.max(Math.max(launch.y, resolved.y) + 32.0,
                    safe.altitudeLayerEnabled() ? safe.minimumClearanceY() : 0.0);
            case CRUISE -> Math.max(Math.max(launch.y, resolved.y) + 64.0,
                    safe.altitudeLayerEnabled() ? safe.minimumClearanceY() : 0.0);
            case PARABOLIC -> Math.max(apex, Math.max(launch.y, resolved.y)
                    + 64.0 + Math.sqrt(horizontalRange) * 8.0 + horizontalRange * 0.008);
            case HIGH_LOFT -> Math.max(apex, Math.max(launch.y, resolved.y)
                    + 128.0 + Math.sqrt(horizontalRange) * 10.0 + horizontalRange * 0.018);
            case TOP_ATTACK -> Math.max(apex, resolved.y + 256.0);
            default -> apex;
        };
        boolean terrainUnknown = false;
        double highestLoadedTerrain = Double.NEGATIVE_INFINITY;
        boolean terrainCollision = false;
        if (terrainProbe != null) {
            for (int i = 0; i < GRAPH_SAMPLES; i++) {
                double f = i / (double) (GRAPH_SAMPLES - 1);
                int x = (int) Math.floor(launch.x + (resolved.x - launch.x) * f);
                int z = (int) Math.floor(launch.z + (resolved.z - launch.z) * f);
                double surface = terrainProbe.surfaceY(x, z);
                if (Double.isFinite(surface)) highestLoadedTerrain = Math.max(highestLoadedTerrain, surface);
                else terrainUnknown = true;
            }
        } else terrainUnknown = true;
        if (terrainUnknown) warnings.add("Terrain unknown outside loaded chunks; conservative altitude corridor used");
        if (safe.altitudeLayerEnabled()) {
            apex = Math.max(apex, safe.minimumClearanceY());
            if (Double.isFinite(highestLoadedTerrain)) apex = Math.max(apex, highestLoadedTerrain + 64.0);
            if (safe.ceilingY() > 0.0 && apex > safe.ceilingY()) {
                warnings.add("Required apex exceeds enabled ceiling");
            }
        }

        List<Vec3> controllerPoints = buildControllerWaypoints(launch, resolved, apex, safe, warnings, terrainProbe);
        List<BallisticFlightPlan.Sample> samples = safe.mode() == BallisticFlightPlan.FlightMode.PURE_BALLISTIC
                ? samplePure(launch, resolved, calculation, gravitySi, dragCoefficient,
                safe.desiredAngleDeg() > 0.0)
                : sampleGuided(launch, resolved, safe, controllerPoints, boostAccel, boostTicks,
                gravitySi, dragCoefficient);
        if (terrainProbe != null) {
            ArrayList<BallisticFlightPlan.Sample> checked = new ArrayList<>(samples.size());
            for (BallisticFlightPlan.Sample sample : samples) {
                double surface = terrainProbe.surfaceY((int) Math.floor(sample.x()), (int) Math.floor(sample.z()));
                boolean known = Double.isFinite(surface);
                if (known && sample.fraction() > 0.03 && sample.fraction() < 0.97 && sample.y() < surface + 12.0) {
                    terrainCollision = true;
                }
                checked.add(new BallisticFlightPlan.Sample(sample.fraction(), sample.x(), sample.y(), sample.z(),
                        sample.phase(), known));
            }
            samples = List.copyOf(checked);
            if (terrainCollision) warnings.add("Loaded terrain intersects the planned safety corridor");
        }

        boolean feasible = safe.mode() == BallisticFlightPlan.FlightMode.GUIDED_BOOST_GLIDE
                || calculation.reachable();
        if (safe.altitudeLayerEnabled() && safe.ceilingY() > 0.0 && apex > safe.ceilingY()) {
            feasible = false;
        }
        if (terrainCollision) feasible = false;
        String status = feasible
                ? safe.profile().name().replace('_', ' ') + " / "
                + (safe.mode() == BallisticFlightPlan.FlightMode.PURE_BALLISTIC
                ? "PURE BALLISTIC SOLUTION" : "GUIDED PLAN")
                : "PLAN INFEASIBLE";

        return new BallisticFlightPlan.Result(revision, safe, launch, resolved, targetShipId, feasible,
                status, calculation.azimuthDeg(), calculation.selectedAngleDeg(),
                calculation.requiredSpeed() * 20.0, calculation.availableSpeed() * 20.0,
                calculation.flightTimeTicks() * tickSeconds, apex,
                calculation.impactSpeed() * 20.0, calculation.predictedMiss(),
                warnings, samples, controllerPoints);
    }

    private static BallisticCalculator.Result chooseCalculation(
            Vec3 launch, Vec3 target, BallisticFlightPlan.Settings settings,
            double boostAccel, int boostTicks, double gravitySi, double drag) {
        double speed = BallisticTrajectory.boostExitSpeed(boostAccel, boostTicks);
        if (settings.desiredAngleDeg() > 0.0) {
            return BallisticCalculator.calculateForAngle(launch, target, speed,
                    settings.desiredAngleDeg(), gravitySi, drag);
        }
        double desiredApex = settings.altitudeLayerEnabled() ? settings.minimumClearanceY() : 0.0;
        if (settings.profile() == BallisticFlightPlan.TrajectoryProfile.HIGH_LOFT
                || settings.profile() == BallisticFlightPlan.TrajectoryProfile.TOP_ATTACK) {
            return BallisticCalculator.calculate(launch, target, speed, desiredApex, gravitySi, drag, true);
        }
        if (settings.profile() == BallisticFlightPlan.TrajectoryProfile.LOW_ARC
                || settings.profile() == BallisticFlightPlan.TrajectoryProfile.DIRECT) {
            return BallisticCalculator.calculate(launch, target, speed, 0, gravitySi, drag, false);
        }
        if (desiredApex > Math.max(launch.y, target.y)) {
            return BallisticCalculator.calculate(launch, target, speed, desiredApex, gravitySi, drag, true);
        }
        return switch (settings.arc()) {
            case HIGH -> BallisticCalculator.calculate(launch, target, speed, 0, gravitySi, drag, true);
            case LOW -> BallisticCalculator.calculate(launch, target, speed, 0, gravitySi, drag, false);
            case AUTO -> {
                BallisticCalculator.Result high = BallisticCalculator.calculate(
                        launch, target, speed, 0, gravitySi, drag, true);
                BallisticCalculator.Result low = BallisticCalculator.calculate(
                        launch, target, speed, 0, gravitySi, drag, false);
                if (high.reachable() != low.reachable()) yield high.reachable() ? high : low;
                yield high.predictedMiss() <= low.predictedMiss() ? high : low;
            }
        };
    }

    private static List<Vec3> buildControllerWaypoints(
            Vec3 launch, Vec3 target, double apexY, BallisticFlightPlan.Settings settings,
            List<String> warnings, TerrainProbe terrainProbe) {
        List<Vec3> points = new ArrayList<>();
        if (settings.angleCommandY() > 0.0) {
            double climbSpan = Math.max(1.0, apexY - launch.y);
            double heightFraction = Math.max(0.02, Math.min(0.98,
                    (settings.angleCommandY() - launch.y) / climbSpan));
            double commandFraction = Math.max(0.02, Math.min(settings.apexFraction(),
                    settings.apexFraction() * Math.sqrt(heightFraction)));
            points.add(pointOnTrack(launch, target, commandFraction, settings.angleCommandY()));
            // A second node makes the selected pitch visible in the preview instead of
            // showing only an altitude marker. Keep its baseline useful at both 500 m
            // and map-scale ranges without adding distance-dependent controller cost.
            double horizontalRange = Math.hypot(target.x - launch.x, target.z - launch.z);
            double pitchBaseline = Math.min(512.0, Math.max(32.0, horizontalRange * 0.04));
            double slopeFraction = Math.min(0.97,
                    commandFraction + pitchBaseline / Math.max(1.0, horizontalRange));
            if (slopeFraction > commandFraction + 1.0e-5) {
                double actualBaseline = (slopeFraction - commandFraction) * horizontalRange;
                double slopeY = settings.angleCommandY()
                        + Math.tan(Math.toRadians(settings.angleCommandDeg())) * actualBaseline;
                points.add(pointOnTrack(launch, target, slopeFraction, slopeY));
            }
            warnings.add(String.format(java.util.Locale.ROOT,
                    "Pitch command %.1f° executes at world Y %.0f",
                    settings.angleCommandDeg(), settings.angleCommandY()));
        }
        if (settings.phaseLayerEnabled()) {
            points.add(pointOnTrack(launch, target, settings.motorCutoffFraction(),
                    Math.max(launch.y + 16.0, profileCorridorY(settings, launch.y, target.y,
                            apexY, settings.motorCutoffFraction()))));
            points.add(pointOnTrack(launch, target, settings.apexFraction(),
                    profileCorridorY(settings, launch.y, target.y, apexY, settings.apexFraction())));
        }
        if (settings.waypointLayerEnabled()) {
            for (BallisticFlightPlan.Waypoint waypoint : settings.waypoints()) {
                Vec3 point = waypoint.position();
                if (settings.altitudeLayerEnabled()) {
                    if (point.y < settings.minimumClearanceY()) {
                        warnings.add("Waypoint below minimum altitude: " + Math.round(point.y));
                    }
                    if (settings.ceilingY() > 0.0 && point.y > settings.ceilingY()) {
                        warnings.add("Waypoint above ceiling: " + Math.round(point.y));
                    }
                }
                points.add(point);
            }
        }
        if (settings.autoEnabled()
                && settings.mode() == BallisticFlightPlan.FlightMode.GUIDED_BOOST_GLIDE) {
            int reservedAfterAuto = (settings.phaseLayerEnabled() ? 2 : 0) + 1;
            int budget = Math.max(0, BallisticFlightPlan.MAX_CONTROLLER_WAYPOINTS
                    - points.size() - reservedAfterAuto);
            double horizontalRange = Math.hypot(target.x - launch.x, target.z - launch.z);
            double spacing = switch (settings.profile()) {
                case TERRAIN_FOLLOWING -> 1_000.0;
                case DIRECT, LOW_ARC -> 4_000.0;
                case TOP_ATTACK -> 2_000.0;
                default -> 2_500.0;
            };
            int desired = Math.max(2, (int) Math.ceil(horizontalRange / spacing) - 1);
            int generated = Math.min(budget, desired);
            for (int i = 1; i <= generated; i++) {
                double f = i / (double) (generated + 1);
                double y = profileCorridorY(settings, launch.y, target.y, apexY, f);
                if (settings.altitudeLayerEnabled()) y = Math.max(y, settings.minimumClearanceY());
                if (terrainProbe != null) {
                    int x = (int) Math.floor(launch.x + (target.x - launch.x) * f);
                    int z = (int) Math.floor(launch.z + (target.z - launch.z) * f);
                    double surface = terrainProbe.surfaceY(x, z);
                    if (Double.isFinite(surface)) y = Math.max(y, surface + 48.0);
                }
                points.add(pointOnTrack(launch, target, f, y));
            }
            if (generated > 0) warnings.add("AUTO generated " + generated + " world-space corridor waypoints");
            if (generated < desired) {
                warnings.add("AUTO corridor resolution limited to " + generated
                        + " nodes by the controller waypoint budget");
            }
        }
        if (settings.phaseLayerEnabled()) {
            double cruiseY = profileCorridorY(settings, launch.y, target.y, apexY,
                    Math.max(settings.apexFraction() + 0.05, settings.terminalFraction() - 0.18));
            points.add(pointOnTrack(launch, target,
                    Math.max(settings.apexFraction() + 0.05, settings.terminalFraction() - 0.18), cruiseY));
            double terminalY = settings.profile() == BallisticFlightPlan.TrajectoryProfile.TOP_ATTACK
                    ? target.y + Math.max(192.0, Math.min(640.0, target.distanceTo(launch) * 0.08))
                    : profileCorridorY(settings, launch.y, target.y, apexY, settings.terminalFraction());
            points.add(pointOnTrack(launch, target, settings.terminalFraction(), terminalY));
        }
        // AUTO orders generated points along the ground track; manual-only preserves user order.
        if (settings.autoEnabled()) {
            points.sort(Comparator.comparingDouble(p -> trackFraction(launch, target, p)));
        }
        List<Vec3> normalized = new ArrayList<>(Math.min(
                BallisticFlightPlan.MAX_CONTROLLER_WAYPOINTS, points.size() + 1));
        for (Vec3 point : points) {
            if (point == null || !finite(point)) continue;
            if (!normalized.isEmpty() && normalized.get(normalized.size() - 1).distanceToSqr(point) < 1.0e-4) {
                continue;
            }
            if (normalized.size() >= BallisticFlightPlan.MAX_CONTROLLER_WAYPOINTS - 1) break;
            normalized.add(point);
        }
        if (normalized.isEmpty() || normalized.get(normalized.size() - 1).distanceToSqr(target) >= 1.0e-4) {
            normalized.add(target);
        } else {
            normalized.set(normalized.size() - 1, target);
        }
        return List.copyOf(normalized);
    }

    private static boolean finite(Vec3 point) {
        return Double.isFinite(point.x) && Double.isFinite(point.y) && Double.isFinite(point.z);
    }

    private static double plannedCorridorY(double launchY, double targetY, double apexY,
                                           double apexFraction, double fraction) {
        double peak = Math.max(0.05, Math.min(0.8, apexFraction));
        if (fraction <= peak) {
            double t = fraction / peak;
            return launchY + (apexY - launchY) * Math.sin(t * Math.PI * 0.5);
        }
        double t = (fraction - peak) / Math.max(0.01, 1.0 - peak);
        return apexY + (targetY - apexY) * t;
    }

    private static double profileCorridorY(BallisticFlightPlan.Settings settings,
                                           double launchY, double targetY, double apexY, double fraction) {
        double f = Math.max(0.0, Math.min(1.0, fraction));
        double linear = launchY + (targetY - launchY) * f;
        return switch (settings.profile()) {
            case DIRECT -> linear;
            case PARABOLIC -> linear + Math.max(0.0, apexY - (launchY + targetY) * 0.5)
                    * 4.0 * f * (1.0 - f);
            case LOW_ARC -> linear + Math.max(24.0, apexY - Math.max(launchY, targetY))
                    * 4.0 * f * (1.0 - f);
            case CRUISE -> {
                double cruise = Math.max(apexY, settings.minimumClearanceY());
                if (f < 0.12) yield launchY + (cruise - launchY) * (f / 0.12);
                if (f > settings.terminalFraction()) {
                    double t = (f - settings.terminalFraction()) / Math.max(0.01, 1.0 - settings.terminalFraction());
                    yield cruise + (targetY - cruise) * t;
                }
                yield cruise;
            }
            case TERRAIN_FOLLOWING -> Math.max(linear + 48.0,
                    settings.altitudeLayerEnabled() ? settings.minimumClearanceY() : linear + 48.0);
            case TOP_ATTACK -> {
                if (f < settings.terminalFraction()) yield plannedCorridorY(launchY, targetY, apexY,
                        settings.apexFraction(), f);
                double t = (f - settings.terminalFraction()) / Math.max(0.01, 1.0 - settings.terminalFraction());
                double attackY = targetY + Math.max(192.0, Math.min(640.0, apexY - targetY));
                yield attackY + (targetY - attackY) * t;
            }
            case HIGH_LOFT, AUTO -> plannedCorridorY(launchY, targetY, apexY,
                    settings.apexFraction(), f);
        };
    }

    private static List<BallisticFlightPlan.Sample> sampleGuided(
            Vec3 launch, Vec3 target, BallisticFlightPlan.Settings settings,
            List<Vec3> controllerPoints, double boostAccel, int boostTicks,
            double gravitySi, double drag) {
        // Integrate the same kind of bounded acceleration used by live guidance. The old
        // preview merely drew straight lines between waypoints, hiding overshoot, gravity,
        // drag and the rounded turns that a massive VS body must actually fly.
        List<Vec3> route = controllerPoints.isEmpty() ? List.of(target) : controllerPoints;
        double pathLength = 0.0;
        double plannedApexY = launch.y;
        Vec3 previous = launch;
        for (Vec3 point : route) {
            pathLength += previous.distanceTo(point);
            plannedApexY = Math.max(plannedApexY, point.y);
            previous = point;
        }

        double baseAccel = Math.max(1.0, boostAccel * 55.0);
        double midcourseAccel = baseAccel * 0.55;
        double terminalAccel = baseAccel * 1.1;
        double boostSeconds = Math.max(0.0, boostTicks / 20.0);
        double estimateSeconds = Math.max(6.0,
                boostSeconds + 2.4 * Math.sqrt(Math.max(1.0, pathLength) / midcourseAccel));
        double dt = Math.max(0.025, Math.min(0.25, estimateSeconds / 2400.0));
        double maxSeconds = Math.min(20.0 * 60.0 * 60.0, estimateSeconds * 2.5 + 30.0);
        int maxSteps = Math.max(GRAPH_SAMPLES,
                Math.min(12000, (int) Math.ceil(maxSeconds / dt)));

        ArrayList<SimPoint> integrated = new ArrayList<>(Math.min(maxSteps, 12000));
        double x = launch.x, y = launch.y, z = launch.z;
        double vx = 0.0, vy = 0.0, vz = 0.0;
        double time = 0.0;
        int waypoint = 0;
        double bestDistance = launch.distanceTo(target);
        int bestIndex = 0;
        boolean terminal = false;
        integrated.add(new SimPoint(x, y, z, BallisticFlightPlan.Phase.EJECT));

        for (int step = 1; step < maxSteps; step++) {
            Vec3 position = new Vec3(x, y, z);
            double distance = position.distanceTo(target);
            double groundFraction = trackFraction(launch, target, position);
            while (waypoint < route.size() - 1) {
                Vec3 point = route.get(waypoint);
                if (position.distanceTo(point) <= 20.0
                        || trackFraction(launch, target, point) + 0.025 < groundFraction) waypoint++;
                else break;
            }

            double ax;
            double ay;
            double az;
            double commandAccel;
            BallisticFlightPlan.Phase phase;
            if (time < 0.4) {
                ax = 0.0; ay = 1.0; az = 0.0;
                commandAccel = baseAccel * 1.4;
                phase = BallisticFlightPlan.Phase.EJECT;
            } else {
                terminal |= groundFraction >= Math.max(0.80, settings.terminalFraction())
                        || distance <= Math.max(64.0, pathLength * 0.08);
                if (terminal) {
                    double invDistance = distance > 1.0e-8 ? 1.0 / distance : 0.0;
                    double desiredSpeed = Math.max(6.0,
                            Math.min(240.0, Math.sqrt(2.0 * terminalAccel * distance) * 0.82));
                    ax = (target.x - x) * invDistance * desiredSpeed - vx;
                    ay = (target.y - y) * invDistance * desiredSpeed - vy;
                    az = (target.z - z) * invDistance * desiredSpeed - vz;
                    commandAccel = terminalAccel;
                    phase = BallisticFlightPlan.Phase.TERMINAL;
                } else {
                    Vec3 point = route.get(Math.min(waypoint, route.size() - 1));
                    double verticalStop = vy > 0.0
                            ? vy * vy / (2.0 * Math.max(1.0, midcourseAccel)) : 0.0;
                    boolean boosting = time < boostSeconds + 0.4
                            && y + verticalStop < plannedApexY - 4.0;
                    commandAccel = boosting ? baseAccel : midcourseAccel;
                    double rx = point.x - x;
                    double ry = point.y - y;
                    double rz = point.z - z;
                    double pointDistance = Math.sqrt(rx * rx + ry * ry + rz * rz);
                    if (boosting) {
                        ax = rx; ay = ry; az = rz;
                    } else if (pointDistance > 1.0e-8) {
                        double desiredSpeed = Math.max(8.0, Math.min(240.0,
                                Math.sqrt(2.0 * midcourseAccel * pointDistance) * 0.72));
                        ax = rx / pointDistance * desiredSpeed - vx;
                        ay = ry / pointDistance * desiredSpeed - vy;
                        az = rz / pointDistance * desiredSpeed - vz;
                    } else {
                        ax = -vx; ay = -vy; az = -vz;
                    }
                    phase = boosting ? BallisticFlightPlan.Phase.BOOST
                            : BallisticFlightPlan.Phase.COAST;
                }
            }

            double commandLength = Math.sqrt(ax * ax + ay * ay + az * az);
            if (commandLength > 1.0e-8) {
                double scale = commandAccel / commandLength;
                ax *= scale; ay *= scale; az *= scale;
            } else {
                ax = ay = az = 0.0;
            }
            ay -= BallisticCalculator.sanitizeGravity(gravitySi);
            double speed = Math.sqrt(vx * vx + vy * vy + vz * vz);
            if (speed > 1.0e-8 && drag > 0.0) {
                double dragAccel = Math.min(50.0, BallisticCalculator.sanitizeDrag(drag)
                        * BallisticCalculator.airDensityFactor(y) * speed * speed);
                ax -= dragAccel * vx / speed;
                ay -= dragAccel * vy / speed;
                az -= dragAccel * vz / speed;
            }

            // Semi-implicit Euler is stable for thrust/drag and matches the live tick order.
            vx += ax * dt; vy += ay * dt; vz += az * dt;
            x += vx * dt; y += vy * dt; z += vz * dt;
            time += dt;
            integrated.add(new SimPoint(x, y, z, phase));

            double newDistance = new Vec3(x, y, z).distanceTo(target);
            if (newDistance < bestDistance) {
                bestDistance = newDistance;
                bestIndex = integrated.size() - 1;
            }
            if (newDistance <= 2.0 || (terminal && integrated.size() - bestIndex > 80)) break;
        }

        int last = Math.max(1, bestIndex);
        List<BallisticFlightPlan.Sample> out = new ArrayList<>(GRAPH_SAMPLES);
        for (int i = 0; i < GRAPH_SAMPLES; i++) {
            double f = i / (double) (GRAPH_SAMPLES - 1);
            double cursor = f * last;
            int a = Math.min(last, (int) Math.floor(cursor));
            int b = Math.min(last, a + 1);
            double local = cursor - a;
            SimPoint pa = integrated.get(a);
            SimPoint pb = integrated.get(b);
            out.add(new BallisticFlightPlan.Sample(f,
                    pa.x + (pb.x - pa.x) * local,
                    pa.y + (pb.y - pa.y) * local,
                    pa.z + (pb.z - pa.z) * local,
                    local < 0.5 ? pa.phase : pb.phase, false));
        }
        return out;
    }

    private record SimPoint(double x, double y, double z, BallisticFlightPlan.Phase phase) {}

    private static List<BallisticFlightPlan.Sample> samplePure(
            Vec3 launch, Vec3 target, BallisticCalculator.Result calculation,
            double gravitySi, double drag, boolean fixedAngle) {
        double pitch = Math.toRadians(calculation.selectedAngleDeg());
        double azimuth = Math.atan2(target.z - launch.z, target.x - launch.x);
        double speed = fixedAngle && Double.isFinite(calculation.requiredSpeed())
                ? Math.min(calculation.availableSpeed(), calculation.requiredSpeed())
                : calculation.availableSpeed();
        double vx = Math.cos(pitch) * Math.cos(azimuth) * speed;
        double vy = Math.sin(pitch) * speed;
        double vz = Math.cos(pitch) * Math.sin(azimuth) * speed;
        double x = launch.x, y = launch.y, z = launch.z;
        double totalTicks = Math.max(1.0, calculation.flightTimeTicks());
        double dt = Math.max(0.05, totalTicks / ((GRAPH_SAMPLES - 1) * 4.0));
        double sampleEvery = totalTicks / (GRAPH_SAMPLES - 1);
        double nextSample = 0.0;
        double time = 0.0;
        double gravity = BallisticCalculator.toTickGravity(gravitySi);
        List<BallisticFlightPlan.Sample> out = new ArrayList<>(GRAPH_SAMPLES);
        while (out.size() < GRAPH_SAMPLES) {
            if (time + 1.0e-6 >= nextSample) {
                double f = out.size() / (double) (GRAPH_SAMPLES - 1);
                out.add(new BallisticFlightPlan.Sample(f, x, y, z,
                        f < 0.12 ? BallisticFlightPlan.Phase.BOOST
                                : (f < 0.82 ? BallisticFlightPlan.Phase.COAST
                                : BallisticFlightPlan.Phase.TERMINAL), false));
                nextSample += sampleEvery;
                if (out.size() >= GRAPH_SAMPLES) break;
            }
            double v = Math.sqrt(vx * vx + vy * vy + vz * vz);
            double dragA = drag * BallisticCalculator.airDensityFactor(y) * v * v;
            double ax = v > 1.0e-9 ? -dragA * vx / v : 0.0;
            double ay = -gravity + (v > 1.0e-9 ? -dragA * vy / v : 0.0);
            double az = v > 1.0e-9 ? -dragA * vz / v : 0.0;
            vx += ax * dt; vy += ay * dt; vz += az * dt;
            x += vx * dt; y += vy * dt; z += vz * dt;
            time += dt;
            if (time > totalTicks * 1.5 + 20.0) break;
        }
        while (out.size() < GRAPH_SAMPLES) {
            double f = out.size() / (double) (GRAPH_SAMPLES - 1);
            out.add(new BallisticFlightPlan.Sample(f, x, y, z,
                    BallisticFlightPlan.Phase.TERMINAL, false));
        }
        return out;
    }

    private static BallisticFlightPlan.Phase phaseFor(double fraction,
                                                       BallisticFlightPlan.Settings settings) {
        if (fraction < 0.02) return BallisticFlightPlan.Phase.EJECT;
        if (fraction < settings.motorCutoffFraction()) return BallisticFlightPlan.Phase.BOOST;
        if (fraction < settings.terminalFraction()) return BallisticFlightPlan.Phase.COAST;
        return BallisticFlightPlan.Phase.TERMINAL;
    }

    private static Vec3 pointOnTrack(Vec3 launch, Vec3 target, double fraction, double y) {
        return new Vec3(launch.x + (target.x - launch.x) * fraction, y,
                launch.z + (target.z - launch.z) * fraction);
    }

    private static double trackFraction(Vec3 launch, Vec3 target, Vec3 point) {
        double dx = target.x - launch.x;
        double dz = target.z - launch.z;
        double r2 = dx * dx + dz * dz;
        if (r2 < 1.0) return 1.0;
        return ((point.x - launch.x) * dx + (point.z - launch.z) * dz) / r2;
    }
}
