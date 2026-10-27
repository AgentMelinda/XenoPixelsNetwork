package net.bullettrain.xenopixelsmod.client.space;

import java.util.SplittableRandom;

/** Pure, fixed-seed equirectangular crack mask used by the shared damage-overlay texture. */
final class PlanetIntegrityCrackPattern {
    static final int WIDTH = 1024;
    static final int HEIGHT = 512;
    private static final long SEED = 0x58A11C0FFEE21L;

    private PlanetIntegrityCrackPattern() {}

    /** Returns ARGB grayscale pixels: a dark fracture halo with a bright center, transparent elsewhere. */
    static int[] create() { return create(false); }

    static int[] create(boolean critical) {
        int[] pixels = new int[WIDTH * HEIGHT];
        var random = new SplittableRandom(SEED ^ (critical ? 0xC8171CA1L : 0L));
        int roots = critical ? 34 : 16;
        for (int root = 0; root < roots; root++) {
            double x = random.nextDouble(WIDTH);
            double y = random.nextDouble(HEIGHT * 0.18, HEIGHT * 0.82);
            double angle = random.nextDouble(0, Math.PI * 2);
            int steps = critical ? random.nextInt(10, 20) : random.nextInt(8, 16);
            for (int step = 0; step < steps; step++) {
                double stride = critical ? random.nextDouble(5.0, 11.0) : random.nextDouble(5.0, 10.0);
                double nextX = x + Math.cos(angle) * stride;
                double nextY = y + Math.sin(angle) * stride;
                drawStroke(pixels, x, y, nextX, nextY);
                if (random.nextDouble() < (critical ? 0.15 : 0.075)) {
                    double branchAngle = angle + random.nextDouble(-1.4, 1.4);
                    drawBranch(pixels, random, nextX, nextY, branchAngle);
                }
                x = nextX;
                y = Math.max(3, Math.min(HEIGHT - 4, nextY));
                angle += random.nextDouble(-0.72, 0.72);
            }
        }
        return pixels;
    }

    static double rotationRadians(String bodyId) {
        long hash = Integer.toUnsignedLong(bodyId == null ? 0 : bodyId.hashCode());
        return hash * (Math.PI * 2.0 / 4_294_967_296.0);
    }

    private static void drawBranch(int[] pixels, SplittableRandom random, double x, double y, double angle) {
        int steps = random.nextInt(3, 7);
        for (int step = 0; step < steps; step++) {
            double stride = random.nextDouble(4.0, 8.0);
            double nextX = x + Math.cos(angle) * stride;
            double nextY = y + Math.sin(angle) * stride;
            drawStroke(pixels, x, y, nextX, nextY);
            x = nextX;
            y = Math.max(3, Math.min(HEIGHT - 4, nextY));
            angle += random.nextDouble(-0.9, 0.9);
        }
    }

    private static void drawStroke(int[] pixels, double x0, double y0, double x1, double y1) {
        double dx = x1 - x0;
        if (dx > WIDTH / 2.0) dx -= WIDTH;
        else if (dx < -WIDTH / 2.0) dx += WIDTH;
        double dy = y1 - y0;
        int samples = Math.max(1, (int) Math.ceil(Math.max(Math.abs(dx), Math.abs(dy)) * 1.5));
        for (int sample = 0; sample <= samples; sample++) {
            double t = sample / (double) samples;
            int x = Math.floorMod((int) Math.round(x0 + dx * t), WIDTH);
            int y = (int) Math.round(y0 + dy * t);
            stamp(pixels, x, y, 2, 0x80000000, false);
            stamp(pixels, x, y, 1, 0xE0FFFFFF, true);
        }
    }

    private static void stamp(int[] pixels, int cx, int cy, int radius, int color, boolean replace) {
        int radiusSquared = radius * radius;
        for (int y = cy - radius; y <= cy + radius; y++) {
            if (y < 0 || y >= HEIGHT) continue;
            for (int x = cx - radius; x <= cx + radius; x++) {
                int dx = x - cx, dy = y - cy;
                if (dx * dx + dy * dy > radiusSquared) continue;
                int index = y * WIDTH + Math.floorMod(x, WIDTH);
                if (replace || pixels[index] == 0) pixels[index] = color;
            }
        }
    }
}
