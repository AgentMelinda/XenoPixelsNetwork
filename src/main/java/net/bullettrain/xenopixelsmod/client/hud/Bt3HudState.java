package net.bullettrain.xenopixelsmod.client.hud;

/**
 * The BT3 HUD's moving parts, kept apart from the drawing.
 *
 * <p>Everything here is a presentation of state the game already owns: bar values chase the
 * snapshot, the lost-health trail chases the bar, the Max Power flash is a one-shot triggered by
 * the authoritative lit-segment count changing, and the follow-up prompt's fade is driven by
 * whether the follow-up window is live. Nothing in this class decides <i>whether</i> something is
 * true — only how quickly the HUD catches up with it. That is the whole reason it is a separate
 * object with no Minecraft types: the timing is testable, and it cannot quietly grow a rule that
 * makes the HUD disagree with the server.
 *
 * <p>Smoothing is frame-rate independent. {@code 1 - exp(-rate * dt)} gives the same curve in the
 * same wall-clock time at 30fps and at 300fps, which a per-frame {@code lerp(v, target, k)} does
 * not; the old approach visibly eased faster the better your machine was.
 */
public final class Bt3HudState {

    /** Bars catch up in roughly a fifth of a second. */
    private static final float BAR_RATE = 9f;
    /** The lost-health trail falls slower than the bar, which is what makes a hit readable. */
    private static final float GHOST_RATE = 3.2f;
    /** How long the trail hangs at the old value before it starts to fall, in seconds. */
    private static final float GHOST_HOLD = 0.45f;
    /** One converted Max Power segment's flash, in seconds. */
    private static final float SEGMENT_FLASH = 0.11f;
    /** Prompt entrance and exit, in seconds. */
    private static final float PROMPT_FADE = 0.09f;
    /**
     * Reduced motion still fades rather than cutting, but only for this long, in seconds.
     *
     * <p>It has to be quicker than {@link #BAR_RATE}, not merely different: the point of the
     * setting is less time spent travelling, and a "reduced" motion that took longer than the full
     * one would be the opposite of what it says.
     */
    private static final float REDUCED_FADE = 0.12f;
    /**
     * Longest frame the smoothing will integrate.
     *
     * <p>A world load or an alt-tab hands the first frame back a delta measured in seconds. Without
     * this the bars would appear to have already finished moving, and the prompt would skip its
     * entrance entirely.
     */
    private static final float MAX_STEP = 0.25f;

    private float hp = 1f;
    private float ki = 1f;
    private float stamina = 1f;
    private float hpGhost = 1f;
    private float ghostHold;
    /**
     * The health fraction the previous frame was asked for.
     *
     * <p>The trail's hold is armed by a <i>drop</i>, not by the trail being above the bar. Arming it
     * from the gap instead re-armed it every frame the trail was still catching up, which left the
     * trail stuck at full health for as long as the player stayed hurt.
     */
    private float lastHpTarget = 1f;
    private int litSegments = -1;
    private float segmentFlash;
    private float prompt;
    private boolean started;

