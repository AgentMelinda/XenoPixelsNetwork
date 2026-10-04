package net.bullettrain.xenopixelsmod.client.npc.speech;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpeechBubbleMotionTest {
    @Test
    void ambientBubblesAnchorAboveTheHeadAndKeepCustomOffsets() {
        assertEquals(2.5f, SpeechBubbleMotion.anchorHeight(1.8f, 0.7f, 0.7f), 0.0001f);
        assertEquals(2.8f, SpeechBubbleMotion.anchorHeight(1.8f, 1.0f, 0.7f), 0.0001f);
        assertEquals(0.5f, SpeechBubbleMotion.anchorHeight(0.5f, 0.0f, 0.7f), 0.0001f,
                "zero means exactly at the top of the entity");
    }

    @Test
    void riseAndReturnAreSmoothAndBounded() {
        float start = SpeechBubbleMotion.verticalOffset(0, 60, 0);
        float rising = SpeechBubbleMotion.verticalOffset(0, 60, 4);
        float peak = SpeechBubbleMotion.verticalOffset(0, 60, 20);
        float settling = SpeechBubbleMotion.verticalOffset(0, 60, 54);
        float end = SpeechBubbleMotion.verticalOffset(0, 60, 60);

        assertEquals(0.0f, start, 0.0001f);
        assertTrue(rising > start && rising < peak);
        assertTrue(peak > settling && settling > end);
        assertEquals(0.0f, end, 0.0001f);
        assertTrue(peak <= 0.16f);
    }

    @Test
    void fadeUsesAnEaseCurveAndSideChoiceIsStable() {
        float lateFade = SpeechBubbleMotion.alpha(60, 57.5);
        assertTrue(lateFade > 0.0f && lateFade < 0.25f);
        assertEquals(0.0f, SpeechBubbleMotion.alpha(60, 60), 0.0001f);
        assertEquals(SpeechBubbleMotion.side(17), SpeechBubbleMotion.side(17));
        assertEquals(-SpeechBubbleMotion.side(17), SpeechBubbleMotion.side(16));
    }
}
