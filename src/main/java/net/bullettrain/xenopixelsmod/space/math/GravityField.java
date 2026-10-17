package net.bullettrain.xenopixelsmod.space.math;

/**
 * Body-field gravity summation, with the two limits that keep it cheap enough to run for every ship
 * on every physics pass.
 *
 * <p>§5 of the space design does not sum the whole chart. Two cuts apply, and both are here:
 * <ol>
 *   <li><b>Range cut</b> — a body farther than the field range contributes nothing and is rejected
 *       before any transcendental or division. This is §21's rule that only bodies within interaction
 *       range of a ship have a field at all, and it is what stops 200 charted bodies from meaning 200
 *       square roots per ship per pass.</li>
 *   <li><b>Contributor cap</b> — of the bodies that survive the range cut, only the strongest
 *       {@code maxContributors} are summed. Three is enough to be physically legible (a planet, its
 *       moon, and the star) and turns an O(bodies) sum into O(bodies) selection plus O(cap) addition,
 *       with the addition being the part that actually costs anything.</li>
 * </ol>
 *
 * <p>Selection keeps a fixed-capacity insertion-sorted array rather than a heap or a full sort. For a
 * cap of three that is at most three comparisons and shifts per candidate, allocation-free, and
 * branch-predictable — measurably better than building a priority queue for a handful of survivors.
 *
 * <p>Instances are reusable scratch, not per-call objects. One accumulator per ship per pass, reset
 * between uses, so the hot path allocates nothing at all.
 */
public final class GravityField {

    /**
     * How a body's pull falls off with distance.
     *
     * <p>Authorable per body (§17's {@code "falloff"} key) because the honest answer and the fun
     * answer are not always the same: a survival world wants real inverse-square, a gag world like
     * King Kai's wants a pull that just stops at the edge of the lawn.
     */
    public enum Falloff {
        /**
         * {@code μ/r²}, the physical answer. Clamped to the surface value inside the body's radius so
         * a ship that clips the collider gets a finite pull instead of an infinite one.
         */
        INVERSE_SQUARE,
        /** Full surface gravity everywhere inside the field range, falling linearly to zero at it. */
        LINEAR,
        /** Uniform surface gravity inside the range, zero outside. Cheapest, and flattest to fly. */
        CONSTANT
    }

    private final double[] ax;
    private final double[] ay;
    private final double[] az;
    private final double[] magnitude;
    private final int capacity;

    private double fieldRange;
    private int size;
    /** Count of bodies rejected by the range cut, exposed for the perf probe. */
    private int culled;
    /** Count of bodies that survived the range cut but lost the contributor contest. */
    private int outranked;

    /**
     * @param maxContributors the cap on summed bodies; the design's tuned default is 3, and anything
     *                        below 1 disables the field entirely rather than throwing
     */
    public GravityField(int maxContributors) {
        this.capacity = Math.max(0, maxContributors);
        this.ax = new double[capacity];
        this.ay = new double[capacity];
        this.az = new double[capacity];
        this.magnitude = new double[capacity];
    }

    /** Clears survivors and counters. Must be called before each body of work. */
    public GravityField reset(double fieldRange) {
        this.fieldRange = Math.max(0.0, fieldRange);
        this.size = 0;
        this.culled = 0;
        this.outranked = 0;
        return this;
    }

