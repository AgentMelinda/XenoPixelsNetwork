package net.bullettrain.xenopixelsmod.ui;

/** ARGB from {@code #RRGGBB} / {@code #AARRGGBB}. No Minecraft types. */
public final class UiColors {
    private UiColors() {
    }

    public static int parse(String hex, int fallback) {
        if (hex == null || hex.isBlank()) {
            return fallback;
        }
        String body = hex.charAt(0) == '#' ? hex.substring(1) : hex;
        if (body.length() == 6) {
            body = "FF" + body;
        }
        if (body.length() != 8) {
            return fallback;
        }
        try {
            return (int) Long.parseLong(body, 16);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
