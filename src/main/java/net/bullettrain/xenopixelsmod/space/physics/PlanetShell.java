package net.bullettrain.xenopixelsmod.space.physics;

/** Outer voxels touching exterior through a face, edge or corner; the hollow shell stays face-connected. */
public final class PlanetShell {
    private PlanetShell() {}

    public static boolean contains(int x, int y, int z, int radius) {
        if (radius < 1 || radius > 400) throw new IllegalArgumentException("radius must be 1..400");
        if (x < -radius || x > radius || y < -radius || y > radius || z < -radius || z > radius) return false;
        long xx = (long) x * x, yy = (long) y * y, zz = (long) z * z;
        long r2 = (long) radius * radius;
        if (xx + yy + zz > r2) return false;
        // Erode by the 26-neighbour cube. A face-only erosion strands the six axis extremities.
        return square(Math.abs((long) x) + 1) + square(Math.abs((long) y) + 1)
                + square(Math.abs((long) z) + 1) > r2;
    }

    private static long square(long value) { return value * value; }

    /** Kepler's XY reference plane becomes Minecraft's horizontal XZ plane. Reuses caller scratch. */
    public static double[] orbitSeed(net.bullettrain.xenopixelsmod.space.math.OrbitalElements elements,
                                     double timeSeconds,double[] out) {
        net.bullettrain.xenopixelsmod.space.math.KeplerSolver.velocity(elements,timeSeconds,out);
        double y=out[1];
        out[1]=out[2];
        out[2]=y;
        return out;
    }

    /** Gravitational parameter matching the authored period, independent of ship gravity. */
    public static double orbitMu(double semiMajorAxis, double periodSeconds) {
        if (!(semiMajorAxis > 0) || !(periodSeconds > 0)
                || !Double.isFinite(semiMajorAxis) || !Double.isFinite(periodSeconds)) return 0;
        double n = 2 * Math.PI / periodSeconds;
        return n * n * semiMajorAxis * semiMajorAxis * semiMajorAxis;
    }
}
