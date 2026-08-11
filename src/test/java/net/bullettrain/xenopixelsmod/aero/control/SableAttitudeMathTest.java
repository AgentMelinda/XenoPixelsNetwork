package net.bullettrain.xenopixelsmod.aero.control;

import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SableAttitudeMathTest {
    private static final Vector3d BODY_NOSE = new Vector3d(0, 1, 0);
    private static final Vector3d BODY_UP = new Vector3d(0, 0, 1);

    @Test
    void yawZeroPointsCalibratedNoseNorth() {
        Quaterniond desired = SableAttitudeMath.desiredOrientation(0, 0, 0, BODY_NOSE, BODY_UP);
        Vector3d nose = desired.transform(new Vector3d(BODY_NOSE));
        assertEquals(0.0, nose.x, 1.0e-8);
        assertEquals(0.0, nose.y, 1.0e-8);
        assertEquals(-1.0, nose.z, 1.0e-8);
    }

    @Test
    void yawNinetyPointsEastAndPitchRaisesNose() {
        Quaterniond desired = SableAttitudeMath.desiredOrientation(90, 30, 0, BODY_NOSE, BODY_UP);
        Vector3d nose = desired.transform(new Vector3d(BODY_NOSE));
        assertEquals(Math.cos(Math.toRadians(30)), nose.x, 1.0e-8);
        assertEquals(0.5, nose.y, 1.0e-8);
        assertEquals(0.0, nose.z, 1.0e-8);
    }

    @Test
    void quaternionErrorUsesShortestRotation() {
        Quaterniond desired = new Quaterniond().rotateY(Math.toRadians(350));
        Vector3d error = SableAttitudeMath.errorVectorWorld(new Quaterniond(), desired, new Vector3d());
        assertEquals(Math.toRadians(10), error.length(), 1.0e-8);
    }

    @Test
    void directorBrakesAndHoversAtFinalTarget() {
        List<Vector3d> route = List.of(new Vector3d(0, 0, 0));
        AeroFlightDirector.Command approach = AeroFlightDirector.guide(
                new Vector3d(-3, 0, 0), new Vector3d(20, 0, 0), route, 0, 10);
        assertTrue(approach.accelerationWorld().x < 0, "overspeed near target must brake");

        AeroFlightDirector.Command arrived = AeroFlightDirector.guide(
                new Vector3d(), new Vector3d(), route, 0, 10);
        assertTrue(arrived.arrived());
        assertEquals(10.0, arrived.accelerationWorld().y, 1.0e-8,
                "arrival hold must retain gravity compensation");
    }

    @Test
    void routeProgressNeverMovesBehindPreviousWaypoint() {
        List<Vector3d> route = List.of(new Vector3d(0, 0, 0), new Vector3d(20, 0, 0),
                new Vector3d(40, 0, 0));
        AeroFlightDirector.Command command = AeroFlightDirector.guide(
                new Vector3d(2, 0, 0), new Vector3d(), route, 1, 10);
        assertTrue(command.waypointIndex() >= 1);
    }

    /**
     * Two identical consecutive waypoints are reachable through the planner UI, and the turn
     * term used to normalize their zero-length difference — producing NaN that propagated all
     * the way into the commanded acceleration.
     */
    @Test
    void duplicateWaypointsDoNotProduceNaN() {
        List<Vector3d> route = List.of(new Vector3d(50, 0, 0), new Vector3d(50, 0, 0),
                new Vector3d(90, 0, 0));
        AeroFlightDirector.Command command = AeroFlightDirector.guide(
                new Vector3d(), new Vector3d(), route, 0, 10);
        Vector3d acceleration = command.accelerationWorld();
        assertTrue(Double.isFinite(acceleration.x) && Double.isFinite(acceleration.y)
                        && Double.isFinite(acceleration.z),
                "duplicate waypoints must not poison the acceleration command");
        assertTrue(Double.isFinite(command.yawDeg()) && Double.isFinite(command.pitchDeg()));
    }

    /**
     * Body calibration alone cannot define roll.
     *
     * <p>Base, centre and nose are three points along the hull axis, and the default calibration
     * is literally {@code pos.below()}, {@code pos}, {@code pos.above()}. Projecting
     * {@code centre - base} perpendicular to the nose collapses to zero, so the "up" it produces
     * is an arbitrary fallback axis rather than anything derived from the ship — which is how a
     * hull ends up holding pitch and yaw correctly while sitting at the wrong roll.
     */
    @Test
    void collinearCalibrationCannotDefineRoll() {
        Vector3d nose = SableAttitudeMath.calibratedNose(0, -1, 0, 0, 1, 0);
        assertEquals(0.0, nose.x, 1.0e-9);
        assertEquals(1.0, nose.y, 1.0e-9);

        // centre - base is (0,1,0): exactly the nose axis, so it carries no roll information.
        Vector3d degenerate = SableAttitudeMath.calibratedUp(nose, 0, -1, 0, 0, 0, 0);
        assertEquals(0.0, degenerate.dot(nose), 1.0e-9, "any up must be perpendicular to the nose");
        assertEquals(1.0, Math.abs(degenerate.x), 1.0e-9,
                "collinear input falls back to the arbitrary X axis, not a ship-derived up");

        // A real ship-local direction — the controller block's FACING — does define roll.
        Vector3d fromFacing = SableAttitudeMath.calibratedUp(nose, 0, 0, 0, 0, 0, -1);
        assertEquals(0.0, fromFacing.dot(nose), 1.0e-9);
        assertEquals(-1.0, fromFacing.z, 1.0e-9, "north-facing controller gives -Z as hull up");
    }

    /**
     * Gravity compensation used to be clamped at zero, so a negative-gravity body — orbital or
     * anti-grav, which pulls the hull upward — received no correction at all rather than the
     * downward correction it needs.
     */
    @Test
    void negativeGravityIsCompensatedDownward() {
        List<Vector3d> route = List.of(new Vector3d(0, 0, 0));
        AeroFlightDirector.Command hovering = AeroFlightDirector.guide(
                new Vector3d(), new Vector3d(), route, 0, -10);
        assertEquals(-10.0, hovering.accelerationWorld().y, 1.0e-8,
                "an upward pull must be cancelled downward, not ignored");
    }

    /**
     * Braking read the straight-line distance to the final target, so a route that loops back
     * near its own start looked nearly finished while most of the path was still ahead, and the
     * ship crawled the rest of the way.
     */
    @Test
    void brakingUsesRemainingPathNotStraightLineDistance() {
        // Out to (60,0,0), then back to (2,0,0): three blocks away in a straight line, but
        // roughly 118 blocks of flying left.
        List<Vector3d> looping = List.of(new Vector3d(60, 0, 0), new Vector3d(2, 0, 0));
        AeroFlightDirector.Command command = AeroFlightDirector.guide(
                new Vector3d(-1, 0, 0), new Vector3d(), looping, 0, 10);
        // The commanded acceleration is the desired velocity scaled by the director's 1.15 gain,
        // the ship being at rest. Straight-line braking over three blocks yields about 2 b/s
        // after the hairpin's turn factor; the remaining 118 blocks of path allow about 9.8.
        double commandedSpeed = command.accelerationWorld().x / 1.15;
        assertTrue(commandedSpeed > 6.0,
                "a long remaining path must not be braked as if the target were three blocks away,"
                        + " commanded " + commandedSpeed);
    }

    /**
     * The vertical-nose fallback picked an axis perpendicular to the limit the non-degenerate
     * branch approaches, so the roll reference jumped 90 degrees as the nose passed through
     * vertical. Approaching straight up must converge on the same attitude as reaching it.
     */
    @Test
    void rollReferenceIsContinuousThroughVertical() {
        Quaterniond nearVertical =
                SableAttitudeMath.desiredOrientation(40, 89.999, 0, BODY_NOSE, BODY_UP);
        Quaterniond vertical = SableAttitudeMath.desiredOrientation(40, 90, 0, BODY_NOSE, BODY_UP);
        Vector3d error = SableAttitudeMath.errorVectorWorld(nearVertical, vertical, new Vector3d());
        assertTrue(error.length() < Math.toRadians(1.0),
                "attitude must not jump at vertical, jumped " + Math.toDegrees(error.length()));

        Quaterniond nearInverted =
                SableAttitudeMath.desiredOrientation(40, -89.999, 0, BODY_NOSE, BODY_UP);
        Quaterniond inverted = SableAttitudeMath.desiredOrientation(40, -90, 0, BODY_NOSE, BODY_UP);
        Vector3d downError =
                SableAttitudeMath.errorVectorWorld(nearInverted, inverted, new Vector3d());
        assertTrue(downError.length() < Math.toRadians(1.0),
                "same at nose-down vertical, jumped " + Math.toDegrees(downError.length()));
    }

    /**
     * Hovering on the target leaves no direction to derive a heading from. The director must
     * say so rather than reporting yaw 0, which would snap the hull round to north on arrival.
     */
    @Test
    void arrivalReportsHeadingAsUnusable() {
        List<Vector3d> route = List.of(new Vector3d(0, 0, 0));
        AeroFlightDirector.Command arrived = AeroFlightDirector.guide(
                new Vector3d(), new Vector3d(), route, 0, 10);
        assertTrue(arrived.arrived());
        assertTrue(!arrived.headingValid(), "no heading is derivable while sitting on the target");

        AeroFlightDirector.Command enRoute = AeroFlightDirector.guide(
                new Vector3d(0, 0, 40), new Vector3d(), route, 0, 10);
        assertTrue(enRoute.headingValid());
        assertEquals(0.0, enRoute.yawDeg(), 1.0e-6, "target due north of the ship reads yaw 0");
    }
}
