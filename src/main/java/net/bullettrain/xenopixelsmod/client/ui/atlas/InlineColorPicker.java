package net.bullettrain.xenopixelsmod.client.ui.atlas;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.OptionalInt;
import java.util.function.Consumer;

/**
 * A colour picker that opens over the current screen instead of replacing it, drawn from the
 * generated atlas: a gold frame, the hue/saturation chart, a value bar, new/old swatches, a hex
 * field and OK/Cancel.
 *
 * <p>Not a widget in the host's list: the host owns one instance, draws it last inside its UI
 * scale, and offers it every mouse and key event first ({@link #mouseClicked} and friends answer
 * true while open, so nothing underneath reacts). Replacing the screen, as the old picker did,
 * re-ran the host's {@code init()} on return and lost scroll position and focus.
 *
 * <p>All coordinates are the host's UI coordinates.
 */
public final class InlineColorPicker {
    public static final String PANEL = "xeno_color_picker_panel";
    private static final int W = 210;
    private static final int H = 150;
    private static final int SQ_X = 12;
    private static final int SQ_Y = 38;
    private static final int SQ = 96;
    private static final int BAR_X = 126;
    private static final int BAR_W = 6;
    private static final int COL_X = 144;
    private static final int TITLE = 0xFF78D5FF;
    private static final int LIGHT = 0xFFE7EDF3;
    private static final int MUTED = 0xFFB7A57A;

    private boolean open;
    private int x;
    private int y;
    private ColorPickerModel model = new ColorPickerModel(0xFFFFFF);
    private int original;
    private Consumer<String> onConfirm = s -> {};
    private Object owner;
    private EditBox hex;
    private AtlasButton ok;
    private AtlasButton cancel;
    /** 0 none, 1 square, 2 value bar. */
    private int drag;

    public boolean isOpen() {
        return open;
    }

    /** True while the picker belongs to {@code swatchOwner}: that swatch draws gold. */
    public boolean isOpenFor(Object swatchOwner) {
        return open && owner == swatchOwner;
    }

    /**
     * Opens next to an anchor point, kept inside {@code uiW x uiH}.
     *
     * @param initialHex the field's current text; unreadable text starts from white
     * @param onConfirm  receives {@code #RRGGBB} on OK
     */
    public void open(Object swatchOwner, int anchorX, int anchorY, int uiW, int uiH, String initialHex,
                     Consumer<String> onConfirm) {
        this.owner = swatchOwner;
        this.onConfirm = onConfirm == null ? s -> {} : onConfirm;
        OptionalInt parsed = ColorPickerModel.parseStrictHex(initialHex);
        original = parsed.orElse(net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile
                .parseHexColor(initialHex).orElse(0xFFFFFF));
        model = ColorPickerModel.opening(original);
        x = Math.max(4, Math.min(uiW - W - 4, anchorX + 4));
        y = Math.max(4, Math.min(uiH - H - 4, anchorY - H / 2));
        Font font = Minecraft.getInstance().font;
        hex = new EditBox(font, x + COL_X, y + 62, 60, 14, Component.literal("Hex"));
        hex.setMaxLength(9);
        hex.setValue(model.hex());
        hex.setResponder(text -> {
            // Only a complete code moves the colour; partial typing does not jump it around.
            OptionalInt rgb = ColorPickerModel.parseStrictHex(text);
            if (rgb.isPresent() && rgb.getAsInt() != model.rgb()) {
                model.setRgb(rgb.getAsInt());
            }
        });
        ok = new AtlasButton(x + COL_X, y + 96, Component.literal("OK"), "xeno_btn_w60_h20",
                b -> confirm());
        cancel = new AtlasButton(x + COL_X, y + 120, Component.literal("Cancel"), "xeno_btn_w60_h20",
                b -> close());
        drag = 0;
        open = true;
    }

    public void close() {
        open = false;
        owner = null;
        drag = 0;
    }

    private void confirm() {
        String value = model.hex();
        close();
        onConfirm.accept(value);
    }

    private void syncHex() {
        if (hex != null && !hex.isFocused()) {
            hex.setValue(model.hex());
        }
    }

    // ---------------------------------------------------------------- render

    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (!open) return;
        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        XenoAtlasSprites.blit(g, PANEL, XenoAtlasSprites.Theme.BLUE, x, y);
        Font font = Minecraft.getInstance().font;
        g.drawString(font, "Colour", x + 10, y + 8, TITLE, false);

