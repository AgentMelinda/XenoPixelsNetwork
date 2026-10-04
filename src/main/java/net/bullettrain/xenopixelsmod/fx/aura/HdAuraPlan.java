package net.bullettrain.xenopixelsmod.fx.aura;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Which HD auras to play for DragonMineZ's aura layers. DMZ draws every layer as its own aura
 * (form, stack form, the form being transformed into), each a little larger than the one below and
 * the incoming one fading in. The HD aura first took only the two lowest layers as one aura's inner
 * and outer colour, which folded a stack form into the outer flame and showed a transformation's
 * target colour at full strength from the first tick (2026-10-02 owner: "we broke stack aura ...
 * and transformation aura color").
 */
public final class HdAuraPlan {
    /** DMZ's own growth per layer (AuraRenderer, DragonMineZ 2.1.3: {@code 1 + layerId * 0.15}). */
    static final float LAYER_STEP = 0.15f;
    /** A layer fainter than this is not played yet: the dimmest baked brightness would overstate it. */
    static final float MIN_ALPHA = 0.2f;
    /**
     * Ice-white extra layers (SSB's old {@code #E1F5FE}) as their own silhouette blow out to a white
     * wall under additive sprites (2026-10-02 owner: "blue forms aura are fucking white").
     */
    static final float PALE_LUMA = 0.82f;

    /** One DMZ aura layer: its id, colour and opacity (below 1 only while transforming). */
    public record Layer(int id, int rgb, float alpha) {}

    /** One HD aura to play: inner and outer colour, size on top of the base aura, and strength. */
    public record Aura(int inner, int outer, float scale, float alpha) {}

    private HdAuraPlan() {
    }

    /**
     * The aura's size at a power release of {@code release} percent (2026-10-02 owner: "with aura
     * going also from 0 to 100"): 40% of full size at 0, full size at 100, and a little more for a
     * release pushed past its limit.
     */
    public static float releaseScale(int release) {
        float r = Math.max(0f, Math.min(1.5f, release / 100f));
        return 0.4f + 0.6f * r;
    }

    /**
     * Real time between two client ticks beyond which the playing aura is taken to have run out.
     * The effect's own clock is real time and each copy emits for half a second, while copies are
     * sent by game tick; after a freeze this long the ones on screen have burnt out and the next
     * is still up to half a second away, so the aura vanished and built up again (2026-10-02
     * owner: "sometimes on client lag aura resets"). Past this, a copy is sent at once.
     */
    public static final long HITCH_NANOS = 250_000_000L;

    public static boolean hitch(long lastTickNanos, long nowNanos) {
        return lastTickNanos != 0L && nowNanos - lastTickNanos > HITCH_NANOS;
    }

    /**
     * First person (2026-10-03 owner: render like normal DMZ aura). DragonMineZ's first-person
     * path in {@code AuraRenderer.executeAuraShaderDraw} is: pose identity;
     * {@code translate(0, -0.6, -0.7)}; {@code scale(normalizedScale * 3)}; draw at
     * {@code alpha * 0.45}. The 0.45 is <b>alpha</b>, not scale (misread until 2026-10-03 FP
     * depth fix). HD copies use the same head-space offset + camera lock each frame via AAA's
     * measured Basis ({@code +Z} behind the eyes), instead of a world-space egg at the feet
     * (that filled the view, 2026-10-02 SSRose3).
     */
    public static boolean cameraSpaceInFirstPerson() {
        return true;
    }

    /** Kept for older call sites: camera-space first person means we do not hide the effect. */
    public static boolean hideWorldSpaceInFirstPerson() {
        return !cameraSpaceInFirstPerson();
    }

    /**
     * AAA / DMZ head-space offset for the FP overlay: {@code {x, y, z}} with AAA {@code +Z}
     * behind the eyes (so {@code z = -0.7} is 0.7 in front — same as DMZ view-space
     * {@code translate(0, -0.6, -0.7)} after identity).
     */
    public static float[] firstPersonEyeOffset() {
        return new float[] {0.0f, -0.6f, -0.7f};
    }

    /**
     * DMZ FP scale multiplier on the normalized aura scale
     * ({@code poseStack.scale(normalizedScaleX * 3, normalizedScaleY * 3, 1)}).
     */
    public static float firstPersonScaleFactor() {
        return 3.0f;
    }

    /**
     * DMZ FP alpha multiplier ({@code finalAlpha * 0.45} in {@code executeAuraShaderDraw}).
     * Applied on top of layer alpha / brightness when the live path can express it.
     */
    public static float firstPersonAlphaFactor() {
        return 0.45f;
    }

    /**
     * Authored silhouette sprite centre above the emitter ({@code aura2.py} / {@code aura3.py}
     * {@code CENTRE_Y = 1.5}). Used so FP placement puts that centre on DMZ's billboard centre.
     */
    public static float silhouetteCentreY() {
        return 1.5f;
    }

    /**
     * How far to pull the emitter down along camera-up (blocks) so a silhouette's authored
     * centre lands on the DMZ FP billboard centre after {@code heightScale} is applied.
     */
    public static float firstPersonEmitterCentreNudge(boolean silhouette, float heightScale) {
        return silhouette ? silhouetteCentreY() * Math.max(0.0f, heightScale) : 0.0f;
    }

    /**
     * Effekseer / AAA rotation (radians) matching head-space
     * {@code Basis.fromEuler(-pitch, PI - yaw, 0)} so the effect is camera-locked like DMZ's
     * identity overlay.
     */
    public static float[] firstPersonRotationRadians(float xRotDeg, float yRotDeg) {
        double pitch = Math.toRadians(xRotDeg);
        double yaw = Math.toRadians(yRotDeg);
        return new float[] {(float) (-pitch), (float) (Math.PI - yaw), 0.0f};
    }

    /**
     * Render-interpolated entity feet, matching {@code Entity.getPosition(partial)} /
     * Camera / DMZ ({@code lerp(partial, xo, getX())}).
     *
     * <p><b>Must use {@code xo}/{@code yo}/{@code zo}, never {@code xOld}/{@code yOld}/{@code zOld}.</b>
     * {@code absMoveTo} and similar paths refresh {@code xo} without {@code xOld}, so lerping from
     * {@code xOld} leaves the HD aura a tick behind when flying or moving fast (2026-10-03).
     */
    public static float[] entityRenderPos(double xo, double yo, double zo,
                                          double x, double y, double z, float partial) {
        return new float[] {
                (float) net.minecraft.util.Mth.lerp(partial, xo, x),
                (float) net.minecraft.util.Mth.lerp(partial, yo, y),
                (float) net.minecraft.util.Mth.lerp(partial, zo, z)
        };
    }

    /**
     * World position of the first-person HD aura from the camera eye and look angles (degrees),
     * using AAA 2.3.1 head-space Basis so {@link #firstPersonEyeOffset()} matches DMZ / flight
     * aura conventions ({@code AaaHeadSpaceOffsetTest}).
     */
    public static float[] firstPersonWorldPos(double eyeX, double eyeY, double eyeZ, float xRot, float yRot) {
        return firstPersonWorldPos(eyeX, eyeY, eyeZ, xRot, yRot, firstPersonEyeOffset());
    }

    /**
     * Like {@link #firstPersonWorldPos(double, double, double, float, float)} with an explicit
     * head-space local offset (after centre-nudge).
     */
    public static float[] firstPersonWorldPos(double eyeX, double eyeY, double eyeZ, float xRot, float yRot,
                                              float[] localOffset) {
        float[] o = localOffset == null ? firstPersonEyeOffset() : localOffset;
        double pitch = Math.toRadians(xRot);
        double yaw = Math.toRadians(yRot);
        net.minecraft.world.phys.Vec3 local = new net.minecraft.world.phys.Vec3(o[0], o[1], o[2]);
        net.minecraft.world.phys.Vec3 world = mod.chloeprime.aaaparticles.common.util.Basis
                .fromEuler(new net.minecraft.world.phys.Vec3(-pitch, Math.PI - yaw, 0.0))
                .toGlobal(local);
        return new float[] {
                (float) (eyeX + world.x),
                (float) (eyeY + world.y),
                (float) (eyeZ + world.z)
        };
    }

    /** @deprecated use {@link #firstPersonScaleFactor()} with camera-space placement */
    @Deprecated
    public static float firstPersonWorldScale() {
        return cameraSpaceInFirstPerson() ? firstPersonScaleFactor() : 0.0f;
    }

    /** The widest the HD aura is ever stretched, whatever the ki aura curve says. */
    public static final float MAX_STRETCH_WIDTH = 4.0f;

    /**
     * DragonMineZ's aura scale for an ordinary player at rest: model scale 0.9375 times its 1.05
     * base. The HD effects are authored to match DMZ's aura at this value, so an aura scale is
     * divided by it to get how much larger than that the HD aura has to be.
     */
    public static final float DMZ_REST_SCALE = 0.9375f * 1.05f;

    /**
     * Where DragonMineZ's aura flame is, for an aura scale of {@code scaleX} by {@code scaleY}
     * (AuraRenderer.executeAuraShaderDraw, 2.1.3): the quad [-1,1] x [-1,1] is scaled by the aura
     * scale times 2.2 and centred 0.05 + 0.7 of that height above the feet, and the flame fills
     * rows 29-986 and columns 119-881 of each 1024 px frame.
     *
     * @return {bottom, top, width} in blocks, bottom and top measured from the feet
     */
    public static float[] dmzFlameBox(float scaleX, float scaleY) {
        float sx = scaleX * 2.2f;
        float sy = scaleY * 2.2f;
        float quadTop = 0.05f + 0.7f * sy + sy;
        float top = quadTop - 29f / 1024f * 2f * sy;
        float bottom = quadTop - 986f / 1024f * 2f * sy;
        float width = (881f - 119f) / 1024f * 2f * sx;
        return new float[] {bottom, top, width};
    }

    /**
     * Each variant's size against that box, at /xenoaura size 1 (2026-10-02 owner: "its size
     * should be size 1"). Variant 2 is authored to the box in blocks (aura2.py), so it needs
     * nothing. Variant 1 is a column from the feet up, about 4.4 tall and 4.5 wide at scale 1, and
     * is brought down to the box; those two figures are read off its authored values and have not
     * been checked against a running game - /xenoaura box is there to check them.
     */
    public static float[] shape(boolean variant2) {
        return variant2 ? new float[] {1.0f, 1.0f} : new float[] {0.72f, 0.92f};
    }

    /** {@code v1}, {@code v2}, {@code v3} or {@code v4}; anything else is v1. */
    public static String parseVariant(String raw) {
        if (raw == null) return "v1";
        String v = raw.trim().toLowerCase();
        return "v2".equals(v) || "v3".equals(v) || "v4".equals(v) ? v : "v1";
    }

    /** Variants 2, 3 and 4 are authored to DragonMineZ's flame box. */
    public static boolean silhouetteBox(String variant) {
        String v = parseVariant(variant);
        return "v2".equals(v) || "v3".equals(v) || "v4".equals(v);
    }

    /** Live copies of aura2 / aura3 sit on the silhouette box; aura_in / aura_out / aura4 stay on v1. */
    public static boolean silhouetteEffect(String path) {
        if (path == null) return false;
        return path.startsWith("aura2/") || path.startsWith("aura3/");
    }

    /** v1 and v3 play the dense outer billow column ({@code aura_out_*}). v4 uses the lean column. */
    public static boolean playsV1Outer(String variant) {
        String v = parseVariant(variant);
        return "v1".equals(v) || "v3".equals(v);
    }

    /** v1, v3 and v4 play the body shell ({@code aura_in_*}). */
    public static boolean playsV1Inner(String variant) {
        String v = parseVariant(variant);
        return "v1".equals(v) || "v3".equals(v) || "v4".equals(v);
    }

    /** v2, v3 and v4 play the spiked silhouette effect set. */
    public static boolean playsSilhouette(String variant) {
        return silhouetteBox(variant);
    }

    /** v4 plays the cheaper plume ({@code aura4/aura4_*}) instead of {@code aura_out_*}. */
    public static boolean playsLeanOuter(String variant) {
        return "v4".equals(parseVariant(variant));
    }

    /**
     * How far below the feet a variant is moved, in blocks at rest ("should start abit below
     * players legs"): DragonMineZ's flame starts 0.44 below them. Variant 2 has that authored in;
     * variant 1 starts at the feet and is lowered.
     */
    public static float drop(boolean variant2) {
        return variant2 ? 0.0f : 0.44f;
    }

    /** An aura scale from DragonMineZ as {width, height} relative to a player at rest. */
    public static float[] relativeToRest(float[] auraScale) {
        if (auraScale == null || auraScale.length < 2) return new float[] {1.0f, 1.0f};
        return new float[] {auraScale[0] / DMZ_REST_SCALE, auraScale[1] / DMZ_REST_SCALE};
    }

    /**
     * The ki aura curve, held to what an effect made of sprites can take.
     *
     * <p>DragonMineZ's aura is one quad, and a quad four or ten times its height is still a
     * flame. The HD aura is hundreds of sprites laid out over that height: at the same factor
     * its flame licks became rays to the sky and its silhouette left the fighter altogether
     * (2026-10-02 owner, with a screenshot: "wtf is this" - battle power 1.6x times a ki charge
     * height of 4x made an aura some thirty blocks tall). So the stretch is capped:
     * {@code maxHeight} tall (/xenoaura maxheight) and {@link #MAX_STRETCH_WIDTH} wide. The default
     * cap is 10, which is DragonMineZ's own size in practice: the owner then asked for the HD aura
     * to follow the ki height setting exactly, and a lower cap is there for whoever wants it tamed.
     *
     * @return {width, height}
     */
    public static float[] capStretch(float width, float height, float maxHeight) {
        float w = Float.isFinite(width) ? width : 1.0f;
        float h = Float.isFinite(height) ? height : 1.0f;
        return new float[] {Math.max(0.2f, Math.min(MAX_STRETCH_WIDTH, w)),
                Math.max(0.2f, Math.min(Math.max(1.0f, maxHeight), h))};
    }

    /** How strongly the body shines at that release, 0.25 to 1 ("make body shine with it too"). */
    public static float releaseShine(int release) {
        float r = Math.max(0f, Math.min(1f, release / 100f));
        return 0.25f + 0.75f * r;
    }

    /** {@code rgb} dimmed to {@code shine}: the outline glow has only its colour to be faint with. */
    public static int shade(int rgb, float shine) {
        float k = Math.max(0f, Math.min(1f, shine));
        int r = Math.round(((rgb >> 16) & 0xFF) * k);
        int g = Math.round(((rgb >> 8) & 0xFF) * k);
        int b = Math.round((rgb & 0xFF) * k);
        return (r << 16) | (g << 8) | b;
    }

    /**
     * @param layers   DMZ's layers, lowest id first
     * @param extras   colours that are a form's extra aura: they tint the outer flame of the aura
     *                 below instead of becoming an aura of their own
     * @param separate false keeps the first behaviour (one aura from the two lowest layers)
     */
    public static List<Aura> plan(List<Layer> layers, Set<Integer> extras, boolean separate) {
        if (layers.isEmpty()) return List.of();
        Layer base = layers.get(0);
        if (!separate) {
            int outer = layers.size() > 1 ? layers.get(1).rgb() : base.rgb();
            return List.of(new Aura(base.rgb(), outer, 1.0f, 1.0f));
        }
        List<Aura> out = new ArrayList<>();
        out.add(new Aura(base.rgb(), base.rgb(), 1.0f, 1.0f));
        for (Layer layer : layers.subList(1, layers.size())) {
            Aura below = out.get(out.size() - 1);
            if (extras.contains(layer.rgb()) && below.outer() == below.inner()) {
                out.set(out.size() - 1, new Aura(below.inner(), layer.rgb(), below.scale(), below.alpha()));
                continue;
            }
            if (layer.alpha() < MIN_ALPHA) continue;
            if (tooPaleToStack(layer.rgb())) continue;
            float scale = 1.0f + Math.max(1, layer.id() - base.id()) * LAYER_STEP;
            out.add(new Aura(layer.rgb(), layer.rgb(), scale, Math.min(1.0f, layer.alpha())));
        }
        return out;
    }

    /** Rec. 709 luma, 0..1. */
    static float luma(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        return (0.2126f * r + 0.7152f * g + 0.0722f * b) / 255f;
    }

    static boolean tooPaleToStack(int rgb) {
        return luma(rgb) >= PALE_LUMA;
    }
}
