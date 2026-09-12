package net.bullettrain.xenopixelsmod.client.anim.studio;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Copy, paste and mirror of a whole studio pose.
 *
 * <p>Mirroring is the cheap half of animating a symmetric rig: pose a right hook, paste it mirrored,
 * and the left hook is done. It swaps the {@code right_*} and {@code left_*} bones and flips the
 * side-dependent axes of each pose (see {@link AnimBonePose#mirrored()}).
 */
public final class AnimPoseClipboard {
    private static Map<String, AnimBonePose> stored = Map.of();

    private AnimPoseClipboard() {}

    public static boolean isEmpty() {
        return stored.isEmpty();
    }

    public static void copy(Map<String, AnimBonePose> pose) {
        stored = deepCopy(pose);
    }

    public static Map<String, AnimBonePose> paste() {
        return deepCopy(stored);
    }

    public static Map<String, AnimBonePose> pasteMirrored() {
        return mirror(stored);
    }

    /** Pure and side-effect free, so the mirror rule can be unit-tested on its own. */
    public static Map<String, AnimBonePose> mirror(Map<String, AnimBonePose> pose) {
        Map<String, AnimBonePose> out = new LinkedHashMap<>();
        if (pose == null) return out;
        for (Map.Entry<String, AnimBonePose> entry : pose.entrySet()) {
            AnimBonePose value = entry.getValue();
            out.put(mirrorBone(entry.getKey()),
                    value == null ? new AnimBonePose() : value.mirrored());
        }
        return out;
    }

    /** {@code right_arm} becomes {@code left_arm} and back; anything else is unchanged. */
    public static String mirrorBone(String bone) {
        if (bone == null) return null;
        if (bone.startsWith("right_")) return "left_" + bone.substring("right_".length());
        if (bone.startsWith("left_")) return "right_" + bone.substring("left_".length());
        return bone;
    }

    private static Map<String, AnimBonePose> deepCopy(Map<String, AnimBonePose> pose) {
        Map<String, AnimBonePose> out = new LinkedHashMap<>();
        if (pose == null) return out;
        for (Map.Entry<String, AnimBonePose> entry : pose.entrySet()) {
            out.put(entry.getKey(),
                    entry.getValue() == null ? new AnimBonePose() : entry.getValue().copy());
        }
        return out;
    }
}
