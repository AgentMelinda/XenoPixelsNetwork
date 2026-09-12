package net.bullettrain.xenopixelsmod.client.anim.studio;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Live bone pose applied while the studio is open or a clip is playing. */
public final class StudioPoseBuffer {
    private static final Map<String, AnimBonePose> LIVE = new LinkedHashMap<>();
    private static UUID subject;
    private static boolean active;
    private static float weight = 1f;

    private StudioPoseBuffer() {}

    public static void setSubject(UUID id) {
        subject = id;
    }

    public static void setActive(boolean value) {
        active = value;
        if (!value) {
            LIVE.clear();
            weight = 1f;
        }
    }

    /**
     * How strongly the studio pose overrides DragonMineZ's own, 0 to 1.
     *
     * <p>1 is a straight overwrite, which is what editing wants. Playback ramps it up at the start
     * and down at the end so a clip eases in and out instead of snapping to its first key and back.
     */
    public static void setWeight(float value) {
        weight = Math.max(0f, Math.min(1f, value));
    }

    public static float weight() {
        return weight;
    }

    public static boolean active() {
        return active;
    }

    public static boolean appliesTo(UUID id) {
        return active && id != null && id.equals(subject);
    }

    public static Map<String, AnimBonePose> live() {
        return LIVE;
    }

    public static AnimBonePose bone(String name) {
        return LIVE.computeIfAbsent(name, n -> new AnimBonePose());
    }

    public static void putAll(Map<String, AnimBonePose> poses) {
        LIVE.clear();
        if (poses == null) return;
        for (var e : poses.entrySet()) {
            LIVE.put(e.getKey(), e.getValue() == null ? new AnimBonePose() : e.getValue().copy());
        }
    }

    public static Map<String, AnimBonePose> snapshot() {
        Map<String, AnimBonePose> copy = new LinkedHashMap<>();
        for (var e : LIVE.entrySet()) copy.put(e.getKey(), e.getValue().copy());
        return Collections.unmodifiableMap(copy);
    }
}
