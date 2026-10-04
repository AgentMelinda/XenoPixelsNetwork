package net.bullettrain.xenopixelsmod.npc;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

/**
 * Colour codes in NPC names and titles: {@code &0}-{@code &9}, {@code &a}-{@code &f} colours,
 * {@code &l &o &n &m &k} formats, {@code &r} reset, and {@code &#RRGGBB} hex. {@code §} works in
 * place of {@code &}. Codes are lower-case only after {@code &}, so ordinary text such as "R&D"
 * stays as written.
 *
 * <p>The stored name keeps its codes; {@link #component} builds the coloured nameplate and
 * {@link #plain} gives the text chat, bubbles and lists should show.
 */
public final class XenoNpcNameFormat {
    private XenoNpcNameFormat() {}

    /** The text without any colour or format codes. */
    public static String plain(String raw) {
        if (raw == null || raw.isEmpty()) return "";
        StringBuilder out = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); ) {
            int skip = codeLength(raw, i);
            if (skip > 0) {
                i += skip;
            } else {
                out.append(raw.charAt(i++));
            }
        }
        return out.toString();
    }

    /** The text as a component, one styled part per run between codes. */
    public static MutableComponent component(String raw) {
        MutableComponent root = Component.empty();
        if (raw == null || raw.isEmpty()) return root;
        Style style = Style.EMPTY;
        StringBuilder run = new StringBuilder();
        for (int i = 0; i < raw.length(); ) {
            int skip = codeLength(raw, i);
            if (skip == 0) {
                run.append(raw.charAt(i++));
                continue;
            }
            if (!run.isEmpty()) {
                root.append(Component.literal(run.toString()).withStyle(style));
                run.setLength(0);
            }
            style = apply(style, raw, i, skip);
            i += skip;
        }
        if (!run.isEmpty()) root.append(Component.literal(run.toString()).withStyle(style));
        return root;
    }

    /** Length of the code starting at {@code i} (2 for {@code &6}, 8 for {@code &#RRGGBB}), else 0. */
    private static int codeLength(String s, int i) {
        char c = s.charAt(i);
        if ((c != '&' && c != '§') || i + 1 >= s.length()) return 0;
        char next = s.charAt(i + 1);
        if (next == '#' && i + 8 <= s.length() && isHex(s, i + 2, i + 8)) return 8;
        char key = c == '§' ? Character.toLowerCase(next) : next;
        return ChatFormatting.getByCode(key) != null && "0123456789abcdefklmnor".indexOf(key) >= 0 ? 2 : 0;
    }

    private static boolean isHex(String s, int from, int to) {
        for (int i = from; i < to; i++) {
            if (Character.digit(s.charAt(i), 16) < 0) return false;
        }
        return true;
    }

    private static Style apply(Style style, String s, int i, int length) {
        if (length == 8) {
            return Style.EMPTY.withColor(TextColor.fromRgb(Integer.parseInt(s.substring(i + 2, i + 8), 16)));
        }
        ChatFormatting format = ChatFormatting.getByCode(Character.toLowerCase(s.charAt(i + 1)));
        if (format == null || format == ChatFormatting.RESET) return Style.EMPTY;
        // As in vanilla chat: a colour starts fresh, a format adds to what is there.
        return format.isColor() ? Style.EMPTY.withColor(format) : style.applyFormat(format);
    }
}
