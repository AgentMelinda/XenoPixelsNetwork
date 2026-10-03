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
     * aura is a camera-space overlay ({@code pose identity; translate(0, -0.6, -0.7)} at 0.45
     * alpha in {@code AuraRenderer.executeAuraShaderDraw}). HD copies use the same eye-space
     * offset each frame, scaled by {@link #firstPersonScaleFactor()}, instead of a world-space
     * egg at the feet (that filled the view, 2026-10-02 SSRose3).
     */
    public static boolean cameraSpaceInFirstPerson() {
        return true;
    }

    /** Kept for older call sites: camera-space first person means we do not hide the effect. */
    public static boolean hideWorldSpaceInFirstPerson() {
        return !cameraSpaceInFirstPerson();
    }

    /** Eye-space offset matching DragonMineZ: {x right, y up, z} with -Z forward. */
    public static float[] firstPersonEyeOffset() {
        return new float[] {0.0f, -0.6f, -0.7f};
    }

    /**
     * How large the HD aura is in first person relative to its third-person size. Matches DMZ's
     * 0.45 first-person alpha so it does not read as a wall.
     */
    public static float firstPersonScaleFactor() {
        return 0.45f;
    }

    /**
     * World position of the first-person HD aura from the camera eye and look angles (degrees,
     * same as {@code Camera} / {@code Vec3.directionFromRotation}).
     */
    public static float[] firstPersonWorldPos(double eyeX, double eyeY, double eyeZ, float xRot, float yRot) {
        float[] o = firstPersonEyeOffset();
        net.minecraft.world.phys.Vec3 forward = net.minecraft.world.phys.Vec3.directionFromRotation(xRot, yRot);
        net.minecraft.world.phys.Vec3 worldUp = new net.minecraft.world.phys.Vec3(0.0, 1.0, 0.0);
        net.minecraft.world.phys.Vec3 right = forward.cross(worldUp);
        if (right.lengthSqr() < 1.0e-6) {
            right = net.minecraft.world.phys.Vec3.directionFromRotation(0.0f, yRot + 90.0f);
        } else {
            right = right.normalize();
        }
        net.minecraft.world.phys.Vec3 up = right.cross(forward).normalize();
        // Eye space +X right, +Y up, -Z forward.
        double wx = eyeX + right.x * o[0] + up.x * o[1] + forward.x * (-o[2]);
        double wy = eyeY + right.y * o[0] + up.y * o[1] + forward.y * (-o[2]);
        double wz = eyeZ + right.z * o[0] + up.z * o[1] + forward.z * (-o[2]);
        return new float[] {(float) wx, (float) wy, (float) wz};
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

    /** {@code v1}, {@code v2} or {@code v3}; anything else is v1. */
    public static String parseVariant(String raw) {
        if (raw == null) return "v1";
        String v = raw.trim().toLowerCase();
        return "v2".equals(v) || "v3".equals(v) ? v : "v1";
    }

    /** Variants 2 and 3 are authored to DragonMineZ's flame box. */
    public static boolean silhouetteBox(String variant) {
        return "v2".equalsIgnoreCase(variant) || "v3".equalsIgnoreCase(variant);
    }

    /** Live copies of aura2 / aura3 sit on the silhouette box; aura_in / aura_out stay on v1. */
    public static boolean silhouetteEffect(String path) {
        if (path == null) return false;
        return path.startsWith("aura2/") || path.startsWith("aura3/");
    }

    /** v1 and v3 play the soft outer billow column ({@code aura_out_*}). */
    public static boolean playsV1Outer(String variant) {
        String v = parseVariant(variant);
        return "v1".equals(v) || "v3".equals(v);
    }

    /** v1 and v3 play the body shell ({@code aura_in_*}). */
    public static boolean playsV1Inner(String variant) {
        String v = parseVariant(variant);
        return "v1".equals(v) || "v3".equals(v);
    }

    /** v2 and v3 play the spiked silhouette effect set. */
    public static boolean playsSilhouette(String variant) {
        return silhouetteBox(variant);
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
