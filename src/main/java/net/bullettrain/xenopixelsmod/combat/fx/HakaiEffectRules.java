package net.bullettrain.xenopixelsmod.combat.fx;

import net.bullettrain.xenopixelsmod.client.combat.HakaiFade;
import net.minecraft.world.phys.Vec3;

/**
 * Where and how big the Hakai effects play (pure, for tests). The effects are authored for a
 * 1.8-block body by tools/effekseer/gen_hakai_effects.py; see docs/effekseer-fx.md.
 */
public final class HakaiEffectRules {
    /** Ticks between crumble bursts at the dissolve line (the veil pulses every 10). */
    public static final int CRUMBLE_INTERVAL = 4;
    private static final float AUTHORED_HEIGHT = 1.8f;
    private static final float AUTHORED_WIDTH = 0.6f;

    private HakaiEffectRules() {
    }

    /**
     * Height fraction (0 feet, 1 head) where the body is currently coming apart: the same line
     * the client's head-to-feet body wipe draws, so the flakes leave exactly where it vanishes.
     */
    public static float crumbleLine(float progress, float speed, float curve) {
        return 1.0f - HakaiFade.charged(progress, speed, curve);
    }

    /** True while the wipe is somewhere on the body (it has started and not reached the feet). */
    public static boolean crumbling(float progress, float speed, float curve) {
        float charged = HakaiFade.charged(progress, speed, curve);
        return charged > 0.0f && charged < 1.0f;
    }

    public static float bodyScale(float bbHeight) {
        return clamp(bbHeight / AUTHORED_HEIGHT, 0.4f, 4.0f);
    }

    /**
     * An area Hakai's effect: the sphere's diameter in authored body heights, times
     * {@code /xenoset hakaiAreaFxScale}, so the veil covers what is being erased.
     */
    public static float areaScale(double radius, float multiplier) {
        return clamp((float) (radius * 2.0 / AUTHORED_HEIGHT) * multiplier, 0.4f, 5000.0f);
    }

    public static float widthScale(float bbWidth) {
        return clamp(bbWidth / AUTHORED_WIDTH, 0.4f, 4.0f);
    }

    /** The caster's raised palm: shoulder height, half a block in front along the look. */
    public static Vec3 palm(Vec3 feet, float bbHeight, Vec3 look) {
        Vec3 flat = new Vec3(look.x, 0.0, look.z);
        flat = flat.lengthSqr() < 1.0e-6 ? new Vec3(0, 0, 1) : flat.normalize();
        return feet.add(0.0, bbHeight * 0.82, 0.0).add(flat.scale(0.55));
    }

    private static float clamp(float v, float lo, float hi) {
        return Float.isFinite(v) ? Math.max(lo, Math.min(hi, v)) : 1.0f;
    }
}
