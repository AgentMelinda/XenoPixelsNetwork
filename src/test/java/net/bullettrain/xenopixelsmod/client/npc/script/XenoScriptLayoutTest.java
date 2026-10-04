package net.bullettrain.xenopixelsmod.client.npc.script;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the Java layout to the sizes the atlas generator (xeno_extra_specs.script_layout) produced,
 * and that nothing is placed outside the frame - the first version ran buttons off its edge.
 */
class XenoScriptLayoutTest {

    @Test
    void theFormulasMatchTheGeneratedPanelSizes() {
        // Printed by: python -c "import xeno_extra_specs as x; print(x.script_layout(400))"
        XenoScriptLayout l = XenoScriptLayout.of(400);
        assertEquals(224, l.h);
        assertEquals(257, l.codeW);
        assertEquals(208, l.codeH);
        assertEquals(217, l.consoleW);
        assertEquals(208, l.consoleH);
        assertEquals(121, l.sideW);
        assertEquals(120, l.filesH);
        assertEquals(112, l.hooksH);
        assertEquals(66, l.constsH);
        XenoScriptLayout small = XenoScriptLayout.of(320);
        assertEquals(177, small.codeW);
        assertEquals(163, small.codeH);
        assertEquals(75, small.filesH);
        XenoScriptLayout large = XenoScriptLayout.of(560);
        assertEquals(417, large.codeW);
        assertEquals(297, large.codeH);
    }

    @Test
    void everyColumnStaysInsideTheFrame() {
        for (int w : XenoScriptLayout.FRAME_WIDTHS) {
            XenoScriptLayout l = XenoScriptLayout.of(w);
            int m = XenoScriptLayout.MARGIN;
            assertTrue(m + l.codeW + XenoScriptLayout.GAP <= l.sideX(), "code meets the button column at " + w);
            assertTrue(l.sideX() + XenoScriptLayout.SIDE_W <= w - m + 1, "buttons inside at " + w);
            assertTrue(m + l.consoleW + XenoScriptLayout.GAP <= l.settingsX(), "console meets settings at " + w);
            assertTrue(l.settingsX() + XenoScriptLayout.SETTINGS_W <= w - m + 1, "settings inside at " + w);
            assertTrue(m + l.codeH <= l.h - m, "code fits vertically at " + w);
            assertTrue(XenoScriptLayout.FILES_TOP + l.filesH <= l.h - m, "files list fits at " + w);
            assertTrue(m + 22 + 4 + l.hooksH + 4 + l.constsH <= l.h - m, "function lists fit at " + w);
        }
    }

    @Test
    void fitPicksTheLargestFrameInsideTheReferenceLimits() {
        assertEquals(320, XenoScriptLayout.fit(360, 240).w, "never smaller than the smallest");
        assertEquals(560, XenoScriptLayout.fit(640, 360).w);
        assertEquals(480, XenoScriptLayout.fit(560, 330).w);
    }
}
