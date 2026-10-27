package net.bullettrain.xenopixelsmod.space.event;

import java.util.ArrayList;
import java.util.List;

/** Pure, bounded rules for selecting exposed Sable hull voxels during a supernova crossing. */
final class StellarHullDamagePolicy {
    enum Axis { X, Y, Z }

    record Face(Axis axis, boolean positive) {}
    record Bounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        Bounds {
            if (minX > maxX || minY > maxY || minZ > maxZ)
                throw new IllegalArgumentException("inverted hull bounds");
        }

        int width(Axis axis) { return switch (axis) { case X -> maxX - minX + 1; case Y -> maxY - minY + 1; case Z -> maxZ - minZ + 1; }; }
        int min(Axis axis) { return switch (axis) { case X -> minX; case Y -> minY; case Z -> minZ; }; }
        int max(Axis axis) { return switch (axis) { case X -> maxX; case Y -> maxY; case Z -> maxZ; }; }
    }
    record Position(int x, int y, int z) {}

    private StellarHullDamagePolicy() {}

    static boolean eligible(boolean hostDimension, boolean identifiedShip, boolean managedCelestial) {
        return hostDimension && identifiedShip && !managedCelestial;
    }

    /** Keeps deferred damage candidates bounded while allowing an already-queued body to retain its entry. */
    static boolean mayQueue(int pendingCount, boolean alreadyQueued, int maxPending) {
        if (alreadyQueued) return true;
        return pendingCount >= 0 && maxPending > 0 && pendingCount < maxPending;
    }

    /** A stale queue entry must be discarded before it spends one of the per-tick damage slots. */
    static boolean pendingCandidateCurrent(boolean bodyEligible, boolean activeWave,
            boolean exposedNow, boolean cooldownReady) {
        return bodyEligible && activeWave && exposedNow && cooldownReady;
    }

    /** Selects the cardinal hull face pointing toward the wave source; zero or malformed vectors are rejected. */
    static Face faceTowardSource(double x, double y, double z) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || (x == 0.0 && y == 0.0 && z == 0.0)) return null;
        double ax = Math.abs(x), ay = Math.abs(y), az = Math.abs(z);
        if (ax >= ay && ax >= az) return new Face(Axis.X, x > 0.0);
        if (ay >= az) return new Face(Axis.Y, y > 0.0);
        return new Face(Axis.Z, z > 0.0);
    }

    /**
     * Returns no more than {@code maxProbes} deterministic positions, all within the selected face and no deeper
     * than {@code maxDepth}. Keeping probe count explicit lets the caller enforce a strict server-work ceiling.
     */
    static List<Position> probes(Bounds bounds, Face face, long seed, int maxProbes, int maxDepth) {
        if (bounds == null || face == null || maxProbes <= 0 || maxDepth < 0) return List.of();
        Axis normal = face.axis();
        Axis u = normal == Axis.X ? Axis.Y : Axis.X;
        Axis v = normal == Axis.Z ? Axis.Y : Axis.Z;
        int uWidth = bounds.width(u), vWidth = bounds.width(v);
        int depthLimit = Math.min(maxDepth, bounds.width(normal) - 1);
        List<Position> result = new ArrayList<>(maxProbes);
        for (int i = 0; i < maxProbes; i++) {
            long mixed = mix(seed + 0x9E3779B97F4A7C15L * i);
            int uValue = bounds.min(u) + Math.floorMod((int) mixed, uWidth);
            int vValue = bounds.min(v) + Math.floorMod((int) (mixed >>> 32), vWidth);
            int depth = Math.floorMod((int) (mixed >>> 17), depthLimit + 1);
            int normalValue = face.positive() ? bounds.max(normal) - depth : bounds.min(normal) + depth;
            result.add(switch (normal) {
                case X -> new Position(normalValue, uValue, vValue);
                case Y -> new Position(uValue, normalValue, vValue);
                case Z -> new Position(uValue, vValue, normalValue);
            });
        }
        return List.copyOf(result);
    }

    static long nextDamageTick(long now, int intervalTicks) {
        long interval = Math.max(1, intervalTicks);
        return now > Long.MAX_VALUE - interval ? Long.MAX_VALUE : now + interval;
    }

    private static long mix(long value) {
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }
}
