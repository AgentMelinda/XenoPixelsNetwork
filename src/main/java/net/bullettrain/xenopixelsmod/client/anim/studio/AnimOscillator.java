package net.bullettrain.xenopixelsmod.client.anim.studio;

import java.util.Locale;

/**
 * Continuous motion on one bone axis: a wave added on top of whatever the keys say.
 *
 * <p>Keeping a part moving - a cape swaying, hair drifting, a fighter breathing between blows - is
 * a lot of near-identical keys to place by hand. An oscillator says it once instead.
 *
 * <p>Two ways to use one, and both matter. <b>Live</b>, it is evaluated during playback on top of
 * the keyed pose, so it stays a single editable knob; that works on the playback paths this mod
 * owns. <b>Baked</b>, {@link #bake} samples it into real keys, so it survives into a plain GeckoLib
 * file and into a bound combat move, where none of our code is running.
 *
 * @param bone      rig bone this drives
 * @param channel   which transform it adds to
 * @param axis      0, 1 or 2 for x, y, z
 * @param amplitude peak offset, in the channel's own units
 * @param period    seconds for one full cycle
 * @param phase     cycles to shift by; 0.25 turns a sine into a cosine
 * @param wave      the shape of one cycle
 */
public record AnimOscillator(String bone, AnimChannel.Kind channel, int axis,
                             float amplitude, double period, double phase, Wave wave) {

    /**
     * One cycle's shape.
     *
     * <p>{@code ORBIT} is a quarter-cycle ahead of {@code SINE}: two oscillators on two axes, one
     * of each, trace a circle. That is what "orbit" means here - there is no single-axis circle.
     */
    public enum Wave { SINE, TRIANGLE, PENDULUM, ORBIT }

    public static final double MIN_PERIOD = 0.05;

    public AnimOscillator {
        bone = bone == null || bone.isBlank() ? XenoRig.COMBAT.get(0) : bone;
        channel = channel == null ? AnimChannel.Kind.ROTATION : channel;
        axis = Math.max(0, Math.min(2, axis));
        period = Math.max(MIN_PERIOD, period);
        wave = wave == null ? Wave.SINE : wave;
    }

    public static AnimOscillator sine(String bone, AnimChannel.Kind channel, int axis,
                                      float amplitude, double period) {
        return new AnimOscillator(bone, channel, axis, amplitude, period, 0.0, Wave.SINE);
    }

    /** The offset this oscillator contributes at {@code seconds}. */
    public float valueAt(double seconds) {
        double cycles = seconds / period + phase;
        double fraction = cycles - Math.floor(cycles);
        return amplitude * (float) shape(fraction);
    }

    private double shape(double fraction) {
        return switch (wave) {
            case TRIANGLE -> triangle(fraction);
            // A pendulum lingers at the ends of its swing, which is what a limb does.
            case PENDULUM -> Math.sin(Math.sin(fraction * 2.0 * Math.PI) * (Math.PI / 2.0));
            case ORBIT -> Math.cos(fraction * 2.0 * Math.PI);
            default -> Math.sin(fraction * 2.0 * Math.PI);
        };
    }

    private static double triangle(double fraction) {
        // Rises 0 -> 1 over the first quarter, falls to -1 by three quarters, back to 0.
        double shifted = fraction * 4.0;
        if (shifted < 1.0) return shifted;
        if (shifted < 3.0) return 2.0 - shifted;
        return shifted - 4.0;
    }

    /**
     * Samples this oscillator into real keys on {@code target}, every {@code step} seconds.
     *
     * <p>Additive: whatever the channel already says at each sample time is kept and the wave is
     * added to it, so baking on top of an authored move keeps the move.
     *
     * @return how many keys were written
     */
    public int bake(AnimChannel target, double fromSeconds, double toSeconds, double step) {
        if (target == null || toSeconds <= fromSeconds) return 0;
        double increment = Math.max(0.01, step);
        int written = 0;

        // Sample first, write second: each key would otherwise change what the next sample reads.
        int count = (int) Math.floor((toSeconds - fromSeconds) / increment) + 1;
        double[] times = new double[count];
        float[][] values = new float[count][];
        for (int i = 0; i < count; i++) {
            double time = fromSeconds + i * increment;
            times[i] = time;
            float[] base = target.valueAt(time);
            base[axis] += valueAt(time);
            values[i] = base;
        }
        for (int i = 0; i < count; i++) {
            target.put(times[i], new AnimKey(values[i][0], values[i][1], values[i][2]));
            written++;
        }
        return written;
    }

    /** Adds this oscillator's contribution to a pose that has already been read from the keys. */
    public void applyTo(AnimBonePose pose, double seconds) {
        if (pose == null) return;
        float offset = valueAt(seconds);
        switch (channel) {
            case POSITION -> {
                float x = axis == 0 ? pose.posX + offset : pose.posX;
                float y = axis == 1 ? pose.posY + offset : pose.posY;
                float z = axis == 2 ? pose.posZ + offset : pose.posZ;
                pose.setPosition(x, y, z);
            }
            case SCALE -> {
                float x = axis == 0 ? pose.scaleX + offset : pose.scaleX;
                float y = axis == 1 ? pose.scaleY + offset : pose.scaleY;
                float z = axis == 2 ? pose.scaleZ + offset : pose.scaleZ;
                pose.setScale(x, y, z);
            }
            case VISIBILITY -> {
                // Nothing sensible to add to a switch.
            }
            default -> {
                if (axis == 0) pose.rotX += offset;
                else if (axis == 1) pose.rotY += offset;
                else pose.rotZ += offset;
            }
        }
    }

    public String describe() {
        return String.format(Locale.ROOT, "%s %s %s  %+.1f  %.2fs  %s",
                bone, channel.name().toLowerCase(Locale.ROOT), "xyz".charAt(axis),
                amplitude, period, wave.name().toLowerCase(Locale.ROOT));
    }
}
