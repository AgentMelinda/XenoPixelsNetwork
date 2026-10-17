package net.bullettrain.xenopixelsmod.space.math;

/** Fixed-size, opaque unit spheres for the visual-only orbit sky. No Minecraft or GPU types. */
public final class PlanetSkyMesh {
    public enum Style { EARTH, MOON, NAMEK }
    public record Mesh(float[] positions, int[] colors) {}
    private static final int BANDS = 24, SEGMENTS = 64;
    private PlanetSkyMesh() {}

    public static Mesh create(Style style) {
        int count = BANDS * SEGMENTS * 6;
        float[] positions = new float[count * 3];
        int[] colors = new int[count];
        int vertex = 0;
        for (int band = 0; band < BANDS; band++) {
            double t0 = Math.PI * band / BANDS, t1 = Math.PI * (band + 1) / BANDS;
            for (int segment = 0; segment < SEGMENTS; segment++) {
                double p0 = Math.PI * 2 * segment / SEGMENTS, p1 = Math.PI * 2 * (segment + 1) / SEGMENTS;
                vertex = put(positions, colors, vertex, t0, p0, style);
                vertex = put(positions, colors, vertex, t0, p1, style);
                vertex = put(positions, colors, vertex, t1, p1, style);
                vertex = put(positions, colors, vertex, t0, p0, style);
                vertex = put(positions, colors, vertex, t1, p1, style);
                vertex = put(positions, colors, vertex, t1, p0, style);
            }
        }
        return new Mesh(positions, colors);
    }

    private static int put(float[] positions, int[] colors, int vertex, double theta, double phi, Style style) {
        double x = Math.sin(theta) * Math.cos(phi), y = Math.cos(theta), z = Math.sin(theta) * Math.sin(phi);
        positions[vertex * 3] = (float)x; positions[vertex * 3 + 1] = (float)y; positions[vertex * 3 + 2] = (float)z;
        double shade = .18 + .82 * Math.max(0, (-.5*x + .65*y - .55*z) / Math.sqrt(.975));
        double land = Math.sin(8*x + 2*z) * Math.cos(6*z - 3*y) + .35 * Math.sin(11*y + 4*x);
        int r, g, b;
        if (style == Style.MOON) {
            double crater = Math.sin(19*x) * Math.sin(17*y) * Math.sin(23*z) > .6 ? .65 : 1;
            r = (int)(185*crater); g = (int)(185*crater); b = (int)(180*crater);
        } else if (style == Style.EARTH && Math.abs(y) > .88) {
            r=215; g=228; b=235;
        } else if (style == Style.EARTH) {
            r=land>.1 ? 62 : 24; g=land>.1 ? 136 : 80; b=land>.1 ? 70 : 170;
        } else {
            r=land>0 ? 126 : 32; g=land>0 ? 180 : 90; b=land>0 ? 69 : 85;
        }
        colors[vertex] = 0xff000000 | ((int)(r*shade) << 16) | ((int)(g*shade) << 8) | (int)(b*shade);
        return vertex + 1;
    }
}
