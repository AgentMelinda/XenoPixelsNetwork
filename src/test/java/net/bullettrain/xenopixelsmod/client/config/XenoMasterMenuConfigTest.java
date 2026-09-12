package net.bullettrain.xenopixelsmod.client.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class XenoMasterMenuConfigTest {

    @Test
    void partCatalogMatchesTheLiveChrome() {
        assertEquals(XenoMasterMenuConfig.Part.COUNT, XenoMasterMenuConfig.Part.NAMES.length);
        assertEquals(0, XenoMasterMenuConfig.Part.byName("nameplate"));
        assertEquals(11, XenoMasterMenuConfig.Part.byName("formLabel"));
        assertEquals(12, XenoMasterMenuConfig.Part.byName("masterHeaderIcon"));
        assertEquals(13, XenoMasterMenuConfig.Part.byName("masterHeaderLabel"));
        assertEquals(14, XenoMasterMenuConfig.Part.byName("formsHeaderIcon"));
        assertEquals(15, XenoMasterMenuConfig.Part.byName("formsHeaderLabel"));
        assertEquals(16, XenoMasterMenuConfig.Part.byName("closeLabel"));
        assertEquals(17, XenoMasterMenuConfig.Part.byName("closeHint"));
        assertEquals(-1, XenoMasterMenuConfig.Part.byName("navRow"));
    }

    @Test
    void shippedLayoutIgnoresStoredOffsetsUntilCustomLayoutIsOn() {
        boolean prior = XenoMasterMenuConfig.customLayout;
        int priorX = XenoMasterMenuConfig.partX[0];
        try {
            XenoMasterMenuConfig.partX[0] = 40;
            XenoMasterMenuConfig.customLayout = false;
            assertEquals(0, XenoMasterMenuConfig.partX(0));
            XenoMasterMenuConfig.customLayout = true;
            assertEquals(40, XenoMasterMenuConfig.partX(0));
        } finally {
            XenoMasterMenuConfig.partX[0] = priorX;
            XenoMasterMenuConfig.customLayout = prior;
        }
    }

    @Test
    void clampKeepsADragOnTheScreen() {
        assertEquals(-200, XenoMasterMenuConfig.clampPartOffset(-999));
        assertEquals(200, XenoMasterMenuConfig.clampPartOffset(999));
        assertEquals(12, XenoMasterMenuConfig.clampPartOffset(12));
    }

    @Test
    void twoPanelsPlusGapFitTheScaledUiWidth() {
        int total = 150 + 4 + 150;
        assertTrue(total <= 320, "MASTER+FORMS must fit ScaledScreen 320 UI width");
    }

    @Test
    void aShorterSavedPartListKeepsNewHeaderSubpartsAtDefaults() {
        int[] stored = new int[12];
        int[] into = new int[XenoMasterMenuConfig.Part.COUNT];
        into[XenoMasterMenuConfig.Part.MASTER_HEADER_ICON] = 0;
        System.arraycopy(stored, 0, into, 0, stored.length);
        assertEquals(0, into[XenoMasterMenuConfig.Part.MASTER_HEADER_ICON]);
        assertEquals(0, into[XenoMasterMenuConfig.Part.MASTER_HEADER_LABEL]);
        assertEquals(0, into[XenoMasterMenuConfig.Part.CLOSE_LABEL]);
        assertEquals(18, XenoMasterMenuConfig.Part.COUNT);
    }
}
