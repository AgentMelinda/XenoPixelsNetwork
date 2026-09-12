package net.bullettrain.xenopixelsmod.client.anim.studio;

import java.util.ArrayList;
import java.util.List;

/**
 * Operations over one channel's keys: settle, smooth, bridge, simplify.
 *
 * <p>Hand-keying is bad at two things - bringing a bone back to where it started without a visible
 * snap, and cleaning up the key storm a recording leaves behind. These are the four tools for that.
 * Every one is a pure function over an {@link AnimChannel}, so each unit-tests on its own.
 */
public final class AnimMotionOps {

    /** How finely {@link #bridge} samples a curve by default: every half tick. */
    public static final double DEFAULT_STEP = 0.025;

    private AnimMotionOps() {}

    /**
     * Eases the channel from wherever it is at {@code fromSeconds} back to its rest value.
     *
     * <p>Two keys, not a run of them: one holding the current value at the start of the settle and
     * one at rest at the end, with the easing on the arriving key. The channel interpolates between
     * them, so the result stays editable rather than being baked into a dozen keys.
     *
     * @return the time the settle lands at
     */
    public static double settle(AnimChannel channel, double fromSeconds, double durationSeconds,
                                String easing) {
        AnimChannel.Kind kind = channel.kind();
        return settleTo(channel, fromSeconds, durationSeconds,
                kind.restX, kind.restY, kind.restZ, easing);
    }

    /** As {@link #settle}, to a value of your choosing. */
    public static double settleTo(AnimChannel channel, double fromSeconds, double durationSeconds,
                                  float x, float y, float z, String easing) {
        double start = AnimChannel.quantise(fromSeconds);
        double end = AnimChannel.quantise(start + Math.max(0.001, durationSeconds));
        float[] held = channel.valueAt(start);
        channel.put(start, new AnimKey(held[0], held[1], held[2], channel.has(start)
                ? channel.at(start).easing : AnimEasing.LINEAR));
        channel.put(end, new AnimKey(x, y, z, easing));
        return end;
    }

    /**
     * Smooths the keys inside a time range with a weighted three-point average, endpoints pinned.
     *
     * <p>Pinning the first and last key of the range means a smooth never drags the motion away
     * from the poses on either side of it, which is what makes it safe to run repeatedly.
     *
     * @param strength 0 leaves the keys alone, 1 is a full average
     * @return how many keys were moved
     */
    public static int smooth(AnimChannel channel, double fromSeconds, double toSeconds,
                             float strength) {
        List<Double> times = timesIn(channel, fromSeconds, toSeconds);
        if (times.size() < 3) return 0;
        float amount = Math.max(0f, Math.min(1f, strength));
        if (amount <= 0f) return 0;

        // Read every value first: smoothing in place would feed each result into the next.
        float[][] source = new float[times.size()][];
        for (int i = 0; i < times.size(); i++) {
            AnimKey key = channel.at(times.get(i));
            source[i] = new float[] {key.x, key.y, key.z};
        }
        int moved = 0;
        for (int i = 1; i < times.size() - 1; i++) {
            AnimKey key = channel.at(times.get(i));
            for (int axis = 0; axis < 3; axis++) {
                float average = (source[i - 1][axis] + 2f * source[i][axis] + source[i + 1][axis]) / 4f;
                key.component(axis, key.component(axis) + (average - key.component(axis)) * amount);
            }
            moved++;
        }
        return moved;
    }

    /**
     * Replaces the segment between two keys with {@code steps} explicitly eased keys.
     *
     * <p>This is how a curve becomes portable. {@code catmullrom} in particular is a spline through
     * the neighbouring keys rather than a curve over one segment, so it survives export only as the
     * points it actually passes through.
     *
     * @return how many keys were written
     */
    public static int bridge(AnimChannel channel, double fromSeconds, double toSeconds, int steps) {
        double start = AnimChannel.quantise(fromSeconds);
        double end = AnimChannel.quantise(toSeconds);
        if (end <= start || steps < 1) return 0;

        // Sample before writing, or each new key changes the curve the next sample reads.
        double[] times = new double[steps];
        float[][] values = new float[steps][];
        for (int i = 0; i < steps; i++) {
            double t = start + (end - start) * ((i + 1.0) / (steps + 1.0));
            times[i] = t;
            values[i] = channel.valueAt(t);
        }
        for (int i = 0; i < steps; i++) {
            channel.put(times[i], new AnimKey(values[i][0], values[i][1], values[i][2]));
        }
        return steps;
    }

    /**
     * Drops keys that the surrounding ones already describe, within {@code tolerance}.
     *
     * <p>Ramer-Douglas-Peucker over the key polyline, measuring each key against the straight line
     * its neighbours would draw. A full-pose recording lays down a key every tick; this is what
     * turns that back into something a person can edit.
     *
     * @return how many keys were removed
     */
    public static int simplify(AnimChannel channel, double tolerance) {
        List<Double> times = new ArrayList<>(channel.times());
        if (times.size() < 3 || tolerance <= 0) return 0;
        boolean[] keep = new boolean[times.size()];
        keep[0] = true;
        keep[times.size() - 1] = true;
        reduce(channel, times, 0, times.size() - 1, tolerance, keep);

        int removed = 0;
        for (int i = 0; i < times.size(); i++) {
            if (!keep[i]) {
                channel.remove(times.get(i));
                removed++;
            }
        }
        return removed;
    }

    private static void reduce(AnimChannel channel, List<Double> times, int first, int last,
                               double tolerance, boolean[] keep) {
        if (last <= first + 1) return;
        double firstTime = times.get(first);
        double lastTime = times.get(last);
        float[] a = components(channel.at(firstTime));
        float[] b = components(channel.at(lastTime));
        double span = lastTime - firstTime;

        double worst = -1;
        int worstIndex = -1;
        for (int i = first + 1; i < last; i++) {
            double t = span <= 0 ? 0 : (times.get(i) - firstTime) / span;
            float[] actual = components(channel.at(times.get(i)));
            double distance = 0;
            for (int axis = 0; axis < 3; axis++) {
                double straight = a[axis] + (b[axis] - a[axis]) * t;
                double delta = actual[axis] - straight;
                distance += delta * delta;
            }
            distance = Math.sqrt(distance);
            if (distance > worst) {
                worst = distance;
                worstIndex = i;
            }
        }
        if (worstIndex < 0 || worst <= tolerance) return;
        keep[worstIndex] = true;
        reduce(channel, times, first, worstIndex, tolerance, keep);
        reduce(channel, times, worstIndex, last, tolerance, keep);
    }

    private static List<Double> timesIn(AnimChannel channel, double from, double to) {
        List<Double> times = new ArrayList<>();
        double lo = Math.min(from, to);
        double hi = Math.max(from, to);
        for (double time : channel.times()) {
            if (time >= lo - AnimChannel.TIME_EPSILON && time <= hi + AnimChannel.TIME_EPSILON) {
                times.add(time);
            }
        }
        return times;
    }

    private static float[] components(AnimKey key) {
        return key == null ? new float[3] : new float[] {key.x, key.y, key.z};
    }
}
