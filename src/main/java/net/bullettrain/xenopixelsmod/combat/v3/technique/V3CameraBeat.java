package net.bullettrain.xenopixelsmod.combat.v3.technique;

import java.util.List;
import net.minecraft.world.phys.Vec3;

/** Authored camera offsets in the attacker-to-target frame: right, up, forward. */
public record V3CameraBeat(int tick, int duration, Vec3 position, Vec3 look, float focus,
                           Easing easing) {
    public enum Easing { CUT, LINEAR, SMOOTH }
    public static final int MAX_BEATS = 128;

    public V3CameraBeat {
        if (tick < 0 || duration <= 0 || tick + (long) duration > V3Beat.MAX_TICK
                || !bounded(position) || !bounded(look) || !Float.isFinite(focus)
                || focus < 0 || focus > 1 || easing == null) {
            throw new IllegalArgumentException("Invalid V3 camera beat");
        }
    }

    private static boolean bounded(Vec3 value) {
        return value != null && Double.isFinite(value.x) && Double.isFinite(value.y)
                && Double.isFinite(value.z) && Math.abs(value.x) <= 64
                && Math.abs(value.y) <= 64 && Math.abs(value.z) <= 64;
    }

    public double progress(double elapsed) {
        double progress = Math.clamp((elapsed - tick) / duration, 0, 1);
        return switch (easing) {
            case CUT -> 1;
            case LINEAR -> progress;
            case SMOOTH -> progress * progress * (3 - 2 * progress);
        };
    }

    /** Empty means this occurrence has no authored camera yet; no fallback is fabricated. */
    public static List<V3CameraBeat> validate(List<V3CameraBeat> beats, int durationTicks) {
        if (beats == null || beats.size() > MAX_BEATS || durationTicks < 0 || durationTicks > V3Beat.MAX_TICK) {
            throw new IllegalArgumentException("Invalid V3 camera timeline");
        }
        int end = 0;
        for (V3CameraBeat beat : beats) {
            if (beat == null || beat.tick < end || beat.tick + beat.duration > durationTicks) {
                throw new IllegalArgumentException("Overlapping or out-of-range V3 camera shot");
            }
            end = beat.tick + beat.duration;
        }
        return List.copyOf(beats);
    }
}
