package net.bullettrain.xenopixelsmod.client.ui.atlas;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * A row of atlas tab cells, each drawn at a shipped size.
 *
 * <p>The reference MyNPCs menu has tabs of differing widths - "Inventory" is visibly wider than
 * "AI" - so a single cell width is wrong, but so is stretching one cell per label: the atlas bakes
 * its border proportions in at generation time and a stretched tab reads as a distorted frame.
 *
 * <p>The way out is that the tab shape is generated at a spread of discrete widths
 * ({@code tab_docked_w20} through {@code tab_docked_w68}). Each label initially picks the narrowest
 * of those that holds it, so every cell is still blitted 1:1 and the strip varies with its labels.
 * If the full set needs a narrower strip, cells step down the generated-width ladder and their text
 * follows the strip-wide scale/ellipsis rule below.
 */
public final class AtlasTabStrip extends AbstractWidget {
    /** Generated widths of the docked-tab shape, ascending. */
    private static final int[] TAB_WIDTHS = {20, 28, 36, 44, 52, 60, 68};

    private static final int LABEL_PADDING = 10;

    /**
     * Padding a cell may be squeezed to when the strip has to fit.
     *
     * <p>Still leaves a pixel or two either side of the glyphs, so a squeezed tab reads as tight
     * rather than as clipped - which is the line {@link #MIN_CELL} is drawn at for the unsqueezed
     * case.
     */
    private static final int TIGHT_PADDING = 4;

    /**
     * Smallest cell the strip will use.
     *
     * <p>The narrowest generated tab (20px) technically holds a two-letter label, but the docked-tab
     * sprite spends several pixels on its border, so "AI" ended up wedged against the frame and read
     * as clipped next to its neighbours. Starting at 36 keeps a visible margin on every label while
     * still letting short tabs stay narrower than long ones, which is what the reference menu does.
     */
    private static final int MIN_CELL = 36;


    /**
     * Gap before the detached tail, matching the reference menu.
     *
     * <p>My NPCs runs its content tabs together and then leaves a clear space before Delete, so the
     * two destructive-ish entries at the end read as a separate group rather than as one more
     * content tab. Ours packed all ten together, which made Delete look like somewhere you might
     * land by accident.
     */
    private static final int DETACH_GAP = 8;

    private final List<Component> labels;
    private final IntConsumer onChange;
    private final String[] cellSprite;
    private final int[] cellX;
    private final int[] cellW;
    private final int cellH;
    private final float groupScale;
    private int selected;

    public AtlasTabStrip(int x, int y, List<Component> labels, int selected, IntConsumer onChange) {
        this(x, y, labels, selected, onChange, Integer.MAX_VALUE, -1);
    }

    /**
     * @param maxWidth pixels the strip may occupy; cells step down the generated width ladder until
     *                 they fit. {@code Integer.MAX_VALUE} for "as wide as it likes".
     * @param detachIndex index that starts the detached tail, or negative for one continuous run
     */
    public AtlasTabStrip(int x, int y, List<Component> labels, int selected, IntConsumer onChange,
                         int maxWidth, int detachIndex) {
        super(x, y, 1, XenoAtlasSprites.get(spriteFor(TAB_WIDTHS[0])).height(),
                Component.literal("Tabs"));
        if (labels.isEmpty()) {
            throw new IllegalArgumentException("Tab count must be positive");
        }
        this.labels = List.copyOf(labels);
        this.onChange = onChange;
        this.selected = Math.max(0, Math.min(labels.size() - 1, selected));

        var font = Minecraft.getInstance().font;
        int count = labels.size();
        this.cellSprite = new String[count];
        this.cellX = new int[count];
        this.cellW = new int[count];

        int[] textW = new int[count];
        for (int i = 0; i < count; i++) {
            textW[i] = font.width(labels.get(i));
            this.cellW[i] = widthFor(textW[i] + LABEL_PADDING);
        }

        int gap = detachIndex > 0 && detachIndex < count ? DETACH_GAP : 0;
        shrinkToFit(this.cellW, maxWidth - gap);

        float commonScale = 1.0f;
        for (int i = 0; i < count; i++) {
            commonScale = Math.min(commonScale,
                    Math.max(0, this.cellW[i] - TIGHT_PADDING) / (float) Math.max(1, textW[i]));
        }
        this.groupScale = Math.max(AtlasTextFit.MIN_SCALE, Math.min(1.0f, commonScale));

        int cursor = 0;
        for (int i = 0; i < count; i++) {
            if (i == detachIndex) {
                cursor += DETACH_GAP;
            }
            this.cellSprite[i] = spriteFor(this.cellW[i]);
            this.cellX[i] = cursor;
            cursor += this.cellW[i];
        }
        this.cellH = XenoAtlasSprites.get(cellSprite[0]).height();
        setWidth(cursor);
        setHeight(cellH);
    }

