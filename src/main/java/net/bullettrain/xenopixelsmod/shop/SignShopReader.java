package net.bullettrain.xenopixelsmod.shop;

import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;

import javax.annotation.Nullable;

/**
 * Adapts a {@link SignText} to the {@link SignShopSyntax} grammar.
 *
 * <p>Lives in the common source set because both sides need it: the server reads the stored text
 * back after an edit to decide whether the sign is a shop, and the client reads the same text to
 * decide whether to render the shop layout. The sign's own vanilla text is the transport, so no
 * extra packet is required and both sides always agree.</p>
 *
 * <p>A sign is only a shop when the marker is present <em>and</em> the target resolves to a real
 * item. A sign that carries the marker but names an unregistered id is treated as a plain sign so
 * it never renders as a broken listing.</p>
 */
public final class SignShopReader {

    private SignShopReader() {
    }

    /**
     * @return the parsed shop, or {@code null} when this sign is not a renderable shop.
     */
    @Nullable
    public static SignShopData fromSignText(@Nullable SignText text, boolean filtered) {
        if (text == null) {
            return null;
        }
        String[] lines = linesOf(text, filtered);
        if (lines == null) {
            return null;
        }
        SignShopData data = SignShopSyntax.parse(lines);
        if (data == null || !SignShopTarget.isResolvable(data.targetId())) {
            return null;
        }
        return data;
    }

    /** Front face first, then back. {@code null} when neither face is a resolvable shop. */
    @Nullable
    public static SignShopData listing(@Nullable SignBlockEntity sign) {
        if (sign == null) {
            return null;
        }
        SignShopData data = fromSignText(sign.getText(true), false);
        return data != null ? data : fromSignText(sign.getText(false), false);
    }

    /** True when this face carries the shop marker, even if the payload is not yet valid. */
    public static boolean hasMarker(@Nullable SignText text) {
        return SignShopSyntax.isShopSign(linesOf(text, false));
    }

    @Nullable
    static String[] linesOf(@Nullable SignText text, boolean filtered) {
        if (text == null) {
            return null;
        }
        Component[] messages = text.getMessages(filtered);
        if (messages == null || messages.length < SignShopSyntax.LINE_COUNT) {
            return null;
        }
        String[] lines = new String[messages.length];
        for (int i = 0; i < messages.length; i++) {
            lines[i] = messages[i] == null ? "" : messages[i].getString();
        }
        return lines;
    }
}