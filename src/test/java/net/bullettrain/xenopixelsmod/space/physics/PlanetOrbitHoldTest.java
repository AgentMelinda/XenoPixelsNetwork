package net.bullettrain.xenopixelsmod.space.physics;

import net.bullettrain.xenopixelsmod.space.math.OrbitalElements;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanetOrbitHoldTest {
    private static final double K = 0.02, C = 0.28, CEILING = 0.5, DT = 0.05;

    @Test void correctionIsClampedButFeedForwardIsNot() {
        double[] out = new double[3];
        double length = PlanetOrbitHold.acceleration(new double[]{0, 0, 0}, new double[]{0, 0, 0},
                new double[]{1000, 0, 0}, new double[]{0, 0, 0}, new double[]{0, 0, -3}, K, C, CEILING, out);
        assertEquals(CEILING, length, 1e-12);
        assertEquals(CEILING, out[0], 1e-12);
        assertEquals(-3, out[2], 1e-12, "feed-forward passes through the clamp untouched");
    }

    @Test void releasedPlanetReturnsToStationWithoutLargeOvershoot() {
        double[] target = {1000, 128.5, 0}, zero = {0, 0, 0}, out = new double[3];
        double[] p = {1100, 128.5, 40}, v = {0, 0, 0};
        double maxBeyond = 0;
        for (int i = 0; i < 20 * 600; i++) {
            PlanetOrbitHold.acceleration(p, v, target, zero, zero, K, C, CEILING, out);
            for (int a = 0; a < 3; a++) { v[a] += out[a] * DT; p[a] += v[a] * DT; }
            maxBeyond = Math.max(maxBeyond, target[0] - p[0]);
        }
        assertEquals(1000, p[0], 0.05);
        assertEquals(0, p[2], 0.05);
        assertTrue(maxBeyond < 15, "near-critical damping must not swing far past the station: " + maxBeyond);
    }

    @Test void towBelowCeilingIsResistedAboveCeilingWins() {
        double[] target = {0, 0, 0}, zero = {0, 0, 0}, out = new double[3];
        // A steady external pull of 0.4 blocks/s² is held near the station; 0.8 drags the planet away.
        assertTrue(simulateTow(0.4, target, zero, out) < 25);
        assertTrue(simulateTow(0.8, target, zero, out) > 1000);
    }

    private static double simulateTow(double pull, double[] target, double[] zero, double[] out) {
        double[] p = {0, 0, 0}, v = {0, 0, 0};
        for (int i = 0; i < 20 * 300; i++) {
            PlanetOrbitHold.acceleration(p, v, target, zero, zero, K, C, CEILING, out);
            v[0] += (out[0] + pull) * DT;
            for (int a = 0; a < 3; a++) p[a] += v[a] * DT;
            v[1] += out[1] * DT; v[2] += out[2] * DT;
        }
        return p[0];
    }

    @Test void releaseReturnComesHomeFastCappedAndWithoutOvershoot() {
        double[] target = {1000, 128.5, 0}, zero = {0, 0, 0}, out = new double[3];
        double[] p = {1000, 128.5, 86}, v = {0, 0, 0};
        double maxSpeed = 0, maxBeyond = 0;
        int settled = -1;
        for (int i = 0; i < 20 * 120; i++) {
            PlanetOrbitHold.returnAcceleration(p, v, target, zero, zero, 0.08, 0.57, 3.0, 60.0, out);
            for (int a = 0; a < 3; a++) { v[a] += out[a] * DT; p[a] += v[a] * DT; }
            maxSpeed = Math.max(maxSpeed, Math.abs(v[2]));
            maxBeyond = Math.max(maxBeyond, -p[2]);
            if (settled < 0 && Math.abs(p[2]) < 1) settled = i;
        }
        assertTrue(settled > 0 && settled < 20 * 30, "86 blocks home within 30 s: " + settled / 20.0);
        assertTrue(maxBeyond < 1, "no overshoot past the station: " + maxBeyond);
        assertTrue(maxSpeed <= 60.0 + 1e-9);
    }

    @Test void releaseReturnCruisesAtTheSpeedCapFromFarAway() {
        double[] target = {0, 0, 0}, zero = {0, 0, 0}, out = new double[3];
        double[] p = {3000, 0, 0}, v = {0, 0, 0};
        double maxSpeed = 0;
        for (int i = 0; i < 20 * 200; i++) {
            PlanetOrbitHold.returnAcceleration(p, v, target, zero, zero, 0.08, 0.57, 3.0, 60.0, out);
            for (int a = 0; a < 3; a++) { v[a] += out[a] * DT; p[a] += v[a] * DT; }
            maxSpeed = Math.max(maxSpeed, Math.abs(v[0]));
        }
        assertTrue(maxSpeed <= 60.0 + 1e-6, "speed cap: " + maxSpeed);
        assertTrue(maxSpeed > 55, "cruises near the cap: " + maxSpeed);
        assertEquals(0, p[0], 0.5);
    }

    @Test void releaseDetectorSeesAPushAndThenTheRelease() {
        var detector = new PlanetOrbitHold.ReleaseDetector();
        double[] command = {0.3, 0, 0}, previous = {0.3, 0, 0};
        // The body does exactly what was commanded: released.
        assertTrue(!detector.update(new double[]{0.3, 0, 0}, command, previous, 0.05, 40, 1));
        // A ship adds 0.6 blocks/s² on top: held.
        for (int i = 0; i < 5; i++) detector.update(new double[]{0.9, 0, 0}, command, previous, 0.05, 40, 1);
        assertTrue(detector.held(40));
        // Released again: still held through the grace period, then released.
        int ticks = 0;
        while (detector.update(new double[]{0.3, 0, 0}, command, previous, 0.05, 40, 1) && ticks < 200) ticks++;
        assertTrue(ticks >= 39 && ticks < 80, "release after the grace period: " + ticks);
    }

    @Test void releaseDetectorAcceptsAOneTickLateImpulse() {
        var detector = new PlanetOrbitHold.ReleaseDetector();
        // The command steps from 0 to 3; the measured response lags one tick behind.
        assertTrue(!detector.update(new double[]{0, 0, 0}, new double[]{3, 0, 0}, new double[]{0, 0, 0}, 0.05, 40, 1));
        assertTrue(!detector.update(new double[]{3, 0, 0}, new double[]{0, 0, 0}, new double[]{3, 0, 0}, 0.05, 40, 1));
    }

    @Test void orbitTrackIsHorizontalWithCentripetalFeedForward() {
        var orbit = OrbitalElements.circular(1000, 3600);
        double mu = PlanetShell.orbitMu(1000, 3600);
        double[] p = new double[3], v = new double[3], a = new double[3];
        PlanetOrbitHold.orbitTrack(orbit, mu, 900, p, v, a);
        assertEquals(0, p[1], 1e-9, "Kepler XY maps to world XZ: no vertical offset");
        assertEquals(0, v[1], 1e-9);
        assertEquals(1000, Math.hypot(p[0], p[2]), 1e-6);
        double n = 2 * Math.PI / 3600;
        assertEquals(n * n * 1000, Math.hypot(a[0], a[2]), 1e-9);
        assertTrue(a[0] * p[0] + a[2] * p[2] < 0, "centripetal: points back at the parent");
    }

    @Test void phaseShiftStartsTheTrackAtTheCurrentBearing() {
        var orbit = OrbitalElements.circular(1000, 3600);
        double now = 932.35;
        double shift = PhysicalEarth.phaseShift(orbit, now, 0, 1000);
        double[] p = new double[3], v = new double[3], a = new double[3];
        PlanetOrbitHold.orbitTrack(orbit, PlanetShell.orbitMu(1000, 3600), now + shift, p, v, a);
        assertEquals(0, p[0], 1e-6);
        assertEquals(1000, p[2], 1e-6);
    }

    @Test void phaseShiftPreservesAnInclinedOrbitInThreeDimensions() {
        var orbit = new OrbitalElements(840, .27, .61, .42, 1.17, -.31, 5280, 0);
        double now = 321.75, knownShift = 913.25;
        double[] reference = new double[3];
        net.bullettrain.xenopixelsmod.space.math.KeplerSolver.position(orbit, now + knownShift, reference);
        // Kepler reference [x,y,z] maps into Minecraft world [x,z,y].
        double worldX = reference[0], worldY = reference[2], worldZ = reference[1];
        double shift = PhysicalEarth.phaseShift(orbit, now, worldX, worldY, worldZ);
        double[] actual = new double[3], velocity = new double[3], acceleration = new double[3];
        PlanetOrbitHold.orbitTrack(orbit, PlanetShell.orbitMu(840, 5280), now + shift,
                actual, velocity, acceleration);
        assertEquals(worldX, actual[0], 1.0e-5);
        assertEquals(worldY, actual[1], 1.0e-5);
        assertEquals(worldZ, actual[2], 1.0e-5);
    }

    @Test void persistedOrbitSignatureTracksEveryElementAndPeriod() {
        var orbit = new OrbitalElements(1000, .1, .2, .3, .4, .5, 3600, 0);
        String signature = PhysicalEarth.orbitSignature(orbit);
        assertEquals(signature, PhysicalEarth.orbitSignature(orbit));
        assertTrue(!PhysicalEarth.orbitNeedsReanchor(signature, orbit), "same saved orbit must keep its phase offset");
        assertTrue(PhysicalEarth.orbitNeedsReanchor("", orbit), "legacy offsets without a signature must re-anchor");
        assertNotEquals(signature, PhysicalEarth.orbitSignature(new OrbitalElements(1000, .1, .2, .3, .4, .5, 269.288, 0)), "period edits must re-anchor saved bodies");
        assertNotEquals(signature, PhysicalEarth.orbitSignature(new OrbitalElements(1000, .1, .2, .3, .4, .6, 3600, 0)), "epoch phase edits must re-anchor saved bodies");
        assertNotEquals(signature, PhysicalEarth.orbitSignature(new OrbitalElements(1000, .1, .21, .3, .4, .5, 3600, 0)), "plane edits must re-anchor saved bodies");
        assertNotEquals(signature, PhysicalEarth.orbitSignature(new OrbitalElements(1000, .1, .2, .3, .4, .5, 3600, 1)), "epoch edits must re-anchor saved bodies");
    }
}
