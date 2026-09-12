package net.bullettrain.xenopixelsmod.shop;

import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.Locale;

/**
 * The XenoPixels sign-shop grammar.
 *
 * <p>Four-line form, where line 1 is the trigger and lines 2-4 are the payload:</p>
 * <pre>
 * [XPSHOP]
 * minecraft:diamond
 * 64x
 * 100
 * </pre>
 *
 * <p>Compact form, for signs where only line 1 is available. Anything after the marker is split on
 * whitespace into the same three fields:</p>
 * <pre>
 * [XPSHOP] minecraft:diamond 64x 100
 * </pre>
 *
 * <p>Nothing here touches a registry, a level, or a block entity, so the grammar is unit-testable
 * without a running game. Target resolution is {@link SignShopTarget}'s job.</p>
 */
public final class SignShopSyntax {

    /** Activation marker. Matched case-insensitively and with surrounding whitespace allowed. */
    public static final String MARKER = "[XPSHOP]";

    /** Largest quantity a single purchase may request. */
    public static final int MAX_QUANTITY = 9999;

    /** How many lines the four-line form occupies. */
    public static final int LINE_COUNT = 4;

    private SignShopSyntax() {
    }

    /**
     * @return the parsed shop, or {@code null} when {@code lines} is not a valid shop sign. A
     *         {@code null} result is the normal "this is just a sign" path, not an error.
     */
    @Nullable
    public static SignShopData parse(String[] lines) {
        if (lines == null || lines.length < LINE_COUNT) {
            return null;
        }
        String head = lines[0] == null ? "" : lines[0].trim();
        int markerEnd = markerEnd(head);
        if (markerEnd < 0) {
            return null;
        }
        String inline = head.substring(markerEnd).trim();
        if (!inline.isEmpty()) {
            return parseFields(inline.split("\\s+"));
        }
        String target = lines[1] == null ? "" : lines[1].trim();
        String quantity = lines[2] == null ? "" : lines[2].trim();
        String price = lines[3] == null ? "" : lines[3].trim();
        return parseFields(new String[]{target, quantity, price});
    }

    /** Serializes back to the four-line form, marker included. */
    public static String[] serialize(SignShopData data) {
        return new String[]{
                MARKER,
                data.targetId().toString(),
                data.quantity() + "x",
                formatPrice(data.price())
        };
    }

    /** True when line 1 carries the activation marker, regardless of payload validity. */
    public static boolean isShopSign(@Nullable String[] lines) {
        if (lines == null || lines.length == 0 || lines[0] == null) {
            return false;
        }
        return markerEnd(lines[0].trim()) >= 0;
    }

    private static int markerEnd(String head) {
        if (head.length() < MARKER.length()) {
            return -1;
        }
        String prefix = head.substring(0, MARKER.length());
        return prefix.equalsIgnoreCase(MARKER) ? MARKER.length() : -1;
    }

    @Nullable
    private static SignShopData parseFields(String[] fields) {
        if (fields.length < 3) {
            return null;
        }
        String target = fields[0].trim();
        if (target.isEmpty()) {
            return null;
        }
        ResourceLocation id = ResourceLocation.tryParse(target);
        if (id == null) {
            return null;
        }
        Integer quantity = parseQuantity(fields[1]);
        if (quantity == null) {
            return null;
        }
        Double price = parsePrice(fields[2]);
        if (price == null) {
            return null;
        }
        return new SignShopData(id, quantity, price);
    }

    @Nullable
    private static Integer parseQuantity(String raw) {
        String value = raw.trim().toLowerCase(Locale.ROOT);
        if (value.length() < 2 || !value.endsWith("x")) {
            return null;
        }
        try {
            int quantity = Integer.parseInt(value.substring(0, value.length() - 1));
            return quantity >= 1 && quantity <= MAX_QUANTITY ? quantity : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    @Nullable
    private static Double parsePrice(String raw) {
        String value = raw.trim();
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

    /** Trailing-zero-free rendering, so {@code 100.0} reads back as {@code 100}. */
    public static String formatPrice(double price) {
        if (price == Math.rint(price) && !Double.isInfinite(price)) {
            return Long.toString((long) price);
        }
        return Double.toString(price);
    }
}