    /**
     * Advance one frame.
     *
     * @param dt          seconds since the previous frame
     * @param hpTarget    0..1 health
     * @param kiTarget    0..1 ki
     * @param stmTarget   0..1 stamina
     * @param litKi       segments the authoritative Max Power charge has converted, 0 when not charging
     * @param promptLive  whether the cinematic follow-up would be accepted right now
     * @param reduced     reduced-motion rendering
     */
    public void advance(float dt, float hpTarget, float kiTarget, float stmTarget,
                        int litKi, boolean promptLive, boolean reduced) {
        float step = Math.max(0f, Math.min(MAX_STEP, dt));
        hpTarget = clamp(hpTarget);
        kiTarget = clamp(kiTarget);
        stmTarget = clamp(stmTarget);

        if (!started) {
            // First frame of a session or a respawn: show the truth rather than sweeping up to it
            // from a full bar, which reads as the player healing when they have not.
            started = true;
            hp = hpTarget;
            ki = kiTarget;
            stamina = stmTarget;
            hpGhost = hpTarget;
            lastHpTarget = hpTarget;
            litSegments = litKi;
            prompt = promptLive ? 1f : 0f;
            return;
        }

        if (reduced) {
            float k = approach(step, fadeRate(REDUCED_FADE));
            hp += (hpTarget - hp) * k;
            ki += (kiTarget - ki) * k;
            stamina += (stmTarget - stamina) * k;
            // No trail and no flash: both are motion whose only job is to draw the eye.
            hpGhost = hp;
            ghostHold = 0f;
            lastHpTarget = hpTarget;
            segmentFlash = 0f;
            litSegments = litKi;
            prompt += ((promptLive ? 1f : 0f) - prompt) * k;
            return;
        }

        float bar = approach(step, BAR_RATE);
        hp += (hpTarget - hp) * bar;
        ki += (kiTarget - ki) * bar;
        stamina += (stmTarget - stamina) * bar;

        if (hpTarget < lastHpTarget - 0.0005f) ghostHold = GHOST_HOLD;
        lastHpTarget = hpTarget;
        if (hpTarget > hpGhost) {
            // Healed past the trail: there is no loss left to show, so it rejoins the bar.
            hpGhost = hp;
            ghostHold = 0f;
        } else if (ghostHold > 0f) {
            ghostHold = Math.max(0f, ghostHold - step);
        } else {
            hpGhost += (hp - hpGhost) * approach(step, GHOST_RATE);
        }

        if (litKi > litSegments && litSegments >= 0) {
            segmentFlash = SEGMENT_FLASH;
        } else if (litKi < litSegments) {
            // An early release resets the charge on the server; the staged segments go back with
            // it rather than finishing a sweep the game has already cancelled.
            segmentFlash = 0f;
        }
        litSegments = litKi;
        segmentFlash = Math.max(0f, segmentFlash - step);

        prompt += ((promptLive ? 1f : 0f) - prompt) * approach(step, fadeRate(PROMPT_FADE));
        if (!promptLive && prompt < 0.02f) prompt = 0f;
    }

    /** Forget the eased values, so the next frame shows the new player's state outright. */
    public void reset() {
        started = false;
        segmentFlash = 0f;
        lastHpTarget = 1f;
        ghostHold = 0f;
        prompt = 0f;
        litSegments = -1;
    }

    public float hp() {
        return hp;
    }

    public float ki() {
        return ki;
    }

    public float stamina() {
        return stamina;
    }

    /** Health the bar has already lost but the trail has not yet given up. */
    public float hpGhost() {
        return hpGhost;
    }

    /** 0..1 over the newest converted Max Power segment's flash; 0 when nothing just converted. */
    public float segmentFlash01() {
        return segmentFlash <= 0f ? 0f : segmentFlash / SEGMENT_FLASH;
    }

    /** 0..1 prompt opacity. Reaches 0 promptly once the follow-up window closes. */
    public float prompt01() {
        return prompt;
    }

    /** The frame-rate independent share of the remaining distance to cover this step. */
    private static float approach(float dt, float rate) {
        if (dt <= 0f || rate <= 0f) return 0f;
        return (float) (1.0 - Math.exp(-rate * dt));
    }

    /**
     * The rate that covers a fade of {@code seconds} in that many seconds.
     *
     * <p>Exponential decay never arrives, so "a 120ms fade" has to mean a point at which it is done
     * to the eye: three time constants, about 95% of the distance. Writing the durations as
     * durations and converting here is what stops a constant named {@code FADE = 0.12f} from
     * quietly meaning a fade that is still visibly moving a third of a second later.
     */
    private static float fadeRate(float seconds) {
        return seconds <= 0f ? Float.MAX_VALUE : 3f / seconds;
    }

    private static float clamp(float value) {
        if (Float.isNaN(value)) return 0f;
        return Math.max(0f, Math.min(1f, value));
    }
}
