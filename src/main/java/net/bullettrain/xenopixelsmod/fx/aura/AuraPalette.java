package net.bullettrain.xenopixelsmod.fx.aura;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * The colours the HD aura has effects for (effeks/aura/palette.txt, written by the generator), and
 * the nearest one to any aura colour. Distance is the same weighted RGB ("redmean") the generator
 * uses to space the palette.
 */
public final class AuraPalette {
    private static final String RESOURCE = "/assets/xenopixelsmod/effeks/aura/palette.txt";
    private static volatile List<Integer> colours;

    private AuraPalette() {
    }

    public static List<Integer> colours() {
        List<Integer> c = colours;
        if (c == null) {
            c = load();
            colours = c;
        }
        return c;
    }

    private static List<Integer> load() {
        List<Integer> out = new ArrayList<>();
        try (InputStream in = AuraPalette.class.getResourceAsStream(RESOURCE)) {
            if (in == null) return List.of(0xFFFFFF);
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.matches("[0-9a-fA-F]{6}")) out.add(Integer.parseInt(line, 16));
            }
        } catch (IOException | NumberFormatException e) {
            return List.of(0xFFFFFF);
        }
        return out.isEmpty() ? List.of(0xFFFFFF) : List.copyOf(out);
    }

    public static int nearest(int rgb) {
        int best = colours().get(0);
        double bestDistance = Double.MAX_VALUE;
        for (int c : colours()) {
            double d = distance(rgb, c);
            if (d < bestDistance) {
                bestDistance = d;
                best = c;
            }
        }
        return best;
    }

    public static double distance(int a, int b) {
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        double rm = (ar + br) / 2.0;
        double dr = ar - br, dg = ag - bg, db = ab - bb;
        return Math.sqrt((2 + rm / 256) * dr * dr + 4 * dg * dg + (2 + (255 - rm) / 256) * db * db);
    }

    public static String hex(int rgb) {
        return String.format("%06x", rgb & 0xFFFFFF);
    }

    public static int rgb(float[] colour) {
        int r = Math.round(Math.max(0f, Math.min(1f, colour[0])) * 255f);
        int g = Math.round(Math.max(0f, Math.min(1f, colour[1])) * 255f);
        int b = Math.round(Math.max(0f, Math.min(1f, colour[2])) * 255f);
        return (r << 16) | (g << 8) | b;
    }

    /**
     * The inner and outer effect colours (as palette hex): the form's main aura colour inside, its
     * extra colour for the outer flame when it has one, otherwise the main colour for both.
     */
    public static String[] pair(float[] main, float[] extra) {
        String inner = hex(nearest(rgb(main)));
        String outer = extra == null ? inner : hex(nearest(rgb(extra)));
        return new String[] {inner, outer};
    }

    /** Brightness levels baked into the aura effects (tools/effekseer/efkgen/effects/aura.py LEVELS). */
    private static final float[] LEVELS = {0.1f, 0.25f, 0.5f, 0.75f, 1.0f, 1.3f};
    /** Below this the aura is not played at all: 0% is off, not the dimmest level. */
    public static final float MIN_VISIBLE = 0.05f;

    /** Whether an aura at this brightness is worth playing. */
    public static boolean visible(float brightness) {
        return brightness >= MIN_VISIBLE;
    }

    /**
     * What a /xenoaura brightness argument means: a percentage, 0 to 130. A value of 1.3 or less
     * is still read as the fraction the command took before 2026-10-02 (0.5 to 1.3), since 1%
     * is not a level anyone asks for.
     */
    public static float brightnessOf(float argument) {
        float fraction = argument <= 1.3f ? argument : argument / 100.0f;
        return Math.max(0.0f, Math.min(1.3f, fraction));
    }

    /** The effect-name suffix of the baked level nearest to {@code brightness} ("" is 100%). */
    public static String brightnessSuffix(float brightness) {
        float best = 1.0f;
        for (float level : LEVELS) {
            if (Math.abs(level - brightness) < Math.abs(best - brightness)) best = level;
        }
        return best == 1.0f ? "" : "_b" + Math.round(best * 100);
    }
}
