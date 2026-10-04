package net.bullettrain.xenopixelsmod.client.npc.speech;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

/** Turns dialogue's legacy colour markers into styled text before font wrapping. */
public final class BubbleText {
    private BubbleText() {}

    public static Component styled(String text) {
        String value = text == null ? "" : text;
        MutableComponent result = Component.empty();
        Style style = Style.EMPTY;
        StringBuilder run = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '§' && i + 1 < value.length()) {
                ChatFormatting code = ChatFormatting.getByCode(value.charAt(i + 1));
                if (code != null) {
                    if (!run.isEmpty()) result.append(Component.literal(run.toString()).setStyle(style));
                    run.setLength(0);
                    style = code == ChatFormatting.RESET ? Style.EMPTY : style.applyLegacyFormat(code);
                    i++;
                    continue;
                }
            }
            run.append(c);
        }
        if (!run.isEmpty()) result.append(Component.literal(run.toString()).setStyle(style));
        return result;
    }
}
