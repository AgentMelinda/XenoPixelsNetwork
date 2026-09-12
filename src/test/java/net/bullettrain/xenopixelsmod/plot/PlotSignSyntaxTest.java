package net.bullettrain.xenopixelsmod.plot;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlotSignSyntaxTest {

    @Test
    void parsesFourLineForm() {
        PlotSignData data = PlotSignSyntax.parse(new String[]{
                "[XPLOT]", "10,-20", "40,5", "500"
        });
        assertNotNull(data);
        assertEquals(10, data.minX());
        assertEquals(-20, data.minZ());
        assertEquals(40, data.maxX());
        assertEquals(5, data.maxZ());
        assertEquals(500.0, data.price());
    }

    @Test
    void markerMatchIsCaseInsensitiveAndTrimmed() {
        assertNotNull(PlotSignSyntax.parse(new String[]{
                "  [xplot]  ", "0,0", "9,9", "1"
        }));
    }

    @Test
    void normalizesCornersTypedInEitherOrder() {
        PlotSignData data = PlotSignSyntax.parse(new String[]{
                "[XPLOT]", "40,5", "10,-20", "500"
        });
        assertNotNull(data);
        assertEquals(10, data.minX());
        assertEquals(-20, data.minZ());
        assertEquals(40, data.maxX());
        assertEquals(5, data.maxZ());
        assertEquals(31, data.width());
        assertEquals(26, data.length());
    }

    @Test
    void acceptsOptionalCurrencyTagAfterPrice() {
        PlotSignData data = PlotSignSyntax.parse(new String[]{
                "[XPLOT]", "0,0", "9,9", "100 zp"
        });
        assertNotNull(data);
        assertEquals(100.0, data.price());
    }

    @Test
    void rejectsNonPlotSigns() {
        assertNull(PlotSignSyntax.parse(null));
        assertNull(PlotSignSyntax.parse(new String[]{"hello", "0,0", "9,9", "5"}));
        assertNull(PlotSignSyntax.parse(new String[]{"[XPLOT]"}));
    }

    @Test
    void rejectsBadCoordinatesAndPrice() {
        assertNull(PlotSignSyntax.parse(new String[]{"[XPLOT]", "nope", "9,9", "5"}));
        assertNull(PlotSignSyntax.parse(new String[]{"[XPLOT]", "0,", "9,9", "5"}));
        assertNull(PlotSignSyntax.parse(new String[]{"[XPLOT]", ",0", "9,9", "5"}));
        assertNull(PlotSignSyntax.parse(new String[]{"[XPLOT]", "0,0", "9,9", "-5"}));
        assertNull(PlotSignSyntax.parse(new String[]{"[XPLOT]", "0,0", "9,9", "abc"}));
        assertNull(PlotSignSyntax.parse(
                new String[]{"[XPLOT]", "99999999,0", "9,9", "5"}));
    }

    @Test
    void roundTripsThroughSerialize() {
        PlotSignData original = new PlotSignData(10, -20, 40, 5, 500.0);
        String[] lines = PlotSignSyntax.serialize(original);
        assertEquals("[XPLOT]", lines[0]);
        assertEquals("10,-20", lines[1]);
        assertEquals("40,5", lines[2]);
        assertEquals("500", lines[3]);
        assertEquals(original, PlotSignSyntax.parse(lines));
    }

    @Test
    void isPlotSignTracksMarkerPresence() {
        assertTrue(PlotSignSyntax.isPlotSign(new String[]{"[XPLOT]", "x", "y", "z"}));
        assertTrue(PlotSignSyntax.isPlotSign(new String[]{"[XPLOT] garbage", "", "", ""}));
        assertFalse(PlotSignSyntax.isPlotSign(new String[]{"plain", "", "", ""}));
        assertFalse(PlotSignSyntax.isPlotSign(null));
    }

    @Test
    void rejectsNegativePrice() {
        assertNull(PlotSignSyntax.parse(new String[]{"[XPLOT]", "0,0", "9,9", "-0.01"}));
    }
}