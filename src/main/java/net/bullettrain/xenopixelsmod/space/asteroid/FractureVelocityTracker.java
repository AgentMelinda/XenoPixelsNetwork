package net.bullettrain.xenopixelsmod.space.asteroid;

/** Measures a sublevel's sampled linear velocity and the change since the prior server-tick sample. */
final class FractureVelocityTracker {
    private boolean initialized;
    private boolean velocityInitialized;
    private double x, y, z;
    private long tick;
    private double vx, vy, vz;

    /**
     * Samples position in blocks. Game time is in 20 Hz ticks; the returned velocity is blocks/second.
     * The first sample establishes a baseline and does not report an impact.
     */
    Sample sample(double nextX, double nextY, double nextZ, long gameTick) {
        if (!Double.isFinite(nextX) || !Double.isFinite(nextY) || !Double.isFinite(nextZ)) {
            initialized = false;
            return null;
        }
        if (!initialized || gameTick <= tick) {
            x = nextX; y = nextY; z = nextZ; tick = gameTick;
            vx = vy = vz = 0.0;
            initialized = true;
            velocityInitialized = false;
            return null;
        }

        double seconds = (gameTick - tick) / 20.0;
        double nextVx = (nextX - x) / seconds;
        double nextVy = (nextY - y) / seconds;
        double nextVz = (nextZ - z) / seconds;
        double dvx = velocityInitialized ? nextVx - vx : 0.0;
        double dvy = velocityInitialized ? nextVy - vy : 0.0;
        double dvz = velocityInitialized ? nextVz - vz : 0.0;
        if (!Double.isFinite(nextVx) || !Double.isFinite(nextVy) || !Double.isFinite(nextVz)
                || !Double.isFinite(dvx) || !Double.isFinite(dvy) || !Double.isFinite(dvz)) {
            initialized = false;
            return null;
        }
        x = nextX; y = nextY; z = nextZ; tick = gameTick;
        vx = nextVx; vy = nextVy; vz = nextVz;
        velocityInitialized = true;
        return new Sample(nextVx, nextVy, nextVz, dvx, dvy, dvz);
    }

    record Sample(double velocityX, double velocityY, double velocityZ,
                  double deltaVX, double deltaVY, double deltaVZ) {
        double deltaV() { return Math.hypot(Math.hypot(deltaVX, deltaVY), deltaVZ); }
        boolean exceeds(double threshold) {
            return Double.isFinite(threshold) && threshold >= 0.0 && deltaV() > threshold;
        }
    }
}
