package net.bullettrain.xenopixelsmod.aero.control;

import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.List;

/** Sable-inspired route lookahead and arrival controller, expressed entirely in world space. */
public final class AeroFlightDirector {
    public static final double MAX_SPEED = 28.0;
    public static final double MAX_ACCEL = 10.0;
    private static final double BRAKING_ACCEL = 7.0;
    private static final double LOOKAHEAD = 18.0;
    private static final double ARRIVAL_RADIUS = 1.5;
    /** Horizontal aim distance below which no meaningful heading can be derived. */
    private static final double HEADING_EPSILON = 0.05;

    private AeroFlightDirector() {}

    /**
     * @param headingValid false when the aim point is too close to resolve a direction from —
     *                     on arrival, or hovering on top of a waypoint. Callers must keep their
     *                     previous heading in that case; taking the computed yaw anyway would
     *                     snap the hull to north the moment it reaches its target.
     */
    public record Command(Vector3d accelerationWorld, double yawDeg, double pitchDeg,
                          int waypointIndex, double distance, double speed, boolean arrived,
                          boolean headingValid, String status) {}

    public static Command guide(Vector3dc position, Vector3dc velocity, List<Vector3d> route,
                                int previousWaypoint, double gravity) {
        if (route == null || route.isEmpty()) {
            return new Command(new Vector3d(), 0, 0, 0, 0, velocity.length(), false, false,
                    "no target");
        }
        int last = route.size() - 1;
        int index = Math.max(0, Math.min(previousWaypoint, last));
        Vector3d finalTarget = route.get(last);

        while (index < last && new Vector3d(route.get(index)).sub(position).length() < 5.0) index++;

        Vector3d aim = lookAhead(position, route, index, LOOKAHEAD);
        Vector3d toFinal = new Vector3d(finalTarget).sub(position);
        double distance = toFinal.length();
        double speed = velocity.length();
        boolean arrived = distance <= ARRIVAL_RADIUS && speed <= 0.75;

        // Brake against the distance actually left to fly, not the straight line to the final
        // target: a route that loops back near its own start reads as "nearly there" by straight
        // line while most of the path is still ahead, and the ship crawls the rest of it.
        double pathRemaining = remainingPathLength(position, route, index);
        double stoppingSpeed =
                Math.sqrt(Math.max(0.0, 2.0 * BRAKING_ACCEL * Math.max(0, pathRemaining - 0.5)));
        double desiredSpeed = Math.min(MAX_SPEED, stoppingSpeed);
        if (index < last) {
            Vector3d incoming = new Vector3d(route.get(index)).sub(position);
            Vector3d outgoing = new Vector3d(route.get(index + 1)).sub(route.get(index));
            // Normalizing a zero-length vector yields NaN, which would poison desiredSpeed and
            // from there the whole acceleration command. Two identical consecutive waypoints
            // are entirely reachable through the planner UI, so guard rather than assume.
            if (incoming.lengthSquared() > 1.0e-8 && outgoing.lengthSquared() > 1.0e-8) {
                double turn = incoming.normalize().dot(outgoing.normalize());
                desiredSpeed *= 0.35 + 0.65 * Math.sqrt(Math.max(0.0, (1.0 + turn) * 0.5));
            }
        }

        Vector3d desiredVelocity = new Vector3d(aim).sub(position);
        if (desiredVelocity.lengthSquared() > 1.0e-8) desiredVelocity.normalize().mul(desiredSpeed);
        if (distance < 8.0 || index == last) {
            desiredVelocity.set(toFinal);
            if (desiredVelocity.lengthSquared() > 1.0e-8) desiredVelocity.normalize().mul(desiredSpeed);
        }

        Vector3d acceleration = desiredVelocity.sub(velocity).mul(1.15);
        // Signed, so a negative-gravity body (orbital or anti-grav) that pulls the hull upward
        // gets the downward correction it needs instead of no compensation at all. The clamp
        // uses the magnitude for the same reason.
        acceleration.y += gravity;
        clampLength(acceleration, MAX_ACCEL + Math.abs(gravity));

        Vector3d headingAim = new Vector3d(aim).sub(position);
        double horizontal = Math.hypot(headingAim.x, headingAim.z);
        // Sitting on the aim point gives no direction to point at. Report the heading as
        // unusable so the caller holds its last one instead of yawing to north on arrival.
        boolean headingValid = horizontal >= HEADING_EPSILON;
        double yaw = headingValid ? Math.toDegrees(Math.atan2(headingAim.x, -headingAim.z)) : 0.0;
        double pitch = headingValid
                ? Math.max(-65.0, Math.min(65.0, Math.toDegrees(Math.atan2(headingAim.y, horizontal))))
                : 0.0;

        String status = arrived ? "arrived — holding position"
                : index < last ? "route waypoint " + (index + 1) + "/" + route.size()
                : "approaching target";
        return new Command(acceleration, yaw, pitch, index, distance, speed, arrived,
                headingValid, status);
    }

    private static Vector3d lookAhead(Vector3dc position, List<Vector3d> route, int index, double distance) {
        Vector3d cursor = new Vector3d(position);
        double remaining = distance;
        for (int i = index; i < route.size(); i++) {
            Vector3d end = route.get(i);
            Vector3d segment = new Vector3d(end).sub(cursor);
            double length = segment.length();
            if (length >= remaining && length > 1.0e-8) return cursor.add(segment.mul(remaining / length));
            remaining -= length;
            cursor.set(end);
        }
        return new Vector3d(route.get(route.size() - 1));
    }

    /**
     * Distance still to fly: the leg to the current waypoint plus every remaining leg. Equal to
     * the straight-line distance for a single-point route, and strictly larger for any route that
     * turns, which is exactly the case straight-line braking gets wrong.
     */
    private static double remainingPathLength(Vector3dc position, List<Vector3d> route, int index) {
        double total = new Vector3d(route.get(index)).sub(position).length();
        for (int i = index; i < route.size() - 1; i++) {
            total += new Vector3d(route.get(i + 1)).sub(route.get(i)).length();
        }
        return total;
    }

    private static void clampLength(Vector3d value, double maximum) {
        double length = value.length();
        if (length > maximum && length > 1.0e-8) value.mul(maximum / length);
    }
}
