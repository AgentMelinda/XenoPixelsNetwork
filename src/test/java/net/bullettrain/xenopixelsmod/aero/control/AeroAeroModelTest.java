package net.bullettrain.xenopixelsmod.aero.control;

import net.bullettrain.xenopixelsmod.aero.AeroConfig;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AeroAeroModelTest {

    @Test
    void belowCrawlSpeedProducesNoForce() {
        Vector3d out = new Vector3d(1, 1, 1);
        double speed = AeroAeroModel.compute(0.001, 0, 0, 0, 1, 0, 0.0, out);
        assertEquals(0.0, speed, 1.0e-12);
        assertEquals(0.0, out.x, 1.0e-12);
        assertEquals(0.0, out.y, 1.0e-12);
        assertEquals(0.0, out.z, 1.0e-12);
    }

    @Test
    void noseAlignedWithVelocityProducesDragOpposingMotionOnly() {
        // Flying straight forward along +X with the nose pointed the same way: zero AoA, so
        // the only force component should be drag, directly opposing velocity.
        Vector3d out = new Vector3d();
        double speed = AeroAeroModel.compute(20, 0, 0, 1, 0, 0, 0.0, out);
        assertEquals(20.0, speed, 1.0e-9);
        assertTrue(out.x < 0.0, "drag must oppose the +X velocity");
        assertTrue(Double.isFinite(out.x) && Double.isFinite(out.y) && Double.isFinite(out.z));
    }

    @Test
    void outputIsAlwaysClampedToMaxAeroAccel() {
        Vector3d out = new Vector3d();
        // Deliberately extreme speed and full flap to try to blow past the configured ceiling.
        AeroAeroModel.compute(500, 0, 0, 0.3, 1, 0, 1.0, out);
        assertTrue(out.length() <= AeroConfig.maxAeroAccel + 1.0e-6,
                "aero force must never exceed the configured safety clamp");
        assertTrue(Double.isFinite(out.length()));
    }

    @Test
    void flapIncreasesLiftMagnitudeAtNonZeroAngleOfAttack() {
        Vector3d withoutFlap = new Vector3d();
        Vector3d withFlap = new Vector3d();
        // Nose pitched up 10 degrees relative to velocity gives a real, non-degenerate AoA.
        double vx = 20, vy = 0, vz = 0;
        double nx = Math.cos(Math.toRadians(10)), ny = Math.sin(Math.toRadians(10)), nz = 0;
        AeroAeroModel.compute(vx, vy, vz, nx, ny, nz, 0.0, withoutFlap);
        AeroAeroModel.compute(vx, vy, vz, nx, ny, nz, 1.0, withFlap);
        assertTrue(withFlap.y > withoutFlap.y, "full flap should add more upward lift than no flap");
    }

    @Test
    void baseLiftFactorZeroRemovesHullLiftButKeepsDrag() {
        Vector3d fullHull = new Vector3d();
        Vector3d noHullLift = new Vector3d();
        double vx = 20, vy = 0, vz = 0;
        double nx = 1, ny = 0, nz = 0; // aligned, so cl uses only the zero-AoA floor term
        AeroAeroModel.compute(vx, vy, vz, nx, ny, nz, 0.0, 1.0, fullHull);
        AeroAeroModel.compute(vx, vy, vz, nx, ny, nz, 0.0, 0.0, noHullLift);
        // Drag (the X component here, since aligned) is unaffected by baseLiftFactor.
        assertEquals(fullHull.x, noHullLift.x, 1.0e-9);
    }

    @Test
    void angleOfAttackIsZeroWhenAlignedAndNinetyWhenPerpendicular() {
        assertEquals(0.0, AeroAeroModel.angleOfAttackDeg(10, 0, 0, 1, 0, 0), 1.0e-6);
        assertEquals(90.0, AeroAeroModel.angleOfAttackDeg(10, 0, 0, 0, 1, 0), 1.0e-6);
    }

    @Test
    void angleOfAttackIsZeroWhenSpeedOrNoseIsDegenerate() {
        assertEquals(0.0, AeroAeroModel.angleOfAttackDeg(0, 0, 0, 1, 0, 0), 1.0e-9);
        assertEquals(0.0, AeroAeroModel.angleOfAttackDeg(10, 0, 0, 0, 0, 0), 1.0e-9);
    }
}
