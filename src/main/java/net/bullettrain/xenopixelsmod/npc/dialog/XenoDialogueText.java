package net.bullettrain.xenopixelsmod.npc.dialog;

import java.util.Locale;

/**
 * Placeholder and colour handling for dialogue text.
 *
 * <p>Kept apart from the screen so the same substitution runs wherever text is shown, and so it can
 * be tested without a client.
 *
 * <p>Supported, per {@code doco.md} section 6: {@code {player}} for the viewer's name, {@code {npc}}
 * for the speaker, and {@code &}-prefixed colour codes. Colour is translated to the section sign
 * only for codes Minecraft actually defines, so a stray ampersand in ordinary prose survives intact
 * instead of eating the character after it.
 */
public final class XenoDialogueText {

    /** The characters Minecraft accepts after a formatting sign. */
    private static final String CODES = "0123456789abcdefklmnor";

    private XenoDialogueText() {
    }

    /** Substitutes placeholders and then colour codes. */
    public static String resolve(String raw, String playerName, String npcName) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        String text = raw
                .replace("{player}", playerName == null ? "" : playerName)
                .replace("{npc}", npcName == null ? "" : npcName);
        return colorize(text);
    }

    /**
     * Turns {@code &a} into the section sign form.
     *
     * <p>{@code &&} is an escape for a literal ampersand, and an {@code &} followed by anything that
     * is not a formatting code is left alone - a dialogue saying "tea &amp; biscuits" should read
     * that way rather than losing a letter.
     */
    public static String colorize(String text) {
        if (text == null || text.indexOf('&') < 0) {
            return text == null ? "" : text;
        }
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c != '&' || i + 1 >= text.length()) {
                out.append(c);
                continue;
            }
            char next = text.charAt(i + 1);
            if (next == '&') {
                out.append('&');
                i++;
            } else if (CODES.indexOf(Character.toLowerCase(next)) >= 0) {
                out.append('§').append(Character.toLowerCase(next));
                i++;
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    /**
     * Substitutes the player placeholder in a command option.
     *
     * <p>{@code @dp} is the reference's spelling for "the player who is talking". The name is not
     * quoted or escaped here because a Minecraft selector has no quoting to speak of; instead the
     * runtime refuses to run a command at all unless an operator has enabled it, which is the real
     * control.
     */
    public static String resolveCommand(String command, String playerName) {
        if (command == null || command.isBlank()) {
            return "";
        }
        String resolved = command.trim().replace("@dp", playerName == null ? "" : playerName);
        return resolved.startsWith("/") ? resolved.substring(1) : resolved;
    }

    /** Lowercases an id the way refs are compared, so case in a pack does not matter. */
    public static String normalizeId(String id) {
        return id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
    }
}
