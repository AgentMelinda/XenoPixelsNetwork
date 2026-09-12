package net.bullettrain.xenopixelsmod.plot;

import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.SignText;

import javax.annotation.Nullable;

/**
 * Adapts a {@link SignText} to the {@link PlotSignSyntax} grammar.
 *
 * <p>Lives in the common source set because both sides need it: the server reads the stored text
 * to resolve a purchase, and the client reads the same text to decide whether to render the plot
 * layout. The sign's own vanilla text is the transport, so no extra packet is required and both
 * sides always agree.</p>
 *
 * <p>Unlike a shop sign there is nothing to resolve against a registry — a plot listing is
 * coordinates and a price — so a syntactically valid marker is enough to render.</p>
 */
public final class PlotSignReader {

    private PlotSignReader() {
    }

    /**
     * @return the parsed listing, or {@code null} when this sign is not a plot sign.
     */
    @Nullable
    public static PlotSignData fromSignText(@Nullable SignText text, boolean filtered) {
        if (text == null) {
            return null;
        }
        Component[] messages = text.getMessages(filtered);
        if (messages == null || messages.length < PlotSignSyntax.LINE_COUNT) {
            return null;
        }
        String[] lines = new String[messages.length];
        for (int i = 0; i < messages.length; i++) {
            lines[i] = messages[i] == null ? "" : messages[i].getString();
        }
        return PlotSignSyntax.parse(lines);
    }
}