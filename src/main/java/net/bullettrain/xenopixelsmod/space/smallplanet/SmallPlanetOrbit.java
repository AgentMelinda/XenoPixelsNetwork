package net.bullettrain.xenopixelsmod.space.smallplanet;

import java.util.Objects;

/**
 * Deterministic two-body circular track for a generated planet. The orbit plane passes through the authored
 * star position and the candidate's full three-dimensional seed position; its normal is the one closest to
 * world-up, which makes the small seeded Y offset a small inclination instead of moving the focus off the star.
 *
 * <p>The period follows the same scaled Kepler convention as the chart's authored solar-system fixture
 * ({@code T = 3600 s * (r / 1000 blocks)^1.5}). It is a gameplay scale, not a claim about physical stellar masses.
 * No state accumulates: callers can evaluate the same time on the server and client.
 */
public final class SmallPlanetOrbit {
    private static final double TAU = Math.PI * 2.0;
    private static final double EPSILON = 1.0e-12;

    private final SmallPlanetField.Candidate candidate;
    private final double starX, starY, starZ;
    private final double radius, periodSeconds, epochSeconds;
    private final double ux, uy, uz;
    private final double vx, vy, vz;
    private final double nx, ny, nz;
    private final double yAmplitude;

    private SmallPlanetOrbit(SmallPlanetField.Candidate candidate, double starX, double starY, double starZ,
                             double periodSeconds, double epochSeconds) {
        this.candidate = Objects.requireNonNull(candidate, "candidate");
        if (!finite(starX, starY, starZ, periodSeconds, epochSeconds) || periodSeconds <= 0.0)
            throw new IllegalArgumentException("star position, period and epoch must be finite; period must be positive");
        this.starX = starX; this.starY = starY; this.starZ = starZ;
        this.periodSeconds = periodSeconds; this.epochSeconds = epochSeconds;

        double dx = candidate.x() - starX, dy = candidate.y() - starY, dz = candidate.z() - starZ;
        this.radius = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (!(radius > 0.0) || !Double.isFinite(radius))
            throw new IllegalArgumentException("candidate must have a finite non-zero radius from its star");
        ux = dx / radius; uy = dy / radius; uz = dz / radius;

        // Project world-up onto the plane perpendicular to the radius vector. This chooses the orbital plane
        // closest to horizontal while still making the star the focus and passing exactly through the candidate.
        double projectedX = -ux * uy;
        double projectedY = 1.0 - uy * uy;
        double projectedZ = -uz * uy;
        double normalLength = Math.sqrt(projectedX * projectedX + projectedY * projectedY + projectedZ * projectedZ);
        if (normalLength < EPSILON) {
            // A candidate almost directly above/below its star has no useful projected world-up vector.
            // Project world-X instead to get a deterministic orthogonal normal.
            projectedX = 1.0 - ux * ux;
            projectedY = -ux * uy;
            projectedZ = -ux * uz;
            normalLength = Math.sqrt(projectedX * projectedX + projectedY * projectedY + projectedZ * projectedZ);
        }
        if (!(normalLength > 0.0) || !Double.isFinite(normalLength))
            throw new IllegalArgumentException("could not derive a stable orbital plane");
        nx = projectedX / normalLength; ny = projectedY / normalLength; nz = projectedZ / normalLength;

        // v = u × n. The chart's Kepler-to-Minecraft (x,z,y) mapping reverses the reference handedness,
        // so this direction gives the same negative world-Y angular momentum as the charted prograde orbits.
        double tangentX = uy * nz - uz * ny;
        double tangentY = uz * nx - ux * nz;
        double tangentZ = ux * ny - uy * nx;
        double tangentLength = Math.sqrt(tangentX * tangentX + tangentY * tangentY + tangentZ * tangentZ);
        if (!(tangentLength > 0.0) || !Double.isFinite(tangentLength))
            throw new IllegalArgumentException("could not derive a stable orbital tangent");
        vx = tangentX / tangentLength; vy = tangentY / tangentLength; vz = tangentZ / tangentLength;
        yAmplitude = radius * Math.hypot(uy, vy);
    }

    public static SmallPlanetOrbit fromCandidate(SmallPlanetField.Candidate candidate,
                                                  double starX, double starY, double starZ) {
        Objects.requireNonNull(candidate, "candidate");
        double radius = distance(candidate.x() - starX, candidate.y() - starY, candidate.z() - starZ);
        return new SmallPlanetOrbit(candidate, starX, starY, starZ, scaledPeriodSeconds(radius), 0.0);
    }