    /**
     * Steps the widest cells down the generated ladder until the strip fits {@code budget}.
     *
     * <p>Narrowing the editor frame to 420 so the visualizer could sit beside it left the ten tabs
     * about one cell wider than the frame, which pushed the last one - X - past the edge. Rather
     * than pick smaller fixed widths and hope, the strip now gives back the slack where there is
     * most of it: the widest cell that can drop a rung does, repeatedly, so long labels lose their
     * padding before short ones lose theirs.
     *
     * <p>Cells may shrink to the smallest generated width. Labels share one strip scale and use an
     * ellipsis below the readable floor, so a long label cannot force the whole tab strip outside
     * its frame.
     *
     * <p>Static and taking its arrays so it can be tested: the rest of this class needs a client
     * font, and this is the part with the arithmetic worth pinning.
     *
     * @param cellW chosen widths, modified in place
     * @param budget pixels available for the cells, excluding any detach gap
     */
    static void shrinkToFit(int[] cellW, int budget) {
        if (budget <= 0) {
            return;
        }
        int total = 0;
        for (int w : cellW) {
            total += w;
        }
        while (total > budget) {
            int victim = -1;
            for (int i = 0; i < cellW.length; i++) {
                int smaller = nextSmaller(cellW[i]);
                if (smaller < 0) {
                    continue;
                }
                if (victim < 0 || cellW[i] > cellW[victim]) {
                    victim = i;
                }
            }
            if (victim < 0) {
                return;
            }
            int smaller = nextSmaller(cellW[victim]);
            total -= cellW[victim] - smaller;
            cellW[victim] = smaller;
        }
    }

    /** The next generated rung down, or -1 when already at the minimum cell. */
    private static int nextSmaller(int current) {
        for (int i = TAB_WIDTHS.length - 1; i >= 0; i--) {
            if (TAB_WIDTHS[i] < current && TAB_WIDTHS[i] >= MIN_CELL) {
                return TAB_WIDTHS[i];
            }
        }
        return -1;
    }

    private static String spriteFor(int width) {
        return "tab_docked_w" + width;
    }

    /** The narrowest generated tab that holds {@code needed} pixels of label. */
    private static int widthFor(int needed) {
        int wanted = Math.max(MIN_CELL, needed);
        for (int w : TAB_WIDTHS) {
            if (w >= wanted) {
                return w;
            }
        }
        return TAB_WIDTHS[TAB_WIDTHS.length - 1];
    }

    /** Total strip width for a label set, so a caller can centre it before constructing. */
    public static int stripWidth(List<Component> labels) {
        var font = Minecraft.getInstance().font;
        int total = 0;
        for (Component label : labels) {
            total += widthFor(font.width(label) + LABEL_PADDING);
        }
        return total;
    }

    public int selected() {
        return selected;
    }

    /** Bounds of each cell as {x offset, width}, for callers aligning content under a tab. */
    public List<int[]> cells() {
        List<int[]> out = new ArrayList<>(cellX.length);
        for (int i = 0; i < cellX.length; i++) {
            out.add(new int[]{cellX[i], cellW[i]});
        }
        return out;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        var font = Minecraft.getInstance().font;
        for (int i = 0; i < labels.size(); i++) {
            int x = getX() + cellX[i];
            boolean active = i == selected;
            boolean hovered = mouseX >= x && mouseX < x + cellW[i]
                    && mouseY >= getY() && mouseY < getY() + cellH;

            // Selection and hover are palette swaps on the same cell, so nothing resizes.
            if (active) {
                XenoAtlasSprites.blit(graphics, cellSprite[i], XenoAtlasSprites.Theme.GOLD,
                        x, getY());
            } else {
                XenoAtlasSprites.blit(graphics, cellSprite[i], x, getY());
            }

            int color = active ? 0xFF2A1B00 : hovered ? 0xFFFFFFFF : 0xFFB8D8EA;
            Component fullLabel = labels.get(i);
            int available = Math.max(1, cellW[i] - TIGHT_PADDING);
            String visible = AtlasTextFit.fit(fullLabel.getString(), available, groupScale,
                    font::width);
            graphics.pose().pushPose();
            graphics.pose().translate(x + cellW[i] / 2.0, getY() + (cellH - 8) / 2.0, 0.0);
            graphics.pose().scale(groupScale, groupScale, 1.0f);
            graphics.drawCenteredString(font, visible, 0, 0, color);
            graphics.pose().popPose();
            if (hovered && !visible.equals(fullLabel.getString())) {
                graphics.renderTooltip(font, fullLabel, mouseX, mouseY);
            }
        }
    }


    /**
     * Silences vanilla's click.
     *
     * <p>{@code AbstractWidget.mouseClicked} plays {@code UI_BUTTON_CLICK} before {@code onClick}
     * runs, so without this every control would click twice - vanilla's, then DragonMineZ's. The
     * DMZ sound is played from {@code onClick} instead, via {@link AtlasSound}.
     */
    @Override
    public void playDownSound(net.minecraft.client.sounds.SoundManager handler) {
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        int local = (int) (mouseX - getX());
        for (int i = 0; i < labels.size(); i++) {
            if (local >= cellX[i] && local < cellX[i] + cellW[i]) {
                AtlasSound.click();
                selected = i;
                if (onChange != null) {
                    onChange.accept(i);
                }
                return;
            }
        }
    }
}
