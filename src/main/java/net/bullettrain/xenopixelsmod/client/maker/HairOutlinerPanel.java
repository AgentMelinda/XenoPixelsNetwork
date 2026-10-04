package net.bullettrain.xenopixelsmod.client.maker;

import net.bullettrain.xenopixelsmod.hair.HairMakerDocument;
import net.bullettrain.xenopixelsmod.hair.HairStrandModel;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;

/**
 * Left outliner for Hair Studio: faces + strand slots. Original Xeno widget logic
 * (design idea from DMZ 2.2 outliner; no copied code).
 */
public final class HairOutlinerPanel {
    public static final int ROW_H = 12;
    public static final int FACE_H = 14;

    private static final int GOLD = 0xFFFFC14A;
    private static final int LIGHT = 0xFFE7EDF3;
    private static final int MUTED = 0xFF6E9680;
    /** Bright green selected-row fill (matches viewport segment highlight). */
    private static final int SELECT = 0x9900E676;
    private static final int EMPTY = 0xFF4A5A50;

    private int scroll;

    public int scroll() {
        return scroll;
    }

    public void scrollBy(int delta) {
        scroll = Math.max(0, scroll + delta);
    }

    public void resetScroll() {
        scroll = 0;
    }

    public void render(
            GuiGraphics g,
            Font font,
            HairMakerDocument document,
            int x,
            int y,
            int w,
            int h) {
        if (document == null || w <= 0 || h <= 0) {
            return;
        }
        g.drawString(font, "Outliner", x + 8, y + 6, GOLD, false);
        int cy = y + 22;
        int bottom = y + h - 6;
        int rowsSkipped = 0;
        for (String faceName : HairMakerDocument.FACE_NAMES) {
            if (rowsSkipped < scroll) {
                rowsSkipped++;
                continue;
            }
            if (cy + FACE_H > bottom) {
                break;
            }
            boolean faceSelected = faceName.equals(document.face());
            if (faceSelected) {
                g.fill(x + 4, cy - 1, x + w - 4, cy + FACE_H - 2, SELECT);
            }
            int visible = 0;
            for (HairStrandModel s : document.faceStrands(faceName)) {
                if (s.visible()) {
                    visible++;
                }
            }
            g.drawString(font, faceName + "  (" + visible + "/"
                            + document.faceCapacity(faceName) + ")",
                    x + 8, cy, faceSelected ? LIGHT : MUTED, false);
            cy += FACE_H;

            if (!faceSelected) {
                continue;
            }
            List<HairStrandModel> strands = document.faceStrands(faceName);
            for (int i = 0; i < strands.size(); i++) {
                if (cy + ROW_H > bottom) {
                    break;
                }
                HairStrandModel strand = strands.get(i);
                boolean sel = i == document.strandIndex();
                if (sel) {
                    g.fill(x + 10, cy - 1, x + w - 4, cy + ROW_H - 2, SELECT);
                }
                String mark = strand.visible() ? "■" : "·";
                int color = strand.visible() ? (sel ? LIGHT : MUTED) : EMPTY;
                g.drawString(font, mark + " [" + i + "] len " + strand.length(),
                        x + 14, cy, color, false);
                cy += ROW_H;
            }
        }
    }

    /**
     * @return true if the click selected a face or strand
     */
    public boolean mouseClicked(
            HairMakerDocument document,
            double uiX,
            double uiY,
            int x,
            int y,
            int w,
            int h,
            Runnable onChanged) {
        if (document == null || uiX < x || uiX >= x + w || uiY < y || uiY >= y + h) {
            return false;
        }
        int cy = y + 22;
        int bottom = y + h - 6;
        int rowsSkipped = 0;
        for (String faceName : HairMakerDocument.FACE_NAMES) {
            if (rowsSkipped < scroll) {
                rowsSkipped++;
                continue;
            }
            if (cy + FACE_H > bottom) {
                break;
            }
            if (uiY >= cy - 1 && uiY < cy + FACE_H - 2) {
                document.face(faceName);
                document.strandIndex(0);
                if (onChanged != null) {
                    onChanged.run();
                }
                return true;
            }
            cy += FACE_H;
            if (!faceName.equals(document.face())) {
                continue;
            }
            List<HairStrandModel> strands = document.faceStrands(faceName);
            for (int i = 0; i < strands.size(); i++) {
                if (cy + ROW_H > bottom) {
                    break;
                }
                if (uiY >= cy - 1 && uiY < cy + ROW_H - 2) {
                    document.strandIndex(i);
                    if (onChanged != null) {
                        onChanged.run();
                    }
                    return true;
                }
                cy += ROW_H;
            }
        }
        return false;
    }
}
