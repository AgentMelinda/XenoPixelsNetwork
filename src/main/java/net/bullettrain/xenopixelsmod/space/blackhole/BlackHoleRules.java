package net.bullettrain.xenopixelsmod.space.blackhole;

/**
 * Pure black-hole rules for design-doc §15 (no Minecraft types). A hole has three concentric bands around its centre:
 * the LENS band (visual only, out to {@code lens}), the TIDAL band (from {@code horizon} to {@code tidal}: a pull toward
 * the centre that grows as you fall in, and faster oxygen use) and the EVENT HORIZON ({@code horizon}): crossing it
 * flushes you to the hole's exit instead of killing you.
 */
public final class BlackHoleRules {
    private BlackHoleRules() {}

    public enum Band { OUTSIDE, LENS, TIDAL, HORIZON }

    public record Hole(String id, double x, double y, double z, double horizon, double tidal, double lens, double pull) {
        public Hole {
            if (!(horizon > 0) || !(tidal > horizon) || !(lens >= tidal) || !(pull >= 0) || !Double.isFinite(pull))
                throw new IllegalArgumentException("invalid black hole " + id);
        }
    }

    public static double distance(Hole hole, double x, double y, double z) {
        double dx = x - hole.x(), dy = y - hole.y(), dz = z - hole.z();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    public static Band band(Hole hole, double distance) {
        if (!Double.isFinite(distance)) return Band.OUTSIDE;
        if (distance <= hole.horizon()) return Band.HORIZON;
        if (distance <= hole.tidal()) return Band.TIDAL;
        if (distance <= hole.lens()) return Band.LENS;
        return Band.OUTSIDE;
    }

    /**
     * Pull toward the centre in blocks/tick^2 inside the tidal band: {@code pull} at the band's outer edge, rising with the
     * inverse square of distance, capped at 8x so it stays escapable with sustained thrust. 0 outside the band.
     */
    public static double pullPerTick(Hole hole, double distance) {
        if (band(hole, distance) != Band.TIDAL) return 0;
        double ratio = hole.tidal() / Math.max(distance, 1e-6);
        return Math.min(hole.pull() * ratio * ratio, hole.pull() * 8);
    }

    /** Escape meter for the HUD: 0 at the horizon, 1 at the tidal band's outer edge. */
    public static double escapeFraction(Hole hole, double distance) {
        if (distance <= hole.horizon()) return 0;
        if (distance >= hole.tidal()) return 1;
        return (distance - hole.horizon()) / (hole.tidal() - hole.horizon());
    }

    /** Oxygen is used this many times faster in the tidal band (hull flex, §15). */
    public static int oxygenFactor(Band band, int tidalFactor) { return band == Band.TIDAL || band == Band.HORIZON ? Math.max(1, tidalFactor) : 1; }
}
