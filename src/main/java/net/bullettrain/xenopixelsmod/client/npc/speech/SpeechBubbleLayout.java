package net.bullettrain.xenopixelsmod.client.npc.speech;

import net.bullettrain.xenopixelsmod.client.ui.atlas.XenoAtlasSprites;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/**
 * Works out how big a speech bubble has to be, and which generated cell that is.
 *
 * <p>{@link SpeechBubbleRenderer} used to draw the whole message as one unwrapped line inside a
 * single 120x56 sprite, so anything longer than about fifteen characters ran straight out past both
 * edges of the bubble. Fixing that needs two things, and this class is both: wrap the text, then
 * choose a bubble that holds it. The atlas is blitted 1:1 with its border baked in at generation
 * (see {@code docs/atlas-ui-doco.md}), so "bigger" means a bigger generated cell - never a stretched
 * one.
 *
 * <p>The arithmetic is kept separate from the drawing because a {@link Font} needs a running client
 * and the sizing does not. Everything here that does not take a {@code Font} is plain arithmetic and
 * is unit-tested; the two {@code Font} overloads are thin wrappers over it.
 */
public final class SpeechBubbleLayout {

    /** Baseline-to-baseline spacing between wrapped lines, in GUI pixels. */
    public static final int LINE_HEIGHT = 10;

    /** Height of one line of Minecraft's font, in GUI pixels. */
    public static final int FONT_HEIGHT = 9;

    /** Total horizontal padding inside the bubble - half of it on each side. */
    public static final int PADDING_X = 14;

    /** Total vertical padding inside the bubble body. */
    public static final int PADDING_Y = 12;

    /**
     * How much of a bubble sprite's height is the tail hanging below the body.
     *
     * <p>Lives here rather than in the renderer because both the renderer (to centre text in the
     * body) and {@link #spriteFor(int, int)} (to size the body) need the same number, and they must
     * not be able to disagree.
     */
    public static final float TAIL_HEIGHT_FRACTION = 0.22f;

    private SpeechBubbleLayout() {
    }

    /** Widest line of text a bubble can hold, in GUI pixels. */
    public static int textBudget() {
        return XenoAtlasSprites.maxBubbleWidth() - PADDING_X;
    }

    /**
     * How many wrapped lines the tallest generated bubble holds.
     *
     * <p>Derived from the sprite sizes rather than hard-coded, so adding a taller cell to
     * {@code xeno_extra_specs.py} raises this on its own instead of silently going unused.
     */
    public static int maxLines() {
        float body = XenoAtlasSprites.maxBubbleHeight() * (1.0f - TAIL_HEIGHT_FRACTION);
        float usable = body - PADDING_Y;
        if (usable < FONT_HEIGHT) {
            return 1;
        }
        return Math.max(1, (int) ((usable - FONT_HEIGHT) / LINE_HEIGHT) + 1);
    }

    /** Pixel height of {@code lineCount} stacked lines of text. */
    public static int blockHeight(int lineCount) {
        if (lineCount <= 0) {
            return 0;
        }
        return (lineCount - 1) * LINE_HEIGHT + FONT_HEIGHT;
    }

    /**
     * The smallest generated bubble that holds a block {@code widestLine} px wide and
     * {@code lineCount} lines tall.
     *
     * <p>The height has to be grossed up by the tail: only {@code 1 - TAIL_HEIGHT_FRACTION} of a
     * sprite is body, and text is centred in the body alone so it never sits over the tail.
     */
    public static String spriteFor(int widestLine, int lineCount) {
        return spriteFor(widestLine, lineCount, net.bullettrain.xenopixelsmod.npc.lines.BubbleShape.ROUNDED);
    }

    /** As above in a given outline; every shape keeps the same body/tail split. */
    public static String spriteFor(int widestLine, int lineCount, net.bullettrain.xenopixelsmod.npc.lines.BubbleShape shape) {
        int neededWidth = Math.max(0, widestLine) + PADDING_X + extraPaddingX(shape);
        int neededBody = blockHeight(lineCount) + PADDING_Y + extraPaddingY(shape);
        int neededHeight = Math.round(neededBody / (1.0f - TAIL_HEIGHT_FRACTION));
        return XenoAtlasSprites.bubble(shape, neededWidth, neededHeight);
    }

    /**
     * The cloud's puffs and the burst's spikes eat into the body box that the rounded bubble
     * leaves clear, so those outlines ask for a larger cell for the same text.
     */
    static int extraPaddingX(net.bullettrain.xenopixelsmod.npc.lines.BubbleShape shape) {
        if (shape == null) return 0;
        return switch (shape) {
            case THOUGHT -> 12;
            case SHOUT -> 20;
            case BANNER -> 8;
            default -> 0;
        };
    }

    static int extraPaddingY(net.bullettrain.xenopixelsmod.npc.lines.BubbleShape shape) {
        if (shape == null) return 0;
        return switch (shape) {
            case THOUGHT -> 8;
            case SHOUT -> 12;
            default -> 0;
        };
    }

    /**
     * Wraps {@code text} to the widest bubble, clamped to {@link #maxLines()}.
     *
     * <p>Clamped rather than allowed to overflow: a bubble that has run out of cells should drop the
     * tail of a very long line, not draw text outside its own border - which is the bug this whole
     * class exists to end.
     */
    public static List<FormattedCharSequence> wrap(Font font, String text) {
        List<FormattedCharSequence> lines =
                font.split(BubbleText.styled(text), textBudget());
        int max = maxLines();
        return lines.size() <= max ? lines : lines.subList(0, max);
    }

    /** The bubble that holds these already-wrapped lines. */
    public static String spriteFor(Font font, List<FormattedCharSequence> lines) {
        int widest = 0;
        for (FormattedCharSequence line : lines) {
            widest = Math.max(widest, font.width(line));
        }
        return spriteFor(widest, lines.size());
    }

    /** As above in a given outline. */
    public static String spriteFor(Font font, List<FormattedCharSequence> lines, net.bullettrain.xenopixelsmod.npc.lines.BubbleShape shape) {
        int widest = 0;
        for (FormattedCharSequence line : lines) {
            widest = Math.max(widest, font.width(line));
        }
        return spriteFor(widest, lines.size(), shape);
    }
}
