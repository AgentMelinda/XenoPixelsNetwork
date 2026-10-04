package net.bullettrain.xenopixelsmod.client.maker;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MakerPreviewLayoutTest {
    @Test
    void previewStaysRightOfList() {
        // Atlas sizes are fixed; use a roomy UI box matching getMinGui for Hair/Form.
        MakerPreviewLayout.Columns c = MakerPreviewLayout.compute(900, 500);
        assertTrue(MakerPreviewLayout.previewClearOfList(c),
                "previewX=" + c.previewX() + " listRight=" + (c.listX() + c.listW()));
        assertEquals(c.settingsX() + c.settingsW() + MakerPreviewLayout.GAP, c.previewX());
        assertTrue(c.previewW() >= 200);
        assertTrue(c.previewH() >= 200);
        assertTrue(c.footerY() >= c.previewY() + c.previewH());
    }

    @Test
    void appearanceFromHairIsNotEmpty() {
        var doc = net.bullettrain.xenopixelsmod.hair.HairMakerDocument.oneStrandDemo();
        MakerPreviewAppearance appearance = MakerPreviewAppearance.fromHair(doc);
        org.junit.jupiter.api.Assertions.assertFalse(appearance.isEmpty());
    }
}
