package net.bullettrain.xenopixelsmod.plot;

import net.bullettrain.xenopixelsmod.shop.SignShopSyntax;

import javax.annotation.Nullable;
import java.util.Locale;

/**
 * The XenoPixels plot-sale sign grammar, a sibling of {@link SignShopSyntax}.
 *
 * <p>Four lines, where line 1 is the trigger and lines 2-4 are the payload:</p>
 * <pre>
 * [XPLOT]
 * 10,-20
 * 40,5
 * 500
 * </pre>
 *
 * <p>Line 2 is the plot origin and line 3 the far corner, matching the
 * {@code (minX, minZ, maxX, maxZ)} tuple that already identifies a plot. The dimension is never
 * written on the sign — it is the level the sign stands in, which both sides already know. That
 * keeps every line inside the client's typing limit and lets the client render without ever
 * reading server plot state.</p>
 *
 * <p>Nothing here touches a registry, a level, or a block entity, so the grammar is unit-testable
 * without a running game.</p>
 */
public final class PlotSignSyntax {

    /** Activation marker. Matched case-insensitively and with surrounding whitespace allowed. */
    public static final String MARKER = "[XPLOT]";

    /** How many lines the four-line form occupies. */
    public static final int LINE_COUNT = 4;

    /** Vanilla world border, used only to keep the width/length arithmetic from overflowing. */
    public static final int COORDINATE_LIMIT = 30_000_000;

    private PlotSignSyntax() {
    }

    /**
     * @return the parsed listing, or {@code null} when {@code lines} is not a valid plot sign. A
     *         {@code null} result is the normal "this is just a sign" path, not an error.
     */
    @Nullable
    public static PlotSignData parse(String[] lines) {
        if (lines == null || lines.length < LINE_COUNT) {
            return null;
        }
        String head = lines[0] == null ? "" : lines[0].trim();
        if (!isMarker(head)) {
            return null;
        }
        int[] origin = parsePoint(lines[1]);
        int[] far = parsePoint(lines[2]);
        if (origin == null || far == null) {
            return null;
        }
        Double price = parsePrice(lines[3]);
        if (price == null) {
            return null;
        }
        return new PlotSignData(origin[0], origin[1], far[0], far[1], price);
    }

    /** Serializes back to the four-line form, marker included. */
    public static String[] serialize(PlotSignData data) {
        return new String[]{
                MARKER,
                data.minX() + "," + data.minZ(),
                data.maxX() + "," + data.maxZ(),
                SignShopSyntax.formatPrice(data.price())
        };
    }

    /** True when line 1 carries the activation marker, regardless of payload validity. */
    public static boolean isPlotSign(@Nullable String[] lines) {
        if (lines == null || lines.length == 0 || lines[0] == null) {
            return false;
        }
        return isMarker(lines[0].trim());
    }

    private static boolean isMarker(String head) {
        if (head.length() < MARKER.length()) {
            return false;
        }
        return head.substring(0, MARKER.length()).equalsIgnoreCase(MARKER);
    }

    @Nullable
    private static int[] parsePoint(@Nullable String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        int comma = value.indexOf(',');
        if (comma <= 0 || comma == value.length() - 1) {
            return null;
        }
        Integer x = parseCoordinate(value.substring(0, comma));
        Integer z = parseCoordinate(value.substring(comma + 1));
        if (x == null || z == null) {
            return null;
        }
        return new int[]{x, z};
    }

    @Nullable
    private static Integer parseCoordinate(String raw) {
        String value = raw.trim();
        if (value.isEmpty()) {
            return null;
        }
        try {
            int coordinate = Integer.parseInt(value);
            return Math.abs(coordinate) <= COORDINATE_LIMIT ? coordinate : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    @Nullable
    private static Double parsePrice(@Nullable String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty()) {
            return null;
        }
        // A trailing currency tag is allowed but carries no meaning: MMO Econ owns the unit.
        int end = value.indexOf(' ');
        if (end > 0) {
            value = value.substring(0, end);
        }
        try {
            double price = Double.parseDouble(value);
            return price >= 0.0 && !Double.isInfinite(price) ? price : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}