package net.bullettrain.xenopixelsmod.client.ui.atlas;

import java.util.List;
import java.util.function.ToIntFunction;

/** Shared text sizing for atlas controls. Widget art and hit boxes remain at native size. */
public final class AtlasTextFit {
    public static final float MIN_SCALE = 0.75f;
    private static final String ELLIPSIS = "…";

    private AtlasTextFit() {}

    /** Returns one scale that fits every label in a visual group, down to the readable floor. */
    public static float groupScale(List<Measure> labels, float minimum) {
        float scale = 1.0f;
        for (Measure label : labels) {
            if (label.textWidth() > 0 && label.availableWidth() > 0) {
                scale = Math.min(scale, label.availableWidth() / (float) label.textWidth());
            }
        }
        return Math.max(Math.max(0.1f, minimum), Math.min(1.0f, scale));
    }

    /** Scale for one control when no sibling group is supplied. */
    public static float scale(int textWidth, int availableWidth) {
        return groupScale(List.of(new Measure(textWidth, availableWidth)), MIN_SCALE);
    }

    /** Trims a label only when the chosen scale still cannot fit it. */
    public static String fit(String text, int availableWidth, float scale,
                             ToIntFunction<String> widthOf) {
        if (text == null || availableWidth <= 0 || widthOf.applyAsInt(text) * scale <= availableWidth) {
            return text == null ? "" : text;
        }
        int unscaledBudget = Math.max(0, (int) Math.floor(availableWidth / Math.max(0.1f, scale)));
        int ellipsisWidth = widthOf.applyAsInt(ELLIPSIS);
        if (ellipsisWidth > unscaledBudget) return "";
        String prefix = text;
        while (!prefix.isEmpty() && widthOf.applyAsInt(prefix) + ellipsisWidth > unscaledBudget) {
            int lastCodePoint = prefix.offsetByCodePoints(prefix.length(), -1);
            prefix = prefix.substring(0, lastCodePoint);
        }
        return prefix + ELLIPSIS;
    }

    public record Measure(int textWidth, int availableWidth) {}
}
