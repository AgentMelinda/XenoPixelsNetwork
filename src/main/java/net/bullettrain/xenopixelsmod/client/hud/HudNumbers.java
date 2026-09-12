package net.bullettrain.xenopixelsmod.client.hud;

import net.bullettrain.xenopixelsmod.client.config.XenoClientConfig;

/**
 * Compact number formatting for the HUD: {@code 12400} reads as {@code 12.4k}.
 *
 * <p>DMZ stat pools reach seven figures well before the endgame, and a raw
 * {@code 1284500/1284500} does not fit beside a bar at HUD scale — it either overruns the frame
 * or shrinks past legibility. Suffixes keep the pair inside a fixed width no matter how far a
 * character has progressed.
 *
 * <p>Thresholds are SI-cased on purpose: lower-case {@code k} for thousands, upper-case
 * {@code M} for millions. The three HUD renderers previously each had their own private
 * {@code formatNum} with different rules — the legacy views only abbreviated above ten thousand
 * and the modern view not at all — so the same character read differently depending on which
 * renderer was selected. This is the single implementation all of them now use.
 */
public final class HudNumbers {

    private static final double THOUSAND = 1_000.0;
    private static final double MILLION = 1_000_000.0;

    private HudNumbers() {
    }

    /**
     * One value, abbreviated when it is large enough to need it.
     *
     * <p>Returns the exact rounded number when {@link XenoClientConfig#hudCompactNumbers} is off,
     * which is the reason that switch exists: a player checking whether a heal landed wants the
     * real figure, and {@code 1.2M} cannot tell them.
     */
    public static String format(double value) {
        double v = Math.max(0.0, value);
        if (!XenoClientConfig.hudCompactNumbers) return exact(v);
        if (v >= MILLION) return trim(v / MILLION) + "M";
        if (v >= THOUSAND) return trim(v / THOUSAND) + "k";
        return exact(v);
    }

    /**
     * The whole number when there is one, otherwise three decimals.
     *
     * <p>{@code Math.round} was the old path and it silently dropped the fraction: a heal that
     * moved 102.4 to 102.9 read as {@code 102} both times, so the exact mode could not actually
     * show a small heal landing. Large values stay integral because DMZ writes them that way, so
     * the common case still prints with no decimal point at all.
     */
    private static String exact(double v) {
        if (v == Math.rint(v) && v < 9.007199254740992E15) {
            return String.valueOf((long) v);
        }
        return String.format(java.util.Locale.ROOT, "%.3f", v);
    }

    /** {@code current/max}, the form the bars label themselves with. */
    public static String formatPair(double current, double max) {
        if (max <= 0.0) return format(current);
        return format(current) + "/" + format(max);
    }

    /**
     * One decimal place, with a trailing {@code .0} dropped.
     *
     * <p>{@code 5k} rather than {@code 5.0k} — the decimal only earns its width when it is
     * carrying information, and at HUD scale two characters is a real amount of room.
     */
    private static String trim(double scaled) {
        double rounded = Math.round(scaled * 10.0) / 10.0;
        if (rounded == Math.floor(rounded)) return String.valueOf((long) rounded);
        return String.valueOf(rounded);
    }
}
