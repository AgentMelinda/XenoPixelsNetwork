package net.bullettrain.xenopixelsmod.space.dragonball;

/**
 * Pure geometry of a planet-sized Super Dragon Ball (design-doc 16, planet form), shared by the voxel body and the
 * client's smooth sphere so both show the same stars. A ball of radius R is a hollow shell (thickness configurable);
 * the stars are five-pointed, crimson, and laid out on the +Z face, one per ball number, in the same arrangement as the
 * held items. Coordinates are in units of the ball radius: (u, v) is the orthographic projection of a unit-sphere point
 * onto the face plane.
 */
public final class DragonBallPlanetShape {
    /** Star centres per star count, (u, v) in radius units on the +Z face. */
    private static final double[][][] LAYOUT = {
            {{0, 0}},
            {{-0.26, 0}, {0.26, 0}},
            {{0, 0.24}, {-0.24, -0.18}, {0.24, -0.18}},
            {{-0.24, 0.24}, {0.24, 0.24}, {-0.24, -0.24}, {0.24, -0.24}},
            {{0, 0.34}, {-0.33, 0.08}, {0.33, 0.08}, {-0.21, -0.3}, {0.21, -0.3}},
            {{-0.22, 0.34}, {0.22, 0.34}, {-0.38, 0}, {0.38, 0}, {-0.22, -0.34}, {0.22, -0.34}},
            {{0, 0.44}, {-0.36, 0.22}, {0.36, 0.22}, {0, 0}, {-0.36, -0.22}, {0.36, -0.22}, {0, -0.44}},
    };
    private static final double INNER = 0.42;

    private DragonBallPlanetShape() {}

    /** Outer star radius in ball-radius units; smaller when more stars share the face. */
    public static double starRadius(int stars) { return stars <= 1 ? 0.24 : stars <= 4 ? 0.17 : 0.14; }

    /** Does the unit-sphere direction (x, y, z) fall inside one of {@code stars} stars? */
    public static boolean inStar(int stars, double x, double y, double z) {
        if (z <= 0.2 || stars < 1 || stars > LAYOUT.length) return false;
        double size = starRadius(stars);
        for (double[] centre : LAYOUT[stars - 1]) if (inFivePointStar(x - centre[0], y - centre[1], size)) return true;
        return false;
    }

    /** Five-pointed star, one point up, outer radius {@code size}, inner radius {@code size * INNER}. */
    static boolean inFivePointStar(double du, double dv, double size) {
        double r = Math.hypot(du, dv);
        if (r > size) return false;
        if (r < size * INNER * 0.81) return true;
        // Angle from the top point, folded into one 72-degree sector and mirrored into its first half.
        double angle = Math.atan2(du, dv);
        double sector = Math.PI * 2 / 5;
        double a = Math.abs(((angle % sector) + sector) % sector);
        if (a > sector / 2) a = sector - a;
        // Edge from the outer point (angle 0, radius size) to the inner vertex (angle sector/2, radius size*INNER).
        double px = 0, py = size, qx = Math.sin(sector / 2) * size * INNER, qy = Math.cos(sector / 2) * size * INNER;
        double sx = Math.sin(a) * r, sy = Math.cos(a) * r;
        // Inside when on the centre side of the edge line.
        double cross = (qx - px) * (sy - py) - (qy - py) * (sx - px);
        return cross <= 0;
    }

    /** Voxel classification for a ball of {@code radius} blocks and {@code thickness}: 0 empty, 1 shell, 2 star inlay. */
    public static int voxel(int stars, int radius, int thickness, int x, int y, int z) {
        double d2 = (double) x * x + (double) y * y + (double) z * z;
        double outer = radius + 0.5, inner = radius + 0.5 - thickness;
        if (d2 > outer * outer || (thickness < radius && d2 < inner * inner)) return 0;
        double d = Math.sqrt(d2);
        if (d < 1e-9) return 1;
        return inStar(stars, x / d, y / d, z / d) ? 2 : 1;
    }

    /** Half-size of the voxel cube that contains the ball. */
    public static int extent(int radius) { return radius + 1; }
}
