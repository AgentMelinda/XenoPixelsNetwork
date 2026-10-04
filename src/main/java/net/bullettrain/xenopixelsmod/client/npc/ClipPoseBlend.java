package net.bullettrain.xenopixelsmod.client.npc;

import java.util.HashMap;
import java.util.Map;

/**
 * Eases a GeckoLib NPC's bones across the two ends of a scripted clip (a wave, a nod).
 *
 * <p>The NPC's idle and walk run on one controller and scripted clips on a second one that
 * replaces whatever bones the clip keys, with no transition. The idle keeps moving the head and
 * waist, so the bones jumped to the clip's first frame when it started and jumped back to wherever
 * the idle had got to when it ended (2026-10-02 owner: "from waving it resets head position when
 * finished making it look glitchy"). This remembers the pose last shown and, for a few ticks after
 * a clip starts or ends, moves from that pose to the animated one instead of cutting.
 *
 * <p>Plain data, one instance per NPC; the model feeds it the bones.
 */
public final class ClipPoseBlend {
    /** Length of the ease in animation ticks. */
    static final double BLEND_TICKS = 5.0;
    /** A gap this long between frames means the NPC was not drawn: nothing to ease from. */
    static final double STALE_TICKS = 10.0;

    private final Map<String, float[]> shown = new HashMap<>();
    private Map<String, float[]> from;
    private boolean seen;
    private boolean lastActive;
    private double lastTick;
    private double blendStart;
    private double tick;

    /** Once per rendered frame, before {@link #apply}. */
    public void frame(boolean clipActive, double animationTick) {
        boolean stale = !seen || animationTick < lastTick || animationTick - lastTick > STALE_TICKS;
        if (stale) {
            from = null;
            shown.clear();
        } else if (clipActive != lastActive) {
            from = new HashMap<>(shown);
            blendStart = animationTick;
        }
        if (from != null && animationTick - blendStart >= BLEND_TICKS) {
            from = null;
        }
        seen = true;
        lastActive = clipActive;
        lastTick = animationTick;
        tick = animationTick;
    }

    /** Whether bones are being eased this frame, so the model only writes to them when needed. */
    public boolean blending() {
        return from != null;
    }

    /**
     * @param animated the bone's values as the controllers left them this frame
     * @return the values to show: {@code animated} itself outside an ease
     */
    public float[] apply(String bone, float[] animated) {
        float[] out = animated;
        float[] start = from == null ? null : from.get(bone);
        if (start != null && start.length == animated.length) {
            float t = (float) Math.max(0.0, Math.min(1.0, (tick - blendStart) / BLEND_TICKS));
            t = t * t * (3.0f - 2.0f * t);
            out = new float[animated.length];
            for (int i = 0; i < out.length; i++) {
                out[i] = start[i] + (animated[i] - start[i]) * t;
            }
        }
        shown.put(bone, out.clone());
        return out;
    }
}
