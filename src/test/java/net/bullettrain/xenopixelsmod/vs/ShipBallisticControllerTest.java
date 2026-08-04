package net.bullettrain.xenopixelsmod.vs;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShipBallisticControllerTest {
    @Test
    void sweptInterceptCatchesTargetSkippedBetweenTicks() {
        double distanceSquared = ShipBallisticController.segmentDistanceSquared(
                0, 100, 0, 120, 100, 0, 60, 103, 0);
        assertEquals(9.0, distanceSquared, 1.0e-9);
        assertTrue(distanceSquared < 8.0 * 8.0);
    }

    @Test
    void sweptInterceptDoesNotHitDistantTarget() {
        double distanceSquared = ShipBallisticController.segmentDistanceSquared(
                0, 100, 0, 120, 100, 0, 60, 150, 0);
        assertEquals(2500.0, distanceSquared, 1.0e-9);
    }

    @Test
    void sweptInterceptCatchesDiagonalHighSpeedCrossing() {
        double distanceSquared = ShipBallisticController.segmentDistanceSquared(
                -80, 130, -40, 90, 70, 45, 0, 100, 0);
        assertTrue(distanceSquared < 8.0 * 8.0);
    }

    @Test
    void oppositeAttitudeCommandSlewsInsteadOfFlipping() {
        ShipBallisticController.AttitudeCommand from =
                new ShipBallisticController.AttitudeCommand(0, 1, 0);
        ShipBallisticController.AttitudeCommand next = ShipBallisticController.slewDirection(
                from, 0, -1, 0, Math.toRadians(7));
        double length = Math.sqrt(next.x() * next.x() + next.y() * next.y() + next.z() * next.z());
        assertEquals(1.0, length, 1.0e-9);
        assertEquals(Math.cos(Math.toRadians(7)), next.y(), 1.0e-9);
        assertTrue(next.y() > 0.0, "one guidance tick must not reverse the missile nose");
    }

    @Test
    void attitudeSlewReachesNearbyDirectionWithoutNumericalError() {
        ShipBallisticController.AttitudeCommand from =
                new ShipBallisticController.AttitudeCommand(0, 1, 0);
        double angle = Math.toRadians(3);
        ShipBallisticController.AttitudeCommand next = ShipBallisticController.slewDirection(
                from, Math.sin(angle), Math.cos(angle), 0, Math.toRadians(7));
        assertEquals(Math.sin(angle), next.x(), 1.0e-9);
        assertEquals(Math.cos(angle), next.y(), 1.0e-9);
        assertEquals(0.0, next.z(), 1.0e-9);
    }

    @Test
    void terminalGuidancePreservesSmallCorrectionMagnitude() {
        ShipBallisticController.AccelerationCommand command =
                ShipBallisticController.terminalAcceleration(100, 0, 0, 52, 0, 0, 20);
        assertTrue(command.magnitude() < 2.0,
                "matching the desired closing velocity must not command full thrust");
    }

    @Test
    void terminalGuidanceCorrectsCrossTrackVelocity() {
        ShipBallisticController.AccelerationCommand command =
                ShipBallisticController.terminalAcceleration(100, 0, 0, 30, 12, -8, 20);
        assertTrue(command.y() < 0.0, "positive lateral velocity needs negative correction");
        assertTrue(command.z() > 0.0, "negative lateral velocity needs positive correction");
        assertTrue(command.magnitude() <= 20.0 + 1.0e-9);
    }

    @Test
    void terminalGuidanceConvergesWithGravityAndCrossTrackError() {
        double x = -400, y = 180, z = 120;
        double vx = 70, vy = -5, vz = 18;
        double dt = 0.05;
        boolean intercepted = false;
        for (int step = 0; step < 600 && !intercepted; step++) {
            double previousX = x, previousY = y, previousZ = z;
            ShipBallisticController.AccelerationCommand command =
                    ShipBallisticController.terminalAcceleration(-x, -y, -z, vx, vy, vz, 20);
            vx += command.x() * dt;
            vy += (command.y() - 9.81) * dt;
            vz += command.z() * dt;
            x += vx * dt;
            y += vy * dt;
            z += vz * dt;
            intercepted = ShipBallisticController.segmentDistanceSquared(
                    previousX, previousY, previousZ, x, y, z, 0, 0, 0) <= 8 * 8;
        }
        assertTrue(intercepted, "terminal controller should cross the target fuse radius");
    }
}
