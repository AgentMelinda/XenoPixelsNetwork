package net.bullettrain.xenopixelsmod.missile;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BallisticFlightPlannerTest {
    @Test
    void defaultsEnableAccuracyFirstAutoGuidance() {
        BallisticFlightPlan.Settings settings = BallisticFlightPlan.Settings.defaults();
        assertTrue(settings.autoEnabled());
        assertEquals(BallisticFlightPlan.FlightMode.GUIDED_BOOST_GLIDE, settings.mode());
        assertEquals(BallisticFlightPlan.ArcPreference.AUTO, settings.arc());
    }

    @Test
    void settingsRoundTripAndClampWaypointCount() {
        ArrayList<BallisticFlightPlan.Waypoint> points = new ArrayList<>();
        for (int i = 0; i < 30; i++) points.add(new BallisticFlightPlan.Waypoint(i, 300 + i, -i, i == 0));
        BallisticFlightPlan.Settings original = new BallisticFlightPlan.Settings(true,
                BallisticFlightPlan.FlightMode.PURE_BALLISTIC, BallisticFlightPlan.ArcPreference.HIGH,
                BallisticFlightPlan.TrajectoryProfile.PARABOLIC,
                true, true, true, .1, .3, .85, 260, 4000, points);
        BallisticFlightPlan.Settings restored = BallisticFlightPlan.Settings.load(original.save());
        assertEquals(BallisticFlightPlan.MAX_WAYPOINTS, restored.waypoints().size());
        assertEquals(original.mode(), restored.mode());
        assertEquals(BallisticFlightPlan.TrajectoryProfile.PARABOLIC, restored.profile());
        assertTrue(restored.waypoints().get(0).locked());
    }

    @Test
    void legacyNbtDefaultsAutoOn() {
        CompoundTag legacy = new CompoundTag();
        legacy.putString("Mode", "GUIDED_BOOST_GLIDE");
        assertTrue(BallisticFlightPlan.Settings.load(legacy).autoEnabled());
    }

    @Test
    void plannerHandlesIsraelScaleRangeWithoutOverflow() {
        BallisticFlightPlan.Result result = BallisticPlanOptimizer.optimize(7,
                new Vec3(0, 80, 0), new Vec3(300_000, 75, 0), Vec3.ZERO, -1,
                BallisticFlightPlan.Settings.defaults(), 2.36, 160,
                BallisticCalculator.EARTH_GRAVITY, BallisticCalculator.DEFAULT_DRAG);
        assertEquals(128, result.samples().size());
        assertTrue(Double.isFinite(result.etaSeconds()));
        assertTrue(Double.isFinite(result.apexY()));
        assertEquals(0, result.launchPosition().x(), 0.001);
        assertEquals(300_000, result.resolvedTarget().x(), 0.001);
    }

    @Test
    void movingTargetGetsLedAndPacketsStayBounded() {
        BallisticFlightPlan.Result result = BallisticPlanOptimizer.optimize(2,
                new Vec3(0, 100, 0), new Vec3(10_000, 100, 0), new Vec3(5, 0, 2), 42,
                BallisticFlightPlan.Settings.defaults(), 1.5, 120,
                BallisticCalculator.EARTH_GRAVITY, BallisticCalculator.DEFAULT_DRAG);
        assertEquals(42, result.targetShipId());
        assertTrue(result.resolvedTarget().x() > 10_000);
        assertTrue(result.resolvedTarget().z() > 0);
        assertTrue(result.samples().size() <= 128);
        assertTrue(result.controllerWaypoints().size() <= BallisticFlightPlan.MAX_CONTROLLER_WAYPOINTS);
    }

    @Test
    void lowTpsExtendsEtaAndMovingTargetLead() {
        Vec3 launch = new Vec3(0, 100, 0);
        Vec3 target = new Vec3(10_000, 100, 0);
        Vec3 targetVelocity = new Vec3(5, 0, 0);
        BallisticFlightPlan.Result normal = BallisticPlanOptimizer.optimize(1, launch, target,
                targetVelocity, 7, BallisticFlightPlan.Settings.defaults(), 1.5, 120,
                BallisticCalculator.EARTH_GRAVITY, BallisticCalculator.DEFAULT_DRAG, null, 0.05);
        BallisticFlightPlan.Result lagged = BallisticPlanOptimizer.optimize(2, launch, target,
                targetVelocity, 7, BallisticFlightPlan.Settings.defaults(), 1.5, 120,
                BallisticCalculator.EARTH_GRAVITY, BallisticCalculator.DEFAULT_DRAG, null, 0.10);
        assertTrue(lagged.etaSeconds() > normal.etaSeconds());
        assertTrue(lagged.resolvedTarget().x > normal.resolvedTarget().x);
    }

    @Test
    void loadedTerrainIsMarkedWithoutForcingUnknownSamples() {
        BallisticFlightPlan.Result result = BallisticPlanOptimizer.optimize(3,
                new Vec3(0, 100, 0), new Vec3(2000, 100, 0), Vec3.ZERO, -1,
                BallisticFlightPlan.Settings.defaults(), 1.2, 100,
                BallisticCalculator.EARTH_GRAVITY, BallisticCalculator.DEFAULT_DRAG,
                (x, z) -> x < 1000 ? 90 : Double.NaN);
        assertTrue(result.samples().stream().anyMatch(BallisticFlightPlan.Sample::terrainKnown));
        assertTrue(result.samples().stream().anyMatch(s -> !s.terrainKnown()));
        assertTrue(result.warnings().stream().anyMatch(s -> s.contains("unknown")));
        assertTrue(result.warnings().stream().anyMatch(s -> s.contains("AUTO generated")));
    }

    @Test
    void trajectoryProfilesProduceDifferentWorldSpaceGeometry() {
        BallisticFlightPlan.Settings defaults = BallisticFlightPlan.Settings.defaults();
        BallisticFlightPlan.Settings direct = withProfile(defaults, BallisticFlightPlan.TrajectoryProfile.DIRECT);
        BallisticFlightPlan.Settings parabolic = withProfile(defaults, BallisticFlightPlan.TrajectoryProfile.PARABOLIC);
        Vec3 launch = new Vec3(1000, 80, -500);
        Vec3 target = new Vec3(1500, 80, -500);
        BallisticFlightPlan.Result directPlan = BallisticPlanOptimizer.optimize(1, launch, target, Vec3.ZERO,
                -1, direct, 1.2, 100, BallisticCalculator.EARTH_GRAVITY, BallisticCalculator.DEFAULT_DRAG);
        BallisticFlightPlan.Result parabolaPlan = BallisticPlanOptimizer.optimize(2, launch, target, Vec3.ZERO,
                -1, parabolic, 1.2, 100, BallisticCalculator.EARTH_GRAVITY, BallisticCalculator.DEFAULT_DRAG);
        assertEquals(launch.x, parabolaPlan.launchPosition().x, 0.001);
        double directMax = directPlan.samples().stream().mapToDouble(BallisticFlightPlan.Sample::y).max().orElseThrow();
        double parabolaMax = parabolaPlan.samples().stream().mapToDouble(BallisticFlightPlan.Sample::y).max().orElseThrow();
        assertTrue(parabolaMax > directMax + 10.0,
                "parabolic max=" + parabolaMax + ", direct max=" + directMax);
    }

    @Test
    void desiredAngleRoundTripsAndDrivesServerPlan() {
        BallisticFlightPlan.Settings defaults = BallisticFlightPlan.Settings.defaults();
        BallisticFlightPlan.Settings locked = new BallisticFlightPlan.Settings(
                defaults.autoEnabled(), BallisticFlightPlan.FlightMode.PURE_BALLISTIC,
                defaults.arc(), defaults.profile(), defaults.phaseLayerEnabled(),
                defaults.waypointLayerEnabled(), false, defaults.motorCutoffFraction(),
                defaults.apexFraction(), defaults.terminalFraction(), 0.0, 0.0, 37.5,
                defaults.waypoints());
        BallisticFlightPlan.Settings restored = BallisticFlightPlan.Settings.load(locked.save());
        assertEquals(37.5, restored.desiredAngleDeg(), 1.0e-9);
        BallisticFlightPlan.Result result = BallisticPlanOptimizer.optimize(9,
                new Vec3(0, 100, 0), new Vec3(1_000, 100, 0), Vec3.ZERO, -1,
                restored, 2.0, 180, BallisticCalculator.EARTH_GRAVITY, 0.0);
        assertEquals(37.5, result.elevationDeg(), 1.0e-6);
        assertTrue(Double.isFinite(result.requiredSpeedMps()));
    }

    @Test
    void altitudePitchCommandPersistsAndAddsPreviewSlope() {
        BallisticFlightPlan.Settings s = BallisticFlightPlan.Settings.defaults();
        BallisticFlightPlan.Settings programmed = new BallisticFlightPlan.Settings(
                s.autoEnabled(), BallisticFlightPlan.FlightMode.GUIDED_BOOST_GLIDE,
                s.arc(), s.profile(), s.phaseLayerEnabled(), true, s.altitudeLayerEnabled(),
                s.motorCutoffFraction(), s.apexFraction(), s.terminalFraction(),
                s.minimumClearanceY(), s.ceilingY(), s.desiredAngleDeg(),
                240.0, 32.0, s.waypoints());
        BallisticFlightPlan.Settings restored = BallisticFlightPlan.Settings.load(programmed.save());
        assertEquals(240.0, restored.angleCommandY(), 1.0e-9);
        assertEquals(32.0, restored.angleCommandDeg(), 1.0e-9);

        BallisticFlightPlan.Result result = BallisticPlanOptimizer.optimize(10,
                new Vec3(0, 80, 0), new Vec3(1_000, 80, 0), Vec3.ZERO, -1,
                restored, 2.0, 180, BallisticCalculator.EARTH_GRAVITY, 0.0);
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("Pitch command")));
        int trigger = -1;
        for (int i = 0; i < result.controllerWaypoints().size(); i++) {
            if (Math.abs(result.controllerWaypoints().get(i).y - 240.0) < 1.0e-6) {
                trigger = i;
                break;
            }
        }
        assertTrue(trigger >= 0);
        Vec3 atY = result.controllerWaypoints().get(trigger);
        boolean hasSlopeNode = false;
        for (int i = trigger + 1; i < result.controllerWaypoints().size(); i++) {
            Vec3 candidate = result.controllerWaypoints().get(i);
            double horizontal = Math.hypot(candidate.x - atY.x, candidate.z - atY.z);
            double angle = Math.toDegrees(Math.atan2(candidate.y - atY.y, horizontal));
            if (Math.abs(angle - 32.0) < 0.01) {
                hasSlopeNode = true;
                break;
            }
        }
        assertTrue(hasSlopeNode);
    }

    @Test
    void fixedAngleCalculatorSolvesRequiredPower() {
        BallisticCalculator.Result result = BallisticCalculator.calculateForAngle(
                new Vec3(0, 80, 0), new Vec3(1_000, 80, 0), 6.0,
                45.0, BallisticCalculator.EARTH_GRAVITY, 0.0);
        assertEquals(45.0, result.selectedAngleDeg(), 1.0e-9);
        assertTrue(Double.isFinite(result.requiredSpeed()));
        assertTrue(result.requiredSpeed() < result.availableSpeed());
        assertTrue(result.predictedMiss() < 16.0);
        assertTrue(result.reachable());
    }

    private static BallisticFlightPlan.Settings withProfile(BallisticFlightPlan.Settings s,
                                                             BallisticFlightPlan.TrajectoryProfile profile) {
        return new BallisticFlightPlan.Settings(s.autoEnabled(), s.mode(), s.arc(), profile,
                s.phaseLayerEnabled(), s.waypointLayerEnabled(), s.altitudeLayerEnabled(),
                s.motorCutoffFraction(), s.apexFraction(), s.terminalFraction(),
                s.minimumClearanceY(), s.ceilingY(), s.waypoints());
    }
}
