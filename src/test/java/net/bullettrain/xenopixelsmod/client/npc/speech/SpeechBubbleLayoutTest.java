package net.bullettrain.xenopixelsmod.client.npc.speech;

import net.bullettrain.xenopixelsmod.client.ui.atlas.XenoAtlasSprites;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The sizing rule behind "the bubble is too small and the text runs out of it".
 *
 * <p>There was one bubble sprite, 120x56, and the message was drawn as a single unwrapped line. Any
 * line wider than the bubble overflowed it in both directions, which reads as misalignment. These
 * tests pin the replacement: a block of wrapped text picks the smallest generated cell that holds
 * it, and never a stretched one.
 *
 * <p>Only the arithmetic is covered. Wrapping itself needs a {@code Font}, which needs a running
 * client, so it is checked in game - the same split {@code NpcDisplayApplySizeTest} makes.
 */
class SpeechBubbleLayoutTest {

    @Test
    void oneLineIsJustTheFontHeight() {
        assertEquals(SpeechBubbleLayout.FONT_HEIGHT, SpeechBubbleLayout.blockHeight(1));
    }

    @Test
    void eachExtraLineAddsExactlyOneLineHeight() {
        for (int n = 1; n < 6; n++) {
            assertEquals(SpeechBubbleLayout.blockHeight(n) + SpeechBubbleLayout.LINE_HEIGHT,
                    SpeechBubbleLayout.blockHeight(n + 1),
                    "line " + (n + 1) + " should cost one line height");
        }
    }

    @Test
    void noTextIsNoHeight() {
        assertEquals(0, SpeechBubbleLayout.blockHeight(0));
        assertEquals(0, SpeechBubbleLayout.blockHeight(-3));
    }

    @Test
    void aLongerLineGetsAWiderBubble() {
        // The regression in one assertion: a short line and a long one must not come back with the
        // same sprite, because that is what made long text overflow.
        String narrow = SpeechBubbleLayout.spriteFor(40, 1);
        String wide = SpeechBubbleLayout.spriteFor(240, 1);
        assertNotEquals(narrow, wide);
        assertTrue(widthOf(wide) > widthOf(narrow), wide + " should be wider than " + narrow);
    }

    @Test
    void moreLinesGetATallerBubble() {
        String one = SpeechBubbleLayout.spriteFor(40, 1);
        String four = SpeechBubbleLayout.spriteFor(40, 4);
        assertTrue(heightOf(four) > heightOf(one), four + " should be taller than " + one);
    }

    @Test
    void theChosenBubbleActuallyHoldsTheTextItWasSizedFor() {
        // The property that matters: whatever cell comes back, the text plus its padding fits
        // inside the body, with the tail left clear. If this fails, text is drawn over the border.
        for (int lines = 1; lines <= SpeechBubbleLayout.maxLines(); lines++) {
            for (int textWidth : new int[]{10, 60, 100, 150, 200, 240}) {
                String name = SpeechBubbleLayout.spriteFor(textWidth, lines);
                XenoAtlasSprites.Sprite sprite = XenoAtlasSprites.get(name);

                assertTrue(sprite.width() >= textWidth + SpeechBubbleLayout.PADDING_X,
                        name + " is too narrow for " + textWidth + "px of text");

                float body = sprite.height() * (1.0f - SpeechBubbleLayout.TAIL_HEIGHT_FRACTION);
                assertTrue(body >= SpeechBubbleLayout.blockHeight(lines) + SpeechBubbleLayout.PADDING_Y,
                        name + " body is too short for " + lines + " lines");
            }
        }
    }

    @Test
    void anAbsurdlyLongLineClampsToTheLargestCellRatherThanFailing() {
        // Falling back beats throwing: an unusually long line should still draw, just clipped.
        String name = SpeechBubbleLayout.spriteFor(10_000, 99);
        XenoAtlasSprites.Sprite sprite = XenoAtlasSprites.get(name);
        assertEquals(XenoAtlasSprites.maxBubbleWidth(), sprite.width());
        assertEquals(XenoAtlasSprites.maxBubbleHeight(), sprite.height());
    }

    @Test
    void everyBubbleTheSizerCanChooseIsARegisteredSprite() {
        // spriteFor builds a name by string concatenation, so a size present in one place and not
        // the other would only show up as a missing texture in game. This is that check.
        for (int lines = 1; lines <= SpeechBubbleLayout.maxLines() + 2; lines++) {
            for (int textWidth = 0; textWidth <= 320; textWidth += 10) {
                String name = SpeechBubbleLayout.spriteFor(textWidth, lines);
                assertTrue(XenoAtlasSprites.shapes().contains(name),
                        name + " is not a registered atlas shape");
            }
        }
    }

    @Test
    void theTextBudgetLeavesRoomForThePadding() {
        assertEquals(XenoAtlasSprites.maxBubbleWidth() - SpeechBubbleLayout.PADDING_X,
                SpeechBubbleLayout.textBudget());
        assertTrue(SpeechBubbleLayout.textBudget() > 0);
    }

    @Test
    void theLineClampMatchesWhatTheTallestBubbleHolds() {
        int max = SpeechBubbleLayout.maxLines();
        assertTrue(max >= 2, "a one-line clamp would be no better than the old single line");

        // The last line that fits must fit, and one more must not.
        float body = XenoAtlasSprites.maxBubbleHeight() * (1.0f - SpeechBubbleLayout.TAIL_HEIGHT_FRACTION);
        assertTrue(SpeechBubbleLayout.blockHeight(max) + SpeechBubbleLayout.PADDING_Y <= body);
        assertTrue(SpeechBubbleLayout.blockHeight(max + 1) + SpeechBubbleLayout.PADDING_Y > body);
    }

    private static int widthOf(String spriteName) {
        return XenoAtlasSprites.get(spriteName).width();
    }

    private static int heightOf(String spriteName) {
        return XenoAtlasSprites.get(spriteName).height();
    }
}
