package net.bullettrain.xenopixelsmod.aero;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuidanceVersionTest {

    @Test
    void byNameParsesAliasesAndDefaultsToV1() {
        assertEquals(GuidanceVersion.V1, GuidanceVersion.byName(null));
        assertEquals(GuidanceVersion.V1, GuidanceVersion.byName(""));
        assertEquals(GuidanceVersion.V1, GuidanceVersion.byName("v1"));
        assertEquals(GuidanceVersion.V1, GuidanceVersion.byName("1"));
        assertEquals(GuidanceVersion.V2, GuidanceVersion.byName("v2"));
        assertEquals(GuidanceVersion.V2, GuidanceVersion.byName("V2"));
        assertEquals(GuidanceVersion.V3, GuidanceVersion.byName("v3"));
        assertEquals(GuidanceVersion.V1, GuidanceVersion.byName("nope"));
    }

    /** 2026-09-28: V3 is real now (V1 flight/HUD/GUI plus the V3 missile guidance). */
    @Test
    void sanitizeKeepsEveryVersion() {
        assertEquals(GuidanceVersion.V1, GuidanceConfig.sanitize(null));
        assertEquals(GuidanceVersion.V1, GuidanceConfig.sanitize(GuidanceVersion.V1));
        assertEquals(GuidanceVersion.V2, GuidanceConfig.sanitize(GuidanceVersion.V2));
        assertEquals(GuidanceVersion.V3, GuidanceConfig.sanitize(GuidanceVersion.V3));
    }

    @Test
    void onlyV2UsesTheV2FlightStackAndOnlyV3TheV3Missiles() {
        assertFalse(GuidanceVersion.V1.isV2());
        assertTrue(GuidanceVersion.V2.isV2());
        assertFalse(GuidanceVersion.V3.isV2(), "V3 keeps V1's flight controller, HUD and planner");
        assertFalse(GuidanceVersion.V1.usesV3Missiles());
        assertFalse(GuidanceVersion.V2.usesV3Missiles());
        assertTrue(GuidanceVersion.V3.usesV3Missiles());
    }
}
