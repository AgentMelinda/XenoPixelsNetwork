package net.bullettrain.xenopixelsmod.client.anim.studio;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The pose the DragonMineZ model actually reached last frame, for the recorder to capture.
 *
 * <p>{@code REC} used to store body yaw, head pitch and swing progress - three numbers that are not
 * a body pose. The render mixin already stands at {@code DMZPlayerModel.setCustomAnimations} RETURN
 * with the {@code AnimationProcessor} in hand, which is the one place the finished pose exists, so
 * it drops a copy here and the recorder picks it up on the next client tick.
 *
 * <p>The render thread writes and the client tick reads, so the map is swapped whole rather than
 * mutated: a reader either sees the previous frame or the new one, never a half-written pose.
 */
public final class StudioBoneSampler {
    private static volatile Map<String, AnimBonePose> latest = Map.of();
    private static volatile boolean wanted;

    private StudioBoneSampler() {}

    /** Sampling costs a map allocation per frame, so it only runs while something wants it. */
    public static void setWanted(boolean value) {
        wanted = value;
        if (!value) latest = Map.of();
    }

    public static boolean wanted() {
        return wanted;
    }

    public static void submit(Map<String, AnimBonePose> pose) {
        latest = pose == null ? Map.of() : pose;
    }

    /** A private copy of the most recent sampled pose; empty when nothing has been sampled. */
    public static Map<String, AnimBonePose> take() {
        Map<String, AnimBonePose> snapshot = latest;
        Map<String, AnimBonePose> copy = new LinkedHashMap<>();
        snapshot.forEach((bone, pose) -> copy.put(bone, pose == null ? new AnimBonePose() : pose.copy()));
        return copy;
    }

    public static boolean hasSample() {
        return !latest.isEmpty();
    }
}
