package net.bullettrain.xenopixelsmod.client.anim.studio;

import net.bullettrain.xenopixelsmod.client.anim.XenoAnimClip;

import java.util.Map;

/**
 * One scene playhead over a clip, in seconds.
 *
 * <p>The scene length is held here as {@link #duration}, deliberately <em>not</em> derived from the
 * clip content. Deriving it was the bug that made the studio unusable: with no keys the length was
 * 0, so {@link #seek} clamped every request to 0, so the playhead could never be moved somewhere a
 * first key could be placed, so no clip could ever grow past a single pose.
 *
 * <p>The playhead is a double. {@link #snap} keeps it on whole ticks for ordinary work, and turning
 * it off is what lets two bones be keyed at genuinely different times.
 */
public final class AnimTimeline {
    /** 3 seconds, matching {@link XenoAnimClip#DEFAULT_DURATION_TICKS}. */
    public static final int DEFAULT_DURATION = XenoAnimClip.DEFAULT_DURATION_TICKS;

    private static final int MIN_DURATION = 1;
    private static final int MAX_DURATION = 20 * 60 * 5;
    private static final double TICK = 1.0 / XenoAnimClip.TICKS_PER_SECOND;

    private XenoAnimClip clip;
    private double playhead;
    private int duration = DEFAULT_DURATION;
    private boolean snap = true;

    /** Binds a clip and rewinds. Use for loading or starting playback. */
    public void bind(XenoAnimClip clip) {
        this.clip = clip;
        this.playhead = 0.0;
        this.duration = clip == null ? DEFAULT_DURATION : clip.sceneTicks();
    }

    /**
     * Binds a clip and keeps the playhead where it is. Use after editing the clip that is already
     * loaded, so that keying a frame does not throw the user back to the start.
     */
    public void rebind(XenoAnimClip clip) {
        double keep = playhead;
        this.clip = clip;
        if (clip != null) extendToTicks(clip.lastKeyTick());
        seek(keep);
    }

    public XenoAnimClip clip() {
        return clip;
    }

    public boolean snap() {
        return snap;
    }

    public void snap(boolean value) {
        snap = value;
        if (value) seek(playhead);
    }

    /** Playhead in seconds. */
    public double playhead() {
        return playhead;
    }

    /** Playhead rounded to the nearest whole tick, for display and tick-based callers. */
    public int playheadTicks() {
        return (int) Math.round(playhead * XenoAnimClip.TICKS_PER_SECOND);
    }

    /** Scene length in ticks. Always at least as long as the last key. */
    public int duration() {
        return clip == null ? duration : Math.max(duration, clip.lastKeyTick());
    }

    public double durationSeconds() {
        return duration() / (double) XenoAnimClip.TICKS_PER_SECOND;
    }

    public void setDuration(int ticks) {
        duration = Math.max(MIN_DURATION, Math.min(MAX_DURATION, ticks));
        seek(playhead);
    }

    /** Grows the scene so {@code ticks} is reachable. Never shrinks it. */
    public void extendToTicks(int ticks) {
        if (ticks > duration) setDuration(ticks);
    }

    /** Moves the playhead, clamped to the scene and snapped to a tick when snapping is on. */
    public void seek(double seconds) {
        double value = Math.max(0.0, Math.min(seconds, durationSeconds()));
        if (snap) value = Math.round(value * XenoAnimClip.TICKS_PER_SECOND) * TICK;
        playhead = AnimChannel.quantise(value);
    }

    public void seekTicks(int tick) {
        seek(tick * TICK);
    }

    /** Steps whole ticks, or one millisecond per unit when snapping is off. */
    public void step(int deltaTicks) {
        seek(playhead + deltaTicks * (snap ? TICK : 0.001));
    }

    /** The end of the scene, which is what playback wraps on. */
    public int lengthTicks() {
        return duration();
    }

    public boolean hasKeyAtPlayhead() {
        return clip != null && clip.hasKeyAt(playhead);
    }

    public boolean hasKeyAtPlayhead(String bone) {
        return clip != null && clip.hasKeyAt(bone, playhead);
    }

    /** Moves to the next key after the playhead; false when there is none. */
    public boolean toNextKey() {
        if (clip == null) return false;
        Double time = clip.nextKeyTime(playhead);
        if (time == null) return false;
        extendToTicks((int) Math.ceil(time * XenoAnimClip.TICKS_PER_SECOND));
        playhead = time;
        return true;
    }

    /** Moves to the previous key before the playhead; false when there is none. */
    public boolean toPrevKey() {
        if (clip == null) return false;
        Double time = clip.prevKeyTime(playhead);
        if (time == null) return false;
        playhead = time;
        return true;
    }

    /**
     * The pose every keyed bone holds at {@code seconds}.
     *
     * <p>Resolved per bone and per channel by the clip itself, so a bone missing from a nearby key
     * holds its own value instead of snapping to rest, and a bone that appears in no key at all is
     * left out so the underlying DragonMineZ animation keeps driving it.
     */
    public Map<String, AnimBonePose> poseAt(double seconds) {
        return clip == null ? new java.util.LinkedHashMap<>() : clip.poseAt(seconds);
    }

    public Map<String, AnimBonePose> poseAtPlayhead() {
        return poseAt(playhead);
    }
}
