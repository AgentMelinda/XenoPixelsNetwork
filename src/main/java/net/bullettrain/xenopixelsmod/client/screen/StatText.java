package net.bullettrain.xenopixelsmod.client.screen;

import java.util.Locale;

/**
 * Text formatting for the XenoPixels DragonMineZ stat panels.
 *
 * <p>Shared by both reworks of that screen -- our own {@link XenoDmzStatsScreen} and the themed
 * DragonMineZ one -- so a stat reads identically whichever is switched on.
 *
 * <p>Deliberately free of any DragonMineZ or Minecraft type so it can be unit tested: the screens
 * themselves cannot be, because the test source set has no DragonMineZ on its classpath. The numbers
 * on those panels are the whole point of them -- a value that reads wrong is worse than a panel that
 * looks wrong -- so these rules are pinned by tests rather than eyeballed in game.
 */
public final class StatText {

    /**
     * How far a multiplier may sit from 1.0 and still count as "no multiplier".
     *
     * <p>The same tolerance DragonMineZ itself uses to decide whether to show one at all
     * ({@code Math.abs(totalMult - 1.0) > 0.01} in {@code CharacterStatsScreen.renderStatsInfo}),
     * so the two agree on which rows are boosted.
     */
    private static final double NEUTRAL_TOLERANCE = 0.01;

    /**
     * The multiplier yellow, sampled from the redesign bundle's own glyphs.
     *
     * <p>{@code 09_micro_elements/example_str_multiplier.png} and its siblings all render their
     * number in this exact colour, so it is the value the art was drawn with rather than a guess --
     * an earlier hand-picked gold was close but visibly not the same. Both the themed DragonMineZ
     * panel and our own stats screen read it from here so they cannot drift apart.
     */
    public static final int MULTIPLIER = 0xFECC22;

    /** The same yellow at rest, for a row whose multiplier is 1. */
    public static final int MULTIPLIER_NEUTRAL = 0x7F6611;

    private StatText() {
    }

    /**
     * Abbreviates a stat for display. DragonMineZ battle power reaches the billions, which would
     * overflow the panel and be unreadable in full anyway.
     */
    public static String format(double value) {
        double abs = Math.abs(value);
        if (abs >= 1.0e9) {
            return String.format(Locale.ROOT, "%.2fB", value / 1.0e9);
        }
        if (abs >= 1.0e6) {
            // A value just under the next unit rounds up into it. 999,999,999 -- which is exactly
            // what a DragonMineZ server configured with a 999,999,999 stat cap hands us -- came out
            // as "1000.00M", which reads as a thousand million and looks like a different, larger
            // number than the one on the stock panel beside it. Roll it into the next unit instead,
            // so it reads "1.00B".
            if (roundsIntoNextUnit(value / 1.0e6, 2)) {
                return String.format(Locale.ROOT, "%.2fB", value / 1.0e9);
            }
            return String.format(Locale.ROOT, "%.2fM", value / 1.0e6);
        }
        if (abs >= 1.0e4) {
            if (roundsIntoNextUnit(value / 1.0e3, 1)) {
                return String.format(Locale.ROOT, "%.2fM", value / 1.0e6);
            }
            return String.format(Locale.ROOT, "%.1fK", value / 1.0e3);
        }
        // Below the abbreviation thresholds, show the exact figure, and do not append a pointless
        // ".0" to values that are whole -- most DMZ stats are.
        return value == Math.floor(value) && !Double.isInfinite(value)
                ? Long.toString((long) value)
                : String.format(Locale.ROOT, "%.1f", value);
    }

    /**
     * True when {@code mantissa}, printed at {@code decimals} places, would reach 1000.
     *
     * <p>Asked before formatting rather than after, because the check is about what
     * {@code String.format} is going to round to, not about the raw value: 999.999999 is under a
     * thousand, and "%.2f" of it is not.
     */
    private static boolean roundsIntoNextUnit(double mantissa, int decimals) {
        double scale = Math.pow(10, decimals);
        return Math.abs(Math.round(mantissa * scale) / scale) >= 1000.0;
    }

