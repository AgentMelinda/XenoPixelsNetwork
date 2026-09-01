package net.bullettrain.xenopixelsmod.client.screen;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/**
 * Hex entry for an ARGB colour, shared by every HUD part editor.
 *
 * <p>Accepts {@code RRGGBB} or {@code AARRGGBB}, with or without a leading {@code #}. A six-digit
 * value keeps the colour's existing alpha rather than forcing it opaque, so typing a swatch copied
 * from elsewhere cannot silently make text solid that was deliberately translucent.
 *
 * <p>Partial and invalid input leaves the colour alone and tints the box red — typing is inherently
 * transient, and resetting to black on every intermediate keystroke would make the field unusable.
 */
@OnlyIn(Dist.CLIENT)
public final class HexColorBox extends EditBox {
    private static final int VALID = 0xFFFFFFFF;
    private static final int INVALID = 0xFFFF6E6E;

    private final IntSupplier current;
    private boolean suppress;

    public HexColorBox(Font font, int x, int y, int width, IntSupplier current, IntConsumer apply) {
        super(font, x, y, width, 18, Component.literal("hex"));
        this.current = current;
        setMaxLength(9);
        setResponder(text -> {
            if (suppress) return;
            Integer parsed = parse(text, current.getAsInt());
            setTextColor(parsed == null ? INVALID : VALID);
            if (parsed != null) apply.accept(parsed);
        });
        syncFromValue();
    }

    /** Re-seed from the live colour, for when the selected part changes underneath the box. */
    public void syncFromValue() {
        suppress = true;
        setValue(String.format("%08X", current.getAsInt()));
        setTextColor(VALID);
        suppress = false;
    }

    private static Integer parse(String raw, int fallbackArgb) {
        String text = raw.trim();
        if (text.startsWith("#")) text = text.substring(1);
        if (text.length() != 6 && text.length() != 8) return null;
        try {
            long value = Long.parseLong(text, 16);
            if (text.length() == 6) {
                // Keep whatever alpha the part already had.
                return (fallbackArgb & 0xFF000000) | (int) (value & 0xFFFFFF);
            }
            return (int) value;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
