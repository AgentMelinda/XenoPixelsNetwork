package net.bullettrain.xenopixelsmod.anim;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Parses {@code !clip*} chat lines. NeoForge {@code ServerChatEvent.getRawText()} is
 * empty when the submitted component is not root {@code PlainTextContents}, so the
 * decorated string is a fallback and {@code <name> !cliplist} is unwrapped.
 */
public final class XenoClipChat {
    public static final int LINE_LIMIT = 240;

    private XenoClipChat() {}

    public static String typedLine(String rawText, String messageString) {
        if (rawText != null && !rawText.isBlank()) {
            return rawText.trim();
        }
        String decorated = messageString == null ? "" : messageString.trim();
        if (decorated.startsWith("!")) {
            return decorated;
        }
        int bang = decorated.indexOf(" !");
        if (bang >= 0) {
            return decorated.substring(bang + 1).trim();
        }
        return decorated;
    }

    public static String command(String typed) {
        if (typed == null) {
            return "";
        }
        String text = typed.trim();
        if (!text.startsWith("!")) {
            return "";
        }
        String rest = text.substring(1).trim();
        int space = rest.indexOf(' ');
        String cmd = space < 0 ? rest : rest.substring(0, space);
        return cmd.toLowerCase(Locale.ROOT);
    }

    public static boolean isHandled(String typed) {
        String cmd = command(typed);
        return "clip".equals(cmd) || "cliplist".equals(cmd)
                || "clipstop".equals(cmd) || "cliphelp".equals(cmd);
    }

    public static String[] parts(String typed) {
        if (typed == null || typed.isBlank()) {
            return new String[0];
        }
        String text = typed.trim();
        if (text.startsWith("!")) {
            text = text.substring(1).trim();
        }
        if (text.isEmpty()) {
            return new String[0];
        }
        return text.split("\\s+");
    }

    public static List<String> chunkNames(String prefix, List<String> names) {
        List<String> lines = new ArrayList<>();
        if (names == null || names.isEmpty()) {
            return lines;
        }
        String head = prefix == null ? "" : prefix;
        StringBuilder line = new StringBuilder(head);
        boolean first = true;
        for (String name : names) {
            if (name == null || name.isBlank()) {
                continue;
            }
            String piece = first ? name : ", " + name;
            if (!first && line.length() + piece.length() > LINE_LIMIT) {
                lines.add(line.toString());
                line = new StringBuilder(name);
                first = false;
                continue;
            }
            line.append(piece);
            first = false;
        }
        if (!line.isEmpty() && !line.toString().equals(head)) {
            lines.add(line.toString());
        }
        return lines;
    }
}