    /**
     * Re-abbreviates a number DragonMineZ already formatted, for when the full one will not fit.
     *
     * <p>DMZ groups its stat values ("10,004,999"), which on a 141-wide panel can run straight into
     * the multiplier column. Parsing its own output back is safe here because these values come from
     * {@code numberFormatter.format((long) …)} and are always whole, so every separator can be
     * dropped regardless of whether the locale groups with commas, dots or spaces.
     *
     * @return the abbreviated form, or the input unchanged if it does not parse as a number
     */
    public static String condense(String grouped) {
        if (grouped == null || grouped.isBlank()) {
            return "";
        }
        StringBuilder digits = new StringBuilder();
        for (int i = 0; i < grouped.length(); i++) {
            char c = grouped.charAt(i);
            if (Character.isDigit(c) || (c == '-' && digits.length() == 0)) {
                digits.append(c);
            }
        }
        try {
            return format(Double.parseDouble(digits.toString()));
        } catch (NumberFormatException ignored) {
            return grouped;
        }
    }

    /** True when a multiplier is close enough to 1.0 that DragonMineZ would not show it at all. */
    public static boolean isNeutral(double multiplier) {
        return Math.abs(multiplier - 1.0) <= NEUTRAL_TOLERANCE;
    }

    /**
     * A multiplier as it appears in the yellow column.
     *
     * <p>Unlike DragonMineZ this never returns empty: an unboosted row shows {@code x1} rather than
     * a blank, so all six rows carry a number and the column reads as a column. That matches the
     * bundle, whose README states a multiplier is shown beside every Information stat row.
     */
    public static String multiplier(double multiplier) {
        String text = String.format(Locale.ROOT, "%.1f", multiplier);
        // The bundle writes a whole multiplier as "x1", not "x1.0" -- its README lists the example
        // rows as x3.9 and x1 -- so a trailing zero decimal is dropped to match the art.
        if (text.endsWith(".0")) {
            text = text.substring(0, text.length() - 2);
        }
        return "x" + text;
    }

    /**
     * Splits DragonMineZ's combined stat text into the number and the multiplier it appended.
     *
     * <p>DMZ builds that string as {@code number + " x" + multiplier} and omits the suffix entirely
     * at 1.0, so this is the inverse. It splits on the <b>last</b> {@code " x"}: a formatted number
     * never contains that sequence, whatever grouping separator the locale uses, so no amount of
     * digit grouping can confuse it.
     *
     * @return the number alone, and the multiplier text including its {@code x}, which is
     *         {@code x1} when DMZ appended nothing
     */
    public static Split split(String combined) {
        if (combined == null) {
            return new Split("", multiplier(1.0), true);
        }
        int at = combined.lastIndexOf(" x");
        if (at < 0) {
            // No suffix means DMZ decided the multiplier was within its tolerance of 1.0.
            return new Split(combined, multiplier(1.0), true);
        }
        String number = combined.substring(0, at);
        String mult = combined.substring(at + 1);
        return new Split(number, mult, false);
    }

    /**
     * One stat row's text, taken apart.
     *
     * @param number    the stat value on its own
     * @param mult      the multiplier including its leading {@code x}
     * @param neutral   true when the multiplier is 1.0, so it can be drawn dimmer
     */
    public record Split(String number, String mult, boolean neutral) {
    }

    /**
     * Title-cases one of DragonMineZ's lower-case identifiers, such as a race or form name, for
     * display. Blank and null inputs come back as an empty string: a character mid-creation can have
     * no form or class set, and that should render as nothing rather than throw.
     */
    public static String title(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String trimmed = raw.trim();
        return Character.toUpperCase(trimmed.charAt(0))
                + trimmed.substring(1).toLowerCase(Locale.ROOT);
    }
}
