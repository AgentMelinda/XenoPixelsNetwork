package net.bullettrain.xenopixelsmod.client.npc.dialog;

import net.bullettrain.xenopixelsmod.client.npc.speech.SpeechBubbleLayout;
import net.bullettrain.xenopixelsmod.client.npc.speech.BubbleText;
import net.bullettrain.xenopixelsmod.client.ui.atlas.XenoAtlasSprites;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;
import java.util.ArrayList;

/**
 * How a dialogue option's bubble is sized, separately from how it is drawn.
 *
 * <p>The same split {@link SpeechBubbleLayout} makes, and for the same reason: the arithmetic can
 * be tested without a running client, and the drawing cannot.
 *
 * <p>Options reuse the speech-bubble sprites rather than getting art of their own. They are the
 * same object - something an NPC or a player says - and generating a second family of nearly
 * identical bubbles would be work with nothing to show for it.
 */
public final class DialogueBubbleLayout {

    /** Vertical gap between the NPC's line and the first option, in GUI pixels. */
    public static final int GAP_BELOW_LINE = 6;

    /**
     * Vertical gap between two options.
     *
     * <p>Wide enough to clear the tail. Every bubble sprite ends in a tail worth
     * {@link SpeechBubbleLayout#TAIL_HEIGHT_FRACTION} of its height - about six pixels on a
     * one-line bubble - so the old three-pixel gap left each tail almost touching the bubble below
     * it. The quads never actually overlapped, but the stack read as crowded, which is the part a
     * player notices.
     */
    public static final int GAP_BETWEEN = 9;

    /**
     * Options are narrower than the line above them.
     *
     * <p>A choice reads as a reply to the thing being said, not as another statement, and making
     * them the same width loses that. Three quarters is enough to be clearly subordinate while
     * still holding a sentence.
     */
    private static final float WIDTH_FRACTION = 0.75f;

    private DialogueBubbleLayout() {
    }

    /** Widest line an option bubble can hold, in GUI pixels. */
    public static int textBudget() {
        return Math.round(SpeechBubbleLayout.textBudget() * WIDTH_FRACTION);
    }

    /** Wraps one option's text, clamped the way a speech bubble is. */
    public static List<FormattedCharSequence> wrap(Font font, String text) {
        List<FormattedCharSequence> lines =
                font.split(BubbleText.styled(text), textBudget());
        int max = SpeechBubbleLayout.maxLines();
        return lines.size() <= max ? lines : lines.subList(0, max);
    }

    /**
     * The sprite for an option holding {@code lines}.
     *
     * <p>Deliberately the same resolver the speech bubble uses, so an option and a line of the same
     * length pick the same cell and the stack looks like one conversation rather than two widgets.
     */
    public static String spriteFor(Font font, List<FormattedCharSequence> lines) {
        return SpeechBubbleLayout.spriteFor(font, lines);
    }

    /** Total height of a stack of option bubbles, in GUI pixels, including the gaps. */
    public static int stackHeight(Font font, List<String> options) {
        if (options == null || options.isEmpty()) {
            return 0;
        }
        int total = GAP_BELOW_LINE;
        for (int i = 0; i < options.size(); i++) {
            if (i > 0) {
                total += GAP_BETWEEN;
            }
            total += XenoAtlasSprites.get(spriteFor(font, wrap(font, options.get(i)))).height();
        }
        return total;
    }

    /** Height of the rendered answer group, including the gaps used by stack or two-column layout. */
    public static int groupHeight(List<Integer> heights, boolean rowChoices) {
        if (heights == null || heights.isEmpty()) return 0;
        if (!rowChoices) {
            return heights.stream().mapToInt(height -> Math.max(0, height)).sum()
                    + GAP_BETWEEN * (heights.size() - 1);
        }
        int rows = 0;
        for (int i = 0; i < heights.size(); i += 2) {
            rows += Math.max(Math.max(0, heights.get(i)), i + 1 < heights.size()
                    ? Math.max(0, heights.get(i + 1)) : 0);
        }
        return rows + GAP_BETWEEN * ((heights.size() + 1) / 2 - 1);
    }

    /**
     * Local Y position for each vertically stacked answer, ordered top to bottom.
     *
     * <p>The bottom edge of the final bubble sits at the NPC's tail anchor. With the billboard's
     * negative Y scale, negative local Y moves a bubble upward into view. Keeping this arithmetic
     * here means rendering and its hit boxes can share the same placement rule.
     */
    public static List<Integer> stackTops(List<Integer> heights) {
        if (heights == null || heights.isEmpty()) {
            return List.of();
        }
        int total = 0;
        for (int height : heights) {
            total += Math.max(0, height);
        }
        total += GAP_BETWEEN * (heights.size() - 1);
        int y = -total;
        List<Integer> tops = new ArrayList<>(heights.size());
        for (int height : heights) {
            tops.add(y);
            y += Math.max(0, height) + GAP_BETWEEN;
        }
        return List.copyOf(tops);
    }

    /** Local Y positions for rows of two choices, ordered left-to-right. */
    public static List<Integer> rowTops(List<Integer> heights) {
        if (heights == null || heights.isEmpty()) {
            return List.of();
        }
        List<Integer> rowHeights = new ArrayList<>();
        for (int start = 0; start < heights.size(); start += 2) {
            int max = 0;
            for (int i = start; i < Math.min(start + 2, heights.size()); i++) {
                max = Math.max(max, Math.max(0, heights.get(i)));
            }
            rowHeights.add(max);
        }
        List<Integer> rowBottoms = new ArrayList<>(rowHeights.size());
        int lowerRowsHeight = 0;
        for (int row = rowHeights.size() - 1; row >= 0; row--) {
            rowBottoms.add(0, -lowerRowsHeight);
            lowerRowsHeight += rowHeights.get(row);
            if (row > 0) lowerRowsHeight += GAP_BETWEEN;
        }
        List<Integer> tops = new ArrayList<>(heights.size());
        for (int i = 0; i < heights.size(); i++) {
            int row = i / 2;
            tops.add(rowBottoms.get(row) - Math.max(0, heights.get(i)));
        }
        return List.copyOf(tops);
    }

    /** Horizontal centres for two-column rows, centred around the NPC. */
    public static List<Float> rowCentres(List<Integer> widths) {
        if (widths == null || widths.isEmpty()) {
            return List.of();
        }
        List<Float> centres = new ArrayList<>(widths.size());
        for (int start = 0; start < widths.size(); start += 2) {
            int count = Math.min(2, widths.size() - start);
            int first = Math.max(0, widths.get(start));
            int second = count == 2 ? Math.max(0, widths.get(start + 1)) : 0;
            if (count == 1) {
                centres.add(0.0f);
                continue;
            }
            float total = first + second + (count == 2 ? GAP_BETWEEN : 0);
            centres.add(-total / 2.0f + first / 2.0f);
            if (count == 2) {
                centres.add(-total / 2.0f + first + GAP_BETWEEN + second / 2.0f);
            }
        }
        return List.copyOf(centres);
    }
}