    /**
     * Offers one body's pull at the query point.
     *
     * @param dx       body x minus query x, and likewise for y and z — deltas, not absolutes, so the
     *                 caller never has to allocate a vector to hand over
     * @param dy       body y minus query y
     * @param dz       body z minus query z
     * @param mu       the body's gravitational parameter {@code GM}; also read as surface gravity for
     *                 the non-inverse-square falloffs, matching how a pack authors one number
     * @param radius   the body's radius, used to clamp the inverse-square singularity
     * @param falloff  how the pull decays; null is treated as {@link Falloff#INVERSE_SQUARE}
     */
    public void add(double dx, double dy, double dz, double mu, double radius, Falloff falloff) {
        if (capacity == 0 || !(mu > 0.0)) return;
        Falloff mode = falloff == null ? Falloff.INVERSE_SQUARE : falloff;

        double distSq = dx * dx + dy * dy + dz * dz;
        if (!(distSq > 0.0) || !Double.isFinite(distSq)) return;

        // Range cut first: one multiply-add and one comparison reject most of the chart, which is the
        // whole point of doing it before the square root.
        if (fieldRange > 0.0 && distSq > fieldRange * fieldRange) {
            culled++;
            return;
        }

        double dist = Math.sqrt(distSq);
        double accel = switch (mode) {
            case INVERSE_SQUARE -> {
                // Inside the body the inverse-square law diverges; hold it at the surface value, which
                // is both finite and what a ship scraping the collider should feel.
                double effective = radius > 0.0 ? Math.max(dist, radius) : dist;
                yield mu / (effective * effective);
            }
            case LINEAR -> fieldRange > 0.0
                    ? mu * Math.max(0.0, 1.0 - dist / fieldRange)
                    : mu;
            case CONSTANT -> mu;
        };
        if (!(accel > 0.0) || !Double.isFinite(accel)) return;

        double invDist = 1.0 / dist;
        insert(dx * invDist * accel, dy * invDist * accel, dz * invDist * accel, accel);
    }

    /** Insertion-sorted push into the fixed-capacity survivor set; the weakest falls off the end. */
    private void insert(double x, double y, double z, double accel) {
        int slot = size;
        if (size < capacity) {
            size++;
        } else {
            // Full: only worth displacing the weakest, and only if we actually beat it.
            if (accel <= magnitude[capacity - 1]) {
                outranked++;
                return;
            }
            slot = capacity - 1;
        }
        // Shift weaker survivors down one to open the ranked slot. Insertion stays sorted by
        // descending magnitude, so index capacity-1 is always the weakest.
        while (slot > 0 && magnitude[slot - 1] < accel) {
            magnitude[slot] = magnitude[slot - 1];
            ax[slot] = ax[slot - 1];
            ay[slot] = ay[slot - 1];
            az[slot] = az[slot - 1];
            slot--;
        }
        magnitude[slot] = accel;
        ax[slot] = x;
        ay[slot] = y;
        az[slot] = z;
    }

    /**
     * Writes the net acceleration into {@code out} as {@code [x, y, z]} and returns it.
     *
     * <p>The surviving contributions are summed strongest-first. Adding in descending magnitude order
     * is not cosmetic: floating-point addition is not associative, and summing the small terms into a
     * large accumulator last loses the least precision.
     */
    public double[] finish(double[] out) {
        out[0] = 0.0;
        out[1] = 0.0;
        out[2] = 0.0;
        for (int i = 0; i < size; i++) {
            out[0] += ax[i];
            out[1] += ay[i];
            out[2] += az[i];
        }
        return out;
    }

    /** Net magnitude of the summed field, for HUD readouts and the perf probe. */
    public double netMagnitude() {
        double x = 0.0, y = 0.0, z = 0.0;
        for (int i = 0; i < size; i++) {
            x += ax[i];
            y += ay[i];
            z += az[i];
        }
        return Math.sqrt(x * x + y * y + z * z);
    }

    /** How many bodies actually contributed, at most the cap. */
    public int contributors() {
        return size;
    }

    /** Configured capacity, for independent diagnostic scratch accumulators. */
    public int capacity() {
        return capacity;
    }

    public int culledCount() {
        return culled;
    }

    public int outrankedCount() {
        return outranked;
    }

    /**
     * One body's pull on one point, for tests and for single-body queries where an accumulator would
     * be ceremony. Returns the magnitude only; direction is the normalized delta.
     */
    public static double acceleration(double distance, double mu, double radius, Falloff falloff,
                                      double fieldRange) {
        if (!(mu > 0.0) || !(distance > 0.0) || !Double.isFinite(distance)) return 0.0;
        if (fieldRange > 0.0 && distance > fieldRange) return 0.0;
        Falloff mode = falloff == null ? Falloff.INVERSE_SQUARE : falloff;
        return switch (mode) {
            case INVERSE_SQUARE -> {
                double effective = radius > 0.0 ? Math.max(distance, radius) : distance;
                yield mu / (effective * effective);
            }
            case LINEAR -> fieldRange > 0.0 ? mu * Math.max(0.0, 1.0 - distance / fieldRange) : mu;
            case CONSTANT -> mu;
        };
    }
}
