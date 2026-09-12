package net.bullettrain.xenopixelsmod.client.anim.studio;

import java.util.Map;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.TreeMap;

/**
 * One animated channel of one bone: keys at arbitrary times, in seconds.
 *
 * <p>This is the shape a GeckoLib animation file already has - a bone carries independent
 * {@code rotation}, {@code position} and {@code scale} objects, and each holds its own times. The
 * studio used to collapse all of that into a single list of whole-tick keys shared by every bone,
 * which is where "one key per tick, and two bones cannot be keyed at different times" came from.
 * Keeping the file's own shape removes the restriction and makes both import and export simpler.
 *
 * <p>Times are quantised to a millisecond so that a key can be found again by the value that wrote
 * it, and so that the {@code %.3f} second strings the file format uses round-trip exactly.
 */
public final class AnimChannel {

    /** Which transform the three components of a key describe. */
    public enum Kind {
        ROTATION(0f, 0f, 0f),
        POSITION(0f, 0f, 0f),
        SCALE(1f, 1f, 1f),
        /** {@code x} is 1 for visible and 0 for hidden; always stepped, never blended. */
        VISIBILITY(1f, 0f, 0f);

        public final float restX;
        public final float restY;
        public final float restZ;

        Kind(float restX, float restY, float restZ) {
            this.restX = restX;
            this.restY = restY;
            this.restZ = restZ;
        }
    }

    /** Key times are stored to the millisecond. */
    public static final double TIME_EPSILON = 0.0005;

    private final Kind kind;
    private final NavigableMap<Double, AnimKey> keys = new TreeMap<>();

    public AnimChannel(Kind kind) {
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }

    /** Rounds a time to the resolution keys are stored at, so lookups are exact. */
    public static double quantise(double seconds) {
        double clamped = Math.max(0.0, seconds);
        return Math.round(clamped * 1000.0) / 1000.0;
    }

    public boolean isEmpty() {
        return keys.isEmpty();
    }

    public int size() {
        return keys.size();
    }

    public NavigableSet<Double> times() {
        return keys.navigableKeySet();
    }

    public Iterable<Map.Entry<Double, AnimKey>> entries() {
        return keys.entrySet();
    }

    public AnimKey at(double seconds) {
        return keys.get(quantise(seconds));
    }

    public boolean has(double seconds) {
        return keys.containsKey(quantise(seconds));
    }

    public AnimKey put(double seconds, float x, float y, float z) {
        return put(seconds, new AnimKey(x, y, z));
    }

    /** Writes a key, keeping the easing already at that time when the new key does not set one. */
    public AnimKey put(double seconds, AnimKey key) {
        double time = quantise(seconds);
        AnimKey value = key == null ? new AnimKey(kind.restX, kind.restY, kind.restZ) : key.copy();
        AnimKey existing = keys.get(time);
        if (existing != null && AnimEasing.LINEAR.equals(value.easing)) {
            value.easing = existing.easing;
        }
        keys.put(time, value);
        return value;
    }

    public boolean remove(double seconds) {
        return keys.remove(quantise(seconds)) != null;
    }

    public void clear() {
        keys.clear();
    }

    public Double firstTime() {
        return keys.isEmpty() ? null : keys.firstKey();
    }

    public Double lastTime() {
        return keys.isEmpty() ? null : keys.lastKey();
    }

    /** The latest key at or before {@code seconds}. */
    public Double floorTime(double seconds) {
        return keys.floorKey(quantise(seconds) + TIME_EPSILON);
    }

    /** The earliest key at or after {@code seconds}. */
    public Double ceilingTime(double seconds) {
        return keys.ceilingKey(quantise(seconds) - TIME_EPSILON);
    }

    public Double nextTime(double seconds) {
        return keys.higherKey(quantise(seconds) + TIME_EPSILON);
    }

    public Double prevTime(double seconds) {
        return keys.lowerKey(quantise(seconds) - TIME_EPSILON);
    }

    /**
     * The channel value at {@code seconds}, eased.
     *
     * <p>Before the first key it holds the first, after the last it holds the last, and an empty
     * channel returns its rest value. Easing comes from the key being approached, which is the
     * Bedrock convention GeckoLib follows.
     */
    public float[] valueAt(double seconds) {
        if (keys.isEmpty()) {
            return new float[] {kind.restX, kind.restY, kind.restZ};
        }
        double time = quantise(seconds);
        Map.Entry<Double, AnimKey> prev = keys.floorEntry(time + TIME_EPSILON);
        Map.Entry<Double, AnimKey> next = keys.ceilingEntry(time - TIME_EPSILON);
        if (prev == null) return components(next.getValue());
        if (next == null) return components(prev.getValue());
        double span = next.getKey() - prev.getKey();
        if (span <= TIME_EPSILON || kind == Kind.VISIBILITY) {
            // Visibility is a switch, not a ramp: it holds the previous key until the next lands.
            return components(prev.getValue());
        }
        float raw = (float) ((time - prev.getKey()) / span);
        float t = AnimEasing.apply(next.getValue().easing, raw);
        AnimKey a = prev.getValue();
        AnimKey b = next.getValue();
        return new float[] {
                a.x + (b.x - a.x) * t,
                a.y + (b.y - a.y) * t,
                a.z + (b.z - a.z) * t,
        };
    }

    /** True when every key sits at the channel's rest value, so the channel says nothing. */
    public boolean isAtRest() {
        for (AnimKey key : keys.values()) {
            if (key.x != kind.restX || key.y != kind.restY || key.z != kind.restZ) return false;
        }
        return true;
    }

    public AnimChannel copy() {
        AnimChannel out = new AnimChannel(kind);
        keys.forEach((time, key) -> out.keys.put(time, key.copy()));
        return out;
    }

    private static float[] components(AnimKey key) {
        return new float[] {key.x, key.y, key.z};
    }
}