        // Hue/saturation chart at full value, darkened to the current value by an overlay whose
        // alpha is 1 - v: exactly v * rgb, so one generated chart serves every value.
        XenoAtlasSprites.blit(g, "xeno_hsv_frame", XenoAtlasSprites.Theme.BLUE, x + SQ_X - 6, y + SQ_Y - 6);
        XenoAtlasSprites.blit(g, "xeno_hsv_square", XenoAtlasSprites.Theme.BLUE, x + SQ_X, y + SQ_Y);
        int shade = Math.round((1.0f - model.value()) * 255.0f);
        if (shade > 0) {
            g.fill(x + SQ_X, y + SQ_Y, x + SQ_X + SQ, y + SQ_Y + SQ, shade << 24);
        }
        int cx = x + SQ_X + Math.round(model.hue() * (SQ - 1));
        int cy = y + SQ_Y + Math.round((1.0f - model.saturation()) * (SQ - 1));
        crosshair(g, cx, cy);

        XenoAtlasSprites.blit(g, "xeno_value_frame", XenoAtlasSprites.Theme.BLUE, x + BAR_X - 6, y + SQ_Y - 6);
        g.fillGradient(x + BAR_X, y + SQ_Y, x + BAR_X + BAR_W, y + SQ_Y + SQ,
                0xFF000000 | model.fullValueRgb(), 0xFF000000);
        int vy = y + SQ_Y + Math.round((1.0f - model.value()) * (SQ - 1));
        g.fill(x + BAR_X - 2, vy, x + BAR_X + BAR_W + 2, vy + 1, 0xFFFFFFFF);

        // New and old swatches.
        swatch(g, x + COL_X, y + 32, model.rgb());
        swatch(g, x + COL_X + 30, y + 32, original);
        g.drawString(font, "new", x + COL_X, y + 52, MUTED, false);
        g.drawString(font, "old", x + COL_X + 30, y + 52, MUTED, false);

        hex.render(g, mouseX, mouseY, partialTick);
        ok.render(g, mouseX, mouseY, partialTick);
        cancel.render(g, mouseX, mouseY, partialTick);
        g.pose().popPose();
    }

    private static void swatch(GuiGraphics g, int sx, int sy, int rgb) {
        XenoAtlasSprites.blit(g, "xeno_swatch_frame", XenoAtlasSprites.Theme.BLUE, sx, sy);
        g.fill(sx + 3, sy + 3, sx + 13, sy + 15, 0xFF000000 | rgb);
    }

    private static void crosshair(GuiGraphics g, int cx, int cy) {
        g.fill(cx - 5, cy, cx + 6, cy + 1, 0xFF000000);
        g.fill(cx, cy - 5, cx + 1, cy + 6, 0xFF000000);
        g.fill(cx - 4, cy, cx + 5, cy + 1, LIGHT);
        g.fill(cx, cy - 4, cx + 1, cy + 5, LIGHT);
    }

    // ---------------------------------------------------------------- input

    private boolean inside(double mx, double my) {
        return mx >= x && mx < x + W && my >= y && my < y + H;
    }

    /** Consumes every click while open; a click outside the panel cancels. */
    public boolean mouseClicked(double mx, double my, int button) {
        if (!open) return false;
        if (!inside(mx, my)) {
            close();
            return true;
        }
        if (button != 0) return true;
        if (mx >= x + SQ_X - 2 && mx < x + SQ_X + SQ + 2 && my >= y + SQ_Y - 2 && my < y + SQ_Y + SQ + 2) {
            drag = 1;
            hex.setFocused(false);
            pickAt(mx, my);
            return true;
        }
        if (mx >= x + BAR_X - 4 && mx < x + BAR_X + BAR_W + 4 && my >= y + SQ_Y - 2 && my < y + SQ_Y + SQ + 2) {
            drag = 2;
            hex.setFocused(false);
            pickAt(mx, my);
            return true;
        }
        if (ok.mouseClicked(mx, my, button) || cancel.mouseClicked(mx, my, button)) return true;
        hex.setFocused(hex.mouseClicked(mx, my, button));
        return true;
    }

    public boolean mouseDragged(double mx, double my, int button) {
        if (!open) return false;
        if (drag != 0) pickAt(mx, my);
        return true;
    }

    public boolean mouseReleased(double mx, double my, int button) {
        if (!open) return false;
        drag = 0;
        return true;
    }

    private void pickAt(double mx, double my) {
        // Clamped by the model, so dragging past an edge pins to it instead of letting go.
        double fy = (my - (y + SQ_Y)) / (SQ - 1.0);
        if (drag == 1) {
            model.pickSquare((mx - (x + SQ_X)) / (SQ - 1.0), fy);
        } else if (drag == 2) {
            model.pickValue(fy);
        }
        syncHex();
    }

    public boolean keyPressed(int key, int scan, int modifiers) {
        if (!open) return false;
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            confirm();
            return true;
        }
        if (hex.isFocused()) {
            hex.keyPressed(key, scan, modifiers);
        }
        return true;
    }

    public boolean charTyped(char c, int modifiers) {
        if (!open) return false;
        if (hex.isFocused()) hex.charTyped(c, modifiers);
        return true;
    }

    public boolean mouseScrolled() {
        return open;
    }
}
