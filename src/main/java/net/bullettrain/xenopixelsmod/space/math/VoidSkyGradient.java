package net.bullettrain.xenopixelsmod.space.math;

/**
 * The {@code void} sky's vertical ramp: near-black at the zenith, a little purple toward the nadir.
 *
 * <p>Owner request 2026-10-14, on the frame cost of the star field: "we just need a fps friendly like
 * void skybox that is just black then abit of purple". The look is therefore one cube with per-vertex
 * colours — the same single static draw the plain black cube already was — and no second buffer. The
 * purple stays faint on purpose; a saturated void is a different skybox than the one asked for.
 *
 * <p>Pure integer math with no Minecraft or GL types, so the endpoints and the ordering are testable.
 */
public final class VoidSkyGradient {
    /** Colour at the bottom of the dome: {@code #2B0F3F}, the only place the purple shows. */
    static final int NADIR = 0xFF2B0F3F;
    /** Colour at the top: {@code #030107}, black with the faintest violet left in it. */
    static final int ZENITH = 0xFF030107;

    private VoidSkyGradient() {}

    /**
     * The blended ARGB colour at one height.
     *
     * @param unitHeight 0 at the nadir, 1 at the zenith; anything outside is clamped, because the
     *                   caller's geometry comes from a cube's corners and a bad value must not be able
     *                   to produce a negative channel
     * @return packed ARGB with full alpha
     */
    public static int argb(double unitHeight) {
        double t = unitHeight < 0.0 ? 0.0 : (unitHeight > 1.0 ? 1.0 : unitHeight);
        int red = channel(NADIR, ZENITH, 16, t);
        int green = channel(NADIR, ZENITH, 8, t);
        int blue = channel(NADIR, ZENITH, 0, t);
        return 0xFF000000 | (red << 16) | (green << 8) | blue;
    }

    private static int channel(int from, int to, int shift, double t) {
        int a = (from >> shift) & 0xFF, b = (to >> shift) & 0xFF;
        return (int) Math.round(a + (b - a) * t);
    }
}
