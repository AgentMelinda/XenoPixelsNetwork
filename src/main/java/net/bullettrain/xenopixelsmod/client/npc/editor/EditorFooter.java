package net.bullettrain.xenopixelsmod.client.npc.editor;

import net.minecraft.client.Minecraft;

/**
 * Shared geometry for the paged footer: a page label sitting between its two arrows.
 *
 * <p>The editor and the appearance screen each worked this out for themselves and disagreed, so the
 * label landed on top of the left arrow while the right one was stranded. One source of numbers
 * means they cannot drift again.
 *
 * <p>All values are in UI-canvas units and are measured from {@code rightEdge}, the right-hand limit
 * of the footer strip, so a caller only has to say where the footer ends.
 */
public record EditorFooter(int prevX, int labelX, int labelWidth, int nextX, int arrowWidth) {

    /** Widest label the pager can show, used so the box does not resize as pages change. */
    private static final String WIDEST = "Page 00 / 00";

    public static EditorFooter of(int rightEdge, int arrowWidth) {
        int labelWidth = Minecraft.getInstance().font.width(WIDEST) + 8;
        int nextX = rightEdge - arrowWidth;
        int labelX = nextX - labelWidth;
        int prevX = labelX - arrowWidth;
        return new EditorFooter(prevX, labelX, labelWidth, nextX, arrowWidth);
    }

    /** Centre of the label box, for {@code drawCenteredString}. */
    public int labelCentreX() {
        return labelX + labelWidth / 2;
    }

    /** Left edge of the whole pager, so a caller can keep other footer content clear of it. */
    public int leftEdge() {
        return prevX;
    }
}
