package net.bullettrain.xenopixelsmod.vs;

import org.joml.Vector3dc;

/** Tracks consecutive physics poses independently of whether guidance is currently allowed to run. */
final class BallisticPhysicsSweep {
    private double previousX, previousY, previousZ;
    private boolean initialized;

    /** Returns whether the segment from the prior sampled pose to {@code current} crosses the target. */
    boolean crossed(Vector3dc current, double targetX, double targetY, double targetZ, double radius) {
        boolean crossed = initialized && ShipBallisticController.segmentDistanceSquared(
                previousX, previousY, previousZ, current.x(), current.y(), current.z(),
                targetX, targetY, targetZ) <= radius * radius;
        sample(current);
        return crossed;
    }

    /** Record a pose while physics guidance is suppressed, preventing a later stale long-chord sweep. */
    void sample(Vector3dc position) {
        previousX = position.x();
        previousY = position.y();
        previousZ = position.z();
        initialized = true;
    }

    void reset(double x, double y, double z) {
        previousX = x;
        previousY = y;
        previousZ = z;
        initialized = true;
    }
}
