package net.bullettrain.xenopixelsmod.client.ui.atlas;

import java.util.Locale;
import java.util.OptionalInt;

/**
 * The picker's state, kept in HSV. Pure, so the picking rules are unit-tested.
 *
 * <p>HSV is the source of truth and RGB is derived from it, never the other way round while the
 * user drags. The old picker converted every change to 8-bit RGB and back: at value 0 or saturation
 * 0 the hue (and saturation) reset to 0, so dragging the value bar to black and back lost the
 * chosen colour and the cursor jumped. Here an RGB is only read in when the author types a hex, and
 * even then an achromatic or black colour keeps the hue (and, for black, the saturation) it had.
 */
public final class ColorPickerModel {
    private float hue;
    private float saturation;
    private float value;

    public ColorPickerModel(int rgb) {
        setRgb(rgb);
    }

    /**
     * The state a picker opens in: the colour's hue and saturation at full brightness, so the value
     * bar starts at its top. 2026-09-30 owner: "set color picker steartgn of color to be the
     * brightest". Cancel still restores the original colour.
     */
    public static ColorPickerModel opening(int rgb) {
        ColorPickerModel model = new ColorPickerModel(rgb);
        model.value = 1.0f;
        return model;
    }

    public float hue() {
        return hue;
    }

    public float saturation() {
        return saturation;
    }

    public float value() {
        return value;
    }

    /** A point in the square: x is hue (0..1), y is saturation top 1 to bottom 0. Clamped. */
    public void pickSquare(double fx, double fy) {
        hue = clamp01((float) fx);
        if (hue >= 1.0f) hue = 0.999f;
        saturation = 1.0f - clamp01((float) fy);
    }

    /** A point on the value bar: top is full value, bottom black. Clamped. */
    public void pickValue(double fy) {
        value = 1.0f - clamp01((float) fy);
    }

    public int rgb() {
        return java.awt.Color.HSBtoRGB(hue, saturation, value) & 0xFFFFFF;
    }

    /** The hue at full saturation and value, for the top of the value bar. */
    public int pureHueRgb() {
        return java.awt.Color.HSBtoRGB(hue, 1.0f, 1.0f) & 0xFFFFFF;
    }

    /** Top of the value bar, at the saturation currently chosen in the square. */
    public int fullValueRgb() {
        return java.awt.Color.HSBtoRGB(hue, saturation, 1.0f) & 0xFFFFFF;
    }

    public String hex() {
        return String.format(Locale.ROOT, "#%06X", rgb());
    }

    /** Reads an RGB in, keeping hue for greys and hue+saturation for black. */
    public void setRgb(int rgb) {
        float[] hsb = java.awt.Color.RGBtoHSB((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, null);
        value = hsb[2];
        if (hsb[2] > 0.0f) {
            saturation = hsb[1];
            if (hsb[1] > 0.0f) {
                hue = hsb[0];
            }
        }
    }

    /**
     * Strict hex: {@code #RRGGBB}, {@code RRGGBB}, or 8 digits with alpha (alpha dropped, as the
     * stored colours are RGB). Anything partial is empty, so typing "#1" does not jump the colour.
     */
    public static OptionalInt parseStrictHex(String text) {
        if (text == null) return OptionalInt.empty();
        String t = text.trim();
        if (t.startsWith("#")) t = t.substring(1);
        else if (t.startsWith("0x") || t.startsWith("0X")) t = t.substring(2);
        if (t.length() != 6 && t.length() != 8) return OptionalInt.empty();
        for (int i = 0; i < t.length(); i++) {
            if (Character.digit(t.charAt(i), 16) < 0) return OptionalInt.empty();
        }
        long parsed = Long.parseLong(t, 16);
        return OptionalInt.of((int) (parsed & 0xFFFFFF));
    }

    private static float clamp01(float v) {
        return v < 0.0f ? 0.0f : Math.min(1.0f, v);
    }
}
