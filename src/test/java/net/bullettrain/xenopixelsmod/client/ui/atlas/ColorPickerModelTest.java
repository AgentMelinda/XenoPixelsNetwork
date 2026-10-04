package net.bullettrain.xenopixelsmod.client.ui.atlas;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** The picking rules the old picker got wrong. */
class ColorPickerModelTest {

    @Test
    void draggingValueToBlackAndBackKeepsTheHue() {
        ColorPickerModel model = new ColorPickerModel(0xFF0000);
        model.pickSquare(0.33, 0.0);
        float hue = model.hue();
        model.pickValue(1.0);
        assertEquals(0x000000, model.rgb());
        model.pickValue(0.0);
        assertEquals(hue, model.hue(), 1e-6, "the hue must survive a trip through black");
        assertEquals(1.0f, model.saturation(), 1e-6);
    }

    @Test
    void readingAGreyKeepsTheCurrentHue() {
        ColorPickerModel model = new ColorPickerModel(0x00FF00);
        float hue = model.hue();
        model.setRgb(0x808080);
        assertEquals(hue, model.hue(), 1e-6);
        assertEquals(0.0f, model.saturation(), 1e-6);
    }

    @Test
    void dragsPastTheEdgesPinInsteadOfLettingGo() {
        ColorPickerModel model = new ColorPickerModel(0xFFFFFF);
        model.pickSquare(-3.0, 5.0);
        assertEquals(0.0f, model.hue(), 1e-6);
        assertEquals(0.0f, model.saturation(), 1e-6);
        model.pickSquare(9.0, -1.0);
        assertEquals(1.0f, model.saturation(), 1e-6);
        model.pickValue(-2.0);
        assertEquals(1.0f, model.value(), 1e-6);
    }

    @Test
    void hexRoundTripsExactly() {
        for (int rgb : new int[] {0x123456, 0xFFC14A, 0x000000, 0xFFFFFF, 0x7F7F7F}) {
            ColorPickerModel model = new ColorPickerModel(rgb);
            assertEquals(rgb, ColorPickerModel.parseStrictHex(model.hex()).getAsInt(),
                    () -> "round trip of " + Integer.toHexString(rgb));
        }
    }

    @Test
    void partialHexIsIgnoredWhileTyping() {
        assertFalse(ColorPickerModel.parseStrictHex("#12").isPresent());
        assertFalse(ColorPickerModel.parseStrictHex("#12345").isPresent());
        assertFalse(ColorPickerModel.parseStrictHex("red").isPresent());
        assertEquals(0x123456, ColorPickerModel.parseStrictHex("#123456").getAsInt());
        assertEquals(0x123456, ColorPickerModel.parseStrictHex("FF123456").getAsInt(),
                "ARGB drops the alpha, as the stored colours are RGB");
    }

    /** 2026-09-30 owner: "set color picker steartgn of color to be the brightest". */
    @Test
    void openingStartsAtFullBrightnessWithTheColoursHueAndSaturation() {
        ColorPickerModel dark = ColorPickerModel.opening(0x222629);
        ColorPickerModel reference = new ColorPickerModel(0x222629);
        assertEquals(1.0f, dark.value(), 1.0e-6);
        assertEquals(reference.hue(), dark.hue(), 1.0e-6);
        assertEquals(reference.saturation(), dark.saturation(), 1.0e-6);

        assertEquals(1.0f, ColorPickerModel.opening(0x000000).value(), 1.0e-6,
                "black opens at the top of the bar too");
        assertEquals(0xFFFFFF, ColorPickerModel.opening(0xFFFFFF).rgb());
    }

    @Test
    void valueBarShowsTheChosenSaturation() {
        ColorPickerModel model = new ColorPickerModel(0xFF0000);
        model.pickSquare(0.33, 1.0);
        assertEquals(0xFFFFFF, model.fullValueRgb());
        model.pickSquare(0.33, 0.0);
        assertEquals(model.pureHueRgb(), model.fullValueRgb());
    }
}
