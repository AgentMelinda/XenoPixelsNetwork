package net.bullettrain.xenopixelsmod.hud;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class HudPartsBundleTest {
    @Test
    void roundTripKeepsPartOffsets() {
        HudPartsBundle bundle = new HudPartsBundle();
        HudPartsBundle.Surface surface = new HudPartsBundle.Surface();
        surface.customLayout = true;
        surface.partX = new int[]{4, -2};
        surface.partY = new int[]{8, 0};
        surface.partHidden = new boolean[]{false, true};
        bundle.surfaces.put("Master", surface);

        HudPartsBundle parsed = HudPartsBundle.fromJson(HudPartsBundle.toJson(bundle));
        assertNotNull(parsed);
        assertEquals(4, parsed.surfaces.get("Master").partX[0]);
        assertEquals(true, parsed.surfaces.get("Master").partHidden[1]);
    }

    @Test
    void garbageJsonIsRejected() {
        assertNull(HudPartsBundle.fromJson("{"));
        assertNull(HudPartsBundle.fromJson(""));
        assertNull(HudPartsBundle.fromJson("{\"version\":1}"));
    }
}