    public static SmallPlanetOrbit fromCandidate(SmallPlanetField.Candidate candidate,
                                                  double starX, double starY, double starZ,
                                                  double epochSeconds) {
        Objects.requireNonNull(candidate, "candidate");
        double radius = distance(candidate.x() - starX, candidate.y() - starY, candidate.z() - starZ);
        return new SmallPlanetOrbit(candidate, starX, starY, starZ, scaledPeriodSeconds(radius), epochSeconds);
    }

    public static double scaledPeriodSeconds(double semiMajorAxisBlocks) {
        if (!(semiMajorAxisBlocks > 0.0) || !Double.isFinite(semiMajorAxisBlocks))
            throw new IllegalArgumentException("orbital radius must be finite and positive");
        double period = 3_600.0 * Math.pow(semiMajorAxisBlocks / 1_000.0, 1.5);
        if (!(period > 0.0) || !Double.isFinite(period))
            throw new IllegalArgumentException("orbital radius produces an unsupported period");
        return period;
    }

    /** Writes world-space position and velocity in blocks and blocks/second into caller-owned arrays. */
    public void positionAndVelocityAt(double timeSeconds, double[] position, double[] velocity) {
        requireVector(position, "position");
        requireVector(velocity, "velocity");
        positionAt(timeSeconds, position);
        if (!Double.isFinite(timeSeconds)) throw new IllegalArgumentException("time must be finite");
        double elapsed = timeSeconds - epochSeconds;
        if (!Double.isFinite(elapsed)) throw new IllegalArgumentException("time delta must be finite");
        double phase = TAU * ((elapsed % periodSeconds) / periodSeconds);
        if (phase < 0.0) phase += TAU;
        double sin = Math.sin(phase), cos = Math.cos(phase);
        double angularSpeed = TAU / periodSeconds;
        double scale = radius * angularSpeed;
        velocity[0] = scale * (-ux * sin + vx * cos);
        velocity[1] = scale * (-uy * sin + vy * cos);
        velocity[2] = scale * (-uz * sin + vz * cos);
    }

    /** Position-only path for client rendering and proximity tests; caller owns the three-value output buffer. */
    public void positionAt(double timeSeconds, double[] position) {
        requireVector(position, "position");
        if (!Double.isFinite(timeSeconds)) throw new IllegalArgumentException("time must be finite");
        double elapsed = timeSeconds - epochSeconds;
        if (!Double.isFinite(elapsed)) throw new IllegalArgumentException("time delta must be finite");
        double phase = TAU * ((elapsed % periodSeconds) / periodSeconds);
        if (phase < 0.0) phase += TAU;
        if (phase == 0.0) {
            position[0] = candidate.x(); position[1] = candidate.y(); position[2] = candidate.z();
            return;
        }
        double sin = Math.sin(phase), cos = Math.cos(phase);
        position[0] = starX + radius * (ux * cos + vx * sin);
        position[1] = starY + radius * (uy * cos + vy * sin);
        position[2] = starZ + radius * (uz * cos + vz * sin);
    }

    public double epochSeconds() { return epochSeconds; }
    public double periodSeconds() { return periodSeconds; }
    public double radius() { return radius; }
    /** Conservative query: the complete orbit lies inside this sphere centred on the actual star focus. */
    public boolean mayEnterRange(double x, double y, double z, double reach) {
        if (!finite(x, y, z, reach) || reach < 0.0) return false;
        double dx = starX - x, dy = starY - y, dz = starZ - z;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        return Double.isFinite(distance) && distance - radius <= reach;
    }
    public double minimumY() { return starY - yAmplitude; }
    public double maximumY() { return starY + yAmplitude; }
    /** Whether the complete closed orbit remains in the supplied vertical world interval. */
    public boolean staysWithinY(double minY, double maxY) {
        return !Double.isNaN(minY) && !Double.isNaN(maxY) && minY <= maxY
                && minimumY() >= minY && maximumY() <= maxY;
    }
    public double[] planeNormal() { return new double[]{nx, ny, nz}; }

    private static double distance(double x, double y, double z) { return Math.sqrt(x*x + y*y + z*z); }
    private static boolean finite(double... values) {
        for (double value : values) if (!Double.isFinite(value)) return false;
        return true;
    }
    private static void requireVector(double[] value, String label) {
        if (value == null || value.length < 3) throw new IllegalArgumentException(label + " output must have at least 3 elements");
    }
}
