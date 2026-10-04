package net.bullettrain.xenopixelsmod.client.ui.atlas;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AtlasTextFitTest {

    @Test
    void groupUsesOneScaleForEverySiblingAndKeepsNormalTextAtNativeSize() {
        float scale = AtlasTextFit.groupScale(List.of(
                new AtlasTextFit.Measure(40, 64),
                new AtlasTextFit.Measure(80, 64)), AtlasTextFit.MIN_SCALE);

        assertEquals(0.8f, scale, 0.0001f);
        assertEquals(scale, AtlasTextFit.groupScale(List.of(
                new AtlasTextFit.Measure(40, 64),
                new AtlasTextFit.Measure(80, 64)), AtlasTextFit.MIN_SCALE));
        assertEquals(1.0f, AtlasTextFit.groupScale(List.of(
                new AtlasTextFit.Measure(40, 64)), AtlasTextFit.MIN_SCALE));
    }

    @Test
    void readableFloorUsesEllipsisInsteadOfAllowingOverflow() {
        float scale = AtlasTextFit.groupScale(List.of(
                new AtlasTextFit.Measure(120, 64)), AtlasTextFit.MIN_SCALE);
        java.util.function.ToIntFunction<String> width = text -> text.length() * 8;
        String fitted = AtlasTextFit.fit("A very long label", 64, scale, width);

        assertEquals(AtlasTextFit.MIN_SCALE, scale);
        assertTrue(fitted.endsWith("…"));
        assertTrue(width.applyAsInt(fitted) * scale <= 64);
    }

    @Test
    void textThatFitsAtTheChosenScaleStaysWhole() {
        assertEquals("Short", AtlasTextFit.fit("Short", 64, 1.0f, String::length));
    }
}
